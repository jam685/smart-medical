package com.hospital.smartmedical.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    /** 本地上传根目录（头像等用户上传文件），默认 ./uploads */
    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                // /uploads/** 为头像等静态资源，浏览器 <img> 直接访问不带 token，需放行
                .excludePathPatterns("/error", "/uploads/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 映射到本地上传目录，外部访问路径含 context-path：
        // /api/smart-medical/v1/uploads/avatars/xxx.jpg
        String absoluteLocation = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absoluteLocation);
    }
}
