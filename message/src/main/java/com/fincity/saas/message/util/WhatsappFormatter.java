package com.fincity.saas.message.util;

public class WhatsappFormatter {

    private WhatsappFormatter() {}

    private static String wrap(String text, String wrapper) {
        return (text == null || text.isBlank()) ? text : wrapper + text + wrapper;
    }

    public static String italic(String text) {
        return wrap(text, "_");
    }

    public static String bold(String text) {
        return wrap(text, "*");
    }

    public static String strikethrough(String text) {
        return wrap(text, "~");
    }

    public static String code(String text) {
        return wrap(text, "```");
    }
}
