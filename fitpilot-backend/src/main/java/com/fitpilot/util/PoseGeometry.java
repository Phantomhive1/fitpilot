package com.fitpilot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * 服务端基于 BlazePose 关键点计算解剖学关节角（2D + 3D）。
 *
 * 架构分工：
 *   - 前端（MediaPipe tasks-vision）在浏览器完成姿态推理，输出两套数据：
 *       landmarks       33 个归一化 2D 关键点 [x, y, visibility]（画骨架/2D 角度）
 *       worldLandmarks  33 个米制 3D 关键点 [x, y, z]（髋部为原点，单目推断深度）
 *   - 后端在这里把关键点换算成教练语言：肘/肩/髋/膝关节角、躯干倾角、膝-脚尖偏移，
 *     有 3D 数据时补算屈膝深度方向的指标（正面视频也能估关节角）
 *   - 计算结果注入多模态模型的 prompt，与画面互相印证，提高评估精度
 *
 * 这样设计的原因：推理放客户端不占服务器算力；几何计算是纯 CPU 纯函数，
 * 放服务端便于以后换前端模型 / 复用（App、小程序）而不用重写逻辑。
 *
 * 3D 数据的定位：BlazePose 的 worldLandmarks 是"2.5D"——深度是模型推断的，
 * 不是测量值，精度低于 x/y。因此 3D 指标统一标注"估算"，并要求 AI 优先以画面为准。
 */
public final class PoseGeometry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* BlazePose 33 关键点索引（健身评估用的子集） */
    private static final int L_SHO = 11, R_SHO = 12, L_ELB = 13, R_ELB = 14,
            L_WRI = 15, R_WRI = 16, L_HIP = 23, R_HIP = 24,
            L_KNE = 25, R_KNE = 26, L_ANK = 27, R_ANK = 28,
            L_FTI = 31, R_FTI = 32;

    /** 关键点 JSON 上限：8 帧 × 33 点 × (2D+3D) 足够，超长直接拒绝（防恶意 payload） */
    private static final int MAX_JSON_LEN = 260_000;

    /** visibility 低于该值的关键点不可信，视为缺失 */
    private static final double MIN_VISIBILITY = 0.3;

    private PoseGeometry() {}

    /**
     * 判断请求是否携带 3D worldLandmarks（调用方用它隔离新旧链路的缓存）。
     */
    public static boolean hasWorld(String landmarksJson) {
        if (landmarksJson == null || landmarksJson.isBlank()
                || landmarksJson.length() > MAX_JSON_LEN) {
            return false;
        }
        try {
            JsonNode frames = MAPPER.readTree(landmarksJson).path("frames");
            if (!frames.isArray() || frames.isEmpty()) return false;
            JsonNode w = frames.get(0).path("world");
            return w.isArray() && w.size() >= 33;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 把前端上传的 landmarks JSON 转成模型可读的几何描述。
     * 输入形如：{"model":"blazepose-heavy","frames":[
     *   {"t":0.4,"pts":[[x,y,v]×33],"world":[[x,y,z]×33]},…]}
     * 数据无效 / 没有可用帧时返回 null（调用方按"无关键点"处理，纯视觉分析）。
     */
    public static String describe(String landmarksJson) {
        if (landmarksJson == null || landmarksJson.isBlank()
                || landmarksJson.length() > MAX_JSON_LEN) {
            return null;
        }
        try {
            JsonNode frames = MAPPER.readTree(landmarksJson).path("frames");
            if (!frames.isArray() || frames.isEmpty()) return null;

            StringBuilder sb2d = new StringBuilder();
            StringBuilder sb3d = new StringBuilder();
            int valid = 0, valid3d = 0;
            for (JsonNode frame : frames) {
                JsonNode pts = frame.path("pts");
                JsonNode world = frame.path("world");
                boolean hasWorld = world.isArray() && world.size() >= 33;
                if (!pts.isArray() || pts.size() < 33) continue;

                double t = frame.path("t").asDouble(0);

                /* ── 2D 部分（可靠）── */
                sb2d.append(String.format(Locale.ROOT, "· 帧@%.2fs：", t));
                appendAngle(sb2d, "左肘", angle(pts, L_SHO, L_ELB, L_WRI));
                appendAngle(sb2d, "右肘", angle(pts, R_SHO, R_ELB, R_WRI));
                appendAngle(sb2d, "左肩", angle(pts, L_HIP, L_SHO, L_ELB));
                appendAngle(sb2d, "右肩", angle(pts, R_HIP, R_SHO, R_ELB));
                appendAngle(sb2d, "左髋", angle(pts, L_SHO, L_HIP, L_KNE));
                appendAngle(sb2d, "右髋", angle(pts, R_SHO, R_HIP, R_KNE));
                appendAngle(sb2d, "左膝", angle(pts, L_HIP, L_KNE, L_ANK));
                appendAngle(sb2d, "右膝", angle(pts, R_HIP, R_KNE, R_ANK));
                Double lean = torsoLean(pts);
                if (lean != null) {
                    sb2d.append(String.format(Locale.ROOT, "躯干偏离竖直%.0f° ", lean));
                }
                String kneeToe = kneeToeOffset(pts);
                if (kneeToe != null) sb2d.append(kneeToe);
                sb2d.setLength(sb2d.length() - 1);   // 去掉行尾空格
                sb2d.append('\n');
                valid++;

                /* ── 3D 部分（估算，仅在携带 worldLandmarks 时输出）── */
                if (hasWorld) {
                    StringBuilder line = new StringBuilder();
                    line.append(String.format(Locale.ROOT, "· 3D估算@%.2fs：", t));
                    appendAngle(line, "左肘", angle3(world, pts, L_SHO, L_ELB, L_WRI));
                    appendAngle(line, "右肘", angle3(world, pts, R_SHO, R_ELB, R_WRI));
                    appendAngle(line, "左髋", angle3(world, pts, L_SHO, L_HIP, L_KNE));
                    appendAngle(line, "右髋", angle3(world, pts, R_SHO, R_HIP, R_KNE));
                    appendAngle(line, "左膝", angle3(world, pts, L_HIP, L_KNE, L_ANK));
                    appendAngle(line, "右膝", angle3(world, pts, R_HIP, R_KNE, R_ANK));
                    Double lean3 = torsoLean3(world, pts);
                    if (lean3 != null) {
                        line.append(String.format(Locale.ROOT, "躯干偏离竖直%.0f° ", lean3));
                    }
                    appendCm(line, "膝外翻(额状面)左", frontalKneeDev(world, pts, L_HIP, L_KNE, L_ANK));
                    appendCm(line, " 右", frontalKneeDev(world, pts, R_HIP, R_KNE, R_ANK));
                    appendCm(line, " 膝超脚尖前后(前为正)左", kneeToeDepth(world, pts, L_KNE, L_FTI));
                    appendCm(line, " 右", kneeToeDepth(world, pts, R_KNE, R_FTI));
                    if (line.length() > String.format(Locale.ROOT, "· 3D估算@%.2fs：", t).length()) {
                        line.setLength(line.length() - 1);
                        line.append('\n');
                        sb3d.append(line);
                        valid3d++;
                    }
                }
            }
            if (valid == 0) return null;

            StringBuilder out = new StringBuilder();
            out.append("共 ").append(valid).append(" 帧有效关键点")
               .append("（2D 角度单位：度，180°=关节完全伸直；")
               .append("水平偏移为画面归一化坐标差，符号取决于朝向）：\n")
               .append(sb2d);
            if (valid3d > 0) {
                out.append("其中 ").append(valid3d).append(" 帧带 3D 估算")
                   .append("（worldLandmarks 米制坐标；深度为单目推断，精度有限仅供参考；")
                   .append("膝外翻=膝偏离髋-踝线的前后距离，膝超脚尖=膝相对脚尖的前移量，正值=膝盖更靠前）：\n")
                   .append(sb3d);
            }
            return out.toString();
        } catch (Exception e) {
            return null;   // 任何解析异常都降级为纯视觉分析，不影响主流程
        }
    }

    /* ─────────────── 2D 几何计算（纯函数，可单测） ─────────────── */

    /** 三点夹角（顶点 b），单位：度；任一点缺失或重合返回 null */
    static Double angle(JsonNode pts, int a, int b, int c) {
        double[] pa = pt(pts, a), pb = pt(pts, b), pc = pt(pts, c);
        if (pa == null || pb == null || pc == null) return null;
        double v1x = pa[0] - pb[0], v1y = pa[1] - pb[1];
        double v2x = pc[0] - pb[0], v2y = pc[1] - pb[1];
        double denom = Math.hypot(v1x, v1y) * Math.hypot(v2x, v2y);
        if (denom < 1e-9) return null;
        double cos = (v1x * v2x + v1y * v2y) / denom;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /** 躯干（双肩中点 → 双髋中点）与竖直方向的夹角，度 */
    static Double torsoLean(JsonNode pts) {
        double[] lSho = pt(pts, L_SHO), rSho = pt(pts, R_SHO);
        double[] lHip = pt(pts, L_HIP), rHip = pt(pts, R_HIP);
        if (lSho == null || rSho == null || lHip == null || rHip == null) return null;
        double dx = (lSho[0] + rSho[0]) / 2 - (lHip[0] + rHip[0]) / 2;
        double dy = (lSho[1] + rSho[1]) / 2 - (lHip[1] + rHip[1]) / 2;
        if (Math.hypot(dx, dy) < 1e-9) return null;
        return Math.toDegrees(Math.atan2(Math.abs(dx), Math.abs(dy)));
    }

    /** 膝盖与脚尖的水平偏移（深蹲"膝盖不过脚尖"参考指标），任一侧有效即输出 */
    static String kneeToeOffset(JsonNode pts) {
        double[] lKne = pt(pts, L_KNE), lFti = pt(pts, L_FTI);
        double[] rKne = pt(pts, R_KNE), rFti = pt(pts, R_FTI);
        if (lKne == null || lFti == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "膝-脚尖水平偏移 左%+.2f", lKne[0] - lFti[0]));
        if (rKne != null && rFti != null) {
            sb.append(String.format(Locale.ROOT, " 右%+.2f", rKne[0] - rFti[0]));
        }
        sb.append(' ');
        return sb.toString();
    }

    /** 取第 i 个关键点 [x, y]；visibility 过低视为缺失 */
    private static double[] pt(JsonNode pts, int i) {
        JsonNode p = pts.get(i);
        if (p == null || p.size() < 2) return null;
        if (p.size() >= 3 && p.get(2).asDouble(1) < MIN_VISIBILITY) return null;
        return new double[]{p.get(0).asDouble(), p.get(1).asDouble()};
    }

    /* ─────────────── 3D 几何计算（基于 worldLandmarks，估算） ─────────────── */

    /** 3D 三点夹角（顶点 b），度；任一点缺失返回 null */
    static Double angle3(JsonNode world, JsonNode pts, int a, int b, int c) {
        double[] pa = pt3(world, pts, a), pb = pt3(world, pts, b), pc = pt3(world, pts, c);
        if (pa == null || pb == null || pc == null) return null;
        double[] v1 = sub(pa, pb), v2 = sub(pc, pb);
        double denom = norm(v1) * norm(v2);
        if (denom < 1e-9) return null;
        double cos = dot(v1, v2) / denom;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /** 3D 躯干偏离竖直的角度（世界系 y 为竖直轴；用 |dy| 对 y 轴正负方向不敏感），度 */
    static Double torsoLean3(JsonNode world, JsonNode pts) {
        double[] lSho = pt3(world, pts, L_SHO), rSho = pt3(world, pts, R_SHO);
        double[] lHip = pt3(world, pts, L_HIP), rHip = pt3(world, pts, R_HIP);
        if (lSho == null || rSho == null || lHip == null || rHip == null) return null;
        double[] v = sub(mid(lSho, rSho), mid(lHip, rHip));
        double n = norm(v);
        if (n < 1e-9) return null;
        return Math.toDegrees(Math.acos(Math.min(1, Math.abs(v[1]) / n)));
    }

    /**
     * 膝外翻参考指标：额状面（x-y）内膝到"髋→踝"连线的距离，cm。
     * 数值越大 = 膝盖相对髋踝线偏出越多（内扣/外展都会计入，方向由 AI 结合画面判断）。
     */
    static Double frontalKneeDev(JsonNode world, JsonNode pts, int hipI, int kneI, int ankI) {
        double[] hip = pt3(world, pts, hipI), kne = pt3(world, pts, kneI), ank = pt3(world, pts, ankI);
        if (hip == null || kne == null || ank == null) return null;
        double ax = ank[0] - hip[0], ay = ank[1] - hip[1];
        double len = Math.hypot(ax, ay);
        if (len < 1e-9) return null;
        double cross = (kne[0] - hip[0]) * ay - (kne[1] - hip[1]) * ax;
        return Math.abs(cross) / len * 100;   // 米 → 厘米
    }

    /**
     * 膝-脚尖前后距离（z 轴，cm）：脚尖z - 膝z，正值 = 膝盖比脚尖更靠前（膝超脚尖方向）。
     * z 的"前方"取决于拍摄朝向，故标注为估算、由 AI 结合画面解读。
     */
    static Double kneeToeDepth(JsonNode world, JsonNode pts, int kneI, int ftiI) {
        double[] kne = pt3(world, pts, kneI), fti = pt3(world, pts, ftiI);
        if (kne == null || fti == null) return null;
        return (fti[2] - kne[2]) * 100;   // 米 → 厘米
    }

    /**
     * 取第 i 个 3D 关键点 [x, y, z]。
     * worldLandmarks 没有可靠的 visibility 字段，用同索引 2D 点的可见度做置信门槛。
     */
    private static double[] pt3(JsonNode world, JsonNode pts, int i) {
        JsonNode p = world.get(i);
        JsonNode q = pts.get(i);
        if (p == null || p.size() < 3) return null;
        if (q == null || q.size() < 2) return null;
        if (q.size() >= 3 && q.get(2).asDouble(1) < MIN_VISIBILITY) return null;
        return new double[]{p.get(0).asDouble(), p.get(1).asDouble(), p.get(2).asDouble()};
    }

    /* ─────────────── 小工具 ─────────────── */

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double norm(double[] v) {
        return Math.sqrt(dot(v, v));
    }

    private static double[] mid(double[] a, double[] b) {
        return new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    private static void appendAngle(StringBuilder sb, String name, Double deg) {
        if (deg == null) return;
        sb.append(name).append(Math.round(deg)).append("° ");
    }

    private static void appendCm(StringBuilder sb, String name, Double cm) {
        if (cm == null) return;
        sb.append(name).append(String.format(Locale.ROOT, "%.1fcm", cm)).append(' ');
    }
}
