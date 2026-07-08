package com.modlix.saas.adzump.validate;

import java.util.Collection;

/**
 * Tiny null-safe helpers shared by the pure J6 rule layers. Package-private: J6 internal only.
 */
final class ValidationSupport {

    private ValidationSupport() {
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static boolean notEmpty(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    static int sizeOf(Collection<?> c) {
        return c == null ? 0 : c.size();
    }
}
