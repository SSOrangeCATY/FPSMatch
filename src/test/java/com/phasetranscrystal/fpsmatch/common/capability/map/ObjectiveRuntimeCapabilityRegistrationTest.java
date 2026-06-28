package com.phasetranscrystal.fpsmatch.common.capability.map;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObjectiveRuntimeCapabilityRegistrationTest {
    @Test
    void doesNotExposeDetachedTestFactory() {
        for (Method method : ObjectiveRuntimeCapability.class.getDeclaredMethods()) {
            assertFalse(Modifier.isStatic(method.getModifiers()) && method.getName().equals("detached"));
        }
    }

    @Test
    void capabilityNameMatchesRegisteredType() {
        assertEquals("ObjectiveRuntimeCapability", ObjectiveRuntimeCapability.class.getSimpleName());
    }
}
