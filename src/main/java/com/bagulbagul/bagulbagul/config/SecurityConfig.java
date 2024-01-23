package com.bagulbagul.bagulbagul.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults());
        // csrf
        http.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable());
        // 기본 인증(아이디 비밀번호 BASE64인코딩) 비활성화
        http.httpBasic(httpSecurityHttpBasicConfigurer -> httpSecurityHttpBasicConfigurer.disable());
        // 세션x
//        http.sessionManagement(session ->
//                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
//        //테스트용 인증 예외 경로
//        http.authorizeHttpRequests(authorizationManagerRequestMatcherRegistry -> {
//            authorizationManagerRequestMatcherRegistry.requestMatchers("/subscribe").permitAll();
//            authorizationManagerRequestMatcherRegistry.requestMatchers("/test").permitAll();
//        });
        return http.build();
    }
}
