package com.fincity.saas.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;

/**
 * A message id with no entry in core's messages_en.properties is answered with
 * `unknown_error`, so the reason the caller passed is thrown away. `mail_send_error`
 * was one of them: every failed email (SMTP refusal, no recipient, missing
 * connection properties) came back as "Internal Server Error (Unknown)", which is
 * all QA-0160 had to go on for the Accept business partner 500.
 */
class CoreMessageKeysTest {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages", Locale.ENGLISH);

    @Test
    @DisplayName("every message id CoreMessageResourceService declares has a text in core")
    void everyKeyHasAMessage() throws IllegalAccessException {

        List<String> missing = new ArrayList<>();

        for (Field f : CoreMessageResourceService.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isFinal(m) || f.getType() != String.class)
                continue;
            String key = (String) f.get(null);
            if (!BUNDLE.containsKey(key))
                missing.add(f.getName() + "=" + key);
        }

        assertTrue(missing.isEmpty(), "message ids without a text, answered as 'Unknown': " + missing);
    }

    @Test
    @DisplayName("a mail failure carries its reason")
    void mailSendErrorCarriesTheReason() {

        String text = StringFormatter.format(BUNDLE.getString(CoreMessageResourceService.MAIL_SEND_ERROR),
                "No Send Addresses Found.");

        assertEquals("Unable to send the email: No Send Addresses Found.", text);
    }
}
