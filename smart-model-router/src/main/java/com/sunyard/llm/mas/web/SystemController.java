package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.OpLogService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 系统治理端点：操作审计日志（此前 15 个写端点的 opRecord 用完即丢，现统一落库可查）
 */
@RestController
public class SystemController {

    private final OpLogService opLogService;

    public SystemController(OpLogService opLogService) {
        this.opLogService = opLogService;
    }

    @GetMapping("/internal/system/op-logs")
    public Mono<Map<String, Object>> listOpLogs(
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "operator", required = false) String operator,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return opLogService.list(module, operator, page, size);
    }
}
