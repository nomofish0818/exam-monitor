package com.exam.monitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 將 /uploads/** 的請求映射到本地 D 槽的 exam-uploads 資料夾
        // 注意：Windows 系統路徑前面要加 file:
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:D:/exam-uploads/");
    }
}