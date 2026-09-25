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
    source          VARCHAR(32)  NOT NULL DEFAULT 'AGENT', -- AGENT/PROMETHEUS/MANUAL
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (node_id, metric_time)
);
CREATE INDEX IF NOT EXISTS idx_compute_metric_time ON mas_compute_metric (metric_time);

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
    keyword_lib     VARCHAR(64),
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    hit_count       BIGINT       NOT NULL DEFAULT 0,
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
