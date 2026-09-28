package com.fitpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.client.ArkClient;
import com.fitpilot.config.ArkProperties;
import com.fitpilot.model.PoseAnalysis;
import com.fitpilot.repo.PoseAnalysisRepository;
import com.fitpilot.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 上传训练动作照片或视频，调用豆包视觉/多模态模型做姿势纠正分析 */
@Service
public class VisionService {

    private final ArkClient ark;
    private final ArkProperties arkProps;
    private final PoseAnalysisRepository poseRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public VisionService(ArkClient ark, ArkProperties arkProps, PoseAnalysisRepository poseRepo) {
        this.ark = ark;
        this.arkProps = arkProps;
        this.poseRepo = poseRepo;
    }

    public Map<String, Object> analyze(MultipartFile file, String movement) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        boolean isVideo = contentType.startsWith("video/");

        String actionHint = (movement == null || movement.isBlank())
                ? "（照片中的动作请自行识别）"
                : "（动作：" + movement.trim() + "）";

        Map<String, Object> mediaPart;
        if (isVideo) {
            // 视频必须先调方舟 Files API 拿到 file_id，再用 video_url 引用，不能 base64 内联
            String fileId = ark.uploadFile(file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "pose.mp4",
                    contentType);
            mediaPart = Map.of("type", "video_url",
                    "video_url", Map.of("file_id", fileId));
        } else {
            // 图片直接 base64 内联，最简单
            String dataUrl = "data:" + contentType + ";base64,"
                    + Base64.getEncoder().encodeToString(file.getBytes());
            mediaPart = Map.of("type", "image_url",
                    "image_url", Map.of("url", dataUrl));
        }

        String prompt = isVideo
                ? """
                  你是一名专业力量训练教练，擅长动作模式评估。视频中%s的演示者正在做训练动作，请按时间顺序观察其姿势轨迹与重心变化，识别关键帧中的姿势问题。
                  只输出一个 JSON 对象，不要输出其他文字或代码块标记。结构固定为：
                  {"score":0到100的整数,"verdict":"一句话总体评价（可提及观察到的关键问题帧）",
                   "issues":["具体姿势问题1","问题2"],
                   "suggestions":["对应的纠正建议1","建议2"],
                   "safety":"安全提醒（如有腰椎/膝盖等风险必须指出，没有则说无明显风险）"}
                  如果视频不清晰或不是训练动作，score 给 0 并在 verdict 中说明。
                  """.formatted(actionHint)
                : """
                  你是一名专业力量训练教练，擅长动作模式评估。请仔细分析图中训练者的动作姿势%s，只输出一个 JSON 对象，不要输出其他文字或代码块标记。结构固定为：
                  {"score":0到100的整数,"verdict":"一句话总体评价",
                   "issues":["具体姿势问题1","问题2"],
                   "suggestions":["对应的纠正建议1","建议2"],
                   "safety":"安全提醒（如有腰椎/膝盖等风险必须指出，没有则说无明显风险）"}
                  如果照片不清晰或不是训练动作，score 给 0 并在 verdict 中说明。
                  """.formatted(actionHint);

        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", prompt),
                mediaPart);

        String resp = ark.chat(List.of(Map.of("role", "user", "content", content)),
                arkProps.getVisionModel(), 0.3);

        String json = JsonUtils.stripCodeFences(resp);
        Map<String, Object> result;
        try {
            result = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (!result.containsKey("score")) {
                throw new IllegalStateException("模型未返回评分字段");
            }
        } catch (Exception e) {
            throw new IllegalStateException("解析视觉模型返回失败：" + e.getMessage(), e);
        }

        PoseAnalysis pa = new PoseAnalysis();
        pa.setImageName(file.getOriginalFilename());
        pa.setMovement(movement);
        pa.setFeedbackJson(json);
        poseRepo.save(pa);
        return result;
    }
}