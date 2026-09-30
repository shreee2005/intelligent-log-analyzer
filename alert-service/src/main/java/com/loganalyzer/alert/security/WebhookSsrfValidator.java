package com.loganalyzer.alert.security;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@Component
public class WebhookSsrfValidator {

    private static final List<String> BLOCKED_IP_RANGES = Arrays.asList(
            "127.0.0.0/8",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            "::1/128",
            "fe80::/10",
            "fc00::/7"
    );

    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
            "localhost",
            "localhost.localdomain",
            "127.0.0.1",
            "::1",
            "0.0.0.0"
    );

    public ValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return ValidationResult.invalid("URL is required");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return ValidationResult.invalid("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return ValidationResult.invalid("Only HTTP/HTTPS URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null) {
            return ValidationResult.invalid("URL must have a host");
        }

        if (BLOCKED_HOSTS.stream().anyMatch(h -> h.equalsIgnoreCase(host))) {
            return ValidationResult.invalid("Host is not allowed");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return ValidationResult.invalid("Unable to resolve host: " + e.getMessage());
        }

        for (InetAddress address : addresses) {
            String ip = address.getHostAddress();
            if (isBlockedIp(ip)) {
                return ValidationResult.invalid("Resolved IP is in blocked range: " + ip);
            }
        }

        return ValidationResult.valid();
    }

    private boolean isBlockedIp(String ip) {
        // Check loopback
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.startsWith("127.")) {
            return true;
        }

        // Check private ranges
        // 10.0.0.0/8
        if (ip.startsWith("10.")) {
            return true;
        }

        // 172.16.0.0/12
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 192.168.0.0/16
        if (ip.startsWith("192.168.")) {
            return true;
        }

        // 169.254.0.0/16 (link-local)
        if (ip.startsWith("169.254.")) {
            return true;
        }

        // IPv6 loopback
        if (ip.equals("::1")) {
            return true;
        }

        // IPv6 link-local (fe80::/10)
        if (ip.startsWith("fe80:")) {
            return true;
        }

        // IPv6 unique local (fc00::/7)
        if (ip.startsWith("fc") || ip.startsWith("fd")) {
            return true;
        }

        return false;
    }

    public record ValidationResult(boolean isValid, String error) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
    }
}