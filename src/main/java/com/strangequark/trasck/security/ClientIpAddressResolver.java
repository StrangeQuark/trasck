package com.strangequark.trasck.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpAddressResolver {

    private final boolean trustForwardedFor;

    public ClientIpAddressResolver(@Value("${trasck.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String remoteAddress(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = trustForwardedFor ? request.getHeader("X-Forwarded-For") : null;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
