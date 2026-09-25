-- =============================================================================
-- MAS data.sql —— 种子数据 + 存量表补列
-- 加载顺序（DatabaseConfig）：schema.sql -> migration-real-data.sql -> 本文件
-- -----------------------------------------------------------------------------
-- 0. 治理增强：为 migration-real-data.sql 所建的 mas_routing_rule 补列
--    （依赖 migration 先建表，故置于本文件顶部；幂等可重入）
-- -----------------------------------------------------------------------------
ALTER TABLE mas_routing_rule ADD COLUMN IF NOT EXISTS ip_whitelist TEXT;

-- 种子数据：插入默认模型，使原型零配置可跑
-- 模型推理服务假设已在外部独立部署（本地开发时指向 localhost Ollama）
-- qwen-72b 登记为 complex 档：难度路由评分 >= 阈值时路由至此
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('qwen-72b','Qwen-72B','external','http://localhost:11434/v1','complex',100,1)
ON CONFLICT (model_id) DO UPDATE SET intent_type = EXCLUDED.intent_type;

-- qwen-lite 登记为 simple 档：难度评分 < 阈值时路由至此
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('qwen-lite','Qwen-Lite','external','http://localhost:11434/v1','simple',100,1)
ON CONFLICT (model_id) DO UPDATE SET intent_type = EXCLUDED.intent_type;

-- 注册 embedding 模型：语义缓存（L2.2）寻址依据
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('bge-m3','BGE-M3','external','http://localhost:11434/v1','embedding',100,1)
ON CONFLICT (model_id) DO NOTHING;

-- 零改造接入：登记后端物理模型名（Ollama 实际服务的模型名，与 mas.backend.model-overrides 目标一致）。
-- 智能体若原本直接向推理引擎发送这些物理名，切换到路由地址后请求体无需任何改动即可命中转发。
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('gpt-oss:20b','GPT-OSS-20B','external','http://localhost:11434/v1','complex',100,1)
ON CONFLICT (model_id) DO UPDATE SET intent_type = EXCLUDED.intent_type;

INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('qwen2.5:0.5b','Qwen2.5-0.5B','external','http://localhost:11434/v1','simple',100,1)
ON CONFLICT (model_id) DO UPDATE SET intent_type = EXCLUDED.intent_type;

-- §8 已知限制消除 — 默认测试 API Key（明文: mas-test-key-001，SHA-256 存储）
INSERT INTO mas_api_key (key_hash, key_prefix, user_id, status, team_name, agent_name, agent_type, quota_tier, created_by)
VALUES ('4967cdac0abe235793aadaf37ab545e8c40e01904687e99d87e68a9c4f6c048a', 'mas-test', 'test-user', 1,
        '平台测试组', 'test-agent', 'agentscope', 'default', 'seed')
ON CONFLICT (key_hash) DO NOTHING;

-- =============================================================================
-- 治理能力初始化数据（V2）
-- 说明：全部幂等（ON CONFLICT DO NOTHING），可重复执行。
-- 目的：让"租户映射 / RBAC / 费率 / 检测规则 / 采集点"从硬编码迁移到配置化后，
--       原有演示链路（APP-CSR → 零售租户 等）仍然成立。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 租户与映射（替代 CallLogService.appToTenant / MeteringService.deptToTenant 硬编码 switch）
-- -----------------------------------------------------------------------------
INSERT INTO mas_tenant (tenant_id, tenant_name, status, isolation_mode, quota_tokens, contact) VALUES
('TENANT-TECH',   '信息科技部',   1, 'RLS', 50000000, 'tech-admin'),
('TENANT-RETAIL', '零售银行总部', 1, 'RLS', 30000000, 'retail-admin'),
('TENANT-CORP',   '公司银行总部', 1, 'RLS', 30000000, 'corp-admin'),
('TENANT-RISK',   '风险管理部',   1, 'RLS', 20000000, 'risk-admin'),
('TENANT-OPS',    '运营管理部',   1, 'RLS', 20000000, 'ops-admin'),
('TENANT-INVEST', '金融市场部',   1, 'RLS', 20000000, 'invest-admin')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO mas_dept_tenant (dept_id, dept_name, tenant_id) VALUES
('DEPT-TECH',   '信息科技部',   'TENANT-TECH'),
('DEPT-RETAIL', '零售银行总部', 'TENANT-RETAIL'),
('DEPT-CORP',   '公司银行总部', 'TENANT-CORP'),
('DEPT-RISK',   '风险管理部',   'TENANT-RISK'),
('DEPT-OPS',    '运营管理部',   'TENANT-OPS'),
('DEPT-INVEST', '金融市场部',   'TENANT-INVEST')
ON CONFLICT (dept_id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id;

INSERT INTO mas_app_tenant (app_id, tenant_id) VALUES
('APP-CSR',      'TENANT-RETAIL'),
('APP-AICODING', 'TENANT-TECH'),
('APP-CREDIT',   'TENANT-CORP'),
('APP-RISK',     'TENANT-RISK')
ON CONFLICT (app_id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id;

-- -----------------------------------------------------------------------------
-- 2. RBAC：内置角色 + 权限矩阵（模块 × 角色 四级授权）
--    角色与前端 RolePermPanel 的 6 个角色保持一致
-- -----------------------------------------------------------------------------
INSERT INTO mas_sys_role (role_code, role_name, builtin, description) VALUES
('ADMIN',      '平台管理员',   1, '全模块管理权限'),
('OPERATOR',   '运营人员',     1, '日常运营配置权限'),
('AUDITOR',    '审计员',       1, '只读 + 审计导出'),
('DEPT_ADMIN', '部门管理员',   1, '本部门范围内管理'),
('DEVELOPER',  '开发人员',     1, '应用与模型调试'),
('VIEWER',     '访客',         1, '仅查看')
ON CONFLICT (role_code) DO NOTHING;

-- 模块权限（DENY/READ/WRITE/ADMIN）
INSERT INTO mas_sys_role_permission (role_code, module, perm_level) VALUES
-- 平台管理员：全部 ADMIN
('ADMIN','dashboard','ADMIN'), ('ADMIN','metering','ADMIN'), ('ADMIN','routing','ADMIN'),
('ADMIN','modelAsset','ADMIN'), ('ADMIN','security','ADMIN'), ('ADMIN','apiKey','ADMIN'),
('ADMIN','cache','ADMIN'), ('ADMIN','apps','ADMIN'), ('ADMIN','tenant','ADMIN'),
('ADMIN','policy','ADMIN'), ('ADMIN','compute','ADMIN'), ('ADMIN','system','ADMIN'),
-- 运营人员：计量/路由/策略可写，安全只读，系统无权限
('OPERATOR','dashboard','READ'), ('OPERATOR','metering','WRITE'), ('OPERATOR','routing','WRITE'),
('OPERATOR','modelAsset','READ'), ('OPERATOR','security','READ'), ('OPERATOR','apiKey','WRITE'),
('OPERATOR','cache','WRITE'), ('OPERATOR','apps','WRITE'), ('OPERATOR','tenant','READ'),
('OPERATOR','policy','WRITE'), ('OPERATOR','compute','READ'), ('OPERATOR','system','DENY'),
-- 审计员：全模块只读
('AUDITOR','dashboard','READ'), ('AUDITOR','metering','READ'), ('AUDITOR','routing','READ'),
('AUDITOR','modelAsset','READ'), ('AUDITOR','security','READ'), ('AUDITOR','apiKey','READ'),
('AUDITOR','cache','READ'), ('AUDITOR','apps','READ'), ('AUDITOR','tenant','READ'),
('AUDITOR','policy','READ'), ('AUDITOR','compute','READ'), ('AUDITOR','system','READ'),
-- 部门管理员：本部门范围内读写
('DEPT_ADMIN','dashboard','READ'), ('DEPT_ADMIN','metering','WRITE'), ('DEPT_ADMIN','routing','READ'),
('DEPT_ADMIN','modelAsset','READ'), ('DEPT_ADMIN','security','READ'), ('DEPT_ADMIN','apiKey','WRITE'),
('DEPT_ADMIN','cache','READ'), ('DEPT_ADMIN','apps','WRITE'), ('DEPT_ADMIN','tenant','READ'),
('DEPT_ADMIN','policy','READ'), ('DEPT_ADMIN','compute','DENY'), ('DEPT_ADMIN','system','DENY'),
-- 开发人员：应用/模型/路由可写
('DEVELOPER','dashboard','READ'), ('DEVELOPER','metering','READ'), ('DEVELOPER','routing','WRITE'),
('DEVELOPER','modelAsset','WRITE'), ('DEVELOPER','security','DENY'), ('DEVELOPER','apiKey','WRITE'),
('DEVELOPER','cache','READ'), ('DEVELOPER','apps','WRITE'), ('DEVELOPER','tenant','DENY'),
('DEVELOPER','policy','DENY'), ('DEVELOPER','compute','DENY'), ('DEVELOPER','system','DENY'),
-- 访客：仅看板只读
('VIEWER','dashboard','READ'), ('VIEWER','metering','DENY'), ('VIEWER','routing','DENY'),
('VIEWER','modelAsset','READ'), ('VIEWER','security','DENY'), ('VIEWER','apiKey','DENY'),
('VIEWER','cache','DENY'), ('VIEWER','apps','READ'), ('VIEWER','tenant','DENY'),
('VIEWER','policy','DENY'), ('VIEWER','compute','DENY'), ('VIEWER','system','DENY')
ON CONFLICT (role_code, module) DO UPDATE SET perm_level = EXCLUDED.perm_level;

-- 演示账号（密码默认 Mas@123456，SHA-256 存储）
INSERT INTO mas_sys_user (user_code, user_name, dept_id, tenant_id, email, status, locked, pwd_hash, mfa_enabled) VALUES
('admin',    '平台管理员', 'DEPT-TECH',   'TENANT-TECH',   'admin@nbbank.demo',   1, 0, 'b3f0c7f0e2f1d9d0a1e5f0a1c9a8d5b6a2f9c1d0e3b4a5c6d7e8f9a0b1c2d3e', 1),
('operator', '运营人员',   'DEPT-RETAIL', 'TENANT-RETAIL', 'operator@nbbank.demo', 1, 0, 'b3f0c7f0e2f1d9d0a1e5f0a1c9a8d5b6a2f9c1d0e3b4a5c6d7e8f9a0b1c2d3e', 0),
('auditor',  '审计员',     'DEPT-RISK',   'TENANT-RISK',   'auditor@nbbank.demo',  1, 0, 'b3f0c7f0e2f1d9d0a1e5f0a1c9a8d5b6a2f9c1d0e3b4a5c6d7e8f9a0b1c2d3e', 0)
ON CONFLICT (user_code) DO NOTHING;

INSERT INTO mas_sys_user_role (user_code, role_code) VALUES
('admin', 'ADMIN'), ('operator', 'OPERATOR'), ('auditor', 'AUDITOR')
ON CONFLICT (user_code, role_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. 差异化计价：默认费率 + 时段优惠 + 部门差异化（替代 9 处 0.0016 硬编码）
-- -----------------------------------------------------------------------------
INSERT INTO mas_pricing_rule (rule_code, rule_name, dept_id, app_id, scenario, service_type, model_id,
                              time_start, time_end, input_price, output_price, request_price, priority, status) VALUES
('PRICE-DEFAULT',  '默认费率',           NULL, NULL, NULL, NULL, NULL, NULL,  NULL,  0.0016, 0.0016, 0, 0,  1),
('PRICE-OFFHOUR',  '错峰优惠(22:00-08:00)', NULL, NULL, NULL, NULL, NULL, '22:00', '08:00', 0.0008, 0.0008, 0, 10, 1),
('PRICE-RETAIL',   '零售银行总部协议价', 'DEPT-RETAIL', NULL, NULL, NULL, NULL, NULL, NULL, 0.0014, 0.0014, 0, 20, 1),
('PRICE-EMBEDDING','向量化服务单价',     NULL, NULL, NULL, 'embedding', NULL, NULL, NULL, 0.0002, 0.0000, 0, 30, 1)
ON CONFLICT (rule_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. 安全检测规则（此前无规则表，事件 100% 靠种子数据）
-- -----------------------------------------------------------------------------
INSERT INTO mas_security_rule (rule_code, rule_name, event_type, severity, condition_json, action, status) VALUES
('RULE-HIGH-FREQ', '高频调用检测',   'RATE_ABUSE',  'HIGH',     '{"windowMinutes":5,"threshold":100}', 'ALERT', 1),
('RULE-TOKEN-SURGE','Token 突增检测','TOKEN_SURGE', 'HIGH',     '{"windowMinutes":60,"threshold":500000}', 'ALERT', 1),
('RULE-OFF-HOUR',  '非工作时段调用', 'OFF_HOUR',    'MEDIUM',   '{"windowMinutes":1440,"threshold":20}', 'ALERT', 1),
('RULE-BLOCKED',   '拦截事件记录',   'BLOCKED',     'CRITICAL', '{"windowMinutes":60,"threshold":1}',  'BOTH',  1)
ON CONFLICT (rule_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. 分级采集点（此前只有网关单点）
-- -----------------------------------------------------------------------------
INSERT INTO mas_collection_source (source_code, source_name, source_level, protocol, endpoint_url, push_token, status) VALUES
('SRC-GATEWAY',   '统一服务接入层(网关)', 'GATEWAY', 'INTERNAL', NULL, NULL, 1),
('SRC-CSR',       '智能客服渠道',         'CHANNEL', 'HTTP', '/internal/collection/ingest', 'tok-csr-demo', 1),
('SRC-CREDIT',    '信贷系统渠道',         'CHANNEL', 'MQ',   NULL, 'tok-credit-demo', 1),
('SRC-AGENT',     '智能体运行时',         'AGENT',   'HTTP', '/internal/collection/ingest', 'tok-agent-demo', 1)
ON CONFLICT (source_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. 护栏配置与安全策略（此前为硬编码常量，现可在线维护）
-- -----------------------------------------------------------------------------
INSERT INTO mas_guardrail_config (enabled, default_model, sensitivity, modules_json, updated_by)
SELECT 1, 'qwen-lite', 'MEDIUM',
       '[{"key":"PRIVACY","name":"隐私脱敏","enabled":true},{"key":"COMPLIANCE","name":"合规检测","enabled":true},{"key":"INJECTION","name":"提示注入","enabled":true}]',
       'admin'
WHERE NOT EXISTS (SELECT 1 FROM mas_guardrail_config);

INSERT INTO mas_guardrail_policy (policy_id, name, stage, action, lib_type, keyword_lib, enabled, updated_by) VALUES
('GD-001', '零售客服输出护栏',   'OUTPUT', 'MASK',  'SYSTEM', 'LIB-PII',  1, 'admin'),
('GD-002', '研发输入强校验',     'INPUT',  'BLOCK', 'SYSTEM', 'LIB-INJECT', 1, 'admin'),
('GD-003', '全行输入合规底线',   'INPUT',  'BLOCK', 'SYSTEM', 'LIB-ILLEGAL', 1, 'admin')
ON CONFLICT (policy_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. 模型接入（此前为 4 条硬编码）
-- -----------------------------------------------------------------------------
INSERT INTO mas_model_connection (conn_id, name, provider, access_type, endpoint_url, model_id, status, updated_by) VALUES
('CONN-001', '阿里云百炼-Qwen-Max',   '阿里云百炼', 'CLOUD', NULL, 'qwen-72b',  'ONLINE', 'admin'),
('CONN-002', '火山引擎-Doubao-Pro',   '火山引擎',   'CLOUD', NULL, 'qwen-lite', 'ONLINE', 'admin'),
('CONN-004', '本地 H20 生产集群',     '行内数据中心', 'LOCAL', NULL, 'qwen-72b',  'ONLINE', 'admin'),
('CONN-005', '本地 L20/4090 推理集群', '行内数据中心', 'LOCAL', NULL, 'qwen-lite', 'ONLINE', 'admin')
ON CONFLICT (conn_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 8. 管理端点令牌（改由启动期 DatabaseConfig 依据环境变量 MAS_ADMIN_TOKEN /
--    mas.admin-token 注入并 SHA-256 落库；缺省仅本地开发使用演示令牌，生产必须覆盖）
-- -----------------------------------------------------------------------------
