package com.fitpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 火山方舟（豆包 Seed 系列）配置项，见 application.yml 中 fitpilot.ark.*
 */
@ConfigurationProperties(prefix = "fitpilot.ark")
public class ArkProperties {

    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String visionModel;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }

    public String getVisionModel() { return visionModel; }
    public void setVisionModel(String visionModel) { this.visionModel = visionModel; }

    /** 启动时打印一眼当前生效的配置，方便排查 Model ID 不对的问题 */
    public String summary() {
        return "baseUrl=" + baseUrl
                + ", chatModel=" + chatModel
                + ", visionModel=" + visionModel
                + ", apiKey=" + (apiKey == null ? "<null>" : (apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "<short>"));
    }
}
