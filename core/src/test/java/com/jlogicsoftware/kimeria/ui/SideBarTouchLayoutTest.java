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
 * D54 (#226 follow-up): in landscape the stick and buttons go into the empty bars beside the 640x360 picture, in both
 * handedness modes, and the picture itself is never a control. Positions are window pixels (top-down), as a finger
 * reports them; "units" are picture units (one picture pixel row = 1 / (height / 360) window pixels).
 */
class SideBarTouchLayoutTest {
    private final Vector2 finger = new Vector2();
    private boolean down, leftHanded;
    private Input previousInput;
    private Application previousApp;
    private final TouchLayout layout = new TouchLayout(640, 360);
    private int width, height;
    private float ppu;

    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "leftHanded" -> leftHanded;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
    /** The height-fitted picture, centred: pixels to 640x360 units. */
    private final Viewport picture = new Viewport() {
        @Override public Vector2 unproject(Vector2 v) { return v.set((v.x - (width - 640 * ppu) / 2f) / ppu, v.y / ppu); }
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

    private void window(int w, int h) { width = w; height = h; ppu = h / 360f; layout.update(w, h, true); }

    private TouchControls controls() {
        TouchControls controls = new TouchControls(assets, game, picture, 640, 360, true);
        controls.setLayout(layout);
        return controls;
    }

    private void touchUnits(float ux, float uy) { finger.set(ux * ppu, uy * ppu); down = true; }

    private float mx(float x, boolean left) { return left ? layout.sideSpaceWidth() - x : x; }

    private boolean anyControl(TouchControls c) {
        return c.jumpHeld() || c.shootHeld() || c.actionHeld() || c.horizontal() != 0 || c.upHeld() || c.downHeld()
            || c.shootPressed() || c.actionPressed();
    }

    @Test void theSideBarLayoutIsChosenOnlyForATouchWindowWiderThanSixteenByNineWithRoomForABar() {
        window(844, 390); // iPhone 14 landscape, no browser toolbars: picture 693 px, bars 75 px
        assertTrue(layout.sideBars()); assertFalse(layout.portrait());
        assertEquals(69.5f, layout.sideBar(), 0.1f);
        assertEquals(640 + 2 * layout.sideBar(), layout.sideSpaceWidth(), 0.01f);
        window(844, 330); // 19.5:9 with browser toolbars eating height: bars 128 px
        assertTrue(layout.sideBars());
        assertEquals(140f, layout.sideBar(), 1f);
        window(844, 300);
        assertTrue(layout.sideBars());
        window(852, 393); window(932, 430); window(2400, 1080);
        assertTrue(layout.sideBars());

        window(640, 360); assertFalse(layout.sideBars(), "exactly 16:9: no bars");
        window(1280, 720); assertFalse(layout.sideBars());
        window(800, 450); assertFalse(layout.sideBars());
        window(1024, 768); assertFalse(layout.sideBars(), "4:3 tablet window");
        window(1366, 1024); assertFalse(layout.sideBars());
        window(680, 360); assertFalse(layout.sideBars(), "bar of 20 units is below the minimum");
        window(700, 360); assertTrue(layout.sideBars(), "bar of 30 units");
        window(390, 844); assertFalse(layout.sideBars()); assertTrue(layout.portrait(), "portrait keeps the D53 strip");
        layout.update(844, 390, false);
        assertFalse(layout.sideBars(), "no touch screen: plain fit");
    }

    @Test void theControlsSpaceStartsAtTheWindowEdgeAndTheCentreIsThePicture() {
        window(844, 390);
        Vector2 v = new Vector2(0, 0);
        layout.controlsViewport().unproject(v);
        assertEquals(0f, v.x, 1e-4f); assertEquals(0f, v.y, 1e-4f);
        v.set(844, 390);
        layout.controlsViewport().unproject(v);
        assertEquals(layout.sideSpaceWidth(), v.x, 1e-2f);
        assertEquals(360f, v.y, 1e-2f);
        // The picture's left edge in controls space is exactly the bar width.
        v.set((844 - 640 * ppu) / 2f, 0);
        layout.controlsViewport().unproject(v);
        assertEquals(layout.sideBar(), v.x, 1e-2f);
    }

    @Test void inABarThatIsWideEnoughEveryControlIsCompletelyOutsideThePictureInBothHandedModes() {
        window(844, 330);
        float bar = layout.sideBar(), space = layout.sideSpaceWidth();
        assertTrue(bar >= 2 * (44 + 8), "stick (radius 44) fits with its margin");
        for (boolean left : new boolean[]{false, true}) {
            float stick = mx(TouchControls.sideStickX(bar), left);
            float buttons = mx(space - TouchControls.sideButtonInset(bar), left);
            float lo = left ? space - bar : 0, hi = left ? space : bar; // the stick's bar
            assertTrue(stick - 44 >= lo && stick + 44 <= hi, "stick drawn inside its bar, left-handed " + left);
            assertTrue(stick - TouchControls.SIDE_STICK_GRAB >= lo - 8 && stick + TouchControls.SIDE_STICK_GRAB <= hi + 8,
                "stick grab at most a margin beyond its bar, left-handed " + left);
            float blo = left ? 0 : space - bar, bhi = left ? bar : space; // the buttons' bar
            assertTrue(buttons - 38.4f >= blo && buttons + 38.4f <= bhi, "button hit circle inside its bar, left-handed " + left);
            assertTrue(left ? stick > space / 2 && buttons < space / 2 : stick < space / 2 && buttons > space / 2, "sides, left-handed " + left);
        }
    }

    @Test void inANarrowBarTheControlsSitAsFarOutAsFitsAndStayInsideTheWindow() {
        window(844, 390);
        float bar = layout.sideBar(), space = layout.sideSpaceWidth();
        assertEquals(52f, TouchControls.sideStickX(bar), 1e-3f, "stick: 44 radius + 8 margin from the left edge");
        assertEquals(46.4f, TouchControls.sideButtonInset(bar), 1e-3f, "buttons: 38.4 hit radius + 8 margin from the right edge");
        assertTrue(TouchControls.sideStickX(bar) + 44 - bar < 40, "stick overlaps the picture by less than 40 units");
        assertTrue(TouchControls.sideButtonInset(bar) + 32 - bar < 10, "buttons overlap the picture by less than 10 units");
        float[] ys = {TouchControls.SIDE_JUMP_Y, TouchControls.SIDE_SHOOT_Y, TouchControls.SIDE_ACTION_Y};
        for (float y : ys) assertTrue(y - 38.4f >= 0 && y + 38.4f <= 360, "button column inside the height at y " + y);
        assertTrue(TouchControls.SIDE_STICK_Y + TouchControls.SIDE_STICK_GRAB <= 360 + 1e-3f);
        assertTrue(ys[0] - ys[1] >= 76.8f && ys[1] - ys[2] >= 76.8f, "hit circles do not overlap");
        assertTrue(space - TouchControls.sideButtonInset(bar) + 38.4f <= space);
    }

    @Test void theStickAndTheButtonsWorkFromTheBarsInBothHandedModesAndOnEveryWindow() {
        int[][] windows = {{844, 390}, {844, 330}, {932, 430}, {700, 360}};
        for (int[] w : windows) {
            window(w[0], w[1]);
            float bar = layout.sideBar(), space = layout.sideSpaceWidth();
            for (boolean left : new boolean[]{false, true}) {
                leftHanded = left;
                float sx = mx(TouchControls.sideStickX(bar), left), bx = mx(space - TouchControls.sideButtonInset(bar), left);
                String at = w[0] + "x" + w[1] + " left-handed " + left;

                TouchControls stick = controls();
                stick.update(true);
                touchUnits(sx + 40, TouchControls.SIDE_STICK_Y);
                stick.update(true);
                assertEquals(1f, stick.horizontal(), at + " stick right");
                down = false; stick.update(true);
                touchUnits(sx - 40, TouchControls.SIDE_STICK_Y);
                stick.update(true);
                assertEquals(-1f, stick.horizontal(), at + " stick left");
                down = false; stick.update(true);

                TouchControls jump = controls();
                jump.update(true);
                touchUnits(bx, TouchControls.SIDE_JUMP_Y);
                jump.update(true);
                assertTrue(jump.jumpHeld(), at + " jump");
                down = false; jump.update(true);

                TouchControls shoot = controls();
                shoot.update(true);
                touchUnits(bx, TouchControls.SIDE_SHOOT_Y);
                shoot.update(true);
                assertTrue(shoot.shootPressed(), at + " ranged");
                down = false; shoot.update(true);

                TouchControls action = controls();
                action.update(true);
                touchUnits(bx, TouchControls.SIDE_ACTION_Y);
                action.update(true);
                assertTrue(action.actionPressed(), at + " action");
                down = false; action.update(true);
            }
        }
    }

    @Test void aTouchAnywhereOnThePictureNeverDrivesTheControlsWhenTheBarsAreWideEnough() {
        window(844, 330);
        float bar = layout.sideBar();
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            for (float px = 0; px <= 640; px += 16) {
                for (float py = 0; py <= 360; py += 12) {
                    TouchControls controls = controls();
                    controls.update(true);
                    touchUnits(bar + px, py);
                    controls.update(true);
                    boolean grabbed = anyControl(controls);
                    assertFalse(grabbed && px > 0 && px < 640 && !nearEdge(px), "picture spot " + px + "," + py + " left-handed " + left);
                    down = false; controls.update(true);
                }
            }
        }
    }

    /** The stick grab is allowed to reach one margin (8 units) into the picture; nothing deeper. */
    private boolean nearEdge(float pictureX) { return pictureX <= 8 || pictureX >= 640 - 8; }

    @Test void theOldOverPictureSpotsDoNotDriveAnythingOnAnIPhoneWindow() {
        window(844, 390);
        float[][] landscapeSpots = {{80, 280}, {590, 290}, {520, 318}, {536, 244}, {320, 180}};
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            for (float[] c : landscapeSpots) {
                TouchControls controls = controls();
                controls.update(true);
                touchUnits(layout.sideBar() + (left ? 640 - c[0] : c[0]), c[1]);
                controls.update(true);
                assertFalse(anyControl(controls), "picture spot " + c[0] + "," + c[1] + " left-handed " + left);
                down = false; controls.update(true);
            }
        }
    }

    @Test void thePauseButtonStillWorksAtTopCentreOfThePictureAndABarTouchDoesNotPause() {
        for (int[] w : new int[][]{{844, 390}, {844, 330}}) {
            window(w[0], w[1]);
            for (boolean left : new boolean[]{false, true}) {
                leftHanded = left;
                TouchControls controls = controls();
                controls.update(true);
                touchUnits(layout.sideBar() + TouchControls.PAUSE_X, TouchControls.PAUSE_Y);
                controls.update(true);
                assertTrue(controls.pausePressed(), w[0] + "x" + w[1] + " left-handed " + left);
                down = false; controls.update(true);

                TouchControls bars = controls();
                bars.update(true);
                touchUnits(layout.sideBar() / 2f, TouchControls.PAUSE_Y);
                bars.update(true);
                assertFalse(bars.pausePressed());
                down = false; bars.update(true);
            }
        }
    }

    @Test void aFingerThatStartedOnThePictureAndSlidesIntoTheBarDoesNotDriveAControl() {
        window(844, 330);
        leftHanded = false;
        TouchControls controls = controls();
        controls.update(true);
        touchUnits(layout.sideBar() + 300, 270);
        controls.update(true);
        touchUnits(layout.sideBar() / 2f, TouchControls.SIDE_STICK_Y);
        controls.update(true);
        assertFalse(anyControl(controls));
    }
}
