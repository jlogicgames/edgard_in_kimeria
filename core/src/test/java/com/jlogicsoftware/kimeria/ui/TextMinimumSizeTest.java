package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D137 (#491): no text is drawn below the minimum on-screen size on a small picture. The picture's scale comes from the real
 * {@link TouchLayout} at the three reference touch windows (CSS pixels, device pixel ratio 3 as on a phone), the fonts the views
 * ask for come from the real views, and every size they ask for, times that scale, must reach {@link FontSizes#MIN_CSS_PX}.
 *
 * <p>Measured apart: the portrait strip's rotate hint ({@link FontSizes#ROTATE_HINT}), the only text of {@code TouchControls} since the
 * buttons show pictograms (D138, #492). It is drawn in the strip's own units, not the picture's, so {@link #theRotateHintIsReadableInTheStripAtEveryPortraitWindow}
 * measures it there; the screens built here never ask for that size, and the test fails if one does.
 */
class TextMinimumSizeTest {
    /** A window in CSS pixels, with its device pixel ratio and whether it has a touch screen. */
    private record Window(String name, int width, int height, int ratio, boolean touch) { }

    /** The three windows of #491: a portrait phone and a phone in landscape, with and without the browser's toolbars. */
    private static final Window[] REFERENCE = {
        new Window("390x844 portrait", 390, 844, 3, true),
        new Window("844x390 landscape", 844, 390, 3, true),
        new Window("844x330 landscape with toolbars", 844, 330, 3, true)};
    /** Other windows the rule covers: the narrowest portrait the rule is stated for, a 16:9 touch window, small and large desktop windows. */
    private static final Window[] OTHERS = {
        new Window("384x832 narrowest portrait", 384, 832, 3, true),
        new Window("640x360 touch", 640, 360, 2, true),
        new Window("480x270 desktop", 480, 270, 1, false),
        new Window("640x360 desktop", 640, 360, 1, false),
        new Window("1280x720 desktop", 1280, 720, 1, false)};

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); harness.font = new MetricsFont(FontSizes.DIALOGUE); }
    @AfterEach void tearDown() { SoftDot.dispose(); Shaders.dispose(); harness.restoreGlobals(); }

    /** The picture's CSS pixels per unit, from the layout the game uses. */
    private static float cssPerUnit(Window window) {
        TouchLayout layout = new TouchLayout(640, 360);
        layout.setPixelRatio(window.ratio());
        layout.update(window.width() * window.ratio(), window.height() * window.ratio(), window.touch());
        return layout.cssPerUnit();
    }

    /** An assets stub that records every font size asked for and gives the real glyph metrics without a texture. */
    private static final class RecordingAssets extends Assets {
        final Set<Integer> sizes = new TreeSet<>();
        @Override public BitmapFont font(int size) { sizes.add(size); return new MetricsFont(size); }
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public TextureRegion region(String path, Texture.TextureFilter filter) { return new TextureRegion(); }
    }

    /** The views of every text screen the minimum covers, built on {@code assets}; one frame of each draws (and so fetches) its fonts. */
    private final class Screens {
        final Overlay overlay;
        final Hud hud;
        final ControlsView controls;
        final HealerDialogueView dialogue;
        final HealerDialogue preBoss = new HealerDialogue(false, false, false, false, false), tale = HealerDialogue.tutorialStart();
        final Level level = harness.roomOf("forest-1", 3);
        final ArtifactInventory inventory = new ArtifactInventory();

        Screens(Assets assets) {
            overlay = new Overlay(assets, 640, 360, null);
            hud = new Hud(assets, harness.game(), 640, 360);
            controls = new ControlsView(assets, overlay);
            dialogue = new HealerDialogueView(assets);
            preBoss.setInstantText(true);
            tale.setInstantText(true);
        }

        /** Both dialogues opened (their frame, thread and box whole), then one drawn frame. */
        void frame() {
            for (HealerDialogue talk : new HealerDialogue[]{preBoss, tale}) for (int i = 0; i < 40; i++) talk.update(1f / 60f);
            draw();
        }

        /** One drawn frame of the HUD, the Controls table and both dialogue screens, with the text pass. */
        void draw() {
            overlay.beginFrame();
            hud.render(batch, null, false, null, "60 FPS");
            controls.draw(batch, ControlsView.Mode.KEYBOARD, Language.ENGLISH, false);
            for (HealerDialogue talk : new HealerDialogue[]{preBoss, tale}) dialogue.render(batch, overlay, talk, Language.ENGLISH, level, inventory);
            overlay.flushText(batch);
        }
    }

    /** The sizes every screen asks for on a window at its tier: the three tiered roles and the five that do not change. */
    private static Set<Integer> expectedSizes(boolean large) {
        Set<Integer> sizes = new TreeSet<>();
        for (int size : new int[]{FontSizes.label(large), FontSizes.body(large), FontSizes.dialogue(large),
            FontSizes.BUTTON, FontSizes.TITLE, FontSizes.STORY, FontSizes.ZONE, FontSizes.COUNT}) sizes.add(size);
        return sizes;
    }

    /** The measurement D137 records: the picture's scale at each reference window (844 / 848 and 330 / 360 are the side-bar layout, D56). */
    @Test void theReferenceWindowsDrawThePictureAtTheScalesTheDecisionRecords() {
        assertEquals(390f / 640f, cssPerUnit(REFERENCE[0]), 1e-4f, "portrait: the picture fills the width");
        assertEquals(844f / 848f, cssPerUnit(REFERENCE[1]), 1e-4f, "landscape: side bars of 104 units each take the rest of the width");
        assertEquals(330f / 360f, cssPerUnit(REFERENCE[2]), 1e-4f, "landscape with toolbars: the height rules");
    }

    @Test void everyFontTheScreensAskForReachesTheMinimumAtTheReferenceWindows() {
        for (Window window : REFERENCE) assertMinimum(window);
    }

    @Test void everyFontTheScreensAskForReachesTheMinimumAtTheOtherWindowsToo() {
        for (Window window : OTHERS) assertMinimum(window);
    }

    private void assertMinimum(Window window) {
        float css = cssPerUnit(window);
        assertTrue(css >= FontSizes.MIN_RULE_FROM_CSS_PER_UNIT - 1e-4f, window.name() + " is inside the rule's range: " + css);
        boolean large = FontSizes.large(css);
        RecordingAssets assets = new RecordingAssets();
        assets.setLargeText(large);
        new Screens(assets).frame();
        assertEquals(expectedSizes(large), assets.sizes, window.name() + ": the screens ask for these fonts and no others");
        assertFalse(assets.sizes.contains(FontSizes.ROTATE_HINT), "the rotate hint belongs to TouchControls and is measured in its strip");
        for (int size : assets.sizes)
            assertTrue(FontSizes.meetsMinimum(size, css), window.name() + ": " + size + " px text is " + FontSizes.onScreen(size, css)
                + " CSS px at " + css + " per unit, below " + FontSizes.MIN_CSS_PX);
    }

    /** The hint is as wide as the window in 360 units, so its on-screen size is the baked size times the window width over 360. */
    @Test void theRotateHintIsReadableInTheStripAtEveryPortraitWindow() {
        for (Window window : new Window[]{REFERENCE[0], OTHERS[0]}) {
            TouchLayout layout = new TouchLayout(640, 360);
            layout.setPixelRatio(window.ratio());
            layout.update(window.width() * window.ratio(), window.height() * window.ratio(), true);
            assertTrue(layout.portrait(), window.name());
            float css = FontSizes.ROTATE_HINT * window.width() / TouchLayout.SPACE_WIDTH;
            assertTrue(Math.round(css) >= FontSizes.MIN_CSS_PX, window.name() + ": the rotate hint is " + css + " CSS px");
        }
    }

    /** The measurement table of D137 (before: the nominal sizes; after: what is drawn), printed for the record. */
    @Test void printTheOnScreenSizesBeforeAndAfter() {
        for (Window window : REFERENCE) {
            float css = cssPerUnit(window);
            boolean large = FontSizes.large(css);
            System.out.printf("TEXT %s scale %.3f: label %d->%d = %.1f->%.1f, body %d->%d = %.1f->%.1f, dialogue %d->%d = %.1f->%.1f CSS px%n", window.name(), css,
                FontSizes.LABEL, FontSizes.label(large), FontSizes.LABEL * css, FontSizes.label(large) * css,
                FontSizes.BODY, FontSizes.body(large), FontSizes.BODY * css, FontSizes.body(large) * css,
                FontSizes.DIALOGUE, FontSizes.dialogue(large), FontSizes.DIALOGUE * css, FontSizes.dialogue(large) * css);
        }
    }

    /** The test would catch a regression: at the old nominal 16 px sizes the portrait window is below the minimum. */
    @Test void theNominalSizesWouldFailOnThePortraitPhone() {
        float css = cssPerUnit(REFERENCE[0]);
        assertFalse(FontSizes.meetsMinimum(FontSizes.BODY, css), "16 px body: " + FontSizes.BODY * css);
        assertFalse(FontSizes.meetsMinimum(FontSizes.LABEL, css));
        assertTrue(FontSizes.meetsMinimum(FontSizes.DIALOGUE, css), "18 px dialogue is 10.97 CSS px, 11 rounded: the large tier is for readability, not the floor");
        assertTrue(FontSizes.large(css));
        assertFalse(FontSizes.large(cssPerUnit(OTHERS[3])), "a 640x360 desktop window keeps the nominal sizes");
        assertFalse(FontSizes.large(cssPerUnit(OTHERS[4])));
    }

    /** The rule is stated down to a 0.6 scale; at exactly that scale every size still rounds to the minimum. */
    @Test void theRuleHoldsAtItsStatedLimit() {
        for (int size : expectedSizes(true)) assertTrue(FontSizes.meetsMinimum(size, FontSizes.MIN_RULE_FROM_CSS_PER_UNIT), size + " px");
        assertFalse(FontSizes.meetsMinimum(FontSizes.BODY_LARGE, 0.55f), "below the limit the 18 px body is under the minimum: the rule says where it stops");
    }

    /** A window that crosses the scale limit changes the fonts of views that are already built, without rebuilding them. */
    @Test void aTierChangeReachesTheViewsThatAreAlreadyBuilt() {
        RecordingAssets assets = new RecordingAssets();
        Screens screens = new Screens(assets);
        screens.frame();
        assertEquals(expectedSizes(false), assets.sizes);
        float nominalHeight = screens.overlay.bodyHeight(Msg.ABOUT_RULES.t(Language.ENGLISH), 580f);
        assets.sizes.clear();
        assets.setLargeText(true);
        screens.frame();
        assertTrue(assets.sizes.containsAll(List.of(FontSizes.body(true), FontSizes.label(true), FontSizes.dialogue(true))), "the views fetched the large fonts: " + assets.sizes);
        for (int size : assets.sizes) assertTrue(size == FontSizes.body(true) || size == FontSizes.label(true) || size == FontSizes.dialogue(true),
            "only the tiered roles are fetched again: " + size);
        assertTrue(screens.overlay.bodyHeight(Msg.ABOUT_RULES.t(Language.ENGLISH), 580f) > nominalHeight, "body text lays out larger");
        assets.sizes.clear();
        assets.setLargeText(false);
        screens.frame();
        assertTrue(assets.sizes.containsAll(List.of(FontSizes.body(false), FontSizes.label(false), FontSizes.dialogue(false))), "and back: " + assets.sizes);
        assertEquals(nominalHeight, screens.overlay.bodyHeight(Msg.ABOUT_RULES.t(Language.ENGLISH), 580f), 0f);
    }

    /** #491: the menu hint keeps a margin from the bottom edge (its baseline was 6 units above it, 3.7 CSS px on a 390 px phone). */
    @Test void theMenuHintKeepsAMarginFromTheBottomEdge() {
        for (boolean large : new boolean[]{false, true}) {
            float textHeight = new MetricsFont(FontSizes.label(large)).getCapHeight();
            float top = Overlay.hintTop(360f - Overlay.HINT_BOTTOM_MARGIN, textHeight, 360f);
            float baseline = top + textHeight;
            assertTrue(360f - baseline >= Overlay.HINT_BOTTOM_MARGIN - 1e-3f, "baseline " + baseline + " leaves " + (360f - baseline) + " units");
            assertEquals(Overlay.HINT_BOTTOM_MARGIN, 360f - baseline, 1e-3f, "a request at the old position is lifted to the margin");
        }
        assertTrue(Overlay.HINT_BOTTOM_MARGIN >= 12f);
        float onPhone = Overlay.HINT_BOTTOM_MARGIN * cssPerUnit(REFERENCE[0]);
        assertTrue(onPhone >= 7f, "at least 7 CSS px on the 390 px phone: " + onPhone);
        assertEquals(100f, Overlay.hintTop(100f, 10f, 360f), 0f, "a hint that is already high enough stays where it was asked");
    }

    /**
     * AGENTS.md, no allocation per frame: a frame at the large tier costs what a frame at the nominal tier does (the tier is an int
     * compared per view per frame; the rebuild is an event at the change). Windows of the two tiers alternate and the median
     * difference is taken, as the other budget tests do, so a JIT change in one window does not count.
     */
    @Test void aFrameAtTheLargeTierAllocatesNoMoreThanAFrameAtTheNominalOne() {
        RecordingAssets assets = new RecordingAssets();
        Screens screens = new Screens(assets);
        screens.frame();
        double nominal = com.jlogicsoftware.kimeria.testsupport.AllocationProbe.bytesPerFrame(i -> screens.draw());
        double difference = com.jlogicsoftware.kimeria.testsupport.AllocationProbe.medianPairedDifference(9, 300,
            () -> { assets.setLargeText(true); screens.draw(); }, i -> screens.draw(),
            () -> { assets.setLargeText(false); screens.draw(); }, i -> screens.draw());
        System.out.printf("TEXT frame allocation: nominal %.1f B/frame, large minus nominal %.2f B/frame%n", nominal, difference);
        assertTrue(difference <= 1.0, "the large tier adds " + difference + " B/frame");
    }
}
