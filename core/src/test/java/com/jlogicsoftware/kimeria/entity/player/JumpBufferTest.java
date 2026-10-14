package com.jlogicsoftware.kimeria.entity.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JumpBufferTest {
    @Test
    void earlyReleasedPressFiresWhenLandingOrClamberingBecomesPossible() {
        for (int source = 0; source < 3; source++) {
            JumpBuffer buffer = new JumpBuffer();
            buffer.update(0f, source == 0, source == 1, source == 2);
            assertFalse(buffer.consume(false));
            buffer.update(0.075f, false, false, false);
            assertTrue(buffer.consume(true));
            assertFalse(buffer.consume(true));
        }
    }

    @Test
    void pressExpiresAtOneHundredMillisecondsEvenIfHeld() {
        JumpBuffer buffer = new JumpBuffer();
        buffer.update(0f, true, false, false);
        buffer.update(JumpBuffer.WINDOW_SECONDS, true, false, false);
        assertFalse(buffer.consume(true));
    }

    @Test
    void holdingThroughLandingDoesNotRepeatButReleaseAndPressDoes() {
        for (int source = 0; source < 3; source++) {
            JumpBuffer buffer = new JumpBuffer();
            buffer.update(0f, source == 0, source == 1, source == 2);
            assertTrue(buffer.consume(true));
            for (int frame = 0; frame < 60; frame++) {
                buffer.update(1f / 60f, source == 0, source == 1, source == 2);
                assertFalse(buffer.consume(frame >= 30));
            }
            buffer.update(0.016f, false, false, false);
            buffer.update(0.016f, source == 0, source == 1, source == 2);
            assertTrue(buffer.consume(true));
        }
    }

    @Test
    void freshPressOnAnotherDeviceIsNotMaskedByAHeldButton() {
        JumpBuffer buffer = new JumpBuffer();
        buffer.update(0f, true, false, false);
        assertTrue(buffer.consume(true));
        buffer.update(0.016f, true, true, false);
        assertTrue(buffer.consume(true));
        buffer.update(0.016f, true, true, true);
        assertTrue(buffer.consume(true));
    }

    @Test
    void newerPressRefreshesTheWindow() {
        JumpBuffer buffer = new JumpBuffer();
        buffer.update(0f, true, false, false);
        buffer.update(0.09f, false, false, false);
        buffer.update(0.001f, true, false, false);
        buffer.update(0.09f, false, false, false);
        assertTrue(buffer.consume(true));
    }

    @Test
    void pauseOrRespawnDiscardsPendingPressesAndSuppressesHeldInput() {
        JumpBuffer buffer = new JumpBuffer();
        buffer.update(0f, true, false, false);
        buffer.reset();
        assertFalse(buffer.consume(true));
        buffer.update(0f, true, true, true);
        assertFalse(buffer.consume(true));
        buffer.update(0.016f, false, false, false);
        buffer.update(0.016f, false, true, false);
        assertTrue(buffer.consume(true));
    }

    @Test
    void suppressedMenuConfirmCannotQueueAJump() {
        JumpBuffer buffer = new JumpBuffer();
        // GamepadInput reports false until the menu-confirm button has been released.
        buffer.reset();
        buffer.update(0.016f, false, false, false);
        assertFalse(buffer.consume(true));
        buffer.update(0.016f, false, false, false);
        assertFalse(buffer.consume(true));
        buffer.update(0.016f, false, true, false);
        assertTrue(buffer.consume(true));
    }
}
