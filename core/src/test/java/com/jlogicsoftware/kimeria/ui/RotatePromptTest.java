package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D140 (#493): the large rotate prompt of the portrait strip. Geometry comes from the real {@link TouchLayout} at the reference phone windows and at
 * short ones; the controls cluster's extents come from the constants of {@link TouchControls} the cluster and the touch tutorial are drawn with.
 * Windows are in backbuffer pixels (CSS pixels here: one per pixel).
 */
class RotatePromptTest {
    private record Window(String name, int width, int height) { }

    /** The three phones named in the brief. Each has room for the pictogram. */
    private static final Window[] PHONES = {new Window("390x844", 390, 844), new Window("360x800", 360, 800), new Window("412x915", 412, 915)};
    /** Shorter portrait windows: a phone with the browser's toolbars out, and ones down to the shortest the strip exists in (strip of 190 units). */
    private static final Window[] SHORT = {new Window("390x664 toolbars", 390, 664), new Window("360x640", 360, 640), new Window("360x560", 360, 560),
        new Window("360x480", 360, 480), new Window("360x410 shortest", 360, 410),
        new Window("768x1024 tablet", 768, 1024)};

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();
    /** Real glyph metrics for the text, no textures for the art: the draw calls are counted, not rasterised. */
    private final Assets assets = new Assets() {
        @Override public BitmapFont font(int size) { return new MetricsFont(size); }
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
    };

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { SoftDot.dispose(); Shaders.dispose(); harness.restoreGlobals(); }

    private static TouchLayout layoutOf(Window window, boolean touch) {
        TouchLayout layout = new TouchLayout(640, 360);
        layout.update(window.width(), window.height(), touch);
        return layout;
    }

    /** The tallest the prompt's text gets in any of the five languages: the layout the game builds, in the real 30 px font. */
    private static float tallestText() {
        MetricsFont font = new MetricsFont(FontSizes.ROTATE_PROMPT);
        float tallest = 0f;
        for (Language language : Language.values()) {
            GlyphLayout text = new GlyphLayout(font, Msg.ROTATE_HINT.t(language), Color.WHITE, RotatePrompt.TEXT_WIDTH, Align.center, true);
            assertTrue(text.runs.size <= 2, language + ": at most two lines, " + text.runs.size);
            tallest = Math.max(tallest, text.height);
        }
        return tallest;
    }

    private TouchControls controlsOn(TouchLayout layout, boolean platformTouch) {
        TouchControls controls = new TouchControls(assets, harness.game(), new ScreenViewport(), 640, 360, platformTouch);
        controls.setLayout(layout);
        return controls;
    }

    @Test void thePhoneTurnsToLandscapeAndBackInAboutTwoSecondsEasedWithHoldsAtBothEnds() {
        assertEquals(2f, RotatePrompt.CYCLE, 0.5f, "about two seconds a loop");
        assertEquals(RotatePrompt.CYCLE, RotatePrompt.HOLD * 2 + RotatePrompt.TURN * 2, 1e-5f, "the legs add up to the loop");
        assertEquals(0f, RotatePrompt.angle(0f), 0f, "upright at the start");
        assertEquals(0f, RotatePrompt.angle(RotatePrompt.HOLD * 0.99f), 0f, "held upright");
        assertEquals(RotatePrompt.TURN_DEGREES / 2f, RotatePrompt.angle(RotatePrompt.HOLD + RotatePrompt.TURN / 2f), 1e-3f, "halfway at the middle of the turn");
        assertEquals(90f, RotatePrompt.TURN_DEGREES, 0f, "to landscape");
        assertEquals(90f, RotatePrompt.angle(RotatePrompt.HOLD + RotatePrompt.TURN + RotatePrompt.HOLD / 2f), 1e-3f, "held in landscape");
        assertEquals(0f, RotatePrompt.angle(RotatePrompt.CYCLE - 1e-4f), 0.01f, "back upright at the end of the loop");
        assertEquals(RotatePrompt.angle(0.3f), RotatePrompt.angle(0.3f + 3 * RotatePrompt.CYCLE), 1e-3f, "it loops");
        assertEquals(RotatePrompt.angle(1.9f), RotatePrompt.angle(1.9f - RotatePrompt.CYCLE), 1e-3f, "and a clock before zero loops too");
        // Smooth and eased: the turn rises, the return falls, no step is larger than a few degrees at 60 frames a second, and the ends are slow.
        float previous = 0f, largest = 0f;
        boolean back = false;
        for (float t = 0f; t < RotatePrompt.CYCLE; t += 1f / 60f) {
            float angle = RotatePrompt.angle(t);
            assertTrue(angle >= 0f && angle <= 90f);
            if (t < RotatePrompt.HOLD + RotatePrompt.TURN) assertTrue(angle >= previous - 1e-4f, "rising at " + t);
            else if (t > RotatePrompt.HOLD * 2 + RotatePrompt.TURN) assertTrue(angle <= previous + 1e-4f, "falling at " + t);
            largest = Math.max(largest, Math.abs(angle - previous));
            previous = angle;
            assertEquals(t >= RotatePrompt.HOLD * 2 + RotatePrompt.TURN, RotatePrompt.returning(t), "the arrow flips with the return leg at " + t);
        }
        assertTrue(largest < 4f, "at most " + largest + " degrees a frame");
        assertTrue(RotatePrompt.angle(RotatePrompt.HOLD + 0.03f) < 1f, "eased in: the first frames of the turn are slow");
    }

    /** The highest the cluster reaches is derived from the real constants and the prompt keeps clear of it (the hand's and the ring's included). */
    @Test void thePromptReachesPastNothingTheControlsOrTheTutorialShow() {
        assertTrue(RotatePrompt.CLUSTER_REACH >= TouchControls.clusterReach(), "the keep-clear height " + RotatePrompt.CLUSTER_REACH + " covers " + TouchControls.clusterReach());
        assertTrue(TouchControls.clusterReach() >= 76f, "it includes the stick's grab circle (76) and the ring at full pulse");
        // The touch coach's hand at the top of its up-push, and the ghost knob it drags: each is inside the reach.
        float handTop = TouchCoach.SLIDE_UNITS + TouchControls.HAND_TIP_Y;
        assertTrue(handTop < RotatePrompt.CLUSTER_REACH, "the hand's top");
        assertTrue(TouchCoach.SLIDE_UNITS + TouchControls.KNOB_SIZE / 2 < RotatePrompt.CLUSTER_REACH, "the ghost knob's top");
    }

    @Test void onTheReferencePhonesThePromptHasRoomForThePictogramAboveTheControlsInPlayAndOutsideIt() {
        float text = tallestText();
        for (Window window : PHONES) {
            TouchLayout layout = layoutOf(window, true);
            assertTrue(layout.portrait(), window.name());
            float space = layout.spaceHeight(), cy = TouchControls.portraitClusterY(space);
            RotatePrompt first = null;
            for (boolean controls : new boolean[]{false, true}) {
                RotatePrompt prompt = new RotatePrompt();
                prompt.fit(space, cy, controls, text);
                assertTrue(prompt.shown && prompt.icon, window.name() + " controls " + controls + ": the pictogram is drawn");
                assertTrue(prompt.iconSize >= RotatePrompt.ICON_MIN && prompt.iconSize <= RotatePrompt.ICON_MAX, window.name() + ": icon " + prompt.iconSize);
                assertTrue(prompt.iconY >= RotatePrompt.TOP_MARGIN, window.name() + ": below the picture by the margin, at " + prompt.iconY);
                assertTrue(prompt.textTop >= prompt.iconY + prompt.iconSize, window.name() + ": the text is under the pictogram");
                float bottom = prompt.textTop + text, highestControl = cy - TouchControls.clusterReach();
                assertTrue(bottom <= highestControl - RotatePrompt.GAP_TO_CONTROLS + 1e-3f, window.name() + ": the prompt ends at " + bottom + ", the controls begin at " + highestControl);
                if (first == null) first = prompt;
                else { assertEquals(first.iconY, prompt.iconY, 0f, window.name() + ": the prompt does not move when play begins"); assertEquals(first.textTop, prompt.textTop, 0f); }
            }
            System.out.printf("PROMPT %s: strip %.1f units, cluster centre %.1f, icon %.0f at y %.1f, text %.0f..%.0f, controls begin at %.1f%n", window.name(), space, cy,
                first.iconSize, first.iconY, first.textTop, first.textTop + text, cy - TouchControls.clusterReach());
        }
    }

    /** What happens when the strip is short: the pictogram shrinks, then goes; the text goes last, and in play it gives way to the controls. */
    @Test void aShortStripShrinksThePictogramThenDropsItAndInPlayDropsTheTextToo() {
        float text = tallestText();
        boolean sawIcon = false, sawTextOnly = false, sawNothing = false;
        float previousSize = Float.MAX_VALUE;
        Window[] byStrip = SHORT.clone();
        java.util.Arrays.sort(byStrip, java.util.Comparator.comparingDouble((Window w) -> -layoutOf(w, true).spaceHeight()));
        for (Window window : byStrip) {
            TouchLayout layout = layoutOf(window, true);
            assertTrue(layout.portrait(), window.name());
            float space = layout.spaceHeight(), cy = TouchControls.portraitClusterY(space);
            RotatePrompt play = new RotatePrompt(), menu = new RotatePrompt();
            play.fit(space, cy, true, text);
            menu.fit(space, cy, false, text);
            assertTrue(menu.shown, window.name() + ": outside play the text is always drawn");
            assertTrue(menu.textTop + text <= space, window.name() + ": inside the strip");
            if (play.shown) {
                float bottom = play.textTop + text;
                assertTrue(bottom <= cy - TouchControls.clusterReach() - RotatePrompt.GAP_TO_CONTROLS + 1e-3f, window.name() + ": in play it keeps clear of the controls: " + bottom);
                assertTrue(play.iconY >= RotatePrompt.TOP_MARGIN - 1e-3f || !play.icon);
                assertEquals(menu.textTop, play.textTop, 0f, window.name() + ": the same place in both");
            }
            if (play.shown && play.icon) { sawIcon = true; assertTrue(play.iconSize <= previousSize + 1e-3f, "the pictogram only shrinks as the strip does"); previousSize = play.iconSize; }
            else if (play.shown) sawTextOnly = true;
            else { sawNothing = true; assertEquals(RotatePrompt.TOP_MARGIN, menu.textTop, 0f, window.name() + ": outside play the text sits at the top"); assertFalse(menu.icon); }
            System.out.printf("PROMPT short %s: strip %.1f units, play: %s, outside play: %s%n", window.name(), space,
                play.shown ? (play.icon ? "icon " + (int) play.iconSize + " and text" : "text only") : "nothing", menu.icon ? "icon and text" : "text only");
        }
        assertTrue(sawIcon && sawTextOnly && sawNothing, "the short windows cover every tier: icon " + sawIcon + ", text only " + sawTextOnly + ", nothing in play " + sawNothing);
    }

    @Test void thePromptIsDrawnInPortraitWithItsTwoPictogramPartsAndTheTextAndNotInLandscape() {
        TouchLayout portrait = layoutOf(PHONES[0], true);
        TouchControls controls = controlsOn(portrait, true);
        controls.setPromptClock(0.1f);
        batch.begin();
        controls.renderStrip(batch, false);
        assertEquals(2, batch.rotatedCount(), "the phone and the arrow");
        assertTrue(batch.vertexDrawCount() > 0, "and the text");
        batch.end();
        assertTrue(controls.prompt().shown);

        batch.resetCounts();
        batch.begin();
        controls.render(batch);
        assertEquals(2, batch.rotatedCount(), "in play too: the same two, the cluster adds none (the tutorial is idle)");
        assertTrue(batch.vertexDrawCount() > 0);
        batch.end();

        // Landscape: side bars, no strip, no prompt, in the menu pass and in play.
        for (Window window : new Window[]{new Window("844x390", 844, 390), new Window("844x330", 844, 330), new Window("640x360", 640, 360)}) {
            TouchLayout landscape = layoutOf(window, true);
            assertFalse(landscape.portrait(), window.name());
            TouchControls side = controlsOn(landscape, true);
            batch.resetCounts();
            batch.begin();
            side.renderStrip(batch, false);
            side.render(batch);
            batch.end();
            assertEquals(0, batch.rotatedCount(), window.name() + ": no prompt beside the picture");
            assertEquals(0, batch.vertexDrawCount(), window.name());
        }
    }

    @Test void thePromptIsNotDrawnOnADesktopWithoutATouchScreen() {
        TouchLayout plain = layoutOf(PHONES[0], false);
        assertFalse(plain.portrait(), "no touch: the plain fit");
        TouchControls desktop = controlsOn(plain, false);
        assertFalse(desktop.isAvailable());
        batch.begin();
        desktop.renderStrip(batch, false);
        desktop.render(batch);
        batch.end();
        assertEquals(0, batch.rotatedCount());
        assertEquals(0, batch.vertexDrawCount());
        // Even handed a portrait layout, a device that is not a touch device draws nothing.
        TouchControls unavailable = controlsOn(layoutOf(PHONES[0], true), false);
        assertFalse(unavailable.isAvailable());
        batch.begin();
        unavailable.renderStrip(batch, false);
        batch.end();
        assertEquals(0, batch.rotatedCount());
    }

    @Test void thePhoneIsDrawnTurnedByTheLoopAndTheArrowIsMirroredOnTheWayBack() {
        TouchControls controls = controlsOn(layoutOf(PHONES[0], true), true);
        float[][] clocks = {{0.1f, 0f, 1f}, {RotatePrompt.HOLD + RotatePrompt.TURN / 2f, 45f, 1f}, {1.1f, 90f, 1f}, {RotatePrompt.CYCLE - 0.1f, RotatePrompt.angle(RotatePrompt.CYCLE - 0.1f), -1f}};
        for (float[] clock : clocks) {
            controls.setPromptClock(clock[0]);
            batch.resetCounts();
            batch.begin();
            controls.renderStrip(batch, false);
            batch.end();
            // The arrow is the second rotated draw (the last): its scale shows the leg; it is never turned.
            assertEquals(clock[2], batch.lastRotatedScaleX(), 0f, "arrow scale at " + clock[0]);
            assertEquals(0f, batch.lastRotatedRotation(), 0f);
            assertEquals(controls.prompt().iconSize, batch.lastRotatedWidth(), 1e-3f);
            assertEquals(clock[1], batch.rotationOf(0), 0.5f, "the phone, the first of the two, is turned by the loop at " + clock[0]);
            assertEquals(RotatePrompt.angle(clock[0]), batch.rotationOf(0), 1e-3f);
        }
    }

    /** In play on every window the prompt drew, no part of it (pictogram box, text box) is inside the strip's area the controls or the pause button use. */
    @Test void inPlayThePromptNeverOverlapsTheControlsOrTheirTouchAreasInEitherHandedness() {
        float text = tallestText();
        for (Window window : new Window[]{PHONES[0], PHONES[1], PHONES[2], SHORT[0], SHORT[1], SHORT[2]}) {
            TouchLayout layout = layoutOf(window, true);
            float space = layout.spaceHeight(), cy = TouchControls.portraitClusterY(space);
            RotatePrompt prompt = new RotatePrompt();
            prompt.fit(space, cy, true, text);
            if (!prompt.shown) continue;
            float top = prompt.iconY, bottom = prompt.textTop + text;
            assertTrue(top >= 0f, window.name() + ": the prompt starts under the picture (the pause button is in the picture)");
            for (boolean left : new boolean[]{false, true}) {
                float[][] circles = {  // centre x, centre y, radius: the stick's grab circle, then each button's hit circle, and the Ranged ring at full pulse
                    {mirrored(TouchControls.PORTRAIT_STICK_X, left), cy, 76f},
                    {mirrored(TouchControls.PORTRAIT_JUMP_X, left), cy + TouchControls.PORTRAIT_JUMP_DY, 38.4f},
                    {mirrored(TouchControls.PORTRAIT_ACTION_X, left), cy + TouchControls.PORTRAIT_ACTION_DY, 38.4f},
                    {mirrored(TouchControls.PORTRAIT_SHOOT_X, left), cy + TouchControls.PORTRAIT_SHOOT_DY, 32f * 0.85f * 1.5f}};
                for (float[] c : circles) {
                    // The prompt's whole width band is the widest it can be (the strip), so a circle only has to be lower than its bottom edge.
                    assertTrue(c[1] - c[2] >= bottom + RotatePrompt.GAP_TO_CONTROLS - 1e-3f, window.name() + " left-handed " + left + ": a control circle at y " + (c[1] - c[2]) + " vs the prompt's end " + bottom);
                }
            }
        }
    }

    private static float mirrored(float x, boolean left) { return left ? TouchLayout.SPACE_WIDTH - x : x; }
}
