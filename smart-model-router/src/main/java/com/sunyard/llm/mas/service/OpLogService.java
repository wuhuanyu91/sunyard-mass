package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.OpLogMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作审计留痕服务（公告二-8 审计追溯 + 等保要求）：
 * 所有管理面写操作统一经此落库，替换此前"返回一条留痕即丢弃"的假实现。
 */
@Service
public class OpLogService {

    private static final Logger log = LoggerFactory.getLogger(OpLogService.class);

    private final OpLogMapper opLogMapper;

    public OpLogService(OpLogMapper opLogMapper) {
        this.opLogMapper = opLogMapper;
    }

    /**
     * 记录一条操作留痕（落库；失败仅记日志，不阻断业务）。
     *
     * @return 含 op_id 的留痕对象，供 Controller 直接回显
     */
    public Mono<Map<String, Object>> record(String opModule, String opType, String operator,
                                            String targetId, String detail,
                                            String beforeJson, String afterJson, String clientIp) {
        String opId = "OP-" + System.currentTimeMillis() + "-" +
                Integer.toHexString((opType + targetId).hashCode());
        return ReactiveDbAdapter.mono(() -> {
            try {
                opLogMapper.insert(opId, opType, opModule, operator, targetId, detail,
                        beforeJson, afterJson, "SUCCESS", clientIp);
            } catch (Exception e) {
                log.error("op log persist failed: opType={}, target={}", opType, targetId, e);
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("op_id", opId);
            record.put("op_type", opType);
            record.put("op_module", opModule);
            record.put("operator", operator != null ? operator : "system");
            record.put("target_id", targetId);
            record.put("detail", detail);
            record.put("result", "SUCCESS");
            record.put("created_at", java.time.Instant.now().toString());
            return record;
        }).onErrorResume(e -> {
            log.error("op log error", e);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("op_id", opId);
            record.put("op_type", opType);
            record.put("result", "SUCCESS");
            return Mono.just(record);
        });
    }

    public Mono<Map<String, Object>> record(String opModule, String opType, String operator,
                                            String targetId, String detail) {
        return record(opModule, opType, operator, targetId, detail, null, null, null);
    }

    /** 操作日志查询（分页） */
    public Mono<Map<String, Object>> list(String opModule, String operator, int page, int size) {
        return ReactiveDbAdapter.mono(() -> {
            int limit = size > 0 ? size : 20;
            int offset = Math.max(page - 1, 0) * limit;
            List<Map<String, Object>> rows = opLogMapper.list(opModule, operator, null, limit, offset);
            long total = opLogMapper.count(opModule, operator);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", rows);
            result.put("total", (int) total);
            return result;
        });
    }
}
