package com.modlix.saas.files.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.modlix.saas.commons2.exception.GenericException;

class FileNameUtilTest {

    @Nested
    @DisplayName("names that are an unguarded interpolation, not a name")
    class StringifiedNothing {

        /**
         * The one that actually happened: 115 folders named "undefined" on dev,
         * from `.../directory{{path ?? ''}}/{{Page.folderName}}` with an empty
         * name box.
         */
        @ParameterizedTest
        @ValueSource(strings = { "undefined", "UNDEFINED", "Undefined", "null", "NaN",
                "[object Object]", "[object object]" })
        @DisplayName("refused, whatever the case")
        void refused(String name) {
            assertThrows(GenericException.class, () -> FileNameUtil.validate(name));
        }

        @Test
        @DisplayName("a name that merely CONTAINS one is fine")
        void containingIsFine() {
            assertDoesNotThrow(() -> FileNameUtil.validate("undefined-behaviour.pdf"));
            assertDoesNotThrow(() -> FileNameUtil.validate("nullable"));
        }
    }

    @Nested
    @DisplayName("names that are not names")
    class Malformed {

        @ParameterizedTest
        @ValueSource(strings = { "", "   " })
        @DisplayName("blank is refused")
        void blank(String name) {
            assertThrows(GenericException.class, () -> FileNameUtil.validate(name));
        }

        @Test
        @DisplayName("a tab-only name is blank too")
        void tabOnly() {
            assertThrows(GenericException.class, () -> FileNameUtil.validate("\t"));
        }

        @Test
        @DisplayName("null is refused rather than thrown past")
        void nullName() {
            assertThrows(GenericException.class, () -> FileNameUtil.validate(null));
        }

        @ParameterizedTest
        @ValueSource(strings = { ".", ".." })
        @DisplayName("the traversal segments are refused")
        void traversal(String name) {
            assertThrows(GenericException.class, () -> FileNameUtil.validate(name));
        }

        @ParameterizedTest
        @ValueSource(strings = { "a/b", "a\\b" })
        @DisplayName("a separator inside one segment is refused")
        void separators(String name) {
            assertThrows(GenericException.class, () -> FileNameUtil.validate(name));
        }

        @Test
        @DisplayName("a control character is refused")
        void controlChar() {
            assertThrows(GenericException.class, () -> FileNameUtil.validate("a\u0001b"));
        }
    }

    @Nested
    @DisplayName("names people really use")
    class Allowed {

        @ParameterizedTest
        @ValueSource(strings = { "docsScreens", "Pan.pdf", "calculator images", "video_test-1",
                "Adzump text updated themes.xlsx", "Group 1707479488.svg",
                "699d4dc399e3a015be13c8f4", "CityVille4_1225_68.pdf", "..leading-dots.png", "a" })
        @DisplayName("allowed")
        void allowed(String name) {
            assertDoesNotThrow(() -> FileNameUtil.validate(name));
        }

        @Test
        @DisplayName("isUsable answers instead of throwing")
        void isUsable() {
            assertFalse(FileNameUtil.isUsable("undefined"));
            assertTrue(FileNameUtil.isUsable("real.png"));
        }
    }
}
