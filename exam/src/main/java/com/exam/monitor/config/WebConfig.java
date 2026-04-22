package com.exam.monitor.config;

import com.exam.monitor.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")         // 拦截所有 API
                .excludePathPatterns("/api/auth/login"); // 排除登录接口
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 保持你原有的静态资源映射逻辑
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:D:/exam-uploads/");
    }
}