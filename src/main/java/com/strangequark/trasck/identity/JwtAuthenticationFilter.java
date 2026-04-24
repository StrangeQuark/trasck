package com.strangequark.trasck.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "trasck_access_token";

    private final JwtTokenService jwtTokenService;
    private final ApiTokenService apiTokenService;
    private final TrasckUserDetailsService userDetailsService;
    private final boolean cookieSecure;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            ApiTokenService apiTokenService,
            TrasckUserDetailsService userDetailsService,
            @Value("${trasck.security.cookie-secure:false}") boolean cookieSecure
    ) {
        this.jwtTokenService = jwtTokenService;
        this.apiTokenService = apiTokenService;
        this.userDetailsService = userDetailsService;
        this.cookieSecure = cookieSecure;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ResolvedToken token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                TrasckPrincipal principal;
                if (apiTokenService.supports(token.value())) {
                    principal = userDetailsService.loadUserByApiToken(apiTokenService.authenticateBearerToken(token.value()));
                } else {
                    UUID userId = jwtTokenService.parseUserId(token.value());
                    principal = userDetailsService.loadUserById(userId);
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BadCredentialsException | UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
                if (token.source() == TokenSource.COOKIE) {
                    clearAccessTokenCookie(response);
                    filterChain.doFilter(request, response);
                    return;
                }
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private ResolvedToken resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return new ResolvedToken(authorization.substring(7).trim(), TokenSource.BEARER);
        }
        Cookie cookie = WebUtils.getCookie(request, ACCESS_TOKEN_COOKIE);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return new ResolvedToken(cookie.getValue(), TokenSource.COOKIE);
        }
        return null;
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private enum TokenSource {
        BEARER,
        COOKIE
    }

    private record ResolvedToken(String value, TokenSource source) {
    }
}
