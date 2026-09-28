package com.fitpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.client.ArkClient;
import com.fitpilot.config.ArkProperties;
import com.fitpilot.model.ChatMessage;
import com.fitpilot.model.TrainingPlan;
import com.fitpilot.repo.ChatMessageRepository;
import com.fitpilot.repo.TrainingPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实时教练：SSE 流式聊天，且当用户要求修改计划时，
 * 模型在回复末尾输出 ```plan {完整新计划 JSON}``` 代码块，
 * 服务端解析校验后保存为新版本计划并推送 plan_updated 事件。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Pattern PLAN_BLOCK =
            Pattern.compile("```plan\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ChatMessageRepository chatRepo;
    private final TrainingPlanRepository planRepo;
    private final ArkClient ark;
    private final ArkProperties arkProps;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatService(ChatMessageRepository chatRepo,
                       TrainingPlanRepository planRepo,
                       ArkClient ark,
                       ArkProperties arkProps) {
        this.chatRepo = chatRepo;
        this.planRepo = planRepo;
        this.ark = ark;
        this.arkProps = arkProps;
    }

    public void stream(String sessionId, String message, SseEmitter emitter) {
        executor.submit(() -> {
            try {
                chatRepo.save(new ChatMessage(sessionId, "user", message));

                TrainingPlan current = planRepo.findFirstByOrderByCreatedAtDesc();
                List<ChatMessage> history = chatRepo.findTop40BySessionIdOrderByCreatedAtAsc(sessionId);

                List<Map<String, Object>> msgs = new ArrayList<>();
                String planCtx = current == null
                        ? "（用户暂时没有训练计划）"
                        : current.getPlanJson();
                msgs.add(Map.of("role", "system", "content", """
                        你是 FitPilot 的 AI 私人教练，专业、耐心、说话简洁接地气。
                        当前用户的训练计划（JSON）：
                        %s
                        回答规则：
                        1. 训练知识、动作讲解、饮食恢复等问题正常回答，简洁分点。
                        2. 当用户要求修改训练计划时：先用一两句话说明你的调整思路，然后在回复的最后单独输出：
                        ```plan
                        {完整的更新后的计划 JSON，结构与当前计划一致，包含 title/summary/days}
                        ```
                        该代码块会被系统自动保存为新版本计划，因此必须是完整、可直接替换的 JSON。
                        3. 用户没有要求改计划时，绝对不要输出 plan 代码块。
                        """.formatted(planCtx)));
                for (ChatMessage h : history) {
                    if (h.getContent() != null && !h.getContent().isBlank()) {
                        msgs.add(Map.of("role", h.getRole(), "content", h.getContent()));
                    }
                }

                StringBuilder full = new StringBuilder();
                ark.chatStream(msgs, arkProps.getChatModel(), 0.6, token -> {
                    full.append(token);
                    trySend(emitter, Map.of("type", "token", "content", token));
                });

                String content = full.toString();
                // 尝试提取并保存新计划
                Matcher m = PLAN_BLOCK.matcher(content);
                if (m.find()) {
                    String planJson = m.group(1).trim();
                    try {
                        JsonNode tree = mapper.readTree(planJson);
                        if (tree.has("days") && tree.path("days").isArray()) {
                            TrainingPlan updated = new TrainingPlan();
                            updated.setRequirementId(current != null ? current.getRequirementId() : null);
                            updated.setTitle(tree.path("title").asText(
                                    current != null ? current.getTitle() : "AI 训练计划"));
                            updated.setSummary(tree.path("summary").asText(""));
                            updated.setPlanJson(planJson);
                            updated.setVersion(current != null && current.getRequirementId() != null
                                    ? (int) planRepo.countByRequirementId(current.getRequirementId()) + 1 : 1);
                            updated.setActive(true);
                            updated.setSource("chat");
                            TrainingPlan saved = planRepo.save(updated);
                            trySend(emitter, Map.of("type", "plan_updated", "planId", saved.getId(),
                                    "version", saved.getVersion()));
                            log.info("聊天已生成新计划版本 planId={} v{}", saved.getId(), saved.getVersion());
                        }
                    } catch (Exception e) {
                        log.warn("plan 代码块解析失败，忽略：{}", e.getMessage());
                        trySend(emitter, Map.of("type", "plan_parse_error"));
                    }
                }

                // 存库时去掉 plan 代码块，避免污染后续上下文
                String toSave = PLAN_BLOCK.matcher(content).replaceAll("");
                chatRepo.save(new ChatMessage(sessionId, "assistant", toSave.trim()));

                trySend(emitter, Map.of("type", "done"));
                emitter.complete();
            } catch (Exception e) {
                log.error("聊天处理失败", e);
                trySend(emitter, Map.of("type", "error", "message", String.valueOf(e.getMessage())));
                emitter.completeWithError(e);
            }
        });
    }

    private void trySend(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception ignored) {
            // 客户端断开等场景，忽略
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
