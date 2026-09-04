#!/usr/bin/env python3
"""
为 cases.json 注入真实业务场景 prompt，替换原有合成数据。
保留所有结构性字段（id, cat, expect, ref, model, nonce_group 等），
仅替换 prompt 字段（以及 error 类的 model 字段保持不动）。

真实场景覆盖 4 大应用：
- APP-CSR 智能客服（银行/零售）
- APP-AICODING AI代码助手
- APP-CREDIT 信贷审批
- APP-RISK 风控报告
"""

import json
import copy
import random

INPUT = "/Users/yizheliu/working/dev/Metis‑Infer/smart-model-router/tests/cases.json"
OUTPUT = "/Users/yizheliu/working/dev/Metis‑Infer/smart-model-router/tests/cases.json"

# ============================================================
# 真实业务 Prompt 库
# ============================================================

# --- 简单问答 (simple intent, difficulty < 0.5) ---
SIMPLE_PROMPTS = [
    # 智能客服
    "如何修改银行卡密码？",
    "信用卡年费是多少？",
    "转账限额是多少？",
    "如何开通手机银行？",
    "信用卡逾期还款有什么后果？",
    "如何查询征信报告？",
    "借记卡和贷记卡有什么区别？",
    "如何办理存款证明？",
    "手机支付忘记密码怎么办？",
    "如何申请提高信用卡额度？",
    "外币兑换汇率怎么查？",
    "如何挂失银行卡？",
    "公积金提取需要什么材料？",
    "理财产品赎回多久到账？",
    "如何开通短信通知服务？",
    "个人贷款需要什么条件？",
    "信用卡积分怎么兑换？",
    "如何修改预留手机号？",
    "网银转账手续费多少？",
    "如何查询账户余额？",
    # 通用简单
    "今天星期几？",
    "一年有几个季度？",
    "一公里等于多少米？",
    "水的沸点是多少度？",
    "中国有多少个省份？",
    "一周有几天工作日？",
    "一小时有多少分钟？",
    "一个季度有几个月？",
    "一吨等于多少公斤？",
    "一升等于多少毫升？",
]

# --- 复杂推理 (complex intent, difficulty >= 0.5) ---
COMPLEX_PROMPTS = [
    # AI代码助手
    "用 Python 实现一个 LRU 缓存，要求 get 和 put 操作都是 O(1) 时间复杂度，并解释设计思路",
    "解释这段 SQL 的执行计划：SELECT * FROM orders WHERE user_id IN (SELECT id FROM users WHERE status='active') ORDER BY created_at DESC LIMIT 100",
    "用 Java 实现生产者消费者模式，使用 ReentrantLock 和 Condition，要求支持优雅关闭",
    "设计一个分布式 ID 生成器，参考 Snowflake 算法，要求支持时钟回拨处理",
    "解释 Kubernetes 中 Deployment 和 StatefulSet 的区别，分别适用于什么场景，给出选型建议",
    "用 Go 编写一个 HTTP 反向代理，支持轮询和加权轮询两种负载均衡策略",
    "分析这段代码的性能瓶颈：一个嵌套循环遍历 10 万条记录做数据库查询，给出优化方案",
    "用 Rust 实现一个简单的键值存储引擎，支持 put/get/delete 和持久化",
    "解释 React 中 useEffect 的依赖数组原理，为什么缺少依赖会导致闭包陷阱",
    "设计一个高并发秒杀系统，要求处理超卖、限流、防刷，给出架构图和核心代码",
    # 信贷审批
    "请分析以下企业的信贷风险状况：该企业为制造业中型企业，年营收 5.2 亿元，资产负债率 62%，近三年营收增长率分别为 8%、5%、3%。企业主要客户为 3 家大型房地产公司，应收账款周转天数 95 天。企业实际控制人名下另有 2 家关联企业，其中 1 家存在诉讼记录。请综合评估该企业的信用风险等级，给出授信建议。",
    "某科技公司申请流动资金贷款 2000 万元，期限 1 年。该企业成立于 2015 年，主营 SaaS 软件开发，年营收 1.8 亿元，净利润 2800 万元。目前有 3 家银行授信合计 5000 万元，已用额度 3200 万元。企业拥有 12 项软件著作权，无抵押物。请分析该企业的还款能力和风险因素。",
    "评估一笔个人住房按揭贷款申请：借款人 35 岁，月收入 2.5 万元，现有房贷月供 8000 元，车贷月供 3500 元。本次申请贷款 280 万元，期限 25 年，月供约 1.55 万元。借款人在该行有 50 万元定期存款，征信记录良好，近 2 年无逾期。请判断是否符合审批条件。",
    "某城投平台申请项目贷款 3 亿元，用于城市基础设施建设。该平台总资产 120 亿元，总负债 78 亿元，所有者权益 42 亿元。近三年政府购买服务收入分别为 4.5 亿、4.8 亿、5.1 亿元。平台有存量债务 58 亿元，其中债券 20 亿元、银行贷款 28 亿元、非标融资 10 亿元。请评估该平台的偿债能力和再融资风险。",
    # 风控报告
    "对以下交易进行风险评分：持卡人张某，卡号尾号 8821，今日凌晨 2:30 在境外线上商户消费 12,800 元，商户类别码(MCC)为 7995（虚拟货币交易）。该持卡人近 3 个月月均消费 8,000 元，无境外交易记录。请给出风险等级和处置建议。",
    "分析以下可疑交易模式：某企业账户在过去 2 小时内发生 47 笔转账，总金额 380 万元，收款方涉及 15 个不同账户，分布在 8 个省份。该企业日均交易笔数为 12 笔，日均金额 45 万元。请评估是否存在洗钱风险。",
    "评估以下信贷欺诈风险：申请人李某，身份证显示年龄 22 岁，申请信用卡额度 5 万元。申请人填写单位为某知名互联网公司，但电话号码归属地与该公司总部不一致。申请人征信报告显示近 6 个月有 4 次信用卡申请记录，均未激活。",
    "对以下保险理赔进行风险评估：投保人王某，投保重大疾病险 180 天，现申请甲状腺癌理赔，保额 50 万元。投保时健康告知全部选否。但核查发现投保前 3 个月有甲状腺结节超声检查记录。",
    "分析以下反欺诈规则触发情况：同一设备 ID 在 24 小时内关联了 8 个不同用户的登录，其中 5 个用户进行了转账操作，累计转出金额 23 万元。这些用户的注册时间集中在近 7 天，且注册时使用的手机号段连续。",
    # 通用复杂
    "比较 RESTful API 和 GraphQL 的优缺点，在什么场景下应该选择哪种方案",
    "解释 CAP 定理的含义，并举例说明在实际分布式系统中如何权衡",
    "设计一个支持千万级并发的消息队列系统，要求保证消息不丢失、不重复",
]

# --- Embedding 文本 ---
EMBEDDING_PROMPTS = [
    "银行卡密码修改流程和注意事项",
    "信用卡逾期还款对个人征信的影响说明",
    "Python 实现快速排序算法的代码示例",
    "企业信贷风险评估方法论和指标体系",
    "反洗钱交易监控规则和客户尽职调查流程",
    "分布式系统一致性协议 Raft 和 Paxos 对比",
    "机器学习模型特征工程最佳实践",
    "供应链金融风险管理框架和操作指引",
    "个人住房贷款审批流程和所需材料清单",
    "网络安全渗透测试方法论和工具介绍",
    "微服务架构设计原则和服务拆分策略",
    "自然语言处理中 Transformer 模型原理详解",
    "区块链智能合约开发语言 Solidity 入门指南",
    "数据库索引优化策略和慢查询分析方法",
    "容器编排平台 Kubernetes 核心概念解析",
    "金融衍生品定价模型 Black-Scholes 公式推导",
    "推荐系统协同过滤算法原理和工程实现",
    "云计算 IaaS PaaS SaaS 三种服务模式对比",
    "量子计算基本原理和量子比特操作门介绍",
    "自动驾驶感知融合算法和多传感器标定方法",
]

# --- 流式请求 ---
STREAM_PROMPTS = [
    "详细介绍长城的历史演变，从秦朝到明朝的修建过程",
    "如何制作一杯正宗的意式浓缩咖啡？请分步骤说明",
    "什么是人工智能？请从定义、发展历程、应用领域三个方面讲解",
    "讲解摄影的基本技巧，包括构图、光线、色彩三个维度",
    "介绍古希腊哲学的主要流派和代表人物",
    "如何系统学习一门外语？请给出从零基础到流利的学习路径",
    "什么是基因编辑技术 CRISPR-Cas9？请解释其原理和应用前景",
    "介绍世界著名的五大博物馆及其镇馆之宝",
    "请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解",
    "如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、熔断降级等方面说明",
    "介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工艺区别",
    "请详细说明 TCP 三次握手和四次挥手的过程，以及为什么需要 TIME_WAIT 状态",
    "如何设计一个支持百万并发的实时推送系统？请给出技术选型和架构设计",
    "介绍深度学习中的注意力机制（Attention），从 RNN attention 到 Transformer self-attention 的演进",
    "请解释区块链的工作原理，包括区块结构、共识机制、智能合约等核心概念",
]

# --- 缓存测试 ---
CACHE_WRITE_PROMPTS = [
    "请说明银行定期存款提前支取的利息计算规则，包括活期利率转换和实际天数计算方法",
    "解释 Python 中装饰器（decorator）的工作原理，给出带参数的装饰器示例",
    "什么是 JWT（JSON Web Token）？请说明其结构、签名验证流程和常见安全注意事项",
    "请介绍 Docker 容器和虚拟机的核心区别，包括资源隔离、启动速度、性能开销等方面",
    "解释数据库事务的 ACID 特性，并举例说明隔离级别从低到高的区别",
    "请说明 HTTPS 的握手过程，包括证书验证、密钥交换和对称加密的建立",
    "什么是 Redis 的持久化机制？请对比 RDB 快照和 AOF 日志的优缺点",
    "解释 Kubernetes 中 Pod、Deployment、Service 三者的关系和各自职责",
    "请介绍 OAuth 2.0 的四种授权模式，分别适用于什么场景",
    "什么是微服务中的 Saga 模式？请说明 Choreography 和 Orchestration 两种实现方式",
]

# --- 错误场景 ---
ERROR_PROMPTS = [
    "请帮我查询账户余额",
    "如何修改我的转账限额",
    "我想申请一张信用卡",
    "请解释一下贷款利率的计算方式",
    "如何开通网上银行功能",
    "我的银行卡被锁定了怎么办",
    "请帮我查询最近的交易记录",
    "如何设置自动还款功能",
    "我想了解理财产品的收益率",
    "如何办理外汇兑换业务",
]

# --- 限流场景 ---
RATE_PROMPTS = [
    "查询我的信用卡账单明细",
    "帮我计算这笔贷款的月供",
    "解释一下等额本息和等额本金的区别",
    "如何申请提高信用卡临时额度",
    "请说明个人征信报告的查询方式",
    "我想了解房贷利率的最新政策",
    "如何办理银行卡挂失补卡",
    "请解释一下存款保险制度",
    "如何开通国际漫游服务",
    "我想了解基金定投的策略",
]

# --- 旧协议场景 ---
LEGACY_PROMPTS = [
    "请帮我分析这份财务报表的关键指标",
    "解释一下资产负债表的构成和阅读方法",
    "如何评估一个企业的偿债能力",
    "请说明现金流量表中经营活动现金流的计算方法",
    "解释市盈率、市净率、市销率三个估值指标的含义和适用场景",
    "如何计算企业的加权平均资本成本（WACC）",
    "请介绍杜邦分析法的核心指标和分解逻辑",
    "解释一下信用利差和收益率曲线的关系",
    "如何评估债券投资的久期和凸性风险",
    "请说明期权定价中希腊字母 Delta、Gamma、Theta 的含义",
]

# --- 系统/链路追踪 ---
TRACE_PROMPTS = [
    "请用一句话概括当前请求的路由决策过程",
    "请描述本次请求经过了哪些处理阶段",
    "请说明本次请求最终被路由到哪个模型",
    "请简述缓存系统在本次请求中的命中情况",
    "请概括本次请求的意图识别结果",
]


# ============================================================
# 替换逻辑
# ============================================================

def replace_cases(cases):
    """按 category 替换 prompt，保留所有其他字段"""
    new_cases = []
    counters = {}

    for case in cases:
        cat = case.get("cat", "")
        cid = case.get("id", "")
        new_case = copy.deepcopy(case)

        if cat == "system":
            # system 类：健康检查、链路追踪
            if "actuator" in case.get("path", ""):
                pass  # 健康检查无 prompt，保持原样
            elif "trace" in cid.lower() or "链路" in case.get("prompt", ""):
                idx = counters.get("system_trace", 0)
                new_case["prompt"] = TRACE_PROMPTS[idx % len(TRACE_PROMPTS)]
                counters["system_trace"] = idx + 1
            else:
                idx = counters.get("system_other", 0)
                counters["system_other"] = idx + 1

        elif cat == "difficulty":
            # difficulty 类：根据 expect 中的 intent 选择 prompt
            expect = case.get("expect", {})
            intent = expect.get("intent", "")
            if intent == "simple":
                idx = counters.get("diff_simple", 0)
                new_case["prompt"] = SIMPLE_PROMPTS[idx % len(SIMPLE_PROMPTS)]
                counters["diff_simple"] = idx + 1
            elif intent == "complex":
                idx = counters.get("diff_complex", 0)
                new_case["prompt"] = COMPLEX_PROMPTS[idx % len(COMPLEX_PROMPTS)]
                counters["diff_complex"] = idx + 1
            else:
                # 没有明确 intent 的，交替使用
                idx = counters.get("diff_other", 0)
                if idx % 2 == 0:
                    new_case["prompt"] = SIMPLE_PROMPTS[idx % len(SIMPLE_PROMPTS)]
                else:
                    new_case["prompt"] = COMPLEX_PROMPTS[idx % len(COMPLEX_PROMPTS)]
                counters["diff_other"] = idx + 1

        elif cat == "embedding":
            idx = counters.get("embedding", 0)
            new_case["prompt"] = EMBEDDING_PROMPTS[idx % len(EMBEDDING_PROMPTS)]
            counters["embedding"] = idx + 1

        elif cat == "cache":
            # 缓存类：保持 ref 关系，替换写入 prompt
            if "ref" in case:
                pass  # 读取类保持原 prompt（由 ref 决定）
            else:
                idx = counters.get("cache_write", 0)
                new_case["prompt"] = CACHE_WRITE_PROMPTS[idx % len(CACHE_WRITE_PROMPTS)]
                counters["cache_write"] = idx + 1

        elif cat == "error":
            # 错误类：保持 model 和 expect 不变，替换 prompt
            idx = counters.get("error", 0)
            new_case["prompt"] = ERROR_PROMPTS[idx % len(ERROR_PROMPTS)]
            counters["error"] = idx + 1

        elif cat == "explicit":
            # 显式模型选择：根据 model 字段选择不同复杂度的 prompt
            model = case.get("model", "")
            idx = counters.get("explicit", 0)
            if "72b" in model or "gpt" in model:
                new_case["prompt"] = COMPLEX_PROMPTS[idx % len(COMPLEX_PROMPTS)]
            else:
                new_case["prompt"] = SIMPLE_PROMPTS[idx % len(SIMPLE_PROMPTS)]
            counters["explicit"] = idx + 1

        elif cat == "stream":
            idx = counters.get("stream", 0)
            new_case["prompt"] = STREAM_PROMPTS[idx % len(STREAM_PROMPTS)]
            counters["stream"] = idx + 1

        elif cat == "rate":
            idx = counters.get("rate", 0)
            new_case["prompt"] = RATE_PROMPTS[idx % len(RATE_PROMPTS)]
            counters["rate"] = idx + 1

        elif cat == "legacy":
            idx = counters.get("legacy", 0)
            new_case["prompt"] = LEGACY_PROMPTS[idx % len(LEGACY_PROMPTS)]
            counters["legacy"] = idx + 1

        new_cases.append(new_case)

    return new_cases


def main():
    with open(INPUT) as f:
        cases = json.load(f)

    print(f"原始用例数: {len(cases)}")

    # 统计原始 prompt 分布
    from collections import Counter
    orig_cats = Counter(c.get("cat", "?") for c in cases)
    print("原始分类分布:", dict(orig_cats))

    new_cases = replace_cases(cases)

    # 验证数量不变
    assert len(new_cases) == len(cases), f"数量不匹配: {len(new_cases)} != {len(cases)}"

    # 验证所有 id 保留
    orig_ids = {c["id"] for c in cases}
    new_ids = {c["id"] for c in new_cases}
    assert orig_ids == new_ids, f"ID 不匹配"

    # 验证 ref 关系保留
    for orig, new in zip(cases, new_cases):
        if "ref" in orig:
            assert "ref" in new and orig["ref"] == new["ref"], f"ref 丢失: {orig['id']}"
        if "model" in orig and orig["cat"] == "error":
            assert new.get("model") == orig["model"], f"error model 丢失: {orig['id']}"
        assert new.get("cat") == orig.get("cat"), f"cat 丢失: {orig['id']}"
        assert new.get("expect") == orig.get("expect"), f"expect 丢失: {orig['id']}"

    # 统计新 prompt 分布
    new_prompts = set()
    for c in new_cases:
        p = c.get("prompt", "")
        if p:
            new_prompts.add(p)
    print(f"新用例数: {len(new_cases)}")
    print(f"唯一 prompt 数: {len(new_prompts)}")

    new_cats = Counter(c.get("cat", "?") for c in new_cases)
    print("新分类分布:", dict(new_cats))

    # 展示部分新 prompt
    print("\n=== 各类别新 prompt 示例 ===")
    by_cat = {}
    for c in new_cases:
        cat = c.get("cat", "?")
        if cat not in by_cat:
            by_cat[cat] = []
        if len(by_cat[cat]) < 3:
            by_cat[cat].append(c.get("prompt", ""))
    for cat in sorted(by_cat.keys()):
        print(f"\n{cat}:")
        for p in by_cat[cat]:
            print(f"  {p[:70]}")

    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(new_cases, f, ensure_ascii=False, indent=2)

    print(f"\n已写入: {OUTPUT}")


if __name__ == "__main__":
    main()
