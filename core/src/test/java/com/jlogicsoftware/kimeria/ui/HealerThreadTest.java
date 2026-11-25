package com.jlogicsoftware.kimeria.ui;

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

import static org.junit.jupiter.api.Assertions.*;

/** The silver thread of the dialogue screen (#495): none before the healer ties it, drawn in on that line, then there for good. */
class HealerThreadTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int RECORDED = 256;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();
    private final TextureRegion thread = new TextureRegion(), glint = new TextureRegion();
    private Level level;
    private Overlay overlay;
    private HealerDialogueView view;

    @BeforeEach void setUp() {
        harness.setUp();
        harness.font = new MetricsFont(FontSizes.DIALOGUE);
        level = harness.roomOf("forest-1", 3);
        overlay = new Overlay(harness.assets(), 640, 360, null);
        view = new HealerDialogueView(new Assets() { // tells the thread's and the glint's draws apart from the rest
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                return path.endsWith("dialogue-thread.png") ? thread : path.endsWith("dialogue-glint.png") ? glint : new TextureRegion();
            }
            @Override public TextureRegion region(String path, com.badlogic.gdx.graphics.Texture.TextureFilter filter) { return new TextureRegion(); }
            @Override public BitmapFont font(int size) { return harness.font; }
        });
    }
    @AfterEach void tearDown() { SoftDot.dispose(); Shaders.dispose(); harness.restoreGlobals(); }

    private static HealerDialogue taleAt(Msg line) {
        HealerDialogue tale = HealerDialogue.tutorialStart();
        tale.update(HealerDialogue.OPEN_SECONDS);
        tale.setInstantText(true);
        while (tale.currentLine() != line) tale.advance(Language.ENGLISH);
        tale.setInstantText(false);
        return tale;
    }

    private static List<HealerDialogue> laterConversations() {
        return List.of(HealerDialogue.tutorialEnd(), HealerDialogue.firstDeath(), HealerDialogue.afterVictory(),
            new HealerDialogue(false, false, false, false, false), new HealerDialogue(false, false, false, false, false, true),
            new HealerDialogue(true, true, true, true, false), new HealerDialogue(false, false, false, false, true));
    }

    @Test void theStartTaleHasNoThreadBeforeTheLineThatTiesIt() {
        for (boolean instant : new boolean[]{false, true}) {
            HealerDialogue tale = HealerDialogue.tutorialStart();
            tale.setInstantText(instant);
            tale.update(HealerDialogue.OPEN_SECONDS);
            int lines = 0;
            while (tale.currentLine() != Msg.HEALER_TALE_THREAD) {
                tale.update(10f); // however long a line stays up
                assertEquals(0f, tale.threadTied(), "instant text " + instant + ": " + tale.currentLine());
                tale.advance(Language.ENGLISH);
                lines++;
            }
            assertEquals(5, lines, "the thread is tied on the sixth line");
        }
    }

    @Test void theTyingLineDrawsTheThreadInAndEveryLaterLineKeepsIt() {
        HealerDialogue tale = taleAt(Msg.HEALER_TALE_THREAD);
        assertEquals(0f, tale.threadTied(), "the line's first frame: nothing yet");
        tale.update(HealerDialogue.THREAD_TIE_SECONDS / 4f);
        assertEquals(.25f, tale.threadTied(), .001f);
        tale.advance(Language.ENGLISH); // the first press only shows the whole line: the draw-in goes on
        assertEquals(Msg.HEALER_TALE_THREAD, tale.currentLine());
        assertEquals(.25f, tale.threadTied(), .001f);
        tale.update(HealerDialogue.THREAD_TIE_SECONDS / 4f);
        assertEquals(.5f, tale.threadTied(), .001f);
        tale.update(HealerDialogue.THREAD_TIE_SECONDS);
        assertEquals(1f, tale.threadTied());
        tale.setInstantText(true);
        while (tale.currentLine() != Msg.EDGARD_TALE_UNDERSTOOD) {
            tale.advance(Language.ENGLISH);
            assertEquals(1f, tale.threadTied(), "tied for good: " + tale.currentLine());
        }
    }

    @Test void leavingTheTyingLineEarlyLeavesTheThreadWhole() {
        HealerDialogue tale = taleAt(Msg.HEALER_TALE_THREAD);
        tale.update(.1f);
        tale.advance(Language.ENGLISH); // reveals
        tale.advance(Language.ENGLISH); // leaves
        assertEquals(Msg.HEALER_TALE_FIREGIFT, tale.currentLine());
        assertEquals(1f, tale.threadTied());
        assertEquals(0f, tale.glintTime(), "and its glint sets off with the line");
    }

    @Test void instantTextTiesTheThreadOnTheLinesFirstFrame() {
        HealerDialogue tale = taleAt(Msg.HEALER_TALE_THREAD);
        tale.setInstantText(true);
        assertEquals(1f, tale.threadTied(), "no motion with instant text");
        assertEquals(0f, tale.glintTime());
    }

    @Test void everyLaterConversationHasTheThreadFromItsOpening() {
        for (HealerDialogue dialogue : laterConversations()) {
            assertEquals(1f, dialogue.threadTied(), dialogue.kind() + " before any update");
            assertEquals(0f, dialogue.glintTime(), dialogue.kind() + ": no delay before the glint");
            dialogue.setInstantText(true);
            for (int press = 0; press < 20; press++) {
                dialogue.advance(Language.ENGLISH);
                assertEquals(1f, dialogue.threadTied(), dialogue.kind().name());
            }
        }
    }

    /** What one frame drew of the thread: the whole art, a left part of it ({@code part} px wide) and the glint's left edge. */
    private record Drawn(boolean whole, float part, float glintX) {
        boolean nothing() { return !whole && part == 0f && Float.isNaN(glintX); }
    }

    private Drawn frame(HealerDialogue dialogue) {
        batch.record(RECORDED);
        batch.resetCounts();
        overlay.beginFrame();
        view.render(batch, overlay, dialogue, Language.ENGLISH, level, new ArtifactInventory());
        overlay.flushText(batch);
        boolean whole = false;
        float glintX = Float.NaN;
        for (int i = 0; i < batch.drawnCount(); i++) {
            if (batch.drawnRegion(i) == thread) whole = true;
            if (batch.drawnRegion(i) == glint) glintX = batch.drawnX(i);
        }
        assertTrue(batch.drawnCount() < RECORDED, "the log holds the whole frame");
        return new Drawn(whole, batch.croppedCount() > 0 ? batch.lastCroppedWidth() : 0f, glintX);
    }

    @Test void theScreenShowsNeitherThreadNorGlintBeforeTheThreadIsTied() {
        HealerDialogue tale = HealerDialogue.tutorialStart();
        while (tale.currentLine() != Msg.HEALER_TALE_THREAD) {
            for (int i = 0; i < 90; i++) {
                tale.update(STEP);
                assertTrue(frame(tale).nothing(), tale.currentLine() + " frame " + i);
            }
            tale.advance(Language.ENGLISH); // reveals the line if it is still typing
            if (tale.fullyRevealed(Language.ENGLISH)) tale.advance(Language.ENGLISH);
        }
        assertTrue(frame(tale).nothing(), "the tying line's first frame");
    }

    @Test void theThreadDrawsInFromEdgardWithTheGlintOnItsEndThenTheGlintRunsFromTheHealerToEdgard() {
        HealerDialogue tale = taleAt(Msg.HEALER_TALE_THREAD);
        int tying = Math.round(HealerDialogue.THREAD_TIE_SECONDS / STEP);
        float width = 0f, glintX = -1f;
        for (int i = 0; i < tying - 2; i++) {
            tale.update(STEP);
            Drawn drawn = frame(tale);
            assertFalse(drawn.whole(), "frame " + i);
            assertTrue(drawn.part() >= width && drawn.part() > 0f && drawn.part() <= HealerDialogueView.THREAD_W, "it only grows: " + drawn.part());
            assertTrue(drawn.glintX() >= glintX, "the glint rides the end towards the healer");
            width = drawn.part();
            glintX = drawn.glintX();
        }
        assertTrue(width > HealerDialogueView.THREAD_W * .9f, "nearly across by the end: " + width);
        for (int i = 0; i < 4; i++) tale.update(STEP);
        Drawn tied = frame(tale);
        assertTrue(tied.whole(), "whole once tied");
        float start = tied.glintX();
        assertTrue(start > HealerDialogueView.THREAD_X1 - HealerDialogueView.GLINT * 2f, "the speaker's glint sets off at the healer: " + start);
        for (int i = 0; i < 30; i++) tale.update(STEP);
        assertTrue(frame(tale).glintX() < start, "and runs to Edgard");
        tale.update(HealerDialogueView.GLINT_SECONDS);
        Drawn after = frame(tale);
        assertTrue(after.whole() && Float.isNaN(after.glintX()), "one pass per line");
    }

    @Test void aLaterConversationShowsTheThreadAsSoonAsTheScreenIsOpen() {
        for (HealerDialogue dialogue : laterConversations()) {
            dialogue.update(HealerDialogue.OPEN_SECONDS);
            dialogue.update(STEP);
            Drawn drawn = frame(dialogue);
            assertTrue(drawn.whole() && !Float.isNaN(drawn.glintX()), dialogue.kind().name());
        }
        HealerDialogue opening = HealerDialogue.tutorialEnd();
        opening.update(HealerDialogue.OPEN_SECONDS * .66f);
        Drawn drawn = frame(opening);
        assertTrue(drawn.part() > 0f && !drawn.whole() && Float.isNaN(drawn.glintX()), "it still draws in with the opening, without a glint");
    }

    /** #495: on a 390 px portrait phone (the D137 reference window) the 5 px glint was 3 CSS px and a tester missed it. */
    @Test void theGlintIsNoticeableOnAPortraitPhone() {
        TouchLayout phone = new TouchLayout(640, 360);
        phone.setPixelRatio(3f);
        phone.update(390 * 3, 844 * 3, true);
        float css = phone.cssPerUnit();
        assertTrue(HealerDialogueView.GLINT * css >= 7f, "glint CSS px: " + HealerDialogueView.GLINT * css);
        assertTrue(HealerDialogueView.GLINT_SECONDS >= 1.2f, "and it stays long enough to be caught");
    }

    @Test void theLargerGlintClearsThePlatesAndTheChoices() {
        float half = HealerDialogueView.GLINT / 2f, top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (float x = HealerDialogueView.THREAD_X0; x <= HealerDialogueView.THREAD_X1; x++) {
            float y = Math.round(HealerDialogueView.threadY(x) - half);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y + HealerDialogueView.GLINT);
        }
        assertTrue(bottom <= HealerDialogueView.PLATE_Y, "above the plates: " + bottom);
        float choicesBottom = HealerDialogueView.CHOICE_Y + HealerDialogueView.CHOICE_STEP + HealerDialogueView.CHOICE_H; // two choices at most
        assertTrue(top >= choicesBottom, "below the choices: " + top);
    }
}
