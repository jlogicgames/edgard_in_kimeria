package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the dialogue screen draws (D142, #616): two still portraits filling their openings, no pointer at the speaker, and each
 * name in the middle of its plate, measured on the glyph quads the font actually laid out.
 */
class HealerDialogueViewTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int RECORDED = 256, CANVAS = 256;
    /** x, y, colour, u, v; the corners of a glyph quad come bottom-left first. */
    private static final int VERTEX_FLOATS = 5, VERTEX_Y = 1;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();
    private final TextureRegion edgard = canvas(), healer = canvas();
    private final List<String> paths = new ArrayList<>();
    private boolean large;
    private int generation;
    private Level level;
    private Overlay overlay;
    private HealerDialogueView view;

    @BeforeEach void setUp() {
        harness.setUp();
        harness.font = new MetricsFont(FontSizes.DIALOGUE);
        level = harness.roomOf("forest-1", 3);
        BitmapFont small = new MetricsFont(FontSizes.dialogue(false)), big = new MetricsFont(FontSizes.dialogue(true));
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { paths.add(path); return new TextureRegion(); }
            @Override public TextureRegion region(String path, Texture.TextureFilter filter) {
                paths.add(path);
                return path.endsWith("portraits/edgard.png") ? edgard : path.endsWith("portraits/healer.png") ? healer : new TextureRegion();
            }
            @Override public BitmapFont font(int size) { return size == FontSizes.dialogue(true) ? big : small; }
            @Override public boolean largeText() { return large; }
            @Override public int textGeneration() { return generation; }
        };
        overlay = new Overlay(harness.assets(), 640, 360, null);
        view = new HealerDialogueView(assets);
    }
    @AfterEach void tearDown() { SoftDot.dispose(); Shaders.dispose(); harness.restoreGlobals(); }

    /** A portrait's shipped canvas size, without a texture. */
    private static TextureRegion canvas() {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return CANVAS; }
            @Override public int getRegionHeight() { return CANVAS; }
        };
    }

    private void frame(HealerDialogue dialogue, Language language) {
        batch.record(RECORDED);
        overlay.beginFrame();
        view.render(batch, overlay, dialogue, language, level, new ArtifactInventory());
        overlay.flushText(batch);
    }

    private float[] drawnAt(TextureRegion region) {
        for (int i = 0; i < batch.drawnCount(); i++)
            if (batch.drawnRegion(i) == region) return new float[]{batch.drawnX(i), batch.drawnY(i)};
        return fail("not drawn");
    }

    /** Neither portrait moves, whoever speaks, over several of the old 1.2 s periods; both fill their openings. */
    @Test void thePortraitsStandStill() {
        float opening = HealerDialogueView.WINDOW - HealerDialogueView.BAND * 2f;
        assertEquals(opening, HealerDialogueView.PORTRAIT, "the portrait fills the opening: no air for a bob");
        HealerDialogue tale = HealerDialogue.tutorialStart();
        tale.update(HealerDialogue.OPEN_SECONDS);
        float top = HealerDialogueView.WINDOW_Y + HealerDialogueView.BAND;
        boolean edgardSpoke = false, healerSpoke = false;
        for (int i = 0; i < 360; i++) {
            if (i == 180) { tale.advance(Language.ENGLISH); tale.advance(Language.ENGLISH); }
            tale.update(STEP);
            frame(tale, Language.ENGLISH);
            assertArrayEquals(new float[]{HealerDialogueView.EDGARD_WINDOW_X + HealerDialogueView.BAND, top}, drawnAt(edgard), "Edgard, frame " + i);
            assertArrayEquals(new float[]{HealerDialogueView.HEALER_WINDOW_X + HealerDialogueView.BAND, top}, drawnAt(healer), "healer, frame " + i);
            if (tale.edgardSpeaking()) edgardSpoke = true; else healerSpoke = true;
        }
        assertTrue(edgardSpoke && healerSpoke, "both sides were the speaker");
    }

    /** The highlight alone names the speaker: the view asks for no pointer art. */
    @Test void nothingPointsAtTheSpeaker() {
        HealerDialogue tale = HealerDialogue.tutorialStart();
        tale.update(HealerDialogue.OPEN_SECONDS);
        frame(tale, Language.ENGLISH);
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("dialogue-plate.png")), "the frame art was loaded: " + paths);
        assertTrue(paths.stream().noneMatch(p -> p.contains("notch")), "no notch: " + paths);
    }

    /**
     * D142: a spoken line stops short of the other speaker's picture. Edgard's starts at the box's left margin and wraps at the
     * healer's window; the healer's ends at the right margin and wraps at Edgard's window. Measured on every line of every script.
     */
    @Test void aLineStopsAtTheOtherSpeakersPicture() {
        float left = HealerDialogueView.TEXT_X, right = HealerDialogueView.TEXT_X + HealerDialogueView.TEXT_W;
        float edgardWindowRight = HealerDialogueView.EDGARD_WINDOW_X + HealerDialogueView.WINDOW;
        assertEquals(HealerDialogueView.HEALER_WINDOW_X - left, right - edgardWindowRight, "the two sides mirror each other");
        boolean edgardWrapped = false, healerWrapped = false;
        for (boolean largeTier : new boolean[]{false, true}) {
            large = largeTier;
            generation++;
            for (Language language : Language.values()) {
                for (HealerDialogue dialogue : new HealerDialogue[]{HealerDialogue.tutorialStart(), HealerDialogue.tutorialEnd(),
                    HealerDialogue.firstDeath(), HealerDialogue.afterVictory(), new HealerDialogue(false, false, false, false, false),
                    new HealerDialogue(true, true, true, true, false), new HealerDialogue(false, false, false, false, true)}) {
                    dialogue.setInstantText(true);
                    dialogue.update(HealerDialogue.OPEN_SECONDS);
                    Msg shown = null;
                    while (dialogue.currentLine() != shown) {
                        shown = dialogue.currentLine();
                        frame(dialogue, language);
                        boolean speaks = dialogue.edgardSpeaking();
                        float from = speaks ? left : edgardWindowRight, to = speaks ? HealerDialogueView.HEALER_WINDOW_X : right;
                        float[] vertices = view.lineBlock().getVertices(0);
                        int count = view.lineBlock().getVertexCount(0);
                        float first = Float.MAX_VALUE, last = -Float.MAX_VALUE, highest = -Float.MAX_VALUE, lowest = Float.MAX_VALUE;
                        for (int i = 0; i < count; i += VERTEX_FLOATS) {
                            first = Math.min(first, vertices[i]);
                            last = Math.max(last, vertices[i]);
                            highest = Math.max(highest, vertices[i + VERTEX_Y]);
                            lowest = Math.min(lowest, vertices[i + VERTEX_Y]);
                        }
                        String what = language + " " + shown + ", large " + largeTier + ": " + first + " to " + last;
                        assertTrue(first >= from - 1f && last <= to + 1f, what + ", allowed " + from + " to " + to);
                        if (speaks) assertTrue(first <= from + 2f, what + ": Edgard's line starts at the left");
                        else assertTrue(last >= to - 2f, what + ": the healer's line ends at the right");
                        if (highest - lowest > FontSizes.dialogue(largeTier)) { if (speaks) edgardWrapped = true; else healerWrapped = true; }
                        dialogue.advance(language);
                    }
                }
            }
        }
        assertTrue(edgardWrapped && healerWrapped, "both sides had a line long enough to wrap");
    }

    /** Every name, in every language and both text tiers: as much plate above its highest pixel as below its lowest (within 1 px). */
    @Test void everyNameSitsInTheMiddleOfItsPlate() {
        HealerDialogue tale = HealerDialogue.tutorialStart();
        tale.update(HealerDialogue.OPEN_SECONDS);
        for (boolean largeTier : new boolean[]{false, true}) {
            large = largeTier;
            generation++;
            for (Language language : Language.values()) {
                frame(tale, language);
                for (boolean edgardName : new boolean[]{true, false}) {
                    String what = language + (edgardName ? " Edgard" : " healer") + ", large " + largeTier;
                    BitmapFontCache block = view.nameBlock(edgardName);
                    float[] vertices = block.getVertices(0);
                    int count = block.getVertexCount(0);
                    assertTrue(count > 0, what + ": the name has glyphs");
                    float highest = Float.MAX_VALUE, lowest = -Float.MAX_VALUE; // Y down, from the screen's top
                    for (int i = 0; i < count; i += VERTEX_FLOATS) {
                        float fromTop = HealerDialogueView.SCREEN_H - vertices[i + VERTEX_Y];
                        highest = Math.min(highest, fromTop);
                        lowest = Math.max(lowest, fromTop);
                    }
                    float above = highest - HealerDialogueView.PLATE_Y;
                    float below = HealerDialogueView.PLATE_Y + HealerDialogueView.PLATE_H - lowest;
                    assertTrue(above >= 1f && below >= 1f, what + " inside the rules: " + above + " above, " + below + " below");
                    assertTrue(Math.abs(above - below) <= 1f, what + " centred: " + above + " above, " + below + " below");
                }
            }
        }
    }
}
