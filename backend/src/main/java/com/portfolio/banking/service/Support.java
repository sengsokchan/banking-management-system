package com.portfolio.banking.service;

import com.portfolio.banking.web.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class Support {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Support() {
    }

    public static String contains(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static BigDecimal money(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("AMOUNT_REQUIRED", "Amount is required.");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("AMOUNT_SCALE", "Amounts use at most two decimal places.");
        }
    }

    public static BigDecimal positiveMoney(BigDecimal amount) {
        BigDecimal scaled = money(amount);
        if (scaled.compareTo(ZERO) <= 0) {
            throw ApiException.badRequest("AMOUNT_NOT_POSITIVE", "Amount must be greater than zero.");
        }
        return scaled;
    }

    public static void checkPassword(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 72
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)) {
            throw ApiException.badRequest(
                    "PASSWORD_POLICY",
                    "Password must be 12 to 72 characters and include an uppercase letter, a lowercase letter, and a digit."
            );
        }
    }

    public static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String requireTrim(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            throw ApiException.badRequest("REQUIRED", "A required value is missing.");
        }
        return trimmed;
    }

    public static String sha256(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    public static String requestHash(String type, BigDecimal amount, UUID sourceId, UUID destinationId, String description) {
        String canonical = String.join("|",
                type,
                amount.toPlainString(),
                sourceId == null ? "-" : sourceId.toString(),
                destinationId == null ? "-" : destinationId.toString(),
                description == null ? "" : description);
        return sha256(canonical);
    }

    public static String idempotencyKey(String header, String body) {
        String key = header != null && !header.isBlank() ? header : body;
        if (key == null || key.isBlank()) {
            return null;
        }
        key = key.trim();
        if (!key.matches("[A-Za-z0-9._:-]{8,80}")) {
            throw ApiException.badRequest(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key must be 8 to 80 characters and use letters, numbers, dots, underscores, colons, or hyphens."
            );
        }
        return key;
    }
}
