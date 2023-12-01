package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(contains(st, NUMBERS));
		assertFalse(contains(st,SEPERATOR+CAPITAL_CASE+SMALL_CASE+SPECIAL_CHARS));
	}

	private boolean contains(String str, String allowedchrs) {
		for (char c : str.toCharArray()) {
			if (!allowedchrs.contains(String.valueOf(c))) {
				return false;
			}
		}
		return true;
	}
	
	
	@Test
	void test() {

		cd.setSeparators(new int[] { 4, 6 });
		String st = CodeUtil.generate(cd);

		assertEquals(10, st.length());
		assertEquals('-', st.charAt(4));
		assertEquals('-', st.charAt(7));
		assertTrue(contains(st, NUMBERS + SEPERATOR));
		assertFalse(contains(st,CAPITAL_CASE+SMALL_CASE+SPECIAL_CHARS));

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
		        .setSmallCase(true)
		        .setCapitalCase(true)
		        .setSpecialChars(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 3, 7 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(10, st.length());
		assertTrue(contains(st, NUMBERS + SEPERATOR+CAPITAL_CASE+SMALL_CASE+SPECIAL_CHARS));
		assertTrue(st.charAt(3) == '-' && st.charAt(8) == '-',
		        "Separators should be present at the specified positions");
		
	}
	
	@Test
	void testCustomvthsep() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSmallCase(true)
		        .setCapitalCase(false)
		        .setSpecialChars(false)
		        .setSeparator("-")
		        .setSeparators(new int[] { 3, 7 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(10, st.length());
		assertTrue(contains(st, NUMBERS + SEPERATOR+SMALL_CASE));
		assertTrue(st.charAt(3) == '-' && st.charAt(8) == '-',
		        "Separators should be present at the specified positions");
		assertFalse(contains(st,CAPITAL_CASE+SPECIAL_CHARS));
	}
	
	@Test
	void testCustomvthsep2() {
		cd.setLength(11)
		        .setNumeric(true)
		        .setSmallCase(false)
		        .setCapitalCase(true)
		        .setSpecialChars(false)
		        .setSeparator("-")
		        .setSeparators(new int[] { 2, 9 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(13, st.length());
		assertTrue(contains(st, NUMBERS + SEPERATOR+CAPITAL_CASE));
		assertTrue(st.charAt(2) == '-' && st.charAt(10) == '-',
		        "Separators should be present at the specified positions");
		assertFalse(contains(st,SMALL_CASE+SPECIAL_CHARS));
	}
	
	@Test
	void testCustomvthsep3() {
		cd.setLength(11)
		        .setNumeric(true)
		        .setSmallCase(false)
		        .setCapitalCase(false)
		        .setSpecialChars(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 2, 9 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(13, st.length());
		assertTrue(contains(st, NUMBERS + SEPERATOR+SPECIAL_CHARS));
		assertTrue(st.charAt(2) == '-' && st.charAt(10) == '-',
		        "Separators should be present at the specified positions");
		assertFalse(contains(st,CAPITAL_CASE+SMALL_CASE));
	}
	
	@Test
	void testCustomvthsep4() {
		cd.setLength(11)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true)
		        .setSpecialChars(false)
		        .setSeparator("-")
		        .setSeparators(new int[] { 2, 9 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(13, st.length());
		assertTrue(contains(st, SEPERATOR+CAPITAL_CASE+SMALL_CASE));
		assertTrue(st.charAt(2) == '-' && st.charAt(10) == '-',
		        "Separators should be present at the specified positions");
		assertFalse(contains(st,SPECIAL_CHARS));
	}
	
	@Test
	void testCustomvthsep5() {
		cd.setLength(11)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(false)
		        .setSpecialChars(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 2, 9 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(13, st.length());
		assertTrue(contains(st, SEPERATOR+SMALL_CASE+SPECIAL_CHARS));
		assertTrue(st.charAt(2) == '-' && st.charAt(10) == '-',
		        "Separators should be present at the specified positions");
		assertFalse(contains(st,CAPITAL_CASE));
	}
	
	@Test
	void testCustomvthsep6() {
		cd.setLength(11)
		        .setNumeric(false)
		        .setSmallCase(false)
		        .setCapitalCase(true)
		        .setSpecialChars(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 2,3,4 });

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(14, st.length());
		assertTrue(contains(st, SEPERATOR+CAPITAL_CASE+SPECIAL_CHARS));
		assertTrue(st.charAt(2) == '-' && st.charAt(4) == '-'&&st.charAt(6)=='-',
		        "Separators should be present at the specified positions");
		assertFalse(contains(st,SMALL_CASE));
	}
	
	@Test
	void testCustomCodeGeneration2() {
		cd.setLength(8)
		        .setNumeric(false)
		        .setSmallCase(false)
		        .setCapitalCase(true)
		        .setSpecialChars(false);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,CAPITAL_CASE));
		assertFalse(contains(st,SEPERATOR+NUMBERS+SMALL_CASE+SPECIAL_CHARS));
	}

	@Test
	void testCustomCodeGeneration3() {
		cd.setLength(8)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(false)
		        .setSpecialChars(false);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,SMALL_CASE));
		assertFalse(contains(st,SEPERATOR+CAPITAL_CASE+NUMBERS+SPECIAL_CHARS));
	}
	
	@Test
	void testCustomCodeGeneration4() {
		cd.setLength(8)
		        .setNumeric(false)
		        .setSmallCase(false)
		        .setCapitalCase(false)
		        .setSpecialChars(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,SPECIAL_CHARS));
		assertFalse(contains(st,SEPERATOR+CAPITAL_CASE+SMALL_CASE+NUMBERS));
	}

	@Test
	void testCustomCodeGeneration5() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSmallCase(true)
		        .setCapitalCase(false)
		        .setSpecialChars(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,SMALL_CASE+NUMBERS+SPECIAL_CHARS));
		assertFalse(contains(st,SEPERATOR+CAPITAL_CASE));
	}

	@Test
	void testCustomCodeGeneration6() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSmallCase(true)
		        .setCapitalCase(false)
		        .setSpecialChars(false);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,SMALL_CASE+NUMBERS));
		assertFalse(contains(st,SEPERATOR+CAPITAL_CASE+SMALL_CASE+SPECIAL_CHARS));
	}
	
	@Test
	void testCustomCodeGeneration7() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSmallCase(false)
		        .setCapitalCase(true)
		        .setSpecialChars(false);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,CAPITAL_CASE+NUMBERS));
		assertFalse(contains(st,SEPERATOR+SMALL_CASE+SPECIAL_CHARS));
	}
	
	@Test
	void testCustomCodeGeneration8() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSmallCase(false)
		        .setCapitalCase(false)
		        .setSpecialChars(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(8, st.length());
		assertTrue(contains(st,NUMBERS+SPECIAL_CHARS));
		assertFalse(contains(st,SEPERATOR+CAPITAL_CASE+SMALL_CASE));
	}

	@Test
	void testCodeGenerationWithNoSeparator() {
		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration().setLength(10)
		        .setNumeric(true)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String st = CodeUtil.generate(config);
		assertNotNull(st);
		assertEquals(10, st.length());
		assertTrue(contains(st, NUMBERS + SEPERATOR+CAPITAL_CASE+SMALL_CASE));
		assertFalse(contains(st,SPECIAL_CHARS));
	}

	@Test
	void testCodeGenerationWithNoSeparator2() {
		cd.setLength(10)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(10, st.length());
		assertTrue(contains(st, SEPERATOR+CAPITAL_CASE+SMALL_CASE));
		assertFalse(contains(st,SPECIAL_CHARS));
	}

	@Test
	void testCodeGenerationWithNoSeparator3() {
		cd.setLength(9)
		        .setNumeric(false)
		        .setSmallCase(false)
		        .setCapitalCase(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(9, st.length());
		assertTrue(contains(st, SEPERATOR+CAPITAL_CASE));
		assertFalse(contains(st,SMALL_CASE+SPECIAL_CHARS));
	}

	@Test
	void testCodeGenerationWithNoSeparator4() {
		cd.setLength(15)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(false);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(15, st.length());
		assertTrue(contains(st, SEPERATOR+SMALL_CASE));
		assertFalse(contains(st,CAPITAL_CASE+SPECIAL_CHARS));
		}

	@Test
	void testCodeGenerationWithNoSeparator5() {
		cd.setLength(2)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(2, st.length());
		assertTrue(contains(st, SEPERATOR+CAPITAL_CASE+SMALL_CASE));
		assertFalse(contains(st,SPECIAL_CHARS));
	}

	@Test
	void test2() {
		cd.setLength(19)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String st = CodeUtil.generate(cd);
		assertNotNull(st);
		assertEquals(19, st.length());
		assertTrue(contains(st,SEPERATOR+CAPITAL_CASE+SMALL_CASE));
		assertFalse(contains(st,SPECIAL_CHARS));
	}

	@Test
	void testSeparatorBeyondLength() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 10 });
		assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd),
		        "Separator position should not be beyond the length of the generated st");
	}

	@Test
	void testSeparatorBeyondLength2() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 7, 12 });
		assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd),
		        "Separator position should not be beyond the length of the generated st");
	}

	@Test
	void testSeparatorBeyondLength3() {
		cd.setLength(8)
		        .setNumeric(true)
		        .setSeparator("-")
		        .setSeparators(new int[] { 10, 12 });
		assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd),
		        "Separator position should not be beyond the length of the generated st");
	}
}
