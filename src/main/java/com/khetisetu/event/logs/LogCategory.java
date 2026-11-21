package com.khetisetu.event.logs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central taxonomy of log categories. Every log entry is classified into exactly one
 * category, either explicitly by the producer or derived here from its action prefix.
 *
 * <p>The prefix mapping also lets queries match legacy logs written before the
 * {@code category} field existed (by translating a category filter into an action
 * prefix regex).</p>
 */
public final class LogCategory {

    public static final String USER = "USER";
    public static final String PRODUCT = "PRODUCT";
    public static final String ORDER = "ORDER";
    public static final String BOOKING = "BOOKING";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String JOB = "JOB";
    public static final String PAYMENT = "PAYMENT";
    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String SUPPLIER = "SUPPLIER";
    public static final String SYSTEM = "SYSTEM";

    /**
     * Ordered category → action-prefix mapping. Order matters: more specific
     * prefixes (e.g. AUTH_TOKEN → PAYMENT) must be checked before broader ones
     * (AUTH_ → USER).
     */
    private static final Map<String, List<String>> PREFIXES = new LinkedHashMap<>();

    static {
        PREFIXES.put(PAYMENT, List.of("PAYMENT", "REFUND", "SUBSCRIPTION_", "REDEMPTION_", "AUTH_TOKEN", "TRANSACTION_"));
        PREFIXES.put(USER, List.of("USER_", "AUTH_", "OTP_", "LOGIN", "REGISTRATION", "ADMIN_"));
        PREFIXES.put(PRODUCT, List.of("ECOM_PRODUCT_", "PRODUCT_"));
        PREFIXES.put(ORDER, List.of("ECOM_ORDER_", "ORDER_", "CART_", "COUPON_"));
        PREFIXES.put(BOOKING, List.of("BOOKING_"));
        PREFIXES.put(EQUIPMENT, List.of("EQUIPMENT_"));
        PREFIXES.put(JOB, List.of("JOB_"));
        PREFIXES.put(SUPPLIER, List.of("SUPPLIER_"));
        PREFIXES.put(NOTIFICATION, List.of("PUSHNOTIFICATION", "EMAILNOTIFICATION", "SMSNOTIFICATION",
                "NOTIFICATION_", "EMAIL_", "PUSH_", "SMS_"));
    }

    private LogCategory() {
    }

    /**
     * Derives a category from an action string; falls back to {@link #SYSTEM}.
     */
    public static String fromAction(String action) {
        if (action == null) return SYSTEM;
        String upper = action.toUpperCase();
        for (var entry : PREFIXES.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (upper.startsWith(prefix)) {
                    return entry.getKey();
                }
            }
        }
        return SYSTEM;
    }

    /**
     * Returns the action prefixes belonging to a category (empty for SYSTEM/unknown).
     * Used to build fallback action-prefix queries for legacy logs without a category field.
     */
    public static List<String> prefixesOf(String category) {
        return PREFIXES.getOrDefault(category == null ? "" : category.toUpperCase(), List.of());
    }

    public static List<String> all() {
        return List.of(USER, PRODUCT, ORDER, BOOKING, EQUIPMENT, JOB, PAYMENT, NOTIFICATION, SUPPLIER, SYSTEM);
    }
}
