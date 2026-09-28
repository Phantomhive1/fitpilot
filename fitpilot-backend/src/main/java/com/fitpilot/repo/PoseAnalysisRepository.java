package com.fitpilot.repo;

import com.fitpilot.model.PoseAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoseAnalysisRepository extends JpaRepository<PoseAnalysis, Long> {

    List<PoseAnalysis> findTop20ByOrderByCreatedAtDesc();
}
