package com.fincity.saas.entity.processor.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing helpers for Meta CAPI / Google enhanced-conversions user_data
 * fields. Follows the Meta CAPI spec (doc Part 5.1 / 5.2):
 *
 * <ul>
 *   <li><b>email</b> — lowercase + trim whitespace, then SHA-256</li>
 *   <li><b>phone</b> — digits-only (strip {@code +}, spaces, dashes), then SHA-256</li>
 *   <li><b>first/last name</b> — lowercase + strip whitespace, then SHA-256</li>
 * </ul>
 *
 * <p>The doc's <b>Part 6.1 fix</b> calls out: phone MUST be digit-stripped BEFORE
 * SHA-256, otherwise match fails on Meta's side.
 */
public final class ConversionsApiHashUtil {

    private ConversionsApiHashUtil() {}

    public static String hashEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return sha256(email.trim().toLowerCase());
    }

    /** Strip everything except digits, then SHA-256. Empty input → null. */
    public static String hashPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        return sha256(digits);
    }

    /** Strip whitespace + lowercase, then SHA-256. Used for first/last name (fn / ln). */
    public static String hashName(String name) {
        if (name == null || name.isBlank()) return null;
        return sha256(name.replaceAll("\\s+", "").toLowerCase());
    }

    public static String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
