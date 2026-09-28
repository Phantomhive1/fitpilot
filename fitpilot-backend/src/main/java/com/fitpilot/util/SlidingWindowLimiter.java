package com.fitpilot.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动窗口限流器：每个 key 在窗口内最多允许 N 次。
 *
 * 用于防护：恶意脚本 / 别的 IP 狂刷接口
 * 场景：
 *   vision.analyze(同一 session) → 10 分钟内最多 5 次
 *   chat.generate  → 同上
 *
 * 内存常驻，进程重启清零；多实例部署需换 Redis。
 */
public class SlidingWindowLimiter {

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.window = window;
    }

    /**
     * 尝试扣一次额度。允许返回 true；超限返回 false。
     */
    public boolean tryAcquire(String key) {
        Deque<Instant> d = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            Instant cutoff = Instant.now().minus(window);
            // 弹掉所有窗口外的旧记录
            while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) {
                d.pollFirst();
            }
            if (d.size() >= maxRequests) return false;
            d.offerLast(Instant.now());
            return true;
        }
    }

    /** 当前 key 在窗口内已用次数（监控用） */
    public int currentCount(String key) {
        Deque<Instant> d = buckets.get(key);
        if (d == null) return 0;
        Instant cutoff = Instant.now().minus(window);
        synchronized (d) {
            int n = 0;
            for (Instant t : d) if (t.isAfter(cutoff)) n++;
            return n;
        }
    }
}