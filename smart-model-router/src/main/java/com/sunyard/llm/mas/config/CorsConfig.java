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

    private final MasProperties properties;

    public CorsConfig(MasProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许源走配置（mas.cors.allowed-origin-patterns / MAS_CORS_ALLOWED_ORIGINS），
        // nginx 以容器名作 Host 转发，同源请求在后端视角仍是跨域，白名单必须覆盖实际访问入口
        config.setAllowedOriginPatterns(java.util.Arrays.stream(properties.getCors().getAllowedOriginPatterns().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
