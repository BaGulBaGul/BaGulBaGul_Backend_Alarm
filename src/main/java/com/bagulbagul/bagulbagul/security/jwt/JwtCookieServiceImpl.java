package com.bagulbagul.bagulbagul.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
@RequiredArgsConstructor
public class JwtCookieServiceImpl implements JwtCookieService {

    @Value("${user.login.access_token_cookie_name}")
    private String ACCESS_TOKEN_COOKIE_NAME;

    @Value("${user.login.refresh_token_cookie_name}")
    private String REFRESH_TOKEN_COOKIE_NAME;

    @Override
    public String getAccessToken(ServerHttpRequest request) {
        return getToken(request, ACCESS_TOKEN_COOKIE_NAME);
    }

    @Override
    public String getRefreshToken(ServerHttpRequest request) {
        return getToken(request, REFRESH_TOKEN_COOKIE_NAME);
    }

    private String getToken(ServerHttpRequest request, String tokenName) {
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        HttpCookie cookie = cookies.getFirst(tokenName);
        if(cookie == null) {
            return null;
        }
        return cookie.getValue();
    }
}
