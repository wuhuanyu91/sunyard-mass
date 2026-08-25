# MAS 原型测试报告

- **生成时间**：2026-08-21 22:57:07
- **应用地址**：http://localhost:9090/smart-router
- **运行隔离**：套件开始前经 /internal/cache/flush 清空两级缓存（含本地 Caffeine 层）；对话类用例拼接按 (model, prompt) 哈希派生的确定性隔离前缀（档案号 1e14 空间防碰撞）；断言 intent/difficulty 的用例使用原始 prompt；ref 用例继承前置用例的 max_tokens（精确缓存键组成部分）；断言缓存命中的用例请求前等待 1s 消除异步写竞态；串行请求限速 0.12s 避免误触限流
- **后端模型**：complex 档 → gpt-oss:20b，simple 档 → qwen2.5:0.5b，embedding → bge-m3（均为本机 Ollama 真实推理）

## 一、总体统计

| 测试类型 | 用例数 | 通过 | 失败 | 通过率 |
| --- | --- | --- | --- | --- |
| 黑盒功能用例（cases.json 数据驱动） | 900 | 890 | 10 | 98.9% |

### 分类统计

| 分类 | 说明 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- | --- |
| system | 系统端点（健康/指标/模型列表/trace） | 100 | 100 | 0 |
| embedding | 嵌入服务 | 100 | 100 | 0 |
| difficulty | 难度路由（无显式 model 自动分流） | 100 | 100 | 0 |
| explicit | 显式模型路由 | 100 | 100 | 0 |
| error | 错误与拦截（404/400/403） | 100 | 100 | 0 |
| legacy | 旧协议兼容层 | 100 | 100 | 0 |
| cache | 两级缓存行为 | 100 | 93 | 7 |
| stream | 流式 SSE | 100 | 97 | 3 |
| rate | 限流 | 100 | 100 | 0 |

## 二、黑盒用例路由明细

路由明细字段：intent（L3 路由档位）、difficulty（难度评分）、后端模型（引擎实际响应的 model）、缓存（命中级别）、耗时。
缓存命中的用例按设计短路返回，不经过 L3，故无 intent/difficulty 字段（— 表示）。

| 用例ID | 分类 | 输入摘要 | HTTP | intent | difficulty | 后端模型 | 缓存 | 耗时(ms) | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SYS-001 | system | /actuator/health | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-002 | system | /actuator/health | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-003 | system | /actuator/health | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-004 | system | /actuator/health | 200 | — | — | — | — | 13 | ✅ PASS |
| SYS-005 | system | /actuator/health | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-006 | system | /actuator/health | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-007 | system | /actuator/health | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-008 | system | /actuator/health | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-009 | system | /actuator/health | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-010 | system | /actuator/health | 200 | — | — | — | — | 14 | ✅ PASS |
| SYS-011 | system | /actuator/prometheus | 200 | — | — | — | — | 26 | ✅ PASS |
| SYS-012 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-013 | system | /actuator/prometheus | 200 | — | — | — | — | 15 | ✅ PASS |
| SYS-014 | system | /actuator/prometheus | 200 | — | — | — | — | 13 | ✅ PASS |
| SYS-015 | system | /actuator/prometheus | 200 | — | — | — | — | 14 | ✅ PASS |
| SYS-016 | system | /actuator/prometheus | 200 | — | — | — | — | 8 | ✅ PASS |
| SYS-017 | system | /actuator/prometheus | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-018 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-019 | system | /actuator/prometheus | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-020 | system | /actuator/prometheus | 200 | — | — | — | — | 8 | ✅ PASS |
| SYS-021 | system | /actuator/prometheus | 200 | — | — | — | — | 11 | ✅ PASS |
| SYS-022 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-023 | system | /actuator/prometheus | 200 | — | — | — | — | 16 | ✅ PASS |
| SYS-024 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-025 | system | /actuator/prometheus | 200 | — | — | — | — | 12 | ✅ PASS |
| SYS-026 | system | /actuator/prometheus | 200 | — | — | — | — | 8 | ✅ PASS |
| SYS-027 | system | /actuator/prometheus | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-028 | system | /actuator/prometheus | 200 | — | — | — | — | 11 | ✅ PASS |
| SYS-029 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-030 | system | /actuator/prometheus | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-031 | system | /actuator/prometheus | 200 | — | — | — | — | 8 | ✅ PASS |
| SYS-032 | system | /actuator/prometheus | 200 | — | — | — | — | 12 | ✅ PASS |
| SYS-033 | system | /actuator/prometheus | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-034 | system | /actuator/prometheus | 200 | — | — | — | — | 12 | ✅ PASS |
| SYS-035 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-036 | system | /actuator/prometheus | 200 | — | — | — | — | 12 | ✅ PASS |
| SYS-037 | system | /actuator/prometheus | 200 | — | — | — | — | 12 | ✅ PASS |
| SYS-038 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-039 | system | /actuator/prometheus | 200 | — | — | — | — | 10 | ✅ PASS |
| SYS-040 | system | /actuator/prometheus | 200 | — | — | — | — | 12 | ✅ PASS |
| SYS-041 | system | /v1/models | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-042 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-043 | system | /v1/models | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-044 | system | /v1/models | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-045 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-046 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-047 | system | /v1/models | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-048 | system | /v1/models | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-049 | system | /v1/models | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-050 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-051 | system | /v1/models | 200 | — | — | — | — | 9 | ✅ PASS |
| SYS-052 | system | /v1/models | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-053 | system | /v1/models | 200 | — | — | — | — | 5 | ✅ PASS |
| SYS-054 | system | /v1/models | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-055 | system | /v1/models | 200 | — | — | — | — | 6 | ✅ PASS |
| SYS-056 | system | /v1/models | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-057 | system | /v1/models | 200 | — | — | — | — | 7 | ✅ PASS |
| SYS-058 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-059 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-060 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-061 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-062 | system | /v1/models | 200 | — | — | — | — | 3 | ✅ PASS |
| SYS-063 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-064 | system | /v1/models | 200 | — | — | — | — | 4 | ✅ PASS |
| SYS-065 | system | /v1/models | 200 | — | — | — | — | 15 | ✅ PASS |
| SYS-066 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-067 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-068 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-069 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-070 | system | /v1/models | 200 | — | — | — | — | 2 | ✅ PASS |
| SYS-071 | system | 链路追踪用例：请用一个词描述序号0的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 2424 | ✅ PASS |
| SYS-072 | system | 链路追踪用例：请用一个词描述序号1的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 340 | ✅ PASS |
| SYS-073 | system | 链路追踪用例：请用一个词描述序号2的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 330 | ✅ PASS |
| SYS-074 | system | 链路追踪用例：请用一个词描述序号3的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 335 | ✅ PASS |
| SYS-075 | system | 链路追踪用例：请用一个词描述序号4的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 281 | ✅ PASS |
| SYS-076 | system | 链路追踪用例：请用一个词描述序号5的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 319 | ✅ PASS |
| SYS-077 | system | 链路追踪用例：请用一个词描述序号6的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 320 | ✅ PASS |
| SYS-078 | system | 链路追踪用例：请用一个词描述序号7的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 317 | ✅ PASS |
| SYS-079 | system | 链路追踪用例：请用一个词描述序号8的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 313 | ✅ PASS |
| SYS-080 | system | 链路追踪用例：请用一个词描述序号9的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 307 | ✅ PASS |
| SYS-081 | system | 链路追踪用例：请用一个词描述序号10的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 314 | ✅ PASS |
| SYS-082 | system | 链路追踪用例：请用一个词描述序号11的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 327 | ✅ PASS |
| SYS-083 | system | 链路追踪用例：请用一个词描述序号12的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 325 | ✅ PASS |
| SYS-084 | system | 链路追踪用例：请用一个词描述序号13的测试 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 150 | ✅ PASS |
| SYS-085 | system | 链路追踪用例：请用一个词描述序号14的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 294 | ✅ PASS |
| SYS-086 | system | 链路追踪用例：请用一个词描述序号15的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 327 | ✅ PASS |
| SYS-087 | system | 链路追踪用例：请用一个词描述序号16的测试 | 200 | — | — | qwen2.5:0.5b | hit:semantic | 157 | ✅ PASS |
| SYS-088 | system | 链路追踪用例：请用一个词描述序号17的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 281 | ✅ PASS |
| SYS-089 | system | 链路追踪用例：请用一个词描述序号18的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 305 | ✅ PASS |
| SYS-090 | system | 链路追踪用例：请用一个词描述序号19的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 333 | ✅ PASS |
| SYS-091 | system | 链路追踪用例：请用一个词描述序号20的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 321 | ✅ PASS |
| SYS-092 | system | 链路追踪用例：请用一个词描述序号21的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 311 | ✅ PASS |
| SYS-093 | system | 链路追踪用例：请用一个词描述序号22的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 317 | ✅ PASS |
| SYS-094 | system | 链路追踪用例：请用一个词描述序号23的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 310 | ✅ PASS |
| SYS-095 | system | 链路追踪用例：请用一个词描述序号24的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 314 | ✅ PASS |
| SYS-096 | system | 链路追踪用例：请用一个词描述序号25的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 316 | ✅ PASS |
| SYS-097 | system | 链路追踪用例：请用一个词描述序号26的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 341 | ✅ PASS |
| SYS-098 | system | 链路追踪用例：请用一个词描述序号27的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 320 | ✅ PASS |
| SYS-099 | system | 链路追踪用例：请用一个词描述序号28的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 328 | ✅ PASS |
| SYS-100 | system | 链路追踪用例：请用一个词描述序号29的测试 | 200 | simple | — | qwen2.5:0.5b | miss | 314 | ✅ PASS |
| EMB-001 | embedding | 嵌入用例：今天天气怎么样？ | 200 | — | — | bge-m3 | — | 140 | ✅ PASS |
| EMB-002 | embedding | 嵌入用例：北京是哪个国家的首都？ | 200 | — | — | bge-m3 | — | 143 | ✅ PASS |
| EMB-003 | embedding | 嵌入用例：一周有几天？ | 200 | — | — | bge-m3 | — | 121 | ✅ PASS |
| EMB-004 | embedding | 嵌入用例：水的化学式是什么？ | 200 | — | — | bge-m3 | — | 120 | ✅ PASS |
| EMB-005 | embedding | 嵌入用例：是谁发明了电灯泡？ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-006 | embedding | 嵌入用例：现在几点了？ | 200 | — | — | bge-m3 | — | 121 | ✅ PASS |
| EMB-007 | embedding | 嵌入用例：你好，介绍一下你自己 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-008 | embedding | 嵌入用例：明天是星期几？ | 200 | — | — | bge-m3 | — | 124 | ✅ PASS |
| EMB-009 | embedding | 嵌入用例：一年有多少个月？ | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| EMB-010 | embedding | 嵌入用例：中国的首都在哪里？ | 200 | — | — | bge-m3 | — | 137 | ✅ PASS |
| EMB-011 | embedding | 嵌入用例：这本书多少钱？ | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-012 | embedding | 嵌入用例：帮我翻译这句话：good morning | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-013 | embedding | 嵌入用例：地球是圆的吗？ | 200 | — | — | bge-m3 | — | 135 | ✅ PASS |
| EMB-014 | embedding | 嵌入用例：猫的英文怎么说？ | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-015 | embedding | 嵌入用例：1加1等于几？ | 200 | — | — | bge-m3 | — | 135 | ✅ PASS |
| EMB-016 | embedding | 嵌入用例：你喜欢什么颜色？ | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-017 | embedding | 嵌入用例：怎么打电话给客服？ | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-018 | embedding | 嵌入用例：米饭的热量是多少？ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-019 | embedding | 嵌入用例：上海在北京的哪个方向？ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-020 | embedding | 嵌入用例：今天限行尾号是多少？ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-021 | embedding | 嵌入用例：太阳从哪边升起？ | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-022 | embedding | 嵌入用例：一年有几个季节？ | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-023 | embedding | 嵌入用例：彩虹有几种颜色？ | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-024 | embedding | 嵌入用例：最大的海洋是哪个？ | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-025 | embedding | 嵌入用例：熊猫主要吃什么？ | 200 | — | — | bge-m3 | — | 137 | ✅ PASS |
| EMB-026 | embedding | 嵌入用例：长城在哪个国家？ | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-027 | embedding | 嵌入用例：一天有多少小时？ | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-028 | embedding | 嵌入用例：冰是什么状态的？ | 200 | — | — | bge-m3 | — | 122 | ✅ PASS |
| EMB-029 | embedding | 嵌入用例：自行车有几个轮子？ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-030 | embedding | 嵌入用例：春节是几月份？ | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-031 | embedding | Embedding case 0: the quick  | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-032 | embedding | Embedding case 1: the quick  | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-033 | embedding | Embedding case 2: the quick  | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-034 | embedding | Embedding case 3: the quick  | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-035 | embedding | Embedding case 4: the quick  | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-036 | embedding | Embedding case 5: the quick  | 200 | — | — | bge-m3 | — | 141 | ✅ PASS |
| EMB-037 | embedding | Embedding case 6: the quick  | 200 | — | — | bge-m3 | — | 135 | ✅ PASS |
| EMB-038 | embedding | Embedding case 7: the quick  | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-039 | embedding | Embedding case 8: the quick  | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-040 | embedding | Embedding case 9: the quick  | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-041 | embedding | Embedding case 10: the quick | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-042 | embedding | Embedding case 11: the quick | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-043 | embedding | Embedding case 12: the quick | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-044 | embedding | Embedding case 13: the quick | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-045 | embedding | Embedding case 14: the quick | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-046 | embedding | Embedding case 15: the quick | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-047 | embedding | Embedding case 16: the quick | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-048 | embedding | Embedding case 17: the quick | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-049 | embedding | Embedding case 18: the quick | 200 | — | — | bge-m3 | — | 126 | ✅ PASS |
| EMB-050 | embedding | Embedding case 19: the quick | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-051 | embedding | 中英混合嵌入用例 mixed text 0 号 | 200 | — | — | bge-m3 | — | 126 | ✅ PASS |
| EMB-052 | embedding | 中英混合嵌入用例 mixed text 1 号 | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-053 | embedding | 中英混合嵌入用例 mixed text 2 号 | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-054 | embedding | 中英混合嵌入用例 mixed text 3 号 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-055 | embedding | 中英混合嵌入用例 mixed text 4 号 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-056 | embedding | 中英混合嵌入用例 mixed text 5 号 | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-057 | embedding | 中英混合嵌入用例 mixed text 6 号 | 200 | — | — | bge-m3 | — | 137 | ✅ PASS |
| EMB-058 | embedding | 中英混合嵌入用例 mixed text 7 号 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-059 | embedding | 中英混合嵌入用例 mixed text 8 号 | 200 | — | — | bge-m3 | — | 197 | ✅ PASS |
| EMB-060 | embedding | 中英混合嵌入用例 mixed text 9 号 | 200 | — | — | bge-m3 | — | 124 | ✅ PASS |
| EMB-061 | embedding | 中英混合嵌入用例 mixed text 10 号 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-062 | embedding | 中英混合嵌入用例 mixed text 11 号 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-063 | embedding | 中英混合嵌入用例 mixed text 12 号 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-064 | embedding | 中英混合嵌入用例 mixed text 13 号 | 200 | — | — | bge-m3 | — | 141 | ✅ PASS |
| EMB-065 | embedding | 中英混合嵌入用例 mixed text 14 号 | 200 | — | — | bge-m3 | — | 135 | ✅ PASS |
| EMB-066 | embedding | 数字与符号序列 0 0 0 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-067 | embedding | 数字与符号序列 1 7 13 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 136 | ✅ PASS |
| EMB-068 | embedding | 数字与符号序列 2 14 26 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-069 | embedding | 数字与符号序列 3 21 39 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-070 | embedding | 数字与符号序列 4 28 52 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-071 | embedding | 数字与符号序列 5 35 65 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 132 | ✅ PASS |
| EMB-072 | embedding | 数字与符号序列 6 42 78 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 120 | ✅ PASS |
| EMB-073 | embedding | 数字与符号序列 7 49 91 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-074 | embedding | 数字与符号序列 8 56 104 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 131 | ✅ PASS |
| EMB-075 | embedding | 数字与符号序列 9 63 117 + - × ÷ = ≠ | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-076 | embedding | 代码嵌入用例：def f0(x): return x * | 200 | — | — | bge-m3 | — | 122 | ✅ PASS |
| EMB-077 | embedding | 代码嵌入用例：def f1(x): return x * | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-078 | embedding | 代码嵌入用例：def f2(x): return x * | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-079 | embedding | 代码嵌入用例：def f3(x): return x * | 200 | — | — | bge-m3 | — | 126 | ✅ PASS |
| EMB-080 | embedding | 代码嵌入用例：def f4(x): return x * | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-081 | embedding | 代码嵌入用例：def f5(x): return x * | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-082 | embedding | 代码嵌入用例：def f6(x): return x * | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-083 | embedding | 代码嵌入用例：def f7(x): return x * | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-084 | embedding | 代码嵌入用例：def f8(x): return x * | 200 | — | — | bge-m3 | — | 130 | ✅ PASS |
| EMB-085 | embedding | 代码嵌入用例：def f9(x): return x * | 200 | — | — | bge-m3 | — | 126 | ✅ PASS |
| EMB-086 | embedding | 长文本嵌入用例第0段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 141 | ✅ PASS |
| EMB-087 | embedding | 长文本嵌入用例第1段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-088 | embedding | 长文本嵌入用例第2段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 140 | ✅ PASS |
| EMB-089 | embedding | 长文本嵌入用例第3段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-090 | embedding | 长文本嵌入用例第4段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 125 | ✅ PASS |
| EMB-091 | embedding | 长文本嵌入用例第5段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 129 | ✅ PASS |
| EMB-092 | embedding | 长文本嵌入用例第6段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| EMB-093 | embedding | 长文本嵌入用例第7段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 128 | ✅ PASS |
| EMB-094 | embedding | 长文本嵌入用例第8段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 133 | ✅ PASS |
| EMB-095 | embedding | 长文本嵌入用例第9段。数据中台建设需要统一指标口径、血缘 | 200 | — | — | bge-m3 | — | 135 | ✅ PASS |
| EMB-096 | embedding | 长文本嵌入用例第10段。数据中台建设需要统一指标口径、血 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-097 | embedding | 长文本嵌入用例第11段。数据中台建设需要统一指标口径、血 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-098 | embedding | 长文本嵌入用例第12段。数据中台建设需要统一指标口径、血 | 200 | — | — | bge-m3 | — | 134 | ✅ PASS |
| EMB-099 | embedding | 长文本嵌入用例第13段。数据中台建设需要统一指标口径、血 | 200 | — | — | bge-m3 | — | 140 | ✅ PASS |
| EMB-100 | embedding | 长文本嵌入用例第14段。数据中台建设需要统一指标口径、血 | 200 | — | — | bge-m3 | — | 127 | ✅ PASS |
| DS-001 | difficulty | 今天天气怎么样？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 338 | ✅ PASS |
| DS-002 | difficulty | 北京是哪个国家的首都？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 373 | ✅ PASS |
| DS-003 | difficulty | 一周有几天？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 360 | ✅ PASS |
| DS-004 | difficulty | 水的化学式是什么？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 354 | ✅ PASS |
| DS-005 | difficulty | 是谁发明了电灯泡？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| DS-006 | difficulty | 现在几点了？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 389 | ✅ PASS |
| DS-007 | difficulty | 你好，介绍一下你自己 | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 358 | ✅ PASS |
| DS-008 | difficulty | 明天是星期几？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 363 | ✅ PASS |
| DS-009 | difficulty | 一年有多少个月？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 307 | ✅ PASS |
| DS-010 | difficulty | 中国的首都在哪里？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 356 | ✅ PASS |
| DS-011 | difficulty | 这本书多少钱？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 363 | ✅ PASS |
| DS-012 | difficulty | 帮我翻译这句话：good morning | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 369 | ✅ PASS |
| DS-013 | difficulty | 地球是圆的吗？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 419 | ✅ PASS |
| DS-014 | difficulty | 猫的英文怎么说？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 324 | ✅ PASS |
| DS-015 | difficulty | 1加1等于几？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 390 | ✅ PASS |
| DS-016 | difficulty | 你喜欢什么颜色？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| DS-017 | difficulty | 怎么打电话给客服？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| DS-018 | difficulty | 米饭的热量是多少？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 367 | ✅ PASS |
| DS-019 | difficulty | 上海在北京的哪个方向？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| DS-020 | difficulty | 今天限行尾号是多少？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 371 | ✅ PASS |
| DS-021 | difficulty | 太阳从哪边升起？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 327 | ✅ PASS |
| DS-022 | difficulty | 一年有几个季节？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| DS-023 | difficulty | 彩虹有几种颜色？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| DS-024 | difficulty | 最大的海洋是哪个？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| DS-025 | difficulty | 熊猫主要吃什么？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 350 | ✅ PASS |
| DS-026 | difficulty | 长城在哪个国家？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 345 | ✅ PASS |
| DS-027 | difficulty | 一天有多少小时？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 350 | ✅ PASS |
| DS-028 | difficulty | 冰是什么状态的？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 351 | ✅ PASS |
| DS-029 | difficulty | 自行车有几个轮子？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| DS-030 | difficulty | 春节是几月份？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 358 | ✅ PASS |
| DS-031 | difficulty | 法国的首都是哪里？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 274 | ✅ PASS |
| DS-032 | difficulty | 日本的货币叫什么？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| DS-033 | difficulty | 珠穆朗玛峰高吗？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 374 | ✅ PASS |
| DS-034 | difficulty | 企鹅会飞吗？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| DS-035 | difficulty | 牛奶是什么颜色的？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| DS-036 | difficulty | 星期天的后一天是星期几？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 330 | ✅ PASS |
| DS-037 | difficulty | 汉字是谁发明的？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| DS-038 | difficulty | 端午节吃什么？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 358 | ✅ PASS |
| DS-039 | difficulty | 飞机在哪里起降？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 368 | ✅ PASS |
| DS-040 | difficulty | 医生在哪里工作？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 380 | ✅ PASS |
| DS-041 | difficulty | 夏天热还是冬天热？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 363 | ✅ PASS |
| DS-042 | difficulty | 鱼生活在哪里？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 364 | ✅ PASS |
| DS-043 | difficulty | 月亮会发光吗？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 366 | ✅ PASS |
| DS-044 | difficulty | 一小时有多少分钟？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 339 | ✅ PASS |
| DS-045 | difficulty | 三角形的边数是多少？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| DS-046 | difficulty | 谁写了《静夜思》？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| DS-047 | difficulty | 巧克力的原料是什么？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| DS-048 | difficulty | 足球几个人踢？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 317 | ✅ PASS |
| DS-049 | difficulty | 地球有几颗卫星？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 348 | ✅ PASS |
| DS-050 | difficulty | 早餐一般吃什么？ | 200 | simple | 0.0 | qwen2.5:0.5b | miss | 373 | ✅ PASS |
| DC-001 | difficulty | 请证明分布式缓存架构，逐步给出多步骤方案。第0组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 10040 | ✅ PASS |
| DC-002 | difficulty | 请推导消息队列可靠性，对比分析权衡并给出设计方案。第1组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 3142 | ✅ PASS |
| DC-003 | difficulty | 请设计编译器词法分析，推导原理并给出优化方案。第2组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1459 | ✅ PASS |
| DC-004 | difficulty | 请重构分布式事务模式，给出架构评估结论与规划。第3组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1448 | ✅ PASS |
| DC-005 | difficulty | 请深入分析数据库索引算法，深入分析原理并规划落地。第4组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1441 | ✅ PASS |
| DC-006 | difficulty | 请对比分析推荐系统召回，逐步给出多步骤方案。第5组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1453 | ✅ PASS |
| DC-007 | difficulty | 请全面评估并发容器原理，对比分析权衡并给出设计方案。第6 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1502 | ✅ PASS |
| DC-008 | difficulty | 实现一个微服务拆分方案，推导原理并给出优化方案。第7组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1468 | ✅ PASS |
| DC-009 | difficulty | 设计一个限流降级策略，给出架构评估结论与规划。第8组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1472 | ✅ PASS |
| DC-010 | difficulty | 写一篇垃圾回收调优，深入分析原理并规划落地。第9组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1453 | ✅ PASS |
| DC-011 | difficulty | 请证明微服务拆分方案，逐步给出多步骤方案。第10组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1486 | ✅ PASS |
| DC-012 | difficulty | 请推导限流降级策略，对比分析权衡并给出设计方案。第11组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1471 | ✅ PASS |
| DC-013 | difficulty | 请设计垃圾回收调优，推导原理并给出优化方案。第12组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1491 | ✅ PASS |
| DC-014 | difficulty | 请重构分布式缓存架构，给出架构评估结论与规划。第13组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1485 | ✅ PASS |
| DC-015 | difficulty | 请深入分析消息队列可靠性，深入分析原理并规划落地。第14 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1476 | ✅ PASS |
| DC-016 | difficulty | 请对比分析编译器词法分析，逐步给出多步骤方案。第15组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1496 | ✅ PASS |
| DC-017 | difficulty | 请全面评估分布式事务模式，对比分析权衡并给出设计方案。第 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1498 | ✅ PASS |
| DC-018 | difficulty | 实现一个数据库索引算法，推导原理并给出优化方案。第17组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1519 | ✅ PASS |
| DC-019 | difficulty | 设计一个推荐系统召回，给出架构评估结论与规划。第18组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1469 | ✅ PASS |
| DC-020 | difficulty | 写一篇并发容器原理，深入分析原理并规划落地。第19组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1550 | ✅ PASS |
| DC-021 | difficulty | 请证明数据库索引算法，逐步给出多步骤方案。第20组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1496 | ✅ PASS |
| DC-022 | difficulty | 请推导推荐系统召回，对比分析权衡并给出设计方案。第21组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1481 | ✅ PASS |
| DC-023 | difficulty | 请设计并发容器原理，推导原理并给出优化方案。第22组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1498 | ✅ PASS |
| DC-024 | difficulty | 请重构微服务拆分方案，给出架构评估结论与规划。第23组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1508 | ✅ PASS |
| DC-025 | difficulty | 请深入分析限流降级策略，深入分析原理并规划落地。第24组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1552 | ✅ PASS |
| DC-026 | difficulty | 请对比分析垃圾回收调优，逐步给出多步骤方案。第25组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1524 | ✅ PASS |
| DC-027 | difficulty | 请全面评估分布式缓存架构，对比分析权衡并给出设计方案。第 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1500 | ✅ PASS |
| DC-028 | difficulty | 实现一个消息队列可靠性，推导原理并给出优化方案。第27组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1503 | ✅ PASS |
| DC-029 | difficulty | 设计一个编译器词法分析，给出架构评估结论与规划。第28组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1483 | ✅ PASS |
| DC-030 | difficulty | 写一篇分布式事务模式，深入分析原理并规划落地。第29组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1548 | ✅ PASS |
| DC-031 | difficulty | 请证明消息队列可靠性，逐步给出多步骤方案。第30组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1507 | ✅ PASS |
| DC-032 | difficulty | 请推导编译器词法分析，对比分析权衡并给出设计方案。第31 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1495 | ✅ PASS |
| DC-033 | difficulty | 请设计分布式事务模式，推导原理并给出优化方案。第32组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1510 | ✅ PASS |
| DC-034 | difficulty | 请重构数据库索引算法，给出架构评估结论与规划。第33组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1492 | ✅ PASS |
| DC-035 | difficulty | 请深入分析推荐系统召回，深入分析原理并规划落地。第34组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1553 | ✅ PASS |
| DC-036 | difficulty | 请对比分析并发容器原理，逐步给出多步骤方案。第35组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1488 | ✅ PASS |
| DC-037 | difficulty | 请全面评估微服务拆分方案，对比分析权衡并给出设计方案。第 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1499 | ✅ PASS |
| DC-038 | difficulty | 实现一个限流降级策略，推导原理并给出优化方案。第37组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1498 | ✅ PASS |
| DC-039 | difficulty | 设计一个垃圾回收调优，给出架构评估结论与规划。第38组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1532 | ✅ PASS |
| DC-040 | difficulty | 写一篇分布式缓存架构，深入分析原理并规划落地。第39组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1497 | ✅ PASS |
| DC-041 | difficulty | 请证明限流降级策略，逐步给出多步骤方案。第40组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1534 | ✅ PASS |
| DC-042 | difficulty | 请推导垃圾回收调优，对比分析权衡并给出设计方案。第41组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1533 | ✅ PASS |
| DC-043 | difficulty | 请设计分布式缓存架构，推导原理并给出优化方案。第42组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1515 | ✅ PASS |
| DC-044 | difficulty | 请重构消息队列可靠性，给出架构评估结论与规划。第43组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1516 | ✅ PASS |
| DC-045 | difficulty | 请深入分析编译器词法分析，深入分析原理并规划落地。第44 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1518 | ✅ PASS |
| DC-046 | difficulty | 请对比分析分布式事务模式，逐步给出多步骤方案。第45组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1538 | ✅ PASS |
| DC-047 | difficulty | 请全面评估数据库索引算法，对比分析权衡并给出设计方案。第 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1621 | ✅ PASS |
| DC-048 | difficulty | 实现一个推荐系统召回，推导原理并给出优化方案。第47组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1532 | ✅ PASS |
| DC-049 | difficulty | 设计一个并发容器原理，给出架构评估结论与规划。第48组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1533 | ✅ PASS |
| DC-050 | difficulty | 写一篇微服务拆分方案，深入分析原理并规划落地。第49组 | 200 | complex | 0.6 | gpt-oss:20b | miss | 1522 | ✅ PASS |
| EXP-001 | explicit | 显式小模型用例0：今天天气怎么样？ | 200 | simple | — | qwen2.5:0.5b | miss | 1314 | ✅ PASS |
| EXP-002 | explicit | 显式小模型用例1：北京是哪个国家的首都？ | 200 | simple | — | qwen2.5:0.5b | miss | 427 | ✅ PASS |
| EXP-003 | explicit | 显式小模型用例2：一周有几天？ | 200 | simple | — | qwen2.5:0.5b | miss | 383 | ✅ PASS |
| EXP-004 | explicit | 显式小模型用例3：水的化学式是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 386 | ✅ PASS |
| EXP-005 | explicit | 显式小模型用例4：是谁发明了电灯泡？ | 200 | simple | — | qwen2.5:0.5b | miss | 380 | ✅ PASS |
| EXP-006 | explicit | 显式小模型用例5：现在几点了？ | 200 | simple | — | qwen2.5:0.5b | miss | 368 | ✅ PASS |
| EXP-007 | explicit | 显式小模型用例6：你好，介绍一下你自己 | 200 | simple | — | qwen2.5:0.5b | miss | 384 | ✅ PASS |
| EXP-008 | explicit | 显式小模型用例7：明天是星期几？ | 200 | simple | — | qwen2.5:0.5b | miss | 379 | ✅ PASS |
| EXP-009 | explicit | 显式小模型用例8：一年有多少个月？ | 200 | simple | — | qwen2.5:0.5b | miss | 389 | ✅ PASS |
| EXP-010 | explicit | 显式小模型用例9：中国的首都在哪里？ | 200 | simple | — | qwen2.5:0.5b | miss | 370 | ✅ PASS |
| EXP-011 | explicit | 显式小模型用例10：这本书多少钱？ | 200 | simple | — | qwen2.5:0.5b | miss | 383 | ✅ PASS |
| EXP-012 | explicit | 显式小模型用例11：帮我翻译这句话：good morni | 200 | simple | — | qwen2.5:0.5b | miss | 396 | ✅ PASS |
| EXP-013 | explicit | 显式小模型用例12：地球是圆的吗？ | 200 | simple | — | qwen2.5:0.5b | miss | 372 | ✅ PASS |
| EXP-014 | explicit | 显式小模型用例13：猫的英文怎么说？ | 200 | simple | — | qwen2.5:0.5b | miss | 389 | ✅ PASS |
| EXP-015 | explicit | 显式小模型用例14：1加1等于几？ | 200 | simple | — | qwen2.5:0.5b | miss | 393 | ✅ PASS |
| EXP-016 | explicit | 显式小模型用例15：你喜欢什么颜色？ | 200 | simple | — | qwen2.5:0.5b | miss | 396 | ✅ PASS |
| EXP-017 | explicit | 显式小模型用例16：怎么打电话给客服？ | 200 | simple | — | qwen2.5:0.5b | miss | 387 | ✅ PASS |
| EXP-018 | explicit | 显式小模型用例17：米饭的热量是多少？ | 200 | simple | — | qwen2.5:0.5b | miss | 383 | ✅ PASS |
| EXP-019 | explicit | 显式小模型用例18：上海在北京的哪个方向？ | 200 | simple | — | qwen2.5:0.5b | miss | 386 | ✅ PASS |
| EXP-020 | explicit | 显式小模型用例19：今天限行尾号是多少？ | 200 | simple | — | qwen2.5:0.5b | miss | 392 | ✅ PASS |
| EXP-021 | explicit | 显式小模型用例20：太阳从哪边升起？ | 200 | simple | — | qwen2.5:0.5b | miss | 385 | ✅ PASS |
| EXP-022 | explicit | 显式小模型用例21：一年有几个季节？ | 200 | simple | — | qwen2.5:0.5b | miss | 375 | ✅ PASS |
| EXP-023 | explicit | 显式小模型用例22：彩虹有几种颜色？ | 200 | simple | — | qwen2.5:0.5b | miss | 372 | ✅ PASS |
| EXP-024 | explicit | 显式小模型用例23：最大的海洋是哪个？ | 200 | simple | — | qwen2.5:0.5b | miss | 380 | ✅ PASS |
| EXP-025 | explicit | 显式小模型用例24：熊猫主要吃什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 368 | ✅ PASS |
| EXP-026 | explicit | 显式小模型用例25：长城在哪个国家？ | 200 | simple | — | qwen2.5:0.5b | miss | 383 | ✅ PASS |
| EXP-027 | explicit | 显式小模型用例26：一天有多少小时？ | 200 | simple | — | qwen2.5:0.5b | miss | 377 | ✅ PASS |
| EXP-028 | explicit | 显式小模型用例27：冰是什么状态的？ | 200 | simple | — | qwen2.5:0.5b | miss | 388 | ✅ PASS |
| EXP-029 | explicit | 显式小模型用例28：自行车有几个轮子？ | 200 | simple | — | qwen2.5:0.5b | miss | 381 | ✅ PASS |
| EXP-030 | explicit | 显式小模型用例29：春节是几月份？ | 200 | simple | — | qwen2.5:0.5b | miss | 379 | ✅ PASS |
| EXP-031 | explicit | 显式小模型用例30：法国的首都是哪里？ | 200 | simple | — | qwen2.5:0.5b | miss | 372 | ✅ PASS |
| EXP-032 | explicit | 显式小模型用例31：日本的货币叫什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 416 | ✅ PASS |
| EXP-033 | explicit | 显式小模型用例32：珠穆朗玛峰高吗？ | 200 | simple | — | qwen2.5:0.5b | miss | 407 | ✅ PASS |
| EXP-034 | explicit | 显式小模型用例33：企鹅会飞吗？ | 200 | simple | — | qwen2.5:0.5b | miss | 369 | ✅ PASS |
| EXP-035 | explicit | 显式小模型用例34：牛奶是什么颜色的？ | 200 | simple | — | qwen2.5:0.5b | miss | 371 | ✅ PASS |
| EXP-036 | explicit | 显式小模型用例35：星期天的后一天是星期几？ | 200 | simple | — | qwen2.5:0.5b | miss | 386 | ✅ PASS |
| EXP-037 | explicit | 显式小模型用例36：汉字是谁发明的？ | 200 | simple | — | qwen2.5:0.5b | miss | 385 | ✅ PASS |
| EXP-038 | explicit | 显式小模型用例37：端午节吃什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 383 | ✅ PASS |
| EXP-039 | explicit | 显式小模型用例38：飞机在哪里起降？ | 200 | simple | — | qwen2.5:0.5b | miss | 387 | ✅ PASS |
| EXP-040 | explicit | 显式小模型用例39：医生在哪里工作？ | 200 | simple | — | qwen2.5:0.5b | miss | 386 | ✅ PASS |
| EXP-041 | explicit | 显式小模型用例40：夏天热还是冬天热？ | 200 | simple | — | qwen2.5:0.5b | miss | 383 | ✅ PASS |
| EXP-042 | explicit | 显式小模型用例41：鱼生活在哪里？ | 200 | simple | — | qwen2.5:0.5b | miss | 378 | ✅ PASS |
| EXP-043 | explicit | 显式小模型用例42：月亮会发光吗？ | 200 | simple | — | qwen2.5:0.5b | miss | 387 | ✅ PASS |
| EXP-044 | explicit | 显式小模型用例43：一小时有多少分钟？ | 200 | simple | — | qwen2.5:0.5b | miss | 381 | ✅ PASS |
| EXP-045 | explicit | 显式小模型用例44：三角形的边数是多少？ | 200 | simple | — | qwen2.5:0.5b | miss | 389 | ✅ PASS |
| EXP-046 | explicit | 显式小模型用例45：谁写了《静夜思》？ | 200 | simple | — | qwen2.5:0.5b | miss | 376 | ✅ PASS |
| EXP-047 | explicit | 显式小模型用例46：巧克力的原料是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 390 | ✅ PASS |
| EXP-048 | explicit | 显式小模型用例47：足球几个人踢？ | 200 | simple | — | qwen2.5:0.5b | miss | 397 | ✅ PASS |
| EXP-049 | explicit | 显式小模型用例48：地球有几颗卫星？ | 200 | simple | — | qwen2.5:0.5b | miss | 403 | ✅ PASS |
| EXP-050 | explicit | 显式小模型用例49：早餐一般吃什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 397 | ✅ PASS |
| EXP-051 | explicit | 显式大模型用例0：米饭的热量是多少？ | 200 | complex | — | gpt-oss:20b | miss | 1509 | ✅ PASS |
| EXP-052 | explicit | 显式大模型用例1：上海在北京的哪个方向？ | 200 | complex | — | gpt-oss:20b | miss | 1522 | ✅ PASS |
| EXP-053 | explicit | 显式大模型用例2：今天限行尾号是多少？ | 200 | complex | — | gpt-oss:20b | miss | 1574 | ✅ PASS |
| EXP-054 | explicit | 显式大模型用例3：太阳从哪边升起？ | 200 | complex | — | gpt-oss:20b | miss | 1642 | ✅ PASS |
| EXP-055 | explicit | 显式大模型用例4：一年有几个季节？ | 200 | complex | — | gpt-oss:20b | miss | 1787 | ✅ PASS |
| EXP-056 | explicit | 显式大模型用例5：彩虹有几种颜色？ | 200 | complex | — | gpt-oss:20b | miss | 1991 | ✅ PASS |
| EXP-057 | explicit | 显式大模型用例6：最大的海洋是哪个？ | 200 | complex | — | gpt-oss:20b | miss | 1855 | ✅ PASS |
| EXP-058 | explicit | 显式大模型用例7：熊猫主要吃什么？ | 200 | complex | — | gpt-oss:20b | miss | 1963 | ✅ PASS |
| EXP-059 | explicit | 显式大模型用例8：长城在哪个国家？ | 200 | complex | — | gpt-oss:20b | miss | 1959 | ✅ PASS |
| EXP-060 | explicit | 显式大模型用例9：一天有多少小时？ | 200 | complex | — | gpt-oss:20b | miss | 1990 | ✅ PASS |
| EXP-061 | explicit | 显式大模型用例10：冰是什么状态的？ | 200 | complex | — | gpt-oss:20b | miss | 2048 | ✅ PASS |
| EXP-062 | explicit | 显式大模型用例11：自行车有几个轮子？ | 200 | complex | — | gpt-oss:20b | miss | 2056 | ✅ PASS |
| EXP-063 | explicit | 显式大模型用例12：春节是几月份？ | 200 | complex | — | gpt-oss:20b | miss | 2155 | ✅ PASS |
| EXP-064 | explicit | 显式大模型用例13：法国的首都是哪里？ | 200 | complex | — | gpt-oss:20b | miss | 2205 | ✅ PASS |
| EXP-065 | explicit | 显式大模型用例14：日本的货币叫什么？ | 200 | complex | — | gpt-oss:20b | miss | 2243 | ✅ PASS |
| EXP-066 | explicit | 显式大模型用例15：珠穆朗玛峰高吗？ | 200 | complex | — | gpt-oss:20b | miss | 2377 | ✅ PASS |
| EXP-067 | explicit | 显式大模型用例16：企鹅会飞吗？ | 200 | complex | — | gpt-oss:20b | miss | 2295 | ✅ PASS |
| EXP-068 | explicit | 显式大模型用例17：牛奶是什么颜色的？ | 200 | complex | — | gpt-oss:20b | miss | 2343 | ✅ PASS |
| EXP-069 | explicit | 显式大模型用例18：星期天的后一天是星期几？ | 200 | complex | — | gpt-oss:20b | miss | 2418 | ✅ PASS |
| EXP-070 | explicit | 显式大模型用例19：汉字是谁发明的？ | 200 | complex | — | gpt-oss:20b | miss | 2409 | ✅ PASS |
| EXP-071 | explicit | 显式大模型用例20：端午节吃什么？ | 200 | complex | — | gpt-oss:20b | miss | 2370 | ✅ PASS |
| EXP-072 | explicit | 显式大模型用例21：飞机在哪里起降？ | 200 | complex | — | gpt-oss:20b | miss | 2662 | ✅ PASS |
| EXP-073 | explicit | 显式大模型用例22：医生在哪里工作？ | 200 | complex | — | gpt-oss:20b | miss | 3075 | ✅ PASS |
| EXP-074 | explicit | 显式大模型用例23：夏天热还是冬天热？ | 200 | complex | — | gpt-oss:20b | miss | 2625 | ✅ PASS |
| EXP-075 | explicit | 显式大模型用例24：鱼生活在哪里？ | 200 | complex | — | gpt-oss:20b | miss | 2637 | ✅ PASS |
| EXP-076 | explicit | 显式大模型用例25：月亮会发光吗？ | 200 | complex | — | gpt-oss:20b | miss | 2736 | ✅ PASS |
| EXP-077 | explicit | 显式大模型用例26：一小时有多少分钟？ | 200 | complex | — | gpt-oss:20b | miss | 2579 | ✅ PASS |
| EXP-078 | explicit | 显式大模型用例27：三角形的边数是多少？ | 200 | complex | — | gpt-oss:20b | miss | 2831 | ✅ PASS |
| EXP-079 | explicit | 显式大模型用例28：谁写了《静夜思》？ | 200 | complex | — | gpt-oss:20b | miss | 2619 | ✅ PASS |
| EXP-080 | explicit | 显式大模型用例29：巧克力的原料是什么？ | 200 | complex | — | gpt-oss:20b | miss | 2630 | ✅ PASS |
| EXP-081 | explicit | 显式大模型用例30：足球几个人踢？ | 200 | complex | — | gpt-oss:20b | miss | 2605 | ✅ PASS |
| EXP-082 | explicit | 显式大模型用例31：地球有几颗卫星？ | 200 | complex | — | gpt-oss:20b | miss | 2694 | ✅ PASS |
| EXP-083 | explicit | 显式大模型用例32：早餐一般吃什么？ | 200 | complex | — | gpt-oss:20b | miss | 2597 | ✅ PASS |
| EXP-084 | explicit | 显式大模型用例33：今天天气怎么样？ | 200 | complex | — | gpt-oss:20b | miss | 2724 | ✅ PASS |
| EXP-085 | explicit | 显式大模型用例34：北京是哪个国家的首都？ | 200 | complex | — | gpt-oss:20b | miss | 2682 | ✅ PASS |
| EXP-086 | explicit | 显式大模型用例35：一周有几天？ | 200 | complex | — | gpt-oss:20b | miss | 2549 | ✅ PASS |
| EXP-087 | explicit | 显式大模型用例36：水的化学式是什么？ | 200 | complex | — | gpt-oss:20b | miss | 2701 | ✅ PASS |
| EXP-088 | explicit | 显式大模型用例37：是谁发明了电灯泡？ | 200 | complex | — | gpt-oss:20b | miss | 2526 | ✅ PASS |
| EXP-089 | explicit | 显式大模型用例38：现在几点了？ | 200 | complex | — | gpt-oss:20b | miss | 2520 | ✅ PASS |
| EXP-090 | explicit | 显式大模型用例39：你好，介绍一下你自己 | 200 | complex | — | gpt-oss:20b | miss | 2516 | ✅ PASS |
| EXP-091 | explicit | 显式大模型用例40：明天是星期几？ | 200 | complex | — | gpt-oss:20b | miss | 2602 | ✅ PASS |
| EXP-092 | explicit | 显式大模型用例41：一年有多少个月？ | 200 | complex | — | gpt-oss:20b | miss | 2515 | ✅ PASS |
| EXP-093 | explicit | 显式大模型用例42：中国的首都在哪里？ | 200 | complex | — | gpt-oss:20b | miss | 2481 | ✅ PASS |
| EXP-094 | explicit | 显式大模型用例43：这本书多少钱？ | 200 | complex | — | gpt-oss:20b | miss | 2475 | ✅ PASS |
| EXP-095 | explicit | 显式大模型用例44：帮我翻译这句话：good morni | 200 | complex | — | gpt-oss:20b | miss | 2510 | ✅ PASS |
| EXP-096 | explicit | 显式大模型用例45：地球是圆的吗？ | 200 | complex | — | gpt-oss:20b | miss | 2567 | ✅ PASS |
| EXP-097 | explicit | 显式大模型用例46：猫的英文怎么说？ | 200 | complex | — | gpt-oss:20b | miss | 2531 | ✅ PASS |
| EXP-098 | explicit | 显式大模型用例47：1加1等于几？ | 200 | complex | — | gpt-oss:20b | miss | 2468 | ✅ PASS |
| EXP-099 | explicit | 显式大模型用例48：你喜欢什么颜色？ | 200 | complex | — | gpt-oss:20b | miss | 2647 | ✅ PASS |
| EXP-100 | explicit | 显式大模型用例49：怎么打电话给客服？ | 200 | complex | — | gpt-oss:20b | miss | 2648 | ✅ PASS |
| ERR-001 | error | 未注册模型用例 | 404 | — | — | — | — | 173 | ✅ PASS |
| ERR-002 | error | 未注册模型用例 | 404 | — | — | — | — | 129 | ✅ PASS |
| ERR-003 | error | 未注册模型用例 | 404 | — | — | — | — | 130 | ✅ PASS |
| ERR-004 | error | 未注册模型用例 | 404 | — | — | — | — | 142 | ✅ PASS |
| ERR-005 | error | 未注册模型用例 | 404 | — | — | — | — | 143 | ✅ PASS |
| ERR-006 | error | 未注册模型用例 | 404 | — | — | — | — | 142 | ✅ PASS |
| ERR-007 | error | 未注册模型用例 | 404 | — | — | — | — | 143 | ✅ PASS |
| ERR-008 | error | 未注册模型用例 | 404 | — | — | — | — | 158 | ✅ PASS |
| ERR-009 | error | 未注册模型用例 | 404 | — | — | — | — | 165 | ✅ PASS |
| ERR-010 | error | 未注册模型用例 | 404 | — | — | — | — | 158 | ✅ PASS |
| ERR-011 | error | 未注册模型用例 | 404 | — | — | — | — | 163 | ✅ PASS |
| ERR-012 | error | 未注册模型用例 | 404 | — | — | — | — | 157 | ✅ PASS |
| ERR-013 | error | 未注册模型用例 | 404 | — | — | — | — | 158 | ✅ PASS |
| ERR-014 | error | 未注册模型用例 | 404 | — | — | — | — | 148 | ✅ PASS |
| ERR-015 | error | 未注册模型用例 | 404 | — | — | — | — | 146 | ✅ PASS |
| ERR-016 | error | 未注册模型用例 | 404 | — | — | — | — | 152 | ✅ PASS |
| ERR-017 | error | 未注册模型用例 | 404 | — | — | — | — | 158 | ✅ PASS |
| ERR-018 | error | 未注册模型用例 | 404 | — | — | — | — | 165 | ✅ PASS |
| ERR-019 | error | 未注册模型用例 | 404 | — | — | — | — | 151 | ✅ PASS |
| ERR-020 | error | 未注册模型用例 | 404 | — | — | — | — | 149 | ✅ PASS |
| ERR-021 | error | 未注册模型用例 | 404 | — | — | — | — | 146 | ✅ PASS |
| ERR-022 | error | 未注册模型用例 | 404 | — | — | — | — | 151 | ✅ PASS |
| ERR-023 | error | 未注册模型用例 | 404 | — | — | — | — | 148 | ✅ PASS |
| ERR-024 | error | 未注册模型用例 | 404 | — | — | — | — | 137 | ✅ PASS |
| ERR-025 | error | 未注册模型用例 | 404 | — | — | — | — | 145 | ✅ PASS |
| ERR-026 | error | [raw]not-json | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-027 | error | [raw]{broken:} | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-028 | error | [raw][1,2,3] | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-029 | error | [raw]{'single': 'quote'} | 400 | — | — | — | — | 9 | ✅ PASS |
| ERR-030 | error | [raw]{"unterminated":  | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-031 | error | [raw]12345 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-032 | error | [raw]true | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-033 | error | [raw]"just-a-string" | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-034 | error | [raw]{,} | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-035 | error | [raw][} | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-036 | error | [raw]{"messages": null} | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-037 | error | [raw]{"messages": {}} | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-038 | error | [raw]{"messages": "text"} | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-039 | error | [raw]{"model": } | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-040 | error | [raw]null | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-041 | error | [raw]{"messages": [], "model | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-042 | error | [raw][] | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-043 | error | [raw]  {invalid leading spac | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-044 | error | [raw]{"stream": notbool} | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-045 | error | [raw]key: value | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-046 | error | 黑名单用户请求0 | 403 | — | — | — | — | 2 | ✅ PASS |
| ERR-047 | error | 黑名单用户请求1 | 403 | — | — | — | — | 3 | ✅ PASS |
| ERR-048 | error | 黑名单用户请求2 | 403 | — | — | — | — | 5 | ✅ PASS |
| ERR-049 | error | 黑名单用户请求3 | 403 | — | — | — | — | 7 | ✅ PASS |
| ERR-050 | error | 黑名单用户请求4 | 403 | — | — | — | — | 4 | ✅ PASS |
| ERR-051 | error | 黑名单用户请求5 | 403 | — | — | — | — | 6 | ✅ PASS |
| ERR-052 | error | 黑名单用户请求6 | 403 | — | — | — | — | 6 | ✅ PASS |
| ERR-053 | error | 黑名单用户请求7 | 403 | — | — | — | — | 6 | ✅ PASS |
| ERR-054 | error | 黑名单用户请求8 | 403 | — | — | — | — | 5 | ✅ PASS |
| ERR-055 | error | 黑名单用户请求9 | 403 | — | — | — | — | 7 | ✅ PASS |
| ERR-056 | error | 黑名单用户请求10 | 403 | — | — | — | — | 6 | ✅ PASS |
| ERR-057 | error | 黑名单用户请求11 | 403 | — | — | — | — | 6 | ✅ PASS |
| ERR-058 | error | 黑名单用户请求12 | 403 | — | — | — | — | 4 | ✅ PASS |
| ERR-059 | error | 黑名单用户请求13 | 403 | — | — | — | — | 2 | ✅ PASS |
| ERR-060 | error | 黑名单用户请求14 | 403 | — | — | — | — | 3 | ✅ PASS |
| ERR-061 | error | 测试敏感词普通内容第0条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-062 | error | 普通内容第1条违法违规 | 400 | — | — | — | — | 3 | ✅ PASS |
| ERR-063 | error | 普通内容第2条测试敏感词普通内容第2条 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-064 | error | 违法违规普通内容第3条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-065 | error | 普通内容第4条测试敏感词 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-066 | error | 普通内容第5条违法违规普通内容第5条 | 400 | — | — | — | — | 3 | ✅ PASS |
| ERR-067 | error | 测试敏感词普通内容第6条 | 400 | — | — | — | — | 2 | ✅ PASS |
| ERR-068 | error | 普通内容第7条违法违规 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-069 | error | 普通内容第8条测试敏感词普通内容第8条 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-070 | error | 违法违规普通内容第9条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-071 | error | 普通内容第10条测试敏感词 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-072 | error | 普通内容第11条违法违规普通内容第11条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-073 | error | 测试敏感词普通内容第12条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-074 | error | 普通内容第13条违法违规 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-075 | error | 普通内容第14条测试敏感词普通内容第14条 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-076 | error | 违法违规普通内容第15条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-077 | error | 普通内容第16条测试敏感词 | 400 | — | — | — | — | 4 | ✅ PASS |
| ERR-078 | error | 普通内容第17条违法违规普通内容第17条 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-079 | error | 测试敏感词普通内容第18条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-080 | error | 普通内容第19条违法违规 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-081 | error | 普通内容第20条测试敏感词普通内容第20条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-082 | error | 违法违规普通内容第21条 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-083 | error | 普通内容第22条测试敏感词 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-084 | error | 普通内容第23条违法违规普通内容第23条 | 400 | — | — | — | — | 8 | ✅ PASS |
| ERR-085 | error | 测试敏感词普通内容第24条 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-086 | error | 流式请求第0条包含测试敏感词 | 400 | — | — | — | — | 13 | ✅ PASS |
| ERR-087 | error | 流式请求第1条包含违法违规 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-088 | error | 流式请求第2条包含测试敏感词 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-089 | error | 流式请求第3条包含违法违规 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-090 | error | 流式请求第4条包含测试敏感词 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-091 | error | 流式请求第5条包含违法违规 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-092 | error | 流式请求第6条包含测试敏感词 | 400 | — | — | — | — | 3 | ✅ PASS |
| ERR-093 | error | 流式请求第7条包含违法违规 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-094 | error | 流式请求第8条包含测试敏感词 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-095 | error | 流式请求第9条包含违法违规 | 400 | — | — | — | — | 5 | ✅ PASS |
| ERR-096 | error | 流式请求第10条包含测试敏感词 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-097 | error | 流式请求第11条包含违法违规 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-098 | error | 流式请求第12条包含测试敏感词 | 400 | — | — | — | — | 7 | ✅ PASS |
| ERR-099 | error | 流式请求第13条包含违法违规 | 400 | — | — | — | — | 6 | ✅ PASS |
| ERR-100 | error | 流式请求第14条包含测试敏感词 | 400 | — | — | — | — | 3 | ✅ PASS |
| LEG-001 | legacy | 旧协议小模型用例0 | 200 | — | — | — | — | 415 | ✅ PASS |
| LEG-002 | legacy | 旧协议小模型用例1 | 200 | — | — | — | — | 158 | ✅ PASS |
| LEG-003 | legacy | 旧协议小模型用例2 | 200 | — | — | — | — | 363 | ✅ PASS |
| LEG-004 | legacy | 旧协议小模型用例3 | 200 | — | — | — | — | 404 | ✅ PASS |
| LEG-005 | legacy | 旧协议小模型用例4 | 200 | — | — | — | — | 374 | ✅ PASS |
| LEG-006 | legacy | 旧协议小模型用例5 | 200 | — | — | — | — | 373 | ✅ PASS |
| LEG-007 | legacy | 旧协议小模型用例6 | 200 | — | — | — | — | 376 | ✅ PASS |
| LEG-008 | legacy | 旧协议小模型用例7 | 200 | — | — | — | — | 150 | ✅ PASS |
| LEG-009 | legacy | 旧协议小模型用例8 | 200 | — | — | — | — | 381 | ✅ PASS |
| LEG-010 | legacy | 旧协议小模型用例9 | 200 | — | — | — | — | 363 | ✅ PASS |
| LEG-011 | legacy | 旧协议小模型用例10 | 200 | — | — | — | — | 387 | ✅ PASS |
| LEG-012 | legacy | 旧协议小模型用例11 | 200 | — | — | — | — | 403 | ✅ PASS |
| LEG-013 | legacy | 旧协议小模型用例12 | 200 | — | — | — | — | 432 | ✅ PASS |
| LEG-014 | legacy | 旧协议小模型用例13 | 200 | — | — | — | — | 478 | ✅ PASS |
| LEG-015 | legacy | 旧协议小模型用例14 | 200 | — | — | — | — | 525 | ✅ PASS |
| LEG-016 | legacy | 旧协议小模型用例15 | 200 | — | — | — | — | 536 | ✅ PASS |
| LEG-017 | legacy | 旧协议小模型用例16 | 200 | — | — | — | — | 547 | ✅ PASS |
| LEG-018 | legacy | 旧协议小模型用例17 | 200 | — | — | — | — | 539 | ✅ PASS |
| LEG-019 | legacy | 旧协议小模型用例18 | 200 | — | — | — | — | 458 | ✅ PASS |
| LEG-020 | legacy | 旧协议小模型用例19 | 200 | — | — | — | — | 439 | ✅ PASS |
| LEG-021 | legacy | 旧协议小模型用例20 | 200 | — | — | — | — | 432 | ✅ PASS |
| LEG-022 | legacy | 旧协议小模型用例21 | 200 | — | — | — | — | 188 | ✅ PASS |
| LEG-023 | legacy | 旧协议小模型用例22 | 200 | — | — | — | — | 432 | ✅ PASS |
| LEG-024 | legacy | 旧协议小模型用例23 | 200 | — | — | — | — | 431 | ✅ PASS |
| LEG-025 | legacy | 旧协议小模型用例24 | 200 | — | — | — | — | 440 | ✅ PASS |
| LEG-026 | legacy | 旧协议小模型用例25 | 200 | — | — | — | — | 418 | ✅ PASS |
| LEG-027 | legacy | 旧协议小模型用例26 | 200 | — | — | — | — | 184 | ✅ PASS |
| LEG-028 | legacy | 旧协议小模型用例27 | 200 | — | — | — | — | 424 | ✅ PASS |
| LEG-029 | legacy | 旧协议小模型用例28 | 200 | — | — | — | — | 192 | ✅ PASS |
| LEG-030 | legacy | 旧协议小模型用例29 | 200 | — | — | — | — | 452 | ✅ PASS |
| LEG-031 | legacy | 旧协议大模型用例0 | 200 | — | — | — | — | 2035 | ✅ PASS |
| LEG-032 | legacy | 旧协议大模型用例1 | 200 | — | — | — | — | 2168 | ✅ PASS |
| LEG-033 | legacy | 旧协议大模型用例2 | 200 | — | — | — | — | 1993 | ✅ PASS |
| LEG-034 | legacy | 旧协议大模型用例3 | 200 | — | — | — | — | 2013 | ✅ PASS |
| LEG-035 | legacy | 旧协议大模型用例4 | 200 | — | — | — | — | 2096 | ✅ PASS |
| LEG-036 | legacy | 旧协议大模型用例5 | 200 | — | — | — | — | 2042 | ✅ PASS |
| LEG-037 | legacy | 旧协议大模型用例6 | 200 | — | — | — | — | 2011 | ✅ PASS |
| LEG-038 | legacy | 旧协议大模型用例7 | 200 | — | — | — | — | 2010 | ✅ PASS |
| LEG-039 | legacy | 旧协议大模型用例8 | 200 | — | — | — | — | 1976 | ✅ PASS |
| LEG-040 | legacy | 旧协议大模型用例9 | 200 | — | — | — | — | 158 | ✅ PASS |
| LEG-041 | legacy | 旧协议大模型用例10 | 200 | — | — | — | — | 1939 | ✅ PASS |
| LEG-042 | legacy | 旧协议大模型用例11 | 200 | — | — | — | — | 155 | ✅ PASS |
| LEG-043 | legacy | 旧协议大模型用例12 | 200 | — | — | — | — | 141 | ✅ PASS |
| LEG-044 | legacy | 旧协议大模型用例13 | 200 | — | — | — | — | 1948 | ✅ PASS |
| LEG-045 | legacy | 旧协议大模型用例14 | 200 | — | — | — | — | 156 | ✅ PASS |
| LEG-046 | legacy | 旧协议大模型用例15 | 200 | — | — | — | — | 2017 | ✅ PASS |
| LEG-047 | legacy | 旧协议大模型用例16 | 200 | — | — | — | — | 2064 | ✅ PASS |
| LEG-048 | legacy | 旧协议大模型用例17 | 200 | — | — | — | — | 1999 | ✅ PASS |
| LEG-049 | legacy | 旧协议大模型用例18 | 200 | — | — | — | — | 2092 | ✅ PASS |
| LEG-050 | legacy | 旧协议大模型用例19 | 200 | — | — | — | — | 2047 | ✅ PASS |
| LEG-051 | legacy | 旧协议大模型用例20 | 200 | — | — | — | — | 1998 | ✅ PASS |
| LEG-052 | legacy | 旧协议大模型用例21 | 200 | — | — | — | — | 154 | ✅ PASS |
| LEG-053 | legacy | 旧协议大模型用例22 | 200 | — | — | — | — | 2010 | ✅ PASS |
| LEG-054 | legacy | 旧协议大模型用例23 | 200 | — | — | — | — | 156 | ✅ PASS |
| LEG-055 | legacy | 旧协议大模型用例24 | 200 | — | — | — | — | 2045 | ✅ PASS |
| LEG-056 | legacy | 旧协议大模型用例25 | 200 | — | — | — | — | 2103 | ✅ PASS |
| LEG-057 | legacy | 旧协议大模型用例26 | 200 | — | — | — | — | 162 | ✅ PASS |
| LEG-058 | legacy | 旧协议大模型用例27 | 200 | — | — | — | — | 144 | ✅ PASS |
| LEG-059 | legacy | 旧协议大模型用例28 | 200 | — | — | — | — | 143 | ✅ PASS |
| LEG-060 | legacy | 旧协议大模型用例29 | 200 | — | — | — | — | 141 | ✅ PASS |
| LEG-061 | legacy | 旧协议默认路由用例0 | 200 | — | — | — | — | 374 | ✅ PASS |
| LEG-062 | legacy | 旧协议默认路由用例1 | 200 | — | — | — | — | 383 | ✅ PASS |
| LEG-063 | legacy | 旧协议默认路由用例2 | 200 | — | — | — | — | 428 | ✅ PASS |
| LEG-064 | legacy | 旧协议默认路由用例3 | 200 | — | — | — | — | 432 | ✅ PASS |
| LEG-065 | legacy | 旧协议默认路由用例4 | 200 | — | — | — | — | 186 | ✅ PASS |
| LEG-066 | legacy | 旧协议默认路由用例5 | 200 | — | — | — | — | 437 | ✅ PASS |
| LEG-067 | legacy | 旧协议默认路由用例6 | 200 | — | — | — | — | 438 | ✅ PASS |
| LEG-068 | legacy | 旧协议默认路由用例7 | 200 | — | — | — | — | 441 | ✅ PASS |
| LEG-069 | legacy | 旧协议默认路由用例8 | 200 | — | — | — | — | 426 | ✅ PASS |
| LEG-070 | legacy | 旧协议默认路由用例9 | 200 | — | — | — | — | 447 | ✅ PASS |
| LEG-071 | legacy | 旧协议默认路由用例10 | 200 | — | — | — | — | 447 | ✅ PASS |
| LEG-072 | legacy | 旧协议默认路由用例11 | 200 | — | — | — | — | 439 | ✅ PASS |
| LEG-073 | legacy | 旧协议默认路由用例12 | 200 | — | — | — | — | 445 | ✅ PASS |
| LEG-074 | legacy | 旧协议默认路由用例13 | 200 | — | — | — | — | 457 | ✅ PASS |
| LEG-075 | legacy | 旧协议默认路由用例14 | 200 | — | — | — | — | 418 | ✅ PASS |
| LEG-076 | legacy | 旧协议默认路由用例15 | 200 | — | — | — | — | 439 | ✅ PASS |
| LEG-077 | legacy | 旧协议默认路由用例16 | 200 | — | — | — | — | 193 | ✅ PASS |
| LEG-078 | legacy | 旧协议默认路由用例17 | 200 | — | — | — | — | 192 | ✅ PASS |
| LEG-079 | legacy | 旧协议默认路由用例18 | 200 | — | — | — | — | 194 | ✅ PASS |
| LEG-080 | legacy | 旧协议默认路由用例19 | 200 | — | — | — | — | 451 | ✅ PASS |
| LEG-081 | legacy | 旧协议maxTokens映射用例0 | 200 | — | — | — | — | 362 | ✅ PASS |
| LEG-082 | legacy | 旧协议maxTokens映射用例1 | 200 | — | — | — | — | 362 | ✅ PASS |
| LEG-083 | legacy | 旧协议maxTokens映射用例2 | 200 | — | — | — | — | 355 | ✅ PASS |
| LEG-084 | legacy | 旧协议maxTokens映射用例3 | 200 | — | — | — | — | 374 | ✅ PASS |
| LEG-085 | legacy | 旧协议maxTokens映射用例4 | 200 | — | — | — | — | 366 | ✅ PASS |
| LEG-086 | legacy | 旧协议未注册模型 | 404 | — | — | — | — | 156 | ✅ PASS |
| LEG-087 | legacy | 旧协议未注册模型 | 404 | — | — | — | — | 145 | ✅ PASS |
| LEG-088 | legacy | 旧协议未注册模型 | 404 | — | — | — | — | 146 | ✅ PASS |
| LEG-089 | legacy | 旧协议未注册模型 | 404 | — | — | — | — | 151 | ✅ PASS |
| LEG-090 | legacy | 旧协议未注册模型 | 404 | — | — | — | — | 147 | ✅ PASS |
| LEG-091 | legacy | [raw]legacy-not-json | 400 | — | — | — | — | 3 | ✅ PASS |
| LEG-092 | legacy | [raw]{bad | 400 | — | — | — | — | 2 | ✅ PASS |
| LEG-093 | legacy | [raw]] | 400 | — | — | — | — | 6 | ✅ PASS |
| LEG-094 | legacy | [raw]42 | 400 | — | — | — | — | 5 | ✅ PASS |
| LEG-095 | legacy | [raw]legacy	  bad | 400 | — | — | — | — | 4 | ✅ PASS |
| LEG-096 | legacy | 旧协议黑名单0 | 403 | — | — | — | — | 4 | ✅ PASS |
| LEG-097 | legacy | 旧协议黑名单1 | 403 | — | — | — | — | 2 | ✅ PASS |
| LEG-098 | legacy | 旧协议黑名单2 | 403 | — | — | — | — | 3 | ✅ PASS |
| LEG-099 | legacy | 旧协议黑名单3 | 403 | — | — | — | — | 4 | ✅ PASS |
| LEG-100 | legacy | 旧协议黑名单4 | 403 | — | — | — | — | 6 | ✅ PASS |
| CH-001 | cache | 精确缓存写入用例0：序号0的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 364 | ✅ PASS |
| CH-002 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-003 | cache | 精确缓存写入用例1：序号1的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 413 | ✅ PASS |
| CH-004 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-005 | cache | 精确缓存写入用例2：序号2的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 425 | ✅ PASS |
| CH-006 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-007 | cache | 精确缓存写入用例3：序号3的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 356 | ✅ PASS |
| CH-008 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-009 | cache | 精确缓存写入用例4：序号4的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 393 | ✅ PASS |
| CH-010 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-011 | cache | 精确缓存写入用例5：序号5的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 357 | ✅ PASS |
| CH-012 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-013 | cache | 精确缓存写入用例6：序号6的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| CH-014 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-015 | cache | 精确缓存写入用例7：序号7的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 352 | ✅ PASS |
| CH-016 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-017 | cache | 精确缓存写入用例8：序号8的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 365 | ✅ PASS |
| CH-018 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| CH-019 | cache | 精确缓存写入用例9：序号9的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 339 | ✅ PASS |
| CH-020 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 4 | ✅ PASS |
| CH-021 | cache | 精确缓存写入用例10：序号10的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 344 | ✅ PASS |
| CH-022 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-023 | cache | 精确缓存写入用例11：序号11的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| CH-024 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-025 | cache | 精确缓存写入用例12：序号12的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 401 | ✅ PASS |
| CH-026 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 3 | ✅ PASS |
| CH-027 | cache | 精确缓存写入用例13：序号13的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| CH-028 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-029 | cache | 精确缓存写入用例14：序号14的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 362 | ✅ PASS |
| CH-030 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-031 | cache | 精确缓存写入用例15：序号15的测试问题是什么？ | 200 | — | — | qwen2.5:0.5b | hit:semantic | 205 | ✅ PASS |
| CH-032 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:semantic | 203 | ❌ FAIL |
| CH-033 | cache | 精确缓存写入用例16：序号16的测试问题是什么？ | 200 | — | — | qwen2.5:0.5b | hit:semantic | 152 | ✅ PASS |
| CH-034 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:semantic | 212 | ❌ FAIL |
| CH-035 | cache | 精确缓存写入用例17：序号17的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 311 | ✅ PASS |
| CH-036 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-037 | cache | 精确缓存写入用例18：序号18的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 368 | ✅ PASS |
| CH-038 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-039 | cache | 精确缓存写入用例19：序号19的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 410 | ✅ PASS |
| CH-040 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 6 | ✅ PASS |
| CH-041 | cache | 精确缓存写入用例20：序号20的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 369 | ✅ PASS |
| CH-042 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-043 | cache | 精确缓存写入用例21：序号21的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 385 | ✅ PASS |
| CH-044 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-045 | cache | 精确缓存写入用例22：序号22的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 375 | ✅ PASS |
| CH-046 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-047 | cache | 精确缓存写入用例23：序号23的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 369 | ✅ PASS |
| CH-048 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 5 | ✅ PASS |
| CH-049 | cache | 精确缓存写入用例24：序号24的测试问题是什么？ | 200 | simple | — | qwen2.5:0.5b | miss | 352 | ✅ PASS |
| CH-050 | cache |  | 200 | — | — | qwen2.5:0.5b | hit:exact | 7 | ✅ PASS |
| CH-051 | cache | 语义缓存基准问法0：2+1等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1254 | ✅ PASS |
| CH-052 | cache | 语义缓存同义问法0：2+1是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 204 | ✅ PASS |
| CH-053 | cache | 语义缓存基准问法1：3+1等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1197 | ✅ PASS |
| CH-054 | cache | 语义缓存同义问法1：3+1是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 198 | ✅ PASS |
| CH-055 | cache | 语义缓存基准问法2：3+2等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1170 | ✅ PASS |
| CH-056 | cache | 语义缓存同义问法2：3+2是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 204 | ✅ PASS |
| CH-057 | cache | 语义缓存基准问法3：4+1等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1220 | ✅ PASS |
| CH-058 | cache | 语义缓存同义问法3：4+1是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 207 | ✅ PASS |
| CH-059 | cache | 语义缓存基准问法4：4+2等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1155 | ✅ PASS |
| CH-060 | cache | 语义缓存同义问法4：4+2是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 196 | ✅ PASS |
| CH-061 | cache | 语义缓存基准问法5：4+3等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1156 | ✅ PASS |
| CH-062 | cache | 语义缓存同义问法5：4+3是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 191 | ✅ PASS |
| CH-063 | cache | 语义缓存基准问法6：5+1等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1149 | ✅ PASS |
| CH-064 | cache | 语义缓存同义问法6：5+1是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 193 | ✅ PASS |
| CH-065 | cache | 语义缓存基准问法7：5+2等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1150 | ✅ PASS |
| CH-066 | cache | 语义缓存同义问法7：5+2是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 201 | ✅ PASS |
| CH-067 | cache | 语义缓存基准问法8：5+3等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1143 | ✅ PASS |
| CH-068 | cache | 语义缓存同义问法8：5+3是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 211 | ✅ PASS |
| CH-069 | cache | 语义缓存基准问法9：5+4等于几？ | 200 | complex | — | gpt-oss:20b | miss | 1164 | ✅ PASS |
| CH-070 | cache | 语义缓存同义问法9：5+4是等于几？ | 200 | — | — | gpt-oss:20b | hit:semantic | 214 | ✅ PASS |
| CH-071 | cache | 跨模型隔离用例0：编号0的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1239 | ✅ PASS |
| CH-072 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 325 | ✅ PASS |
| CH-073 | cache | 跨模型隔离用例1：编号1的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1295 | ✅ PASS |
| CH-074 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| CH-075 | cache | 跨模型隔离用例2：编号2的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1286 | ✅ PASS |
| CH-076 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 337 | ✅ PASS |
| CH-077 | cache | 跨模型隔离用例3：编号3的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1316 | ✅ PASS |
| CH-078 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 340 | ✅ PASS |
| CH-079 | cache | 跨模型隔离用例4：编号4的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1353 | ✅ PASS |
| CH-080 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 353 | ✅ PASS |
| CH-081 | cache | 跨模型隔离用例5：编号5的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1363 | ✅ PASS |
| CH-082 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 345 | ✅ PASS |
| CH-083 | cache | 跨模型隔离用例6：编号6的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1375 | ✅ PASS |
| CH-084 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 341 | ✅ PASS |
| CH-085 | cache | 跨模型隔离用例7：编号7的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1365 | ✅ PASS |
| CH-086 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 355 | ✅ PASS |
| CH-087 | cache | 跨模型隔离用例8：编号8的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1322 | ✅ PASS |
| CH-088 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 342 | ✅ PASS |
| CH-089 | cache | 跨模型隔离用例9：编号9的隔离验证问题 | 200 | complex | — | gpt-oss:20b | miss | 1345 | ✅ PASS |
| CH-090 | cache |  | 200 | simple | — | qwen2.5:0.5b | miss | 343 | ✅ PASS |
| CH-091 | cache | 流式写缓存用例0：序号0的流式问题 | 200 | simple | — | qwen2.5:0.5b | miss | 339 | ✅ PASS |
| CH-092 | cache |  | 200 | — | — | qwen-lite | hit:semantic | 202 | ❌ FAIL |
| CH-093 | cache | 流式写缓存用例1：序号1的流式问题 | 200 | simple | — | qwen2.5:0.5b | miss | 309 | ✅ PASS |
| CH-094 | cache |  | 200 | — | — | qwen-lite | hit:semantic | 194 | ❌ FAIL |
| CH-095 | cache | 流式写缓存用例2：序号2的流式问题 | 200 | simple | — | qwen2.5:0.5b | miss | 311 | ✅ PASS |
| CH-096 | cache |  | 200 | — | — | qwen-lite | hit:semantic | 210 | ❌ FAIL |
| CH-097 | cache | 流式写缓存用例3：序号3的流式问题 | 200 | simple | — | qwen2.5:0.5b | miss | 307 | ✅ PASS |
| CH-098 | cache |  | 200 | — | — | qwen-lite | hit:semantic | 198 | ❌ FAIL |
| CH-099 | cache | 流式写缓存用例4：序号4的流式问题 | 200 | simple | — | qwen2.5:0.5b | miss | 304 | ✅ PASS |
| CH-100 | cache |  | 200 | — | — | qwen-lite | hit:semantic | 207 | ❌ FAIL |
| ST-001 | stream | 流式简单问句0：今天天气怎么样？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 307 | ✅ PASS |
| ST-002 | stream | 流式简单问句1：北京是哪个国家的首都？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 329 | ✅ PASS |
| ST-003 | stream | 流式简单问句2：一周有几天？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 325 | ✅ PASS |
| ST-004 | stream | 流式简单问句3：水的化学式是什么？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 342 | ✅ PASS |
| ST-005 | stream | 流式简单问句4：是谁发明了电灯泡？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 356 | ✅ PASS |
| ST-006 | stream | 流式简单问句5：现在几点了？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 345 | ✅ PASS |
| ST-007 | stream | 流式简单问句6：你好，介绍一下你自己 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 328 | ✅ PASS |
| ST-008 | stream | 流式简单问句7：明天是星期几？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 330 | ✅ PASS |
| ST-009 | stream | 流式简单问句8：一年有多少个月？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 318 | ✅ PASS |
| ST-010 | stream | 流式简单问句9：中国的首都在哪里？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 348 | ✅ PASS |
| ST-011 | stream | 流式简单问句10：这本书多少钱？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 325 | ✅ PASS |
| ST-012 | stream | 流式简单问句11：帮我翻译这句话：good mornin | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 327 | ✅ PASS |
| ST-013 | stream | 流式简单问句12：地球是圆的吗？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 329 | ✅ PASS |
| ST-014 | stream | 流式简单问句13：猫的英文怎么说？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 328 | ✅ PASS |
| ST-015 | stream | 流式简单问句14：1加1等于几？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| ST-016 | stream | 流式简单问句15：你喜欢什么颜色？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 332 | ✅ PASS |
| ST-017 | stream | 流式简单问句16：怎么打电话给客服？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 336 | ✅ PASS |
| ST-018 | stream | 流式简单问句17：米饭的热量是多少？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 340 | ✅ PASS |
| ST-019 | stream | 流式简单问句18：上海在北京的哪个方向？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 347 | ✅ PASS |
| ST-020 | stream | 流式简单问句19：今天限行尾号是多少？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 341 | ✅ PASS |
| ST-021 | stream | 流式简单问句20：太阳从哪边升起？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 334 | ✅ PASS |
| ST-022 | stream | 流式简单问句21：一年有几个季节？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 335 | ✅ PASS |
| ST-023 | stream | 流式简单问句22：彩虹有几种颜色？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 328 | ✅ PASS |
| ST-024 | stream | 流式简单问句23：最大的海洋是哪个？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 331 | ✅ PASS |
| ST-025 | stream | 流式简单问句24：熊猫主要吃什么？ | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 341 | ✅ PASS |
| ST-026 | stream | 流式复杂任务0：请证明分布式缓存架构，逐步给出多步骤方案 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1326 | ✅ PASS |
| ST-027 | stream | 流式复杂任务1：请推导消息队列可靠性，对比分析权衡并给出 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1361 | ✅ PASS |
| ST-028 | stream | 流式复杂任务2：请设计编译器词法分析，推导原理并给出优化 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1414 | ✅ PASS |
| ST-029 | stream | 流式复杂任务3：请重构分布式事务模式，给出架构评估结论与 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1426 | ✅ PASS |
| ST-030 | stream | 流式复杂任务4：请深入分析数据库索引算法，深入分析原理并 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1449 | ✅ PASS |
| ST-031 | stream | 流式复杂任务5：请对比分析推荐系统召回，逐步给出多步骤方 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1414 | ✅ PASS |
| ST-032 | stream | 流式复杂任务6：请全面评估并发容器原理，对比分析权衡并给 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1419 | ✅ PASS |
| ST-033 | stream | 流式复杂任务7：实现一个微服务拆分方案，推导原理并给出优 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1399 | ✅ PASS |
| ST-034 | stream | 流式复杂任务8：设计一个限流降级策略，给出架构评估结论与 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1454 | ✅ PASS |
| ST-035 | stream | 流式复杂任务9：写一篇垃圾回收调优，深入分析原理并规划落 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1417 | ✅ PASS |
| ST-036 | stream | 流式复杂任务10：请证明微服务拆分方案，逐步给出多步骤方 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1462 | ✅ PASS |
| ST-037 | stream | 流式复杂任务11：请推导限流降级策略，对比分析权衡并给出 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1471 | ✅ PASS |
| ST-038 | stream | 流式复杂任务12：请设计垃圾回收调优，推导原理并给出优化 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1481 | ✅ PASS |
| ST-039 | stream | 流式复杂任务13：请重构分布式缓存架构，给出架构评估结论 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1443 | ✅ PASS |
| ST-040 | stream | 流式复杂任务14：请深入分析消息队列可靠性，深入分析原理 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1473 | ✅ PASS |
| ST-041 | stream | 流式复杂任务15：请对比分析编译器词法分析，逐步给出多步 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1600 | ✅ PASS |
| ST-042 | stream | 流式复杂任务16：请全面评估分布式事务模式，对比分析权衡 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1530 | ✅ PASS |
| ST-043 | stream | 流式复杂任务17：实现一个数据库索引算法，推导原理并给出 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1516 | ✅ PASS |
| ST-044 | stream | 流式复杂任务18：设计一个推荐系统召回，给出架构评估结论 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1490 | ✅ PASS |
| ST-045 | stream | 流式复杂任务19：写一篇并发容器原理，深入分析原理并规划 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1543 | ✅ PASS |
| ST-046 | stream | 流式复杂任务20：请证明数据库索引算法，逐步给出多步骤方 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1500 | ✅ PASS |
| ST-047 | stream | 流式复杂任务21：请推导推荐系统召回，对比分析权衡并给出 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1524 | ✅ PASS |
| ST-048 | stream | 流式复杂任务22：请设计并发容器原理，推导原理并给出优化 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1523 | ✅ PASS |
| ST-049 | stream | 流式复杂任务23：请重构微服务拆分方案，给出架构评估结论 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1534 | ✅ PASS |
| ST-050 | stream | 流式复杂任务24：请深入分析限流降级策略，深入分析原理并 | 200 | complex | 0.7 | gpt-oss:20b | miss | 1551 | ✅ PASS |
| ST-051 | stream | 流式显式小模型用例0 | 200 | simple | — | qwen2.5:0.5b | miss | 340 | ✅ PASS |
| ST-052 | stream | 流式显式小模型用例1 | 200 | simple | — | qwen2.5:0.5b | miss | 316 | ✅ PASS |
| ST-053 | stream | 流式显式小模型用例2 | 200 | simple | — | qwen2.5:0.5b | miss | 333 | ✅ PASS |
| ST-054 | stream | 流式显式小模型用例3 | 200 | simple | — | qwen2.5:0.5b | miss | 327 | ✅ PASS |
| ST-055 | stream | 流式显式小模型用例4 | 200 | simple | — | qwen2.5:0.5b | miss | 352 | ✅ PASS |
| ST-056 | stream | 流式显式小模型用例5 | 200 | simple | — | qwen2.5:0.5b | miss | 360 | ✅ PASS |
| ST-057 | stream | 流式显式小模型用例6 | 200 | simple | — | qwen2.5:0.5b | miss | 351 | ✅ PASS |
| ST-058 | stream | 流式显式小模型用例7 | 200 | — | — | qwen-lite | hit:semantic | 183 | ❌ FAIL |
| ST-059 | stream | 流式显式小模型用例8 | 200 | simple | — | qwen2.5:0.5b | miss | 357 | ✅ PASS |
| ST-060 | stream | 流式显式小模型用例9 | 200 | simple | — | qwen2.5:0.5b | miss | 367 | ✅ PASS |
| ST-061 | stream | 流式显式大模型用例0 | 200 | complex | — | gpt-oss:20b | miss | 1486 | ✅ PASS |
| ST-062 | stream | 流式显式大模型用例1 | 200 | complex | — | gpt-oss:20b | miss | 1634 | ✅ PASS |
| ST-063 | stream | 流式显式大模型用例2 | 200 | complex | — | gpt-oss:20b | miss | 1575 | ✅ PASS |
| ST-064 | stream | 流式显式大模型用例3 | 200 | — | — | qwen-72b | hit:semantic | 168 | ❌ FAIL |
| ST-065 | stream | 流式显式大模型用例4 | 200 | complex | — | gpt-oss:20b | miss | 1467 | ✅ PASS |
| ST-066 | stream | 流式显式大模型用例5 | 200 | complex | — | gpt-oss:20b | miss | 1546 | ✅ PASS |
| ST-067 | stream | 流式显式大模型用例6 | 200 | complex | — | gpt-oss:20b | miss | 1712 | ✅ PASS |
| ST-068 | stream | 流式显式大模型用例7 | 200 | complex | — | gpt-oss:20b | miss | 1741 | ✅ PASS |
| ST-069 | stream | 流式显式大模型用例8 | 200 | complex | — | gpt-oss:20b | miss | 1761 | ✅ PASS |
| ST-070 | stream | 流式显式大模型用例9 | 200 | — | — | qwen-72b | hit:semantic | 155 | ❌ FAIL |
| ST-071 | stream | 流式帧序列用例0：请简短回答序号0的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 339 | ✅ PASS |
| ST-072 | stream | 流式帧序列用例1：请简短回答序号1的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 370 | ✅ PASS |
| ST-073 | stream | 流式帧序列用例2：请简短回答序号2的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 389 | ✅ PASS |
| ST-074 | stream | 流式帧序列用例3：请简短回答序号3的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 417 | ✅ PASS |
| ST-075 | stream | 流式帧序列用例4：请简短回答序号4的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 478 | ✅ PASS |
| ST-076 | stream | 流式帧序列用例5：请简短回答序号5的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 437 | ✅ PASS |
| ST-077 | stream | 流式帧序列用例6：请简短回答序号6的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 436 | ✅ PASS |
| ST-078 | stream | 流式帧序列用例7：请简短回答序号7的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 447 | ✅ PASS |
| ST-079 | stream | 流式帧序列用例8：请简短回答序号8的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 469 | ✅ PASS |
| ST-080 | stream | 流式帧序列用例9：请简短回答序号9的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 429 | ✅ PASS |
| ST-081 | stream | 流式帧序列用例10：请简短回答序号10的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 436 | ✅ PASS |
| ST-082 | stream | 流式帧序列用例11：请简短回答序号11的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 409 | ✅ PASS |
| ST-083 | stream | 流式帧序列用例12：请简短回答序号12的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 446 | ✅ PASS |
| ST-084 | stream | 流式帧序列用例13：请简短回答序号13的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 426 | ✅ PASS |
| ST-085 | stream | 流式帧序列用例14：请简短回答序号14的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 444 | ✅ PASS |
| ST-086 | stream | 流式帧序列用例15：请简短回答序号15的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 419 | ✅ PASS |
| ST-087 | stream | 流式帧序列用例16：请简短回答序号16的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 422 | ✅ PASS |
| ST-088 | stream | 流式帧序列用例17：请简短回答序号17的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 423 | ✅ PASS |
| ST-089 | stream | 流式帧序列用例18：请简短回答序号18的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 427 | ✅ PASS |
| ST-090 | stream | 流式帧序列用例19：请简短回答序号19的问题 | 200 | simple | 0.1 | qwen2.5:0.5b | miss | 423 | ✅ PASS |
| ST-091 | stream | 流式敏感词拦截0：内容包含测试敏感词 | 400 | — | — | — | — | 4 | ✅ PASS |
| ST-092 | stream | 流式敏感词拦截1：内容包含测试敏感词 | 400 | — | — | — | — | 3 | ✅ PASS |
| ST-093 | stream | 流式敏感词拦截2：内容包含测试敏感词 | 400 | — | — | — | — | 5 | ✅ PASS |
| ST-094 | stream | 流式敏感词拦截3：内容包含测试敏感词 | 400 | — | — | — | — | 5 | ✅ PASS |
| ST-095 | stream | 流式敏感词拦截4：内容包含测试敏感词 | 400 | — | — | — | — | 6 | ✅ PASS |
| ST-096 | stream | 流式黑名单拦截0 | 403 | — | — | — | — | 3 | ✅ PASS |
| ST-097 | stream | 流式黑名单拦截1 | 403 | — | — | — | — | 6 | ✅ PASS |
| ST-098 | stream | 流式黑名单拦截2 | 403 | — | — | — | — | 5 | ✅ PASS |
| ST-099 | stream | 流式黑名单拦截3 | 403 | — | — | — | — | 5 | ✅ PASS |
| ST-100 | stream | 流式黑名单拦截4 | 403 | — | — | — | — | 6 | ✅ PASS |
| RATE-001 | rate | 限流超限突发用例0 | 200 | — | — | — | — | 3720 | ✅ PASS |
| RATE-002 | rate | 限流超限突发用例1 | 200 | — | — | — | — | 4565 | ✅ PASS |
| RATE-003 | rate | 限流超限突发用例2 | 200 | — | — | — | — | 4601 | ✅ PASS |
| RATE-004 | rate | 限流超限突发用例3 | 200 | — | — | — | — | 5051 | ✅ PASS |
| RATE-005 | rate | 限流超限突发用例4 | 200 | — | — | — | — | 5768 | ✅ PASS |
| RATE-006 | rate | 限流超限突发用例5 | 200 | — | — | — | — | 4993 | ✅ PASS |
| RATE-007 | rate | 限流超限突发用例6 | 200 | — | — | — | — | 6452 | ✅ PASS |
| RATE-008 | rate | 限流超限突发用例7 | 200 | — | — | — | — | 6102 | ✅ PASS |
| RATE-009 | rate | 限流超限突发用例8 | 200 | — | — | — | — | 2209 | ✅ PASS |
| RATE-010 | rate | 限流超限突发用例9 | 200 | — | — | — | — | 1609 | ✅ PASS |
| RATE-011 | rate | 限流超限突发用例10 | 200 | — | — | — | — | 5146 | ✅ PASS |
| RATE-012 | rate | 限流超限突发用例11 | 200 | — | — | — | — | 6004 | ✅ PASS |
| RATE-013 | rate | 限流超限突发用例12 | 200 | — | — | — | — | 5987 | ✅ PASS |
| RATE-014 | rate | 限流超限突发用例13 | 200 | — | — | — | — | 4760 | ✅ PASS |
| RATE-015 | rate | 限流超限突发用例14 | 200 | — | — | — | — | 5342 | ✅ PASS |
| RATE-016 | rate | 限流超限突发用例15 | 200 | — | — | — | — | 5501 | ✅ PASS |
| RATE-017 | rate | 限流超限突发用例16 | 200 | — | — | — | — | 5852 | ✅ PASS |
| RATE-018 | rate | 限流超限突发用例17 | 200 | — | — | — | — | 5175 | ✅ PASS |
| RATE-019 | rate | 限流超限突发用例18 | 200 | — | — | — | — | 6479 | ✅ PASS |
| RATE-020 | rate | 限流超限突发用例19 | 200 | — | — | — | — | 5109 | ✅ PASS |
| RATE-021 | rate | 限流超限突发用例20 | 200 | — | — | — | — | 6564 | ✅ PASS |
| RATE-022 | rate | 限流超限突发用例21 | 200 | — | — | — | — | 7450 | ✅ PASS |
| RATE-023 | rate | 限流超限突发用例22 | 200 | — | — | — | — | 6352 | ✅ PASS |
| RATE-024 | rate | 限流超限突发用例23 | 200 | — | — | — | — | 2095 | ✅ PASS |
| RATE-025 | rate | 限流超限突发用例24 | 200 | — | — | — | — | 1375 | ✅ PASS |
| RATE-026 | rate | 限流超限突发用例25 | 200 | — | — | — | — | 1608 | ✅ PASS |
| RATE-027 | rate | 限流超限突发用例26 | 200 | — | — | — | — | 4332 | ✅ PASS |
| RATE-028 | rate | 限流超限突发用例27 | 200 | — | — | — | — | 2135 | ✅ PASS |
| RATE-029 | rate | 限流超限突发用例28 | 200 | — | — | — | — | 1397 | ✅ PASS |
| RATE-030 | rate | 限流超限突发用例29 | 200 | — | — | — | — | 4697 | ✅ PASS |
| RATE-031 | rate | 限流超限突发用例30 | 200 | — | — | — | — | 7229 | ✅ PASS |
| RATE-032 | rate | 限流超限突发用例31 | 200 | — | — | — | — | 5931 | ✅ PASS |
| RATE-033 | rate | 限流超限突发用例32 | 200 | — | — | — | — | 2017 | ✅ PASS |
| RATE-034 | rate | 限流超限突发用例33 | 200 | — | — | — | — | 1327 | ✅ PASS |
| RATE-035 | rate | 限流超限突发用例34 | 200 | — | — | — | — | 1786 | ✅ PASS |
| RATE-036 | rate | 限流超限突发用例35 | 200 | — | — | — | — | 5527 | ✅ PASS |
| RATE-037 | rate | 限流超限突发用例36 | 200 | — | — | — | — | 2413 | ✅ PASS |
| RATE-038 | rate | 限流超限突发用例37 | 200 | — | — | — | — | 4888 | ✅ PASS |
| RATE-039 | rate | 限流超限突发用例38 | 200 | — | — | — | — | 5357 | ✅ PASS |
| RATE-040 | rate | 限流超限突发用例39 | 200 | — | — | — | — | 6316 | ✅ PASS |
| RATE-041 | rate | 限流低水位用例0 | 200 | — | — | — | — | 1861 | ✅ PASS |
| RATE-042 | rate | 限流低水位用例1 | 200 | — | — | — | — | 1126 | ✅ PASS |
| RATE-043 | rate | 限流低水位用例2 | 200 | — | — | — | — | 1029 | ✅ PASS |
| RATE-044 | rate | 限流低水位用例3 | 200 | — | — | — | — | 1187 | ✅ PASS |
| RATE-045 | rate | 限流低水位用例4 | 200 | — | — | — | — | 689 | ✅ PASS |
| RATE-046 | rate | 限流低水位用例5 | 200 | — | — | — | — | 1088 | ✅ PASS |
| RATE-047 | rate | 限流低水位用例6 | 200 | — | — | — | — | 1196 | ✅ PASS |
| RATE-048 | rate | 限流低水位用例7 | 200 | — | — | — | — | 1276 | ✅ PASS |
| RATE-049 | rate | 限流低水位用例8 | 200 | — | — | — | — | 611 | ✅ PASS |
| RATE-050 | rate | 限流低水位用例9 | 200 | — | — | — | — | 1190 | ✅ PASS |
| RATE-051 | rate | 限流低水位用例10 | 200 | — | — | — | — | 1224 | ✅ PASS |
| RATE-052 | rate | 限流低水位用例11 | 200 | — | — | — | — | 1222 | ✅ PASS |
| RATE-053 | rate | 限流低水位用例12 | 200 | — | — | — | — | 565 | ✅ PASS |
| RATE-054 | rate | 限流低水位用例13 | 200 | — | — | — | — | 993 | ✅ PASS |
| RATE-055 | rate | 限流低水位用例14 | 200 | — | — | — | — | 1180 | ✅ PASS |
| RATE-056 | rate | 限流低水位用例15 | 200 | — | — | — | — | 725 | ✅ PASS |
| RATE-057 | rate | 限流低水位用例16 | 200 | — | — | — | — | 1115 | ✅ PASS |
| RATE-058 | rate | 限流低水位用例17 | 200 | — | — | — | — | 1083 | ✅ PASS |
| RATE-059 | rate | 限流低水位用例18 | 200 | — | — | — | — | 591 | ✅ PASS |
| RATE-060 | rate | 限流低水位用例19 | 200 | — | — | — | — | 421 | ✅ PASS |
| RATE-061 | rate | 限流低水位用例20 | 200 | — | — | — | — | 404 | ✅ PASS |
| RATE-062 | rate | 限流低水位用例21 | 200 | — | — | — | — | 995 | ✅ PASS |
| RATE-063 | rate | 限流低水位用例22 | 200 | — | — | — | — | 486 | ✅ PASS |
| RATE-064 | rate | 限流低水位用例23 | 200 | — | — | — | — | 1021 | ✅ PASS |
| RATE-065 | rate | 限流低水位用例24 | 200 | — | — | — | — | 469 | ✅ PASS |
| RATE-066 | rate | 限流低水位用例25 | 200 | — | — | — | — | 1147 | ✅ PASS |
| RATE-067 | rate | 限流低水位用例26 | 200 | — | — | — | — | 630 | ✅ PASS |
| RATE-068 | rate | 限流低水位用例27 | 200 | — | — | — | — | 1104 | ✅ PASS |
| RATE-069 | rate | 限流低水位用例28 | 200 | — | — | — | — | 1242 | ✅ PASS |
| RATE-070 | rate | 限流低水位用例29 | 200 | — | — | — | — | 1191 | ✅ PASS |
| RATE-071 | rate | 限流低水位用例30 | 200 | — | — | — | — | 655 | ✅ PASS |
| RATE-072 | rate | 限流低水位用例31 | 200 | — | — | — | — | 1214 | ✅ PASS |
| RATE-073 | rate | 限流低水位用例32 | 200 | — | — | — | — | 1143 | ✅ PASS |
| RATE-074 | rate | 限流低水位用例33 | 200 | — | — | — | — | 1096 | ✅ PASS |
| RATE-075 | rate | 限流低水位用例34 | 200 | — | — | — | — | 1077 | ✅ PASS |
| RATE-076 | rate | 限流低水位用例35 | 200 | — | — | — | — | 650 | ✅ PASS |
| RATE-077 | rate | 限流低水位用例36 | 200 | — | — | — | — | 432 | ✅ PASS |
| RATE-078 | rate | 限流低水位用例37 | 200 | — | — | — | — | 1112 | ✅ PASS |
| RATE-079 | rate | 限流低水位用例38 | 200 | — | — | — | — | 1161 | ✅ PASS |
| RATE-080 | rate | 限流低水位用例39 | 200 | — | — | — | — | 557 | ✅ PASS |
| RATE-081 | rate | 限流等水位用例0 | 200 | — | — | — | — | 4698 | ✅ PASS |
| RATE-082 | rate | 限流等水位用例1 | 200 | — | — | — | — | 6448 | ✅ PASS |
| RATE-083 | rate | 限流等水位用例2 | 200 | — | — | — | — | 5460 | ✅ PASS |
| RATE-084 | rate | 限流等水位用例3 | 200 | — | — | — | — | 4703 | ✅ PASS |
| RATE-085 | rate | 限流等水位用例4 | 200 | — | — | — | — | 4642 | ✅ PASS |
| RATE-086 | rate | 限流等水位用例5 | 200 | — | — | — | — | 4212 | ✅ PASS |
| RATE-087 | rate | 限流等水位用例6 | 200 | — | — | — | — | 5378 | ✅ PASS |
| RATE-088 | rate | 限流等水位用例7 | 200 | — | — | — | — | 5369 | ✅ PASS |
| RATE-089 | rate | 限流等水位用例8 | 200 | — | — | — | — | 2167 | ✅ PASS |
| RATE-090 | rate | 限流等水位用例9 | 200 | — | — | — | — | 4539 | ✅ PASS |
| RATE-091 | rate | 限流等水位用例10 | 200 | — | — | — | — | 5037 | ✅ PASS |
| RATE-092 | rate | 限流等水位用例11 | 200 | — | — | — | — | 4827 | ✅ PASS |
| RATE-093 | rate | 限流等水位用例12 | 200 | — | — | — | — | 5143 | ✅ PASS |
| RATE-094 | rate | 限流等水位用例13 | 200 | — | — | — | — | 2013 | ✅ PASS |
| RATE-095 | rate | 限流等水位用例14 | 200 | — | — | — | — | 1338 | ✅ PASS |
| RATE-096 | rate | 限流等水位用例15 | 200 | — | — | — | — | 4196 | ✅ PASS |
| RATE-097 | rate | 限流等水位用例16 | 200 | — | — | — | — | 5099 | ✅ PASS |
| RATE-098 | rate | 限流等水位用例17 | 200 | — | — | — | — | 4947 | ✅ PASS |
| RATE-099 | rate | 限流等水位用例18 | 200 | — | — | — | — | 5359 | ✅ PASS |
| RATE-100 | rate | 限流等水位用例19 | 200 | — | — | — | — | 2280 | ✅ PASS |

## 三、失败用例断言明细

### CH-032
- ❌ cache_level=exact（实际 semantic）

### CH-034
- ❌ cache_level=exact（实际 semantic）

### CH-092
- ❌ cache_level=exact（实际 semantic）

### CH-094
- ❌ cache_level=exact（实际 semantic）

### CH-096
- ❌ cache_level=exact（实际 semantic）

### CH-098
- ❌ cache_level=exact（实际 semantic）

### CH-100
- ❌ cache_level=exact（实际 semantic）

### ST-058
- ❌ intent=simple（实际 None）
- ❌ 后端模型=qwen2.5:0.5b（实际 qwen-lite）

### ST-064
- ❌ intent=complex（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen-72b）

### ST-070
- ❌ intent=complex（实际 None）
- ❌ 后端模型=gpt-oss:20b（实际 qwen-72b）

## 五、说明

- 难度路由契约：评分 ≥ 阈值（0.5）→ complex 档（qwen-72b → gpt-oss:20b），否则 simple 档（qwen-lite → qwen2.5:0.5b）；显式 model 字段优先级最高。
- 缓存契约：命中即短路（响应 meta 无 intent/difficulty）；精确缓存键含 model/messages/temperature/max_tokens，stream 排除在外（CH-07/CH-08 验证）。
- 语义缓存按 model_id 隔离（CH-05/CH-06 验证），余弦阈值 0.95。
- 限流用例按 burst 参数并发突发（超限 40 / 等水位 20 / 低水位 5），每例独立用户避免桶状态串扰（RATE-*）。
