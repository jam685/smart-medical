package com.hospital.smartmedical.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOriginPattern("*");
        cfg.addAllowedMethod("*");
        cfg.addAllowedHeader("*");
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(java.time.Duration.ofSeconds(3600));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(src);
    }
}
