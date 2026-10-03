package com.fitpilot.repo;

import com.fitpilot.model.DietPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DietPlanRepository extends JpaRepository<DietPlan, Long> {

    /** 当前会话的历史饮食计划（倒序，最多 20 条） */
    List<DietPlan> findTop20BySessionIdOrderByIdDesc(String sessionId);

    /** 同参数输入最近 24 小时内是否生成过（去重兜底，跨重启仍可命中） */
    @Query("select p from DietPlan p where p.inputHash = :hash and p.cached = false and p.createdAt >= :since order by p.id desc")
    List<DietPlan> findByInputHashSince(@Param("hash") String hash, @Param("since") LocalDateTime since);
}
