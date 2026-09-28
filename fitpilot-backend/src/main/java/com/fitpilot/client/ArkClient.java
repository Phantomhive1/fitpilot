package com.fitpilot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.ArkProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 火山方舟（豆包 Seed 系列）API 客户端。
 * 协议与 OpenAI Chat Completions 兼容，支持普通调用与 SSE 流式调用。
 */
@Component
public class ArkClient {

    private final ArkProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ArkClient(ArkProperties props) {
        this.props = props;
    }

    /** 普通对话（非流式），返回首条回复文本 */
    public String chat(List<Map<String, Object>> messages, String model, double temperature) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        // Seed-2.1 / 2.0-pro 默认开启「深度思考」会 1-3 分钟才返回；这里默认关掉以保证 UX
        body.put("thinking", Map.of("type", "disabled"));
        System.out.println("[ArkClient] POST chat model=" + model + " messages=" + messages.size() + " temp=" + temperature);
        JsonNode resp = post(body);
        return resp.path("choices").path(0).path("message").path("content").asText();
    }

    /**
     * 流式对话：每收到一个增量 token 回调一次 onToken，阻塞直到流结束。
     * messages 中的 content 既可以是 String（纯文本），也可以是 List（多模态：text/image_url）。
     */
    public void chatStream(List<Map<String, Object>> messages, String model, double temperature,
                           Consumer<String> onToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", true);
        body.put("thinking", Map.of("type", "disabled"));
        System.out.println("[ArkClient] POST chatStream model=" + model + " messages=" + messages.size());

        HttpRequest request = newRequest(body);
        try {
            HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Ark API 错误(" + resp.statusCode() + "): " + err);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;
                    JsonNode node = mapper.readTree(data);
                    String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) onToken.accept(delta);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用 Ark API 失败: " + e.getMessage(), e);
        }
    }

    private JsonNode post(Map<String, Object> body) {
        HttpRequest request = newRequest(body);
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Ark API 错误(" + resp.statusCode() + "): " + resp.body());
            }
            return mapper.readTree(resp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用 Ark API 失败: " + e.getMessage(), e);
        }
    }

    private HttpRequest newRequest(Map<String, Object> body) {
        String key = props.getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("未配置 ARK_API_KEY。请在方舟控制台「API Key 管理」创建，"
                    + "点击眼睛图标显示完整 key 后整串复制（表格里打码的 •••• 不是 key 本身），"
                    + "并通过环境变量 ARK_API_KEY 注入（IDEA 运行需在 Run Configuration 的 Environment variables 中配置，改完要重启）。");
        }
        // 只做最基本的检查；key 真实格式为 ark- 开头的长串，格式对错由方舟服务端裁决
        String trimmed = key.trim();
        return HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + trimmed)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8))
                .build();
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 调用方舟 Files API 上传一个文件（用于视频等大文件），返回 file_id。
     * 图片可直接走 base64 内联，不必调此方法。
     */
    public String uploadFile(byte[] bytes, String filename, String contentType) throws IOException {
        String ak = props.getApiKey() == null ? "<null>" : props.getApiKey().trim();
        System.out.println("[ArkClient] uploadFile start: filename=" + filename
                + ", size=" + bytes.length + "B, contentType=" + contentType
                + ", apiKey=" + (ak.length() > 8 ? ak.substring(0, 8) + "..." : ak + "(len=" + ak.length() + ")"));
        String boundary = "----FitPilotBoundary" + System.currentTimeMillis();
        var byteStream = new java.io.ByteArrayOutputStream();
        // file 字段
        byteStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        byteStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        byteStream.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byteStream.write(bytes);
        byteStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        // purpose 字段
        byteStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        byteStream.write("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        byteStream.write("user_data\r\n".getBytes(StandardCharsets.UTF_8));
        byteStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/files"))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + props.getApiKey().trim())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(byteStream.toByteArray()))
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                throw new IllegalStateException("Ark Files API 错误(" + resp.statusCode() + "): " + resp.body());
            }
            String fileId = mapper.readTree(resp.body()).path("id").asText();
            if (fileId.isBlank()) throw new IllegalStateException("Files API 未返回 file_id: " + resp.body());
            System.out.println("[ArkClient] uploadFile " + filename + " (" + bytes.length + "B) -> " + fileId);
            // 上传完成 ≠ 可用，方舟需要内部处理（一般几秒，视频偶尔几十秒），必须等状态变 active 才能在 chat 里引用
            waitFileActive(fileId, Duration.ofSeconds(90));
            return fileId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用 Ark Files API 失败: " + e.getMessage(), e);
        }
    }

    /** 轮询 GET /files/{file_id} 直到 status=active（或 timeout/失败） */
    private void waitFileActive(String fileId, Duration maxWait) {
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/files/" + fileId))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + props.getApiKey().trim())
                .GET()
                .build();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    String status = mapper.readTree(resp.body()).path("status").asText("");
                    System.out.println("[ArkClient] file " + fileId + " status=" + status);
                    if ("active".equalsIgnoreCase(status) || "processed".equalsIgnoreCase(status)) {
                        return;
                    }
                    if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                        throw new IllegalStateException("方舟文件处理失败：" + resp.body());
                    }
                } else {
                    System.out.println("[ArkClient] file " + fileId + " GET status=" + resp.statusCode() + " body=" + resp.body());
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("文件处理失败")) throw e;
                // GET 单次失败不致命，继续轮询
                System.out.println("[ArkClient] file " + fileId + " GET error: " + e.getMessage());
            } catch (java.io.IOException ioe) {
                System.out.println("[ArkClient] file " + fileId + " GET io error: " + ioe.getMessage());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        throw new IllegalStateException("方舟文件处理超时（" + maxWait.toSeconds() + "秒），请稍后重试或换小一点的视频");
    }
}
