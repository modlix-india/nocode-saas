package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CodeUtilTest {

    CodeUtil.CodeGenerationConfiguration cd = new CodeUtil.CodeGenerationConfiguration();

    private static final String NUMBERS = "0123456789";
    private static final String CAPITAL_CASE = "BCDFGHJKLMNPQRSTVWXZ";
    private static final String SMALL_CASE = "bcdfghjklmnpqrstvwxz";
    private static final String SPECIAL_CHARS = "!@#$";
    private static final String SEPERATOR = "-";

    @Test
    void testGenerateDefaultCode() {
        String st = CodeUtil.generate();
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, NUMBERS));
    }

    private boolean isValidString(String str, String allowedchrs) {
        System.out.println(str + "   -   " + allowedchrs);
        for (char c : str.toCharArray()) {
            if (!allowedchrs.contains(String.valueOf(c))) {
                return false;
            }
        }
        return true;
    }

    @Test
    void test() {

        cd.setSeparators(new int[] {4, 6});
        String st = CodeUtil.generate(cd);

        assertEquals(10, st.length());
        assertEquals('-', st.charAt(4));
        assertEquals('-', st.charAt(7));
        assertTrue(isValidString(st, NUMBERS + SEPERATOR));
    }

    @Test
    void testdefault() {

        String st = CodeUtil.generate();
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(st.matches("^[0-9]{8}$"));
    }

    @Test
    void testCustomCodeGeneration() {
        cd.setLength(8)
                .setNumeric(true)
                .setLowercase(true)
                .setUppercase(true)
                .setSpecialChars(true)
                .setSeparator("-")
                .setSeparators(new int[] {3, 7});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(10, st.length());
        assertTrue(isValidString(st, NUMBERS + SEPERATOR + CAPITAL_CASE + SMALL_CASE + SPECIAL_CHARS));
        assertTrue(
                st.charAt(3) == '-' && st.charAt(8) == '-', "Separators should be present at the specified positions");
    }

    @Test
    void testCustomvthsep() {
        cd.setLength(8)
                .setNumeric(true)
                .setLowercase(true)
                .setUppercase(false)
                .setSpecialChars(false)
                .setSeparator("-")
                .setSeparators(new int[] {3, 7});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(10, st.length());
        assertTrue(isValidString(st, NUMBERS + SEPERATOR + SMALL_CASE));
        assertTrue(
                st.charAt(3) == '-' && st.charAt(8) == '-', "Separators should be present at the specified positions");
    }

    @Test
    void testCustomvthsep2() {
        cd.setLength(11)
                .setNumeric(true)
                .setLowercase(false)
                .setUppercase(true)
                .setSpecialChars(false)
                .setSeparator("-")
                .setSeparators(new int[] {2, 9});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(13, st.length());
        assertTrue(isValidString(st, NUMBERS + SEPERATOR + CAPITAL_CASE));
        assertTrue(
                st.charAt(2) == '-' && st.charAt(10) == '-', "Separators should be present at the specified positions");
    }

    @Test
    void testCustomvthsep3() {
        cd.setLength(11)
                .setNumeric(true)
                .setLowercase(false)
                .setUppercase(false)
                .setSpecialChars(true)
                .setSeparator("-")
                .setSeparators(new int[] {2, 9});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(13, st.length());
        assertTrue(isValidString(st, NUMBERS + SEPERATOR + SPECIAL_CHARS));
        assertTrue(
                st.charAt(2) == '-' && st.charAt(10) == '-', "Separators should be present at the specified positions");
    }

    @Test
    void testCustomvthsep4() {
        cd.setLength(11)
                .setNumeric(false)
                .setLowercase(true)
                .setUppercase(true)
                .setSpecialChars(false)
                .setSeparator("-")
                .setSeparators(new int[] {2, 9});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(13, st.length());
        assertTrue(isValidString(st, SEPERATOR + CAPITAL_CASE + SMALL_CASE));
        assertTrue(
                st.charAt(2) == '-' && st.charAt(10) == '-', "Separators should be present at the specified positions");
    }

    @Test
    void testCustomvthsep5() {
        cd.setLength(11)
                .setNumeric(false)
                .setLowercase(true)
                .setUppercase(false)
                .setSpecialChars(true)
                .setSeparator("-")
                .setSeparators(new int[] {2, 9});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(13, st.length());
        assertTrue(isValidString(st, SEPERATOR + SMALL_CASE + SPECIAL_CHARS));
        assertTrue(
                st.charAt(2) == '-' && st.charAt(10) == '-', "Separators should be present at the specified positions");
    }

    @Test
    void testCustomvthsep6() {
        cd.setLength(11)
                .setNumeric(false)
                .setLowercase(false)
                .setUppercase(true)
                .setSpecialChars(true)
                .setSeparator("-")
                .setSeparators(new int[] {2, 3, 4});

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(14, st.length());
        assertTrue(isValidString(st, SEPERATOR + CAPITAL_CASE + SPECIAL_CHARS));
        assertTrue(
                st.charAt(2) == '-' && st.charAt(4) == '-' && st.charAt(6) == '-',
                "Separators should be present at the specified positions");
    }

    @Test
    void testCustomCodeGeneration2() {
        cd.setLength(8).setNumeric(false).setLowercase(false).setUppercase(true).setSpecialChars(false);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, CAPITAL_CASE));
    }

    @Test
    void testCustomCodeGeneration3() {
        cd.setLength(8).setNumeric(false).setLowercase(true).setUppercase(false).setSpecialChars(false);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, SMALL_CASE));
    }

    @Test
    void testCustomCodeGeneration4() {
        cd.setLength(8)
                .setNumeric(false)
                .setLowercase(false)
                .setUppercase(false)
                .setSpecialChars(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, SPECIAL_CHARS));
    }

    @Test
    void testCustomCodeGeneration5() {
        cd.setLength(8).setNumeric(true).setLowercase(true).setUppercase(false).setSpecialChars(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, SMALL_CASE + NUMBERS + SPECIAL_CHARS));
    }

    @Test
    void testCustomCodeGeneration6() {
        cd.setLength(8).setNumeric(true).setLowercase(true).setUppercase(false).setSpecialChars(false);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, SMALL_CASE + NUMBERS));
    }

    @Test
    void testCustomCodeGeneration7() {
        cd.setLength(8).setNumeric(true).setLowercase(false).setUppercase(true).setSpecialChars(false);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, CAPITAL_CASE + NUMBERS));
    }

    @Test
    void testCustomCodeGeneration8() {
        cd.setLength(8).setNumeric(true).setLowercase(false).setUppercase(false).setSpecialChars(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(8, st.length());
        assertTrue(isValidString(st, NUMBERS + SPECIAL_CHARS));
    }

    @Test
    void testCodeGenerationWithNoSeparator() {
        CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration()
                .setLength(10)
                .setNumeric(true)
                .setLowercase(true)
                .setUppercase(true);

        String st = CodeUtil.generate(config);
        assertNotNull(st);
        assertEquals(10, st.length());
        assertTrue(isValidString(st, NUMBERS + SEPERATOR + CAPITAL_CASE + SMALL_CASE));
    }

    @Test
    void testCodeGenerationWithNoSeparator2() {
        cd.setLength(10).setNumeric(false).setLowercase(true).setUppercase(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(10, st.length());
        assertTrue(isValidString(st, SEPERATOR + CAPITAL_CASE + SMALL_CASE));
    }

    @Test
    void testCodeGenerationWithNoSeparator3() {
        cd.setLength(9).setNumeric(false).setLowercase(false).setUppercase(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(9, st.length());
        assertTrue(isValidString(st, SEPERATOR + CAPITAL_CASE));
    }

    @Test
    void testCodeGenerationWithNoSeparator4() {
        cd.setLength(15).setNumeric(false).setLowercase(true).setUppercase(false);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(15, st.length());
        assertTrue(isValidString(st, SEPERATOR + SMALL_CASE));
    }

    @Test
    void testCodeGenerationWithNoSeparator5() {
        cd.setLength(2).setNumeric(false).setLowercase(true).setUppercase(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(2, st.length());
        assertTrue(isValidString(st, SEPERATOR + CAPITAL_CASE + SMALL_CASE));
    }

    @Test
    void test2() {
        cd.setLength(19).setNumeric(false).setLowercase(true).setUppercase(true);

        String st = CodeUtil.generate(cd);
        assertNotNull(st);
        assertEquals(19, st.length());
        assertTrue(isValidString(st, SEPERATOR + CAPITAL_CASE + SMALL_CASE));
    }

    @Test
    void testSeparatorBeyondLength() {
        cd.setLength(8).setNumeric(true).setSeparator("-").setSeparators(new int[] {10});
        assertThrows(
                StringIndexOutOfBoundsException.class,
                () -> CodeUtil.generate(cd),
                "Separator position should not be beyond the length of the generated st");
    }

    @Test
    void testSeparatorBeyondLength2() {
        cd.setLength(8).setNumeric(true).setSeparator("-").setSeparators(new int[] {7, 12});
        assertThrows(
                StringIndexOutOfBoundsException.class,
                () -> CodeUtil.generate(cd),
                "Separator position should not be beyond the length of the generated st");
    }

    @Test
    void testSeparatorBeyondLength3() {
        cd.setLength(8).setNumeric(true).setSeparator("-").setSeparators(new int[] {10, 12});
        assertThrows(
                StringIndexOutOfBoundsException.class,
                () -> CodeUtil.generate(cd),
                "Separator position should not be beyond the length of the generated st");
    }

    @Test
    void testnegativelength() {
        cd.setLength(-1).setNumeric(true).setSeparator("-");

        assertThrows(
                NegativeArraySizeException.class,
                () -> CodeUtil.generate(cd),
                "Separator position should not be beyond the length of the generated st");
    }

    @Test
    void testsmallerlength() {
        cd.setLength(2).setNumeric(true).setLowercase(true).setUppercase(true).setSpecialChars(true);
        String st = CodeUtil.generate(cd);
        assertEquals(2, st.length());
        assertTrue(isValidString(st, NUMBERS + CAPITAL_CASE + SMALL_CASE + SPECIAL_CHARS));
    }

    @Test
    void testseparator() {
        cd.setLength(7)
                .setNumeric(true)
                .setLowercase(true)
                .setUppercase(true)
                .setSpecialChars(true)
                .setSeparator("*")
                .setSeparators(new int[] {3, 4});
        String st = CodeUtil.generate(cd);
        assertEquals(9, st.length());
        assertTrue(isValidString(st, NUMBERS + CAPITAL_CASE + SMALL_CASE + SPECIAL_CHARS + "*"));
    }

    @Test
    void testseparator2() {
        cd.setLength(7).setSeparator("*").setSeparators(new int[] {3, 4});
        String st = CodeUtil.generate(cd);
        assertEquals(9, st.length());
        assertTrue(isValidString(st, NUMBERS + "*"));
    }

    @Test
    void testseparator3() {
        cd.setLength(7).setSeparator("*");
        String st = CodeUtil.generate(cd);
        assertEquals(7, st.length());
        assertTrue(isValidString(st, NUMBERS + "*"));
    }

    @Test
    void testlen() {
        cd.setLength(0);
        String st = CodeUtil.generate(cd);
        assertEquals(0, st.length());
    }

    @Test
    void testsample() {
        cd.setLength(7).setSeparator("*").setSeparators(new int[] {0, 7});

        String st = CodeUtil.generate(cd);
        assertEquals(9, st.length());
        assertTrue(isValidString(st, NUMBERS + "*"));
    }

    @Test
    void testsample2() {
        cd.setLength(7).setSeparator("*").setSeparators(new int[] {3, 4});

        String st = CodeUtil.generate(cd);
        assertEquals(9, st.length());
        assertTrue(isValidString(st, NUMBERS + "*"));
    }

    @Test
    void testnegseplen() {
        cd.setLength(7).setSeparator("-").setSeparators(new int[] {-1, 7});
        assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd));
    }

    @Test
    void minlentest() {
        cd.setLength(4).setNumeric(true).setUppercase(true).setLowercase(true).setSpecialChars(true);

        String st = CodeUtil.generate(cd);
        assertEquals(4, st.length());
        assertTrue(isValidString(st, NUMBERS + CAPITAL_CASE + SMALL_CASE + SPECIAL_CHARS));
    }

    @Test
    void minlentest2() {
        cd.setLength(4)
                .setNumeric(true)
                .setUppercase(true)
                .setLowercase(true)
                .setSpecialChars(true)
                .setSeparator("-")
                .setSeparators(new int[] {2});

        String st = CodeUtil.generate(cd);
        assertEquals(5, st.length());
        assertTrue(isValidString(st, NUMBERS + CAPITAL_CASE + SMALL_CASE + SPECIAL_CHARS + SEPERATOR));
    }
}
