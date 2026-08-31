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
INSERT INTO mas_api_key (key_hash, key_prefix, user_id, status)
VALUES ('4967cdac0abe235793aadaf37ab545e8c40e01904687e99d87e68a9c4f6c048a', 'mas-test', 'test-user', 1)
ON CONFLICT (key_hash) DO NOTHING;
