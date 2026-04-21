package com.strangequark.trasck.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final List<String> allowedWildcardHosts;
    private final List<CidrRange> allowedCidrs;

    public OutboundUrlPolicy(@Value("${trasck.security.outbound-url.allowed-hosts:}") String allowedHosts) {
        List<String> entries = Arrays.stream((allowedHosts == null ? "" : allowedHosts).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        this.allowedHosts = entries.stream()
                .filter(entry -> !entry.contains("/") && !entry.startsWith("*."))
                .collect(Collectors.toUnmodifiableSet());
        this.allowedWildcardHosts = entries.stream()
                .filter(entry -> entry.startsWith("*."))
                .toList();
        this.allowedCidrs = entries.stream()
                .filter(entry -> entry.contains("/"))
                .map(CidrRange::parse)
                .toList();
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
            InetAddress literalAddress = address(host, fieldName);
            if (isAllowedAddress(literalAddress)) {
                return;
            }
            validateAddress(literalAddress, fieldName);
        }
        if (resolveHost) {
            InetAddress[] addresses = addresses(host, fieldName);
            for (InetAddress resolvedAddress : addresses) {
                if (isAllowedAddress(resolvedAddress)) {
                    continue;
                }
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
                || (port == -1 && (allowedHosts.contains(host + ":80") || allowedHosts.contains(host + ":443")))
                || isAllowedWildcardHost(host, port);
    }

    private boolean isAllowedWildcardHost(String host, int port) {
        List<String> candidates = new ArrayList<>();
        candidates.add(host);
        candidates.add(host + ":" + port);
        if (port == -1) {
            candidates.add(host + ":80");
            candidates.add(host + ":443");
        }
        return candidates.stream().anyMatch(this::matchesWildcard);
    }

    private boolean matchesWildcard(String candidate) {
        for (String wildcard : allowedWildcardHosts) {
            int separator = wildcard.indexOf('.');
            if (separator < 0 || separator + 1 >= wildcard.length()) {
                continue;
            }
            String suffix = wildcard.substring(separator + 1);
            if (candidate.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedAddress(InetAddress address) {
        return allowedCidrs.stream().anyMatch(cidr -> cidr.contains(address));
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

    private record CidrRange(byte[] network, int prefixBits) {

        static CidrRange parse(String value) {
            int separator = value.indexOf('/');
            if (separator < 1 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Invalid outbound URL CIDR allowlist entry: " + value);
            }
            try {
                InetAddress networkAddress = InetAddress.getByName(value.substring(0, separator));
                int prefix = Integer.parseInt(value.substring(separator + 1));
                int maxBits = networkAddress.getAddress().length * 8;
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException("Invalid outbound URL CIDR prefix: " + value);
                }
                return new CidrRange(networkAddress.getAddress(), prefix);
            } catch (UnknownHostException | NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid outbound URL CIDR allowlist entry: " + value, ex);
            }
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (Byte.toUnsignedInt(candidate[fullBytes]) & mask)
                    == (Byte.toUnsignedInt(network[fullBytes]) & mask);
        }
    }
}
