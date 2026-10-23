package com.jlogicsoftware.kimeria.entity.player;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LookAroundTest {
    private LookAround ready() {
        LookAround look = new LookAround();
        look.update(0, 0);
        return look;
    }
    private void advance(LookAround look, float seconds, int direction) {
        for (int i = 0; i < Math.round(seconds * 60); i++) look.update(1f / 60, direction);
    }
    @Test void poseIsImmediateButCameraWaitsThenEasesOverOneSecond() {
        LookAround look = ready();
        look.update(0, -1);
        assertEquals(-1, look.direction());
        advance(look, .2f, -1); assertEquals(0, look.offset(), .001);
        advance(look, .6f, -1); assertEquals(-50f, look.offset(), .01);
        advance(look, 1.5f, -1); assertEquals(-100, look.offset(), .01);
        advance(look, 3, -1); assertEquals(-100, look.offset(), .01);
    }
    @Test void downHasTheSameTimingAndReleaseReturnsWithoutJumping() {
        LookAround look = ready();
        advance(look, 3, 1);
        assertEquals(150, look.offset(), .01);
        look.update(0, 0); assertEquals(0, look.direction()); assertEquals(150, look.offset(), .01);
        advance(look, .1f, 0); assertEquals(75, look.offset(), .01);
        advance(look, .1f, 0); assertEquals(0, look.offset(), .01);
    }
    @Test void reversingRestartsTheDelayAndPreservesTheCurrentOffset() {
        LookAround look = ready();
        advance(look, 3, -1);
        look.update(0, 1); assertEquals(-100, look.offset(), .01);
        advance(look, .2f, 1); assertEquals(0, look.offset(), .01);
        advance(look, .1f, 1); assertEquals(0, look.offset(), .01);
        advance(look, 2.5f, 1); assertEquals(150, look.offset(), .01);
    }
    @Test void releaseBeforeTheDelayDoesNotMoveTheCamera() {
        LookAround look = ready();
        advance(look, .2f, -1); advance(look, 3, 0);
        assertEquals(0, look.offset());
        advance(look, .1f, 1); assertEquals(0, look.offset());
    }
    @Test void interactionAtHoldStartGetsOneSecondWithoutDependingOnLaterLabelVisibility() {
        for (int direction : new int[]{-1, 1}) {
            LookAround look = ready();
            look.update(0, direction, true, true);
            assertEquals(direction, look.direction());
            // The look chord may hide another-direction hint; it must not shorten this hold.
            advance(look, .9f, direction); assertEquals(0, look.offset(), .001);
            advance(look, .6f, direction);
            assertEquals(direction < 0 ? -50 : 75, look.offset(), .01);
        }
    }
    @Test void uiBoundariesRequireReleaseAndDiscardOldHoldTime() {
        LookAround look = ready();
        advance(look, 3, -1); look.suppress();
        advance(look, 3, -1); assertEquals(0, look.direction()); assertEquals(0, look.offset(), .01);
        look.update(0, 0); advance(look, .2f, -1); assertEquals(0, look.offset(), .01);
        look.clear(); advance(look, 3, -1); assertEquals(0, look.offset());
    }
    @Test void movementOrActionCannotCountAsReleasingTheVerticalInputAfterUi() {
        LookAround look = new LookAround();
        for (int i = 0; i < 180; i++) look.update(1f / 60, 0, true);
        advance(look, 3, -1); assertEquals(0, look.direction());
        look.update(0, 0, false); look.update(0, -1, true);
        assertEquals(-1, look.direction());
    }

    @Test void fullPeekActuallyReturnsWithinPointTwoSecondsThroughTheGameCamera() throws Exception {
        var previousGraphics = com.badlogic.gdx.Gdx.graphics;
        com.badlogic.gdx.Gdx.graphics = (com.badlogic.gdx.Graphics) java.lang.reflect.Proxy.newProxyInstance(
            com.badlogic.gdx.Graphics.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Graphics.class},
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? 1f / 60 : null);
        try {
            var game = new com.jlogicsoftware.kimeria.KimeriaGame();
            var field = game.getClass().getDeclaredField("cameraTarget");
            field.setAccessible(true);
            var camera = (com.badlogic.gdx.math.Vector2) field.get(game);
            for (int direction : new int[]{-1, 1}) {
                LookAround look = ready();
                advance(look, 3, direction);
                camera.set(0, look.offset());
                look.update(0, 0);
                for (int i = 0; i < 12; i++) {
                    look.update(1f / 60, 0);
                    game.moveCameraTo(new com.badlogic.gdx.math.Vector2(0, look.offset()), look.cameraSpeed(1400));
                }
                assertEquals(0, camera.y, .01, "The real chase speed must not stretch the return past 0.2 s");
            }
        } finally {
            com.badlogic.gdx.Gdx.graphics = previousGraphics;
        }
    }
}
