package com.fitpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。
 *
 * 生产环境推荐做法：让 nginx 把前后端反代到同一个域名下，根本不走 CORS。
 * 但开发期或前端独立部署时，需要 CORS。
 *
 * 配置方式（application.yml）：
 *   fitpilot:
 *     cors:
 *       allowed-origins: https://fit.yourdomain.cn,https://admin.yourdomain.cn
 *       # 不配置则默认只允许 http://localhost:5173（Vite 开发服务器）
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${fitpilot.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}