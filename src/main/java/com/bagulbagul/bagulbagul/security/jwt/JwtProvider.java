package com.bagulbagul.bagulbagul.security.jwt;

import com.bagulbagul.bagulbagul.security.exception.AccessTokenException;
import com.bagulbagul.bagulbagul.security.exception.RefreshTokenException;
import io.jsonwebtoken.JwtException;

public interface JwtProvider {
    Long getUserIdFromAccessToken(String accessToken) throws AccessTokenException;
    Long getUserIdFromRefreshToken(String refreshToken) throws RefreshTokenException;
    String getSubject(String token) throws JwtException;
}

