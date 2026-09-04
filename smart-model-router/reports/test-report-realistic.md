# MAS 原型测试报告

- **生成时间**：2026-09-05 01:35:48
- **应用地址**：http://localhost:9090/smart-router
- **运行隔离**：套件开始前经 /internal/cache/flush 清空两级缓存（含本地 Caffeine 层）；对话类用例拼接按 (model, prompt) 哈希派生的确定性隔离前缀（档案号 1e14 空间防碰撞）；断言 intent/difficulty 的用例使用原始 prompt；ref 用例继承前置用例的 max_tokens（精确缓存键组成部分）；断言缓存命中的用例请求前等待 1s 消除异步写竞态；串行请求限速 0.12s 避免误触限流
- **后端模型**：complex 档 → gpt-oss:20b，simple 档 → qwen2.5:0.5b，embedding → bge-m3（均为本机 Ollama 真实推理）

## 一、总体统计

| 测试类型 | 用例数 | 通过 | 失败 | 通过率 |
| --- | --- | --- | --- | --- |
| 黑盒功能用例（cases.json 数据驱动） | 900 | 629 | 271 | 69.9% |
| 单元测试（JUnit） | 135 | 135 | 0 | 100.0% |
| **合计** | **1035** | **764** | **271** | 73.8% |

### 分类统计

| 分类 | 说明 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- | --- |
| system | 系统端点（健康/指标/模型列表/trace） | 100 | 100 | 0 |
| embedding | 嵌入服务 | 100 | 100 | 0 |
| difficulty | 难度路由（无显式 model 自动分流） | 100 | 36 | 64 |
| explicit | 显式模型路由 | 100 | 52 | 48 |
| error | 错误与拦截（404/400/403） | 100 | 45 | 55 |
| legacy | 旧协议兼容层 | 100 | 90 | 10 |
| cache | 两级缓存行为 | 100 | 89 | 11 |
| stream | 流式 SSE | 100 | 54 | 46 |
| rate | 限流 | 100 | 63 | 37 |

## 二、黑盒用例路由明细

路由明细字段：intent（L3 路由档位）、difficulty（难度评分）、后端模型（引擎实际响应的 model）、缓存（命中级别）、耗时。
缓存命中的用例按设计短路返回，不经过 L3，故无 intent/difficulty 字段（— 表示）。

| 用例ID | 分类 | 输入摘要 | HTTP | intent | difficulty | 后端模型 | 缓存 | 耗时(ms) | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SYS-001 | system | /actuator/health | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-002 | system | /actuator/health | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-003 | system | /actuator/health | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-004 | system | /actuator/health | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-005 | system | /actuator/health | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-006 | system | /actuator/health | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-007 | system | /actuator/health | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-008 | system | /actuator/health | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-009 | system | /actuator/health | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-010 | system | /actuator/health | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-011 | system | /actuator/prometheus | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-012 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-013 | system | /actuator/prometheus | 200 | — | — | — | — | 8 | ✅ PASS |
| SYS-014 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-015 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-016 | system | /actuator/prometheus | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-017 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-018 | system | /actuator/prometheus | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-019 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-020 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-021 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-022 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-023 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-024 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-025 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-026 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-027 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-028 | system | /actuator/prometheus | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-029 | system | /actuator/prometheus | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-030 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-031 | system | /actuator/prometheus | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-032 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-033 | system | /actuator/prometheus | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-034 | system | /actuator/prometheus | 200 | — | — | — | — | 8 | ✅ PASS |
| SYS-035 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-036 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-037 | system | /actuator/prometheus | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-038 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-039 | system | /actuator/prometheus | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-040 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-041 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-042 | system | /v1/models | 200 | — | — | — | — | 1 | ✅ PASS |
| SYS-043 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-044 | system | /v1/models | 200 | — | — | — | — | 1 | ✅ PASS |
| SYS-045 | system | /v1/models | 200 | — | — | — | — | 1 | ✅ PASS |
| SYS-046 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-047 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-048 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-049 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-050 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-051 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-052 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-053 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-054 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-055 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-056 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-057 | system | /v1/models | 200 | — | — | — | — | 1 | ✅ PASS |
| SYS-058 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-059 | system | /v1/models | 200 | — | — | — | — | 1 | ✅ PASS |
| SYS-060 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-061 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-062 | system | /v1/models | 200 | — | — | — | — | 1 | ✅ PASS |
| SYS-063 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-064 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-065 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-066 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-067 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-068 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-069 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-070 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-071 | system | 请用一句话概括当前请求的路由决策过程 | 200 | simple | — | qwen2.5:0.5b | miss | 2032 | ✅ PASS |
| SYS-072 | system | 请描述本次请求经过了哪些处理阶段 | 200 | simple | — | qwen2.5:0.5b | miss | 332 | ✅ PASS |
| SYS-073 | system | 请说明本次请求最终被路由到哪个模型 | 200 | simple | — | qwen2.5:0.5b | miss | 318 | ✅ PASS |
| SYS-074 | system | 请简述缓存系统在本次请求中的命中情况 | 200 | simple | — | qwen2.5:0.5b | miss | 318 | ✅ PASS |
| SYS-075 | system | 请概括本次请求的意图识别结果 | 200 | simple | — | qwen2.5:0.5b | miss | 316 | ✅ PASS |
| SYS-076 | system | 请用一句话概括当前请求的路由决策过程 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| SYS-077 | system | 请描述本次请求经过了哪些处理阶段 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-078 | system | 请说明本次请求最终被路由到哪个模型 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| SYS-079 | system | 请简述缓存系统在本次请求中的命中情况 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-080 | system | 请概括本次请求的意图识别结果 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| SYS-081 | system | 请用一句话概括当前请求的路由决策过程 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| SYS-082 | system | 请描述本次请求经过了哪些处理阶段 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-083 | system | 请说明本次请求最终被路由到哪个模型 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-084 | system | 请简述缓存系统在本次请求中的命中情况 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-085 | system | 请概括本次请求的意图识别结果 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| SYS-086 | system | 请用一句话概括当前请求的路由决策过程 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| SYS-087 | system | 请描述本次请求经过了哪些处理阶段 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-088 | system | 请说明本次请求最终被路由到哪个模型 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-089 | system | 请简述缓存系统在本次请求中的命中情况 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-090 | system | 请概括本次请求的意图识别结果 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| SYS-091 | system | 请用一句话概括当前请求的路由决策过程 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| SYS-092 | system | 请描述本次请求经过了哪些处理阶段 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| SYS-093 | system | 请说明本次请求最终被路由到哪个模型 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| SYS-094 | system | 请简述缓存系统在本次请求中的命中情况 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| SYS-095 | system | 请概括本次请求的意图识别结果 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| SYS-096 | system | 请用一句话概括当前请求的路由决策过程 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| SYS-097 | system | 请描述本次请求经过了哪些处理阶段 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| SYS-098 | system | 请说明本次请求最终被路由到哪个模型 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| SYS-099 | system | 请简述缓存系统在本次请求中的命中情况 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| SYS-100 | system | 请概括本次请求的意图识别结果 | 200 | — | — | qwen2.5:0.5b | hit:exact | 8 | ✅ PASS |
| EMB-001 | embedding | 银行卡密码修改流程和注意事项 | 200 | — | — | bge-m3 | — | 165 | ✅ PASS |
| EMB-002 | embedding | 信用卡逾期还款对个人征信的影响说明 | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| EMB-003 | embedding | Python 实现快速排序算法的代码示例 | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-004 | embedding | 企业信贷风险评估方法论和指标体系 | 200 | — | — | bge-m3 | — | 183 | ✅ PASS |
| EMB-005 | embedding | 反洗钱交易监控规则和客户尽职调查流程 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-006 | embedding | 分布式系统一致性协议 Raft 和 Paxos 对比 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-007 | embedding | 机器学习模型特征工程最佳实践 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-008 | embedding | 供应链金融风险管理框架和操作指引 | 200 | — | — | bge-m3 | — | 124 | ✅ PASS |
| EMB-009 | embedding | 个人住房贷款审批流程和所需材料清单 | 200 | — | — | bge-m3 | — | 118 | ✅ PASS |
| EMB-010 | embedding | 网络安全渗透测试方法论和工具介绍 | 200 | — | — | bge-m3 | — | 118 | ✅ PASS |
| EMB-011 | embedding | 微服务架构设计原则和服务拆分策略 | 200 | — | — | bge-m3 | — | 124 | ✅ PASS |
| EMB-012 | embedding | 自然语言处理中 Transformer 模型原理详解 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-013 | embedding | 区块链智能合约开发语言 Solidity 入门指南 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-014 | embedding | 数据库索引优化策略和慢查询分析方法 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-015 | embedding | 容器编排平台 Kubernetes 核心概念解析 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-016 | embedding | 金融衍生品定价模型 Black-Scholes 公式推导 | 200 | — | — | bge-m3 | — | 124 | ✅ PASS |
| EMB-017 | embedding | 推荐系统协同过滤算法原理和工程实现 | 200 | — | — | bge-m3 | — | 122 | ✅ PASS |
| EMB-018 | embedding | 云计算 IaaS PaaS SaaS 三种服务模式对比 | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-019 | embedding | 量子计算基本原理和量子比特操作门介绍 | 200 | — | — | bge-m3 | — | 122 | ✅ PASS |
| EMB-020 | embedding | 自动驾驶感知融合算法和多传感器标定方法 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-021 | embedding | 银行卡密码修改流程和注意事项 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-022 | embedding | 信用卡逾期还款对个人征信的影响说明 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-023 | embedding | Python 实现快速排序算法的代码示例 | 200 | — | — | bge-m3 | — | 126 | ✅ PASS |
| EMB-024 | embedding | 企业信贷风险评估方法论和指标体系 | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-025 | embedding | 反洗钱交易监控规则和客户尽职调查流程 | 200 | — | — | bge-m3 | — | 124 | ✅ PASS |
| EMB-026 | embedding | 分布式系统一致性协议 Raft 和 Paxos 对比 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-027 | embedding | 机器学习模型特征工程最佳实践 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-028 | embedding | 供应链金融风险管理框架和操作指引 | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| EMB-029 | embedding | 个人住房贷款审批流程和所需材料清单 | 200 | — | — | bge-m3 | — | 121 | ✅ PASS |
| EMB-030 | embedding | 网络安全渗透测试方法论和工具介绍 | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-031 | embedding | 微服务架构设计原则和服务拆分策略 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-032 | embedding | 自然语言处理中 Transformer 模型原理详解 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-033 | embedding | 区块链智能合约开发语言 Solidity 入门指南 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-034 | embedding | 数据库索引优化策略和慢查询分析方法 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-035 | embedding | 容器编排平台 Kubernetes 核心概念解析 | 200 | — | — | bge-m3 | — | 122 | ✅ PASS |
| EMB-036 | embedding | 金融衍生品定价模型 Black-Scholes 公式推导 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-037 | embedding | 推荐系统协同过滤算法原理和工程实现 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-038 | embedding | 云计算 IaaS PaaS SaaS 三种服务模式对比 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-039 | embedding | 量子计算基本原理和量子比特操作门介绍 | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| EMB-040 | embedding | 自动驾驶感知融合算法和多传感器标定方法 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-041 | embedding | 银行卡密码修改流程和注意事项 | 200 | — | — | bge-m3 | — | 123 | ✅ PASS |
| EMB-042 | embedding | 信用卡逾期还款对个人征信的影响说明 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-043 | embedding | Python 实现快速排序算法的代码示例 | 200 | — | — | bge-m3 | — | 123 | ✅ PASS |
| EMB-044 | embedding | 企业信贷风险评估方法论和指标体系 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-045 | embedding | 反洗钱交易监控规则和客户尽职调查流程 | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| EMB-046 | embedding | 分布式系统一致性协议 Raft 和 Paxos 对比 | 200 | — | — | bge-m3 | — | 135 | ✅ PASS |
| EMB-047 | embedding | 机器学习模型特征工程最佳实践 | 200 | — | — | bge-m3 | — | 121 | ✅ PASS |
| EMB-048 | embedding | 供应链金融风险管理框架和操作指引 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-049 | embedding | 个人住房贷款审批流程和所需材料清单 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-050 | embedding | 网络安全渗透测试方法论和工具介绍 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-051 | embedding | 微服务架构设计原则和服务拆分策略 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-052 | embedding | 自然语言处理中 Transformer 模型原理详解 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-053 | embedding | 区块链智能合约开发语言 Solidity 入门指南 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-054 | embedding | 数据库索引优化策略和慢查询分析方法 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-055 | embedding | 容器编排平台 Kubernetes 核心概念解析 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-056 | embedding | 金融衍生品定价模型 Black-Scholes 公式推导 | 200 | — | — | bge-m3 | — | 123 | ✅ PASS |
| EMB-057 | embedding | 推荐系统协同过滤算法原理和工程实现 | 200 | — | — | bge-m3 | — | 120 | ✅ PASS |
| EMB-058 | embedding | 云计算 IaaS PaaS SaaS 三种服务模式对比 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-059 | embedding | 量子计算基本原理和量子比特操作门介绍 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-060 | embedding | 自动驾驶感知融合算法和多传感器标定方法 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-061 | embedding | 银行卡密码修改流程和注意事项 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-062 | embedding | 信用卡逾期还款对个人征信的影响说明 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-063 | embedding | Python 实现快速排序算法的代码示例 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-064 | embedding | 企业信贷风险评估方法论和指标体系 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-065 | embedding | 反洗钱交易监控规则和客户尽职调查流程 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-066 | embedding | 分布式系统一致性协议 Raft 和 Paxos 对比 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-067 | embedding | 机器学习模型特征工程最佳实践 | 200 | — | — | bge-m3 | — | 121 | ✅ PASS |
| EMB-068 | embedding | 供应链金融风险管理框架和操作指引 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-069 | embedding | 个人住房贷款审批流程和所需材料清单 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-070 | embedding | 网络安全渗透测试方法论和工具介绍 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-071 | embedding | 微服务架构设计原则和服务拆分策略 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-072 | embedding | 自然语言处理中 Transformer 模型原理详解 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-073 | embedding | 区块链智能合约开发语言 Solidity 入门指南 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-074 | embedding | 数据库索引优化策略和慢查询分析方法 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-075 | embedding | 容器编排平台 Kubernetes 核心概念解析 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-076 | embedding | 金融衍生品定价模型 Black-Scholes 公式推导 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-077 | embedding | 推荐系统协同过滤算法原理和工程实现 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-078 | embedding | 云计算 IaaS PaaS SaaS 三种服务模式对比 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-079 | embedding | 量子计算基本原理和量子比特操作门介绍 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-080 | embedding | 自动驾驶感知融合算法和多传感器标定方法 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-081 | embedding | 银行卡密码修改流程和注意事项 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-082 | embedding | 信用卡逾期还款对个人征信的影响说明 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-083 | embedding | Python 实现快速排序算法的代码示例 | 200 | — | — | bge-m3 | — | 122 | ✅ PASS |
| EMB-084 | embedding | 企业信贷风险评估方法论和指标体系 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-085 | embedding | 反洗钱交易监控规则和客户尽职调查流程 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-086 | embedding | 分布式系统一致性协议 Raft 和 Paxos 对比 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-087 | embedding | 机器学习模型特征工程最佳实践 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-088 | embedding | 供应链金融风险管理框架和操作指引 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-089 | embedding | 个人住房贷款审批流程和所需材料清单 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-090 | embedding | 网络安全渗透测试方法论和工具介绍 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-091 | embedding | 微服务架构设计原则和服务拆分策略 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-092 | embedding | 自然语言处理中 Transformer 模型原理详解 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-093 | embedding | 区块链智能合约开发语言 Solidity 入门指南 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-094 | embedding | 数据库索引优化策略和慢查询分析方法 | 200 | — | — | bge-m3 | — | 126 | ✅ PASS |
| EMB-095 | embedding | 容器编排平台 Kubernetes 核心概念解析 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-096 | embedding | 金融衍生品定价模型 Black-Scholes 公式推导 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-097 | embedding | 推荐系统协同过滤算法原理和工程实现 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-098 | embedding | 云计算 IaaS PaaS SaaS 三种服务模式对比 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-099 | embedding | 量子计算基本原理和量子比特操作门介绍 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-100 | embedding | 自动驾驶感知融合算法和多传感器标定方法 | 200 | — | — | bge-m3 | — | 136 | ✅ PASS |
| DS-001 | difficulty | 如何修改银行卡密码？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 388 | ✅ PASS |
| DS-002 | difficulty | 信用卡年费是多少？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-003 | difficulty | 转账限额是多少？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-004 | difficulty | 如何开通手机银行？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 348 | ✅ PASS |
| DS-005 | difficulty | 信用卡逾期还款有什么后果？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| DS-006 | difficulty | 如何查询征信报告？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-007 | difficulty | 借记卡和贷记卡有什么区别？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 363 | ✅ PASS |
| DS-008 | difficulty | 如何办理存款证明？ | 200 | simple | 0.2 | qwen2.5:0.5b | miss | 361 | ✅ PASS |
| DS-009 | difficulty | 手机支付忘记密码怎么办？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-010 | difficulty | 如何申请提高信用卡额度？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| DS-011 | difficulty | 外币兑换汇率怎么查？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| DS-012 | difficulty | 如何挂失银行卡？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 358 | ✅ PASS |
| DS-013 | difficulty | 公积金提取需要什么材料？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 343 | ✅ PASS |
| DS-014 | difficulty | 理财产品赎回多久到账？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 351 | ✅ PASS |
| DS-015 | difficulty | 如何开通短信通知服务？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 347 | ✅ PASS |
| DS-016 | difficulty | 个人贷款需要什么条件？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 347 | ✅ PASS |
| DS-017 | difficulty | 信用卡积分怎么兑换？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 337 | ✅ PASS |
| DS-018 | difficulty | 如何修改预留手机号？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 356 | ✅ PASS |
| DS-019 | difficulty | 网银转账手续费多少？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 368 | ✅ PASS |
| DS-020 | difficulty | 如何查询账户余额？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| DS-021 | difficulty | 今天星期几？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| DS-022 | difficulty | 一年有几个季度？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 292 | ✅ PASS |
| DS-023 | difficulty | 一公里等于多少米？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 318 | ✅ PASS |
| DS-024 | difficulty | 水的沸点是多少度？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 364 | ✅ PASS |
| DS-025 | difficulty | 中国有多少个省份？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 363 | ✅ PASS |
| DS-026 | difficulty | 一周有几天工作日？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-027 | difficulty | 一小时有多少分钟？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 303 | ✅ PASS |
| DS-028 | difficulty | 一个季度有几个月？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-029 | difficulty | 一吨等于多少公斤？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| DS-030 | difficulty | 一升等于多少毫升？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 364 | ✅ PASS |
| DS-031 | difficulty | 如何修改银行卡密码？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 2 | ❌ FAIL |
| DS-032 | difficulty | 信用卡年费是多少？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DS-033 | difficulty | 转账限额是多少？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| DS-034 | difficulty | 如何开通手机银行？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| DS-035 | difficulty | 信用卡逾期还款有什么后果？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DS-036 | difficulty | 如何查询征信报告？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DS-037 | difficulty | 借记卡和贷记卡有什么区别？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DS-038 | difficulty | 如何办理存款证明？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DS-039 | difficulty | 手机支付忘记密码怎么办？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DS-040 | difficulty | 如何申请提高信用卡额度？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DS-041 | difficulty | 外币兑换汇率怎么查？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| DS-042 | difficulty | 如何挂失银行卡？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DS-043 | difficulty | 公积金提取需要什么材料？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 9 | ❌ FAIL |
| DS-044 | difficulty | 理财产品赎回多久到账？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DS-045 | difficulty | 如何开通短信通知服务？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 2 | ❌ FAIL |
| DS-046 | difficulty | 个人贷款需要什么条件？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DS-047 | difficulty | 信用卡积分怎么兑换？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DS-048 | difficulty | 如何修改预留手机号？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DS-049 | difficulty | 网银转账手续费多少？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DS-050 | difficulty | 如何查询账户余额？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| DC-001 | difficulty | 用 Python 实现一个 LRU 缓存，要求 get  | 200 | simple | 0.45000000000000007 | qwen2.5:0.5b | miss | 382 | ❌ FAIL |
| DC-002 | difficulty | 解释这段 SQL 的执行计划：SELECT * FROM | 200 | simple | 0.3 | qwen2.5:0.5b | miss | 370 | ❌ FAIL |
| DC-003 | difficulty | 用 Java 实现生产者消费者模式，使用 Reentra | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 348 | ❌ FAIL |
| DC-004 | difficulty | 设计一个分布式 ID 生成器，参考 Snowflake  | 200 | complex | 0.5 | gpt-oss:20b | miss | 11346 | ✅ PASS |
| DC-005 | difficulty | 解释 Kubernetes 中 Deployment 和 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 2921 | ❌ FAIL |
| DC-006 | difficulty | 用 Go 编写一个 HTTP 反向代理，支持轮询和加权轮 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 381 | ❌ FAIL |
| DC-007 | difficulty | 分析这段代码的性能瓶颈：一个嵌套循环遍历 10 万条记录 | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 357 | ❌ FAIL |
| DC-008 | difficulty | 用 Rust 实现一个简单的键值存储引擎，支持 put/ | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 358 | ❌ FAIL |
| DC-009 | difficulty | 解释 React 中 useEffect 的依赖数组原理 | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 357 | ❌ FAIL |
| DC-010 | difficulty | 设计一个高并发秒杀系统，要求处理超卖、限流、防刷，给出架 | 200 | simple | 0.4 | qwen2.5:0.5b | miss | 374 | ❌ FAIL |
| DC-011 | difficulty | 请分析以下企业的信贷风险状况：该企业为制造业中型企业，年 | 200 | complex | 0.65 | gpt-oss:20b | miss | 1720 | ✅ PASS |
| DC-012 | difficulty | 某科技公司申请流动资金贷款 2000 万元，期限 1 年 | 200 | simple | 0.44999999999999996 | qwen2.5:0.5b | miss | 411 | ❌ FAIL |
| DC-013 | difficulty | 评估一笔个人住房按揭贷款申请：借款人 35 岁，月收入  | 200 | complex | 0.65 | gpt-oss:20b | miss | 1735 | ✅ PASS |
| DC-014 | difficulty | 某城投平台申请项目贷款 3 亿元，用于城市基础设施建设。 | 200 | complex | 0.65 | gpt-oss:20b | miss | 1779 | ✅ PASS |
| DC-015 | difficulty | 对以下交易进行风险评分：持卡人张某，卡号尾号 8821， | 200 | simple | 0.3 | qwen2.5:0.5b | miss | 430 | ❌ FAIL |
| DC-016 | difficulty | 分析以下可疑交易模式：某企业账户在过去 2 小时内发生  | 200 | complex | 0.5 | gpt-oss:20b | miss | 1682 | ✅ PASS |
| DC-017 | difficulty | 评估以下信贷欺诈风险：申请人李某，身份证显示年龄 22  | 200 | complex | 0.5 | gpt-oss:20b | miss | 1685 | ✅ PASS |
| DC-018 | difficulty | 对以下保险理赔进行风险评估：投保人王某，投保重大疾病险  | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 421 | ❌ FAIL |
| DC-019 | difficulty | 分析以下反欺诈规则触发情况：同一设备 ID 在 24 小 | 200 | simple | 0.3 | qwen2.5:0.5b | miss | 391 | ❌ FAIL |
| DC-020 | difficulty | 比较 RESTful API 和 GraphQL 的优缺 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 354 | ❌ FAIL |
| DC-021 | difficulty | 解释 CAP 定理的含义，并举例说明在实际分布式系统中如 | 200 | simple | 0.4 | qwen2.5:0.5b | miss | 349 | ❌ FAIL |
| DC-022 | difficulty | 设计一个支持千万级并发的消息队列系统，要求保证消息不丢失 | 200 | simple | 0.2 | qwen2.5:0.5b | miss | 352 | ❌ FAIL |
| DC-023 | difficulty | 用 Python 实现一个 LRU 缓存，要求 get  | 200 | — | — | qwen2.5:0.5b | hit:exact | 16 | ❌ FAIL |
| DC-024 | difficulty | 解释这段 SQL 的执行计划：SELECT * FROM | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DC-025 | difficulty | 用 Java 实现生产者消费者模式，使用 Reentra | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DC-026 | difficulty | 设计一个分布式 ID 生成器，参考 Snowflake  | 200 | — | — | gpt-oss:20b | hit:exact | 7 | ❌ FAIL |
| DC-027 | difficulty | 解释 Kubernetes 中 Deployment 和 | 200 | — | — | qwen2.5:0.5b | hit:exact | 8 | ❌ FAIL |
| DC-028 | difficulty | 用 Go 编写一个 HTTP 反向代理，支持轮询和加权轮 | 200 | — | — | qwen2.5:0.5b | hit:exact | 26 | ❌ FAIL |
| DC-029 | difficulty | 分析这段代码的性能瓶颈：一个嵌套循环遍历 10 万条记录 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DC-030 | difficulty | 用 Rust 实现一个简单的键值存储引擎，支持 put/ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DC-031 | difficulty | 解释 React 中 useEffect 的依赖数组原理 | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| DC-032 | difficulty | 设计一个高并发秒杀系统，要求处理超卖、限流、防刷，给出架 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DC-033 | difficulty | 请分析以下企业的信贷风险状况：该企业为制造业中型企业，年 | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| DC-034 | difficulty | 某科技公司申请流动资金贷款 2000 万元，期限 1 年 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| DC-035 | difficulty | 评估一笔个人住房按揭贷款申请：借款人 35 岁，月收入  | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| DC-036 | difficulty | 某城投平台申请项目贷款 3 亿元，用于城市基础设施建设。 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| DC-037 | difficulty | 对以下交易进行风险评分：持卡人张某，卡号尾号 8821， | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| DC-038 | difficulty | 分析以下可疑交易模式：某企业账户在过去 2 小时内发生  | 200 | — | — | gpt-oss:20b | hit:exact | 3 | ❌ FAIL |
| DC-039 | difficulty | 评估以下信贷欺诈风险：申请人李某，身份证显示年龄 22  | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| DC-040 | difficulty | 对以下保险理赔进行风险评估：投保人王某，投保重大疾病险  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DC-041 | difficulty | 分析以下反欺诈规则触发情况：同一设备 ID 在 24 小 | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| DC-042 | difficulty | 比较 RESTful API 和 GraphQL 的优缺 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DC-043 | difficulty | 解释 CAP 定理的含义，并举例说明在实际分布式系统中如 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DC-044 | difficulty | 设计一个支持千万级并发的消息队列系统，要求保证消息不丢失 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DC-045 | difficulty | 用 Python 实现一个 LRU 缓存，要求 get  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DC-046 | difficulty | 解释这段 SQL 的执行计划：SELECT * FROM | 200 | — | — | qwen2.5:0.5b | hit:exact | 9 | ❌ FAIL |
| DC-047 | difficulty | 用 Java 实现生产者消费者模式，使用 Reentra | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| DC-048 | difficulty | 设计一个分布式 ID 生成器，参考 Snowflake  | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| DC-049 | difficulty | 解释 Kubernetes 中 Deployment 和 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| DC-050 | difficulty | 用 Go 编写一个 HTTP 反向代理，支持轮询和加权轮 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-001 | explicit | 如何修改银行卡密码？ | 200 | simple | — | qwen2.5:0.5b | miss | 441 | ✅ PASS |
| EXP-002 | explicit | 信用卡年费是多少？ | 200 | simple | — | qwen2.5:0.5b | miss | 360 | ✅ PASS |
| EXP-003 | explicit | 转账限额是多少？ | 200 | simple | — | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| EXP-004 | explicit | 如何开通手机银行？ | 200 | simple | — | qwen2.5:0.5b | miss | 346 | ✅ PASS |
| EXP-005 | explicit | 信用卡逾期还款有什么后果？ | 200 | simple | — | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| EXP-006 | explicit | 如何查询征信报告？ | 200 | simple | — | qwen2.5:0.5b | miss | 358 | ✅ PASS |
| EXP-007 | explicit | 借记卡和贷记卡有什么区别？ | 200 | simple | — | qwen2.5:0.5b | miss | 351 | ✅ PASS |
| EXP-008 | explicit | 如何办理存款证明？ | 200 | simple | — | qwen2.5:0.5b | miss | 361 | ✅ PASS |
| EXP-009 | explicit | 手机支付忘记密码怎么办？ | 200 | simple | — | qwen2.5:0.5b | miss | 354 | ✅ PASS |
| EXP-010 | explicit | 如何申请提高信用卡额度？ | 200 | simple | — | qwen2.5:0.5b | miss | 354 | ✅ PASS |
| EXP-011 | explicit | 外币兑换汇率怎么查？ | 200 | simple | — | qwen2.5:0.5b | miss | 351 | ✅ PASS |
| EXP-012 | explicit | 如何挂失银行卡？ | 200 | simple | — | qwen2.5:0.5b | miss | 349 | ✅ PASS |
| EXP-013 | explicit | 公积金提取需要什么材料？ | 200 | simple | — | qwen2.5:0.5b | miss | 356 | ✅ PASS |
| EXP-014 | explicit | 理财产品赎回多久到账？ | 200 | simple | — | qwen2.5:0.5b | miss | 352 | ✅ PASS |
| EXP-015 | explicit | 如何开通短信通知服务？ | 200 | simple | — | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| EXP-016 | explicit | 个人贷款需要什么条件？ | 200 | simple | — | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| EXP-017 | explicit | 信用卡积分怎么兑换？ | 200 | simple | — | qwen2.5:0.5b | miss | 359 | ✅ PASS |
| EXP-018 | explicit | 如何修改预留手机号？ | 200 | simple | — | qwen2.5:0.5b | miss | 385 | ✅ PASS |
| EXP-019 | explicit | 网银转账手续费多少？ | 200 | simple | — | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| EXP-020 | explicit | 如何查询账户余额？ | 200 | simple | — | qwen2.5:0.5b | miss | 361 | ✅ PASS |
| EXP-021 | explicit | 今天星期几？ | 200 | simple | — | qwen2.5:0.5b | miss | 357 | ✅ PASS |
| EXP-022 | explicit | 一年有几个季度？ | 200 | simple | — | qwen2.5:0.5b | miss | 359 | ✅ PASS |
| EXP-023 | explicit | 一公里等于多少米？ | 200 | simple | — | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| EXP-024 | explicit | 水的沸点是多少度？ | 200 | simple | — | qwen2.5:0.5b | miss | 471 | ✅ PASS |
| EXP-025 | explicit | 中国有多少个省份？ | 200 | simple | — | qwen2.5:0.5b | miss | 372 | ✅ PASS |
| EXP-026 | explicit | 一周有几天工作日？ | 200 | simple | — | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| EXP-027 | explicit | 一小时有多少分钟？ | 200 | simple | — | qwen2.5:0.5b | miss | 422 | ✅ PASS |
| EXP-028 | explicit | 一个季度有几个月？ | 200 | simple | — | qwen2.5:0.5b | miss | 368 | ✅ PASS |
| EXP-029 | explicit | 一吨等于多少公斤？ | 200 | simple | — | qwen2.5:0.5b | miss | 356 | ✅ PASS |
| EXP-030 | explicit | 一升等于多少毫升？ | 200 | simple | — | qwen2.5:0.5b | miss | 367 | ✅ PASS |
| EXP-031 | explicit | 如何修改银行卡密码？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| EXP-032 | explicit | 信用卡年费是多少？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| EXP-033 | explicit | 转账限额是多少？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| EXP-034 | explicit | 如何开通手机银行？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-035 | explicit | 信用卡逾期还款有什么后果？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| EXP-036 | explicit | 如何查询征信报告？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-037 | explicit | 借记卡和贷记卡有什么区别？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| EXP-038 | explicit | 如何办理存款证明？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-039 | explicit | 手机支付忘记密码怎么办？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| EXP-040 | explicit | 如何申请提高信用卡额度？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-041 | explicit | 外币兑换汇率怎么查？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| EXP-042 | explicit | 如何挂失银行卡？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| EXP-043 | explicit | 公积金提取需要什么材料？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| EXP-044 | explicit | 理财产品赎回多久到账？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-045 | explicit | 如何开通短信通知服务？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-046 | explicit | 个人贷款需要什么条件？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| EXP-047 | explicit | 信用卡积分怎么兑换？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| EXP-048 | explicit | 如何修改预留手机号？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| EXP-049 | explicit | 网银转账手续费多少？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| EXP-050 | explicit | 如何查询账户余额？ | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ❌ FAIL |
| EXP-051 | explicit | 分析这段代码的性能瓶颈：一个嵌套循环遍历 10 万条记录 | 200 | complex | — | gpt-oss:20b | miss | 1669 | ✅ PASS |
| EXP-052 | explicit | 用 Rust 实现一个简单的键值存储引擎，支持 put/ | 200 | complex | — | gpt-oss:20b | miss | 1579 | ✅ PASS |
| EXP-053 | explicit | 解释 React 中 useEffect 的依赖数组原理 | 200 | complex | — | gpt-oss:20b | miss | 1614 | ✅ PASS |
| EXP-054 | explicit | 设计一个高并发秒杀系统，要求处理超卖、限流、防刷，给出架 | 200 | complex | — | gpt-oss:20b | miss | 1656 | ✅ PASS |
| EXP-055 | explicit | 请分析以下企业的信贷风险状况：该企业为制造业中型企业，年 | 200 | complex | — | gpt-oss:20b | miss | 1849 | ✅ PASS |
| EXP-056 | explicit | 某科技公司申请流动资金贷款 2000 万元，期限 1 年 | 200 | complex | — | gpt-oss:20b | miss | 1843 | ✅ PASS |
| EXP-057 | explicit | 评估一笔个人住房按揭贷款申请：借款人 35 岁，月收入  | 200 | complex | — | gpt-oss:20b | miss | 1835 | ✅ PASS |
| EXP-058 | explicit | 某城投平台申请项目贷款 3 亿元，用于城市基础设施建设。 | 200 | complex | — | gpt-oss:20b | miss | 1856 | ✅ PASS |
| EXP-059 | explicit | 对以下交易进行风险评分：持卡人张某，卡号尾号 8821， | 200 | complex | — | gpt-oss:20b | miss | 1792 | ✅ PASS |
| EXP-060 | explicit | 分析以下可疑交易模式：某企业账户在过去 2 小时内发生  | 200 | complex | — | gpt-oss:20b | miss | 1757 | ✅ PASS |
| EXP-061 | explicit | 评估以下信贷欺诈风险：申请人李某，身份证显示年龄 22  | 200 | complex | — | gpt-oss:20b | miss | 1722 | ✅ PASS |
| EXP-062 | explicit | 对以下保险理赔进行风险评估：投保人王某，投保重大疾病险  | 200 | complex | — | gpt-oss:20b | miss | 1735 | ✅ PASS |
| EXP-063 | explicit | 分析以下反欺诈规则触发情况：同一设备 ID 在 24 小 | 200 | complex | — | gpt-oss:20b | miss | 1738 | ✅ PASS |
| EXP-064 | explicit | 比较 RESTful API 和 GraphQL 的优缺 | 200 | complex | — | gpt-oss:20b | miss | 1617 | ✅ PASS |
| EXP-065 | explicit | 解释 CAP 定理的含义，并举例说明在实际分布式系统中如 | 200 | complex | — | gpt-oss:20b | miss | 1641 | ✅ PASS |
| EXP-066 | explicit | 设计一个支持千万级并发的消息队列系统，要求保证消息不丢失 | 200 | complex | — | gpt-oss:20b | miss | 1657 | ✅ PASS |
| EXP-067 | explicit | 用 Python 实现一个 LRU 缓存，要求 get  | 200 | complex | — | gpt-oss:20b | miss | 1654 | ✅ PASS |
| EXP-068 | explicit | 解释这段 SQL 的执行计划：SELECT * FROM | 200 | complex | — | gpt-oss:20b | miss | 1657 | ✅ PASS |
| EXP-069 | explicit | 用 Java 实现生产者消费者模式，使用 Reentra | 200 | complex | — | gpt-oss:20b | miss | 1830 | ✅ PASS |
| EXP-070 | explicit | 设计一个分布式 ID 生成器，参考 Snowflake  | 200 | complex | — | gpt-oss:20b | miss | 1865 | ✅ PASS |
| EXP-071 | explicit | 解释 Kubernetes 中 Deployment 和 | 200 | complex | — | gpt-oss:20b | miss | 1883 | ✅ PASS |
| EXP-072 | explicit | 用 Go 编写一个 HTTP 反向代理，支持轮询和加权轮 | 200 | complex | — | gpt-oss:20b | miss | 1664 | ✅ PASS |
| EXP-073 | explicit | 分析这段代码的性能瓶颈：一个嵌套循环遍历 10 万条记录 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-074 | explicit | 用 Rust 实现一个简单的键值存储引擎，支持 put/ | 200 | — | — | gpt-oss:20b | hit:exact | 9 | ❌ FAIL |
| EXP-075 | explicit | 解释 React 中 useEffect 的依赖数组原理 | 200 | — | — | gpt-oss:20b | hit:exact | 28 | ❌ FAIL |
| EXP-076 | explicit | 设计一个高并发秒杀系统，要求处理超卖、限流、防刷，给出架 | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-077 | explicit | 请分析以下企业的信贷风险状况：该企业为制造业中型企业，年 | 200 | — | — | gpt-oss:20b | hit:exact | 6 | ❌ FAIL |
| EXP-078 | explicit | 某科技公司申请流动资金贷款 2000 万元，期限 1 年 | 200 | — | — | gpt-oss:20b | hit:exact | 7 | ❌ FAIL |
| EXP-079 | explicit | 评估一笔个人住房按揭贷款申请：借款人 35 岁，月收入  | 200 | — | — | gpt-oss:20b | hit:exact | 3 | ❌ FAIL |
| EXP-080 | explicit | 某城投平台申请项目贷款 3 亿元，用于城市基础设施建设。 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-081 | explicit | 对以下交易进行风险评分：持卡人张某，卡号尾号 8821， | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-082 | explicit | 分析以下可疑交易模式：某企业账户在过去 2 小时内发生  | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-083 | explicit | 评估以下信贷欺诈风险：申请人李某，身份证显示年龄 22  | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-084 | explicit | 对以下保险理赔进行风险评估：投保人王某，投保重大疾病险  | 200 | — | — | gpt-oss:20b | hit:exact | 19 | ❌ FAIL |
| EXP-085 | explicit | 分析以下反欺诈规则触发情况：同一设备 ID 在 24 小 | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-086 | explicit | 比较 RESTful API 和 GraphQL 的优缺 | 200 | — | — | gpt-oss:20b | hit:exact | 7 | ❌ FAIL |
| EXP-087 | explicit | 解释 CAP 定理的含义，并举例说明在实际分布式系统中如 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-088 | explicit | 设计一个支持千万级并发的消息队列系统，要求保证消息不丢失 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-089 | explicit | 用 Python 实现一个 LRU 缓存，要求 get  | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-090 | explicit | 解释这段 SQL 的执行计划：SELECT * FROM | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-091 | explicit | 用 Java 实现生产者消费者模式，使用 Reentra | 200 | — | — | gpt-oss:20b | hit:exact | 6 | ❌ FAIL |
| EXP-092 | explicit | 设计一个分布式 ID 生成器，参考 Snowflake  | 200 | — | — | gpt-oss:20b | hit:exact | 6 | ❌ FAIL |
| EXP-093 | explicit | 解释 Kubernetes 中 Deployment 和 | 200 | — | — | gpt-oss:20b | hit:exact | 6 | ❌ FAIL |
| EXP-094 | explicit | 用 Go 编写一个 HTTP 反向代理，支持轮询和加权轮 | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-095 | explicit | 分析这段代码的性能瓶颈：一个嵌套循环遍历 10 万条记录 | 200 | — | — | gpt-oss:20b | hit:exact | 5 | ❌ FAIL |
| EXP-096 | explicit | 用 Rust 实现一个简单的键值存储引擎，支持 put/ | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-097 | explicit | 解释 React 中 useEffect 的依赖数组原理 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-098 | explicit | 设计一个高并发秒杀系统，要求处理超卖、限流、防刷，给出架 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-099 | explicit | 请分析以下企业的信贷风险状况：该企业为制造业中型企业，年 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| EXP-100 | explicit | 某科技公司申请流动资金贷款 2000 万元，期限 1 年 | 200 | — | — | gpt-oss:20b | hit:exact | 4 | ❌ FAIL |
| ERR-001 | error | 请帮我查询账户余额 | 404 | — | — | — | — | 183 | ✅ PASS |
| ERR-002 | error | 如何修改我的转账限额 | 404 | — | — | — | — | 143 | ✅ PASS |
| ERR-003 | error | 我想申请一张信用卡 | 404 | — | — | — | — | 156 | ✅ PASS |
| ERR-004 | error | 请解释一下贷款利率的计算方式 | 404 | — | — | — | — | 140 | ✅ PASS |
| ERR-005 | error | 如何开通网上银行功能 | 404 | — | — | — | — | 146 | ✅ PASS |
| ERR-006 | error | 我的银行卡被锁定了怎么办 | 404 | — | — | — | — | 136 | ✅ PASS |
| ERR-007 | error | 请帮我查询最近的交易记录 | 404 | — | — | — | — | 143 | ✅ PASS |
| ERR-008 | error | 如何设置自动还款功能 | 404 | — | — | — | — | 136 | ✅ PASS |
| ERR-009 | error | 我想了解理财产品的收益率 | 404 | — | — | — | — | 134 | ✅ PASS |
| ERR-010 | error | 如何办理外汇兑换业务 | 404 | — | — | — | — | 148 | ✅ PASS |
| ERR-011 | error | 请帮我查询账户余额 | 404 | — | — | — | — | 129 | ✅ PASS |
| ERR-012 | error | 如何修改我的转账限额 | 404 | — | — | — | — | 132 | ✅ PASS |
| ERR-013 | error | 我想申请一张信用卡 | 404 | — | — | — | — | 131 | ✅ PASS |
| ERR-014 | error | 请解释一下贷款利率的计算方式 | 404 | — | — | — | — | 133 | ✅ PASS |
| ERR-015 | error | 如何开通网上银行功能 | 404 | — | — | — | — | 133 | ✅ PASS |
| ERR-016 | error | 我的银行卡被锁定了怎么办 | 404 | — | — | — | — | 134 | ✅ PASS |
| ERR-017 | error | 请帮我查询最近的交易记录 | 404 | — | — | — | — | 132 | ✅ PASS |
| ERR-018 | error | 如何设置自动还款功能 | 404 | — | — | — | — | 134 | ✅ PASS |
| ERR-019 | error | 我想了解理财产品的收益率 | 404 | — | — | — | — | 132 | ✅ PASS |
| ERR-020 | error | 如何办理外汇兑换业务 | 404 | — | — | — | — | 142 | ✅ PASS |
| ERR-021 | error | 请帮我查询账户余额 | 404 | — | — | — | — | 135 | ✅ PASS |
| ERR-022 | error | 如何修改我的转账限额 | 404 | — | — | — | — | 126 | ✅ PASS |
| ERR-023 | error | 我想申请一张信用卡 | 404 | — | — | — | — | 141 | ✅ PASS |
| ERR-024 | error | 请解释一下贷款利率的计算方式 | 404 | — | — | — | — | 124 | ✅ PASS |
| ERR-025 | error | 如何开通网上银行功能 | 404 | — | — | — | — | 133 | ✅ PASS |
| ERR-026 | error | 我的银行卡被锁定了怎么办 | 400 | — | — | — | — | 2 | ✅ PASS |
| ERR-027 | error | 请帮我查询最近的交易记录 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-028 | error | 如何设置自动还款功能 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-029 | error | 我想了解理财产品的收益率 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-030 | error | 如何办理外汇兑换业务 | 400 | — | — | — | — | 9 | ✅ PASS |
| ERR-031 | error | 请帮我查询账户余额 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-032 | error | 如何修改我的转账限额 | 400 | — | — | — | — | 2 | ✅ PASS |
| ERR-033 | error | 我想申请一张信用卡 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-034 | error | 请解释一下贷款利率的计算方式 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-035 | error | 如何开通网上银行功能 | 400 | — | — | — | — | 8 | ✅ PASS |
| ERR-036 | error | 我的银行卡被锁定了怎么办 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-037 | error | 请帮我查询最近的交易记录 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-038 | error | 如何设置自动还款功能 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-039 | error | 我想了解理财产品的收益率 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-040 | error | 如何办理外汇兑换业务 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-041 | error | 请帮我查询账户余额 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-042 | error | 如何修改我的转账限额 | 400 | — | — | — | — | 3 | ✅ PASS |
| ERR-043 | error | 我想申请一张信用卡 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-044 | error | 请解释一下贷款利率的计算方式 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-045 | error | 如何开通网上银行功能 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-046 | error | 我的银行卡被锁定了怎么办 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 402 | ❌ FAIL |
| ERR-047 | error | 请帮我查询最近的交易记录 | 200 | simple | — | qwen2.5:0.5b | miss | 386 | ❌ FAIL |
| ERR-048 | error | 如何设置自动还款功能 | 200 | complex | — | gpt-oss:20b | miss | 1549 | ❌ FAIL |
| ERR-049 | error | 我想了解理财产品的收益率 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 348 | ❌ FAIL |
| ERR-050 | error | 如何办理外汇兑换业务 | 200 | simple | — | qwen2.5:0.5b | miss | 352 | ❌ FAIL |
| ERR-051 | error | 请帮我查询账户余额 | 200 | complex | — | gpt-oss:20b | miss | 1545 | ❌ FAIL |
| ERR-052 | error | 如何修改我的转账限额 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 369 | ❌ FAIL |
| ERR-053 | error | 我想申请一张信用卡 | 200 | simple | — | qwen2.5:0.5b | miss | 443 | ❌ FAIL |
| ERR-054 | error | 请解释一下贷款利率的计算方式 | 200 | complex | — | gpt-oss:20b | miss | 1663 | ❌ FAIL |
| ERR-055 | error | 如何开通网上银行功能 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 391 | ❌ FAIL |
| ERR-056 | error | 我的银行卡被锁定了怎么办 | 200 | simple | — | qwen2.5:0.5b | miss | 446 | ❌ FAIL |
| ERR-057 | error | 请帮我查询最近的交易记录 | 200 | complex | — | gpt-oss:20b | miss | 1698 | ❌ FAIL |
| ERR-058 | error | 如何设置自动还款功能 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 387 | ❌ FAIL |
| ERR-059 | error | 我想了解理财产品的收益率 | 200 | simple | — | qwen2.5:0.5b | miss | 416 | ❌ FAIL |
| ERR-060 | error | 如何办理外汇兑换业务 | 200 | complex | — | gpt-oss:20b | miss | 1681 | ❌ FAIL |
| ERR-061 | error | 请帮我查询账户余额 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 381 | ❌ FAIL |
| ERR-062 | error | 如何修改我的转账限额 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-063 | error | 我想申请一张信用卡 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 379 | ❌ FAIL |
| ERR-064 | error | 请解释一下贷款利率的计算方式 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 380 | ❌ FAIL |
| ERR-065 | error | 如何开通网上银行功能 | 200 | — | — | qwen2.5:0.5b | hit:exact | 2 | ❌ FAIL |
| ERR-066 | error | 我的银行卡被锁定了怎么办 | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ❌ FAIL |
| ERR-067 | error | 请帮我查询最近的交易记录 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 659 | ❌ FAIL |
| ERR-068 | error | 如何设置自动还款功能 | 200 | — | — | qwen2.5:0.5b | hit:exact | 2 | ❌ FAIL |
| ERR-069 | error | 我想了解理财产品的收益率 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| ERR-070 | error | 如何办理外汇兑换业务 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 427 | ❌ FAIL |
| ERR-071 | error | 请帮我查询账户余额 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| ERR-072 | error | 如何修改我的转账限额 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-073 | error | 我想申请一张信用卡 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-074 | error | 请解释一下贷款利率的计算方式 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| ERR-075 | error | 如何开通网上银行功能 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-076 | error | 我的银行卡被锁定了怎么办 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-077 | error | 请帮我查询最近的交易记录 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| ERR-078 | error | 如何设置自动还款功能 | 200 | — | — | qwen2.5:0.5b | hit:exact | 10 | ❌ FAIL |
| ERR-079 | error | 我想了解理财产品的收益率 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| ERR-080 | error | 如何办理外汇兑换业务 | 200 | — | — | qwen2.5:0.5b | hit:exact | 59 | ❌ FAIL |
| ERR-081 | error | 请帮我查询账户余额 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-082 | error | 如何修改我的转账限额 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| ERR-083 | error | 我想申请一张信用卡 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ❌ FAIL |
| ERR-084 | error | 请解释一下贷款利率的计算方式 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ❌ FAIL |
| ERR-085 | error | 如何开通网上银行功能 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ❌ FAIL |
| ERR-086 | error | 我的银行卡被锁定了怎么办 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 199 | ❌ FAIL |
| ERR-087 | error | 请帮我查询最近的交易记录 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 151 | ❌ FAIL |
| ERR-088 | error | 如何设置自动还款功能 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 153 | ❌ FAIL |
| ERR-089 | error | 我想了解理财产品的收益率 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 153 | ❌ FAIL |
| ERR-090 | error | 如何办理外汇兑换业务 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 153 | ❌ FAIL |
| ERR-091 | error | 请帮我查询账户余额 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 158 | ❌ FAIL |
| ERR-092 | error | 如何修改我的转账限额 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 195 | ❌ FAIL |
| ERR-093 | error | 我想申请一张信用卡 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 148 | ❌ FAIL |
| ERR-094 | error | 请解释一下贷款利率的计算方式 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 151 | ❌ FAIL |
| ERR-095 | error | 如何开通网上银行功能 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 139 | ❌ FAIL |
| ERR-096 | error | 我的银行卡被锁定了怎么办 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 175 | ❌ FAIL |
| ERR-097 | error | 请帮我查询最近的交易记录 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 143 | ❌ FAIL |
| ERR-098 | error | 如何设置自动还款功能 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 160 | ❌ FAIL |
| ERR-099 | error | 我想了解理财产品的收益率 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 150 | ❌ FAIL |
| ERR-100 | error | 如何办理外汇兑换业务 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 158 | ❌ FAIL |
| LEG-001 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 389 | ✅ PASS |
| LEG-002 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 412 | ✅ PASS |
| LEG-003 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 387 | ✅ PASS |
| LEG-004 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 388 | ✅ PASS |
| LEG-005 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 393 | ✅ PASS |
| LEG-006 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 385 | ✅ PASS |
| LEG-007 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 374 | ✅ PASS |
| LEG-008 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 375 | ✅ PASS |
| LEG-009 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 368 | ✅ PASS |
| LEG-010 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 382 | ✅ PASS |
| LEG-011 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-012 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-013 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 8 | ✅ PASS |
| LEG-014 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-015 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 7 | ✅ PASS |
| LEG-016 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-017 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-018 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-019 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-020 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-021 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-022 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-023 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-024 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-025 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-026 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-027 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 7 | ✅ PASS |
| LEG-028 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 3 | ✅ PASS |
| LEG-029 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 7 | ✅ PASS |
| LEG-030 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-031 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 1646 | ✅ PASS |
| LEG-032 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 1544 | ✅ PASS |
| LEG-033 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 1547 | ✅ PASS |
| LEG-034 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 1606 | ✅ PASS |
| LEG-035 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 1618 | ✅ PASS |
| LEG-036 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 1598 | ✅ PASS |
| LEG-037 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 1698 | ✅ PASS |
| LEG-038 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 1597 | ✅ PASS |
| LEG-039 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 1598 | ✅ PASS |
| LEG-040 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 1683 | ✅ PASS |
| LEG-041 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-042 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 11 | ✅ PASS |
| LEG-043 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-044 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-045 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 22 | ✅ PASS |
| LEG-046 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-047 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-048 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-049 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-050 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-051 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-052 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-053 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-054 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-055 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-056 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-057 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-058 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-059 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 3 | ✅ PASS |
| LEG-060 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 3 | ✅ PASS |
| LEG-061 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 447 | ✅ PASS |
| LEG-062 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 383 | ✅ PASS |
| LEG-063 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 394 | ✅ PASS |
| LEG-064 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 394 | ✅ PASS |
| LEG-065 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 399 | ✅ PASS |
| LEG-066 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 395 | ✅ PASS |
| LEG-067 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 382 | ✅ PASS |
| LEG-068 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 358 | ✅ PASS |
| LEG-069 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 394 | ✅ PASS |
| LEG-070 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 396 | ✅ PASS |
| LEG-071 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-072 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 8 | ✅ PASS |
| LEG-073 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-074 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 28 | ✅ PASS |
| LEG-075 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-076 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 4 | ✅ PASS |
| LEG-077 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 6 | ✅ PASS |
| LEG-078 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 8 | ✅ PASS |
| LEG-079 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-080 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 5 | ✅ PASS |
| LEG-081 | legacy | 请帮我分析这份财务报表的关键指标 | 200 | — | — | — | — | 193 | ✅ PASS |
| LEG-082 | legacy | 解释一下资产负债表的构成和阅读方法 | 200 | — | — | — | — | 149 | ✅ PASS |
| LEG-083 | legacy | 如何评估一个企业的偿债能力 | 200 | — | — | — | — | 141 | ✅ PASS |
| LEG-084 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 200 | — | — | — | — | 135 | ✅ PASS |
| LEG-085 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 200 | — | — | — | — | 134 | ✅ PASS |
| LEG-086 | legacy | 如何计算企业的加权平均资本成本（WACC） | 404 | — | — | — | — | 139 | ✅ PASS |
| LEG-087 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 404 | — | — | — | — | 134 | ✅ PASS |
| LEG-088 | legacy | 解释一下信用利差和收益率曲线的关系 | 404 | — | — | — | — | 130 | ✅ PASS |
| LEG-089 | legacy | 如何评估债券投资的久期和凸性风险 | 404 | — | — | — | — | 132 | ✅ PASS |
| LEG-090 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 404 | — | — | — | — | 141 | ✅ PASS |
| LEG-091 | legacy | 请帮我分析这份财务报表的关键指标 | 400 | — | — | — | — | 7 | ❌ FAIL |
| LEG-092 | legacy | 解释一下资产负债表的构成和阅读方法 | 400 | — | — | — | — | 3 | ❌ FAIL |
| LEG-093 | legacy | 如何评估一个企业的偿债能力 | 400 | — | — | — | — | 4 | ❌ FAIL |
| LEG-094 | legacy | 请说明现金流量表中经营活动现金流的计算方法 | 400 | — | — | — | — | 6 | ❌ FAIL |
| LEG-095 | legacy | 解释市盈率、市净率、市销率三个估值指标的含义和适用场景 | 400 | — | — | — | — | 2 | ❌ FAIL |
| LEG-096 | legacy | 如何计算企业的加权平均资本成本（WACC） | 200 | — | — | — | — | 5 | ❌ FAIL |
| LEG-097 | legacy | 请介绍杜邦分析法的核心指标和分解逻辑 | 200 | — | — | — | — | 4 | ❌ FAIL |
| LEG-098 | legacy | 解释一下信用利差和收益率曲线的关系 | 200 | — | — | — | — | 6 | ❌ FAIL |
| LEG-099 | legacy | 如何评估债券投资的久期和凸性风险 | 200 | — | — | — | — | 5 | ❌ FAIL |
| LEG-100 | legacy | 请说明期权定价中希腊字母 Delta、Gamma、The | 200 | — | — | — | — | 4 | ❌ FAIL |
| CH-001 | cache | 请说明银行定期存款提前支取的利息计算规则，包括活期利率转 | 200 | simple | — | qwen2.5:0.5b | miss | 386 | ✅ PASS |
| CH-002 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-003 | cache | 解释 Python 中装饰器（decorator）的工作 | 200 | simple | — | qwen2.5:0.5b | miss | 425 | ✅ PASS |
| CH-004 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-005 | cache | 什么是 JWT（JSON Web Token）？请说明其 | 200 | simple | — | qwen2.5:0.5b | miss | 360 | ✅ PASS |
| CH-006 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| CH-007 | cache | 请介绍 Docker 容器和虚拟机的核心区别，包括资源隔 | 200 | simple | — | qwen2.5:0.5b | miss | 348 | ✅ PASS |
| CH-008 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-009 | cache | 解释数据库事务的 ACID 特性，并举例说明隔离级别从低 | 200 | simple | — | qwen2.5:0.5b | miss | 364 | ✅ PASS |
| CH-010 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-011 | cache | 请说明 HTTPS 的握手过程，包括证书验证、密钥交换和 | 200 | simple | — | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| CH-012 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-013 | cache | 什么是 Redis 的持久化机制？请对比 RDB 快照和 | 200 | simple | — | qwen2.5:0.5b | miss | 354 | ✅ PASS |
| CH-014 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-015 | cache | 解释 Kubernetes 中 Pod、Deployme | 200 | simple | — | qwen2.5:0.5b | miss | 361 | ✅ PASS |
| CH-016 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-017 | cache | 请介绍 OAuth 2.0 的四种授权模式，分别适用于什 | 200 | simple | — | qwen2.5:0.5b | miss | 367 | ✅ PASS |
| CH-018 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 11 | ✅ PASS |
| CH-019 | cache | 什么是微服务中的 Saga 模式？请说明 Choreog | 200 | simple | — | qwen2.5:0.5b | miss | 370 | ✅ PASS |
| CH-020 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-021 | cache | 请说明银行定期存款提前支取的利息计算规则，包括活期利率转 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| CH-022 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-023 | cache | 解释 Python 中装饰器（decorator）的工作 | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| CH-024 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-025 | cache | 什么是 JWT（JSON Web Token）？请说明其 | 200 | — | — | qwen2.5:0.5b | hit:exact | 2 | ✅ PASS |
| CH-026 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-027 | cache | 请介绍 Docker 容器和虚拟机的核心区别，包括资源隔 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-028 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 9 | ✅ PASS |
| CH-029 | cache | 解释数据库事务的 ACID 特性，并举例说明隔离级别从低 | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-030 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-031 | cache | 请说明 HTTPS 的握手过程，包括证书验证、密钥交换和 | 200 | — | — | qwen2.5:0.5b | hit:exact | 8 | ✅ PASS |
| CH-032 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-033 | cache | 什么是 Redis 的持久化机制？请对比 RDB 快照和 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-034 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 10 | ✅ PASS |
| CH-035 | cache | 解释 Kubernetes 中 Pod、Deployme | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-036 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 8 | ✅ PASS |
| CH-037 | cache | 请介绍 OAuth 2.0 的四种授权模式，分别适用于什 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-038 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-039 | cache | 什么是微服务中的 Saga 模式？请说明 Choreog | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-040 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-041 | cache | 请说明银行定期存款提前支取的利息计算规则，包括活期利率转 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-042 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-043 | cache | 解释 Python 中装饰器（decorator）的工作 | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-044 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 12 | ✅ PASS |
| CH-045 | cache | 什么是 JWT（JSON Web Token）？请说明其 | 200 | — | — | qwen2.5:0.5b | hit:exact | 8 | ✅ PASS |
| CH-046 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-047 | cache | 请介绍 Docker 容器和虚拟机的核心区别，包括资源隔 | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-048 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-049 | cache | 解释数据库事务的 ACID 特性，并举例说明隔离级别从低 | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-050 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-051 | cache | 请说明 HTTPS 的握手过程，包括证书验证、密钥交换和 | 200 | complex | — | gpt-oss:20b | miss | 1289 | ✅ PASS |
| CH-052 | cache | 什么是 Redis 的持久化机制？请对比 RDB 快照和 | 200 | complex | — | gpt-oss:20b | miss | 1646 | ❌ FAIL |
| CH-053 | cache | 解释 Kubernetes 中 Pod、Deployme | 200 | complex | — | gpt-oss:20b | miss | 1270 | ✅ PASS |
| CH-054 | cache | 请介绍 OAuth 2.0 的四种授权模式，分别适用于什 | 200 | complex | — | gpt-oss:20b | miss | 1613 | ❌ FAIL |
| CH-055 | cache | 什么是微服务中的 Saga 模式？请说明 Choreog | 200 | complex | — | gpt-oss:20b | miss | 1237 | ✅ PASS |
| CH-056 | cache | 请说明银行定期存款提前支取的利息计算规则，包括活期利率转 | 200 | complex | — | gpt-oss:20b | miss | 1634 | ❌ FAIL |
| CH-057 | cache | 解释 Python 中装饰器（decorator）的工作 | 200 | complex | — | gpt-oss:20b | miss | 1241 | ✅ PASS |
| CH-058 | cache | 什么是 JWT（JSON Web Token）？请说明其 | 200 | complex | — | gpt-oss:20b | miss | 1609 | ❌ FAIL |
| CH-059 | cache | 请介绍 Docker 容器和虚拟机的核心区别，包括资源隔 | 200 | complex | — | gpt-oss:20b | miss | 1231 | ✅ PASS |
| CH-060 | cache | 解释数据库事务的 ACID 特性，并举例说明隔离级别从低 | 200 | complex | — | gpt-oss:20b | miss | 1618 | ❌ FAIL |
| CH-061 | cache | 请说明 HTTPS 的握手过程，包括证书验证、密钥交换和 | 200 | complex | — | gpt-oss:20b | miss | 1227 | ✅ PASS |
| CH-062 | cache | 什么是 Redis 的持久化机制？请对比 RDB 快照和 | 200 | complex | — | gpt-oss:20b | miss | 1636 | ❌ FAIL |
| CH-063 | cache | 解释 Kubernetes 中 Pod、Deployme | 200 | — | — | gpt-oss:20b | hit:semantic | 165 | ✅ PASS |
| CH-064 | cache | 请介绍 OAuth 2.0 的四种授权模式，分别适用于什 | 200 | — | — | gpt-oss:20b | hit:semantic | 195 | ✅ PASS |
| CH-065 | cache | 什么是微服务中的 Saga 模式？请说明 Choreog | 200 | complex | — | gpt-oss:20b | miss | 1264 | ✅ PASS |
| CH-066 | cache | 请说明银行定期存款提前支取的利息计算规则，包括活期利率转 | 200 | complex | — | gpt-oss:20b | miss | 1642 | ❌ FAIL |
| CH-067 | cache | 解释 Python 中装饰器（decorator）的工作 | 200 | complex | — | gpt-oss:20b | miss | 1287 | ✅ PASS |
| CH-068 | cache | 什么是 JWT（JSON Web Token）？请说明其 | 200 | complex | — | gpt-oss:20b | miss | 1621 | ❌ FAIL |
| CH-069 | cache | 请介绍 Docker 容器和虚拟机的核心区别，包括资源隔 | 200 | — | — | gpt-oss:20b | hit:semantic | 153 | ✅ PASS |
| CH-070 | cache | 解释数据库事务的 ACID 特性，并举例说明隔离级别从低 | 200 | complex | — | gpt-oss:20b | miss | 1621 | ❌ FAIL |
| CH-071 | cache | 请说明 HTTPS 的握手过程，包括证书验证、密钥交换和 | 200 | complex | — | gpt-oss:20b | miss | 1236 | ✅ PASS |
| CH-072 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 343 | ✅ PASS |
| CH-073 | cache | 什么是 Redis 的持久化机制？请对比 RDB 快照和 | 200 | — | — | gpt-oss:20b | hit:semantic | 170 | ✅ PASS |
| CH-074 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 302 | ✅ PASS |
| CH-075 | cache | 解释 Kubernetes 中 Pod、Deployme | 200 | complex | — | gpt-oss:20b | miss | 1254 | ✅ PASS |
| CH-076 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 318 | ✅ PASS |
| CH-077 | cache | 请介绍 OAuth 2.0 的四种授权模式，分别适用于什 | 200 | complex | — | gpt-oss:20b | miss | 1238 | ✅ PASS |
| CH-078 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 313 | ✅ PASS |
| CH-079 | cache | 什么是微服务中的 Saga 模式？请说明 Choreog | 200 | complex | — | gpt-oss:20b | miss | 1270 | ✅ PASS |
| CH-080 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:semantic | 162 | ❌ FAIL |
| CH-081 | cache | 请说明银行定期存款提前支取的利息计算规则，包括活期利率转 | 200 | complex | — | gpt-oss:20b | miss | 1277 | ✅ PASS |
| CH-082 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 379 | ✅ PASS |
| CH-083 | cache | 解释 Python 中装饰器（decorator）的工作 | 200 | complex | — | gpt-oss:20b | miss | 1279 | ✅ PASS |
| CH-084 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 359 | ✅ PASS |
| CH-085 | cache | 什么是 JWT（JSON Web Token）？请说明其 | 200 | complex | — | gpt-oss:20b | miss | 1261 | ✅ PASS |
| CH-086 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 354 | ✅ PASS |
| CH-087 | cache | 请介绍 Docker 容器和虚拟机的核心区别，包括资源隔 | 200 | complex | — | gpt-oss:20b | miss | 1370 | ✅ PASS |
| CH-088 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:semantic | 207 | ❌ FAIL |
| CH-089 | cache | 解释数据库事务的 ACID 特性，并举例说明隔离级别从低 | 200 | complex | — | gpt-oss:20b | miss | 1333 | ✅ PASS |
| CH-090 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 377 | ✅ PASS |
| CH-091 | cache | 请说明 HTTPS 的握手过程，包括证书验证、密钥交换和 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 199 | ✅ PASS |
| CH-092 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-093 | cache | 什么是 Redis 的持久化机制？请对比 RDB 快照和 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 186 | ✅ PASS |
| CH-094 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-095 | cache | 解释 Kubernetes 中 Pod、Deployme | 200 | — | — | qwen2.5:0.5b | hit:semantic | 190 | ✅ PASS |
| CH-096 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| CH-097 | cache | 请介绍 OAuth 2.0 的四种授权模式，分别适用于什 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 179 | ✅ PASS |
| CH-098 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 8 | ✅ PASS |
| CH-099 | cache | 什么是微服务中的 Saga 模式？请说明 Choreog | 200 | — | — | qwen2.5:0.5b | hit:semantic | 234 | ✅ PASS |
| CH-100 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| ST-001 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 371 | ✅ PASS |
| ST-002 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| ST-003 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 336 | ✅ PASS |
| ST-004 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 344 | ✅ PASS |
| ST-005 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 337 | ✅ PASS |
| ST-006 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| ST-007 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 336 | ✅ PASS |
| ST-008 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 330 | ✅ PASS |
| ST-009 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 335 | ✅ PASS |
| ST-010 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 335 | ✅ PASS |
| ST-011 | stream | 介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 339 | ✅ PASS |
| ST-012 | stream | 请详细说明 TCP 三次握手和四次挥手的过程，以及为什么 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 349 | ✅ PASS |
| ST-013 | stream | 如何设计一个支持百万并发的实时推送系统？请给出技术选型和 | 200 | complex | 0.5 | gpt-oss:20b | miss | 1364 | ❌ FAIL |
| ST-014 | stream | 介绍深度学习中的注意力机制（Attention），从 R | 200 | simple | 0.3 | qwen2.5:0.5b | miss | 329 | ✅ PASS |
| ST-015 | stream | 请解释区块链的工作原理，包括区块结构、共识机制、智能合约 | 200 | simple | 0.30000000000000004 | qwen2.5:0.5b | miss | 327 | ✅ PASS |
| ST-016 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | — | — | — | hit:exact | 3 | ❌ FAIL |
| ST-017 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | — | — | — | hit:exact | 3 | ❌ FAIL |
| ST-018 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-019 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-020 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-021 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-022 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-023 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | — | — | — | hit:exact | 7 | ❌ FAIL |
| ST-024 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-025 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-026 | stream | 介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-027 | stream | 请详细说明 TCP 三次握手和四次挥手的过程，以及为什么 | 200 | — | — | — | hit:exact | 3 | ❌ FAIL |
| ST-028 | stream | 如何设计一个支持百万并发的实时推送系统？请给出技术选型和 | 200 | — | — | — | hit:exact | 3 | ❌ FAIL |
| ST-029 | stream | 介绍深度学习中的注意力机制（Attention），从 R | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-030 | stream | 请解释区块链的工作原理，包括区块结构、共识机制、智能合约 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-031 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-032 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-033 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-034 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | — | — | — | hit:exact | 7 | ❌ FAIL |
| ST-035 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-036 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | — | — | — | hit:exact | 3 | ❌ FAIL |
| ST-037 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-038 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-039 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-040 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-041 | stream | 介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-042 | stream | 请详细说明 TCP 三次握手和四次挥手的过程，以及为什么 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-043 | stream | 如何设计一个支持百万并发的实时推送系统？请给出技术选型和 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-044 | stream | 介绍深度学习中的注意力机制（Attention），从 R | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-045 | stream | 请解释区块链的工作原理，包括区块结构、共识机制、智能合约 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-046 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-047 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | — | — | — | hit:exact | 4 | ❌ FAIL |
| ST-048 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | — | — | — | hit:exact | 6 | ❌ FAIL |
| ST-049 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-050 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | — | — | — | hit:exact | 5 | ❌ FAIL |
| ST-051 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | simple | — | qwen2.5:0.5b | miss | 381 | ✅ PASS |
| ST-052 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | simple | — | qwen2.5:0.5b | miss | 331 | ✅ PASS |
| ST-053 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | simple | — | qwen2.5:0.5b | miss | 321 | ✅ PASS |
| ST-054 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | simple | — | qwen2.5:0.5b | miss | 319 | ✅ PASS |
| ST-055 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | simple | — | qwen2.5:0.5b | miss | 332 | ✅ PASS |
| ST-056 | stream | 介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工 | 200 | simple | — | qwen2.5:0.5b | miss | 333 | ✅ PASS |
| ST-057 | stream | 请详细说明 TCP 三次握手和四次挥手的过程，以及为什么 | 200 | simple | — | qwen2.5:0.5b | miss | 326 | ✅ PASS |
| ST-058 | stream | 如何设计一个支持百万并发的实时推送系统？请给出技术选型和 | 200 | simple | — | qwen2.5:0.5b | miss | 329 | ✅ PASS |
| ST-059 | stream | 介绍深度学习中的注意力机制（Attention），从 R | 200 | simple | — | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| ST-060 | stream | 请解释区块链的工作原理，包括区块结构、共识机制、智能合约 | 200 | simple | — | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| ST-061 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | complex | — | gpt-oss:20b | miss | 1349 | ✅ PASS |
| ST-062 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | complex | — | gpt-oss:20b | miss | 1326 | ✅ PASS |
| ST-063 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | complex | — | gpt-oss:20b | miss | 1368 | ✅ PASS |
| ST-064 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | complex | — | gpt-oss:20b | miss | 1389 | ✅ PASS |
| ST-065 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | complex | — | gpt-oss:20b | miss | 1319 | ✅ PASS |
| ST-066 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | complex | — | gpt-oss:20b | miss | 1337 | ✅ PASS |
| ST-067 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | complex | — | gpt-oss:20b | miss | 1387 | ✅ PASS |
| ST-068 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | complex | — | gpt-oss:20b | miss | 1397 | ✅ PASS |
| ST-069 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | complex | — | gpt-oss:20b | miss | 1412 | ✅ PASS |
| ST-070 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | complex | — | gpt-oss:20b | miss | 1390 | ✅ PASS |
| ST-071 | stream | 介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工 | 200 | — | — | — | hit:exact | 2 | ✅ PASS |
| ST-072 | stream | 请详细说明 TCP 三次握手和四次挥手的过程，以及为什么 | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-073 | stream | 如何设计一个支持百万并发的实时推送系统？请给出技术选型和 | 200 | — | — | — | hit:exact | 7 | ✅ PASS |
| ST-074 | stream | 介绍深度学习中的注意力机制（Attention），从 R | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-075 | stream | 请解释区块链的工作原理，包括区块结构、共识机制、智能合约 | 200 | — | — | — | hit:exact | 3 | ✅ PASS |
| ST-076 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | — | — | — | hit:exact | 8 | ✅ PASS |
| ST-077 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | — | — | — | hit:exact | 6 | ✅ PASS |
| ST-078 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | — | — | — | hit:exact | 38 | ✅ PASS |
| ST-079 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | — | — | — | hit:exact | 6 | ✅ PASS |
| ST-080 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | — | — | — | hit:exact | 7 | ✅ PASS |
| ST-081 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | — | — | — | hit:exact | 4 | ✅ PASS |
| ST-082 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | — | — | — | hit:exact | 3 | ✅ PASS |
| ST-083 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-084 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | — | — | — | hit:exact | 3 | ✅ PASS |
| ST-085 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-086 | stream | 介绍中国茶文化的历史渊源，包括绿茶、红茶、乌龙茶的制作工 | 200 | — | — | — | hit:exact | 4 | ✅ PASS |
| ST-087 | stream | 请详细说明 TCP 三次握手和四次挥手的过程，以及为什么 | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-088 | stream | 如何设计一个支持百万并发的实时推送系统？请给出技术选型和 | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-089 | stream | 介绍深度学习中的注意力机制（Attention），从 R | 200 | — | — | — | hit:exact | 6 | ✅ PASS |
| ST-090 | stream | 请解释区块链的工作原理，包括区块结构、共识机制、智能合约 | 200 | — | — | — | hit:exact | 5 | ✅ PASS |
| ST-091 | stream | 详细介绍长城的历史演变，从秦朝到明朝的修建过程 | 200 | — | — | — | hit:semantic | 196 | ❌ FAIL |
| ST-092 | stream | 如何制作一杯正宗的意式浓缩咖啡？请分步骤说明 | 200 | — | — | — | hit:semantic | 144 | ❌ FAIL |
| ST-093 | stream | 什么是人工智能？请从定义、发展历程、应用领域三个方面讲解 | 200 | — | — | — | hit:semantic | 147 | ❌ FAIL |
| ST-094 | stream | 讲解摄影的基本技巧，包括构图、光线、色彩三个维度 | 200 | — | — | — | hit:semantic | 158 | ❌ FAIL |
| ST-095 | stream | 介绍古希腊哲学的主要流派和代表人物 | 200 | — | — | — | hit:semantic | 151 | ❌ FAIL |
| ST-096 | stream | 如何系统学习一门外语？请给出从零基础到流利的学习路径 | 200 | — | — | — | hit:semantic | 160 | ❌ FAIL |
| ST-097 | stream | 什么是基因编辑技术 CRISPR-Cas9？请解释其原理 | 200 | — | — | — | hit:semantic | 162 | ❌ FAIL |
| ST-098 | stream | 介绍世界著名的五大博物馆及其镇馆之宝 | 200 | — | — | — | hit:semantic | 161 | ❌ FAIL |
| ST-099 | stream | 请解释量子纠缠现象，用通俗的语言让非物理专业的人也能理解 | 200 | — | — | — | hit:semantic | 157 | ❌ FAIL |
| ST-100 | stream | 如何搭建一个高可用的微服务架构？请从服务注册、负载均衡、 | 200 | — | — | — | hit:semantic | 173 | ❌ FAIL |
| RATE-001 | rate | 查询我的信用卡账单明细 | 200 | — | — | — | — | 2258 | ✅ PASS |
| RATE-002 | rate | 帮我计算这笔贷款的月供 | 200 | — | — | — | — | 2858 | ✅ PASS |
| RATE-003 | rate | 解释一下等额本息和等额本金的区别 | 200 | — | — | — | — | 4221 | ✅ PASS |
| RATE-004 | rate | 如何申请提高信用卡临时额度 | 200 | — | — | — | — | 3689 | ✅ PASS |
| RATE-005 | rate | 请说明个人征信报告的查询方式 | 200 | — | — | — | — | 3712 | ✅ PASS |
| RATE-006 | rate | 我想了解房贷利率的最新政策 | 200 | — | — | — | — | 6120 | ✅ PASS |
| RATE-007 | rate | 如何办理银行卡挂失补卡 | 200 | — | — | — | — | 3777 | ✅ PASS |
| RATE-008 | rate | 请解释一下存款保险制度 | 200 | — | — | — | — | 7821 | ✅ PASS |
| RATE-009 | rate | 如何开通国际漫游服务 | 200 | — | — | — | — | 6142 | ✅ PASS |
| RATE-010 | rate | 我想了解基金定投的策略 | 200 | — | — | — | — | 4336 | ✅ PASS |
| RATE-011 | rate | 查询我的信用卡账单明细 | 200 | — | — | — | — | 152 | ✅ PASS |
| RATE-012 | rate | 帮我计算这笔贷款的月供 | 429 | — | — | — | — | 68 | ✅ PASS |
| RATE-013 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 44 | ✅ PASS |
| RATE-014 | rate | 如何申请提高信用卡临时额度 | 429 | — | — | — | — | 32 | ✅ PASS |
| RATE-015 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 27 | ✅ PASS |
| RATE-016 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 25 | ✅ PASS |
| RATE-017 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 20 | ✅ PASS |
| RATE-018 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 23 | ✅ PASS |
| RATE-019 | rate | 如何开通国际漫游服务 | 200 | — | — | — | — | 30 | ✅ PASS |
| RATE-020 | rate | 我想了解基金定投的策略 | 429 | — | — | — | — | 26 | ✅ PASS |
| RATE-021 | rate | 查询我的信用卡账单明细 | 429 | — | — | — | — | 17 | ✅ PASS |
| RATE-022 | rate | 帮我计算这笔贷款的月供 | 429 | — | — | — | — | 24 | ✅ PASS |
| RATE-023 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 20 | ✅ PASS |
| RATE-024 | rate | 如何申请提高信用卡临时额度 | 429 | — | — | — | — | 19 | ✅ PASS |
| RATE-025 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 14 | ✅ PASS |
| RATE-026 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 17 | ✅ PASS |
| RATE-027 | rate | 如何办理银行卡挂失补卡 | 200 | — | — | — | — | 20 | ✅ PASS |
| RATE-028 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 19 | ✅ PASS |
| RATE-029 | rate | 如何开通国际漫游服务 | 429 | — | — | — | — | 28 | ✅ PASS |
| RATE-030 | rate | 我想了解基金定投的策略 | 429 | — | — | — | — | 19 | ✅ PASS |
| RATE-031 | rate | 查询我的信用卡账单明细 | 429 | — | — | — | — | 23 | ✅ PASS |
| RATE-032 | rate | 帮我计算这笔贷款的月供 | 429 | — | — | — | — | 82 | ✅ PASS |
| RATE-033 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 19 | ✅ PASS |
| RATE-034 | rate | 如何申请提高信用卡临时额度 | 429 | — | — | — | — | 16 | ✅ PASS |
| RATE-035 | rate | 请说明个人征信报告的查询方式 | 200 | — | — | — | — | 17 | ✅ PASS |
| RATE-036 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 21 | ✅ PASS |
| RATE-037 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 20 | ✅ PASS |
| RATE-038 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 19 | ✅ PASS |
| RATE-039 | rate | 如何开通国际漫游服务 | 429 | — | — | — | — | 34 | ✅ PASS |
| RATE-040 | rate | 我想了解基金定投的策略 | 429 | — | — | — | — | 15 | ✅ PASS |
| RATE-041 | rate | 查询我的信用卡账单明细 | 429 | — | — | — | — | 4 | ❌ FAIL |
| RATE-042 | rate | 帮我计算这笔贷款的月供 | 429 | — | — | — | — | 8 | ❌ FAIL |
| RATE-043 | rate | 解释一下等额本息和等额本金的区别 | 200 | — | — | — | — | 5 | ✅ PASS |
| RATE-044 | rate | 如何申请提高信用卡临时额度 | 200 | — | — | — | — | 5 | ✅ PASS |
| RATE-045 | rate | 请说明个人征信报告的查询方式 | 200 | — | — | — | — | 7 | ✅ PASS |
| RATE-046 | rate | 我想了解房贷利率的最新政策 | 200 | — | — | — | — | 5 | ✅ PASS |
| RATE-047 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 7 | ❌ FAIL |
| RATE-048 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 6 | ❌ FAIL |
| RATE-049 | rate | 如何开通国际漫游服务 | 429 | — | — | — | — | 6 | ❌ FAIL |
| RATE-050 | rate | 我想了解基金定投的策略 | 429 | — | — | — | — | 5 | ❌ FAIL |
| RATE-051 | rate | 查询我的信用卡账单明细 | 200 | — | — | — | — | 6 | ✅ PASS |
| RATE-052 | rate | 帮我计算这笔贷款的月供 | 200 | — | — | — | — | 7 | ✅ PASS |
| RATE-053 | rate | 解释一下等额本息和等额本金的区别 | 200 | — | — | — | — | 5 | ✅ PASS |
| RATE-054 | rate | 如何申请提高信用卡临时额度 | 200 | — | — | — | — | 7 | ✅ PASS |
| RATE-055 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 8 | ❌ FAIL |
| RATE-056 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 7 | ❌ FAIL |
| RATE-057 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 7 | ❌ FAIL |
| RATE-058 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 8 | ❌ FAIL |
| RATE-059 | rate | 如何开通国际漫游服务 | 200 | — | — | — | — | 8 | ✅ PASS |
| RATE-060 | rate | 我想了解基金定投的策略 | 200 | — | — | — | — | 6 | ✅ PASS |
| RATE-061 | rate | 查询我的信用卡账单明细 | 200 | — | — | — | — | 5 | ✅ PASS |
| RATE-062 | rate | 帮我计算这笔贷款的月供 | 200 | — | — | — | — | 8 | ✅ PASS |
| RATE-063 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 6 | ❌ FAIL |
| RATE-064 | rate | 如何申请提高信用卡临时额度 | 429 | — | — | — | — | 6 | ❌ FAIL |
| RATE-065 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 6 | ❌ FAIL |
| RATE-066 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 5 | ❌ FAIL |
| RATE-067 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 8 | ❌ FAIL |
| RATE-068 | rate | 请解释一下存款保险制度 | 200 | — | — | — | — | 10 | ✅ PASS |
| RATE-069 | rate | 如何开通国际漫游服务 | 200 | — | — | — | — | 8 | ✅ PASS |
| RATE-070 | rate | 我想了解基金定投的策略 | 200 | — | — | — | — | 6 | ✅ PASS |
| RATE-071 | rate | 查询我的信用卡账单明细 | 200 | — | — | — | — | 7 | ✅ PASS |
| RATE-072 | rate | 帮我计算这笔贷款的月供 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-073 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 6 | ❌ FAIL |
| RATE-074 | rate | 如何申请提高信用卡临时额度 | 429 | — | — | — | — | 8 | ❌ FAIL |
| RATE-075 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 7 | ❌ FAIL |
| RATE-076 | rate | 我想了解房贷利率的最新政策 | 200 | — | — | — | — | 7 | ✅ PASS |
| RATE-077 | rate | 如何办理银行卡挂失补卡 | 200 | — | — | — | — | 6 | ✅ PASS |
| RATE-078 | rate | 请解释一下存款保险制度 | 200 | — | — | — | — | 6 | ✅ PASS |
| RATE-079 | rate | 如何开通国际漫游服务 | 200 | — | — | — | — | 6 | ✅ PASS |
| RATE-080 | rate | 我想了解基金定投的策略 | 429 | — | — | — | — | 8 | ❌ FAIL |
| RATE-081 | rate | 查询我的信用卡账单明细 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-082 | rate | 帮我计算这笔贷款的月供 | 429 | — | — | — | — | 14 | ❌ FAIL |
| RATE-083 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-084 | rate | 如何申请提高信用卡临时额度 | 200 | — | — | — | — | 13 | ✅ PASS |
| RATE-085 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 15 | ❌ FAIL |
| RATE-086 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 14 | ❌ FAIL |
| RATE-087 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 27 | ❌ FAIL |
| RATE-088 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 14 | ❌ FAIL |
| RATE-089 | rate | 如何开通国际漫游服务 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-090 | rate | 我想了解基金定投的策略 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-091 | rate | 查询我的信用卡账单明细 | 429 | — | — | — | — | 11 | ❌ FAIL |
| RATE-092 | rate | 帮我计算这笔贷款的月供 | 200 | — | — | — | — | 10 | ✅ PASS |
| RATE-093 | rate | 解释一下等额本息和等额本金的区别 | 429 | — | — | — | — | 9 | ❌ FAIL |
| RATE-094 | rate | 如何申请提高信用卡临时额度 | 429 | — | — | — | — | 14 | ❌ FAIL |
| RATE-095 | rate | 请说明个人征信报告的查询方式 | 429 | — | — | — | — | 12 | ❌ FAIL |
| RATE-096 | rate | 我想了解房贷利率的最新政策 | 429 | — | — | — | — | 14 | ❌ FAIL |
| RATE-097 | rate | 如何办理银行卡挂失补卡 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-098 | rate | 请解释一下存款保险制度 | 429 | — | — | — | — | 13 | ❌ FAIL |
| RATE-099 | rate | 如何开通国际漫游服务 | 429 | — | — | — | — | 15 | ❌ FAIL |
| RATE-100 | rate | 我想了解基金定投的策略 | 200 | — | — | — | — | 25 | ✅ PASS |

## 三、失败用例断言明细

### DS-031
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-032
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-033
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-034
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-035
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-036
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-037
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-038
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-039
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-040
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-041
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-042
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-043
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-044
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-045
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-046
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-047
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-048
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-049
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DS-050
- ❌ intent=simple（实际 None）
- ❌ difficulty<0.5（实际 None）

### DC-001
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.45000000000000007）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-002
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.3）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-003
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.1）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-005
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.1）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-006
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.0）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-007
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.30000000000000004）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-008
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.30000000000000004）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-009
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.30000000000000004）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-010
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.4）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-012
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.44999999999999996）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-015
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.3）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-018
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.30000000000000004）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-019
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.3）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-020
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.1）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-021
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.4）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-022
- ❌ intent=complex（实际 simple）
- ❌ difficulty>=0.5（实际 0.2）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-023
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-024
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-025
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-026
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-027
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-028
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-029
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-030
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-031
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-032
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-033
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-034
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-035
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-036
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-037
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-038
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-039
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-040
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-041
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-042
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-043
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-044
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-045
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-046
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-047
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-048
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）

### DC-049
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### DC-050
- ❌ intent=complex（实际 None）
- ❌ difficulty>=0.5（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen2.5:0.5b）

### EXP-031
- ❌ intent=simple（实际 None）

### EXP-032
- ❌ intent=simple（实际 None）

### EXP-033
- ❌ intent=simple（实际 None）

### EXP-034
- ❌ intent=simple（实际 None）

### EXP-035
- ❌ intent=simple（实际 None）

### EXP-036
- ❌ intent=simple（实际 None）

### EXP-037
- ❌ intent=simple（实际 None）

### EXP-038
- ❌ intent=simple（实际 None）

### EXP-039
- ❌ intent=simple（实际 None）

### EXP-040
- ❌ intent=simple（实际 None）

### EXP-041
- ❌ intent=simple（实际 None）

### EXP-042
- ❌ intent=simple（实际 None）

### EXP-043
- ❌ intent=simple（实际 None）

### EXP-044
- ❌ intent=simple（实际 None）

### EXP-045
- ❌ intent=simple（实际 None）

### EXP-046
- ❌ intent=simple（实际 None）

### EXP-047
- ❌ intent=simple（实际 None）

### EXP-048
- ❌ intent=simple（实际 None）

### EXP-049
- ❌ intent=simple（实际 None）

### EXP-050
- ❌ intent=simple（实际 None）

### EXP-073
- ❌ intent=complex（实际 None）

### EXP-074
- ❌ intent=complex（实际 None）

### EXP-075
- ❌ intent=complex（实际 None）

### EXP-076
- ❌ intent=complex（实际 None）

### EXP-077
- ❌ intent=complex（实际 None）

### EXP-078
- ❌ intent=complex（实际 None）

### EXP-079
- ❌ intent=complex（实际 None）

### EXP-080
- ❌ intent=complex（实际 None）

### EXP-081
- ❌ intent=complex（实际 None）

### EXP-082
- ❌ intent=complex（实际 None）

### EXP-083
- ❌ intent=complex（实际 None）

### EXP-084
- ❌ intent=complex（实际 None）

### EXP-085
- ❌ intent=complex（实际 None）

### EXP-086
- ❌ intent=complex（实际 None）

### EXP-087
- ❌ intent=complex（实际 None）

### EXP-088
- ❌ intent=complex（实际 None）

### EXP-089
- ❌ intent=complex（实际 None）

### EXP-090
- ❌ intent=complex（实际 None）

### EXP-091
- ❌ intent=complex（实际 None）

### EXP-092
- ❌ intent=complex（实际 None）

### EXP-093
- ❌ intent=complex（实际 None）

### EXP-094
- ❌ intent=complex（实际 None）

### EXP-095
- ❌ intent=complex（实际 None）

### EXP-096
- ❌ intent=complex（实际 None）

### EXP-097
- ❌ intent=complex（实际 None）

### EXP-098
- ❌ intent=complex（实际 None）

### EXP-099
- ❌ intent=complex（实际 None）

### EXP-100
- ❌ intent=complex（实际 None）

### ERR-046
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-047
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-048
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-049
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-050
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-051
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-052
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-053
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-054
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-055
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-056
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-057
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-058
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-059
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-060
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）
- ❌ 错误类型=permission_error（实际 None）

### ERR-061
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-062
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-063
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-064
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-065
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-066
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-067
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-068
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-069
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-070
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-071
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-072
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-073
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-074
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-075
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-076
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-077
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-078
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-079
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-080
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-081
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-082
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-083
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-084
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-085
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）
- ❌ 错误类型=content_filter（实际 None）

### ERR-086
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-087
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-088
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-089
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-090
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-091
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-092
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-093
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-094
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-095
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-096
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-097
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-098
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-099
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ERR-100
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### LEG-091
- ❌ 旧协议 code=-1（实际 1000）

### LEG-092
- ❌ 旧协议 code=-1（实际 1000）

### LEG-093
- ❌ 旧协议 code=-1（实际 1000）

### LEG-094
- ❌ 旧协议 code=-1（实际 1000）

### LEG-095
- ❌ 旧协议 code=-1（实际 1000）

### LEG-096
- ❌ HTTP 403（实际 200）
- ❌ 旧协议 code=-1（实际 0）

### LEG-097
- ❌ HTTP 403（实际 200）
- ❌ 旧协议 code=-1（实际 0）

### LEG-098
- ❌ HTTP 403（实际 200）
- ❌ 旧协议 code=-1（实际 0）

### LEG-099
- ❌ HTTP 403（实际 200）
- ❌ 旧协议 code=-1（实际 0）

### LEG-100
- ❌ HTTP 403（实际 200）
- ❌ 旧协议 code=-1（实际 0）

### CH-052
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-054
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-056
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-058
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-060
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-062
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-066
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-068
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-070
- ❌ cache_hit=True（实际 False）
- ❌ cache_level=semantic（实际 None）

### CH-080
- ❌ cache_hit=False（实际 True）

### CH-088
- ❌ cache_hit=False（实际 True）

### ST-013
- ❌ intent=simple（实际 complex）

### ST-016
- ❌ intent=simple（实际 None）

### ST-017
- ❌ intent=simple（实际 None）

### ST-018
- ❌ intent=simple（实际 None）

### ST-019
- ❌ intent=simple（实际 None）

### ST-020
- ❌ intent=simple（实际 None）

### ST-021
- ❌ intent=simple（实际 None）

### ST-022
- ❌ intent=simple（实际 None）

### ST-023
- ❌ intent=simple（实际 None）

### ST-024
- ❌ intent=simple（实际 None）

### ST-025
- ❌ intent=simple（实际 None）

### ST-026
- ❌ intent=complex（实际 None）

### ST-027
- ❌ intent=complex（实际 None）

### ST-028
- ❌ intent=complex（实际 None）

### ST-029
- ❌ intent=complex（实际 None）

### ST-030
- ❌ intent=complex（实际 None）

### ST-031
- ❌ intent=complex（实际 None）

### ST-032
- ❌ intent=complex（实际 None）

### ST-033
- ❌ intent=complex（实际 None）

### ST-034
- ❌ intent=complex（实际 None）

### ST-035
- ❌ intent=complex（实际 None）

### ST-036
- ❌ intent=complex（实际 None）

### ST-037
- ❌ intent=complex（实际 None）

### ST-038
- ❌ intent=complex（实际 None）

### ST-039
- ❌ intent=complex（实际 None）

### ST-040
- ❌ intent=complex（实际 None）

### ST-041
- ❌ intent=complex（实际 None）

### ST-042
- ❌ intent=complex（实际 None）

### ST-043
- ❌ intent=complex（实际 None）

### ST-044
- ❌ intent=complex（实际 None）

### ST-045
- ❌ intent=complex（实际 None）

### ST-046
- ❌ intent=complex（实际 None）

### ST-047
- ❌ intent=complex（实际 None）

### ST-048
- ❌ intent=complex（实际 None）

### ST-049
- ❌ intent=complex（实际 None）

### ST-050
- ❌ intent=complex（实际 None）

### ST-091
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ST-092
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ST-093
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ST-094
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ST-095
- ❌ HTTP 400（实际 200）
- ❌ 错误码=input_blocked（实际 None）

### ST-096
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）

### ST-097
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）

### ST-098
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）

### ST-099
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）

### ST-100
- ❌ HTTP 403（实际 200）
- ❌ 错误码=blacklisted（实际 None）

### RATE-041
- ❌ 429 次数<1（实际 5）

### RATE-042
- ❌ 429 次数<1（实际 5）

### RATE-047
- ❌ 429 次数<1（实际 5）

### RATE-048
- ❌ 429 次数<1（实际 5）

### RATE-049
- ❌ 429 次数<1（实际 5）

### RATE-050
- ❌ 429 次数<1（实际 5）

### RATE-055
- ❌ 429 次数<1（实际 5）

### RATE-056
- ❌ 429 次数<1（实际 5）

### RATE-057
- ❌ 429 次数<1（实际 5）

### RATE-058
- ❌ 429 次数<1（实际 5）

### RATE-063
- ❌ 429 次数<1（实际 5）

### RATE-064
- ❌ 429 次数<1（实际 5）

### RATE-065
- ❌ 429 次数<1（实际 5）

### RATE-066
- ❌ 429 次数<1（实际 5）

### RATE-067
- ❌ 429 次数<1（实际 5）

### RATE-072
- ❌ 429 次数<1（实际 5）

### RATE-073
- ❌ 429 次数<1（实际 5）

### RATE-074
- ❌ 429 次数<1（实际 5）

### RATE-075
- ❌ 429 次数<1（实际 5）

### RATE-080
- ❌ 429 次数<1（实际 5）

### RATE-081
- ❌ 429 次数<3（实际 20）

### RATE-082
- ❌ 429 次数<3（实际 20）

### RATE-083
- ❌ 429 次数<3（实际 20）

### RATE-085
- ❌ 429 次数<3（实际 20）

### RATE-086
- ❌ 429 次数<3（实际 20）

### RATE-087
- ❌ 429 次数<3（实际 20）

### RATE-088
- ❌ 429 次数<3（实际 20）

### RATE-089
- ❌ 429 次数<3（实际 20）

### RATE-090
- ❌ 429 次数<3（实际 20）

### RATE-091
- ❌ 429 次数<3（实际 20）

### RATE-093
- ❌ 429 次数<3（实际 20）

### RATE-094
- ❌ 429 次数<3（实际 20）

### RATE-095
- ❌ 429 次数<3（实际 20）

### RATE-096
- ❌ 429 次数<3（实际 20）

### RATE-097
- ❌ 429 次数<3（实际 20）

### RATE-098
- ❌ 429 次数<3（实际 20）

### RATE-099
- ❌ 429 次数<3（实际 20）

## 四、单元测试明细（JUnit）

### MasExceptionTest（3/3）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| engineTimeoutPreservesCause | 0.001 | ✅ PASS |
| allFactoryMethodsFollowG2Contract | 0.000 | ✅ PASS |
| messagesCarryContext | 0.000 | ✅ PASS |

### PipelineContextFactoryBoundaryTest（7/7）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| streamStringTrueCoercedToBoolean | 0.001 | ✅ PASS |
| emptyModelStringLeavesRoutingToPipeline | 0.001 | ✅ PASS |
| whitespaceOnlyUserIdTreatedAsAnonymous | 0.001 | ✅ PASS |
| emptyBodyRejectedWithInvalidParam | 0.001 | ✅ PASS |
| streamExplicitFalseRespected | 0.002 | ✅ PASS |
| streamDefaultsToFalseWhenAbsent | 0.001 | ✅ PASS |
| jsonArrayBodyRejectedWithInvalidParam | 0.004 | ✅ PASS |

### PipelineContextFactoryTest（6/6）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| blankBodyUserLeavesAgentIdNull | 0.154 | ✅ PASS |
| missingTraceAttributeYieldsEmptyTraceId | 0.001 | ✅ PASS |
| invalidJsonRejectedWithInvalidParam | 0.007 | ✅ PASS |
| parsesBodyUserFieldAsAgentId | 0.001 | ✅ PASS |
| parsesIdentityHeadersAndModel | 0.001 | ✅ PASS |
| missingHeadersFallBackToAnonymous | 0.002 | ✅ PASS |

### L1RuleInterceptStageTest（6/6）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| authDisabledSkipsKeyValidationAndUsesBodyUserAsIdentity | 0.416 | ✅ PASS |
| authEnabledEmptyValidateSignalIsRejectedFailClosed | 0.004 | ✅ PASS |
| authDisabledWithoutBodyUserKeepsHeaderIdentity | 0.001 | ✅ PASS |
| authEnabledInvalidKeyIsRejected | 0.002 | ✅ PASS |
| authEnabledWithoutAuthorizationIsRejected | 0.001 | ✅ PASS |
| authEnabledValidKeyIdentityComesFromKeyAndAgentIdRetained | 0.002 | ✅ PASS |

### ApiKeyServiceTest（7/7）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| expiredKeyRejected | 0.131 | ✅ PASS |
| sha256KnownValue | 0.000 | ✅ PASS |
| sha256EmptyString | 0.000 | ✅ PASS |
| sha256Deterministic | 0.001 | ✅ PASS |
| sha256DifferentInputsProduceDifferentHashes | 0.000 | ✅ PASS |
| unknownKeyRejected | 0.003 | ✅ PASS |
| validKeyReturnsBoundIdentity | 0.003 | ✅ PASS |

### CacheKeyGeneratorBoundaryTest（8/8）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| differentMaxTokensProducesDifferentKey | 0.003 | ✅ PASS |
| explicitNullModelEqualsMissingModel | 0.000 | ✅ PASS |
| differentTemperatureProducesDifferentKey | 0.001 | ✅ PASS |
| unicodeContentIsStableAndDistinct | 0.001 | ✅ PASS |
| keyIs64CharLowercaseHex | 0.000 | ✅ PASS |
| deterministicAcrossInvocations | 0.001 | ✅ PASS |
| extraMessageFieldsDoNotAffectKeyButOrderDoes | 0.000 | ✅ PASS |
| missingMessagesEqualsEmptyArray | 0.001 | ✅ PASS |

### CacheKeyGeneratorTest（6/6）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| streamFlagIncludedInKey | 0.000 | ✅ PASS |
| messageOrderMatters | 0.001 | ✅ PASS |
| differentModelProducesDifferentKey | 0.000 | ✅ PASS |
| extraMessageFieldsIgnored | 0.000 | ✅ PASS |
| sameRequestProducesSameKey | 0.001 | ✅ PASS |
| missingParamsUseNullPlaceholder | 0.001 | ✅ PASS |

### ModelHealthTrackerTest（7/7）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| halfOpenSuccessClosesCircuit | 0.307 | ✅ PASS |
| halfOpenFailureReOpensCircuit | 0.310 | ✅ PASS |
| initialStateIsClosed | 0.002 | ✅ PASS |
| differentEndpointsIndependent | 0.001 | ✅ PASS |
| successResetsToClosed | 0.001 | ✅ PASS |
| openCircuitTransitionsToHalfOpen | 0.308 | ✅ PASS |
| consecutiveFailuresOpenCircuit | 0.003 | ✅ PASS |

### ModelRouterWeightedPickTest（5/5）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| zeroWeightStillEligibleWithFloorOne | 0.003 | ✅ PASS |
| singleCandidateAlwaysPicked | 0.001 | ✅ PASS |
| weightDistributionApproximatesRatio | 0.005 | ✅ PASS |
| emptyCandidatesReturnNull | 0.000 | ✅ PASS |
| modelConfigActiveAndFallback | 0.000 | ✅ PASS |

### RateLimitServiceBoundaryTest（5/5）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| highQpsDoesNotFalseRejectWithinBudget | 0.004 | ✅ PASS |
| anonymousUserIdIsAValidBucketKey | 0.001 | ✅ PASS |
| negativeQpsFlooredToOnePerSecond | 0.000 | ✅ PASS |
| manyUsersKeepIndependentBuckets | 0.001 | ✅ PASS |
| zeroQpsFlooredToOnePerSecond | 0.001 | ✅ PASS |

### RateLimitServiceTest（2/2）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| allowsUpToQpsThenRejects | 0.001 | ✅ PASS |
| bucketsArePerUser | 0.000 | ✅ PASS |

### RuleDifficultyClassifierBoundaryTest（15/15）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| multiLineTextAddsPointOneFive | 0.001 | ✅ PASS |
| simpleKeywordDeductionOnlyWithin40Chars | 0.000 | ✅ PASS |
| keywordScoreCapsAtPointSix | 0.000 | ✅ PASS |
| lengthBoundaryAt100 | 0.001 | ✅ PASS |
| lengthBoundaryAt300 | 0.000 | ✅ PASS |
| emptyAndWhitespaceOnlyScoreZero | 0.001 | ✅ PASS |
| exactlyThresholdComposition | 0.000 | ✅ PASS |
| caseInsensitiveKeywordMatching | 0.000 | ✅ PASS |
| codeSignalsAddPointOneFive | 0.000 | ✅ PASS |
| heavyCompositionClampedToOne | 0.001 | ✅ PASS |
| nullInputScoresZero | 0.000 | ✅ PASS |
| enumerationPatternsAddPointOneFive | 0.000 | ✅ PASS |
| simpleTextDeductsBelowZeroClampedToZero | 0.000 | ✅ PASS |
| lengthBoundaryAt40 | 0.001 | ✅ PASS |
| singleComplexKeywordAddsPointTwo | 0.000 | ✅ PASS |

### RuleDifficultyClassifierTest（5/5）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| scoreIsClampedToUnitInterval | 0.001 | ✅ PASS |
| blankInputScoresZero | 0.000 | ✅ PASS |
| shortSimpleQuestionScoresLow | 0.000 | ✅ PASS |
| complexTaskScoresHigh | 0.000 | ✅ PASS |
| longTextAndCodeRaiseScore | 0.001 | ✅ PASS |

### RuleIntentClassifierBoundaryTest（10/10）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| apiImplKeywordRequiresExactPhrase | 0.000 | ✅ PASS |
| nullInputDefaultsToChat | 0.000 | ✅ PASS |
| singleKeywordAnywhereInTextMatches | 0.000 | ✅ PASS |
| codeTakesPriorityWhenBothCodeAndRagKeywordsPresent | 0.000 | ✅ PASS |
| everyCodeKeywordRoutesToCode | 0.001 | ✅ PASS |
| substringOccurrencesCount | 0.000 | ✅ PASS |
| keywordMatchingIsCaseInsensitive | 0.001 | ✅ PASS |
| whitespaceOnlyDefaultsToChat | 0.000 | ✅ PASS |
| everyRagKeywordRoutesToRag | 0.001 | ✅ PASS |
| pureChineseGeneralTextFallsBackToChat | 0.000 | ✅ PASS |

### RuleIntentClassifierTest（5/5）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| ragKeywordsRouteToRag | 0.000 | ✅ PASS |
| generalTextFallsBackToChat | 0.001 | ✅ PASS |
| keywordMatchingIsCaseInsensitive | 0.000 | ✅ PASS |
| codeKeywordsRouteToCode | 0.000 | ✅ PASS |
| blankInputDefaultsToChat | 0.000 | ✅ PASS |

### SensitiveWordFilterBoundaryTest（13/13）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| maskReturnsInputUnchangedForNullOrEmpty | 0.001 | ✅ PASS |
| overlappingWordsLongerEmitMaskedFirst | 0.000 | ✅ PASS |
| hitAtTextEnd | 0.000 | ✅ PASS |
| multipleHitsPreserveSurroundingText | 0.001 | ✅ PASS |
| wholeTextIsTheWord | 0.000 | ✅ PASS |
| hitAtTextStart | 0.000 | ✅ PASS |
| matchingIsCaseSensitiveForAsciiWords | 0.000 | ✅ PASS |
| rebuildWithNullDisablesFilter | 0.000 | ✅ PASS |
| consecutiveHitsAllMasked | 0.000 | ✅ PASS |
| overlappingEmitsSkippedAfterFirstMask | 0.000 | ✅ PASS |
| containsReturnsFalseForNullOrEmptyText | 0.000 | ✅ PASS |
| wordsAreTrimmedBeforeMatching | 0.000 | ✅ PASS |
| nullAndBlankWordsAreIgnoredWithoutError | 0.001 | ✅ PASS |

### SensitiveWordFilterTest（3/3）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| emptyWordListDisablesFilter | 0.001 | ✅ PASS |
| containsMatchesMultiPattern | 0.003 | ✅ PASS |
| maskReplacesHits | 0.001 | ✅ PASS |

### TokenCounterBoundaryTest（12/12）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| emptyStringReturnsZero | 0.001 | ✅ PASS |
| countingIsDeterministic | 0.000 | ✅ PASS |
| emptyArrayReturnsZero | 0.000 | ✅ PASS |
| totalEqualsRolePlusContentPlusOverhead | 0.000 | ✅ PASS |
| nullReturnsZero | 0.000 | ✅ PASS |
| missingRoleAndContentStillCountOverhead | 0.001 | ✅ PASS |
| nullMessagesReturnsZero | 0.000 | ✅ PASS |
| nonArrayMessagesReturnsZero | 0.000 | ✅ PASS |
| eachMessageCarriesFixedOverheadOfFour | 0.000 | ✅ PASS |
| longerTextCountsMoreTokens | 0.001 | ✅ PASS |
| differentTextsProduceDifferentCounts | 0.000 | ✅ PASS |
| whitespaceOnlyCountsPositive | 0.000 | ✅ PASS |

### TokenCounterTest（5/5）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| nullAndEmptyCountZero | 0.271 | ✅ PASS |
| englishTextCountsPositive | 0.003 | ✅ PASS |
| countMessagesHandlesInvalidInput | 0.000 | ✅ PASS |
| chineseTextCountsPositive | 0.000 | ✅ PASS |
| countMessagesSumsRolesAndContents | 0.000 | ✅ PASS |

### LegacyCompatMappingTest（5/5）

| 用例 | 耗时(s) | 结论 |
| --- | --- | --- |
| requestMapping | 0.003 | ✅ PASS |
| invalidJsonRejected | 0.000 | ✅ PASS |
| requestMappingStreamDefaultsToFalse | 0.001 | ✅ PASS |
| missingModelIdFallsBackToDefaultRouting | 0.000 | ✅ PASS |
| responseMapping | 0.006 | ✅ PASS |

## 五、说明

- 难度路由契约：评分 ≥ 阈值（0.5）→ complex 档（qwen-72b → gpt-oss:20b），否则 simple 档（qwen-lite → qwen2.5:0.5b）；显式 model 字段优先级最高。
- 缓存契约：命中即短路（响应 meta 无 intent/difficulty）；精确缓存键含 model/messages/temperature/max_tokens，stream 排除在外（CH-07/CH-08 验证）。
- 语义缓存按 model_id 隔离（CH-05/CH-06 验证），余弦阈值 0.95。
- 限流用例按 burst 参数并发突发（超限 40 / 等水位 20 / 低水位 5），每例独立用户避免桶状态串扰（RATE-*）。
