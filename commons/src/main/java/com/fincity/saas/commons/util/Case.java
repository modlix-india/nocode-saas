package com.fincity.saas.commons.util;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public enum Case {

    /** Unknown case type. */
    UNKNOWN("^$", str -> str),
    /** Snake case (e.g., "hello_world"). */
    SNAKE("^[a-z0-9_]+$", Case::toSnakeCase),
    /** Screaming snake case (e.g., "HELLO_WORLD"). */
    SCREAMING_SNAKE_CASE("^[A-Z0-9_]+$", Case::toScreamingSnakeCase),
    /** Camel case (e.g., "helloWorld"). */
    CAMEL("^[a-z][a-zA-Z0-9]*$", Case::toCamelCase),
    /** Pascal case (e.g., "HelloWorld"). */
    PASCAL("^[A-Z][a-zA-Z0-9]*$", Case::toPascalCase),
    /** Kebab case (e.g., "hello-world"). */
    KEBAB("^[a-z0-9-]+$", Case::toKebabCase),;

    private static final Map<Case, Pattern> CASE_PATTERNS = initializeCasePatterns();
    private final Pattern pattern;
    private final UnaryOperator<String> converter;

    Case(String regex, UnaryOperator<String> converter) {
        this.pattern = Pattern.compile(regex);
        this.converter = converter;
    }

    private static Map<Case, Pattern> initializeCasePatterns() {
        return Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Function.identity(), c -> c.pattern));
    }

    public static CaseBuilder from(String input) {
        return new CaseBuilder(input);
    }

    private static String toSnakeCase(String str) {
        return str.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String toScreamingSnakeCase(String str) {
        return str.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    private static String toCamelCase(String str) {
        String[] parts = str.split("[_\\-]");
        StringBuilder camelCaseString = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            camelCaseString.append(capitalize(parts[i]).toLowerCase());
        }
        return camelCaseString.toString();
    }

    private static String toPascalCase(String str) {
        return capitalize(toCamelCase(str));
    }

    private static String toKebabCase(String str) {
        return toSnakeCase(str).replace('_', '-');
    }

    private static String capitalize(String str) {
        return str.isEmpty() ? str : Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

	private String convert(String str, Case targetCase) {
        if (this == targetCase) {
            return str;
        }
        String normalized = this.converter.apply(str);
        return targetCase.converter.apply(normalized);
    }

    public static class CaseBuilder {

        @Getter
        private final String input;

        private Case sourceCase;

        private CaseBuilder(String input) {
            this.input = input;
            this.sourceCase = detect();
        }

        public CaseBuilder fromCase(Case sourceCase) {
            this.sourceCase = sourceCase;
            return this;
        }

        public String to(Case targetCase) {
            return sourceCase.convert(input, targetCase);
        }

        private Case detect() {
            return CASE_PATTERNS.entrySet().stream()
                    .filter(entry -> entry.getValue().matcher(input).matches())
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .orElse(Case.UNKNOWN);
        }
    }
}
