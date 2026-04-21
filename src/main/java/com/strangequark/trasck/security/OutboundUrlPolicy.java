package com.strangequark.trasck.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OutboundUrlPolicy {

    private final Set<String> allowedHosts;

    public OutboundUrlPolicy(@Value("${trasck.security.outbound-url.allowed-hosts:}") String allowedHosts) {
        this.allowedHosts = Arrays.stream((allowedHosts == null ? "" : allowedHosts).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validateHttpUrl(String url, String fieldName) {
        validateHttpUri(parse(url, fieldName), fieldName, false);
    }

    public void validateResolvedHttpUri(URI uri, String fieldName) {
        validateHttpUri(uri, fieldName, true);
    }

    private void validateHttpUri(URI uri, String fieldName, boolean resolveHost) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw badRequest(fieldName + " must include scheme and host");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme)) {
            throw badRequest(fieldName + " must use http or https");
        }
        String host = normalizedHost(uri.getHost());
        if (isAllowedHost(host, uri.getPort())) {
            return;
        }
        rejectLocalHostnames(host, fieldName);
        if (isIpLiteral(host)) {
            validateAddress(address(host, fieldName), fieldName);
        }
        if (resolveHost) {
            InetAddress[] addresses = addresses(host, fieldName);
            for (InetAddress resolvedAddress : addresses) {
                validateAddress(resolvedAddress, fieldName);
            }
        }
    }

    private URI parse(String url, String fieldName) {
        try {
            return URI.create(required(url, fieldName));
        } catch (IllegalArgumentException ex) {
            throw badRequest(fieldName + " must be a valid URI");
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " is required");
        }
        return value.trim();
    }

    private boolean isAllowedHost(String host, int port) {
        return allowedHosts.contains(host)
                || allowedHosts.contains(host + ":" + port)
                || (port == -1 && (allowedHosts.contains(host + ":80") || allowedHosts.contains(host + ":443")));
    }

    private void rejectLocalHostnames(String host, String fieldName) {
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            throw badRequest(fieldName + " host is blocked by outbound URL policy");
        }
    }

    private void validateAddress(InetAddress address, String fieldName) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isUniqueLocalIpv6(address)) {
            throw badRequest(fieldName + " host resolves to a blocked private or local network address");
        }
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && Byte.toUnsignedInt(bytes[0]) == 100
                && Byte.toUnsignedInt(bytes[1]) >= 64
                && Byte.toUnsignedInt(bytes[1]) <= 127;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    private InetAddress address(String host, String fieldName) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            throw badRequest(fieldName + " host cannot be resolved");
        }
    }

    private InetAddress[] addresses(String host, String fieldName) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw badRequest(fieldName + " host cannot be resolved");
        }
    }

    private String normalizedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
