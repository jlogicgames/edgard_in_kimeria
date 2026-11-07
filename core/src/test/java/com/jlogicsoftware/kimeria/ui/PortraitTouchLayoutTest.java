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

/**
 * #226: in portrait the stick and buttons live in the strip under the 640x360 picture, never over it, in both
 * handedness modes, and the pause button (D52) still works at its place in the picture. All positions here are
 * window pixels (top-down), as a finger reports them.
 */
class PortraitTouchLayoutTest {
    private static final int PHONE_W = 390, PHONE_H = 844;
    private static final float PICTURE_SCALE = PHONE_W / 640f;
    private final Vector2 finger = new Vector2();
    private boolean down, leftHanded;
    private Input previousInput;
    private Application previousApp;
    private final TouchLayout layout = new TouchLayout(640, 360);

    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "leftHanded" -> leftHanded;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
    /** The picture fitted to the width and pinned to the top: pixels to 640x360 units. */
    private final Viewport picture = new Viewport() {
        @Override public Vector2 unproject(Vector2 v) { return v.set(v.x / PICTURE_SCALE, v.y / PICTURE_SCALE); }
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
        layout.update(PHONE_W, PHONE_H, true);
    }

    @AfterEach void restore() { Gdx.input = previousInput; Gdx.app = previousApp; }

    private TouchControls controls() {
        TouchControls controls = new TouchControls(assets, game, picture, 640, 360, true);
        controls.setLayout(layout);
        return controls;
    }

    private void touchAt(float x, float y) { finger.set(x, y); down = true; }

    /** Window pixel of a point in the strip's own space. */
    private float[] stripPixel(float unitsX, float unitsY) {
        float ppu = PHONE_W / TouchLayout.SPACE_WIDTH;
        return new float[]{unitsX * ppu, layout.gameScreenHeight() + unitsY * ppu};
    }

    @Test void aPortraitPhoneGetsTheStripAndLandscapeOrNonTouchDoNot() {
        assertTrue(layout.portrait());
        assertEquals(219, layout.gameScreenHeight(), "640x360 fitted to 390 px wide");
        assertEquals(PHONE_H - 219, layout.controlsScreenHeight());
        assertTrue(layout.spaceHeight() >= TouchLayout.MIN_SPACE_HEIGHT);
        TouchLayout other = new TouchLayout(640, 360);
        other.update(844, 390, true);
        assertFalse(other.portrait(), "landscape phone: no strip (side bars instead, D54)");
        other.update(PHONE_W, PHONE_H, false);
        assertFalse(other.portrait(), "no touch screen: plain fit");
        other.update(1024, 768, true);
        assertFalse(other.portrait(), "a 4:3 tablet window leaves too little room under the picture");
        other.update(768, 1024, true);
        assertTrue(other.portrait(), "a portrait tablet does");
        other.update(200, 100, true);
        assertFalse(other.portrait());
    }

    @Test void theStripStartsWhereThePictureEndsAndMapsItsPixelsToUnits() {
        Vector2 v = new Vector2(0, layout.gameScreenHeight());
        layout.controlsViewport().unproject(v);
        assertEquals(0f, v.x, 1e-4f); assertEquals(0f, v.y, 1e-4f);
        v.set(PHONE_W, PHONE_H);
        layout.controlsViewport().unproject(v);
        assertEquals(TouchLayout.SPACE_WIDTH, v.x, 1e-3f);
        assertEquals(layout.spaceHeight(), v.y, 1e-2f);
    }

    @Test void everyControlIsInTheStripAndDoesNotOverlapThePictureInEitherHandedness() {
        float[][] centres = {{TouchControls.PORTRAIT_STICK_X, 0}, {TouchControls.PORTRAIT_JUMP_X, TouchControls.PORTRAIT_JUMP_DY},
            {TouchControls.PORTRAIT_ACTION_X, TouchControls.PORTRAIT_ACTION_DY}, {TouchControls.PORTRAIT_SHOOT_X, TouchControls.PORTRAIT_SHOOT_DY}};
        float cy = layout.spaceHeight() / 2f + TouchControls.PORTRAIT_CLUSTER_DY;
        for (int i = 0; i < centres.length; i++) {
            float[] c = centres[i];
            float grab = i == 0 ? 76f : 38.4f; // stick grab radius, button hit radius
            assertTrue(cy + c[1] - grab >= 0 && cy + c[1] + grab <= layout.spaceHeight(), "inside the strip, with the grab radius, at dy " + c[1]);
            assertTrue(c[0] - grab >= 0 && c[0] + grab <= TouchLayout.SPACE_WIDTH, "inside the width at x " + c[0]);
        }
        // Rotate hint line sits above the highest control.
        assertTrue(TouchControls.PORTRAIT_HINT_Y + 14 < cy + TouchControls.PORTRAIT_SHOOT_DY - 32);
    }

    @Test void theStickAndTheButtonsWorkFromTheStripMirroredForLeftHandedPlay() {
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            float cy = layout.spaceHeight() / 2f + TouchControls.PORTRAIT_CLUSTER_DY;
            java.util.function.Function<Float, Float> mx = x -> left ? TouchLayout.SPACE_WIDTH - x : x;

            TouchControls stick = controls();
            stick.update(true);
            float[] p = stripPixel(mx.apply(TouchControls.PORTRAIT_STICK_X) + 40, cy);
            touchAt(p[0], p[1]);
            stick.update(true);
            assertEquals(1f, stick.horizontal(), "stick pushed right, left-handed " + left);
            down = false; stick.update(true);

            TouchControls jump = controls();
            jump.update(true);
            p = stripPixel(mx.apply(TouchControls.PORTRAIT_JUMP_X), cy + TouchControls.PORTRAIT_JUMP_DY);
            touchAt(p[0], p[1]);
            jump.update(true);
            assertTrue(jump.jumpHeld(), "jump, left-handed " + left);
            down = false; jump.update(true);

            TouchControls shoot = controls();
            shoot.update(true);
            p = stripPixel(mx.apply(TouchControls.PORTRAIT_SHOOT_X), cy + TouchControls.PORTRAIT_SHOOT_DY);
            touchAt(p[0], p[1]);
            shoot.update(true);
            assertTrue(shoot.shootPressed(), "ranged, left-handed " + left);
            down = false; shoot.update(true);

            TouchControls action = controls();
            action.update(true);
            p = stripPixel(mx.apply(TouchControls.PORTRAIT_ACTION_X), cy + TouchControls.PORTRAIT_ACTION_DY);
            touchAt(p[0], p[1]);
            action.update(true);
            assertTrue(action.actionPressed(), "action, left-handed " + left);
            down = false; action.update(true);
        }
    }

    @Test void touchesOnThePictureNeverDriveTheStickOrButtonsInPortrait() {
        // Where the landscape layout puts the stick and buttons (640x360 units), now converted to picture pixels.
        float[][] landscapeSpots = {{80, 280}, {590, 290}, {520, 318}, {536, 244}};
        for (float[] c : landscapeSpots) {
            TouchControls controls = controls();
            controls.update(true);
            touchAt(c[0] * PICTURE_SCALE, c[1] * PICTURE_SCALE);
            controls.update(true);
            assertFalse(controls.jumpHeld() || controls.shootHeld() || controls.actionHeld() || controls.horizontal() != 0
                || controls.shootPressed() || controls.actionPressed(), "picture spot " + c[0] + "," + c[1]);
            down = false; controls.update(true);
        }
    }

    @Test void thePauseButtonStillWorksInThePictureAtItsLogicalSpotAndIsBiggerForThumbs() {
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            TouchControls controls = controls();
            controls.update(true);
            touchAt(TouchControls.PAUSE_X * PICTURE_SCALE, TouchControls.PAUSE_Y * PICTURE_SCALE);
            controls.update(true);
            assertTrue(controls.pausePressed(), "left-handed " + left);
            down = false; controls.update(true);

            // Edge of the portrait target: 35 units off centre pauses (it would not at the landscape radius 26).
            TouchControls edge = controls();
            edge.update(true);
            touchAt((TouchControls.PAUSE_X + 35) * PICTURE_SCALE, TouchControls.PAUSE_Y * PICTURE_SCALE);
            edge.update(true);
            assertTrue(edge.pausePressed(), "wider portrait target, left-handed " + left);
            down = false; edge.update(true);
        }
        assertTrue(TouchControls.PAUSE_HIT_RADIUS_PORTRAIT * PICTURE_SCALE * 2 >= 44, "at least 44 px across on a 390 px phone");
    }

    /** D54: a phone window wider than 16:9 now gets side bars, so "controls over the picture" is checked on a 4:3 tablet window. */
    @Test void theLandscapeLayoutIsUnchangedWithoutAPortraitLayout() {
        layout.update(1024, 768, true);
        assertFalse(layout.portrait() || layout.sideBars());
        TouchControls controls = new TouchControls(assets, game, new Viewport() {
            @Override public Vector2 unproject(Vector2 v) { return v; }
        }, 640, 360, true);
        controls.setLayout(layout);
        controls.update(true);
        touchAt(80 + 40, 280);
        controls.update(true);
        assertEquals(1f, controls.horizontal());
    }
}
