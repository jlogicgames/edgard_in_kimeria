package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.input.KeyBindings;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input.Keys;

import static org.junit.jupiter.api.Assertions.*;

/** D135 (#532): the Controls screen lists the real bindings, fits in every language and mode, and draws without allocating. */
class ControlsViewTest {
    private static final double DRAW_CEILING_BYTES_PER_FRAME = 0.5;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private Overlay overlay;
    private ControlsView view;

    @BeforeEach void setUp() {
        harness.setUp();
        Assets assets = new Assets() {
            @Override public BitmapFont font(int size) { return new MetricsFont(size); }
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        };
        overlay = new Overlay(assets, 640, 360, null);
        view = new ControlsView(assets, overlay);
    }

    @AfterEach void tearDown() { Shaders.dispose(); harness.restoreGlobals(); }

    @Test void theDeviceFollowsTouchThenPadThenKeyboard() {
        assertEquals(ControlsView.Mode.TOUCH, ControlsView.modeFor(true, true));
        assertEquals(ControlsView.Mode.TOUCH, ControlsView.modeFor(true, false));
        assertEquals(ControlsView.Mode.GAMEPAD, ControlsView.modeFor(false, true));
        assertEquals(ControlsView.Mode.KEYBOARD, ControlsView.modeFor(false, false));
    }

    @Test void theKeyboardColumnsNameTheRealBindings() {
        String[][] rows = ControlsView.rows(ControlsView.Mode.KEYBOARD, Language.ENGLISH);
        KeyBindings right = KeyBindings.RIGHT_HANDED, left = KeyBindings.LEFT_HANDED;
        assertEquals(8, rows.length);
        assertEquals("Move", rows[0][0]);
        assertEquals("A / D", rows[0][1]);
        assertEquals("W / S", rows[1][1]);
        assertEquals(right.upName(), rows[2][1]);       // "Pick up / use": the Up key of each hand (D126)
        assertEquals(right.downName(), rows[3][1]);     // "Put down"
        assertEquals(Keys.toString(Keys.J), rows[4][1]);
        assertEquals(right.actionName(), rows[5][1]);   // Sword
        assertEquals(right.rangedName(), rows[6][1]);   // Throw / Fireball
        assertEquals("Esc", rows[7][1]);
        assertEquals(left.upName(), rows[2][2]);
        assertEquals(left.downName(), rows[3][2]);
        assertEquals(Keys.toString(Keys.Z), rows[4][2]);
        assertEquals(left.actionName(), rows[5][2]);
        assertEquals(left.rangedName(), rows[6][2]);
        assertEquals("Esc", rows[7][2]);
    }

    @Test void theGamepadColumnsFollowTheMirroredLayout() {
        String[][] rows = ControlsView.rows(ControlsView.Mode.GAMEPAD, Language.ENGLISH);
        assertEquals("Left stick / D-pad", rows[0][1]);
        assertEquals("Right stick", rows[0][2]);
        assertEquals("Left stick / D-pad up", rows[2][1]);
        assertEquals("Right stick up", rows[2][2]);
        assertEquals("Left stick / D-pad down", rows[3][1]);
        assertEquals("A", rows[4][1]);
        assertEquals("D-pad down", rows[4][2]);
        assertEquals("X / B", rows[5][1]);
        assertEquals("D-pad left/right", rows[5][2]);
        assertEquals("Y", rows[6][1]);
        assertEquals("D-pad up", rows[6][2]);
        assertEquals("Start", rows[7][1]);
    }

    @Test void everyModeFitsBetweenTheTitleAndTheButtonsInEveryLanguage() {
        for (Language language : Language.values())
            for (ControlsView.Mode mode : ControlsView.Mode.values()) {
                float bottom = view.tableBottom(mode, language);
                if (mode == ControlsView.Mode.TOUCH) bottom = ControlsView.TOP + ControlsView.HEADER_H + ControlsView.PHONE_H + 8f
                    + overlay.bodyLines(Msg.STICK_HINT.t(language), ControlsView.HINT_W) * ControlsView.LINE_H;
                assertTrue(bottom <= ControlsView.MAX_BOTTOM, language.code() + " " + mode + " ends at " + bottom);
            }
    }

    @Test void everyCellFitsItsColumnOnOneOrTwoLines() {
        float cell = ControlsView.CELL_W;
        for (Language language : Language.values())
            for (ControlsView.Mode mode : new ControlsView.Mode[]{ControlsView.Mode.KEYBOARD, ControlsView.Mode.GAMEPAD})
                for (String[] row : ControlsView.rows(mode, language)) {
                    assertTrue(overlay.bodyLines(row[0], ControlsView.LABEL_W) <= ControlsView.MAX_CELL_LINES, language.code() + " " + row[0]);
                    assertTrue(overlay.bodyLines(row[1], cell) <= ControlsView.MAX_CELL_LINES, language.code() + " " + row[1]);
                    assertTrue(overlay.bodyLines(row[2], cell) <= ControlsView.MAX_CELL_LINES, language.code() + " " + row[2]);
                }
    }

    @Test void drawingEveryModeAndHandAllocatesNothingPerFrame() {
        NoopBatch batch = new NoopBatch();
        for (ControlsView.Mode mode : ControlsView.Mode.values())
            for (boolean left : new boolean[]{false, true}) {
                double bytes = AllocationProbe.bytesPerFrame(i -> {
                    overlay.beginFrame();
                    view.draw(batch, mode, Language.ENGLISH, left);
                    overlay.flushText(batch);
                });
                assertTrue(bytes <= DRAW_CEILING_BYTES_PER_FRAME, mode + " left=" + left + ": " + bytes + " B/frame");
            }
    }
}
