-- MAS 原型建表脚本（对应设计方案 §8 DDL + 附录 H.2 mas_blacklist，均为幂等语句）
-- 向量扩展（语义缓存前置，需 pgvector 已安装）
-- 用 DO 块包裹：缺 pgvector 的嵌入 PG 上仅 NOTICE，不中止后续建表（否则 continueOnError 会跳到下一个资源文件）
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector extension not available, semantic cache vector column will be skipped';
END
$$;

-- 8.1.1 模型配置表
CREATE TABLE IF NOT EXISTS mas_model_config (
    id                  BIGSERIAL PRIMARY KEY,
    model_id            VARCHAR(64)  NOT NULL,
    model_name          VARCHAR(128) NOT NULL,
    provider            VARCHAR(32)  NOT NULL,
    endpoint_url        VARCHAR(512) NOT NULL,
    intent_type         VARCHAR(32)  NOT NULL,
    weight              INT          NOT NULL DEFAULT 100,
    status              SMALLINT     NOT NULL DEFAULT 1,
    max_context_tokens  INT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (model_id)
);

-- 8.1.2 调用记录表
CREATE TABLE IF NOT EXISTS mas_call_log (
    id                  BIGSERIAL PRIMARY KEY,
    trace_id            VARCHAR(32)  NOT NULL,
    app_id              VARCHAR(64),
    user_id             VARCHAR(64),
    agent_id            VARCHAR(64),
    model_id            VARCHAR(64)  NOT NULL,
    intent_type         VARCHAR(32),
    cache_hit           SMALLINT     NOT NULL DEFAULT 0,
    cache_level         VARCHAR(16),
    routed_to           VARCHAR(128),
    prompt_tokens       INT,
    completion_tokens   INT,
    total_tokens        INT,
    pipeline_cost_ms    INT,
    total_cost_ms       INT,
    status              SMALLINT     NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_call_log_created ON mas_call_log (created_at);
-- 审计追溯按 trace_id 检索与响应内容回填（此前无索引，回填查询走全表扫描）
CREATE INDEX IF NOT EXISTS idx_call_log_trace ON mas_call_log (trace_id);
-- 存量库升级：补 agent_id 列（请求体 OpenAI 标准 user 字段，智能体自报身份）
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64);

-- 8.1.3 Token 配额表
CREATE TABLE IF NOT EXISTS mas_token_quota (
    id          BIGSERIAL PRIMARY KEY,
    quota_type  VARCHAR(16)  NOT NULL,
    quota_key   VARCHAR(64)  NOT NULL,
    period      VARCHAR(16)  NOT NULL,
    token_limit BIGINT       NOT NULL,
    token_used  BIGINT       NOT NULL DEFAULT 0,
    reset_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (quota_type, quota_key, period)
);

-- L2.1 精确缓存表
CREATE TABLE IF NOT EXISTS mas_exact_cache (
    cache_key     VARCHAR(64) PRIMARY KEY,   -- SHA256(model|messages|temperature|max_tokens)
    model_id      VARCHAR(64),
    response_json TEXT       NOT NULL,
    created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at     TIMESTAMP  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_exact_cache_expire ON mas_exact_cache (expire_at);

-- L2.2 语义缓存表（pgvector）
-- 用 DO 块包裹：无 pgvector 扩展的环境（如嵌入式测试库）embedding 列降级为 TEXT，
-- 保证 fail-fast 加载下整库也能干净建出；有扩展则正常使用 vector(1024)。
DO $$
BEGIN
    BEGIN
        CREATE TABLE IF NOT EXISTS mas_semantic_cache (
            id            BIGSERIAL PRIMARY KEY,
            model_id      VARCHAR(64),
            embedding     vector(1024),
            response_json TEXT       NOT NULL,
            created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
            expire_at     TIMESTAMP  NOT NULL
        );
    EXCEPTION WHEN undefined_object THEN
        RAISE NOTICE 'pgvector unavailable, mas_semantic_cache.embedding falls back to TEXT';
        CREATE TABLE IF NOT EXISTS mas_semantic_cache (
            id            BIGSERIAL PRIMARY KEY,
            model_id      VARCHAR(64),
            embedding     TEXT,
            response_json TEXT       NOT NULL,
            created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
            expire_at     TIMESTAMP  NOT NULL
        );
    END;
END
$$;
-- hnsw 无最小行数要求（ivfflat lists=100 在空表上建索引会失败），原型默认选型；
-- 向量索引仅在 pgvector 可用时创建（无扩展时 embedding 为 TEXT，向量操作符类不存在）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        CREATE INDEX IF NOT EXISTS idx_semantic_cache_vec ON mas_semantic_cache
            USING hnsw (embedding vector_cosine_ops);
    END IF;
END
$$;

-- 附录 H.2 黑名单表（管理面预留，L1 与配置黑名单取并集）
CREATE TABLE IF NOT EXISTS mas_blacklist (
    id           BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(16)  NOT NULL,
    subject_key  VARCHAR(128) NOT NULL,
    reason       VARCHAR(256),
    expire_at    TIMESTAMP,
    created_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (subject_type, subject_key)
);

-- §8 已知限制消除 — API Key 鉴权体系
CREATE TABLE IF NOT EXISTS mas_api_key (
    id          BIGSERIAL PRIMARY KEY,
    key_hash    VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256(key)
    key_prefix  VARCHAR(12)  NOT NULL,          -- 前缀用于识别（如 mas-xxxx）
    user_id     VARCHAR(64)  NOT NULL,
    app_id      VARCHAR(64),
    status      SMALLINT     NOT NULL DEFAULT 1, -- 1=active, 0=revoked
    expire_at   TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_api_key_hash ON mas_api_key (key_hash);

-- §1.4 自助申请元数据扩展（幂等 DDL，存量记录新增字段为 NULL 不影响鉴权链路）
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS team_name   VARCHAR(128);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS agent_name  VARCHAR(128);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS agent_type  VARCHAR(32);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS purpose     VARCHAR(256);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS quota_tier  VARCHAR(16) DEFAULT 'default';
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS created_by  VARCHAR(64) DEFAULT 'admin';

-- §8 已知限制消除 — 分布式限流（替代 Bucket4j 内存桶，支持多实例部署）
CREATE TABLE IF NOT EXISTS mas_rate_limit (
    user_id     VARCHAR(64)  NOT NULL,
    window_key  VARCHAR(32)  NOT NULL,  -- 秒级窗口键如 '20260828194500'
    token_count INT          NOT NULL DEFAULT 0,
    window_end  TIMESTAMP    NOT NULL,
    UNIQUE (user_id, window_key)
);
CREATE INDEX IF NOT EXISTS idx_rate_limit_window ON mas_rate_limit (window_end);

-- =============================================================================
-- MAS 治理能力扩展 DDL（V2 · 宁波银行统一模型运营管控需求补齐）
-- 设计依据：供应商召集公告《主要需求概述》三节 + 需求概览.md「统一控制面 + 五大中心」
-- 约定：全部语句幂等，可重复执行（CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. mas_call_log 存量表升级：审计内容留存 + 计量维度补全
--    （公告二-8 审计追溯 / 一-5 差异化计量：业务场景、服务类型）
-- -----------------------------------------------------------------------------
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS request_content  TEXT;
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS response_content TEXT;
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS content_hash     VARCHAR(64);   -- SHA-256(请求+响应)，防篡改
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS dept_id          VARCHAR(64);   -- 部门维度
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS tenant_id        VARCHAR(64);   -- 租户维度（存量库补）
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS sla_level        VARCHAR(8);    -- P0-P3
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS data_level       VARCHAR(8);    -- L1-L3
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS scenario         VARCHAR(64);   -- 业务场景（差异化计量维度）
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS service_type     VARCHAR(32);   -- 服务类型：chat/embedding/rerank
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS cost_amount      NUMERIC(18,6); -- 单笔成本（计价引擎写入，取代前端 0.0016 硬算）
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS bill_month       VARCHAR(7);    -- 归属账期 YYYY-MM
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS billed           SMALLINT NOT NULL DEFAULT 0; -- 是否已入账（锁账后置 1）
CREATE INDEX IF NOT EXISTS idx_call_log_bill   ON mas_call_log (bill_month, tenant_id);
CREATE INDEX IF NOT EXISTS idx_call_log_dept   ON mas_call_log (dept_id);

-- -----------------------------------------------------------------------------
-- 1. 一-4/一-5 差异化计价：五维费率规则表（部门/系统/业务场景/服务类型/使用时段）
--    priority 越大越优先；命中即停
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_pricing_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(64)  NOT NULL UNIQUE,
    rule_name       VARCHAR(128) NOT NULL,
    dept_id         VARCHAR(64),              -- 维度1：部门（NULL=通配）
    app_id          VARCHAR(64),              -- 维度2：系统/应用（NULL=通配）
    scenario        VARCHAR(64),              -- 维度3：业务场景（NULL=通配）
    service_type    VARCHAR(32),              -- 维度4：服务类型 chat/embedding/rerank（NULL=通配）
    model_id        VARCHAR(64),              -- 维度4补充：模型（NULL=通配）
    time_start      VARCHAR(5),               -- 维度5：使用时段起 HH:mm（NULL=不限）
    time_end        VARCHAR(5),               -- 维度5：使用时段止 HH:mm
    input_price     NUMERIC(18,8) NOT NULL DEFAULT 0,  -- 元/ token（输入）
    output_price    NUMERIC(18,8) NOT NULL DEFAULT 0,  -- 元/ token（输出）
    request_price   NUMERIC(18,8) NOT NULL DEFAULT 0,  -- 元/ 次调用
    priority        INT          NOT NULL DEFAULT 0,
    status          SMALLINT     NOT NULL DEFAULT 1,   -- 1=启用 0=停用
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,
    created_by      VARCHAR(64)  DEFAULT 'admin',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pricing_rule_active ON mas_pricing_rule (status, priority);

-- -----------------------------------------------------------------------------
-- 2. 一-4 计费结算与对账：账单 + 账单明细 + 对账记录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_bill (
    id              BIGSERIAL PRIMARY KEY,
    bill_no         VARCHAR(64)  NOT NULL UNIQUE,
    bill_month      VARCHAR(7)   NOT NULL,     -- 账期 YYYY-MM
    tenant_id       VARCHAR(64)  NOT NULL,
    dept_id         VARCHAR(64),
    total_calls     BIGINT       NOT NULL DEFAULT 0,
    total_tokens    BIGINT       NOT NULL DEFAULT 0,
    total_amount    NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/CONFIRMED/LOCKED/SETTLED
    locked_at       TIMESTAMP,                 -- 锁账时间（锁账后该账期不可再变）
    locked_by       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bill_month, tenant_id)
);
CREATE INDEX IF NOT EXISTS idx_bill_month ON mas_bill (bill_month);

CREATE TABLE IF NOT EXISTS mas_bill_item (
    id              BIGSERIAL PRIMARY KEY,
    bill_no         VARCHAR(64)  NOT NULL,
    app_id          VARCHAR(64),
    model_id        VARCHAR(64),
    scenario        VARCHAR(64),
    service_type    VARCHAR(32),
    calls           BIGINT       NOT NULL DEFAULT 0,
    input_tokens    BIGINT       NOT NULL DEFAULT 0,
    output_tokens   BIGINT       NOT NULL DEFAULT 0,
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bill_item_no ON mas_bill_item (bill_no);

CREATE TABLE IF NOT EXISTS mas_reconciliation (
    id              BIGSERIAL PRIMARY KEY,
    bill_month      VARCHAR(7)   NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    platform_amount NUMERIC(18,2) NOT NULL,   -- 平台计量口径
    upstream_amount NUMERIC(18,2) NOT NULL,   -- 上游/行内财务口径
    diff_amount     NUMERIC(18,2) NOT NULL,
    diff_ratio      NUMERIC(10,6),
    result          VARCHAR(16)  NOT NULL,    -- MATCHED / DIFF / PENDING
    remark          VARCHAR(512),
    operator        VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 3. 二-4 RBAC 权限控制（后端此前零代码）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_sys_user (
    id              BIGSERIAL PRIMARY KEY,
    user_code       VARCHAR(64)  NOT NULL UNIQUE,
    user_name       VARCHAR(128) NOT NULL,
    dept_id         VARCHAR(64),
    tenant_id       VARCHAR(64),
    email           VARCHAR(128),
    phone           VARCHAR(32),
    status          SMALLINT     NOT NULL DEFAULT 1,   -- 1=正常 0=禁用
    locked          SMALLINT     NOT NULL DEFAULT 0,   -- 1=锁定（连续失败）
    fail_count      INT          NOT NULL DEFAULT 0,
    pwd_hash        VARCHAR(128),
    pwd_must_change SMALLINT     NOT NULL DEFAULT 0,
    mfa_enabled     SMALLINT     NOT NULL DEFAULT 0,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_sys_role (
    id              BIGSERIAL PRIMARY KEY,
    role_code       VARCHAR(64)  NOT NULL UNIQUE,
    role_name       VARCHAR(128) NOT NULL,
    builtin         SMALLINT     NOT NULL DEFAULT 0,   -- 1=内置（不可删除）
    description     VARCHAR(256),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_sys_permission (
    id              BIGSERIAL PRIMARY KEY,
    perm_code       VARCHAR(128) NOT NULL UNIQUE,      -- 如 metering:quota:write
    module          VARCHAR(64)  NOT NULL,             -- 模块：metering/security/...
    perm_name       VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_sys_user_role (
    id              BIGSERIAL PRIMARY KEY,
    user_code       VARCHAR(64)  NOT NULL,
    role_code       VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_code, role_code)
);

CREATE TABLE IF NOT EXISTS mas_sys_role_permission (
    id              BIGSERIAL PRIMARY KEY,
    role_code       VARCHAR(64)  NOT NULL,
    module          VARCHAR(64)  NOT NULL,
    perm_level      VARCHAR(16)  NOT NULL,             -- DENY/READ/WRITE/ADMIN
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_code, module)
);

-- -----------------------------------------------------------------------------
-- 4. 二-1/二-2 多租户与强制隔离（消除 Java 硬编码 switch）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_tenant (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL UNIQUE,
    tenant_name     VARCHAR(128) NOT NULL,
    status          SMALLINT     NOT NULL DEFAULT 1,   -- 1=启用 0=停用（停用即收回模型与数据权限）
    isolation_mode  VARCHAR(16)  NOT NULL DEFAULT 'RLS', -- RLS/SCHEMA/NONE
    quota_tokens    BIGINT,                            -- 租户级月度 Token 配额
    contact         VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_dept_tenant (
    id              BIGSERIAL PRIMARY KEY,
    dept_id         VARCHAR(64)  NOT NULL,
    dept_name       VARCHAR(128),
    tenant_id       VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dept_id)
);

CREATE TABLE IF NOT EXISTS mas_app_tenant (
    id              BIGSERIAL PRIMARY KEY,
    app_id          VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id)
);

-- -----------------------------------------------------------------------------
-- 5. 二-7 行为监测与异常识别：检测规则表（此前 mas_security_event 无生产者）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_security_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(64)  NOT NULL UNIQUE,
    rule_name       VARCHAR(128) NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,   -- SENSITIVE_LEAK/RATE_ABUSE/QUOTA_ABUSE/OFF_HOUR/ANOMALY/...
    severity        VARCHAR(16)  NOT NULL,   -- LOW/MEDIUM/HIGH/CRITICAL
    condition_json  TEXT         NOT NULL,   -- 阈值条件（JSON）
    action          VARCHAR(32)  NOT NULL DEFAULT 'ALERT', -- ALERT/BLOCK/BOTH
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 5.5 行为监测事件与告警（mas_security_event / mas_alert）：
--     表定义单一来源为 migration-real-data.sql，该脚本先于本文件加载
--     （见 DatabaseConfig.SCRIPTS 顺序），此处不再重复定义，避免双源漂移。
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 6. 二-8 操作审计留痕持久化（此前 15 个写端点 opRecord 用完即丢）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_op_log (
    id              BIGSERIAL PRIMARY KEY,
    op_id           VARCHAR(64)  NOT NULL UNIQUE,
    op_type         VARCHAR(64)  NOT NULL,
    op_module       VARCHAR(64),
    operator        VARCHAR(128),
    target_id       VARCHAR(128),
    detail          TEXT,
    before_json     TEXT,
    after_json      TEXT,
    result          VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    client_ip       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_op_log_created ON mas_op_log (created_at);
CREATE INDEX IF NOT EXISTS idx_op_log_module  ON mas_op_log (op_module);

-- -----------------------------------------------------------------------------
-- 7. 统一控制面：策略治理（草稿→审批→发布→回滚 + 执行留痕）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_policy (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,   -- ROUTING/QUOTA/GUARDRAIL/COST/SCHEDULE
    scope           VARCHAR(64),             -- 生效范围：全局/租户/部门/应用
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/PENDING/PUBLISHED/ROLLED_BACK
    current_version INT          NOT NULL DEFAULT 0,
    owner           VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_policy_version (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       VARCHAR(64)  NOT NULL,
    version         INT          NOT NULL,
    content_json    TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/PENDING/PUBLISHED/ROLLED_BACK
    submitter       VARCHAR(64),
    approver        VARCHAR(64),
    approve_comment VARCHAR(512),
    approved_at     TIMESTAMP,
    published_at    TIMESTAMP,
    rolled_back_at  TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (policy_id, version)
);

-- 单次请求执行了哪些策略（需求概览 POC 第 11 问）
CREATE TABLE IF NOT EXISTS mas_policy_exec_log (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(32)  NOT NULL,
    policy_id       VARCHAR(64)  NOT NULL,
    version         INT          NOT NULL,
    stage           VARCHAR(32),             -- L1/L2/L3/L4
    decision        VARCHAR(32),             -- PASS/BLOCK/ROUTE/DEGRADE
    detail          VARCHAR(512),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_policy_exec_trace ON mas_policy_exec_log (trace_id);

-- -----------------------------------------------------------------------------
-- 8. 模型资产中心：版本 / 血缘 / 灰度发布回滚
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_model_version (
    id              BIGSERIAL PRIMARY KEY,
    model_id        VARCHAR(64)  NOT NULL,
    version         VARCHAR(32)  NOT NULL,
    source_type     VARCHAR(32),             -- BASE/FINETUNE/DISTILL/QUANTIZE
    base_version    VARCHAR(32),             -- 派生自哪个版本（血缘）
    status          VARCHAR(16)  NOT NULL DEFAULT 'ONLINE', -- ONLINE/OFFLINE/ARCHIVED
    config_json     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (model_id, version)
);

CREATE TABLE IF NOT EXISTS mas_model_lineage (
    id              BIGSERIAL PRIMARY KEY,
    child_model     VARCHAR(64)  NOT NULL,
    child_version   VARCHAR(32)  NOT NULL,
    parent_model    VARCHAR(64)  NOT NULL,
    parent_version  VARCHAR(32)  NOT NULL,
    relation        VARCHAR(32)  NOT NULL,   -- FINETUNE/DISTILL/QUANTIZE/MERGE
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_model_release (
    id              BIGSERIAL PRIMARY KEY,
    release_id      VARCHAR(64)  NOT NULL UNIQUE,
    model_id        VARCHAR(64)  NOT NULL,
    from_version    VARCHAR(32),
    to_version      VARCHAR(32)  NOT NULL,
    gray_percent    INT          NOT NULL DEFAULT 0,   -- 灰度流量百分比
    gray_scope      VARCHAR(128),                      -- 灰度范围（应用/租户/用户）
    status          VARCHAR(16)  NOT NULL DEFAULT 'GRAYING', -- GRAYING/FULL/ROLLING_BACK/ROLLBACK/ABORTED
    operator        VARCHAR(64),
    sla_rollback_ms INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 9. 一-1 分级采集上报通道：采集点登记 + 渠道上报批次
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_collection_source (
    id              BIGSERIAL PRIMARY KEY,
    source_code     VARCHAR(64)  NOT NULL UNIQUE,
    source_name     VARCHAR(128) NOT NULL,
    source_level    VARCHAR(16)  NOT NULL,   -- GATEWAY/CHANNEL/AGENT（分级：接入层/渠道系统/智能体）
    protocol        VARCHAR(32)  NOT NULL DEFAULT 'HTTP', -- HTTP/MQ/FILE
    endpoint_url    VARCHAR(512),
    push_token      VARCHAR(128),            -- 上报鉴权 token
    status          SMALLINT     NOT NULL DEFAULT 1,
    last_report_at  TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_collection_batch (
    id              BIGSERIAL PRIMARY KEY,
    batch_no        VARCHAR(64)  NOT NULL UNIQUE,
    source_code     VARCHAR(64)  NOT NULL,
    record_count    INT          NOT NULL DEFAULT 0,
    accepted_count  INT          NOT NULL DEFAULT 0,
    rejected_count  INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED', -- RECEIVED/VERIFIED/REJECTED
    report_time     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 10. 一-3 计算资源消耗归集（替代 GPU 请求量反推模拟值）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_compute_metric (
    id              BIGSERIAL PRIMARY KEY,
    node_id         VARCHAR(64)  NOT NULL,
    metric_time     TIMESTAMP    NOT NULL,
    gpu_util        NUMERIC(6,2),
    gpu_mem_util    NUMERIC(6,2),
    gpu_hours       NUMERIC(12,4),           -- 卡时
    requests        INT,
    tokens          BIGINT,
    vram_total_gb   NUMERIC(8,2),            -- 显存总量（GB）
    vram_used_gb    NUMERIC(8,2),            -- 显存占用（GB）
    instance_count  INT,                     -- 承载推理实例数
    queue_depth     INT,                     -- 当前排队任务数
    source          VARCHAR(32)  NOT NULL DEFAULT 'AGENT', -- AGENT/PROMETHEUS/MANUAL
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (node_id, metric_time)
);
CREATE INDEX IF NOT EXISTS idx_compute_metric_time ON mas_compute_metric (metric_time);
-- 存量库升级：节点详情抽屉所需的显存/实例/队列维度（新建库已由上方 CREATE TABLE 带上）
ALTER TABLE mas_compute_metric ADD COLUMN IF NOT EXISTS vram_total_gb  NUMERIC(8,2);
ALTER TABLE mas_compute_metric ADD COLUMN IF NOT EXISTS vram_used_gb   NUMERIC(8,2);
ALTER TABLE mas_compute_metric ADD COLUMN IF NOT EXISTS instance_count INT;
ALTER TABLE mas_compute_metric ADD COLUMN IF NOT EXISTS queue_depth    INT;

-- -----------------------------------------------------------------------------
-- 11. 配置面持久化：限流规则补列 / 护栏配置与策略 / 模型接入
--     （此前 15 个写端点只返回 opRecord 留痕、不落库，页面"保存成功"刷新即回原值）
-- -----------------------------------------------------------------------------
-- [Moved to data.sql section 0] The ALTER adding ip_whitelist to mas_routing_rule depends on the table created by
-- migration-real-data.sql (load order: schema -> migration -> data), so it is placed at the top of data.sql.
CREATE TABLE IF NOT EXISTS mas_guardrail_config (
    id              BIGSERIAL PRIMARY KEY,
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    default_model   VARCHAR(64),
    sensitivity     VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    modules_json    TEXT,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_guardrail_policy (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    stage           VARCHAR(16)  NOT NULL DEFAULT 'INPUT',  -- INPUT/OUTPUT/BOTH
    action          VARCHAR(16)  NOT NULL DEFAULT 'MASK',   -- MASK/BLOCK/ALERT
    lib_type        VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM', -- SYSTEM/CUSTOM
    keyword_lib     TEXT,                                     -- 词库 ID 或内联词表（逗号/换行分隔，运行时并入敏感词 AC 自动机）
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    hit_count       BIGINT      NOT NULL DEFAULT 0,
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 存量库升级：词库列拓宽为 TEXT 以支持内联词表（此前 VARCHAR(64) 只能存词库 ID）
ALTER TABLE mas_guardrail_policy ALTER COLUMN keyword_lib TYPE TEXT;

CREATE TABLE IF NOT EXISTS mas_model_connection (
    id              BIGSERIAL PRIMARY KEY,
    conn_id         VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    provider        VARCHAR(64),
    access_type     VARCHAR(16)  NOT NULL DEFAULT 'CLOUD',  -- CLOUD/LOCAL/RENT
    endpoint_url    VARCHAR(512),
    model_id        VARCHAR(64),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ONLINE', -- ONLINE/OFFLINE
    latency_ms      INT,
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 12. 管理端点认证：管理员令牌（此前 /internal/* 全部裸奔）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_admin_token (
    id              BIGSERIAL PRIMARY KEY,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    user_code       VARCHAR(64)  NOT NULL,
    role_code       VARCHAR(64),
    status          SMALLINT     NOT NULL DEFAULT 1,
    expire_at       TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 13. 路由配置面落库（此前 saveRoutingEngine / saveRoutingRuleSet /
--     createAggregationGroup / saveElasticSwitch 四个端点只返回操作留痕、不落库，
--     页面提示"保存成功"刷新即回原值）
-- -----------------------------------------------------------------------------

-- 13.1 路由引擎配置（四维权重 + 策略开关），单行配置表
CREATE TABLE IF NOT EXISTS mas_routing_engine (
    id                  SMALLINT     PRIMARY KEY DEFAULT 1,
    weight_latency      NUMERIC(5,2) NOT NULL DEFAULT 30,
    weight_cost         NUMERIC(5,2) NOT NULL DEFAULT 25,
    weight_risk         NUMERIC(5,2) NOT NULL DEFAULT 25,
    weight_load         NUMERIC(5,2) NOT NULL DEFAULT 20,
    cache_first         SMALLINT     NOT NULL DEFAULT 1,
    budget_guard        SMALLINT     NOT NULL DEFAULT 1,
    sla_priority        SMALLINT     NOT NULL DEFAULT 1,
    auto_fallback       SMALLINT     NOT NULL DEFAULT 1,
    openai_compat       SMALLINT     NOT NULL DEFAULT 1,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_routing_engine (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- 13.2 场景路由规则集（信贷审批 / 风控反欺诈 / 营销触达 / 客服问答）
CREATE TABLE IF NOT EXISTS mas_routing_rule_set (
    scene_key           VARCHAR(32)  PRIMARY KEY,
    scene_name          VARCHAR(64)  NOT NULL,
    priority            VARCHAR(8)   NOT NULL DEFAULT 'P1',
    allowed_models      TEXT,
    fallback_model      VARCHAR(64),
    latency_ceil_ms     INT          NOT NULL DEFAULT 1200,
    policy_id           VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_routing_rule_set (scene_key, scene_name, priority, allowed_models, fallback_model, latency_ceil_ms)
VALUES ('CREDIT',  '信贷审批',   'P0', 'qwen-72b,qwen-lite',      'qwen-lite',     1200),
       ('RISK',    '风控反欺诈', 'P0', 'qwen-lite,qwen-72b',      'qwen-72b',       800),
       ('SERVICE', '客服问答',   'P1', 'qwen-lite,qwen2.5:0.5b',  'qwen2.5:0.5b',  1500)
ON CONFLICT (scene_key) DO NOTHING;

-- 13.3 模型聚合组
CREATE TABLE IF NOT EXISTS mas_aggregation_group (
    group_id            VARCHAR(64)  PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    members             TEXT,
    strategy            VARCHAR(16)  NOT NULL DEFAULT 'WEIGHTED', -- ROUND_ROBIN/WEIGHTED/LATENCY
    auto_skip_fault     SMALLINT     NOT NULL DEFAULT 1,
    health_check_sec    INT          NOT NULL DEFAULT 30,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_aggregation_group (group_id, name, members, strategy, auto_skip_fault, health_check_sec)
VALUES ('AGG-001', '客服问答聚合组', 'qwen-lite,qwen2.5:0.5b', 'WEIGHTED', 1, 30),
       ('AGG-002', '复杂推理聚合组', 'qwen-72b,qwen-lite',     'LATENCY',  1, 15)
ON CONFLICT (group_id) DO NOTHING;

-- 13.4 弹性切换配置（算力水位触发切租赁/云端池），单行配置表
CREATE TABLE IF NOT EXISTS mas_elastic_switch (
    id                  SMALLINT     PRIMARY KEY DEFAULT 1,
    trigger_util        NUMERIC(5,2) NOT NULL DEFAULT 85,
    sustain_min         INT          NOT NULL DEFAULT 5,
    target              VARCHAR(16)  NOT NULL DEFAULT 'RENTAL', -- RENTAL/CLOUD/LOCAL
    traffic_ratio       NUMERIC(5,2) NOT NULL DEFAULT 30,
    active              SMALLINT     NOT NULL DEFAULT 1,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_elastic_switch (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 14. 弹性算力编排（此前达成度仅 25%：无编排配置落库、无错峰任务、无异构厂商纳管）
-- -----------------------------------------------------------------------------

-- 14.1 资源编排配置（混部 / 优先级隔离 / 连续批处理 / KV 缓存 / 投机解码）
CREATE TABLE IF NOT EXISTS mas_compute_orchestration (
    id                      SMALLINT     PRIMARY KEY DEFAULT 1,
    mixed_deploy_enabled    SMALLINT     NOT NULL DEFAULT 0,
    affinity_models         TEXT,                              -- 可同卡混部的小模型，逗号分隔
    memory_reserve_pct      INT          NOT NULL DEFAULT 15,  -- 显存预留 5~30%
    prio_weight_p0          INT          NOT NULL DEFAULT 8,
    prio_weight_p1          INT          NOT NULL DEFAULT 5,
    prio_weight_p2          INT          NOT NULL DEFAULT 2,
    low_prio_queueing       SMALLINT     NOT NULL DEFAULT 0,   -- 低优任务自动降速排队
    allow_p0_preempt        SMALLINT     NOT NULL DEFAULT 0,   -- 允许 P0 抢占
    continuous_batch        SMALLINT     NOT NULL DEFAULT 0,   -- 连续批处理
    batch_max_size          INT          NOT NULL DEFAULT 64,
    prefix_kv_cache         SMALLINT     NOT NULL DEFAULT 0,   -- 前缀 KV 缓存
    kv_strategy             VARCHAR(24)  NOT NULL DEFAULT 'ROUND_ROBIN', -- ROUND_ROBIN/SEMANTIC_AWARE
    kv_tenant_isolate       SMALLINT     NOT NULL DEFAULT 1,   -- 租户间缓存隔离
    kv_sensitive_forbidden  SMALLINT     NOT NULL DEFAULT 1,   -- 敏感信息禁止缓存
    kv_ttl_min              INT          NOT NULL DEFAULT 60,
    speculative_decode      SMALLINT     NOT NULL DEFAULT 0,   -- 投机解码
    draft_model             VARCHAR(64),
    updated_by              VARCHAR(64),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_compute_orchestration (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- 14.2 错峰调度任务（批量处理 / 评测 / 研发任务排到低负载窗口）
CREATE TABLE IF NOT EXISTS mas_batch_task (
    task_id             VARCHAR(64)  PRIMARY KEY,
    task_name           VARCHAR(128) NOT NULL,
    task_type           VARCHAR(32)  NOT NULL,   -- BATCH/EVAL/DEV/MIGRATE
    target_node         VARCHAR(64),
    target_pool         VARCHAR(64),
    window_start        VARCHAR(5)   NOT NULL DEFAULT '00:00',
    window_end          VARCHAR(5)   NOT NULL DEFAULT '06:00',
    priority            VARCHAR(8)   NOT NULL DEFAULT 'P2',
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/DONE/FAILED/CANCELLED
    expect_saving       NUMERIC(12,2),
    source_suggestion   VARCHAR(256),            -- 来源：算力热区建议
    operator            VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_batch_task_status ON mas_batch_task (status);

-- 14.3 异构算力厂商纳管（英伟达 / 华为昇腾 / 沐曦 / Intel 等）
CREATE TABLE IF NOT EXISTS mas_hetero_vendor (
    vendor_id           VARCHAR(64)  PRIMARY KEY,
    vendor_name         VARCHAR(128) NOT NULL,
    arch                VARCHAR(32)  NOT NULL,   -- CUDA/ASCEND/MACA/X86
    domestic            SMALLINT     NOT NULL DEFAULT 0,
    card_models         TEXT,
    node_count          INT          NOT NULL DEFAULT 0,
    adapt_status        VARCHAR(16)  NOT NULL DEFAULT 'ADAPTED', -- ADAPTED/TESTING/INCOMPATIBLE
    perf_ratio          NUMERIC(6,2) NOT NULL DEFAULT 100,       -- 相对基准算力百分比
    stability_score     NUMERIC(5,2) NOT NULL DEFAULT 100,
    cost_per_card_hour  NUMERIC(12,4),
    sched_policy        VARCHAR(32)  NOT NULL DEFAULT 'BALANCED', -- BALANCED/PREFER/PREFER_DOMESTIC/DISABLED
    enabled             SMALLINT     NOT NULL DEFAULT 1,
    discover_mode       VARCHAR(16)  NOT NULL DEFAULT 'REGISTRY', -- REGISTRY(注册表)/AGENT(自动发现)/PROMETHEUS
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_hetero_vendor (vendor_id, vendor_name, arch, domestic, card_models, node_count, perf_ratio, cost_per_card_hour, sched_policy)
VALUES ('NVIDIA', '英伟达',   'CUDA',   0, 'H20,L20,4090D', 96, 100, 12.5,  'BALANCED'),
       ('HUAWEI', '华为昇腾', 'ASCEND', 1, '910B,910A',     24,  82,  8.60, 'PREFER_DOMESTIC'),
       ('METAX',  '沐曦',     'MACA',   1, 'C500',          12,  76,  7.20, 'PREFER_DOMESTIC'),
       ('INTEL',  'Intel',    'X86',    0, 'Gaudi2',        10,  68,  6.10, 'BALANCED')
ON CONFLICT (vendor_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 15. 模型评测与归档（此前 getEvals / getArchivedModels / getArchiveRules 全为前端 mock）
-- -----------------------------------------------------------------------------

-- 15.1 模型评测记录（准入评测 / 灰度 A-B 评测）
CREATE TABLE IF NOT EXISTS mas_model_eval (
    eval_id             VARCHAR(64)  PRIMARY KEY,
    model_id            VARCHAR(64)  NOT NULL,
    version             VARCHAR(32),
    eval_type           VARCHAR(24)  NOT NULL,   -- ADMISSION/GRAY/REGRESSION
    dataset             VARCHAR(128),
    accuracy            NUMERIC(6,2),
    task_success_rate   NUMERIC(6,2),
    human_accept_rate   NUMERIC(6,2),
    avg_latency_ms      INT,
    token_cost          NUMERIC(12,2),
    anomaly_rate        NUMERIC(6,2),
    compliance_rate     NUMERIC(6,2),
    score               NUMERIC(6,2),
    conclusion          VARCHAR(16),             -- PASS/FAIL/WARN
    operator            VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_model_eval_model ON mas_model_eval (model_id);

-- 15.2 模型归档（下线与归档管理）
CREATE TABLE IF NOT EXISTS mas_model_archive (
    archive_id          VARCHAR(64)  PRIMARY KEY,
    model_id            VARCHAR(64)  NOT NULL,
    model_name          VARCHAR(128),
    reason              VARCHAR(24)  NOT NULL,   -- NO_CALL_90D/REPLACED/COMPLIANCE/MANUAL
    retention           VARCHAR(16)  NOT NULL DEFAULT '24M', -- 24M/PERMANENT
    value_score         VARCHAR(2),              -- A/B/C/D
    score_cost          NUMERIC(6,2),
    score_conversion    NUMERIC(6,2),
    score_risk_acc      NUMERIC(6,2),
    dependency_apps     TEXT,                    -- 归档时快照的在用应用，逗号分隔
    archived_by         VARCHAR(64),
    archived_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revived_at          TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_model_archive_model ON mas_model_archive (model_id);

-- 15.3 自动归档规则
CREATE TABLE IF NOT EXISTS mas_archive_rule (
    rule_id             VARCHAR(64)  PRIMARY KEY,
    rule_key            VARCHAR(32)  NOT NULL,   -- NO_CALL_90D/REPLACED/COMPLIANCE
    rule_name           VARCHAR(128) NOT NULL,
    enabled             SMALLINT     NOT NULL DEFAULT 0,
    action              VARCHAR(16)  NOT NULL DEFAULT 'SUGGEST', -- SUGGEST/AUTO_ARCHIVE
    threshold_days      INT          NOT NULL DEFAULT 90,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_archive_rule (rule_id, rule_key, rule_name, enabled, action, threshold_days)
VALUES ('AR-001', 'NO_CALL_90D', '90 天无调用自动建议下线', 0, 'SUGGEST', 90),
       ('AR-002', 'REPLACED',    '版本被替代自动建议归档',   0, 'SUGGEST', 0),
       ('AR-003', 'COMPLIANCE',  '合规名单变更自动下线',     0, 'SUGGEST', 0)
ON CONFLICT (rule_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 16. 数据分级差异化管控（data_level/sla_level 此前只有字段、无执行逻辑）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_data_level_policy (
    data_level          VARCHAR(8)   PRIMARY KEY,   -- L1/L2/L3
    allow_local         SMALLINT     NOT NULL DEFAULT 1,
    allow_cloud         SMALLINT     NOT NULL DEFAULT 1,
    allow_rental        SMALLINT     NOT NULL DEFAULT 1,
    mask_strength       VARCHAR(16)  NOT NULL DEFAULT 'STANDARD', -- NONE/STANDARD/STRICT
    content_retention   SMALLINT     NOT NULL DEFAULT 1,          -- 请求/响应原文是否留存
    retention_days      INT          NOT NULL DEFAULT 180,
    max_context_tokens  INT,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO mas_data_level_policy (data_level, allow_local, allow_cloud, allow_rental, mask_strength, content_retention, retention_days)
VALUES ('L1', 1, 1, 1, 'STANDARD', 1, 180),
       ('L2', 1, 1, 0, 'STANDARD', 1, 180),
       ('L3', 1, 0, 0, 'STRICT',   0, 365)
ON CONFLICT (data_level) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 17. 限流规则运行时命中统计（mas_routing_rule 此前从不进入请求链路）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_rate_limit_hit (
    id                  BIGSERIAL PRIMARY KEY,
    rule_id             VARCHAR(64)  NOT NULL,
    target_type         VARCHAR(16),
    target_id           VARCHAR(64),
    hit_action          VARCHAR(16),             -- REJECT/QUEUE/DOWNGRADE
    hit_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rate_limit_hit_rule ON mas_rate_limit_hit (rule_id);
CREATE INDEX IF NOT EXISTS idx_rate_limit_hit_time ON mas_rate_limit_hit (hit_at);

-- 存量库升级：模型配置补部署形态列（数据分级管控需要判断模型是否落在本地/云端/租赁算力上）
ALTER TABLE mas_model_config ADD COLUMN IF NOT EXISTS deploy_type VARCHAR(16) DEFAULT 'LOCAL'; -- LOCAL/CLOUD/RENTAL

-- 注：mas_dept_quota 由 migration-real-data.sql 创建（本脚本先于它加载），
--     因此"配额通知渠道/恢复审批"补列语句放在最后加载的 data.sql 中，避免 relation does not exist。

-- -----------------------------------------------------------------------------
-- 18. 平台配置 KV（系统参数 / 安全基线 / 成本预警：此前只在前端内存，保存刷新即回退）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_platform_config (
    config_key          VARCHAR(64)  PRIMARY KEY,  -- SYSTEM_PARAMS / SECURITY_BASELINE / COST_ALERT
    config_json         TEXT         NOT NULL,
    operator            VARCHAR(64),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 19. 通用配置承载表 mas_platform_config 已覆盖下列"配置类"功能的真实落库：
--     检测模块 / 词库 / 检测模型默认 / 优化建议 / 举报反馈 / 模型卡片 / 广场申请 /
--     节点配置 / 异构调度策略 / 引擎版本 / 应急工单。
--     这些此前只在前端内存（保存刷新即回退），现统一以 JSON 落入 mas_platform_config，
--     由 /internal/system/config/{key} 读写。config_key 取值见前端 api.ts 的 CONFIG_KEYS。
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 21. 行内底座/运营管理体系对接适配器（兼容适配#2：IAM / 4A / 统一监控 / 告警平台 / 工单系统）
--     mas_base_integration 存对接配置（含 GATEWAY 网关系统）；mas_integration_log 存同步/转发事件。
--     外部行内系统对接为配置门控：未配置 endpoint 或 enabled=false 时不发起真实外呼，
--     适配器以本地闭环（IAM 同步取本地账号、监控快照取本地指标、告警转本地工单）保证演示可跑通。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mas_base_integration (
    code         VARCHAR(32)  PRIMARY KEY,                       -- IAM / FOUR_A / MONITOR / ALERT / TICKET
    name         VARCHAR(64)  NOT NULL,
    type         VARCHAR(16)  NOT NULL,
    endpoint     VARCHAR(256),
    enabled      SMALLINT     NOT NULL DEFAULT 0,
    last_sync_at TIMESTAMP,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',       -- PENDING / CONNECTED / UNREACHABLE / DISABLED
    remark       VARCHAR(256),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mas_integration_log (
    id         BIGSERIAL PRIMARY KEY,
    int_code   VARCHAR(32)  NOT NULL,
    action     VARCHAR(32)  NOT NULL,                           -- SYNC_IAM / PUSH_MONITOR / FORWARD_ALERT / CREATE_TICKET / TEST
    status     VARCHAR(16)  NOT NULL DEFAULT 'OK',              -- OK / FAIL
    message    VARCHAR(512),
    latency_ms INT,
    operator   VARCHAR(64),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_integration_log_code ON mas_integration_log (int_code);
CREATE INDEX IF NOT EXISTS idx_integration_log_time ON mas_integration_log (created_at);

INSERT INTO mas_base_integration (code, name, type, endpoint, enabled, status, remark)
VALUES
  ('IAM',     '统一身份认证(IAM)', 'IAM',     NULL, 0, 'PENDING', '演示环境未配置行内 IAM 地址'),
  ('FOUR_A',  '4A 运维审计',      'FOUR_A',  NULL, 0, 'PENDING', '演示环境未配置 4A 地址'),
  ('MONITOR', '统一监控平台',      'MONITOR', NULL, 0, 'PENDING', '演示环境未配置监控平台地址'),
  ('ALERT',   '告警平台',          'ALERT',   NULL, 0, 'PENDING', '演示环境未配置告警平台地址'),
  ('TICKET',  '工单系统',          'TICKET',  NULL, 0, 'PENDING', '演示环境未配置工单系统地址'),
  ('GATEWAY', '行内 API 网关',     'GATEWAY', NULL, 0, 'PENDING', '演示环境未配置网关地址（公告三-1 网关系统衔接）')
ON CONFLICT (code) DO NOTHING;
