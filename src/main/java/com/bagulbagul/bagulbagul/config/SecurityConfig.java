package com.bagulbagul.bagulbagul.config;

import com.bagulbagul.bagulbagul.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.cors(Customizer.withDefaults());
        // csrf
        http.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable());
        // 기본 인증(아이디 비밀번호 BASE64인코딩) 비활성화
        http.httpBasic(httpSecurityHttpBasicConfigurer -> httpSecurityHttpBasicConfigurer.disable());
        // 세션x
//        http.sessionManagement(session ->
//                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        // 경로별 인증 설정
        http.authorizeExchange(authorizeExchangeSpec -> authorizeExchangeSpec.anyExchange().authenticated());
        return http.build();
    }
}

