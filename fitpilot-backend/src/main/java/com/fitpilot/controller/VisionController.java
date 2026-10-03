package com.fitpilot.controller;

import com.fitpilot.service.VisionService;
import com.fitpilot.util.SlidingWindowLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 姿势纠正接口。
 *
 * 防护：
 *   - 文件空检查
 *   - 滑动窗口限流（每 session/IP 每 10 分钟最多 N 次真实调用）
 *   - 内容哈希去重（在 service 里）
 *
 * sessionId 来源（按优先级）：
 *   1. 请求头 X-Fitpilot-Session（前端固定一个 UUID，刷新页面不变）
 *   2. 没有则 fallback 用 IP
 *
 * 注意：限制的是"真实方舟调用"，缓存命中不计。
 */
@RestController
@RequestMapping("/api/vision")
public class VisionController {

    private final VisionService visionService;

    /** 限流：10 分钟内最多 5 次（缓存命中不计，所以实际能调用方舟的上限） */
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(5, Duration.ofMinutes(10));

    /** 不同用户的限流桶互相隔离：key 用 sessionId（或 IP） */
    private static final ThreadLocal<Long> LAST_LIMIT_REJECT_TS = new ThreadLocal<>();

    public VisionController(VisionService visionService) {
        this.visionService = visionService;
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "movement", required = false) String movement,
                                       @RequestParam(value = "strictness", required = false, defaultValue = "standard") String strictness,
                                       @RequestHeader(value = "X-Fitpilot-Session", required = false) String sessionHeader,
                                       HttpServletRequest request)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请上传照片");
        }

        // 限流 key：优先用客户端 sessionHeader；缺失则用 IP
        String dedupKey = (sessionHeader != null && !sessionHeader.isBlank())
                ? "sess:" + sessionHeader
                : "ip:" + clientIp(request);

        if (!limiter.tryAcquire(dedupKey)) {
            int used = limiter.currentCount(dedupKey);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "10 分钟内最多 5 次姿势分析（已用 " + used + " 次），请稍后再试。"
                    + " 同一张照片走缓存不计入此限制。");
        }

        return visionService.analyze(file, movement, dedupKey, strictness);
    }

    /** 当前会话的姿势分析历史（进步轨迹），最多 50 条，倒序 */
    @GetMapping("/history")
    public java.util.List<Map<String, Object>> history(
            @RequestHeader(value = "X-Fitpilot-Session", required = false) String sessionHeader,
            HttpServletRequest request) {
        String dedupKey = (sessionHeader != null && !sessionHeader.isBlank())
                ? "sess:" + sessionHeader
                : "ip:" + clientIp(request);
        return visionService.history(dedupKey);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}