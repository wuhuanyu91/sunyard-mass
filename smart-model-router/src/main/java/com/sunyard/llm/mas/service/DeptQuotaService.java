package com.sunyard.llm.mas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.entity.CallLogEntity;
import com.sunyard.llm.mas.entity.DeptQuotaEntity;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.mapper.DeptQuotaMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 部门/租户级配额运行时执行（公告一-5 差异化计量规则 + 二-2 租户隔离）：
 * <p>
 * 【改造背景】此前 mas_dept_quota 仅用于展示，over_limit_stop 字段全项目没有任何运行时决策点消费，
 * 真正执行的配额是 user 级的 mas_token_quota，与"按部门/租户差异化计量"口径对不上。
 * 本服务在 L4 执行管控阶段接入，使部门配额与租户配额真正生效。
 * <p>
 * fail-closed 策略由 {@code mas.governance.fail-closed} 控制（默认 false，与既有限流/配额保持一致；银行生产建议置 true）。
 */
@Service
public class DeptQuotaService {

    private static final Logger log = LoggerFactory.getLogger(DeptQuotaService.class);
    private static final String UNKNOWN = "UNKNOWN";

    private final DeptQuotaMapper deptQuotaMapper;
    private final CallLogMapper callLogMapper;
    private final TenantService tenantService;
    private final MasProperties props;

    public DeptQuotaService(DeptQuotaMapper deptQuotaMapper, CallLogMapper callLogMapper,
                            TenantService tenantService, MasProperties props) {
        this.deptQuotaMapper = deptQuotaMapper;
        this.callLogMapper = callLogMapper;
        this.tenantService = tenantService;
        this.props = props;
    }

    /**
     * 配额闸口：在 L4 预扣前执行。
     *
     * @param appId          应用 ID（用于解析租户）
     * @param reservedTokens 本次请求预扣 token 数
     */
    public Mono<Void> check(String appId, int reservedTokens) {
        if (props.getGovernance() == null || !props.getGovernance().isDeptQuotaEnabled()) {
            return Mono.empty();
        }
        // 全部阻塞操作（含租户解析缓存 miss 时的查库）统一调度到 DB 线程池，
        // 与 QuotaService 的 ReactiveDbAdapter.mono 语义一致，避免阻塞 Netty 事件循环
        return ReactiveDbAdapter.mono(() -> doCheck(appId, reservedTokens))
                .onErrorResume(e -> {
                    boolean failClosed = props.getGovernance() != null && props.getGovernance().isFailClosed();
                    if (e instanceof MasException me) {
                        return Mono.error(me);
                    }
                    log.error("quota check error, app={}, failClosed={}", appId, failClosed, e);
                    return failClosed ? Mono.error(MasException.quotaExceeded()) : Mono.empty();
                })
                .then();
    }

    /** 阻塞检查逻辑（由 ReactiveDbAdapter.mono 调度到 DB 线程池执行） */
    private Void doCheck(String appId, int reservedTokens) {
        String tenantId = tenantService.resolveTenantByApp(appId);
        if (tenantId == null || tenantId.isEmpty() || UNKNOWN.equals(tenantId)) {
            return null;   // 无租户归属的记录不纳入部门/租户配额
        }
        // 1) 租户停用 → 立即拒绝（二-2：停用即收回模型与数据权限）
        if (!tenantService.isTenantActive(tenantId)) {
            log.warn("tenant inactive, reject request: {}", tenantId);
            throw MasException.blacklisted(tenantId);
        }

        // 2) 租户级月度配额
        Long tenantQuota = tenantService.tenantQuota(tenantId);
        long used = usedTokensOfTenant(tenantId);   // 单次聚合，供租户级与部门级共同使用
        if (tenantQuota != null && tenantQuota > 0) {
            if (used + reservedTokens > tenantQuota) {
                log.warn("tenant quota exceeded: tenant={}, used={}, quota={}", tenantId, used, tenantQuota);
                throw MasException.quotaExceeded();
            }
        }

        // 3) 部门级月度配额 + over_limit_stop（此前仅展示，现真正生效）
        List<DeptQuotaEntity> quotas = deptQuotaMapper.selectList(null);
        for (DeptQuotaEntity q : quotas) {
            if (q.getDeptId() == null) continue;
            String deptTenant = tenantService.resolveTenantByDept(q.getDeptId());
            if (!tenantId.equals(deptTenant)) continue;
            long limit = q.getMonthTokenQuota() == null ? 0L : q.getMonthTokenQuota();
            if (limit <= 0) continue;
            if (used + reservedTokens > limit) {
                boolean stop = Boolean.TRUE.equals(q.getOverLimitStop());
                if (stop) {
                    log.warn("dept quota exceeded with over_limit_stop: dept={}, used={}, limit={}",
                            q.getDeptId(), used, limit);
                    throw MasException.quotaExceeded();
                }
                // 未开启"超限即停"：仅告警，放行并留痕
                log.warn("dept quota warning (not stopped): dept={}, used={}, limit={}",
                        q.getDeptId(), used, limit);
            }
        }
        return null;
    }

    /** 租户本月已用 token（实时聚合 mas_call_log） */
    private long usedTokensOfTenant(String tenantId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Map<String, Object>> rows = callLogMapper.selectMaps(
                new QueryWrapper<CallLogEntity>()
                        .select("COALESCE(SUM(total_tokens),0) as used")
                        .eq("tenant_id", tenantId)
                        .ge("created_at", monthStart)
        );
        if (rows.isEmpty()) return 0L;
        Object v = rows.get(0).get("used");
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}
