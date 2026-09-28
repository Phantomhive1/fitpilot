package com.fitpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.client.ArkClient;
import com.fitpilot.config.ArkProperties;
import com.fitpilot.model.PoseAnalysis;
import com.fitpilot.repo.PoseAnalysisRepository;
import com.fitpilot.util.HashUtils;
import com.fitpilot.util.JsonUtils;
import com.fitpilot.util.TtlCache;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 姿势分析服务。
 *
 * 三层去重，命中即跳过方舟调用、节省费用：
 *   1. 内存缓存（TtlCache，TTL 1~4 小时）—— 同进程内最快
 *   2. 数据库历史（同哈希最近 24 小时内）—— 跨重启/多实例仍能命中
 *   3. 真正调方舟 → 写库 + 写内存
 *
 * 哈希依据：上传文件 SHA-256（字节级去重）。
 */
@Service
public class VisionService {

    private final ArkClient ark;
    private final ArkProperties arkProps;
    private final PoseAnalysisRepository poseRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 内存缓存：同一文件短时间内复用结果 */
    private final TtlCache<String, Map<String, Object>> cache;

    public VisionService(ArkClient ark, ArkProperties arkProps, PoseAnalysisRepository poseRepo) {
        this.ark = ark;
        this.arkProps = arkProps;
        this.poseRepo = poseRepo;
        this.cache = new TtlCache<>(Duration.ofHours(2), "vision");
    }

    /**
     * @param dedupKey 用来归类的会话/用户 key（用于限流 + 持久化索引）
     */
    public Map<String, Object> analyze(MultipartFile file, String movement, String dedupKey) throws IOException {
        byte[] bytes = file.getBytes();
        String contentHash = HashUtils.sha256Hex(bytes);
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        boolean isVideo = contentType.startsWith("video/");

        // ───── 1) 内存缓存命中 ─────
        String cacheKey = contentHash + "|" + (isVideo ? "v" : "i") + "|" + (movement == null ? "" : movement.trim());
        Map<String, Object> hit = cache.get(cacheKey);
        if (hit != null) {
            Map<String, Object> r = new LinkedHashMap<>(hit);
            r.put("cached", true);
            r.put("cacheSource", "memory");
            saveRecord(file, movement, contentHash, contentType, bytes.length, r);
            return r;
        }

        // ───── 2) 数据库历史命中（最近 24 小时同哈希）─────
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<PoseAnalysis> history = poseRepo.findByContentHashSince(contentHash, since);
        if (!history.isEmpty()) {
            Map<String, Object> r;
            try {
                r = mapper.readValue(history.get(0).getFeedbackJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                r = null;
            }
            if (r != null) {
                cache.put(cacheKey, r);
                Map<String, Object> out = new LinkedHashMap<>(r);
                out.put("cached", true);
                out.put("cacheSource", "database");
                out.put("previousAnalyzedAt", history.get(0).getCreatedAt().toString());
                saveRecord(file, movement, contentHash, contentType, bytes.length, out);
                return out;
            }
        }

        // ───── 3) 真正调方舟 ─────
        Map<String, Object> mediaPart;
        if (isVideo) {
            String fileId = ark.uploadFile(bytes,
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "pose.mp4",
                    contentType);
            mediaPart = Map.of("type", "video_url",
                    "video_url", Map.of("file_id", fileId));
        } else {
            String dataUrl = "data:" + contentType + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
            mediaPart = Map.of("type", "image_url",
                    "image_url", Map.of("url", dataUrl));
        }

        String actionHint = (movement == null || movement.isBlank())
                ? "（照片中的动作请自行识别）"
                : "（动作：" + movement.trim() + "）";

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

        result.put("cached", false);
        result.put("cacheSource", "fresh");
        cache.put(cacheKey, result);
        saveRecord(file, movement, contentHash, contentType, bytes.length, result);
        return result;
    }

    /** 把每次分析记一行（包括缓存命中，便于查日志/审计） */
    private void saveRecord(MultipartFile file, String movement, String hash, String contentType,
                            long size, Map<String, Object> result) {
        try {
            PoseAnalysis pa = new PoseAnalysis();
            pa.setImageName(file.getOriginalFilename());
            pa.setMovement(movement);
            pa.setContentHash(hash);
            pa.setMediaType(contentType.startsWith("video/") ? "video" : "image");
            pa.setFileSize(size);
            // cached 字段写"此次响应是否复用历史结果"
            pa.setCached(Boolean.TRUE.equals(result.get("cached")));
            pa.setFeedbackJson(mapper.writeValueAsString(result));
            poseRepo.save(pa);
        } catch (Exception e) {
            // 记录失败不能影响主流程
            System.out.println("[VisionService] saveRecord failed: " + e.getMessage());
        }
    }
}