package com.bagulbagul.bagulbagul.security.filter;

import com.bagulbagul.bagulbagul.security.exception.AccessTokenException;
import com.bagulbagul.bagulbagul.security.exception.RefreshTokenException;
import com.bagulbagul.bagulbagul.security.jwt.JwtCookieService;
import com.bagulbagul.bagulbagul.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ReactorContextWebFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtProvider jwtProvider;
    private final JwtCookieService jwtCookieService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return authenticate(exchange.getRequest(), exchange.getResponse())
                .flatMap(userId -> {
                    AbstractAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            AuthorityUtils.NO_AUTHORITIES
                    );
                    SecurityContext securityContext = new SecurityContextImpl(authenticationToken);
                    return chain.filter(exchange).contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)));
                });
    }

    private Mono<Long> authenticate(ServerHttpRequest request, ServerHttpResponse response) {
        //AccessToken 추출
        String accessToken = jwtCookieService.getAccessToken(request);
        log.debug("access token = {}", accessToken);

        //AccessToken 검증 후 userId 추출 시도
        try {
            return Mono.just(jwtProvider.getUserIdFromAccessToken(accessToken));
        }
        //실패 시 RefreshToken 검증 후 userId 추출 시도
        catch (AccessTokenException ae) {
            String refreshToken = jwtCookieService.getRefreshToken(request);
            log.debug("refresh token = {}", refreshToken);
            //RefreshToken 검증 후 userId 추출 시도
            try {
                return Mono.just(jwtProvider.getUserIdFromRefreshToken(refreshToken));
            }
            //RefreshToken도 없다면 인증 실패
            catch (RefreshTokenException re) {
                return Mono.empty();
            }
        }
    }
}