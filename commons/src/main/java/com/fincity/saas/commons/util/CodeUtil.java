package com.fincity.saas.commons.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

public class CodeUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Data
    @Accessors(chain = true)
    public static class CodeGenerationConfiguration {

        private int length = 8;
        private boolean numeric = true;
        private boolean lowercase = false;
        private boolean uppercase = false;
        private boolean specialChars = false;
        private int[] separators;
        private String separator = "-";
    }

    private static final String NUMBERS = "0123456789";
    private static final String UPPERCASE = "BCDFGHJKLMNPQRSTVWXZ";
    private static final String LOWERCASE = "bcdfghjklmnpqrstvwxz";
    private static final String SPECIAL_CHARS = "!@#$";

    public static String generate() {
        return generate(new CodeGenerationConfiguration());
    }

    public static String generate(CodeGenerationConfiguration config) {

        StringBuilder sb = new StringBuilder(config.getLength()
                + (config.separators == null ? 0 : config.separators.length) * config.separator.length());

        List<String> charSets = new ArrayList<>();

        if (config.numeric)
            charSets.add(NUMBERS);
        if (config.uppercase)
            charSets.add(UPPERCASE);
        if (config.lowercase)
            charSets.add(LOWERCASE);
        if (config.specialChars)
            charSets.add(SPECIAL_CHARS);

        while (sb.length() != config.length) {
            int set = 0;
            if (charSets.size() > 1)
                set = SECURE_RANDOM.nextInt(charSets.size());
            String charSet = charSets.get(set);
            sb.append(charSet.charAt(SECURE_RANDOM.nextInt(charSet.length())));
        }

        if (config.separators != null && config.separators.length != 0) {
            for (int i = 0; i < config.separators.length; i++) {
                sb.insert(config.separators[i] + (i * config.separator.length()), config.separator);
            }
        }

        return sb.toString();
    }

    private CodeUtil() {
    }
}
