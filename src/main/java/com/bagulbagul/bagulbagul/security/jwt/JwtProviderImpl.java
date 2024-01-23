package com.bagulbagul.bagulbagul.security.jwt;

import com.bagulbagul.bagulbagul.security.exception.AccessTokenException;
import com.bagulbagul.bagulbagul.security.exception.RefreshTokenException;
import io.jsonwebtoken.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Builder;

public class JwtProviderImpl implements JwtProvider {

    private final String SECRET_KEY_STRING;
    private final String SECRET_KEY_ALGORITHM;

    private SecretKey secretKey;

    @Builder
    private JwtProviderImpl(
            String SECRET_KEY_STRING,
            String SECRET_KEY_ALGORITHM
    ) {
        this.SECRET_KEY_STRING = SECRET_KEY_STRING;
        this.SECRET_KEY_ALGORITHM = SECRET_KEY_ALGORITHM;
        byte[] decodedKey = SECRET_KEY_STRING.getBytes();
        this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, SECRET_KEY_ALGORITHM);
    }

    @Override
    public Long getUserIdFromAccessToken(String accessToken) {
        if(accessToken == null) {
            return null;
        }
        try {
            return Long.parseLong(getSubject(accessToken));
        }
        catch (Exception ex) {
            throw new AccessTokenException();
        }
    }

    @Override
    public Long getUserIdFromRefreshToken(String refreshToken) {
        if(refreshToken == null) {
            return null;
        }
        try {
            return Long.parseLong(getSubject(refreshToken));
        }
        catch (Exception ex) {
            throw new RefreshTokenException();
        }
    }

    @Override
    public String getSubject(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseClaimsJws(token)
                .getPayload();
        return claims.getSubject();
    }
}
