package com.bagulbagul.bagulbagul.security.jwt;


import org.springframework.http.server.reactive.ServerHttpRequest;

public interface JwtCookieService {
    String getAccessToken(ServerHttpRequest request);
    String getRefreshToken(ServerHttpRequest request);
}
