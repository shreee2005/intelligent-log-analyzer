package com.loganalyzer.ml.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PiiRedactionService {

    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");

    // JWT pattern (three base64 parts separated by dots)
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    // API Key patterns (common formats)
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|apikey|access[_-]?token|secret[_-]?key)[\"'\\s:=]+([A-Za-z0-9._-]{20,})");

    // AWS Access Key
    private static final Pattern AWS_ACCESS_KEY_PATTERN = Pattern.compile(
            "\\bAKIA[0-9A-Z]{16}\\b");

    // AWS Secret Key
    private static final Pattern AWS_SECRET_KEY_PATTERN = Pattern.compile(
            "(?i)aws[_-]?secret[_-]?access[_-]?key[\"'\\s:=]+([A-Za-z0-9/+=]{40})");

    // Generic password/secret
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|credential)[\"'\\s:=]+([^\\s\"']{8,})");

    // Bearer token
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)Bearer\\s+([A-Za-z0-9._-]{20,})");

    // IPv4 address
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

    // IPv6 address (simplified)
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b");

    // Credit card (basic Luhn-agnostic pattern)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,16}\\b");

    // UUID
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    // Authorization header
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization|x-api-key|x-auth-token)[\"'\\s:=]+([^\\s\"']{10,})");

    // Database connection string
    private static final Pattern DB_CONNECTION_PATTERN = Pattern.compile(
            "(?i)(jdbc:|mongodb://|redis://|postgres://|mysql://)[^\\s\"']+");

    private static final String REDACTED_EMAIL = "[EMAIL_REDACTED]";
    private static final String REDACTED_JWT = "[JWT_REDACTED]";
    private static final String REDACTED_API_KEY = "[API_KEY_REDACTED]";
    private static final String REDACTED_AWS_KEY = "[AWS_KEY_REDACTED]";
    private static final String REDACTED_PASSWORD = "[PASSWORD_REDACTED]";
    private static final String REDACTED_BEARER = "[BEARER_TOKEN_REDACTED]";
    private static final String REDACTED_IP = "[IP_REDACTED]";
    private static final String REDACTED_CC = "[CREDIT_CARD_REDACTED]";
    private static final String REDACTED_UUID = "[UUID_REDACTED]";
    private static final String REDACTED_AUTH = "[AUTH_REDACTED]";
    private static final String REDACTED_DB = "[DB_CONNECTION_REDACTED]";

    public String redact(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;

        // Order matters - more specific patterns first
        result = redactPattern(result, JWT_PATTERN, REDACTED_JWT);
        result = redactPattern(result, AWS_ACCESS_KEY_PATTERN, REDACTED_AWS_KEY);
        result = redactPatternWithGroups(result, API_KEY_PATTERN, 2, REDACTED_API_KEY);
        result = redactPatternWithGroups(result, PASSWORD_PATTERN, 2, REDACTED_PASSWORD);
        result = redactPatternWithGroups(result, BEARER_TOKEN_PATTERN, 1, REDACTED_BEARER);
        result = redactPatternWithGroups(result, AUTH_HEADER_PATTERN, 2, REDACTED_AUTH);
        result = redactPattern(result, CREDIT_CARD_PATTERN, REDACTED_CC);
        result = redactPattern(result, EMAIL_PATTERN, REDACTED_EMAIL);
        result = redactPattern(result, IPV4_PATTERN, REDACTED_IP);
        result = redactPattern(result, IPV6_PATTERN, REDACTED_IP);
        result = redactPattern(result, UUID_PATTERN, REDACTED_UUID);
        result = redactPattern(result, DB_CONNECTION_PATTERN, REDACTED_DB);

        return result;
    }

    public String redactLogs(java.util.List<String> logLines) {
        if (logLines == null || logLines.isEmpty()) {
            return "";
        }
        return logLines.stream()
                .map(this::redact)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String redactPattern(String input, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll(replacement);
    }

    private String redactPatternWithGroups(String input, Pattern pattern, int groupIndex, String replacement) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(0).replace(matcher.group(groupIndex), replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Redact sensitive fields in a JSON string while preserving structure.
     * Useful for redacting log payloads that are JSON.
     */
    public String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        // First redact common patterns
        String result = redact(json);

        // Then handle JSON-specific sensitive fields
        result = redactJsonField(result, "password");
        result = redactJsonField(result, "secret");
        result = redactJsonField(result, "apiKey");
        result = redactJsonField(result, "accessToken");
        result = redactJsonField(result, "refreshToken");
        result = redactJsonField(result, "authorization");
        result = redactJsonField(result, "creditCard");
        result = redactJsonField(result, "ssn");
        result = redactJsonField(result, "socialSecurity");
        result = redactJsonField(result, "privateKey");

        return result;
    }

    private String redactJsonField(String json, String fieldName) {
        // Pattern to match "fieldName": "value" or 'fieldName': 'value'
        String pattern = String.format(
                "(?i)(\"%s\"\\s*:\\s*\")([^\"]+)(\")|('%s'\\s*:\\s*')([^']+)(')",
                fieldName, fieldName);
        return json.replaceAll(pattern, "$1[REDACTED]$3$4[REDACTED]$6");
    }
}