package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class BooleanUtilTest {

    @Test
    void test() {

        StepVerifier.create(BooleanUtil.safeValueOfWithEmpty(true))
                .assertNext(value -> assertTrue(value))
                .verifyComplete();

        StepVerifier.create(BooleanUtil.safeValueOfWithEmpty(false)).verifyComplete();
    }
}
