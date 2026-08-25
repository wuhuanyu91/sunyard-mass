#!/usr/bin/env python3
"""
cases.json 用例生成器：9 个类别各 100 条（共 900 条）黑盒用例。

生成期校验（不满足即报错退出）：
1) 难度断言用例按 Java RuleDifficultyClassifier 的复刻规则评分，必须落在断言区间；
2) 难度断言用例使用原始 prompt（执行器不加隔离前缀），同一路由档位内两两
   bge-m3 相似度必须 < 0.93，防止套件内语义缓存误命中；
3) 语义缓存命中对（cache 类）在共享隔离前缀下的相似度必须 >= 0.96（阈值 0.95）。

依赖：本机 Ollama bge-m3（仅校验阶段调用 embedding）。
用法：python3 tests/gen_cases.py [--skip-emb-check]
"""
import argparse
import hashlib
import json
import math
import os
import re
import sys
import urllib.request

EMB_URL = "http://localhost:11434/v1/embeddings"
BAG = ["墨水", "潮汐", "青苔", "齿轮", "青铜", "星尘", "帆影", "烛台", "砂岩", "雾松"]

# ---------------------------------------------------------------- 难度评分复刻
CK = ["证明", "推导", "设计方案", "架构", "对比分析", "评估", "权衡", "优化", "重构", "原理",
      "定理", "算法", "规划", "多步骤", "逐步", "深入分析", "详细分析", "全面", "实现一个",
      "设计一个", "写一篇"]
SK = ["是什么", "什么是", "多少钱", "几点", "翻译", "总结一下", "是谁", "在哪"]


def score(t):
    """与 RuleDifficultyClassifier.score 完全一致的 Python 复刻"""
    if not t or not t.strip():
        return 0.0
    lo = t.lower()
    s = 0.0
    n = len(t)
    s += 0.5 if n > 300 else 0.3 if n > 100 else 0.1 if n > 40 else 0.0
    s += min(0.2 * sum(1 for k in CK if k in lo), 0.6)
    if len(t.split("\n")) >= 3 or re.search(r'(?s)([0-9]+[.、)]|①|②|- )', lo):
        s += 0.15
    if "```" in t or "function " in lo or "class " in lo:
        s += 0.15
    if n <= 40 and any(k in lo for k in SK):
        s -= 0.2
    return max(0.0, min(1.0, s))


def gen_prefix(key):
    """与执行器 run_cases.py 的 gen_prefix 完全一致"""
    h = int(hashlib.sha1(key.encode("utf-8")).hexdigest(), 16)
    return f"{BAG[h % 10]}与{BAG[(h // 7) % 10]}的关联是什么？档案{h % 1000000}。"


# ---------------------------------------------------------------- embedding 工具
def embed_batch(texts):
    out = []
    for i in range(0, len(texts), 32):
        chunk = texts[i:i + 32]
        req = urllib.request.Request(
            EMB_URL, data=json.dumps({"model": "bge-m3", "input": chunk}).encode(),
            headers={"Content-Type": "application/json"})
        resp = json.load(urllib.request.urlopen(req, timeout=300))
        out.extend(d["embedding"] for d in resp["data"])
    return out


def cos(a, b):
    return sum(x * y for x, y in zip(a, b)) / (
        math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))


# ---------------------------------------------------------------- 提示词数据
SIMPLE_QUESTIONS = [
    "今天天气怎么样？", "北京是哪个国家的首都？", "一周有几天？", "水的化学式是什么？",
    "是谁发明了电灯泡？", "现在几点了？", "你好，介绍一下你自己", "明天是星期几？",
    "一年有多少个月？", "中国的首都在哪里？", "这本书多少钱？", "帮我翻译这句话：good morning",
    "地球是圆的吗？", "猫的英文怎么说？", "1加1等于几？", "你喜欢什么颜色？",
    "怎么打电话给客服？", "米饭的热量是多少？", "上海在北京的哪个方向？", "今天限行尾号是多少？",
    "太阳从哪边升起？", "一年有几个季节？", "彩虹有几种颜色？", "最大的海洋是哪个？",
    "熊猫主要吃什么？", "长城在哪个国家？", "一天有多少小时？", "冰是什么状态的？",
    "自行车有几个轮子？", "春节是几月份？", "法国的首都是哪里？", "日本的货币叫什么？",
    "珠穆朗玛峰高吗？", "企鹅会飞吗？", "牛奶是什么颜色的？", "星期天的后一天是星期几？",
    "汉字是谁发明的？", "端午节吃什么？", "飞机在哪里起降？", "医生在哪里工作？",
    "夏天热还是冬天热？", "鱼生活在哪里？", "月亮会发光吗？", "一小时有多少分钟？",
    "三角形的边数是多少？", "谁写了《静夜思》？", "巧克力的原料是什么？", "足球几个人踢？",
    "地球有几颗卫星？", "早餐一般吃什么？",
]

C_VERBS = ["请证明", "请推导", "请设计", "请重构", "请深入分析",
           "请对比分析", "请全面评估", "实现一个", "设计一个", "写一篇"]
C_TOPICS = ["分布式缓存架构", "微服务拆分方案", "数据库索引算法", "消息队列可靠性", "限流降级策略",
            "推荐系统召回", "编译器词法分析", "垃圾回收调优", "并发容器原理", "分布式事务模式"]
C_TAILS = ["，逐步给出多步骤方案。", "，对比分析权衡并给出设计方案。", "，推导原理并给出优化方案。",
           "，给出架构评估结论与规划。", "，深入分析原理并规划落地。"]

MALFORMED_BODIES = [
    "not-json", "{broken:}", "[1,2,3]", "{'single': 'quote'}", "{\"unterminated\": ",
    "12345", "true", "\"just-a-string\"", "{,}", "[}",
    "{\"messages\": null}", "{\"messages\": {}}", "{\"messages\": \"text\"}",
    "{\"model\": }", "null",
    "{\"messages\": [], \"model\": \"qwen-lite\"}", "[]",
    "  {invalid leading spaces", "{\"stream\": notbool}", "key: value",
]
FAKE_MODELS = [
    "gpt-99b", "claude-opus-x", "llama-405b", "gemini-ultra-2", "mistral-9000",
    "未注册模型甲", "不存在的模型乙", "no-such-model-01", "no-such-model-02", "phantom-llm",
    "qwen-0.1b", "deepseek-999b", "chatgpt-4o-mini-fake", "baichuan-max", "yi-lightning-x",
    "abc", "x", "12345", "-leading-dash", "model with space",
    "UPPER-CASE-MODEL", "emoji模型🚀", "very-long-model-id-" + "x" * 60, "sql'; drop", "../etc/passwd",
]

SEM_PAIRS = [(a, b) for a in range(2, 12) for b in range(1, a)][:10]  # (a,b) → a+b 算术对


def build_cases():
    cases = []
    seq = {}

    def nid(prefix):
        seq[prefix] = seq.get(prefix, 0) + 1
        return f"{prefix}-{seq[prefix]:03d}"

    # ---- system：10 健康 + 30 指标 + 30 模型列表 + 30 trace ----
    for _ in range(10):
        cases.append({"id": nid("SYS"), "cat": "system", "path": "/actuator/health",
                      "method": "GET", "expect": {"http": 200, "contains": ["\"status\":\"UP\""]}})
    metrics = ["jvm_memory_used_bytes", "jvm_memory_committed_bytes", "jvm_threads_live_threads",
               "jvm_threads_daemon_threads", "jvm_classes_loaded_classes", "process_cpu_usage",
               "process_uptime_seconds", "system_cpu_count", "http_server_requests_seconds_count",
               "http_server_requests_seconds_sum"]
    for i in range(30):
        cases.append({"id": nid("SYS"), "cat": "system", "path": "/actuator/prometheus",
                      "method": "GET", "expect": {"http": 200, "contains": [metrics[i % 10]]}})
    model_asserts = [
        {"contains": ["\"id\":\"qwen-72b\""]}, {"contains": ["\"id\":\"qwen-lite\""]},
        {"contains": ["\"object\":\"list\""]}, {"contains": ["\"id\":\"qwen-72b\"", "\"id\":\"qwen-lite\""]},
        {"excludes": ["\"id\":\"bge-m3\""]}, {"contains": ["\"data\""]},
    ]
    for i in range(30):
        cases.append({"id": nid("SYS"), "cat": "system", "path": "/v1/models",
                      "method": "GET", "expect": {"http": 200, **model_asserts[i % 6]}})
    for i in range(30):
        cases.append({"id": nid("SYS"), "cat": "system", "model": "qwen-lite",
                      "prompt": f"链路追踪用例：请用一个词描述序号{i}的测试", "max_tokens": 8,
                      "expect": {"http": 200, "trace_len": 32}})

    # ---- embedding：100 条多样文本 ----
    emb_texts = []
    for q in SIMPLE_QUESTIONS[:30]:
        emb_texts.append(f"嵌入用例：{q}")
    for i in range(20):
        emb_texts.append(f"Embedding case {i}: the quick brown fox number {i}.")
    for i in range(15):
        emb_texts.append(f"中英混合嵌入用例 mixed text {i} 号")
    for i in range(10):
        emb_texts.append(f"数字与符号序列 {i} {i*7} {i*13} + - × ÷ = ≠")
    for i in range(10):
        emb_texts.append(f"代码嵌入用例：def f{i}(x): return x * {i}")
    for i in range(15):
        emb_texts.append(f"长文本嵌入用例第{i}段。数据中台建设需要统一指标口径、血缘追踪与质量稽核，"
                         f"本段用于验证第{i}组输入的嵌入稳定性，不包含路由决策语义。")
    for t in emb_texts:
        cases.append({"id": nid("EMB"), "cat": "embedding", "prompt": t,
                      "expect": {"http": 200, "embedding_dim": 1024}})

    # ---- difficulty：50 simple + 50 complex（原始 prompt，不加前缀） ----
    for q in SIMPLE_QUESTIONS:
        cases.append({"id": nid("DS"), "cat": "difficulty", "prompt": q,
                      "expect": {"http": 200, "intent": "simple", "difficulty_lt": 0.5,
                                 "backend": "qwen2.5:0.5b"}})
    for i in range(50):
        p = f"{C_VERBS[i % 10]}{C_TOPICS[(i * 3 + i // 10) % 10]}{C_TAILS[i % 5]}第{i}组"
        cases.append({"id": nid("DC"), "cat": "difficulty", "prompt": p,
                      "expect": {"http": 200, "intent": "complex", "difficulty_ge": 0.5,
                                 "backend": "gpt-oss:20b"}})

    # ---- explicit：50 lite + 50 72b（运行期加隔离前缀） ----
    for i in range(50):
        cases.append({"id": nid("EXP"), "cat": "explicit", "model": "qwen-lite",
                      "prompt": f"显式小模型用例{i}：{SIMPLE_QUESTIONS[i]}",
                      "expect": {"http": 200, "intent": "simple", "backend": "qwen2.5:0.5b"}})
    for i in range(50):
        cases.append({"id": nid("EXP"), "cat": "explicit", "model": "qwen-72b",
                      "prompt": f"显式大模型用例{i}：{SIMPLE_QUESTIONS[(i + 17) % 50]}",
                      "expect": {"http": 200, "intent": "complex", "backend": "gpt-oss:20b"}})

    # ---- error：25 未注册模型 + 20 非法报文 + 15 黑名单 + 25 敏感词 + 15 流式敏感词 ----
    for m in FAKE_MODELS:
        cases.append({"id": nid("ERR"), "cat": "error", "model": m, "prompt": "未注册模型用例",
                      "expect": {"http": 404, "err_code": "model_not_found",
                                 "err_type": "invalid_request_error"}})
    for body in MALFORMED_BODIES:
        exp = {"http": 400}
        if body:  # 空 body 走 ServerWebInputException，仅断言状态码
            exp["err_code"] = "invalid_param"
        cases.append({"id": nid("ERR"), "cat": "error", "raw": body, "expect": exp})
    for i in range(15):
        c = {"id": nid("ERR"), "cat": "error", "user": "banned-user",
             "prompt": f"黑名单用户请求{i}",
             "expect": {"http": 403, "err_code": "blacklisted", "err_type": "permission_error"}}
        if i % 3 == 1:
            c["model"] = "qwen-lite"
        elif i % 3 == 2:
            c["model"] = "qwen-72b"
        cases.append(c)
    for i in range(25):
        word = "测试敏感词" if i % 2 == 0 else "违法违规"
        filler = f"普通内容第{i}条"
        p = (word + filler) if i % 3 == 0 else (filler + word) if i % 3 == 1 else (filler + word + filler)
        cases.append({"id": nid("ERR"), "cat": "error", "prompt": p,
                      "expect": {"http": 400, "err_code": "input_blocked",
                                 "err_type": "content_filter"}})
    for i in range(15):
        word = "测试敏感词" if i % 2 == 0 else "违法违规"
        cases.append({"id": nid("ERR"), "cat": "error", "stream": True,
                      "prompt": f"流式请求第{i}条包含{word}",
                      "expect": {"http": 400, "err_code": "input_blocked"}})

    # ---- legacy：30 lite + 30 72b + 20 默认路由 + 5 maxTokens + 15 错误 ----
    for i in range(30):
        cases.append({"id": nid("LEG"), "cat": "legacy", "model": "qwen-lite",
                      "prompt": f"旧协议小模型用例{i}",
                      "expect": {"http": 200, "legacy_code": 0, "legacy_content": True}})
    for i in range(30):
        cases.append({"id": nid("LEG"), "cat": "legacy", "model": "qwen-72b",
                      "prompt": f"旧协议大模型用例{i}",
                      "expect": {"http": 200, "legacy_code": 0, "legacy_content": True}})
    for i in range(20):
        cases.append({"id": nid("LEG"), "cat": "legacy", "prompt": f"旧协议默认路由用例{i}",
                      "expect": {"http": 200, "legacy_code": 0, "legacy_content": True}})
    for i in range(5):
        cases.append({"id": nid("LEG"), "cat": "legacy", "model": "qwen-lite",
                      "prompt": f"旧协议maxTokens映射用例{i}", "max_tokens": 8,
                      "expect": {"http": 200, "legacy_code": 0}})
    for i in range(5):
        cases.append({"id": nid("LEG"), "cat": "legacy", "model": f"not-exist-{i}",
                      "prompt": "旧协议未注册模型",
                      "expect": {"http": 404, "legacy_code": -1}})
    for body in ["legacy-not-json", "{bad", "]", "42", "legacy\t\n bad"]:
        cases.append({"id": nid("LEG"), "cat": "legacy", "raw": body,
                      "expect": {"http": 400, "legacy_code": -1}})
    for i in range(5):
        cases.append({"id": nid("LEG"), "cat": "legacy", "user": "banned-user",
                      "model": "qwen-lite", "prompt": f"旧协议黑名单{i}",
                      "expect": {"http": 403, "legacy_code": -1}})

    # ---- cache：25 精确对 + 10 语义对 + 10 模型隔离对 + 5 流式写入对 ----
    for i in range(25):
        w = {"id": nid("CH"), "cat": "cache", "model": "qwen-lite",
             "prompt": f"精确缓存写入用例{i}：序号{i}的测试问题是什么？", "max_tokens": 8,
             "expect": {"http": 200}}
        h = {"id": nid("CH"), "cat": "cache", "ref": w["id"],
             "expect": {"http": 200, "cache_hit": True, "cache_level": "exact"}}
        cases += [w, h]
    for i, (a, b) in enumerate(SEM_PAIRS):
        grp = f"sem-pair-{i}"
        w = {"id": nid("CH"), "cat": "cache", "model": "qwen-72b", "nonce_group": grp,
             "prompt": f"语义缓存基准问法{i}：{a}+{b}等于几？", "max_tokens": 8,
             "expect": {"http": 200}}
        h = {"id": nid("CH"), "cat": "cache", "model": "qwen-72b", "nonce_group": grp,
             "prompt": f"语义缓存同义问法{i}：{a}+{b}是等于几？",
             "expect": {"http": 200, "cache_hit": True, "cache_level": "semantic"}}
        cases += [w, h]
    for i in range(10):
        w = {"id": nid("CH"), "cat": "cache", "model": "qwen-72b",
             "prompt": f"跨模型隔离用例{i}：编号{i}的隔离验证问题", "max_tokens": 8,
             "expect": {"http": 200}}
        h = {"id": nid("CH"), "cat": "cache", "ref": w["id"], "model": "qwen-lite",
             "expect": {"http": 200, "cache_hit": False}}
        cases += [w, h]
    for i in range(5):
        w = {"id": nid("CH"), "cat": "cache", "model": "qwen-lite", "stream": True,
             "prompt": f"流式写缓存用例{i}：序号{i}的流式问题", "max_tokens": 8,
             "expect": {"http": 200, "sse": True}}
        h = {"id": nid("CH"), "cat": "cache", "ref": w["id"], "model": "qwen-lite",
             "expect": {"http": 200, "cache_hit": True, "cache_level": "exact"}}
        cases += [w, h]

    # ---- stream：25 simple + 25 complex + 20 显式 + 20 帧序列 + 5 敏感词 + 5 黑名单 ----
    st_simple = [f"流式简单问句{i}：{SIMPLE_QUESTIONS[i]}" for i in range(25)]
    for p in st_simple:
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True, "prompt": p,
                      "max_tokens": 8,
                      "expect": {"http": 200, "sse": True, "intent": "simple"}})
    for i in range(25):
        p = f"流式复杂任务{i}：{C_VERBS[i % 10]}{C_TOPICS[(i * 3 + i // 10) % 10]}{C_TAILS[i % 5]}第{i}批"
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True, "prompt": p,
                      "max_tokens": 8,
                      "expect": {"http": 200, "sse": True, "intent": "complex"}})
    for i in range(10):
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True, "model": "qwen-lite",
                      "prompt": f"流式显式小模型用例{i}", "max_tokens": 8,
                      "expect": {"http": 200, "sse": True, "intent": "simple",
                                 "backend": "qwen2.5:0.5b"}})
    for i in range(10):
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True, "model": "qwen-72b",
                      "prompt": f"流式显式大模型用例{i}", "max_tokens": 8,
                      "expect": {"http": 200, "sse": True, "intent": "complex",
                                 "backend": "gpt-oss:20b"}})
    for i in range(20):
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True,
                      "prompt": f"流式帧序列用例{i}：请简短回答序号{i}的问题", "max_tokens": 8,
                      "expect": {"http": 200, "sse": True}})
    for i in range(5):
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True,
                      "prompt": f"流式敏感词拦截{i}：内容包含测试敏感词",
                      "expect": {"http": 400, "err_code": "input_blocked"}})
    for i in range(5):
        cases.append({"id": nid("ST"), "cat": "stream", "stream": True, "user": "banned-user",
                      "prompt": f"流式黑名单拦截{i}",
                      "expect": {"http": 403, "err_code": "blacklisted"}})

    # ---- rate：40 超限突发 + 40 低水位 + 20 等水位 ----
    for i in range(40):
        cases.append({"id": nid("RATE"), "cat": "rate", "type": "rate", "model": "qwen-lite",
                      "user": f"rate-over-{i:03d}", "burst": 40,
                      "prompt": f"限流超限突发用例{i}", "expect": {"rate429_ge": 1}})
    for i in range(40):
        cases.append({"id": nid("RATE"), "cat": "rate", "type": "rate", "model": "qwen-lite",
                      "user": f"rate-under-{i:03d}", "burst": 5,
                      "prompt": f"限流低水位用例{i}", "expect": {"rate429_lt": 1}})
    for i in range(20):
        cases.append({"id": nid("RATE"), "cat": "rate", "type": "rate", "model": "qwen-lite",
                      "user": f"rate-edge-{i:03d}", "burst": 20,
                      "prompt": f"限流等水位用例{i}", "expect": {"rate429_lt": 3}})
    return cases


def validate(cases, skip_emb=False):
    # 1) 难度评分区间校验
    for c in cases:
        exp = c.get("expect", {})
        p = c.get("prompt")
        if not p or c.get("cat") != "difficulty":
            continue
        s = score(p)
        if "difficulty_lt" in exp and not s < exp["difficulty_lt"]:
            sys.exit(f"评分校验失败：{c['id']} score={s} 不满足 <{exp['difficulty_lt']}：{p}")
        if "difficulty_ge" in exp and not s >= exp["difficulty_ge"]:
            sys.exit(f"评分校验失败：{c['id']} score={s} 不满足 >={exp['difficulty_ge']}：{p}")
    print("难度评分区间校验通过")
    if skip_emb:
        print("已跳过 embedding 相似度校验（--skip-emb-check）")
        return

    # 2) 难度用例同档位两两相似度 < 0.93（原始 prompt，无隔离前缀）
    for cat, bucket in (("DS", "qwen-lite"), ("DC", "qwen-72b")):
        texts = [c["prompt"] for c in cases if c["id"].startswith(cat)]
        vecs = embed_batch(texts)
        worst, wi, wj = 0.0, -1, -1
        for i in range(len(vecs)):
            for j in range(i + 1, len(vecs)):
                s = cos(vecs[i], vecs[j])
                if s > worst:
                    worst, wi, wj = s, i, j
        print(f"{cat}（{bucket} 档）{len(texts)} 条两两最大相似度 {worst:.4f}")
        if worst >= 0.93:
            sys.exit(f"相似度校验失败：{texts[wi]} ↔ {texts[wj]} = {worst:.4f} >= 0.93")

    # 3) 语义缓存对：共享前缀下相似度 >= 0.96
    pairs = {}
    for c in cases:
        if c.get("nonce_group"):
            pairs.setdefault(c["nonce_group"], []).append(c)
    for grp, cs in pairs.items():
        pfx = gen_prefix(grp)
        t0, t1 = (pfx + " " + cs[0]["prompt"], pfx + " " + cs[1]["prompt"])
        v0, v1 = embed_batch([t0, t1])
        s = cos(v0, v1)
        print(f"语义对 {grp}：{s:.4f}")
        if s < 0.96:
            sys.exit(f"语义对校验失败：{grp} = {s:.4f} < 0.96")
    print("embedding 相似度校验全部通过")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-emb-check", action="store_true",
                    help="跳过 bge-m3 相似度校验（无 Ollama 时使用）")
    args = ap.parse_args()

    cases = build_cases()
    from collections import Counter
    dist = Counter(c["cat"] for c in cases)
    print(f"生成用例 {len(cases)} 条：{dict(dist)}")
    for cat, n in dist.items():
        assert n >= 100, f"类别 {cat} 仅 {n} 条，不足 100"
    ids = [c["id"] for c in cases]
    assert len(ids) == len(set(ids)), "用例 ID 重复"

    validate(cases, args.skip_emb_check)

    out = os.path.join(os.path.dirname(__file__), "cases.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(cases, f, ensure_ascii=False, indent=1)
    print(f"已写入 {os.path.abspath(out)}")


if __name__ == "__main__":
    main()
