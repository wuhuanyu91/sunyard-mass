package com.sunyard.llm.mas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 配置：允许前端开发服务器访问后端 API。
 * WebFlux 环境必须使用 reactive 包下的 CorsWebFilter（非 MVC 的 CorsFilter）。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 开发环境 + 生产环境允许的源
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",   // Vite dev (5173) 及其他本地端口
                "http://127.0.0.1:*",
                "https://*.example.com" // 生产域名（按需替换）
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
