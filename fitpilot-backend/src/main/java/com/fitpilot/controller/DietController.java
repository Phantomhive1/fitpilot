package com.fitpilot.controller;

import com.fitpilot.service.DietService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 饮食计划接口。
 *
 * sessionId 来源（与姿势分析一致）：
 *   1. 请求头 X-Fitpilot-Session（前端固定 UUID）
 *   2. 缺失则 fallback 用 IP
 *
 * 限流/去重在 DietService 内完成（缓存命中不计限流次数）。
 */
@RestController
@RequestMapping("/api/diet")
public class DietController {

    private final DietService dietService;

    public DietController(DietService dietService) {
        this.dietService = dietService;
    }

    /**
     * 生成饮食计划。
     * body: mode=lazy|pro + 各模式参数（详见 DietService 注释）
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "X-Fitpilot-Session", required = false) String sessionHeader,
                                        HttpServletRequest request) {
        return dietService.generate(body, dedupKey(sessionHeader, request));
    }

    /** 当前会话的饮食计划历史（倒序，最多 20 条） */
    @GetMapping("/history")
    public List<Map<String, Object>> history(
            @RequestHeader(value = "X-Fitpilot-Session", required = false) String sessionHeader,
            HttpServletRequest request) {
        return dietService.history(dedupKey(sessionHeader, request));
    }

    /** 单条计划详情 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id,
                                      @RequestHeader(value = "X-Fitpilot-Session", required = false) String sessionHeader,
                                      HttpServletRequest request) {
        return dietService.detail(id, dedupKey(sessionHeader, request));
    }

    private String dedupKey(String sessionHeader, HttpServletRequest request) {
        return (sessionHeader != null && !sessionHeader.isBlank())
                ? "sess:" + sessionHeader
                : "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
