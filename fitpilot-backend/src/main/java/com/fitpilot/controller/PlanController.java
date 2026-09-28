package com.fitpilot.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.model.TrainingPlan;
import com.fitpilot.repo.TrainingPlanRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 计划仪表盘：计划列表与按天明细 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final TrainingPlanRepository planRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanController(TrainingPlanRepository planRepo) {
        this.planRepo = planRepo;
    }

    /** 计划列表（可按需求过滤），最新在前 */
    @GetMapping
    public List<TrainingPlan> list(@RequestParam(required = false) Long requirementId) {
        List<TrainingPlan> plans = (requirementId == null)
                ? planRepo.findAll()
                : planRepo.findByRequirementIdOrderByIdDesc(requirementId);
        plans.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return plans;
    }

    /** 计划明细：解析 planJson，返回 title/summary/days(含动作/组数/次数) 等结构 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) throws Exception {
        TrainingPlan plan = planRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("计划不存在: " + id));
        Map<String, Object> result = mapper.readValue(plan.getPlanJson(),
                new TypeReference<LinkedHashMap<String, Object>>() {});
        result.put("id", plan.getId());
        result.put("requirementId", plan.getRequirementId());
        result.put("version", plan.getVersion());
        result.put("source", plan.getSource());
        result.put("createdAt", plan.getCreatedAt() == null ? null : plan.getCreatedAt().toString());
        return result;
    }
}
