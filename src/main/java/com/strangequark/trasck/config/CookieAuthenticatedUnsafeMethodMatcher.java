package com.strangequark.trasck.config;

import com.strangequark.trasck.identity.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.WebUtils;

class CookieAuthenticatedUnsafeMethodMatcher implements RequestMatcher {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        return WebUtils.getCookie(request, JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE) != null;
    }
}
