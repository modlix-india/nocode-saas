package com.fincity.saas.entity.processor.util;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberToTimeZonesMapper;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class PhoneUtil {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PhoneUtil.class);

    private static final String DEFAULT_REGION = "IN";
    private static final String UNKNOWN_REGION = "ZZ";
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();
    private static final int DEFAULT_CALLING_CODE = PHONE_NUMBER_UTIL.getCountryCodeForRegion(DEFAULT_REGION);
    private static final PhoneNumberToTimeZonesMapper TIME_ZONES = PhoneNumberToTimeZonesMapper.getInstance();

    private PhoneUtil() {
        throw new IllegalStateException("PhoneUtil is a utility class");
    }

    public static String getDefaultRegion() {
        return DEFAULT_REGION;
    }

    public static int getDefaultCallingCode() {
        return DEFAULT_CALLING_CODE;
    }

    public static PhoneNumber parse(String phoneNumber) {
        return parse(null, phoneNumber);
    }

    /**
     * The local clocks a number could be on, most likely first.
     *
     * <p>Quiet hours are about not waking somebody up, so they have to be judged where that person
     * actually is, and a phone number is the only thing we hold that says where that is. The dial
     * code on its own is not enough: +1 spans six zones and +7 spans eleven, so a New Jersey and a
     * California lead would share one answer and one of them gets written to three hours off. The
     * subscriber digits are what separate them, which is why this takes the whole number.
     *
     * <p>Returns several zones when the number genuinely does not pin one down, and the caller is
     * expected to treat every one of them as possible rather than picking the first. Returns empty
     * when nothing can be said - an unparseable number, or a non-geographic one such as a satellite
     * or premium-rate range - and empty means "no opinion", never "UTC". Defaulting a silent failure
     * to UTC would put Indian quiet hours five and a half hours out and look like it worked.
     *
     * <p>The ids come out as the mapper's data spells them, which is tzdb's older naming: India
     * arrives as {@code Asia/Calcutta} rather than {@code Asia/Kolkata}. Those are links to one set
     * of rules, so every offset and transition is identical and nothing here depends on the spelling.
     * It is left alone rather than mapped, because the alias list would be a table to maintain in
     * exchange for a nicer label.
     */
    public static List<ZoneId> zonesOf(Integer countryCallingCode, String phoneNumber) {

        Phonenumber.PhoneNumber parsed = parseLeniently(countryCallingCode, phoneNumber);
        if (parsed == null) return List.of();

        List<ZoneId> zones = new ArrayList<>();

        for (String id : TIME_ZONES.getTimeZonesForNumber(parsed)) {
            // The mapper reports "not known" as a zone id of its own rather than an empty list, and
            // ZoneId.of throws on it. Skipping leaves the list empty, which is the honest answer.
            if (id == null || id.equals(PhoneNumberToTimeZonesMapper.getUnknownTimeZone())) continue;

            try {
                zones.add(ZoneId.of(id));
            } catch (Exception e) {
                // A zone this JDK's tzdb does not carry. Rare, and the remaining candidates are
                // still usable, so it is dropped rather than failing the whole lookup.
                logger.warn("Ignoring time zone '{}' from the phone number mapper: this JDK does not know it.", id, e);
            }
        }

        return zones;
    }

    /**
     * Parses for classification rather than for storage.
     *
     * <p>Deliberately does not run {@link #validatePhoneNumber}. That check exists to stop a bad
     * number being saved against a deal; here the number is already saved and the question is only
     * which country it belongs to. A number that fails validation - a wrong length, a range
     * libphonenumber's metadata has not caught up with - still carries a perfectly readable country
     * code, and refusing to read it would drop the lead back to the default zone for no gain.
     */
    private static Phonenumber.PhoneNumber parseLeniently(Integer countryCallingCode, String phoneNumber) {

        if (StringUtil.safeIsBlank(phoneNumber)) return null;

        String trimmed = phoneNumber.trim();

        // With a leading "+" the number carries its own country code and the region hint is ignored,
        // which is what we want: the stored dial code and the number can disagree, and the number is
        // the one that was actually messaged.
        String region;
        if (trimmed.startsWith("+")) region = UNKNOWN_REGION;
        else if (countryCallingCode != null) region = getRegionFromCountryCode(countryCallingCode);
        else region = DEFAULT_REGION;

        try {
            return PHONE_NUMBER_UTIL.parse(trimmed, region);
        } catch (NumberParseException e) {
            return null;
        }
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
