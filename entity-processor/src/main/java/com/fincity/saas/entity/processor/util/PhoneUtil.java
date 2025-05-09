package com.fincity.saas.entity.processor.util;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public class PhoneUtil {

    private static final String DEFAULT_REGION = "IN";
    private static final String UNKNOWN_REGION = "ZZ";
    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    private PhoneUtil() {
        // To be used as a static Phone Number utilities class
        throw new IllegalStateException("PhoneUtil class");
    }

    public static String getDefaultRegion() {
        return DEFAULT_REGION;
    }

    public static Integer getDefaultCallingCode() {
        return phoneNumberUtil.getCountryCodeForRegion(DEFAULT_REGION);
    }

    public static PhoneNumber parse(String phoneNumber) {
        return parse(null, phoneNumber);
    }

    public static PhoneNumber parse(Integer countryCallingCode, String phoneNumber) {

        String region = countryCallingCode != null
                ? phoneNumberUtil.getRegionCodeForCountryCode(countryCallingCode)
                : DEFAULT_REGION;

        if (StringUtil.safeIsBlank(region) || region.equals(UNKNOWN_REGION)) region = DEFAULT_REGION;

        try {
            Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(phoneNumber, region);

            if (!phoneNumberUtil.isValidNumber(parsedNumber))
                throw new NumberParseException(
                        NumberParseException.ErrorType.NOT_A_NUMBER,
                        StringFormatter.format("Phone Number $ is not valid", phoneNumber));

            if (!phoneNumberUtil.isValidNumberForRegion(parsedNumber, region))
                throw new NumberParseException(
                        NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                        StringFormatter.format("Phone Number $ is not valid for $", phoneNumber, region));

            return PhoneNumber.of(
                    parsedNumber.getCountryCode(),
                    phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException e) {
            return null;
        }
    }
}
