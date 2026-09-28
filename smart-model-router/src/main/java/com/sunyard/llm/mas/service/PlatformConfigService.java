package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunyard.llm.mas.entity.PlatformConfigEntity;
import com.sunyard.llm.mas.mapper.PlatformConfigMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台配置服务：系统参数 / 安全基线 / 成本预警 的真实落库读写。
 * <p>
 * 【改造背景】这三组配置此前只存在于前端内存对象里 —— 页面点"保存"成功、刷新即回原值，
 * 属于讲解红线中"管理面保存按钮回退"的一部分。现统一落到 mas_platform_config，
 * 未配置过则回落到调用方传入的默认值，保证页面永远有东西可展示。
 */
@Service
public class PlatformConfigService {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigService.class);
    private static final String MODULE = "system";

    private final PlatformConfigMapper configMapper;
    private final OpLogService opLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlatformConfigService(PlatformConfigMapper configMapper, OpLogService opLogService) {
        this.configMapper = configMapper;
        this.opLogService = opLogService;
    }

    /** 读取配置；库中无记录时原样返回默认值 */
    public Mono<Map<String, Object>> get(String key, Map<String, Object> defaults) {
        return ReactiveDbAdapter.mono(() -> {
            PlatformConfigEntity e = configMapper.selectByKey(key);
            if (e == null || e.getConfigJson() == null || e.getConfigJson().isBlank()) {
                return new LinkedHashMap<>(defaults);
            }
            Map<String, Object> stored = objectMapper.readValue(e.getConfigJson(), Map.class);
            Map<String, Object> merged = new LinkedHashMap<>(defaults);
            merged.putAll(stored);
            return merged;
        }).onErrorResume(ex -> {
            log.warn("platform config read degraded ({}): {}", key, ex.getMessage());
            return Mono.just(new LinkedHashMap<>(defaults));
        });
    }

    /** 保存配置（UPSERT）+ 操作留痕 */
    public Mono<Map<String, Object>> save(String key, String title, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
            configMapper.upsert(key, json, operator);
            return json;
        }).flatMap(json -> opLogService.record(MODULE, "保存" + title, operator, key, json.length() > 200 ? json.substring(0, 200) + "…" : json));
    }

    /** 通用配置读取：返回原始 JSON（对象或数组），库中无记录时返回空数组（保证前端列表不空指针） */
    public Mono<Object> getJson(String key) {
        return ReactiveDbAdapter.mono(() -> {
            PlatformConfigEntity e = configMapper.selectByKey(key);
            if (e == null || e.getConfigJson() == null || e.getConfigJson().isBlank()) {
                return (Object) new java.util.ArrayList<>();
            }
            return (Object) objectMapper.readValue(e.getConfigJson(), Object.class);
        }).onErrorResume(ex -> {
            log.warn("platform config read degraded ({}): {}", key, ex.getMessage());
            return Mono.just(new java.util.ArrayList<>());
        });
    }

    /** 通用配置保存（UPSERT 任意 JSON：对象或数组）+ 操作留痕 */
    public Mono<Map<String, Object>> saveJson(String key, String label, Object body, String operator) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("配置序列化失败: " + e.getMessage()));
        }
        final String finalJson = json;
        return ReactiveDbAdapter.monoVoid(() -> configMapper.upsert(key, finalJson, operator))
                .flatMap(v -> opLogService.record(MODULE, "保存" + label, operator, key, key))
                .thenReturn(Map.<String, Object>of("key", key, "ok", true));
    }

    /** 向数组型配置（如 TICKETS 工单）追加一条记录 + 操作留痕；配置不存在时新建数组 */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> appendArrayItem(String key, Object item, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            PlatformConfigEntity e = configMapper.selectByKey(key);
            List<Object> list = new java.util.ArrayList<>();
            if (e != null && e.getConfigJson() != null && !e.getConfigJson().isBlank()) {
                Object parsed = objectMapper.readValue(e.getConfigJson(), Object.class);
                if (parsed instanceof List) {
                    list = (List<Object>) parsed;
                }
            }
            list.add(item);
            String json = objectMapper.writeValueAsString(list);
            configMapper.upsert(key, json, operator);
            return (Object) list;
        }).flatMap(list -> opLogService.record(MODULE, "追加" + key, operator, key, key))
          .thenReturn(Map.<String, Object>of("key", key, "ok", true));
    }
}
