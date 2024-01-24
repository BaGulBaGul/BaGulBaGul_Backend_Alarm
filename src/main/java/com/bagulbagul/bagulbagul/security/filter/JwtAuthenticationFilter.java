package com.bagulbagul.bagulbagul.security.filter;

import com.bagulbagul.bagulbagul.security.exception.AccessTokenException;
import com.bagulbagul.bagulbagul.security.exception.RefreshTokenException;
import com.bagulbagul.bagulbagul.security.jwt.JwtCookieService;
import com.bagulbagul.bagulbagul.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final JwtCookieService jwtCookieService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        authenticate(request, response);
        filterChain.doFilter(request, response);
    }

    /*
     * Cookie에 있는 Access Token을 검증 후 userId를 추출해서 인증 처리
     * 만약 AccessToken이 만료되었다면 RefreshToken을 검증 후 토큰을 재발급
     * 만약 RefreshToken도 만료되었다면 인증 실패
     */
    private void authenticate(HttpServletRequest request, HttpServletResponse response) {
        //AccessToken 추출
        String accessToken = jwtCookieService.getAccessToken(request);
        log.debug("access token = {}", accessToken);

        //AccessToken 검증 후 userId 추출 시도
        Long userId;
        try {
            userId = jwtProvider.getUserIdFromAccessToken(accessToken);
        }
        //실패 시 RefreshToken 검증 후 userId 추출 시도
        catch (AccessTokenException ae) {
            String refreshToken = jwtCookieService.getRefreshToken(request);
            log.debug("refresh token = {}", refreshToken);
            //RefreshToken 검증 후 userId 추출 시도
            try {
                userId = jwtProvider.getUserIdFromRefreshToken(refreshToken);
            }
            //RefreshToken도 없다면 인증 실패
            catch (RefreshTokenException re) {
                return;
            }
        }

        log.debug("인증 완료, userId = {}", userId);
        //인증 처리를 위해 security context설정
        AbstractAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                AuthorityUtils.NO_AUTHORITIES
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticationToken);
        SecurityContextHolder.setContext(securityContext);
    }
}
