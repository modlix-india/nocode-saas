package com.fincity.saas.entity.processor.model.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether a stage request names the stage and each of its statuses.
 *
 * <p>A stage sent with no name used to be saved under its code, so the template showed a random 22
 * character key as the stage's name. The case that exposed it: Save pressed on an empty Add stage form.
 */
class StageRequestNameTest {

    private static StageRequest named(String name) {
        StageRequest request = new StageRequest();
        // null stands for a status sent without a name key: the setter is never called
        if (name != null) request.setName(name);
        return request;
    }

    private static StageRequest withChildren(String parent, String... children) {
        StageRequest request = named(parent);
        Map<Integer, StageRequest> map = new HashMap<>();
        for (int i = 0; i < children.length; i++) map.put(i, named(children[i]));
        request.setChildren(map);
        return request;
    }

    @Test
    @DisplayName("an empty Add stage form names nothing")
    void emptyForm() {
        StageRequest request = new StageRequest();
        request.setChildren(new HashMap<>());

        assertTrue(request.hasNoName());
        assertFalse(request.hasChildWithoutName());
    }

    @Test
    @DisplayName("a name of spaces is no name")
    void blankName() {
        assertTrue(named("   ").hasNoName());
        assertFalse(named("Contactable").hasNoName());
    }

    @Test
    @DisplayName("a blank status among named ones is caught")
    void blankStatus() {
        assertTrue(withChildren("Contactable", "Shared Details", " ").hasChildWithoutName());
        assertTrue(withChildren("Contactable", "Shared Details", null).hasChildWithoutName());
    }

    @Test
    @DisplayName("a stage with no statuses, or all named, passes")
    void namedStatuses() {
        assertFalse(named("Contactable").hasChildWithoutName());
        assertFalse(withChildren("Contactable", "Shared Details", "Follow Up").hasChildWithoutName());
    }
}
