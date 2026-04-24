package com.strangequark.trasck.config;

import com.strangequark.trasck.identity.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.WebUtils;

class CookieAuthenticatedUnsafeMethodMatcher implements RequestMatcher {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> CSRF_EXEMPT_UNSAFE_PATHS = Set.of("/api/v1/auth/logout");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        if (CSRF_EXEMPT_UNSAFE_PATHS.contains(pathWithinApplication(request))) {
            return false;
        }
        return WebUtils.getCookie(request, JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE) != null;
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
