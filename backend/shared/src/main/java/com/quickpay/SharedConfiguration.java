package com.quickpay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class SharedConfiguration {
    @Bean
    JwtService jwtService(Environment environment) {
        return new JwtService(environment.getProperty(
            "JWT_SECRET", "local-only-change-this-secret-before-deployment-123456"));
    }
}
