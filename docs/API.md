# 统一模型运营管控模块 · API 接口文档

> 版本：2026-09-28 · 服务：`smart-model-router` · Base Path：`/smart-router`
>
> 本文档为对外交付物（招标公告·兼容适配「提供系统接口、技术文档」要求），与代码内路由一一对应。

---

## 1. 通用约定

### 1.1 认证

| 端点类别 | 鉴权方式 |
|---|---|
| 外部推理 API（`/v1/*`） | `Authorization: Bearer <API Key>`（密钥管理签发，形如 `sk-...`） |
| 管理端点（`/internal/*`） | `X-Admin-Token: <token>` 或 `Authorization: Bearer <token>`（登录签发） |
| 采集上报（`/internal/collection/ingest`） | `X-Source-Code` + `X-Push-Token`（采集点级令牌，独立于用户令牌） |
| 登录（`/internal/auth/login`） | 免令牌（凭据验证本身即签发入口） |

### 1.2 RBAC 权限 enforcement（AdminAuthFilter）

- 路由前缀映射 RBAC 模块（dashboard/metering/routing/modelAsset/security/apiKey/cache/apps/tenant/policy/compute/system）；
- 方法映射级别：`GET=READ`、`POST/PUT/PATCH=WRITE`、`DELETE=ADMIN`；
- `/internal/rbac/**`、`/internal/admin-auth/**` 一律要求 `ADMIN`；`system` 模块写操作要求 `ADMIN`；
- 权限不足返回 `403`；令牌无效返回 `401`；判定链路异常按无权限处理（fail-closed）；
- 数据面租户隔离：非 ADMIN 用户调用 `/internal/metering/call-logs` 强制按本人租户过滤。

### 1.3 登录与令牌

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/auth/login` | 登录。Body：`{userCode, password}`。返回 `{token, userCode, userName, roles, pwdMustChange, expireAt}`。连续失败 5 次自动锁定 |
| GET | `/internal/auth/me` | 当前用户 + 各模块权限级别（DENY/READ/WRITE/ADMIN） |
| POST | `/internal/auth/change-password` | Body：`{oldPassword, newPassword}`（新密码 ≥10 位且含字母数字） |
| POST | `/internal/auth/logout` | 吊销当前令牌 |
| POST | `/internal/admin-auth/tokens` | 管理员签发长期令牌（ADMIN） |
| GET/DELETE | `/internal/admin-auth/tokens` | 令牌列表 / 吊销（ADMIN） |

### 1.4 错误格式

```json
{ "error": { "code": "forbidden", "message": "当前用户无 metering 模块 WRITE 权限" } }
```

`400` 入参校验失败（IllegalArgumentException）· `401` 未认证 · `403` 权限不足 · `404` 资源不存在 · `500` 服务端异常。

---

## 2. 外部推理 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/chat/completions` | 对话补全（OpenAI 兼容格式），经 L1-L4 路由管线：规则拦截 → 多级缓存 → 意图路由 → 执行管控（限流/配额/灰度） |
| POST | `/v1/embeddings` | 向量化 |
| GET | `/v1/models` | 可用模型列表 |

---

## 3. 管理端点（/internal/*）

### 3.1 运营驾驶舱（dashboard）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/dashboard/summary` | 平台总览 KPI |
| GET | `/internal/dashboard/roi` | 投入产出综合分析（本月成本投入 / 评测价值 / 落地节省 / 投入产出比） |
| GET | `/internal/dashboard/token-series?hours=&step=` | Token 时序 |
| GET | `/internal/dashboard/trend-series` | 时延/利用率趋势 |
| GET | `/internal/dashboard/dept-tco` · `app-tco-rank` · `model-tco-rank` | TCO 分摊（部门/应用/模型） |
| GET | `/internal/dashboard/funnel` · `heatmap` · `queue` · `circuit-breakers` · `rate-limit-hits` · `batch-trend` | 漏斗/热力/队列/熔断/限流命中/批处理趋势 |
| GET | `/internal/dashboard/optimize-advice` | 优化建议（含闭环推进） |

### 3.2 计量运营（metering/billing/pricing）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/metering/call-logs?user_id=&app_id=&model=&status=&tenant_id=&page=&size=` | 调用日志（非 ADMIN 强制本租户） |
| GET | `/internal/metering/model-stats` · `monthly-bills` · `personal-usage` · `model-recommends` | 模型统计/月账单/个人用量/模型推荐 |
| GET/PUT | `/internal/metering/quotas` · `/internal/metering/quotas/{deptId}` | 部门配额 |
| POST | `/internal/metering/quotas/{deptId}/resume`（`/approve`） | 配额恢复申请/审批 |
| GET/PUT | `/internal/metering/cost-alert` | 成本告警配置 |
| GET | `/internal/billing/bills` · `/internal/billing/bills/{billNo}/items` | 账单与明细 |
| POST | `/internal/billing/generate` · `/{billNo}/confirm` · `/{billNo}/lock` · `/{billNo}/settle` | 账单生成→确认→锁账→结算 |
| GET/POST | `/internal/billing/reconciliations` | 平台口径 vs 上游口径对账 |
| GET/POST/PUT/DELETE | `/internal/pricing/rules`（`/{ruleCode}`） | 差异化计价规则（五维：部门/应用/场景/服务类型+模型/时段） |
| POST | `/internal/pricing/simulate` | 计价模拟 |

### 3.3 路由治理（routing）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/internal/routing/engine` · `/internal/routing/elastic-switch` | 路由引擎配置 / 弹性开关 |
| GET/POST/PUT/DELETE | `/internal/routing/routing-rule-sets`（`/{sceneKey}`） | 场景路由规则集 |
| GET/POST/PUT/DELETE | `/internal/routing/rate-limit-rules`（`/{ruleId}`） | 规则限流（并发/QPS/Token 上限） |
| GET/POST/DELETE | `/internal/routing/aggregation-groups`（`/{groupId}`） | 聚合组 |
| GET | `/internal/routing/router-logs` | 路由决策日志 |

### 3.4 模型资产（models）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/models` | 模型列表 |
| GET/POST | `/internal/models/versions` · `/{modelId}/versions` · `/{modelId}/versions/{version}/status`(PATCH) | 版本管理 |
| GET/POST/PUT/DELETE | `/internal/models/connections`（`/{connId}`，`/{connId}/test`） | 接入连接管理/连通性测试 |
| GET | `/internal/models/{modelId}/dependencies` · `/{modelId}/lineage` · `/internal/models/stale` · `/internal/models/benefits` | 依赖检查/血缘/闲置识别/收益分析 |
| GET/POST | `/internal/models/eval-records` · `/internal/models/evals` | 评测记录 |
| GET/POST/DELETE | `/internal/models/archives`（`/{archiveId}/revive`，`/by-model/{modelId}/revive`） | 模型归档/复活 |
| GET/PUT | `/internal/models/archive-rules` | 自动归档规则 |
| GET/POST | `/internal/models/releases`；PATCH `/{releaseId}/percent`；POST `/{releaseId}/rollback` | 灰度发布/推进/回滚 |

### 3.5 安全运营（security）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/internal/security/events` · `/internal/security/alerts/{alertId}/handle` | 安全事件/告警处理 |
| GET/POST/PUT/DELETE | `/internal/security/detect-rules`（`/{ruleCode}`） | 异常检测规则 |
| POST | `/internal/security/scan` | 触发行为扫描（高频/Token 突增/非工作时段） |
| GET/PUT | `/internal/security/guardrail` · `/internal/security/guardrail/policies`（`/{policyId}`） | 内容护栏（运行时敏感词过滤含流式阻断） |
| GET/PUT | `/internal/security/data-level-policies` | 数据分级管控（脱敏） |
| GET/PUT | `/internal/security/baseline` | 安全基线 |

### 3.6 弹性算力（compute）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/compute/summary` · `nodes` · `vendors` · `batch-tasks` · `off-peak-suggestions` | 总览/节点/异构厂商/批任务/错峰建议 |
| POST | `/internal/compute/vendors`（`/discover`） · `batch-tasks`（`/{taskId}/advance`） · `metrics` | 厂商纳管/自动发现、批任务推进、算力指标上报 |
| GET/PUT | `/internal/compute/orchestration` | 算力编排配置（混合部署/显存预留/优先级权重/KV 治理） |
| DELETE | `/internal/compute/batch-tasks/{taskId}` | 删除批任务 |

### 3.7 租户 / 应用 / 密钥 / RBAC / 系统

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | `/internal/tenants`（`/{tenantId}`，`/{tenantId}/status` PATCH） | 多租户管理 |
| GET/POST | `/internal/tenants/app-mapping` · `/internal/tenants/dept-mapping` | 应用/部门归属映射 |
| CRUD | `/internal/apps`（`/{app_id}`，`/{app_id}/toggle`） | 应用注册与启停 |
| CRUD | `/internal/api-keys`（`/{prefix}`，`/{prefix}/status`，`/{prefix}/rotate`，`/batch-revoke`，`/{prefix}/usage`） | API 密钥全生命周期 |
| CRUD | `/internal/rbac/users` · `roles` · `permissions` · `perm-matrix` · `members` | RBAC 用户/角色/权限矩阵/成员 |
| GET/PUT | `/internal/system/config/{key}` · `/internal/system/params` · `/internal/system/op-logs` | 平台配置 KV / 系统参数 / 操作日志 |

### 3.8 行内系统对接（integration，兼容适配）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/internal/integration` | 对接点配置（IAM / FOUR_A / MONITOR / ALERT / TICKET / **GATEWAY**） |
| POST | `/internal/integration/{code}/test` | 连通性测试（未配置地址如实回报 UNREACHABLE） |
| POST | `/internal/integration/iam/sync` · `/monitor/push` · `/alert/forward/{alertId}` · `/ticket` | IAM 同步 / 监控推送 / 告警转发 / 工单下发 |
| POST | `/internal/integration/gateway/register` | 向行内 API 网关注册服务与路由（网关系统衔接） |
| GET | `/internal/integration/logs` | 对接事件日志 |

### 3.9 采集（collection）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT | `/internal/collection/sources`（`/{sourceCode}`） | 采集点注册与管理 |
| POST | `/internal/collection/ingest` | 渠道数据批量上报（`X-Source-Code` + `X-Push-Token` 源级鉴权） |
| GET | `/internal/collection/batches` | 上报批次查询 |

---

## 4. 部署初始化说明

1. 依赖：PostgreSQL 12+；建库后由应用启动脚本自动执行 `db/schema.sql → db/migration-real-data.sql → db/data.sql`（幂等）。
2. 种子账号：`admin / operator / auditor`，初始密码 `Mas@123456`（SHA-256 落库），**首次登录后必须修改**。
3. 管理令牌引导：应用启动时从环境变量 `MAS_ADMIN_TOKEN`（或 `mas.admin-token`）读取引导令牌写入 `mas_admin_token`（user=admin, role=ADMIN）；未配置时写入演示令牌 `mat-demo-admin-token` 并输出安全告警——**任何非本地部署必须配置强令牌**。后续应使用登录签发的令牌，引导令牌可在「系统管理」中吊销。
4. 建议流程：`MAS_ADMIN_TOKEN=<强随机值> 启动 → 用引导令牌调 POST /internal/auth/login 为管理员改密 → 吊销引导令牌 → 日常使用登录令牌`。
