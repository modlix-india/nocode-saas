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
