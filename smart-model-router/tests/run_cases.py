#!/usr/bin/env python3
"""
数据驱动的黑盒测试执行器：从 tests/cases.json 读取全部用例，
对运行中的 MAS 应用逐条执行并断言，生成 reports/test-report.md。

设计约定：
- 本脚本不包含任何测试提示词；用例（含 prompt/期望）全部声明在 cases.json。
- 运行隔离（抗语义缓存干扰）三重保障：
  1) 套件开始前清空两级缓存表（mas_exact_cache / mas_semantic_cache），保证确定性；
  2) 对话类用例拼接按 (model, prompt) 哈希派生的确定性随机无义前缀，
     不同用例间相似度压到 0.95 阈值以下（实测 0.62~0.79），同文用例（ref/nonce_group）
     共享前缀以支撑缓存命中类断言（CH-03/CH-04）；
  3) 断言 intent/difficulty 的用例不加前缀，保证难度评分只基于用例原始 prompt。
- 报告含：总体统计、分类统计、每条用例的路由明细（intent/difficulty/后端模型/缓存/耗时）、
  以及 target/surefire-reports 中的单元测试明细（如存在）。

用法：python3 tests/run_cases.py [--base http://localhost:9090/smart-router] [--report reports/test-report.md]
退出码：0=全部通过；1=存在失败；2=应用不可达。
"""
import argparse
import concurrent.futures
import glob
import hashlib
import json
import os
import re
import subprocess
import time
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
from datetime import datetime

BAG = ["墨水", "潮汐", "青苔", "齿轮", "青铜", "星尘", "帆影", "烛台", "砂岩", "雾松"]


def gen_prefix(key):
    """按 key（model+prompt 或 nonce_group）确定性派生隔离前缀：同 key 同前缀，异 key 异前缀。
    档案号取 48 位熵（1e14 空间），避免套件内前缀碰撞导致的意外语义命中"""
    h = int(hashlib.sha1(key.encode("utf-8")).hexdigest(), 16)
    return f"{BAG[h % 10]}与{BAG[(h // 7) % 10]}的关联是什么？档案{h % 10**14:014d}。"


def truncate_caches(base):
    """套件开始前清空两级缓存（含应用内 Caffeine 本地层），保证断言确定性；失败仅告警不中断"""
    try:
        status, _, text, _ = http_call(base + "/internal/cache/flush", body="", timeout=15)
        if status == 200:
            print(f"已清空两级缓存（{text.strip()}，含本地 Caffeine 层）")
            return
        print(f"警告：缓存清空端点返回 HTTP {status}，回退 psql truncate")
    except Exception as e:
        print(f"警告：缓存清空端点不可用（{e}），回退 psql truncate")
    psql = "/opt/homebrew/opt/postgresql@18/bin/psql"
    try:
        r = subprocess.run([psql, "postgresql://mas:mas123@localhost:5432/mas",
                            "-qc", "TRUNCATE mas_exact_cache, mas_semantic_cache;"],
                           capture_output=True, text=True, timeout=10)
        if r.returncode == 0:
            print("已通过 psql 清空缓存表（注意：应用内本地缓存层未清，建议重启应用）")
        else:
            print(f"警告：缓存清空失败（{r.stderr.strip()}），依赖隔离前缀继续")
    except Exception as e:
        print(f"警告：缓存清空跳过（{e}），依赖隔离前缀继续")


# 串行请求限速：缓存命中/错误类用例响应极快，不限速会瞬时突破 per-user-qps=20 触发 429
MIN_INTERVAL_S = 0.12
_last_req_ts = 0.0


def pace():
    global _last_req_ts
    wait = MIN_INTERVAL_S - (time.time() - _last_req_ts)
    if wait > 0:
        time.sleep(wait)
    _last_req_ts = time.time()


class CaseResult:
    def __init__(self, case):
        self.case = case
        self.http = None
        self.latency_ms = None
        self.intent = None
        self.difficulty = None
        self.backend = None
        self.cache_hit = None
        self.cache_level = None
        self.routed_to = None
        self.err_code = None
        self.err_type = None
        self.legacy_code = None
        self.sse_frames = None
        self.trace_id = None
        self.count_429 = None
        self.content_final = None
        self.model_final = None
        self.max_tokens_final = None
        self.checks = []  # (ok, desc)

    @property
    def passed(self):
        return all(ok for ok, _ in self.checks)

    def add(self, ok, desc):
        self.checks.append((bool(ok), desc))


def http_call(url, method="POST", body=None, headers=None, timeout=300):
    data = body.encode("utf-8") if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, dict(resp.headers), resp.read().decode("utf-8", "replace"), int((time.time() - t0) * 1000)
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace"), int((time.time() - t0) * 1000)


def parse_sse(text):
    """返回 (帧数, meta dict, 首帧model)"""
    frames, meta, model = 0, None, None
    for line in text.splitlines():
        if not line.startswith("data: "):
            continue
        payload = line[6:].strip()
        if payload == "[DONE]":
            frames += 1
            continue
        frames += 1
        try:
            obj = json.loads(payload)
        except Exception:
            continue
        if "x-mas-meta" in obj:
            meta = obj["x-mas-meta"]
        elif model is None and obj.get("model"):
            model = obj["model"]
    return frames, meta, model


def expect_contains(exp, key, actual, res, label=None):
    if key in exp:
        res.add(actual == exp[key], f"{label or key}={exp[key]}（实际 {actual}）")


def evaluate(res, exp, body_text):
    if "http" in exp:
        res.add(res.http == exp["http"], f"HTTP {exp['http']}（实际 {res.http}）")
    if "contains" in exp:
        for s in exp["contains"]:
            res.add(s in body_text, f"响应含 {s[:24]}")
    if "excludes" in exp:
        for s in exp["excludes"]:
            res.add(s not in body_text, f"响应不含 {s[:24]}")
    if "trace_len" in exp:
        res.add(res.trace_id and len(res.trace_id) == exp["trace_len"],
                f"X-Trace-Id 长度 {exp['trace_len']}（实际 {len(res.trace_id) if res.trace_id else 0}）")
    if "embedding_dim" in exp:
        dim = None
        try:
            dim = len(json.loads(body_text)["data"][0]["embedding"])
        except Exception:
            pass
        res.add(dim == exp["embedding_dim"], f"embedding 维度 {exp['embedding_dim']}（实际 {dim}）")
    if "intent" in exp:
        expect_contains(exp, "intent", res.intent, res, "intent")
    if "difficulty_lt" in exp:
        res.add(res.difficulty is not None and res.difficulty < exp["difficulty_lt"],
                f"difficulty<{exp['difficulty_lt']}（实际 {res.difficulty}）")
    if "difficulty_ge" in exp:
        res.add(res.difficulty is not None and res.difficulty >= exp["difficulty_ge"],
                f"difficulty>={exp['difficulty_ge']}（实际 {res.difficulty}）")
    if "backend" in exp:
        expect_contains(exp, "backend", res.backend, res, "后端模型")
    if "cache_hit" in exp:
        expect_contains(exp, "cache_hit", res.cache_hit, res, "cache_hit")
    if "cache_level" in exp:
        expect_contains(exp, "cache_level", res.cache_level, res, "cache_level")
    if "err_code" in exp:
        expect_contains(exp, "err_code", res.err_code, res, "错误码")
    if "err_type" in exp:
        expect_contains(exp, "err_type", res.err_type, res, "错误类型")
    if "legacy_code" in exp:
        expect_contains(exp, "legacy_code", res.legacy_code, res, "旧协议 code")
    if exp.get("legacy_content"):
        content = ""
        try:
            content = json.loads(body_text)["data"]["content"]
        except Exception:
            pass
        res.add(bool(content), "旧协议 data.content 非空")
    if exp.get("sse"):
        res.add(res.sse_frames and res.sse_frames >= 2, f"SSE 帧数>=2（实际 {res.sse_frames}）")
    if "rate429_ge" in exp:
        res.add(res.count_429 is not None and res.count_429 >= exp["rate429_ge"],
                f"429 次数>={exp['rate429_ge']}（实际 {res.count_429}）")
    if "rate429_lt" in exp:
        res.add(res.count_429 is not None and res.count_429 < exp["rate429_lt"],
                f"429 次数<{exp['rate429_lt']}（实际 {res.count_429}）")


def build_content(case, registry):
    """用例文本组装：ref 继承被引用用例的最终文本；断言 difficulty 评分区间的用例用原始 prompt
    （保证难度评分不受前缀污染）；其余用例（含仅断言 intent 的）拼接按 (model, prompt)
    派生的确定性隔离前缀，避免套件内语义缓存误命中"""
    if case.get("ref"):
        return registry[case["ref"]].content_final
    exp = case.get("expect", {})
    if "difficulty_ge" in exp or "difficulty_lt" in exp:
        return case["prompt"]
    key = case.get("nonce_group") or f"{case.get('model', '')}|{case['prompt']}"
    return gen_prefix(key) + " " + case["prompt"]


def run_case(case, ctx):
    res = CaseResult(case)
    base, registry = ctx["base"], ctx["registry"]
    exp = case.get("expect", {})
    pace()
    # 缓存写入为异步（响应返回后 fire-and-forget）：断言命中的用例请求前等待，
    # 确保前置写入用例的两级缓存已落盘，消除写竞态
    if exp.get("cache_hit") is True:
        time.sleep(1.0)

    # ---- 限流专用：并发突发（burst 参数化，默认 40） ----
    if case.get("type") == "rate":
        content = build_content(case, registry)
        body = json.dumps({"model": case.get("model"), "messages": [{"role": "user", "content": content}],
                           "stream": False, "max_tokens": 8}, ensure_ascii=False)

        def burst(_):
            status, _, _, _ = http_call(base + "/v1/chat/completions",
                                        body=body, headers={"X-User-Id": case.get("user", "rate-suite")}, timeout=120)
            return status

        t0 = time.time()
        n = case.get("burst", 40)
        with concurrent.futures.ThreadPoolExecutor(max_workers=n) as pool:
            codes = list(pool.map(burst, range(n)))
        res.latency_ms = int((time.time() - t0) * 1000)
        res.count_429 = codes.count(429)
        res.http = 200 if codes.count(200) else codes[0]
        evaluate(res, exp, "")
        registry[case["id"]] = res
        return res

    # ---- 系统 GET 端点 ----
    if case.get("method") == "GET":
        status, headers, text, cost = http_call(base + case["path"], method="GET", timeout=30)
        res.http, res.latency_ms = status, cost
        res.trace_id = headers.get("X-Trace-Id")
        evaluate(res, exp, text)
        registry[case["id"]] = res
        return res

    # ---- 请求体组装 ----
    headers = {}
    if case.get("user"):
        headers["X-User-Id"] = case["user"]
    stream = bool(case.get("stream"))
    # ref 用例继承被引用用例的 max_tokens：精确缓存键含 max_tokens，不一致会导致键漂移
    max_tokens = case.get("max_tokens") or (
        registry[case["ref"]].max_tokens_final if case.get("ref") else None) or 16

    if "raw" in case:
        body_str = case["raw"]
        url = base + ("/ai/gateway/chatModel" if case.get("cat") == "legacy" else "/v1/chat/completions")
        content = None
    elif case.get("cat") == "legacy":
        content = build_content(case, registry)
        model = case.get("model") or (registry[case["ref"]].model_final if case.get("ref") else None)
        legacy = {"messages": [{"role": "user", "content": content}]}
        if model:
            legacy["modelId"] = model
        legacy["maxTokens"] = max_tokens
        if stream:
            legacy["stream"] = True
        body_str, url = json.dumps(legacy, ensure_ascii=False), base + "/ai/gateway/chatModel"
    elif case.get("cat") == "embedding":
        body_str = json.dumps({"model": "bge-m3", "input": case["prompt"]}, ensure_ascii=False)
        url = base + "/v1/embeddings"
        content = case["prompt"]
    else:
        content = build_content(case, registry)
        model = case.get("model") or (registry[case["ref"]].model_final if case.get("ref") else None)
        req = {"messages": [{"role": "user", "content": content}], "stream": stream,
               "max_tokens": max_tokens}
        if model:
            req["model"] = model
        body_str, url = json.dumps(req, ensure_ascii=False), base + "/v1/chat/completions"

    status, resp_headers, text, cost = http_call(url, body=body_str, headers=headers)
    res.http, res.latency_ms = status, cost
    res.trace_id = resp_headers.get("X-Trace-Id")
    res.content_final = content
    res.model_final = case.get("model") or (registry[case["ref"]].model_final if case.get("ref") else None)
    res.max_tokens_final = max_tokens

    # ---- 响应解析（路由明细提取） ----
    meta = None
    if stream and status == 200 and case.get("cat") != "legacy":
        frames, meta, sse_model = parse_sse(text)
        res.sse_frames = frames
        res.backend = sse_model
    else:
        try:
            obj = json.loads(text)
            if case.get("cat") == "legacy":
                res.legacy_code = obj.get("code")
                meta = None
            else:
                res.backend = obj.get("model")
                meta = obj.get("x-mas-meta")
                err = obj.get("error")
                if isinstance(err, dict):
                    res.err_code, res.err_type = err.get("code"), err.get("type")
        except Exception:
            pass
    if meta:
        res.intent = meta.get("intent")
        res.difficulty = meta.get("difficulty")
        res.cache_hit = meta.get("cache_hit")
        res.cache_level = meta.get("cache_level")
        res.routed_to = meta.get("routed_to")

    evaluate(res, exp, text)
    registry[case["id"]] = res
    return res


def parse_surefire(report_dir):
    suites = []
    for f in sorted(glob.glob(os.path.join(report_dir, "TEST-*.xml"))):
        try:
            root = ET.parse(f).getroot()
        except Exception:
            continue
        cases = []
        for tc in root.iter("testcase"):
            failed = tc.find("failure") is not None or tc.find("error") is not None
            cases.append((tc.get("classname", "").split(".")[-1], tc.get("name", ""),
                          float(tc.get("time", 0)), failed))
        suites.append((root.get("name", f), cases))
    return suites


def md_escape(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


def write_report(path, results, suites, ctx):
    total = len(results)
    passed = sum(1 for r in results if r.passed)
    cats = {}
    for r in results:
        c = cats.setdefault(r.case["cat"], [0, 0])
        c[0] += 1
        if r.passed:
            c[1] += 1
    unit_total = sum(len(cs) for _, cs in suites)
    unit_pass = sum(1 for _, cs in suites for t in cs if not t[3])

    lines = []
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    lines.append("# MAS 原型测试报告")
    lines.append("")
    lines.append(f"- **生成时间**：{now}")
    lines.append(f"- **应用地址**：{ctx['base']}")
    lines.append("- **运行隔离**：套件开始前经 /internal/cache/flush 清空两级缓存（含本地 Caffeine 层）；对话类用例拼接按 (model, prompt) 哈希派生的确定性隔离前缀（档案号 1e14 空间防碰撞）；断言 intent/difficulty 的用例使用原始 prompt；ref 用例继承前置用例的 max_tokens（精确缓存键组成部分）；断言缓存命中的用例请求前等待 1s 消除异步写竞态；串行请求限速 0.12s 避免误触限流")
    lines.append(f"- **后端模型**：complex 档 → gpt-oss:20b，simple 档 → qwen2.5:0.5b，embedding → bge-m3（均为本机 Ollama 真实推理）")
    lines.append("")
    lines.append("## 一、总体统计")
    lines.append("")
    lines.append("| 测试类型 | 用例数 | 通过 | 失败 | 通过率 |")
    lines.append("| --- | --- | --- | --- | --- |")
    lines.append(f"| 黑盒功能用例（cases.json 数据驱动） | {total} | {passed} | {total - passed} | {passed / total * 100:.1f}% |")
    if suites:
        lines.append(f"| 单元测试（JUnit） | {unit_total} | {unit_pass} | {unit_total - unit_pass} | {unit_pass / unit_total * 100:.1f}% |")
        lines.append(f"| **合计** | **{total + unit_total}** | **{passed + unit_pass}** | **{total - passed + unit_total - unit_pass}** | {(passed + unit_pass) / (total + unit_total) * 100:.1f}% |")
    lines.append("")
    lines.append("### 分类统计")
    lines.append("")
    lines.append("| 分类 | 说明 | 用例数 | 通过 | 失败 |")
    lines.append("| --- | --- | --- | --- | --- |")
    cat_desc = {"system": "系统端点（健康/指标/模型列表/trace）", "embedding": "嵌入服务",
                "difficulty": "难度路由（无显式 model 自动分流）", "explicit": "显式模型路由",
                "error": "错误与拦截（404/400/403）", "legacy": "旧协议兼容层",
                "cache": "两级缓存行为", "stream": "流式 SSE", "rate": "限流"}
    for cat, (n, p) in cats.items():
        lines.append(f"| {cat} | {cat_desc.get(cat, '')} | {n} | {p} | {n - p} |")
    lines.append("")
    lines.append("## 二、黑盒用例路由明细")
    lines.append("")
    lines.append("路由明细字段：intent（L3 路由档位）、difficulty（难度评分）、后端模型（引擎实际响应的 model）、缓存（命中级别）、耗时。")
    lines.append("缓存命中的用例按设计短路返回，不经过 L3，故无 intent/difficulty 字段（— 表示）。")
    lines.append("")
    lines.append("| 用例ID | 分类 | 输入摘要 | HTTP | intent | difficulty | 后端模型 | 缓存 | 耗时(ms) | 结论 |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in results:
        c = r.case
        summary = c.get("prompt") or c.get("raw") or c.get("path") or c.get("type") or ""
        if summary and c.get("raw") is not None and "prompt" not in c:
            summary = f"[raw]{summary}"
        cache = "—"
        if r.cache_hit is True:
            cache = f"hit:{r.cache_level}"
        elif r.cache_hit is False:
            cache = "miss"
        verdict = "✅ PASS" if r.passed else "❌ FAIL"
        lines.append(f"| {c['id']} | {c['cat']} | {md_escape(summary[:28])} | {r.http} | "
                     f"{r.intent or '—'} | {r.difficulty if r.difficulty is not None else '—'} | "
                     f"{r.backend or '—'} | {cache} | {r.latency_ms} | {verdict} |")
    lines.append("")
    fails = [r for r in results if not r.passed]
    if fails:
        lines.append("## 三、失败用例断言明细")
        lines.append("")
        for r in fails:
            lines.append(f"### {r.case['id']}")
            for ok, desc in r.checks:
                if not ok:
                    lines.append(f"- ❌ {desc}")
            lines.append("")
    else:
        lines.append("## 三、失败用例断言明细")
        lines.append("")
        lines.append("无失败用例。")
        lines.append("")
    if suites:
        lines.append("## 四、单元测试明细（JUnit）")
        lines.append("")
        for name, cs in suites:
            short = name.split(".")[-1]
            lines.append(f"### {short}（{sum(1 for t in cs if not t[3])}/{len(cs)}）")
            lines.append("")
            lines.append("| 用例 | 耗时(s) | 结论 |")
            lines.append("| --- | --- | --- |")
            for cls, tname, t, failed in cs:
                lines.append(f"| {tname} | {t:.3f} | {'✅ PASS' if not failed else '❌ FAIL'} |")
            lines.append("")
    lines.append("## 五、说明")
    lines.append("")
    lines.append("- 难度路由契约：评分 ≥ 阈值（0.5）→ complex 档（qwen-72b → gpt-oss:20b），否则 simple 档（qwen-lite → qwen2.5:0.5b）；显式 model 字段优先级最高。")
    lines.append("- 缓存契约：命中即短路（响应 meta 无 intent/difficulty）；精确缓存键含 model/messages/temperature/max_tokens，stream 排除在外（CH-07/CH-08 验证）。")
    lines.append("- 语义缓存按 model_id 隔离（CH-05/CH-06 验证），余弦阈值 0.95。")
    lines.append("- 限流用例按 burst 参数并发突发（超限 40 / 等水位 20 / 低水位 5），每例独立用户避免桶状态串扰（RATE-*）。")
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return total, passed, unit_total, unit_pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=os.environ.get("MAS_BASE", "http://localhost:9090/smart-router"))
    ap.add_argument("--cases", default=os.path.join(os.path.dirname(__file__), "cases.json"))
    ap.add_argument("--report", default=os.path.join(os.path.dirname(__file__), "..", "reports", "test-report.md"))
    ap.add_argument("--surefire", default=os.path.join(os.path.dirname(__file__), "..", "target", "surefire-reports"))
    args = ap.parse_args()

    status, _, _, _ = http_call(args.base + "/actuator/health", method="GET", timeout=10)
    if status != 200:
        print(f"应用不可达：{args.base}（HTTP {status}），请先启动应用")
        return 2

    with open(args.cases, encoding="utf-8") as f:
        cases = json.load(f)
    truncate_caches(args.base)
    ctx = {"base": args.base, "registry": {}}
    print(f"加载用例 {len(cases)} 条")

    results = []
    for i, case in enumerate(cases, 1):
        res = run_case(case, ctx)
        results.append(res)
        mark = "PASS" if res.passed else "FAIL"
        detail = f"intent={res.intent or '-'} diff={res.difficulty if res.difficulty is not None else '-'} backend={res.backend or '-'}"
        print(f"[{i:3d}/{len(cases)}] {mark} {case['id']:<8} {res.http} {res.latency_ms}ms {detail}")

    suites = parse_surefire(args.surefire)
    total, passed, unit_total, unit_pass = write_report(args.report, results, suites, ctx)
    print("=" * 60)
    print(f"黑盒用例：{passed}/{total} 通过；单元测试：{unit_pass}/{unit_total} 通过")
    print(f"报告已写入：{os.path.abspath(args.report)}")
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
