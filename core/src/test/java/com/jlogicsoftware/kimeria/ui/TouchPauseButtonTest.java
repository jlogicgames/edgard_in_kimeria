package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** The touch pause button (#259): the only way a touch-only player reaches the pause menu. */
class TouchPauseButtonTest {
    private static final float W = 640, H = 360;
    private final Vector2 finger = new Vector2();
    private boolean down;
    private boolean leftHanded;
    private Input previousInput;
    private Application previousApp;

    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "leftHanded" -> leftHanded;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
    /** Identity projection: logical coordinates in, logical coordinates out. */
    private final Viewport identity = new Viewport() {
        @Override public Vector2 unproject(Vector2 v) { return v; }
    };

    @BeforeEach void setUp() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        previousInput = Gdx.input; previousApp = Gdx.app;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isTouched" -> down && (int) args[0] == 0;
            case "getX" -> (int) finger.x;
            case "getY" -> (int) finger.y;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.Android : null);
    }

    @AfterEach void restore() { Gdx.input = previousInput; Gdx.app = previousApp; }

    private TouchControls controls() { return new TouchControls(assets, game, identity, W, H, true); }

    private void touchAt(float x, float y) { finger.set(x, y); down = true; }
    private void release() { down = false; }

    private boolean pauseTapAt(TouchControls controls, float x, float y, boolean active, boolean pausable) {
        release();
        controls.update(active, pausable);
        touchAt(x, y);
        controls.update(active, pausable);
        boolean pressed = controls.pausePressed();
        release();
        controls.update(active, pausable);
        return pressed;
    }

    @Test void aTapOnTheButtonPausesOnceAndOnlyOnTheFrameItLandsOn() {
        TouchControls controls = controls();
        controls.update(true);
        touchAt(TouchControls.PAUSE_X, TouchControls.PAUSE_Y);
        controls.update(true);
        assertTrue(controls.pausePressed());
        controls.update(true);
        assertFalse(controls.pausePressed(), "holding the finger down is not a second press");
        assertFalse(controls.jumpHeld() || controls.shootHeld() || controls.actionHeld() || controls.horizontal() != 0);
    }

    @Test void theButtonWorksInGameplayAndInHealerDialogue() {
        assertTrue(pauseTapAt(controls(), TouchControls.PAUSE_X, TouchControls.PAUSE_Y, true, true), "gameplay");
        assertTrue(pauseTapAt(controls(), TouchControls.PAUSE_X, TouchControls.PAUSE_Y, false, true), "healer dialogue");
    }

    @Test void theButtonIsInactiveInMenusAndWhilePaused() {
        assertFalse(pauseTapAt(controls(), TouchControls.PAUSE_X, TouchControls.PAUSE_Y, false, false));
    }

    @Test void aFingerThatStartedElsewhereAndSlidesOntoTheButtonDoesNotPause() {
        TouchControls controls = controls();
        controls.update(true);
        touchAt(80, 280); // the stick
        controls.update(true);
        touchAt(TouchControls.PAUSE_X, TouchControls.PAUSE_Y);
        controls.update(true);
        assertFalse(controls.pausePressed());
    }

    @Test void theTapThatDismissesAMenuDoesNotPauseTheNextFrame() {
        TouchControls controls = controls();
        touchAt(TouchControls.PAUSE_X, TouchControls.PAUSE_Y);
        controls.update(false, false); // a menu was showing under the finger
        controls.update(true, true);   // play resumes with the finger still down
        assertFalse(controls.pausePressed());
    }

    @Test void theButtonStaysInPlaceInBothHandednessModesAndNeverTakesAnotherControlsTouch() {
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            assertTrue(pauseTapAt(controls(), TouchControls.PAUSE_X, TouchControls.PAUSE_Y, true, true), "left-handed " + left);
            // Every other control's centre, mirrored with the layout, must not pause.
            float[][] others = {{80, 280}, {590, 290}, {520, 318}, {536, 244}};
            for (float[] c : others) {
                float x = left ? W - c[0] : c[0];
                assertFalse(pauseTapAt(controls(), x, c[1], true, true), "control at " + x + "," + c[1] + " left-handed " + left);
            }
        }
    }

    @Test void thePauseTargetIsThumbSizedAndEveryPointOfItPausesWithoutMovingThePlayer() {
        assertTrue(TouchControls.PAUSE_HIT_RADIUS * 2 >= 48, "at least 48 logical px across");
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            for (int a = 0; a < 360; a += 30) for (float r : new float[]{0, TouchControls.PAUSE_HIT_RADIUS - 1}) {
                float x = TouchControls.PAUSE_X + r * (float) Math.cos(Math.toRadians(a));
                float y = TouchControls.PAUSE_Y + r * (float) Math.sin(Math.toRadians(a));
                TouchControls controls = controls();
                assertTrue(pauseTapAt(controls, x, y, true, true), x + "," + y);
                assertEquals(0f, controls.horizontal());
            }
        }
    }

    @Test void theButtonSitsInTheTopStripClearOfTheHudAndTheStickAndButtons() {
        float bottom = TouchControls.PAUSE_Y + TouchControls.PAUSE_HIT_RADIUS;
        float left = TouchControls.PAUSE_X - TouchControls.PAUSE_HIT_RADIUS, right = TouchControls.PAUSE_X + TouchControls.PAUSE_HIT_RADIUS;
        assertTrue(TouchControls.PAUSE_Y + TouchControls.PAUSE_RADIUS <= 32, "drawn part ends above the boss label (BossHud LABEL_TOP 32)");
        assertTrue(bottom < 244 - 32 * 1.2f * 0.85f, "above the highest action button");
        assertTrue(left > 200 && right < 640 - 10 - 3 * 32 - 2 * 4 - 4, "between the coin/artifact HUD and the fireball stock");
    }
}
