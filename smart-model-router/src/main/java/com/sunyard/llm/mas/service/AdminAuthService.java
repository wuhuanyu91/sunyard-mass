package com.sunyard.llm.mas.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.mapper.AdminTokenMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端点认证（此前 /internal/* 全部裸奔 —— 无任何认证即可操作所有管理接口）：
 * 令牌签发 / 校验 / 吊销，校验结果带 60s 缓存。
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminTokenMapper adminTokenMapper;
    private final OpLogService opLogService;

    private final Cache<String, String> tokenCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(5000).build();

    public AdminAuthService(AdminTokenMapper adminTokenMapper, OpLogService opLogService) {
        this.adminTokenMapper = adminTokenMapper;
        this.opLogService = opLogService;
    }

    /** 签发管理令牌（明文仅返回一次，库里只存 SHA-256） */
    public Mono<Map<String, Object>> issue(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String userCode = String.valueOf(body.getOrDefault("userCode", "admin"));
            String roleCode = String.valueOf(body.getOrDefault("roleCode", "ADMIN"));
            int days = body.get("days") == null ? 90 : Integer.parseInt(String.valueOf(body.get("days")));
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = "mat-" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            adminTokenMapper.insert(sha256(token), userCode, roleCode, LocalDateTime.now().plusDays(days));
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("token", token);
            res.put("user_code", userCode);
            res.put("role_code", roleCode);
            res.put("expire_at", LocalDateTime.now().plusDays(days).toString());
            log.info("admin token issued for {}", userCode);
            return res;
        }).flatMap(res -> opLogService.record("system", "签发管理令牌", operator,
                String.valueOf(res.get("user_code")), "签发管理端点访问令牌")
                // 必须回传签发结果：明文令牌仅在本次响应出现一次，若被留痕回执替换，
                // 调用方将永远拿不到令牌，等价于无法签发任何管理令牌
                .thenReturn(res));
    }

    /** 校验令牌，返回 user_code；无效返回 null（fail-closed） */
    public String resolve(String token) {
        if (token == null || token.isEmpty()) return null;
        return tokenCache.get(token, t -> {
            try {
                adminTokenMapper.expireOutdated();
                Map<String, Object> row = adminTokenMapper.selectByHash(sha256(t));
                if (row == null) return "";
                Object expireAt = row.get("expire_at");
                if (expireAt instanceof LocalDateTime exp && exp.isBefore(LocalDateTime.now())) return "";
                return String.valueOf(row.get("user_code"));
            } catch (Exception e) {
                log.error("admin token resolve failed", e);
                return "";
            }
        });
    }

    public Mono<Map<String, Object>> revoke(String token, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            adminTokenMapper.revoke(sha256(token));
            tokenCache.invalidate(token);
            return "revoked";
        }).flatMap(r -> opLogService.record("system", "吊销管理令牌", operator, null, "吊销管理端点令牌"));
    }

    public Mono<List<Map<String, Object>>> list() {
        return ReactiveDbAdapter.mono(adminTokenMapper::list);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
