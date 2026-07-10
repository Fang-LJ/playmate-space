package com.playmate.space.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.playmate.space.common.security.JwtProperties;
import com.playmate.space.storage.MinioProperties;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, MinioProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/health",
                        "/api/auth/wx-login",
                        "/api/auth/account-register",
                        "/api/auth/account-login",
                        "/api/activity-invites/**"
                );
    }
}
