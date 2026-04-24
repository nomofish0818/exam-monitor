package com.exam.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. 允许任何前端来源跨域访问 (Spring Boot 2.4+ 推荐使用 allowedOriginPatterns)
        config.addAllowedOriginPattern("*");

        // 2. 允许任何请求头 (如 Authorization, Content-Type 等)
        config.addAllowedHeader("*");

        // 3. 允许任何 HTTP 请求方法 (GET, POST, OPTIONS, PUT, DELETE 等)
        config.addAllowedMethod("*");

        // 4. 允许前端携带凭证 (如 Cookie、Token)
        config.setAllowCredentials(true);

        // 5. 预检请求的缓存时间（秒），避免每次发请求前都发 OPTIONS
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 6. 对所有的 API 路由生效
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}