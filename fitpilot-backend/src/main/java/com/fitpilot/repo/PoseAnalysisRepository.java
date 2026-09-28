package com.fitpilot.repo;

import com.fitpilot.model.PoseAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PoseAnalysisRepository extends JpaRepository<PoseAnalysis, Long> {

    List<PoseAnalysis> findTop20ByOrderByCreatedAtDesc();

    /**
     * 按内容哈希查询最近一条分析记录，用于跨重启的持久化去重。
     * 同字节文件 SHA-256 相同，哈希命中即可复用结果。
     */
    @Query("SELECT p FROM PoseAnalysis p WHERE p.contentHash = :hash AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<PoseAnalysis> findByContentHashSince(@Param("hash") String hash, @Param("since") LocalDateTime since);
}