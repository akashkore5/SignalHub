package com.khetisetu.event.notifications.constants;

/**
 * This class contains regex patterns used for validating various inputs in the application.
 * It includes patterns for MPIN, password, and phone number formats.
 */
public class Constants {

    public static final long OTP_EXPIRATION_MINUTES = 5;
    public static final long REFRESH_TOKEN_EXPIRATION_MS = 2 * 24 * 60 * 60 * 1000L; // 2 days
    public static final String OTP_PREFIX = "otp:";
    public static final String OTP = "otp";
    public static final String OTP_SMS = "otp_sms";
    public static final String OTP_SENT_PHONE = "Otp sent to phone: ";
    public static final String OTP_SENT_EMAIL = "Otp sent to email: ";
    public static final String OTP_EMAIL = "otp_email";
    public static final String RATE_LIMIT_OTP = "ratelimit:otp:";

    // Common attribute keys
    public static final String STATUS = "status";
    public static final String USER_ID = "userId";
    public static final String EVENT_ID = "eventId";
    public static final String WORKER = "WORKER";
    public static final String OWNER_ID = "ownerId";
    public static final String ADMIN = "ADMIN";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String RESOURCE_TYPE = "type";
    public static final String ACTION = "action";


    public static final double EARTH_RADIUS_KM = 6371.0; // Earth radius for distance calculation
    public static final double KM_TO_METER = 1000.0; // Earth radius for distance calculation
    public static final String MPIN_REGEX = "^[0-9]{4,6}$"; // 4 to 6 digits
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"; // At least 8 characters, 1 uppercase, 1 lowercase, 1 digit, 1 special character
    public static final String PHONE_REGEX = "^(\\+\\d{1,3}[- ]?)?\\d{10}$"; // Optional country code, followed by 10 digits
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"; // Basic email validation
    public static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$"; // YYYY-MM-DD format
    public static final String TIME_REGEX = "^([01]?\\d|2[0-3]):[0-5]\\d$"; // HH:mm format
    public static final String DATE_TIME_REGEX = "^\\d{4}-\\d{2}-\\d{2}T([01]?\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?(Z|[+-][01]?\\d:[0-5]\\d)?$"; // ISO 8601 format
    public static final String BOOKING_PREFIX = "BKG";
    public static final String WORKER_PREFIX = "WKF";
    public static final String TRANSACTION_PREFIX = "TXN";
    public static final String NOTIFICATION_PREFIX = "NTF";
    public static final String EQUIPMENT_PREFIX = "EQP";
    public static final String USER_PREFIX = "USR";
    public static final String ADMIN_PREFIX = "ADM";
    public static final String ECOMMERCE_PREFIX = "ECM";
    public static final String PRODUCT_PREFIX = "PRD";
    public static final String PRICING_RULE = "PPR";
    public static final String ORDER_PREFIX = "ORD";
    public static final String PAYMENT_PREFIX = "PMT";
    public static final String CATEGORY_PREFIX = "CAT";
    public static final String SUBSCRIPTION_PREFIX = "SUB";
    public static final String CART_PREFIX = "CRT";
    public static final String CART_ITEM_PREFIX = "CRT";
    public static final String COUPON_PREFIX = "KHT";
    public static final String JOB_PREFIX = "JOB";
    public static final String EMAIL_SENDER_PREFIX = "EML";
    public static final String JOB_APPLICATION_PREFIX = "JAP";
    public static final String JOB_REVIEW_PREFIX = "JRV";
    public static final String EQUIPMENT_REVIEW_PREFIX = "ERV";
    public static final String PRODUCT_REVIEW_PREFIX = "PRV";
    public static final String SUPPORT_PREFIX = "SUP";
    public static final String SUPPORT_OPEN = "OPEN";
    public static final String SUPPORT_ESCALATED = "ESCALATED";
    public static final String SUPPORT_RESOLVED = "RESOLVED";
    public static final String CHAT_PREFIX = "CHT";
    public static final String ADDRESS_PREFIX = "ADDRESS";
    public static final String GSTIN_REGEX =
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$";
    public static final String CREATE = "_CREATE";
    public static final String PAYMENT = "PAYMENT";
    public static final String PRODUCT = "PRODUCT";
    public static final String UPDATE = "_UPDATE";
    public static final String CONFIRM = "_CONFIRM";
    public static final String DELETE = "_DELETE";
    public static final String RETRIEVE = "_RETRIEVE";
    public static final String APPLY = "APPLY";
    public static final String BOOKING = "BOOKING";
    public static final String ECOM = "ECOM";
    public static final String JOB = "JOB";
    public static final String USER = "USER";
    public static final String REVIEW = "REVIEW";
    public static final String SHIPPING_STD = "ship_std";
    public static final String SHIPPING_EXPRESS = "ship_exp";
    public static final String STANDARD_SHIPPING = "Standard Shipping";
    public static final String EXPRESS_SHIPPING = "Express Shipping";
}
