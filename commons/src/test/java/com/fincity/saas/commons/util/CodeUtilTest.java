package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CodeUtilTest {

	CodeUtil.CodeGenerationConfiguration cd = new CodeUtil.CodeGenerationConfiguration();

	@Test
	void test() {

		cd.setSeparators(new int[] { 4, 6 });
		String st = CodeUtil.generate(cd);

		assertEquals(10, st.length());
		assertEquals('-', st.charAt(4));
		assertEquals('-', st.charAt(7));
		assertTrue(st.matches("^[0-9-]+$"));

	}

	@Test
	void testdefault() {

		String code = CodeUtil.generate();
		assertNotNull(code);
		assertEquals(8, code.length());
		assertTrue(code.matches("[0-9A-Za-z]+"));
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

		String code = CodeUtil.generate(cd);
		assertNotNull(code);
		assertEquals(10, code.length());
		assertTrue(code.matches("[0-9A-Za-z!@#$-]+"));
		assertTrue(code.charAt(3) == '-' && code.charAt(8) == '-',
		        "Separators should be present at the specified positions");
	}

	@Test
	void testCodeGenerationWithNoSeparator() {
		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration().setLength(10)
		        .setNumeric(true)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String code = CodeUtil.generate(config);
		assertNotNull(code);
		assertEquals(10, code.length());
		assertTrue(code.matches("[0-9A-Za-z]+"), "Code should not contain separators");
	}

	@Test
	void testCodeGenerationWithNoSeparator2() {
		cd.setLength(10)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String code = CodeUtil.generate(cd);
		assertNotNull(code);
		assertEquals(10, code.length());
		assertTrue(code.matches("[A-Za-z]+"), "Code should not contain separators");
	}

	@Test
	void testCodeGenerationWithNoSeparator3() {
		cd.setLength(10)
		        .setNumeric(false)
		        .setSmallCase(false)
		        .setCapitalCase(true);

		String code = CodeUtil.generate(cd);
		assertNotNull(code);
		assertEquals(10, code.length());
		assertTrue(code.matches("[A-Z]+"), "Code should not contain separators");
	}

	@Test
	void testCodeGenerationWithNoSeparator4() {
		cd.setLength(10)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(false);

		String code = CodeUtil.generate(cd);
		assertNotNull(code);
		assertEquals(10, code.length());
		assertTrue(code.matches("[a-z]+"), "Code should not contain separators");
	}

	@Test
	void testCodeGenerationWithNoSeparator5() {
		cd.setLength(2)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String code = CodeUtil.generate(cd);
		assertNotNull(code);
		assertEquals(2, code.length());
		assertTrue(code.matches("[A-Za-z]+"), "Code should not contain separators");
	}

	@Test
	void testinvalid() {
		cd.setLength(10)
		        .setNumeric(false)
		        .setSmallCase(true)
		        .setCapitalCase(true);

		String code = CodeUtil.generate(cd);
		assertNotNull(code);
		assertEquals(10, code.length());
		assertTrue(code.matches("[A-Za-z]+"), "Code should not contain separators");
	}
	
	 @Test
	     void testSeparatorBeyondLength() {
	                cd.setLength(8)
	                .setNumeric(true)
	                .setSeparator("-")
	                .setSeparators(new int[]{10});
	        assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd),
	                "Separator position should not be beyond the length of the generated code");
	    }
	 
	 @Test
     void testSeparatorBeyondLength2() {
                cd.setLength(8)
                .setNumeric(true)
                .setSeparator("-")
                .setSeparators(new int[]{7,12});
        assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd),
                "Separator position should not be beyond the length of the generated code");
    }

	 @Test
     void testSeparatorBeyondLength3() {
                cd.setLength(8)
                .setNumeric(true)
                .setSeparator("-")
                .setSeparators(new int[]{10,12});
        assertThrows(StringIndexOutOfBoundsException.class, () -> CodeUtil.generate(cd),
                "Separator position should not be beyond the length of the generated code");
    }
}
