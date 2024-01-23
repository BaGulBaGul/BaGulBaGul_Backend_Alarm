package com.bagulbagul.bagulbagul.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtCookieServiceImpl implements JwtCookieService {

    @Value("${user.login.access_token_cookie_name}")
    private String ACCESS_TOKEN_COOKIE_NAME;

    @Value("${user.login.refresh_token_cookie_name}")
    private String REFRESH_TOKEN_COOKIE_NAME;

    @Override
    public String getAccessToken(HttpServletRequest request) {
        return getToken(request, ACCESS_TOKEN_COOKIE_NAME);
    }

    @Override
    public String getRefreshToken(HttpServletRequest request) {
        return getToken(request, REFRESH_TOKEN_COOKIE_NAME);
    }

    private String getToken(HttpServletRequest request, String tokenName) {
        if(request.getCookies() == null) {
            return null;
        }
        for(Cookie cookie : request.getCookies()) {
            if(cookie.getName().equals(tokenName)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
