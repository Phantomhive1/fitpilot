package com.fitpilot.repo;

import com.fitpilot.model.TrainingRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingRequirementRepository extends JpaRepository<TrainingRequirement, Long> {
}
