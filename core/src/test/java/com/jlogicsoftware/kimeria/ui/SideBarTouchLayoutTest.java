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
 * D54 / D56 (#226 follow-up): on a touch window that is not portrait the stick and buttons always go into the bars beside
 * the 640x360 picture (which shrinks to leave at least 104 units per bar), in both handedness modes, and the picture itself
 * is never a control. Positions are window pixels (top-down), as a finger reports them; "units" are controls-space units
 * (window pixels divided by the picture's pixels per unit; X from the window's left edge, Y from its top, the picture at
 * offset {@code sideBar()}, {@code sideTop()}).
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
    /** The shrunken, centred picture: pixels to 640x360 units. */
    private final Viewport picture = new Viewport() {
        @Override public Vector2 unproject(Vector2 v) { return v.set((v.x - (width - 640 * ppu) / 2f) / ppu, (v.y - (height - 360 * ppu) / 2f) / ppu); }
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

    private void window(int w, int h) { width = w; height = h; layout.update(w, h, true); ppu = layout.pictureScale(); }

    /** Controls-space Y of a picture-relative Y. */
    private float cy(float pictureY) { return layout.sideTop() + pictureY; }

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

    @Test void everyLandscapeTouchWindowGetsBarsOfAtLeast104UnitsAndAPictureOfAtLeast75PercentOfTheFullFit() {
        // {width, height, expected pixels per unit}: measured values, see D56.
        float[][] windows = {{844, 390, 0.9953f}, {844, 330, 0.9167f}, {844, 300, 0.8333f}, {852, 393, 1.0047f}, {932, 430, 1.0991f},
            {640, 360, 0.7547f}, {1280, 720, 1.5094f}, {800, 450, 0.9434f}, {1024, 768, 1.2075f}, {1366, 1024, 1.6108f},
            {680, 360, 0.8019f}, {2400, 1080, 2.8302f}, {500, 480, 0.5896f}};
        for (float[] w : windows) {
            window((int) w[0], (int) w[1]);
            String at = (int) w[0] + "x" + (int) w[1];
            assertTrue(layout.sideBars(), at); assertFalse(layout.portrait(), at);
            assertEquals(w[2], layout.pictureScale(), 1e-3f, at + " pixels per picture unit");
            assertTrue(layout.sideBar() >= TouchLayout.REQUIRED_BAR - 1e-3f, at + " bar " + layout.sideBar());
            assertEquals(640 + 2 * layout.sideBar(), layout.sideSpaceWidth(), 0.01f, at);
            assertEquals(360 + 2 * layout.sideTop(), layout.sideSpaceHeight(), 0.01f, at);
            assertTrue(layout.sideSpaceWidth() * layout.pictureScale() <= w[0] + 0.5f && layout.sideSpaceHeight() * layout.pictureScale() <= w[1] + 0.5f, at + " fits");
            float fullFit = Math.min(w[0] / 640f, w[1] / 360f);
            assertTrue(layout.pictureScale() >= 0.755f * fullFit - 1e-3f, at + " picture " + layout.pictureScale() / fullFit + " of the full fit");
        }
        window(844, 390);
        assertEquals(0.919f, layout.pictureScale() / (390 / 360f), 1e-3f, "iPhone landscape without toolbars: picture 91.9% of the full fit (637 px wide, was 693)");
        window(844, 330);
        assertEquals(140.4f, layout.sideBar(), 0.1f, "with toolbars the window already leaves more than 104: unchanged from D54");
        window(390, 844); assertFalse(layout.sideBars()); assertTrue(layout.portrait(), "portrait keeps the D53 strip");
        layout.update(844, 390, false);
        assertFalse(layout.sideBars(), "no touch screen: plain fit");
        assertEquals(0f, layout.pictureScale(), "no touch screen: the picture is not capped");
    }

    @Test void theControlsSpaceStartsAtTheWindowCornerAndThePictureIsCentredInIt() {
        window(844, 390);
        Vector2 v = new Vector2(0, 0);
        layout.controlsViewport().unproject(v);
        assertEquals(0f, v.x, 1e-4f); assertEquals(0f, v.y, 1e-4f);
        v.set(844, 390);
        layout.controlsViewport().unproject(v);
        assertEquals(layout.sideSpaceWidth(), v.x, 1e-2f);
        assertEquals(layout.sideSpaceHeight(), v.y, 1e-2f);
        // The picture's top-left corner in controls space is exactly (bar, top).
        v.set((844 - 640 * ppu) / 2f, (390 - 360 * ppu) / 2f);
        layout.controlsViewport().unproject(v);
        assertEquals(layout.sideBar(), v.x, 1e-2f);
        assertEquals(layout.sideTop(), v.y, 1e-2f);
        assertEquals(layout.sideBar(), (layout.sideSpaceWidth() - 640) / 2f, 1e-3f, "bars are symmetric");
    }

    @Test void everyControlIsCompletelyInsideItsBarOnEveryWindowInBothHandedModes() {
        int[][] windows = {{844, 390}, {844, 330}, {640, 360}, {1280, 720}, {1024, 768}, {2400, 1080}, {500, 480}};
        for (int[] w : windows) {
            window(w[0], w[1]);
            float bar = layout.sideBar(), space = layout.sideSpaceWidth();
            for (boolean left : new boolean[]{false, true}) {
                String at = w[0] + "x" + w[1] + " left-handed " + left;
                float stick = mx(TouchControls.sideStickX(bar), left);
                float buttons = mx(space - TouchControls.sideButtonInset(bar), left);
                float lo = left ? space - bar : 0, hi = left ? space : bar; // the stick's bar
                assertTrue(stick - 44 >= lo + 8 - 1e-3f && stick + 44 <= hi - 8 + 1e-3f, "stick drawn inside its bar with its margin, " + at);
                float blo = left ? 0 : space - bar, bhi = left ? bar : space; // the buttons' bar
                assertTrue(buttons - 38.4f >= blo && buttons + 38.4f <= bhi, "button hit circle inside its bar, " + at);
                assertTrue(left ? stick > space / 2 && buttons < space / 2 : stick < space / 2 && buttons > space / 2, "sides, " + at);
            }
        }
    }

    @Test void atTheMinimumBarTheControlsAreCentredAndTheColumnFitsTheHeight() {
        window(640, 360);
        float bar = layout.sideBar();
        assertEquals(104f, bar, 1e-2f);
        assertEquals(52f, TouchControls.sideStickX(bar), 1e-3f, "stick: centred in the bar, 44 radius + 8 margin each side");
        assertEquals(52f, TouchControls.sideButtonInset(bar), 1e-3f, "buttons: centred in the bar, 38.4 hit radius + 13.6 spare");
        float[] ys = {TouchControls.SIDE_JUMP_Y, TouchControls.SIDE_SHOOT_Y, TouchControls.SIDE_ACTION_Y};
        for (float y : ys) assertTrue(y - 38.4f >= 0 && y + 38.4f <= 360, "button column inside the height at y " + y);
        assertTrue(TouchControls.SIDE_STICK_Y + 52 <= 360 + 1e-3f);
        assertTrue(ys[0] - ys[1] >= 76.8f && ys[1] - ys[2] >= 76.8f, "hit circles do not overlap");
    }

    @Test void theStickAndTheButtonsWorkFromTheBarsInBothHandedModesAndOnEveryWindow() {
        int[][] windows = {{844, 390}, {844, 330}, {932, 430}, {640, 360}, {1280, 720}, {1024, 768}};
        for (int[] w : windows) {
            window(w[0], w[1]);
            float bar = layout.sideBar(), space = layout.sideSpaceWidth();
            for (boolean left : new boolean[]{false, true}) {
                leftHanded = left;
                float sx = mx(TouchControls.sideStickX(bar), left), bx = mx(space - TouchControls.sideButtonInset(bar), left);
                String at = w[0] + "x" + w[1] + " left-handed " + left;

                TouchControls stick = controls();
                stick.update(true);
                touchUnits(sx + 40, cy(TouchControls.SIDE_STICK_Y));
                stick.update(true);
                assertEquals(1f, stick.horizontal(), at + " stick right");
                down = false; stick.update(true);
                touchUnits(sx - 40, cy(TouchControls.SIDE_STICK_Y));
                stick.update(true);
                assertEquals(-1f, stick.horizontal(), at + " stick left");
                down = false; stick.update(true);

                TouchControls jump = controls();
                jump.update(true);
                touchUnits(bx, cy(TouchControls.SIDE_JUMP_Y));
                jump.update(true);
                assertTrue(jump.jumpHeld(), at + " jump");
                down = false; jump.update(true);

                TouchControls shoot = controls();
                shoot.update(true);
                touchUnits(bx, cy(TouchControls.SIDE_SHOOT_Y));
                shoot.update(true);
                assertTrue(shoot.shootPressed(), at + " ranged");
                down = false; shoot.update(true);

                TouchControls action = controls();
                action.update(true);
                touchUnits(bx, cy(TouchControls.SIDE_ACTION_Y));
                action.update(true);
                assertTrue(action.actionPressed(), at + " action");
                down = false; action.update(true);
            }
        }
    }

    @Test void aTouchAnywhereOnThePictureNeverDrivesTheControlsOnAnyWindow() {
        for (int[] w : new int[][]{{844, 390}, {844, 330}, {640, 360}, {1280, 720}, {1024, 768}}) {
            window(w[0], w[1]);
            float bar = layout.sideBar();
            for (boolean left : new boolean[]{false, true}) {
                leftHanded = left;
                for (float px = 1; px < 640; px += 8) {
                    for (float py = 1; py < 360; py += 8) {
                        TouchControls controls = controls();
                        controls.update(true);
                        touchUnits(bar + px, cy(py));
                        controls.update(true);
                        assertFalse(anyControl(controls), "picture spot " + px + "," + py + " on " + w[0] + "x" + w[1] + " left-handed " + left);
                        down = false; controls.update(true);
                    }
                }
            }
        }
    }

    @Test void theOldOverPictureSpotsDoNotDriveAnythingOnAnIPhoneWindow() {
        window(844, 390);
        float[][] landscapeSpots = {{80, 280}, {590, 290}, {520, 318}, {536, 244}, {320, 180}};
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            for (float[] c : landscapeSpots) {
                TouchControls controls = controls();
                controls.update(true);
                touchUnits(layout.sideBar() + (left ? 640 - c[0] : c[0]), cy(c[1]));
                controls.update(true);
                assertFalse(anyControl(controls), "picture spot " + c[0] + "," + c[1] + " left-handed " + left);
                down = false; controls.update(true);
            }
        }
    }

    @Test void thePauseButtonStillWorksAtTopCentreOfThePictureAndABarTouchDoesNotPause() {
        for (int[] w : new int[][]{{844, 390}, {844, 330}, {640, 360}, {1280, 720}}) {
            window(w[0], w[1]);
            for (boolean left : new boolean[]{false, true}) {
                leftHanded = left;
                TouchControls controls = controls();
                controls.update(true);
                touchUnits(layout.sideBar() + TouchControls.PAUSE_X, cy(TouchControls.PAUSE_Y));
                controls.update(true);
                assertTrue(controls.pausePressed(), w[0] + "x" + w[1] + " left-handed " + left);
                down = false; controls.update(true);

                TouchControls bars = controls();
                bars.update(true);
                touchUnits(layout.sideBar() / 2f, cy(TouchControls.PAUSE_Y));
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
        touchUnits(layout.sideBar() + 300, cy(270));
        controls.update(true);
        touchUnits(layout.sideBar() / 2f, cy(TouchControls.SIDE_STICK_Y));
        controls.update(true);
        assertFalse(anyControl(controls));
    }

    @Test void thePauseTargetStaysAtLeast48PixelsAcrossWhenThePictureShrinks() {
        window(640, 360); // 0.755 px per unit: the 26-unit hit circle would be only 39 px across
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            TouchControls inside = controls();
            inside.update(true);
            touchUnits(layout.sideBar() + TouchControls.PAUSE_X + 30, cy(TouchControls.PAUSE_Y));
            inside.update(true);
            assertTrue(inside.pausePressed(), "30 units = 22.6 px from the centre");
            down = false; inside.update(true);
            TouchControls outside = controls();
            outside.update(true);
            touchUnits(layout.sideBar() + TouchControls.PAUSE_X + 40, cy(TouchControls.PAUSE_Y));
            outside.update(true);
            assertFalse(outside.pausePressed(), "40 units = 30 px is outside");
            down = false; outside.update(true);
        }
    }

    /** D57: the web canvas is in physical pixels, so a window is ratio x CSS size; the finger-sized rules must not shrink. */
    @Test void thePauseTargetIsMeasuredInCssPixelsOnAPhysicalPixelCanvas() {
        // 640x360 CSS at ratio 1 and the same window backed with 3x pixels must hit the same CSS area.
        for (boolean left : new boolean[]{false, true}) {
            leftHanded = left;
            layout.setPixelRatio(3f);
            window(1920, 1080); // 2.264 px per unit; 24 CSS px = 72 px = 31.8 units, as 24 / 0.755 at ratio 1
            TouchControls inside = controls();
            inside.update(true);
            touchUnits(layout.sideBar() + TouchControls.PAUSE_X + 30, cy(TouchControls.PAUSE_Y));
            inside.update(true);
            assertTrue(inside.pausePressed(), "30 units inside at ratio 3");
            down = false; inside.update(true);
            TouchControls outside = controls();
            outside.update(true);
            touchUnits(layout.sideBar() + TouchControls.PAUSE_X + 40, cy(TouchControls.PAUSE_Y));
            outside.update(true);
            assertFalse(outside.pausePressed(), "40 units outside at ratio 3");
            down = false; outside.update(true);
            layout.setPixelRatio(1f);
        }
    }

    @Test void thePixelRatioNeverChangesTheLayoutInPictureUnitsAndIsAtLeastOne() {
        window(844, 390);
        float bar = layout.sideBar();
        layout.setPixelRatio(3f);
        window(844 * 3, 390 * 3);
        assertEquals(bar, layout.sideBar(), 1e-3f, "the same CSS window gives the same bars in units");
        assertEquals(3f, layout.pixelRatio());
        layout.setPixelRatio(0.2f);
        assertEquals(1f, layout.pixelRatio(), "a ratio below 1 is clamped");
    }
}
