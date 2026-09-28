package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.entity.RoutingRuleEntity;
import com.sunyard.llm.mas.mapper.RateLimitHitMapper;
import com.sunyard.llm.mas.mapper.RoutingRuleMapper;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理面限流规则运行时（招标二-2 流量管控 + POC 第 4 问）：
 * 把 mas_routing_rule 里配置的 QPS / 输入Token / 输出Token / 并发 / 超限动作
 * 真正作用到请求链路上，支持按 GLOBAL / DEPT / APP / API_KEY / MODEL 五个维度分别设限。
 * <p>
 * 【改造背景】此前运行时限流只有 RateLimitService 的 per-user 固定 QPS（来自配置文件），
 * UI 上那张 mas_routing_rule 规则表只被读出来展示 —— 在页面上把 QPS 改成 1 也不会有任何效果。
 * <p>
 * 计数采用进程内分钟级滑动窗口（规则本身 60s 热加载）；多实例部署时各实例按自身配额执行，
 * 需要全局严格一致时可关闭本服务改用数据库原子计数。
 */
@Service
public class RuleRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RuleRateLimitService.class);
    private static final long RULES_TTL_MS = 60_000L;
    private static final long WINDOW_MS = 60_000L;
    /** QUEUE 动作的最长排队等待，避免把线程挂死 */
    private static final int MAX_QUEUE_MS = 2_000;

    /** 超限动作 */
    public enum Action { PASS, REJECT, QUEUE, DOWNGRADE }

    private final RoutingRuleMapper ruleMapper;
    private final RateLimitHitMapper hitMapper;
    private final AppProfileService appProfileService;
    private final MasProperties props;

    private volatile List<RoutingRuleEntity> rules = List.of();
    private volatile long loadedAt = 0L;

    /** 规则 → 分钟内请求时间戳队列 */
    private final ConcurrentHashMap<String, long[]> windowCounter = new ConcurrentHashMap<>();
    /** 规则 → 当前并发数 */
    private final ConcurrentHashMap<String, AtomicInteger> concurrency = new ConcurrentHashMap<>();

    public RuleRateLimitService(RoutingRuleMapper ruleMapper, RateLimitHitMapper hitMapper,
                                AppProfileService appProfileService, MasProperties props) {
        this.ruleMapper = ruleMapper;
        this.hitMapper = hitMapper;
        this.appProfileService = appProfileService;
        this.props = props;
    }

    public boolean enabled() {
        return props == null || props.getGovernance() == null || props.getGovernance().isRuleRateLimitEnabled();
    }

    // ---------------- 规则加载 ----------------

    private List<RoutingRuleEntity> activeRules() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < RULES_TTL_MS && !rules.isEmpty()) return rules;
        synchronized (this) {
            if (now - loadedAt < RULES_TTL_MS && !rules.isEmpty()) return rules;
            try {
                List<RoutingRuleEntity> all = ruleMapper.selectList(null);
                List<RoutingRuleEntity> enabled = new ArrayList<>();
                for (RoutingRuleEntity r : all) {
                    if (r.getEnabled() != null && r.getEnabled()) enabled.add(r);
                }
                rules = enabled;
                loadedAt = now;
            } catch (Exception e) {
                log.warn("rate limit rule reload failed (keep previous): {}", e.getMessage());
                if (rules.isEmpty()) rules = List.of();
            }
            return rules;
        }
    }

    /** 管理面改规则后立即生效 */
    public void refreshNow() {
        synchronized (this) {
            loadedAt = 0L;
        }
        activeRules();
    }

    // ---------------- 运行时判定 ----------------

    /**
     * 按命中的规则执行限流。规则优先级：更具体的维度优先（API_KEY > APP > MODEL > DEPT > GLOBAL），
     * 同一维度下取阈值最严格的一条。
     *
     * @return 决策结果，含采取的动作与命中规则
     */
    public Decision check(PipelineContext ctx) {
        if (!enabled()) return Decision.pass();
        List<RoutingRuleEntity> matched = match(ctx);
        if (matched.isEmpty()) return Decision.pass();

        RoutingRuleEntity rule = strictest(matched);
        String key = rule.getRuleId();
        long now = System.currentTimeMillis();

        // 1) 并发数：先原子占位再判断，超限则立刻回滚（check-then-increment 在并发下会放行超额请求）。
        //    占位成功后，后续 QPS / Token 拒绝路径必须归还额度，否则会形成"幽灵占用"泄漏。
        Integer conc = rule.getConcurrency();
        boolean holdingSlot = false;
        if (conc != null && conc > 0) {
            AtomicInteger inFlight = concurrency.computeIfAbsent(key, k -> new AtomicInteger(0));
            if (inFlight.incrementAndGet() > conc) {
                inFlight.decrementAndGet();
                return hit(rule, ctx, Action.REJECT, "并发数达到上限 " + conc);
            }
            holdingSlot = true;
        }

        // 2) 分钟级请求频率（QPS 按每分钟次数配置）
        Integer qpsPerMin = rule.getQpsLimit();
        if (qpsPerMin != null && qpsPerMin > 0) {
            long[] bucket = windowCounter.computeIfAbsent(key, k -> new long[]{0L, 0});
            synchronized (bucket) {
                if (now - bucket[1] > WINDOW_MS) {
                    bucket[0] = 0L;
                    bucket[1] = now;
                }
                if (bucket[0] >= qpsPerMin) {
                    if (holdingSlot) releaseSlot(key);
                    return hit(rule, ctx, actionOf(rule), "分钟请求数达到上限 " + qpsPerMin);
                }
                bucket[0] = bucket[0] + 1;
            }
        }

        // 3) 单请求 Token 上限（输入按已估算的 prompt tokens，输出按 max_tokens 预估）
        Integer inLimit = rule.getInputTokenLimit();
        if (inLimit != null && inLimit > 0 && ctx.getPromptTokens() > inLimit) {
            if (holdingSlot) releaseSlot(key);
            return hit(rule, ctx, Action.REJECT, "单请求输入 Token 超过上限 " + inLimit);
        }
        Integer outLimit = rule.getOutputTokenLimit();
        if (outLimit != null && outLimit > 0) {
            int want = ctx.getRequest() != null && ctx.getRequest().path("max_tokens").isInt()
                    ? ctx.getRequest().path("max_tokens").asInt() : 0;
            if (want > outLimit) {
                // 输出超限不直接拒绝，压到上限继续服务（更贴合业务语义）
                ((com.fasterxml.jackson.databind.node.ObjectNode) ctx.getRequest()).put("max_tokens", outLimit);
            }
        }

        // 占位已在步骤 1 完成，此处只登记规则号，响应结束由 release() 归还
        ctx.setRateLimitRuleId(key);
        return Decision.pass(rule);
    }

    /** 归还未登记到 ctx 的并发占位（请求在 L1 阶段被拒绝时调用） */
    private void releaseSlot(String ruleId) {
        AtomicInteger c = concurrency.get(ruleId);
        if (c != null && c.get() > 0) c.decrementAndGet();
    }

    /** 请求结束后归还并发额度 */
    public void release(PipelineContext ctx) {
        String ruleId = ctx == null ? null : ctx.getRateLimitRuleId();
        if (ruleId == null) return;
        AtomicInteger c = concurrency.get(ruleId);
        if (c != null && c.get() > 0) c.decrementAndGet();
    }

    /** QUEUE 动作：短暂排队后放行，避免直接拒绝造成业务中断 */
    public Mono<Void> queueDelay(int queueMs) {
        int ms = Math.min(Math.max(queueMs, 0), MAX_QUEUE_MS);
        return ms <= 0 ? Mono.empty() : Mono.delay(Duration.ofMillis(ms)).then();
    }

    private Action actionOf(RoutingRuleEntity rule) {
        String a = rule.getOverAction();
        if (a == null) return Action.REJECT;
        return switch (a.trim().toUpperCase()) {
            case "QUEUE" -> Action.QUEUE;
            case "DOWNGRADE" -> Action.DOWNGRADE;
            default -> Action.REJECT;
        };
    }

    private List<RoutingRuleEntity> match(PipelineContext ctx) {
        String appId = ctx.getAppId() == null ? "" : ctx.getAppId();
        String dept = appProfileService == null ? "" : appProfileService.deptOf(appId);
        String apiKey = ctx.getAuthorization() == null ? "" : ctx.getAuthorization();
        String model = ctx.getRequestedModel() == null ? "" : ctx.getRequestedModel();
        List<RoutingRuleEntity> matched = new ArrayList<>();
        for (RoutingRuleEntity r : activeRules()) {
            String type = r.getTargetType() == null ? "GLOBAL" : r.getTargetType().trim().toUpperCase();
            String id = r.getTargetId() == null ? "" : r.getTargetId();
            boolean hitTarget = switch (type) {
                case "DEPT" -> id.equalsIgnoreCase(dept);
                case "APP" -> id.equalsIgnoreCase(appId);
                case "API_KEY" -> !id.isBlank() && apiKey.contains(id);
                case "MODEL" -> id.equalsIgnoreCase(model);
                default -> true; // GLOBAL 或未识别类型视为全局
            };
            if (hitTarget) matched.add(r);
        }
        return matched;
    }

    /** 同一批命中规则中取阈值最严格的一条（QPS 最小者） */
    private RoutingRuleEntity strictest(List<RoutingRuleEntity> list) {
        RoutingRuleEntity best = list.get(0);
        for (RoutingRuleEntity r : list) {
            int a = r.getQpsLimit() == null || r.getQpsLimit() <= 0 ? Integer.MAX_VALUE : r.getQpsLimit();
            int b = best.getQpsLimit() == null || best.getQpsLimit() <= 0 ? Integer.MAX_VALUE : best.getQpsLimit();
            if (a < b) best = r;
        }
        return best;
    }

    private Decision hit(RoutingRuleEntity rule, PipelineContext ctx, Action action, String reason) {
        String target = rule.getTargetType() + ":" + (rule.getTargetId() == null ? "*" : rule.getTargetId());
        ReactiveDbAdapter.monoVoid(() -> hitMapper.insertHit(rule.getRuleId(),
                        rule.getTargetType(), rule.getTargetId(), action.name()))
                .subscribe(null, e -> log.warn("rate limit hit log failed: {}", e.getMessage()));
        log.info("rate limit hit: rule={}, target={}, action={}, reason={}", rule.getRuleId(), target, action, reason);
        return new Decision(action, rule, reason);
    }

    // ---------------- 定时维护 ----------------

    /**
     * 每 5 分钟把命中次数回写到 mas_routing_rule.hits_24h（前端列表"近 24h 命中次数"列取此值）。
     * initialDelay 错开启动窗口：fixedDelay 的首次触发在调度器启动时立即执行，
     * 会与 schema ApplicationRunner 竞速（启动日志里的 "relation does not exist" 噪音即来源于此）。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void syncHits() {
        try {
            for (RoutingRuleEntity r : ruleMapper.selectList(null)) {
                int hits = hitMapper.countHits24h(r.getRuleId());
                hitMapper.syncHits24h(r.getRuleId(), hits);
            }
        } catch (Exception e) {
            log.warn("rate limit hits sync failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void purge() {
        try {
            hitMapper.purgeOld();
        } catch (Exception e) {
            log.warn("rate limit hit purge failed: {}", e.getMessage());
        }
    }

    /** 清空进程内计数窗口（管理面改规则后调用可立即重置） */
    public void resetCounters() {
        windowCounter.clear();
    }

    /** 决策结果 */
    public record Decision(Action action, RoutingRuleEntity rule, String reason) {
        public static Decision pass() {
            return new Decision(Action.PASS, null, "");
        }

        public static Decision pass(RoutingRuleEntity rule) {
            return new Decision(Action.PASS, rule, "");
        }

        public boolean rejected() {
            return action == Action.REJECT;
        }

        public Map<String, Object> toMap() {
            return Map.of("action", action.name(), "reason", reason,
                    "rule_id", rule == null ? "" : String.valueOf(rule.getRuleId()));
        }
    }
}
