package com.fitpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.client.ArkClient;
import com.fitpilot.config.ArkProperties;
import com.fitpilot.model.TrainingPlan;
import com.fitpilot.model.TrainingRequirement;
import com.fitpilot.repo.TrainingPlanRepository;
import com.fitpilot.repo.TrainingRequirementRepository;
import com.fitpilot.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 根据训练需求调用豆包模型生成一周训练计划 */
@Service
public class PlanService {

    private final TrainingRequirementRepository requirementRepo;
    private final TrainingPlanRepository planRepo;
    private final ArkClient ark;
    private final ArkProperties arkProps;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanService(TrainingRequirementRepository requirementRepo,
                       TrainingPlanRepository planRepo,
                       ArkClient ark,
                       ArkProperties arkProps) {
        this.requirementRepo = requirementRepo;
        this.planRepo = planRepo;
        this.ark = ark;
        this.arkProps = arkProps;
    }

    public TrainingPlan generate(Long requirementId) {
        TrainingRequirement req = requirementRepo.findById(requirementId)
                .orElseThrow(() -> new IllegalArgumentException("训练需求不存在: " + requirementId));

        String system = """
                你是一名持有 NSCA-CSCS 认证的资深体能教练。请根据用户的训练需求，制定一份为期一周、可循环执行的训练计划。
                要求：
                1. 只输出一个 JSON 对象，不要输出任何解释文字或 Markdown 代码块标记。
                2. JSON 结构固定为：
                   {"title":"计划名称","summary":"总体思路与进阶逻辑(100字以内)",
                    "days":[{"day":1,"focus":"当日部位/主题","exercises":[
                       {"name":"动作名","sets":4,"reps":"8-10","restSeconds":90,"notes":"动作要点或替代方案"}]}]}
                3. days 数组的数量必须等于用户每周训练天数。
                4. 动作选择必须与用户水平匹配，且只能使用用户可用器械；对伤病/受限部位必须规避并给出替代动作。
                5. reps 为字符串，自重/力竭动作可用 "力竭" 等描述。
                """;

        String user = String.format(
                "训练目标：%s；训练水平：%s；可用器械：%s；伤病或受限：%s；每周训练 %s 天；每次训练 %s 分钟；其他说明：%s",
                nvl(req.getGoal()), nvl(req.getLevel()), nvl(req.getEquipment()),
                nvl(req.getInjuries()), req.getDaysPerWeek(), req.getSessionMinutes(),
                nvl(req.getNotes(), "无"));

        String content = ark.chat(
                List.of(Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                arkProps.getChatModel(), 0.7);

        String json = JsonUtils.stripCodeFences(content);
        JsonNode tree;
        try {
            tree = mapper.readTree(json);
            if (!tree.has("days") || !tree.path("days").isArray() || tree.path("days").isEmpty()) {
                throw new IllegalStateException("模型未返回有效的计划结构");
            }
        } catch (Exception e) {
            throw new IllegalStateException("解析模型返回的计划失败：" + e.getMessage(), e);
        }

        TrainingPlan plan = new TrainingPlan();
        plan.setRequirementId(req.getId());
        plan.setTitle(tree.path("title").asText("AI 训练计划"));
        plan.setSummary(tree.path("summary").asText(""));
        plan.setPlanJson(json);
        plan.setVersion((int) planRepo.countByRequirementId(req.getId()) + 1);
        plan.setActive(true);
        plan.setSource("ai");
        return planRepo.save(plan);
    }

    private String nvl(String s) { return nvl(s, "未填写"); }

    private String nvl(String s, String def) { return (s == null || s.isBlank()) ? def : s; }
}
