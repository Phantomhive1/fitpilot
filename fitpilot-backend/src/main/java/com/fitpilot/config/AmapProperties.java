package com.fitpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 高德开放平台配置项，见 application.yml 中 fitpilot.amap.*
 *
 * Key 申请：https://console.amap.com → 创建应用 → 添加 Key，服务平台必须选「Web服务」。
 * 个人开发者认证后有每日免费额度（周边搜索几千次/日），自用完全够。
 */
@ConfigurationProperties(prefix = "fitpilot.amap")
public class AmapProperties {

    private String key;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String summary() {
        return "key=" + (key == null || key.isBlank() ? "<未配置>" : key.substring(0, 6) + "...");
    }
}
