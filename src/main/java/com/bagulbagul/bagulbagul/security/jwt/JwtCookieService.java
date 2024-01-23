package com.bagulbagul.bagulbagul.security.jwt;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface JwtCookieService {
    String getAccessToken(HttpServletRequest request);
    String getRefreshToken(HttpServletRequest request);
}
