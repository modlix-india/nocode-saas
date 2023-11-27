package com.fincity.saas.commons.util;

import org.junit.jupiter.api.Test;

class CodeUtilTest {

    @Test
    void test() {

        CodeUtil.CodeGenerationConfiguration cd = new CodeUtil.CodeGenerationConfiguration();
        
        cd.setSeparators(new int[] {4,6});        
        String st = CodeUtil.generate(cd);
        System.out.println(st);
    }

}
