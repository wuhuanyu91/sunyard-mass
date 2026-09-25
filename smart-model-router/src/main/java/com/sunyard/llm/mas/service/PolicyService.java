package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.PolicyMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一控制面策略治理服务（需求概览 5.2/六章 —— 银行框图最顶层的治理中枢）：
 * 策略全生命周期：草稿 → 提交审批 → 审批通过/驳回 → 发布 → 回滚，全部落库并留痕；
 * 同时提供"单次请求执行了哪些策略"的执行留痕写入与查询（POC 第 11 问）。
 * <p>
 * 【改造背景】此前前端 control/index.tsx 608 行策略中心为纯内存操作，后端零代码。
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);
    private static final String MODULE = "policy";

    private final PolicyMapper policyMapper;
    private final OpLogService opLogService;

    public PolicyService(PolicyMapper policyMapper, OpLogService opLogService) {
        this.policyMapper = policyMapper;
        this.opLogService = opLogService;
    }

    // ---------------- 策略 ----------------

    public Mono<List<Map<String, Object>>> listPolicies(String category, String status) {
        return ReactiveDbAdapter.mono(() -> policyMapper.listPolicies(category, status));
    }

    public Mono<Map<String, Object>> createPolicy(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String policyId = str(body.get("policyId"));
            if (policyId.isEmpty()) throw new IllegalArgumentException("policyId 必填");
            policyMapper.insertPolicy(policyId, str(body.getOrDefault("name", policyId)),
                    str(body.getOrDefault("category", "ROUTING")), str(body.get("scope")),
                    operator != null ? operator : "admin");
            // 初始草稿版本
            policyMapper.insertVersion(policyId, 1, str(body.getOrDefault("contentJson", "{}")), operator);
            policyMapper.updatePolicy(policyId, null, null, null, "DRAFT", 1);
            return policyId;
        }).flatMap(id -> opLogService.record(MODULE, "创建策略", operator, id, "新建策略并生成 v1 草稿"));
    }

    /** 提交审批（DRAFT → PENDING） */
    public Mono<Map<String, Object>> submit(String policyId, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int version = nextVersion(policyId);
            policyMapper.insertVersion(policyId, version, str(body.getOrDefault("contentJson", "{}")), operator);
            policyMapper.updateVersionStatus(policyId, version, "PENDING", null, null);
            policyMapper.updatePolicy(policyId, null, null, null, "PENDING", null);
            return policyId + "#v" + version;
        }).flatMap(t -> opLogService.record(MODULE, "提交审批", operator, policyId, "提交版本 " + t));
    }

    /** 审批（PENDING → PUBLISHED / 驳回回 DRAFT） */
    public Mono<Map<String, Object>> approve(String policyId, int version, boolean approved,
                                             String comment, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            if (approved) {
                policyMapper.updateVersionStatus(policyId, version, "PUBLISHED", operator, comment);
                policyMapper.updatePolicy(policyId, null, null, null, "PUBLISHED", version);
            } else {
                policyMapper.updateVersionStatus(policyId, version, "DRAFT", operator, comment);
                policyMapper.updatePolicy(policyId, null, null, null, "DRAFT", null);
            }
            log.info("policy {} v{} approved={} by {}", policyId, version, approved, operator);
            return policyId + "#v" + version;
        }).flatMap(t -> opLogService.record(MODULE, approved ? "审批通过" : "审批驳回", operator, policyId,
                t + (comment == null ? "" : "，意见：" + comment)));
    }

    /** 回滚：将当前版本标记 ROLLED_BACK，策略回退到上一已发布版本 */
    public Mono<Map<String, Object>> rollback(String policyId, int version, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            policyMapper.updateVersionStatus(policyId, version, "ROLLED_BACK", operator, "回滚");
            List<Map<String, Object>> versions = policyMapper.listVersions(policyId);
            Integer fallback = null;
            for (Map<String, Object> v : versions) {
                if ("PUBLISHED".equals(String.valueOf(v.get("status")))) {
                    fallback = ((Number) v.get("version")).intValue();
                    break;
                }
            }
            policyMapper.updatePolicy(policyId, null, null, null,
                    fallback == null ? "DRAFT" : "PUBLISHED", fallback == null ? 0 : fallback);
            return policyId + "#rollback v" + version + " -> v" + fallback;
        }).flatMap(t -> opLogService.record(MODULE, "策略回滚", operator, policyId, t));
    }

    public Mono<List<Map<String, Object>>> listVersions(String policyId) {
        return ReactiveDbAdapter.mono(() -> policyMapper.listVersions(policyId));
    }

    /** 审批最新待审版本（前端 control/index.tsx 调 approvePolicy(policyId, approve, opinion)，不传 version） */
    public Mono<Map<String, Object>> approveLatest(String policyId, boolean approved, String comment, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> versions = policyMapper.listVersions(policyId);
            Integer target = null;
            for (Map<String, Object> v : versions) {
                if ("PENDING".equals(String.valueOf(v.get("status")))) {
                    target = ((Number) v.get("version")).intValue();
                    break;
                }
            }
            if (target == null) target = policyMapper.maxVersion(policyId);
            return target == null ? 0 : target;
        }).flatMap(ver -> (ver == null || ver == 0)
                ? Mono.error(new IllegalArgumentException("策略 " + policyId + " 无可用版本"))
                : approve(policyId, ver, approved, comment, operator));
    }

    /** 回滚最新版本（前端 control/index.tsx 调 rollbackPolicy(policyId)，不传 version） */
    public Mono<Map<String, Object>> rollbackLatest(String policyId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int latest = policyMapper.maxVersion(policyId);
            return latest;
        }).flatMap(latest -> (latest <= 1)
                ? Mono.error(new IllegalArgumentException("策略 " + policyId + " 无可回滚版本"))
                : rollback(policyId, latest, operator));
    }

    // ---------------- 执行留痕 ----------------

    /** 记录一次请求命中了哪些策略（供 POC 第 11 问追溯） */
    public void logExecution(String traceId, String policyId, int version, String stage,
                             String decision, String detail) {
        if (traceId == null || traceId.isEmpty()) return;
        try {
            policyMapper.insertExecLog(traceId, policyId, version, stage, decision, detail);
        } catch (Exception e) {
            log.warn("policy exec log write failed: {}", e.getMessage());
        }
    }

    public Mono<List<Map<String, Object>>> listExecLogs(String traceId) {
        return ReactiveDbAdapter.mono(() -> policyMapper.listExecLogs(traceId));
    }

    // ---------------- helpers ----------------

    private int nextVersion(String policyId) {
        return policyMapper.maxVersion(policyId) + 1;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
