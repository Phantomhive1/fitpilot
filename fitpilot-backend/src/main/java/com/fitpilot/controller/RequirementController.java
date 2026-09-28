package com.fitpilot.controller;

import com.fitpilot.model.TrainingPlan;
import com.fitpilot.model.TrainingRequirement;
import com.fitpilot.repo.TrainingRequirementRepository;
import com.fitpilot.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 训练需求：填写 & 生成计划 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    private final TrainingRequirementRepository requirementRepo;
    private final PlanService planService;

    public RequirementController(TrainingRequirementRepository requirementRepo, PlanService planService) {
        this.requirementRepo = requirementRepo;
        this.planService = planService;
    }

    @PostMapping
    public TrainingRequirement create(@Valid @RequestBody TrainingRequirement req) {
        if (req.getGoal() == null || req.getGoal().isBlank()) {
            throw new IllegalArgumentException("训练目标不能为空");
        }
        if (req.getLevel() == null || req.getLevel().isBlank()) {
            throw new IllegalArgumentException("训练水平不能为空");
        }
        if (req.getDaysPerWeek() == null || req.getDaysPerWeek() < 1 || req.getDaysPerWeek() > 7) {
            req.setDaysPerWeek(3);
        }
        if (req.getSessionMinutes() == null || req.getSessionMinutes() < 20) {
            req.setSessionMinutes(60);
        }
        return requirementRepo.save(req);
    }

    @PostMapping("/{id}/plan")
    public TrainingPlan generatePlan(@PathVariable Long id) {
        return planService.generate(id);
    }

    @GetMapping("/{id}")
    public TrainingRequirement get(@PathVariable Long id) {
        return requirementRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("训练需求不存在: " + id));
    }
}
