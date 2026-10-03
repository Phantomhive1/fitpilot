package com.fitpilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.client.AmapClient;
import com.fitpilot.client.ArkClient;
import com.fitpilot.config.ArkProperties;
import com.fitpilot.model.DietPlan;
import com.fitpilot.repo.DietPlanRepository;
import com.fitpilot.util.HashUtils;
import com.fitpilot.util.JsonUtils;
import com.fitpilot.util.SlidingWindowLimiter;
import com.fitpilot.util.TtlCache;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饮食计划服务。
 *
 * 两种模式：
 *   lazy（懒人）: 只填 身高/体重/年龄/性别/目标/训练量，AI 自己算 TDEE 和三大宏量再排餐。
 *   pro  （专业）: 宏量目标已定 —— 用户要么直接给克数（inputType=grams），
 *                  要么给 体重 + 每公斤倍数（inputType=multiplier，服务端换算成克数），AI 只负责按克数排餐。
 *
 * 防重复调用（与姿势分析同思路，缓存命中不计限流、不调方舟）：
 *   1. 参数归一化 → SHA-256 哈希
 *   2. 内存缓存（TtlCache 2 小时）
 *   3. 数据库兜底（同哈希 24 小时内的首条真实结果）
 *   4. 真正调方舟前先过滑动窗口限流（10 分钟 5 次）
 */
@Service
public class DietService {

    private final ArkClient ark;
    private final ArkProperties arkProps;
    private final AmapClient amap;
    private final DietPlanRepository dietRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    private final TtlCache<String, Map<String, Object>> cache = new TtlCache<>(Duration.ofHours(2), "diet");
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(5, Duration.ofMinutes(10));

    public DietService(ArkClient ark, ArkProperties arkProps, AmapClient amap, DietPlanRepository dietRepo) {
        this.ark = ark;
        this.arkProps = arkProps;
        this.amap = amap;
        this.dietRepo = dietRepo;
    }

    public Map<String, Object> generate(Map<String, Object> body, String dedupKey) {
        String mode = str(body, "mode", "lazy");
        boolean lazy = "lazy".equalsIgnoreCase(mode) || "懒人".equals(mode);
        if (!lazy && !"pro".equalsIgnoreCase(mode) && !"专业".equals(mode)) {
            throw new IllegalArgumentException("mode 只支持 lazy（懒人）或 pro（专业）");
        }
        mode = lazy ? "lazy" : "pro";

        // 用餐方式：cook（有时间做饭，输出一日循环计划）/ takeaway（没时间做饭，查周边外卖并输出一周计划）
        String dining = str(body, "dining", "cook");
        boolean takeaway = "takeaway".equalsIgnoreCase(dining) || "点外卖".equals(dining);
        if (!takeaway && !"cook".equalsIgnoreCase(dining) && !"自己做饭".equals(dining)) {
            throw new IllegalArgumentException("dining 只支持 cook（自己做饭）或 takeaway（点外卖）");
        }
        dining = takeaway ? "takeaway" : "cook";

        // ── 归一化参数（同时做校验），LinkedHashMap 保证序列化字段顺序稳定 ──
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("dining", dining);
        normalized.put("mode", mode);
        normalized.put("mealsPerDay", intOf(body, "mealsPerDay", 3, 2, 6));
        normalized.put("preference", str(body, "preference", "").trim());

        // 外卖模式：查周边餐厅（列表也进哈希——周边店变了视为新输入）
        String restaurantList = "";
        if (takeaway) {
            String address = str(body, "address", "");
            if (address.length() < 2) {
                throw new IllegalArgumentException("点外卖模式请填写所在地址（如「北京市海淀区中关村大街1号」）");
            }
            int radius = intOf(body, "radius", 3000, 500, 10000);
            List<String> restaurants = amap.nearbyRestaurants(address, radius);
            if (restaurants.isEmpty()) {
                throw new IllegalArgumentException("你周边 " + radius + " 米内没找到餐厅，请把地址写得更具体一些或扩大搜索范围");
            }
            normalized.put("address", address);
            normalized.put("radius", radius);
            normalized.put("restaurants", String.join("、", restaurants));
            restaurantList = String.join("、", restaurants);
        }

        String macroBrief;      // 给 AI 的宏量说明
        if (lazy) {
            String gender = str(body, "gender", "");
            if (!"男".equals(gender) && !"女".equals(gender)) {
                throw new IllegalArgumentException("请选择性别");
            }
            int age = intOf(body, "age", 0, 10, 90);
            int height = intOf(body, "heightCm", 0, 120, 230);
            double weight = dblOf(body, "weightKg", 0, 30, 300);
            String goal = str(body, "goal", "");
            if (!List.of("减脂", "增肌", "维持").contains(goal)) {
                throw new IllegalArgumentException("目标只支持 减脂 / 增肌 / 维持");
            }
            int days = intOf(body, "daysPerWeek", 0, 0, 7);
            int minutes = intOf(body, "sessionMinutes", 0, 0, 240);
            if (days == 0) throw new IllegalArgumentException("请填写每周训练天数");

            normalized.put("gender", gender);
            normalized.put("age", age);
            normalized.put("heightCm", height);
            normalized.put("weightKg", weight);
            normalized.put("goal", goal);
            normalized.put("daysPerWeek", days);
            normalized.put("sessionMinutes", minutes);
            macroBrief = String.format("""
                    用户身体数据：性别 %s，年龄 %d 岁，身高 %d cm，体重 %.1f kg。
                    训练情况：每周训练 %d 天，每次约 %d 分钟。
                    目标：%s。
                    请你先用 Mifflin-St Jeor 公式估算基础代谢 BMR，按训练量确定活动系数并算出每日总消耗 TDEE，
                    再根据目标调整热量缺口/盈余（减脂 15%%~20%% 缺口，增肌 10%%~15%% 盈余，维持即 TDEE），
                    然后自行确定每日碳水化合物/蛋白质/脂肪克数（减脂蛋白质建议 ≥1.8g/kg，增肌蛋白质 1.6~2.2g/kg，
                    脂肪不低于 0.6g/kg），填入 targetCalories 与 macros。""",
                    gender, age, height, weight, days, minutes, goal);
        } else {
            String inputType = str(body, "inputType", "grams");
            int carbs, protein, fat;
            if ("multiplier".equalsIgnoreCase(inputType) || "倍数".equals(inputType)) {
                double weight = dblOf(body, "weightKg", 0, 30, 300);
                double carbX = dblOf(body, "carbX", 0, 0.1, 15);
                double proteinX = dblOf(body, "proteinX", 0, 0.1, 8);
                double fatX = dblOf(body, "fatX", 0, 0.1, 5);
                carbs = (int) Math.round(weight * carbX);
                protein = (int) Math.round(weight * proteinX);
                fat = (int) Math.round(weight * fatX);
                normalized.put("inputType", "multiplier");
                normalized.put("weightKg", weight);
                normalized.put("carbX", carbX);
                normalized.put("proteinX", proteinX);
                normalized.put("fatX", fatX);
            } else {
                carbs = intOf(body, "carbsG", 0, 20, 1500);
                protein = intOf(body, "proteinG", 0, 20, 500);
                fat = intOf(body, "fatG", 0, 10, 400);
                normalized.put("inputType", "grams");
                normalized.put("carbsG", carbs);
                normalized.put("proteinG", protein);
                normalized.put("fatG", fat);
            }
            normalized.put("carbsFinal", carbs);
            normalized.put("proteinFinal", protein);
            normalized.put("fatFinal", fat);

            int calories = Math.round(carbs * 4f + protein * 4f + fat * 9f);
            macroBrief = String.format("""
                    用户已明确指定每日宏量目标（专业模式），你必须严格按以下克数设计，不得改动：
                    碳水化合物 %d g、蛋白质 %d g、脂肪 %d g（换算热量约 %d kcal，±5%% 以内）。
                    请把上述数值原样填入 targetCalories 与 macros（targetCalories 填换算值，macros 填给定克数）。""",
                    carbs, protein, fat, calories);
        }

        // ── 去重：参数哈希 ──
        String inputJson;
        try {
            inputJson = mapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("参数序列化失败", e);
        }
        String inputHash = HashUtils.sha256Hex(inputJson);

        // 1) 内存缓存命中
        Map<String, Object> hit = cache.get(inputHash);
        if (hit != null) {
            Map<String, Object> r = new LinkedHashMap<>(hit);
            r.put("cached", true);
            r.put("cacheSource", "memory");
            saveRecord(dedupKey, mode, inputHash, inputJson, r);
            return r;
        }

        // 2) 数据库历史命中（24 小时内同参数的首条真实结果）
        List<DietPlan> history = dietRepo.findByInputHashSince(inputHash, LocalDateTime.now().minusHours(24));
        if (!history.isEmpty()) {
            DietPlan first = history.get(0);
            try {
                Map<String, Object> plan = mapper.readValue(first.getPlanJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                cache.put(inputHash, plan);
                Map<String, Object> out = new LinkedHashMap<>(plan);
                out.put("cached", true);
                out.put("cacheSource", "database");
                out.put("planId", first.getId());
                out.put("previousGeneratedAt", first.getCreatedAt().toString());
                saveRecord(dedupKey, mode, inputHash, inputJson, out);
                return out;
            } catch (Exception ignore) {
                // 历史记录损坏则视为未命中，走真实调用
            }
        }

        // 3) 限流（只限制真实方舟调用；缓存命中不计）
        if (!limiter.tryAcquire(dedupKey)) {
            int used = limiter.currentCount(dedupKey);
            throw new IllegalArgumentException(
                    "10 分钟内最多生成 5 次饮食计划（已用 " + used + " 次），相同参数重复生成走缓存不占次数，请稍后再试。");
        }

        // 4) 真正调方舟
        String system;
        if (takeaway) {
            system = """
                    你是一名持有资质的运动营养师。用户平时没时间做饭，一日三餐（含加餐）全部点外卖解决。
                    请只从下面这份「用户周边可选餐厅列表」中选店安排：
                    """ + restaurantList + """

                    要求：
                    1. 只输出一个 JSON 对象，不要输出任何解释文字或 Markdown 代码块标记。
                    2. JSON 结构固定为：
                       {"title":"计划名称","targetCalories":整数,
                        "macros":{"carbs":整数,"protein":整数,"fat":整数},
                        "summary":"总思路（80字以内）",
                        "days":[{"day":1,"label":"周一",
                          "meals":[{"meal":"早餐","restaurant":"从列表中选的店名","order":"具体点法（含分量调整/替换建议，如：麦辣鸡腿堡套餐，薯条换玉米，可乐换零度）","calories":整数,"carbs":整数,"protein":整数,"fat":整数,
                            "foods":[{"name":"单品名","amount":"分量描述","calories":整数}]}]}],
                        "tips":["执行建议1","执行建议2","执行建议3"]}
                    3. days 必须是周一到周日共 7 天，每天 meals 数量等于用户要求的每日餐数。
                    4. 餐厅要在列表内轮换着用，避免 7 天全点同一家；order 必须是真实菜单上的点法（你了解的常见连锁/常见菜品即可），并主动做健康化调整（酱料减半、含糖饮料换无糖、主食减半等）。
                    5. 每天各餐 calories/carbs/protein/fat 加总应接近 targetCalories/macros（误差 ±5%）。
                    6. tips 给 2~4 条实操建议（如满减凑单怎么凑最健康、骑手高峰期提前下单）。
                    """;
        } else {
            system = """
                    你是一名持有资质的运动营养师，擅长为中国健身人群设计落地性强的饮食计划。请根据用户信息设计一日饮食计划（可长期循环执行）。
                    要求：
                    1. 只输出一个 JSON 对象，不要输出任何解释文字或 Markdown 代码块标记。
                    2. JSON 结构固定为：
                       {"title":"计划名称","targetCalories":整数,
                        "macros":{"carbs":整数,"protein":整数,"fat":整数},
                        "summary":"总思路（80字以内）",
                        "meals":[{"meal":"早餐","time":"建议时间段","calories":整数,"carbs":整数,"protein":整数,"fat":整数,
                          "foods":[{"name":"食物名","amount":"具体分量（克数/份量，注明生重或熟重）","calories":整数}]}],
                        "tips":["执行建议1","执行建议2","执行建议3"]}
                    3. meals 数量必须等于用户要求的每日餐数；各餐的 calories/carbs/protein/fat 加总必须与 targetCalories/macros 基本一致（误差 ±5%）。
                    4. 食物必须是中国超市/菜市场/外卖常见、容易买到的；同一计划内食物不要大量重复。
                    5. 用户有饮食偏好或忌口时必须严格遵守。
                    6. tips 给 2~4 条实操建议（如备餐、外食替换、补剂时机）。
                    """;
        }

        String user = macroBrief + "\n" + String.format("""
                每日餐数：%d 餐（按训练日安排，加餐可包含训练后补给）。
                饮食偏好/忌口：%s""",
                (int) normalized.get("mealsPerDay"),
                normalized.get("preference").toString().isEmpty() ? "无" : normalized.get("preference"));

        String content = ark.chat(
                List.of(Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                arkProps.getChatModel(), 0.7);

        String json = JsonUtils.stripCodeFences(content);
        Map<String, Object> result;
        try {
            JsonNode tree = mapper.readTree(json);
            // 外卖模式要求 days（7 天），做饭模式要求 meals（一日）
            boolean ok = takeaway
                    ? tree.path("days").isArray() && !tree.path("days").isEmpty()
                    : tree.path("meals").isArray() && !tree.path("meals").isEmpty();
            if (!ok) {
                throw new IllegalStateException("模型未返回有效的" + (takeaway ? "一周" : "") + "餐单结构");
            }
            result = mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析模型返回的饮食计划失败：" + e.getMessage(), e);
        }

        result.put("cached", false);
        result.put("cacheSource", "fresh");
        cache.put(inputHash, result);

        DietPlan saved = saveRecord(dedupKey, mode, inputHash, inputJson, result);
        if (saved != null) result.put("planId", saved.getId());
        return result;
    }

    /** 当前会话的饮食计划历史（倒序，最多 20 条），含解析后的完整计划 */
    public List<Map<String, Object>> history(String dedupKey) {
        List<DietPlan> records = dietRepo.findTop20BySessionIdOrderByIdDesc(dedupKey);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DietPlan p : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("mode", p.getMode());
            item.put("title", p.getTitle());
            item.put("cached", p.isCached());
            item.put("createdAt", p.getCreatedAt().toString());
            try {
                Map<String, Object> plan = mapper.readValue(p.getPlanJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                item.put("plan", plan);
            } catch (Exception ignore) {
                // planJson 损坏只展示元信息
            }
            out.add(item);
        }
        return out;
    }

    /** 单条详情（用于前端点击历史查看） */
    public Map<String, Object> detail(Long id, String dedupKey) {
        DietPlan p = dietRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("饮食计划不存在: " + id));
        if (!dedupKey.equals(p.getSessionId())) {
            throw new IllegalArgumentException("无权查看该计划");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.getId());
        out.put("mode", p.getMode());
        out.put("title", p.getTitle());
        out.put("createdAt", p.getCreatedAt().toString());
        try {
            out.put("plan", mapper.readValue(p.getPlanJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {}));
        } catch (Exception ignore) { }
        return out;
    }

    /** 落库（缓存命中也记录一条，便于历史/审计；失败不影响主流程） */
    private DietPlan saveRecord(String dedupKey, String mode, String inputHash,
                                String inputJson, Map<String, Object> result) {
        try {
            DietPlan p = new DietPlan();
            p.setSessionId(dedupKey);
            p.setMode(mode);
            p.setInputHash(inputHash);
            p.setInputJson(inputJson);
            p.setTitle(String.valueOf(result.getOrDefault("title", "AI 饮食计划")));
            p.setPlanJson(mapper.writeValueAsString(result));
            p.setCached(Boolean.TRUE.equals(result.get("cached")));
            return dietRepo.save(p);
        } catch (Exception e) {
            System.out.println("[DietService] saveRecord failed: " + e.getMessage());
            return null;
        }
    }

    // ── 参数读取小工具 ──

    private String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null ? def : String.valueOf(v).trim();
    }

    private int intOf(Map<String, Object> body, String key, int def, int min, int max) {
        Object v = body.get(key);
        int n;
        if (v instanceof Number num) n = num.intValue();
        else if (v != null && !String.valueOf(v).isBlank()) {
            try { n = (int) Math.round(Double.parseDouble(String.valueOf(v))); }
            catch (Exception e) { throw new IllegalArgumentException("参数 " + key + " 必须是数字"); }
        } else n = def;
        if (n < min || n > max) {
            throw new IllegalArgumentException("参数 " + key + " 超出合理范围（" + min + " ~ " + max + "）");
        }
        return n;
    }

    private double dblOf(Map<String, Object> body, String key, double def, double min, double max) {
        Object v = body.get(key);
        double d;
        if (v instanceof Number num) d = num.doubleValue();
        else if (v != null && !String.valueOf(v).isBlank()) {
            try { d = Double.parseDouble(String.valueOf(v)); }
            catch (Exception e) { throw new IllegalArgumentException("参数 " + key + " 必须是数字"); }
        } else d = def;
        if (d < min || d > max) {
            throw new IllegalArgumentException("参数 " + key + " 超出合理范围（" + min + " ~ " + max + "）");
        }
        return d;
    }
}
