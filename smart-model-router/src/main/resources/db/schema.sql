-- MAS 原型建表脚本（对应设计方案 §8 DDL + 附录 H.2 mas_blacklist，均为幂等语句）
-- 向量扩展（语义缓存前置，需 pgvector 已安装）
CREATE EXTENSION IF NOT EXISTS vector;

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
CREATE TABLE IF NOT EXISTS mas_semantic_cache (
    id            BIGSERIAL PRIMARY KEY,
    model_id      VARCHAR(64),
    embedding     vector(1024),
    response_json TEXT       NOT NULL,
    created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at     TIMESTAMP  NOT NULL
);
-- hnsw 无最小行数要求（ivfflat lists=100 在空表上建索引会失败），原型默认选型
CREATE INDEX IF NOT EXISTS idx_semantic_cache_vec ON mas_semantic_cache
    USING hnsw (embedding vector_cosine_ops);

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

-- §8 已知限制消除 — 分布式限流（替代 Bucket4j 内存桶，支持多实例部署）
CREATE TABLE IF NOT EXISTS mas_rate_limit (
    user_id     VARCHAR(64)  NOT NULL,
    window_key  VARCHAR(32)  NOT NULL,  -- 秒级窗口键如 '20260828194500'
    token_count INT          NOT NULL DEFAULT 0,
    window_end  TIMESTAMP    NOT NULL,
    UNIQUE (user_id, window_key)
);
CREATE INDEX IF NOT EXISTS idx_rate_limit_window ON mas_rate_limit (window_end);
