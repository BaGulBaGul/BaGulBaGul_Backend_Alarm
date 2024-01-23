package com.bagulbagul.bagulbagul.config;

import com.bagulbagul.bagulbagul.security.jwt.JwtProvider;
import com.bagulbagul.bagulbagul.security.jwt.JwtProviderImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {
    @Value("${jwt.secret_key}")
    private String SECRET_KEY_STRING;

    private String SECRET_KEY_ALGORITHM = "HmacSHA512";

    @Bean
    public JwtProvider jwtProvider() {
        return JwtProviderImpl.builder()
                .SECRET_KEY_STRING(SECRET_KEY_STRING)
                .SECRET_KEY_ALGORITHM(SECRET_KEY_ALGORITHM)
                .build();
    }
}