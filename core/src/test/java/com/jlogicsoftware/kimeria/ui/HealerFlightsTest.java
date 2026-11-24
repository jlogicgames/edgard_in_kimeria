package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.ArtifactInventory;
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

/** The healer screen's flights (D132, #539, #540, #541): the fairy between the portraits, the hand-over and the elixir. All cosmetic. */
class HealerFlightsTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); harness.font = new MetricsFont(FontSizes.DIALOGUE); }
    @AfterEach void tearDown() { SoftDot.dispose(); Shaders.dispose(); harness.restoreGlobals(); }

    private static void frame(HealerDialogueView view, Overlay overlay, NoopBatch batch, HealerDialogue dialogue, Level level,
                              ArtifactInventory inventory, boolean running, List<String> sounds) {
        if (running) {
            dialogue.update(STEP);
            view.update(STEP, dialogue);
            String sound = view.takeSound();
            if (sound != null) sounds.add(sound);
        }
        overlay.beginFrame();
        view.render(batch, overlay, dialogue, Language.ENGLISH, level, inventory);
        overlay.flushText(batch);
    }

    @Test void thePaidScreenHandsTheItemsOverThenTheElixirAndTouchesNoState() {
        Level level = harness.roomOf("forest-1", 3);
        ArtifactInventory inventory = new ArtifactInventory();
        List<ArtifactInventory.Pickup> before = List.copyOf(inventory.items());
        Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        HealerDialogue paid = new HealerDialogue(false, false, false, false, true);
        List<String> sounds = new ArrayList<>();
        int peak = 0;
        for (int i = 0; i < 60 * 4; i++) {
            frame(view, overlay, batch, paid, level, inventory, true, sounds);
            if (view.flights() != null) peak = Math.max(peak, view.flights().count());
        }
        assertEquals(List.of("handover", "handover", "handover", "elixir"), sounds, "three arrivals at the healer, then the elixir at Edgard");
        assertEquals(4, peak, "three items and the elixir");
        assertEquals(0, view.flights().count(), "all landed by 4 s");
        assertEquals(before, inventory.items(), "a flight never reads or writes the inventory");
        assertFalse(paid.choosing() && paid.choices().isEmpty());
    }

    @Test void theRowIconsWaitAtTheirRowsUntilTheyTakeOff() {
        Level level = harness.roomOf("forest-1", 3);
        Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        HealerDialogue paid = new HealerDialogue(false, false, false, false, true);
        List<String> sounds = new ArrayList<>();
        for (int i = 0; i < 6; i++) frame(view, overlay, batch, paid, level, new ArtifactInventory(), true, sounds);
        for (int slot = 0; slot < HealerDialogueView.ROWS; slot++)
            assertTrue(view.flights().waiting(slot), "icon " + slot + " is waiting at 0.1 s: the bat leaves at 0.5 s");
        assertEquals(List.of(), sounds);
    }

    @Test void instantTextShowsNoFlightAndPlaysTheElixirSoundOnce() {
        Level level = harness.roomOf("forest-1", 3);
        Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        HealerDialogue paid = new HealerDialogue(false, false, false, false, true);
        paid.setInstantText(true);
        List<String> sounds = new ArrayList<>();
        for (int i = 0; i < 60 * 4; i++) {
            frame(view, overlay, batch, paid, level, new ArtifactInventory(), true, sounds);
            assertEquals(0, view.flights().count());
        }
        assertEquals(List.of("elixir"), sounds);
    }

    @Test void aPausedScreenFreezesItsFlights() {
        Level level = harness.roomOf("forest-1", 3);
        Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        HealerDialogue paid = new HealerDialogue(false, false, false, false, true);
        List<String> sounds = new ArrayList<>();
        for (int i = 0; i < 40; i++) frame(view, overlay, batch, paid, level, new ArtifactInventory(), true, sounds); // 0.67 s: the bat is airborne
        float progress = view.flights().progress(0);
        assertTrue(progress > 0f && progress < 1f);
        for (int i = 0; i < 120; i++) frame(view, overlay, batch, paid, level, new ArtifactInventory(), false, sounds); // drawn, not updated
        assertEquals(progress, view.flights().progress(0), 0f);
    }

    @Test void aNewDialogueNeverDrawsTheLastOnesFlightsOrFairyNotEvenForItsFirstFrame() {
        Level level = harness.roomOf("forest-1", 3);
        ArtifactInventory inventory = new ArtifactInventory();
        Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        List<String> sounds = new ArrayList<>();
        // One view, three dialogues in a row: a tale at the fairy's line, a paid screen mid-flight, a plain pre-boss stop.
        HealerDialogue tale = HealerDialogue.tutorialStart();
        tale.setInstantText(true);
        while (tale.currentLine() != Msg.HEALER_TALE_FAIRY) tale.advance(Language.ENGLISH);
        for (int i = 0; i < 30; i++) frame(view, overlay, batch, tale, level, inventory, true, sounds);
        assertTrue(view.fairyShown());
        HealerDialogue paid = new HealerDialogue(false, false, false, false, true);
        frame(view, overlay, batch, paid, level, inventory, false, sounds); // the first frame of the next dialogue: drawn, not yet updated
        assertFalse(view.fairyShown(), "the fairy of the tale is gone on the very first frame of the next dialogue");
        assertEquals(4, view.flights().count(), "and the paid screen shows its own flights, not leftovers");
        for (int i = 0; i < 40; i++) frame(view, overlay, batch, paid, level, inventory, true, sounds); // the bat is in the air
        HealerDialogue stop = new HealerDialogue(false, true, false, false, false);
        frame(view, overlay, batch, stop, level, inventory, false, sounds);
        assertEquals(0, view.flights().count(), "no icon or elixir of the paid screen crosses into the next dialogue");
        for (int slot = 0; slot < HealerDialogueView.ROWS; slot++) assertFalse(view.flights().waiting(slot));
    }

    @Test void thePaidScreensWaitingIconsNeverOverlapItsMessageInAnyLanguage() {
        float iconRight = HealerDialogueView.PAID_ICON_X + HealerDialogueView.ROW_ICON;
        assertTrue(HealerDialogueView.PAID_TEXT_X >= iconRight + HealerDialogueView.ROW_GAP, "the message starts right of the icon column");
        assertTrue(HealerDialogueView.PAID_TEXT_X + HealerDialogueView.PAID_TEXT_W <= HealerDialogueView.MIDDLE_X + HealerDialogueView.MIDDLE_W, "and stays in the column");
    }

    @Test void theFairyAppearsOnTheLineThatGivesHerAndStaysForTheRestOfTheTale() {
        HealerDialogue tale = HealerDialogue.tutorialStart();
        tale.setInstantText(true);
        while (tale.currentLine() != Msg.HEALER_TALE_FAIRIES) tale.advance(Language.ENGLISH);
        assertFalse(HealerDialogueView.fairyLine(tale), "the handles line is not hers");
        tale.advance(Language.ENGLISH);
        while (tale.currentLine() != Msg.HEALER_TALE_FAIRY) tale.advance(Language.ENGLISH);
        assertTrue(HealerDialogueView.fairyLine(tale));
        assertFalse(HealerDialogueView.fairyLine(new HealerDialogue(false, false, false, false, false)), "only the start tale");
    }

    @Test void theFairyFliesAcrossTheEmptyColumnAndNeverCrossesTheThread() {
        Rectangle keepOut = new Rectangle(HealerDialogueView.THREAD_X - HealerDialogueView.THREAD_KEEP_OUT, HealerDialogueView.THREAD_Y - HealerDialogueView.THREAD_KEEP_OUT,
            HealerDialogueView.THREAD_W + 2 * HealerDialogueView.THREAD_KEEP_OUT, HealerDialogueView.THREAD_H + 2 * HealerDialogueView.THREAD_KEEP_OUT);
        Vector2 at = new Vector2();
        float half = HealerDialogueView.FAIRY_SIZE / 2f;
        float last = Float.MAX_VALUE;
        for (int n = 0; n <= 300; n++) { // line time 0 to 3 s: waiting, flying, hovering
            float lt = n / 100f;
            HealerDialogueView.fairyPosition(lt, at);
            Rectangle body = new Rectangle(at.x - half, at.y - half, 2 * half, 2 * half);
            assertFalse(body.overlaps(keepOut), "she clears the thread at " + lt + " s");
            assertTrue(at.y + half < HealerDialogueView.WINDOW_Y + HealerDialogueView.WINDOW, "and stays above the plates");
            if (lt >= HealerDialogueView.FAIRY_APPEAR && lt <= HealerDialogueView.FAIRY_APPEAR + HealerDialogueView.FAIRY_FLIGHT) {
                assertTrue(at.x <= last + 1e-3f, "she only moves towards Edgard");
                last = at.x;
            }
        }
        HealerDialogueView.fairyPosition(0f, at);
        assertEquals(HealerDialogueView.FAIRY_START_X, at.x, 0f);
        HealerDialogueView.fairyPosition(10f + HealerDialogueView.FAIRY_BOB_PERIOD * 0f, at);
        assertEquals(HealerDialogueView.FAIRY_HOVER_X, at.x, 0f);
        assertTrue(HealerDialogueView.FAIRY_HOVER_X + half <= HealerDialogueView.EDGARD_WINDOW_X + HealerDialogueView.WINDOW, "she hovers over Edgard's window");
        assertTrue(HealerDialogueView.FAIRY_START_X - half >= HealerDialogueView.HEALER_WINDOW_X, "and starts inside the healer's");
    }

    @Test void theFairyClipsFollowTheGuidesFrames() {
        assertEquals(FairyGuide.APPEAR_FIRST, HealerDialogueView.fairyFrame(0f));
        assertEquals(FairyGuide.APPEAR_FIRST + 1, HealerDialogueView.fairyFrame(.25f));
        assertEquals(FairyGuide.FLY_FIRST, HealerDialogueView.fairyFrame(HealerDialogueView.FAIRY_APPEAR + .01f));
        assertEquals(FairyGuide.IDLE_FIRST, HealerDialogueView.fairyFrame(HealerDialogueView.FAIRY_APPEAR + HealerDialogueView.FAIRY_FLIGHT + .01f));
        assertTrue(HealerDialogueView.fairyFlipped(HealerDialogueView.FAIRY_APPEAR + .1f), "faces left on the way to Edgard");
        assertFalse(HealerDialogueView.fairyFlipped(0f));
        assertFalse(HealerDialogueView.fairyFlipped(HealerDialogueView.FAIRY_APPEAR + HealerDialogueView.FAIRY_FLIGHT + .1f));
    }
}
