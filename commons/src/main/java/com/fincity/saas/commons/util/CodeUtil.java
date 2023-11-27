package com.fincity.saas.commons.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lombok.Data;
import lombok.experimental.Accessors;

public class CodeUtil {

    @Data
    @Accessors(chain = true)
    public static class CodeGenerationConfiguration {

        private int length = 8;
        private boolean numeric = true;
        private boolean smallCase = false;
        private boolean capitalCase = false;
        private boolean specialChars = false;
        private int[] separators;
        private String separator = "-";
    }

    private static final String NUMBERS = "0123456789";
    private static final String CAPITAL_CASE = "BCDFGHJKLMNPQRSTVWXZ";
    private static final String SMALL_CASE = "bcdfghjklmnpqrstvwxz";
    private static final String SPECIAL_CHARS = "!@#$";

    public static String generate() {
        return generate(new CodeGenerationConfiguration());
    }

    public static String generate(CodeGenerationConfiguration config) {

        Random rand = new Random();
        StringBuilder sb = new StringBuilder(config.getLength()
                + (config.separators == null ? 0 : config.separators.length) * config.separator.length());

        List<String> charSets = new ArrayList<>();

        if (config.numeric)
            charSets.add(NUMBERS);
        if (config.capitalCase)
            charSets.add(CAPITAL_CASE);
        if (config.smallCase)
            charSets.add(SMALL_CASE);
        if (config.specialChars)
            charSets.add(SPECIAL_CHARS);

        while (sb.length() != config.length) {
            int set = 0;
            if (charSets.size() > 1)
                set = rand.nextInt(charSets.size());
            String charSet = charSets.get(set);
            sb.append(charSet.charAt(rand.nextInt(charSet.length())));
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
