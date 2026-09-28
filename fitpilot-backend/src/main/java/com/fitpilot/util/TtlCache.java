package com.fitpilot.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用内存缓存：基于 key + TTL。
 *
 * 用于：
 *   1. VisionService：内容哈希 → 分析结果，避免同一文件被反复调方舟
 *   2. ChatService：可以同样包一层，避免短时间重复问题
 *
 * 特点：
 *   - 线程安全（ConcurrentHashMap）
 *   - 命中即返回过期时间，省一层检查
 *   - 不主动清空，靠惰性删除 + 大小上限（生产可换成 Caffeine）
 */
public class TtlCache<K, V> {

    private static final int MAX_SIZE = 5000;   // 防止内存爆炸（按单实例估）

    private final Map<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final String name;

    public TtlCache(Duration ttl, String name) {
        this.ttl = ttl;
        this.name = name;
    }

    /** 获取；命中且未过期返回 value，否则返回 null */
    public V get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return null;
        if (Instant.now().isAfter(e.expiresAt)) {
            map.remove(key, e);
            return null;
        }
        return e.value;
    }

    /** 写入；超过 MAX_SIZE 时随机丢弃旧条目（简单策略） */
    public void put(K key, V value) {
        if (map.size() >= MAX_SIZE) {
            // 抽样删 10% 旧条目，防止无界增长
            int n = 0;
            for (K k : map.keySet()) {
                if (++n > MAX_SIZE / 10) break;
                map.remove(k);
            }
        }
        map.put(key, new Entry<>(value, Instant.now().plus(ttl)));
    }

    /** 当前缓存大小（监控用） */
    public int size() { return map.size(); }

    public String getName() { return name; }

    private record Entry<V>(V value, Instant expiresAt) {}
}