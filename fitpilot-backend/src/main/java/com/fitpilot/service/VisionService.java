package com.fitpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.client.ArkClient;
import com.fitpilot.config.ArkProperties;
import com.fitpilot.model.PoseAnalysis;
import com.fitpilot.repo.PoseAnalysisRepository;
import com.fitpilot.util.HashUtils;
import com.fitpilot.util.JsonUtils;
import com.fitpilot.util.PoseGeometry;
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
     * @param strictness 评估尺度：lenient（宽松）/ standard（标准）/ strict（严格），空值按 standard
     * @param landmarks 前端 BlazePose 提取的骨骼关键点 JSON（可空；空 = 纯视觉分析）
     */
    public Map<String, Object> analyze(MultipartFile file, String movement, String dedupKey,
                                       String strictness, String landmarks) throws IOException {
        byte[] bytes = file.getBytes();
        String contentHash = HashUtils.sha256Hex(bytes);
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        boolean isVideo = contentType.startsWith("video/");

        // 归一化严格度（防止乱传值打到 prompt 里）
        String level = switch (strictness == null ? "" : strictness.trim().toLowerCase()) {
            case "lenient", "宽松" -> "lenient";
            case "strict", "严格" -> "strict";
            default -> "standard";
        };

        // 关键点几何（无效返回 null → 纯视觉分析）；world=true 表示携带 3D worldLandmarks
        String poseHint = PoseGeometry.describe(landmarks);
        boolean world = poseHint != null && PoseGeometry.hasWorld(landmarks);

        // ───── 1) 内存缓存命中（key 含严格度：同一文件不同尺度分开缓存；
        //        "|lm"=带 2D 关键点，"|lm3"=带 3D 关键点——保证新链路不被旧缓存挡住）─────
        String cacheKey = contentHash + "|" + (isVideo ? "v" : "i") + "|"
                + (movement == null ? "" : movement.trim()) + "|" + level
                + (poseHint == null ? "" : (world ? "|lm3" : "|lm"));
        Map<String, Object> hit = cache.get(cacheKey);
        if (hit != null) {
            Map<String, Object> r = new LinkedHashMap<>(hit);
            r.put("cached", true);
            r.put("cacheSource", "memory");
            saveRecord(file, movement, contentHash, contentType, bytes.length, r, dedupKey);
            return r;
        }

        // ───── 2) 数据库历史命中（最近 24 小时同哈希 + 同严格度）─────
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<PoseAnalysis> history = poseRepo.findByContentHashSince(contentHash, since);
        Map<String, Object> matched = matchHistory(history, level, world);
        if (matched != null) {
            cache.put(cacheKey, matched);
            Map<String, Object> out = new LinkedHashMap<>(matched);
            out.put("cached", true);
            out.put("cacheSource", "database");
            out.put("previousAnalyzedAt", matchHistoryCreatedAt(history, level, world));
            saveRecord(file, movement, contentHash, contentType, bytes.length, out, dedupKey);
            return out;
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

        // 评估尺度：随 strictness 参数变化
        String scaleHint = switch (level) {
            case "lenient" -> """
                  评估尺度【宽松】：你面对的是普通健身爱好者，不是竞技选手。
                  - 只报告明显影响训练效果或安全的问题（如严重塌腰、膝盖明显内扣、重心明显偏移）。
                  - 小偏差不要作为问题提出，例如：膝盖未与脚尖方向完全对齐（大致对齐即可）、
                    轻微的躯干前倾、握距略宽/略窄、肘部角度的小差异——这些属于正常个体差异。
                  - 评分基调：大体正确的动作应给 80 分以上；issues 通常不超过 2 条。
                  - safety 只在存在真正的受伤风险时才写内容，否则说"无明显风险"。
                  """;
            case "strict" -> """
                  评估尺度【严格】：你面对的是认真训练者，按教练认证考试的标准逐项检查。
                  - 任何可见的偏差都要指出，包括细微的对齐问题、轨迹偏移、稳定性不足。
                  - 评分从严：存在明显技术缺陷时应低于 70 分。
                  """;
            default -> """
                  评估尺度【标准】：按普通教练指导课的标准评估，兼顾安全与效果。
                  """;
        };

        String prompt = isVideo
                ? """
                  你是一名专业力量训练教练，擅长动作模式评估。视频中%s的演示者正在做训练动作，请按时间顺序观察其姿势轨迹与重心变化，识别关键帧中的姿势问题。
                  %s
                  只输出一个 JSON 对象，不要输出其他文字或代码块标记。结构固定为：
                  {"score":0到100的整数,"verdict":"一句话总体评价（可提及观察到的关键问题帧）",
                   "issues":["具体姿势问题1","问题2"],
                   "suggestions":["对应的纠正建议1","建议2"],
                   "safety":"安全提醒（如有腰椎/膝盖等风险必须指出，没有则说无明显风险）"}
                  如果视频不清晰或不是训练动作，score 给 0 并在 verdict 中说明。
                  """.formatted(actionHint, scaleHint)
                : """
                  你是一名专业力量训练教练，擅长动作模式评估。请仔细分析图中训练者的动作姿势%s。
                  %s
                  只输出一个 JSON 对象，不要输出其他文字或代码块标记。结构固定为：
                  {"score":0到100的整数,"verdict":"一句话总体评价",
                   "issues":["具体姿势问题1","问题2"],
                   "suggestions":["对应的纠正建议1","建议2"],
                   "safety":"安全提醒（如有腰椎/膝盖等风险必须指出，没有则说无明显风险）"}
                  如果照片不清晰或不是训练动作，score 给 0 并在 verdict 中说明。
                  """.formatted(actionHint, scaleHint);

        // 关键点几何数据注入：让模型"看图 + 读数"双重判断
        if (poseHint != null) {
            prompt = prompt + """


                另附：浏览器端已用 BlazePose 姿态估计模型（MediaPipe）提取骨骼关键点，
                服务端按解剖学关节角计算出的几何数据如下。
                %s
                使用要求：这份客观数据与你的视觉观察互为印证；两者冲突时以画面为准；
                利用它更精确地量化问题（如膝角、躯干倾角、膝-脚尖偏移），并在 issues 中引用具体数值。
                """.formatted(poseHint);
        }

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

        result.put("strictness", level);
        result.put("lm3", world);
        result.put("cached", false);
        result.put("cacheSource", "fresh");
        cache.put(cacheKey, result);
        saveRecord(file, movement, contentHash, contentType, bytes.length, result, dedupKey);
        return result;
    }

    /**
     * 当前会话的历史记录（倒序，最多 50 条），用于前端「进步轨迹」。
     * 返回精简字段 + 从 feedbackJson 解析出的 score/verdict/issues 等。
     */
    public List<Map<String, Object>> history(String dedupKey) {
        // dedupKey 形如 "sess:xxx" 或 "ip:1.2.3.4"，库里存的是原样的 key
        List<PoseAnalysis> records = poseRepo.findTop50BySessionIdOrderByIdDesc(dedupKey);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (PoseAnalysis pa : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", pa.getId());
            item.put("movement", pa.getMovement());
            item.put("mediaType", pa.getMediaType());
            item.put("createdAt", pa.getCreatedAt().toString());
            item.put("cached", pa.isCached());
            // 解析模型返回
            try {
                Map<String, Object> fb = mapper.readValue(pa.getFeedbackJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                item.put("score", fb.get("score"));
                item.put("verdict", fb.get("verdict"));
                item.put("issues", fb.get("issues"));
                item.put("suggestions", fb.get("suggestions"));
                item.put("safety", fb.get("safety"));
                item.put("strictness", fb.getOrDefault("strictness", "standard"));
            } catch (Exception ignore) {
                // feedback 损坏的记录只展示元信息
            }
            out.add(item);
        }
        return out;
    }

    /** 从数据库历史中挑出与当前严格度 + 关键点链路（2D/3D）都匹配的最近一条结果 */
    private Map<String, Object> matchHistory(List<PoseAnalysis> history, String level, boolean world) {
        for (PoseAnalysis pa : history) {
            try {
                Map<String, Object> r = mapper.readValue(pa.getFeedbackJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String recLevel = String.valueOf(r.get("strictness"));
                // 旧数据没有 strictness 字段 → 视为 standard
                if ("null".equals(recLevel) || recLevel.isBlank()) recLevel = "standard";
                // 旧数据没有 lm3 字段 → 视为 2D 链路
                boolean recWorld = Boolean.TRUE.equals(r.get("lm3"));
                if (recLevel.equals(level) && recWorld == world) {
                    return r;
                }
            } catch (Exception ignore) {
                // 单条损坏跳过
            }
        }
        return null;
    }

    /** 找到匹配严格度 + 链路那条记录的时间（用于前端展示"复用于何时"） */
    private String matchHistoryCreatedAt(List<PoseAnalysis> history, String level, boolean world) {
        for (PoseAnalysis pa : history) {
            try {
                Map<String, Object> r = mapper.readValue(pa.getFeedbackJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String recLevel = String.valueOf(r.get("strictness"));
                if ("null".equals(recLevel) || recLevel.isBlank()) recLevel = "standard";
                boolean recWorld = Boolean.TRUE.equals(r.get("lm3"));
                if (recLevel.equals(level) && recWorld == world) {
                    return pa.getCreatedAt().toString();
                }
            } catch (Exception ignore) { }
        }
        return null;
    }

    /** 把每次分析记一行（包括缓存命中，便于查日志/审计 + 历史记录） */
    private void saveRecord(MultipartFile file, String movement, String hash, String contentType,
                            long size, Map<String, Object> result, String dedupKey) {
        try {
            PoseAnalysis pa = new PoseAnalysis();
            pa.setImageName(file.getOriginalFilename());
            pa.setMovement(movement);
            pa.setContentHash(hash);
            pa.setMediaType(contentType.startsWith("video/") ? "video" : "image");
            pa.setFileSize(size);
            pa.setSessionId(dedupKey);
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