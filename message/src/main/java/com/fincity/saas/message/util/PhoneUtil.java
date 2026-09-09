package com.fincity.saas.message.util;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public class PhoneUtil {

    private static final String DEFAULT_REGION = "IN";
    private static final String UNKNOWN_REGION = "ZZ";
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();
    private static final int DEFAULT_CALLING_CODE = PHONE_NUMBER_UTIL.getCountryCodeForRegion(DEFAULT_REGION);

    private PhoneUtil() {
        throw new IllegalStateException("PhoneUtil is a utility class");
    }

    public static String getDefaultRegion() {
        return DEFAULT_REGION;
    }

    public static int getDefaultCallingCode() {
        return DEFAULT_CALLING_CODE;
    }

    /**
     * Whether two values name the same phone.
     *
     * <p>Normalises through {@link #parse} first, so {@code +910000000001} and {@code 0000000001}
     * are recognised as one number rather than two — the provider echoes a number back in whatever
     * shape it stores it, and a plain string compare reports a change on every re-provision.
     *
     * <p>Falls back to comparing the last ten digits when either side will not parse, and to an
     * exact compare when there are fewer than ten. That covers what {@code parse} rejects: a SIP
     * URI, an extension, a short code.
     *
     * <p><b>The fallback is region-blind, and that is a scoping assumption rather than an
     * oversight.</b> Comparing the last ten digits treats two numbers as one whenever their tails
     * match, which is only safe where numbers share a country — true of this integration, which is
     * scoped to one regional provider account. It runs only when {@link #parse} rejects a side,
     * and {@code parse} itself defaults to {@code DEFAULT_REGION}. A caller spanning countries
     * should compare parsed numbers and treat an unparseable one as unequal.
     *
     * <p>Errs toward reporting a <em>difference</em>. A false difference costs one redundant write
     * of the same values; a false match means an operator's new number silently never takes effect,
     * which is the defect this exists to prevent.
     */
    public static boolean isSameNumber(String left, String right) {

        PhoneNumber parsedLeft = parse(left);
        PhoneNumber parsedRight = parse(right);

        if (parsedLeft != null && parsedRight != null)
            return StringUtil.safeEquals(parsedLeft.getNumber(), parsedRight.getNumber());

        String digitsLeft = digitsOf(left);
        String digitsRight = digitsOf(right);

        if (digitsLeft.length() < 10 || digitsRight.length() < 10) return digitsLeft.equals(digitsRight);

        return digitsLeft.substring(digitsLeft.length() - 10).equals(digitsRight.substring(digitsRight.length() - 10));
    }

    private static String digitsOf(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static PhoneNumber parse(String phoneNumber) {
        return parse(null, phoneNumber);
    }

    public static PhoneNumber parse(Integer countryCallingCode, String phoneNumber) {
        if (phoneNumber == null) return null;

        String region = determineRegion(countryCallingCode, phoneNumber);

        try {
            Phonenumber.PhoneNumber parsedNumber = PHONE_NUMBER_UTIL.parse(phoneNumber, region);

            validatePhoneNumber(parsedNumber, phoneNumber, region);

            return new PhoneNumber()
                    .setCountryCode(parsedNumber.getCountryCode())
                    .setNumber(PHONE_NUMBER_UTIL.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164));

        } catch (NumberParseException e) {
            return null;
        }
    }

    private static String determineRegion(Integer countryCallingCode, String phoneNumber) {
        return countryCallingCode != null
                ? getRegionFromCountryCode(countryCallingCode)
                : detectRegionFromPhoneNumber(phoneNumber);
    }

    private static String getRegionFromCountryCode(int countryCallingCode) {
        String region = PHONE_NUMBER_UTIL.getRegionCodeForCountryCode(countryCallingCode);
        return isValidRegion(region) ? region : DEFAULT_REGION;
    }

    private static String detectRegionFromPhoneNumber(String phoneNumber) {
        try {
            Phonenumber.PhoneNumber tentativeParse = PHONE_NUMBER_UTIL.parse(phoneNumber, UNKNOWN_REGION);

            if (tentativeParse.hasCountryCode()) {
                String detectedRegion = PHONE_NUMBER_UTIL.getRegionCodeForCountryCode(tentativeParse.getCountryCode());

                if (isValidRegion(detectedRegion)) return detectedRegion;
            }
        } catch (NumberParseException e) {
            // Fall through to return the default region
        }

        return DEFAULT_REGION;
    }

    private static boolean isValidRegion(String region) {
        return !StringUtil.safeIsBlank(region) && !UNKNOWN_REGION.equals(region);
    }

    private static void validatePhoneNumber(Phonenumber.PhoneNumber parsedNumber, String originalNumber, String region)
            throws NumberParseException {
        if (!PHONE_NUMBER_UTIL.isValidNumber(parsedNumber))
            throw new NumberParseException(
                    NumberParseException.ErrorType.NOT_A_NUMBER,
                    StringFormatter.format("Phone Number $ is not valid", originalNumber));

        if (!PHONE_NUMBER_UTIL.isValidNumberForRegion(parsedNumber, region))
            throw new NumberParseException(
                    NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                    StringFormatter.format("Phone Number $ is not valid for $", originalNumber, region));
    }
}
