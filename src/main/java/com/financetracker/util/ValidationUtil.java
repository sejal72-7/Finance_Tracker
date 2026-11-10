package com.financetracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Input validation and parsing helpers shared across servlets.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private ValidationUtil() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 150 && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidTransactionType(String type) {
        return "INCOME".equals(type) || "EXPENSE".equals(type);
    }

    public static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String upper = type.trim().toUpperCase();
        return isValidTransactionType(upper) ? upper : null;
    }

    public static boolean isValidHexColor(String color) {
        return color != null && HEX_COLOR_PATTERN.matcher(color.trim()).matches();
    }

    public static BigDecimal parsePositiveAmount(Object rawAmount) {
        if (rawAmount == null) {
            return null;
        }
        try {
            String text = rawAmount.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            BigDecimal amount = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
                return null;
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    public static LocalDate parseDate(String rawDate) {
        String trimmed = trimToNull(rawDate);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Integer parsePositiveInt(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            int val = Integer.parseInt(trimmed);
            return val > 0 ? val : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

