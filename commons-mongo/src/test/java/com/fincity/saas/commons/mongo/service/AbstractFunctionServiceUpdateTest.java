package com.fincity.saas.commons.mongo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.mongo.document.AbstractFunction;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;

/**
 * An update of a client's function copy reaches updatableEntity as its DIFFERENCE from the
 * base, which carries no name or namespace when the client did not change them. That was
 * refused as "Namespace or name missing" on every client copy, publishing its draft included.
 */
class AbstractFunctionServiceUpdateTest {

    static class TestFunction extends AbstractFunction<TestFunction> {

        private static final long serialVersionUID = 1L;

        TestFunction(Map<String, Object> definition, String baseClientCode) {
            this.setDefinition(definition);
            this.setName("ZzOvr.zzOvrFn");
            this.setBaseClientCode(baseClientCode);
        }
    }

    static class TestService extends AbstractFunctionService<TestFunction, IOverridableDataRepository<TestFunction>> {
        TestService() {
            super(TestFunction.class, null, null);
        }
    }

    private final TestService service = new TestService();

    @Test
    void derivedCopyWithoutNameUsesTheStoredName() {
        TestFunction stored = new TestFunction(new HashMap<>(), "SYSTEM");
        TestFunction delta = new TestFunction(new HashMap<>(Map.of("steps", Map.of())), "SYSTEM");
        assertEquals("ZzOvr.zzOvrFn", service.updatedFunctionName(delta, stored));
    }

    @Test
    void derivedCopyStillReportsARename() {
        TestFunction stored = new TestFunction(new HashMap<>(), "SYSTEM");
        TestFunction delta = new TestFunction(new HashMap<>(Map.of("name", "other")), "SYSTEM");
        assertEquals("ZzOvr.other", service.updatedFunctionName(delta, stored));
    }

    @Test
    void baseFunctionMustCarryBoth() {
        TestFunction stored = new TestFunction(new HashMap<>(), null);
        TestFunction delta = new TestFunction(new HashMap<>(Map.of("name", "zzOvrFn")), null);
        assertNull(service.updatedFunctionName(delta, stored));
        TestFunction full = new TestFunction(new HashMap<>(Map.of("name", "zzOvrFn", "namespace", "ZzOvr")), null);
        assertEquals("ZzOvr.zzOvrFn", service.updatedFunctionName(full, stored));
    }
}
