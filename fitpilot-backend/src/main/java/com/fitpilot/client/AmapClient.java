package com.fitpilot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.AmapProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 高德开放平台 Web 服务 API 客户端（外卖模式用）。
 *
 * 流程：地址 → 地理编码拿经纬度 → 周边搜索「餐饮服务」类 POI → 返回餐厅名列表。
 * 文档：https://lbs.amap.com/api/webservice/guide/api/search
 *
 * 注意：Key 必须是「Web服务」类型，创建应用时选错类型会报 INVALID_USER_SCODE / USERKEY_PLAT_NOMATCH。
 */
@Component
public class AmapClient {

    private static final String GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String AROUND_URL = "https://restapi.amap.com/v3/place/around";
    /** 高德 POI 分类：050000 = 餐饮服务 */
    private static final String TYPE_FOOD = "050000";

    private final AmapProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AmapClient(AmapProperties props) {
        this.props = props;
    }

    /** 是否已配置高德 Key（外卖模式前置检查用） */
    public boolean enabled() {
        return props.getKey() != null && !props.getKey().isBlank();
    }

    /**
     * 查询地址周边的餐厅名列表（去重，最多 20 个）。
     *
     * @param address       可地理编码的地址（城市+区+小区/大厦/街道）
     * @param radiusMeters  搜索半径（米）
     */
    public List<String> nearbyRestaurants(String address, int radiusMeters) {
        String key = props.getKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("外卖模式需要高德 Key：请到 https://console.amap.com 注册并创建「Web服务」类型的 Key，通过环境变量 AMAP_KEY 注入后重启即可使用。");
        }

        // 1) 地理编码：地址 → 经纬度
        String location = geocode(key, address);
        System.out.println("[AmapClient] geocode \"" + address + "\" -> " + location);

        // 2) 周边搜索餐饮 POI
        String url = AROUND_URL
                + "?key=" + key
                + "&location=" + location
                + "&types=" + TYPE_FOOD
                + "&radius=" + radiusMeters
                + "&offset=25&page=1&sortrule=weight&extensions=base";
        JsonNode body = getJson(url, "周边搜索");
        JsonNode pois = body.path("pois");
        Set<String> names = new LinkedHashSet<>();
        if (pois.isArray()) {
            for (JsonNode poi : pois) {
                String name = poi.path("name").asText("").trim();
                String type = poi.path("type").asText("");
                if (!name.isEmpty() && type.startsWith("餐饮")) {
                    names.add(name);
                }
                if (names.size() >= 20) break;
            }
        }
        // 上面 type 过滤偏严（部分 POI 的 type 只有分类码），过滤后为空则退回不过滤的结果
        if (names.isEmpty() && pois.isArray()) {
            for (JsonNode poi : pois) {
                String name = poi.path("name").asText("").trim();
                if (!name.isEmpty()) names.add(name);
                if (names.size() >= 20) break;
            }
        }
        System.out.println("[AmapClient] around " + location + " radius=" + radiusMeters + "m -> " + names.size() + " restaurants");
        return new ArrayList<>(names);
    }

    /** 地理编码，返回 "lng,lat" */
    private String geocode(String key, String address) {
        String url = GEOCODE_URL
                + "?key=" + key
                + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
        JsonNode body = getJson(url, "地理编码");
        JsonNode geocodes = body.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw new IllegalArgumentException("地址无法识别：「" + address + "」。请写完整一点，例如「北京市海淀区中关村大街1号」。");
        }
        String location = geocodes.get(0).path("location").asText("");
        if (location.isBlank()) {
            throw new IllegalArgumentException("地址无法定位：「" + address + "」");
        }
        return location;
    }

    private JsonNode getJson(String url, String scene) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("高德 " + scene + " HTTP 错误(" + resp.statusCode() + "): " + resp.body());
            }
            JsonNode body = mapper.readTree(resp.body());
            // 高德业务状态码：status=1 成功
            if (!"1".equals(body.path("status").asText())) {
                String info = body.path("info").asText("未知错误");
                throw new IllegalStateException("高德 " + scene + " 失败: " + info + "（infocode=" + body.path("infocode").asText() + "）");
            }
            return body;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用高德 API 失败: " + e.getMessage(), e);
        }
    }
}
