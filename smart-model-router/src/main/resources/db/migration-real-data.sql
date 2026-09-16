-- ============================================================
-- MAS 数据库迁移：补全字段 + 新建配置表 + 种子数据
-- ============================================================

-- 辅助函数（先定义）
CREATE OR REPLACE FUNCTION tenant_to_dept(t_id VARCHAR) RETURNS VARCHAR AS $$
BEGIN
    RETURN CASE t_id
        WHEN 'TENANT-TECH' THEN 'DEPT-TECH'
        WHEN 'TENANT-RETAIL' THEN 'DEPT-RETAIL'
        WHEN 'TENANT-CORP' THEN 'DEPT-CORP'
        WHEN 'TENANT-RISK' THEN 'DEPT-RISK'
        WHEN 'TENANT-OPS' THEN 'DEPT-OPS'
        WHEN 'TENANT-INVEST' THEN 'DEPT-INVEST'
        ELSE 'DEPT-TECH'
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 1. 补全 mas_call_log 缺失字段
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS sla_level VARCHAR(8);
ALTER TABLE mas_call_log ADD COLUMN IF NOT EXISTS data_level VARCHAR(8);

-- 2. 回填 tenant_id / sla_level / data_level
UPDATE mas_call_log SET tenant_id = 'TENANT-RETAIL', sla_level = 'P1', data_level = 'L2'
WHERE user_id = 'anonymous' AND tenant_id IS NULL;

UPDATE mas_call_log SET tenant_id = 'TENANT-TECH', sla_level = 'P2', data_level = 'L1'
WHERE (user_id LIKE 'rate-%' OR user_id = 'stress') AND tenant_id IS NULL;

UPDATE mas_call_log SET tenant_id = 'TENANT-TECH', sla_level = 'P2', data_level = 'L2'
WHERE user_id IN ('test-user','diff-test','db-test') AND tenant_id IS NULL;

UPDATE mas_call_log SET tenant_id = 'TENANT-CORP', sla_level = 'P0', data_level = 'L3'
WHERE user_id = 'agent-zero-touch' AND tenant_id IS NULL;

-- 3. 回填 app_id
UPDATE mas_call_log SET app_id = 'APP-CSR' WHERE model_id = 'qwen-lite' AND (app_id IS NULL OR app_id = '');
UPDATE mas_call_log SET app_id = 'APP-AICODING' WHERE model_id = 'qwen-72b' AND (app_id IS NULL OR app_id = '');
UPDATE mas_call_log SET app_id = 'APP-CREDIT' WHERE model_id = 'qwen2.5:0.5b' AND (app_id IS NULL OR app_id = '');
UPDATE mas_call_log SET app_id = 'APP-RISK' WHERE model_id = 'broken-model' AND (app_id IS NULL OR app_id = '');
UPDATE mas_call_log SET app_id = 'APP-CSR', model_id = 'qwen-lite' WHERE (model_id IS NULL OR model_id = '') AND (app_id IS NULL OR app_id = '');

-- 4. 新建表
CREATE TABLE IF NOT EXISTS mas_routing_rule (
    rule_id VARCHAR(32) PRIMARY KEY, name VARCHAR(128) NOT NULL,
    target_type VARCHAR(16) NOT NULL, target_id VARCHAR(64),
    enabled BOOLEAN DEFAULT true, qps_limit INT DEFAULT 0,
    input_token_limit INT DEFAULT 0, output_token_limit INT DEFAULT 0,
    concurrency INT DEFAULT 0, over_action VARCHAR(16) DEFAULT 'REJECT',
    hits_24h INT DEFAULT 0, created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mas_dept_quota (
    dept_id VARCHAR(32) PRIMARY KEY, dept_name VARCHAR(64) NOT NULL,
    month_token_quota BIGINT DEFAULT 0, used_tokens BIGINT DEFAULT 0,
    month_cost DOUBLE PRECISION DEFAULT 0,
    over_limit_stop BOOLEAN DEFAULT false, warn_threshold INT DEFAULT 80,
    status VARCHAR(16) DEFAULT 'NORMAL', created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mas_security_event (
    event_id VARCHAR(32) PRIMARY KEY, trace_id VARCHAR(64),
    tenant_id VARCHAR(64), user_id VARCHAR(64), app_id VARCHAR(64),
    asset_id VARCHAR(64), event_type VARCHAR(32) NOT NULL,
    event_level VARCHAR(16) NOT NULL, guardrail_stage VARCHAR(16),
    rule_id VARCHAR(32), rule_name VARCHAR(128),
    masked BOOLEAN DEFAULT false, blocked BOOLEAN DEFAULT false,
    reason_code VARCHAR(64), reason_text TEXT,
    log_storage_type VARCHAR(16), hash_signature VARCHAR(128),
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mas_alert (
    alert_id VARCHAR(32) PRIMARY KEY, alert_status VARCHAR(16) DEFAULT 'OPEN',
    event_level VARCHAR(16) NOT NULL, title VARCHAR(256) NOT NULL,
    detail TEXT, trace_id VARCHAR(64), created_at TIMESTAMP DEFAULT now()
);

-- 4.5 应用注册表（§1.5 应用身份统一管控 - 阶段 1）
CREATE TABLE IF NOT EXISTS mas_app (
    app_id          VARCHAR(32) PRIMARY KEY,
    app_name        VARCHAR(128) NOT NULL,      -- 中文应用名（平台统一维护）
    app_name_en     VARCHAR(64),                -- 英文名（可选）
    dept_id         VARCHAR(32) NOT NULL,       -- 归属部门
    owner_id        VARCHAR(64) NOT NULL,       -- 负责人
    owner_email     VARCHAR(128),               -- 负责人邮箱
    sla_level       VARCHAR(8) DEFAULT 'P1',    -- 默认 SLA 等级
    data_level      VARCHAR(8) DEFAULT 'L2',    -- 默认数据等级
    status          SMALLINT DEFAULT 1,         -- 0=待审批, 1=已启用, 2=已停用, 3=已驳回
    month_quota     BIGINT DEFAULT 0,           -- 月度 Token 配额（0=不限制）
    description     TEXT,                       -- 应用描述/用途说明
    approved_by     VARCHAR(64),                -- 审批人
    approved_at     TIMESTAMP,                  -- 审批时间
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);

-- 应用接入申请表（审批流程留痕）
CREATE TABLE IF NOT EXISTS mas_app_application (
    apply_id        VARCHAR(32) PRIMARY KEY,
    app_id          VARCHAR(32),                -- 审批通过后写入 mas_app
    applicant_id    VARCHAR(64) NOT NULL,       -- 申请人
    applicant_dept  VARCHAR(32) NOT NULL,
    app_name        VARCHAR(128) NOT NULL,
    purpose         TEXT NOT NULL,              -- 用途说明
    est_month_calls BIGINT,                     -- 预估月调用量
    status          VARCHAR(16) DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
    reviewer_id     VARCHAR(64),                -- 审批人
    review_opinion  TEXT,                       -- 审批意见
    created_at      TIMESTAMP DEFAULT now(),
    reviewed_at     TIMESTAMP
);

-- API Key 表增加 app_id 关联字段
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS app_id VARCHAR(32);

-- API Key 表增加 §1.4 元数据字段（与 jar 内 schema.sql 保持一致）
-- 注意: 这 6 列【必须】由本迁移脚本添加——jar 内 schema.sql 的同名 ALTER
-- 在应用启动时以 mas 账号执行,但表属主是 postgres(部署脚本所建),
-- PG 要求 ALTER TABLE 必须是表属主,GRANT ALL 不含此权限,
-- 应用启动时的 ALTER 会静默失败(continueOnError),导致鉴权查询
-- (selectByHash 选取这些列)报 column does not exist → 全部 Key 401。
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS team_name   VARCHAR(128);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS agent_name  VARCHAR(128);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS agent_type  VARCHAR(32);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS purpose     VARCHAR(256);
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS quota_tier  VARCHAR(16) DEFAULT 'default';
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS created_by  VARCHAR(64) DEFAULT 'admin';

-- 5. 种子数据 - 限流规则
INSERT INTO mas_routing_rule (rule_id, name, target_type, target_id, enabled, qps_limit, input_token_limit, output_token_limit, concurrency, over_action, hits_24h)
VALUES
('RL-CFG-001', '全行 QPS 总闸', 'GLOBAL', '*', true, 8000, 131072, 32768, 2000, 'REJECT',
    (SELECT COUNT(*) FROM mas_call_log WHERE created_at >= now() - INTERVAL '24 hours')),
('RL-CFG-002', '智能客服限流', 'APP', 'APP-CSR', true, 1200, 32768, 8192, 400, 'QUEUE',
    (SELECT COUNT(*) FROM mas_call_log WHERE app_id = 'APP-CSR' AND created_at >= now() - INTERVAL '24 hours')),
('RL-CFG-003', 'AI 代码助手限流', 'APP', 'APP-AICODING', true, 600, 65536, 16384, 200, 'DOWNGRADE',
    (SELECT COUNT(*) FROM mas_call_log WHERE app_id = 'APP-AICODING' AND created_at >= now() - INTERVAL '24 hours'))
ON CONFLICT (rule_id) DO NOTHING;

-- 6. 种子数据 - 部门配额
INSERT INTO mas_dept_quota (dept_id, dept_name, month_token_quota, used_tokens, month_cost, status)
VALUES
('DEPT-TECH', '信息科技部', 3600000000,
    (SELECT COALESCE(SUM(total_tokens),0) FROM mas_call_log WHERE tenant_id='TENANT-TECH' AND created_at >= date_trunc('month',now())),
    0, 'NORMAL'),
('DEPT-RETAIL', '零售银行总部', 3000000000,
    (SELECT COALESCE(SUM(total_tokens),0) FROM mas_call_log WHERE tenant_id='TENANT-RETAIL' AND created_at >= date_trunc('month',now())),
    0, 'NORMAL'),
('DEPT-CORP', '公司银行总部', 2000000000,
    (SELECT COALESCE(SUM(total_tokens),0) FROM mas_call_log WHERE tenant_id='TENANT-CORP' AND created_at >= date_trunc('month',now())),
    0, 'NORMAL'),
('DEPT-RISK', '风险管理部', 1500000000, 0, 0, 'NORMAL'),
('DEPT-OPS', '运营管理部', 1200000000, 0, 0, 'NORMAL'),
('DEPT-INVEST', '金融市场部', 900000000, 0, 0, 'NORMAL')
ON CONFLICT (dept_id) DO UPDATE SET used_tokens = EXCLUDED.used_tokens;

-- 6.5 种子数据 - 应用注册表（§1.5 阶段 1）
INSERT INTO mas_app (app_id, app_name, app_name_en, dept_id, owner_id, sla_level, data_level, status, month_quota, description)
VALUES
('APP-CSR', '智能客服', 'Customer Service Bot', 'DEPT-RETAIL', 'zhang.san', 'P1', 'L2', 1, 3000000000, '零售银行智能客服问答系统'),
('APP-AICODING', 'AI代码助手', 'AI Coding Assistant', 'DEPT-TECH', 'li.si', 'P2', 'L1', 1, 2000000000, '信息科技部 AI 代码生成与审查工具'),
('APP-CREDIT', '信贷审批助手', 'Credit Approval Assistant', 'DEPT-CORP', 'wang.wu', 'P0', 'L3', 1, 1500000000, '公司银行信贷审批报告自动生成'),
('APP-RISK', '风控报告生成', 'Risk Report Generator', 'DEPT-RISK', 'zhao.liu', 'P0', 'L3', 1, 1000000000, '风险管理部风控分析报告生成')
ON CONFLICT (app_id) DO NOTHING;

-- 7. 种子数据 - 安全事件
INSERT INTO mas_security_event (event_id, trace_id, tenant_id, user_id, app_id, asset_id, event_type, event_level, guardrail_stage, rule_id, rule_name, masked, blocked, reason_code, reason_text, log_storage_type)
SELECT
    'SEC-' || LPAD(id::text, 6, '0'),
    trace_id, tenant_id, user_id, app_id, model_id,
    'MASKING', 'INFO', 'OUTPUT', 'MASK-001', '敏感信息脱敏',
    true, false, 'PII_DETECTED', '输出已脱敏处理', 'MASKED'
FROM mas_call_log WHERE cache_hit = 1 LIMIT 10
ON CONFLICT (event_id) DO NOTHING;

-- 8. 种子数据 - 告警
INSERT INTO mas_alert (alert_id, alert_status, event_level, title, detail, trace_id) VALUES
('ALT-001', 'OPEN', 'CRITICAL', '智能客服应用 Token 用量接近配额上限',
    'APP-CSR 本月已用 ' || (SELECT COALESCE(SUM(total_tokens),0) FROM mas_call_log WHERE app_id='APP-CSR' AND created_at >= date_trunc('month',now())) || ' tokens',
    (SELECT trace_id FROM mas_call_log WHERE app_id='APP-CSR' ORDER BY created_at DESC LIMIT 1)),
('ALT-002', 'ACKNOWLEDGED', 'WARN', 'POOL-L20 节点高负载', 'node-gpu-04 队列深度 12', NULL),
('ALT-003', 'CLOSED', 'INFO', '缓存命中率波动', 'INS-QWEN72-01 命中率回落至 61%', NULL)
ON CONFLICT (alert_id) DO NOTHING;
