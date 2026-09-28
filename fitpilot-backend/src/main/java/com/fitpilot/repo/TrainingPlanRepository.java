package com.fitpilot.repo;

import com.fitpilot.model.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

    List<TrainingPlan> findByRequirementIdOrderByIdDesc(Long requirementId);

    TrainingPlan findFirstByOrderByCreatedAtDesc();

    long countByRequirementId(Long requirementId);
}
