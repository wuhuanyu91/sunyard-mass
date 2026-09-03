package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.AppService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 应用管理端点（§1.5 应用身份统一管控）：
 * <ul>
 *   <li>GET /internal/apps — 应用列表</li>
 *   <li>GET /internal/apps/{app_id} — 应用详情</li>
 *   <li>POST /internal/apps — 创建应用（自动生成 app_id + API Key）</li>
 *   <li>PUT /internal/apps/{app_id} — 更新应用</li>
 *   <li>POST /internal/apps/{app_id}/toggle — 启用/停用</li>
 *   <li>DELETE /internal/apps/{app_id} — 删除应用</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/apps")
public class AppController {

    private final AppService appService;

    public AppController(AppService appService) {
        this.appService = appService;
    }

    /** 应用列表 */
    @GetMapping
    public Mono<List<Map<String, Object>>> listApps(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dept_id) {
        return appService.listApps(status, dept_id);
    }

    /** 应用详情 */
    @GetMapping("/{app_id}")
    public Mono<Map<String, Object>> getApp(@PathVariable("app_id") String app_id) {
        return appService.getApp(app_id);
    }

    /** 创建应用 */
    @PostMapping
    public Mono<Map<String, Object>> createApp(@RequestBody Map<String, Object> body) {
        return appService.createApp(body);
    }

    /** 更新应用 */
    @PutMapping("/{app_id}")
    public Mono<Map<String, Object>> updateApp(
            @PathVariable("app_id") String app_id,
            @RequestBody Map<String, Object> body) {
        return appService.updateApp(app_id, body);
    }

    /** 启用/停用应用 */
    @PostMapping("/{app_id}/toggle")
    public Mono<Map<String, Object>> toggleApp(@PathVariable("app_id") String app_id) {
        return appService.toggleApp(app_id);
    }

    /** 删除应用 */
    @DeleteMapping("/{app_id}")
    public Mono<Map<String, Object>> deleteApp(@PathVariable("app_id") String app_id) {
        return appService.deleteApp(app_id);
    }
}
