-- 种子数据（附录 C）：插入默认模型，使原型零配置可跑
-- qwen-72b 登记为 complex 档：难度路由（mas.routing.difficulty）评分 >= 阈值时路由至此
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('qwen-72b','Qwen-72B','ollama','http://localhost:11434/v1','complex',100,1)
ON CONFLICT (model_id) DO UPDATE SET intent_type = EXCLUDED.intent_type;

-- qwen-lite 登记为 simple 档：难度评分 < 阈值时路由至此（小参数模型，低成本快响应）
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('qwen-lite','Qwen-Lite','ollama','http://localhost:11434/v1','simple',100,1)
ON CONFLICT (model_id) DO UPDATE SET intent_type = EXCLUDED.intent_type;

-- 注册 embedding 模型：语义缓存（L2.2）寻址依据（附录 G.6 mas.cache.embedding-model）
INSERT INTO mas_model_config (model_id, model_name, provider, endpoint_url, intent_type, weight, status)
VALUES ('bge-m3','BGE-M3','embedding','http://localhost:11434/v1','embedding',100,1)
ON CONFLICT (model_id) DO NOTHING;
