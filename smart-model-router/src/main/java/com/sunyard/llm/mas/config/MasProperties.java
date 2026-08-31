package com.sunyard.llm.mas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置属性绑定（附录 G.6 配置项清单，前缀 mas）。
 * 原型阶段为静态配置；后续管理面接入时通过 SettingProvider 接口切换到数据库来源（附录 H）。
 */
@ConfigurationProperties(prefix = "mas")
public class MasProperties {

    private Backend backend = new Backend();
    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();
    private Blacklist blacklist = new Blacklist();
    private SensitiveWords sensitiveWords = new SensitiveWords();
    private Quota quota = new Quota();
    private Routing routing = new Routing();
    private Compress compress = new Compress();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    public Backend getBackend() { return backend; }
    public void setBackend(Backend backend) { this.backend = backend; }
    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public Blacklist getBlacklist() { return blacklist; }
    public void setBlacklist(Blacklist blacklist) { this.blacklist = blacklist; }
    public SensitiveWords getSensitiveWords() { return sensitiveWords; }
    public void setSensitiveWords(SensitiveWords sensitiveWords) { this.sensitiveWords = sensitiveWords; }
    public Quota getQuota() { return quota; }
    public void setQuota(Quota quota) { this.quota = quota; }
    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }
    public Compress getCompress() { return compress; }
    public void setCompress(Compress compress) { this.compress = compress; }
    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public static class Backend {
        /** 无模型配置时的兜底后端 */
        private String defaultEndpoint = "http://localhost:11434/v1";
        /** 后端引擎响应超时，超时按附录 G.2 返 504 */
        private Duration timeout = Duration.ofSeconds(60);
        /** 原型工程字段：按路由 model_id 映射后端真实模型名（如 qwen-lite → Ollama 实际拉取的 qwen2.5:0.5b），未配置则原样转发 */
        private Map<String, String> modelOverrides = new LinkedHashMap<>();

        public String getDefaultEndpoint() { return defaultEndpoint; }
        public void setDefaultEndpoint(String defaultEndpoint) { this.defaultEndpoint = defaultEndpoint; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public Map<String, String> getModelOverrides() { return modelOverrides; }
        public void setModelOverrides(Map<String, String> modelOverrides) { this.modelOverrides = modelOverrides; }
    }

    public static class Cache {
        private Duration exactTtl = Duration.ofMinutes(30);
        private int maxExactEntries = 100_000;
        private Duration semanticTtl = Duration.ofMinutes(60);
        private double semanticThreshold = 0.95;
        /** §8 改进：按意图类型覆盖语义缓存阈值（优先级高于全局默认） */
        private Map<String, Double> semanticThresholdByIntent = new LinkedHashMap<>();
        private int maxSemanticEntries = 50_000;
        /** embedding 模型标识，需在 mas_model_config 中注册（provider=embedding） */
        private String embeddingModel = "bge-m3";

        public Duration getExactTtl() { return exactTtl; }
        public void setExactTtl(Duration exactTtl) { this.exactTtl = exactTtl; }
        public int getMaxExactEntries() { return maxExactEntries; }
        public void setMaxExactEntries(int maxExactEntries) { this.maxExactEntries = maxExactEntries; }
        public Duration getSemanticTtl() { return semanticTtl; }
        public void setSemanticTtl(Duration semanticTtl) { this.semanticTtl = semanticTtl; }
        public double getSemanticThreshold() { return semanticThreshold; }
        public void setSemanticThreshold(double semanticThreshold) { this.semanticThreshold = semanticThreshold; }
        public Map<String, Double> getSemanticThresholdByIntent() { return semanticThresholdByIntent; }
        public void setSemanticThresholdByIntent(Map<String, Double> semanticThresholdByIntent) { this.semanticThresholdByIntent = semanticThresholdByIntent; }
        public int getMaxSemanticEntries() { return maxSemanticEntries; }
        public void setMaxSemanticEntries(int maxSemanticEntries) { this.maxSemanticEntries = maxSemanticEntries; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    }

    public static class RateLimit {
        private int perUserQps = 20;

        public int getPerUserQps() { return perUserQps; }
        public void setPerUserQps(int perUserQps) { this.perUserQps = perUserQps; }
    }

    public static class Blacklist {
        /** 配置式黑名单，与 PG mas_blacklist 表合并生效 */
        private List<String> users = new ArrayList<>();

        public List<String> getUsers() { return users; }
        public void setUsers(List<String> users) { this.users = users; }
    }

    public static class SensitiveWords {
        /** AC 词表路径，文件缺失/为空时跳过敏感词检查 */
        private String path = "classpath:sensitive-words.txt";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    public static class Quota {
        private long perUserPerMinute = 100_000L;
        private long perUserPerDay = 5_000_000L;

        public long getPerUserPerMinute() { return perUserPerMinute; }
        public void setPerUserPerMinute(long perUserPerMinute) { this.perUserPerMinute = perUserPerMinute; }
        public long getPerUserPerDay() { return perUserPerDay; }
        public void setPerUserPerDay(long perUserPerDay) { this.perUserPerDay = perUserPerDay; }
    }

    public static class Routing {
        private String defaultModel = "qwen-72b";
        private Difficulty difficulty = new Difficulty();

        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public Difficulty getDifficulty() { return difficulty; }
        public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    }

    public static class Difficulty {
        /** 难度路由开关：开启后无显式 model 的请求按难度分在 simple/complex 模型间路由 */
        private boolean enabled = true;
        /** 难度阈值：评分 >= threshold 判定为 complex，走大参数模型 */
        private double threshold = 0.5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
    }

    public static class Compress {
        private int maxContextTokens = 4096;

        public int getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
    }

    /** 熔断器配置（§8 已知限制 — 熔断/故障转移） */
    public static class CircuitBreakerConfig {
        /** 连续失败次数触发熔断 */
        private int failureThreshold = 5;
        /** 熔断持续时间，之后进入 HALF_OPEN */
        private Duration openDuration = Duration.ofSeconds(30);
        /** HALF_OPEN 状态最大试探次数 */
        private int halfOpenMaxAttempts = 2;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public Duration getOpenDuration() { return openDuration; }
        public void setOpenDuration(Duration openDuration) { this.openDuration = openDuration; }
        public int getHalfOpenMaxAttempts() { return halfOpenMaxAttempts; }
        public void setHalfOpenMaxAttempts(int halfOpenMaxAttempts) { this.halfOpenMaxAttempts = halfOpenMaxAttempts; }
    }
}
