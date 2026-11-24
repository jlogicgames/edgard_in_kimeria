package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;

/**
 * The Controls screen's body (D135, #532): both hand layouts side by side for the input mode in use, the active hand lit
 * and the other grey, so toggling Left-handed visibly swaps the highlight. The title and the buttons belong to the menu;
 * this draws what sits between them, from y {@link #TOP} to at most {@link #MAX_BOTTOM}.
 *
 * <p>Text is laid out once into {@link BitmapFontCache} blocks and rebuilt only when the mode, language or handedness
 * changes (a state change, never a frame), so {@link #draw} allocates nothing.
 */
public final class ControlsView {
    /** What the table lists: the keyboard, a connected pad or the touch layout. */
    public enum Mode { KEYBOARD, GAMEPAD, TOUCH }

    public static final float TOP = 52f, MAX_BOTTOM = 228f;
    public static final float LABEL_X = 40f, LABEL_W = 200f;
    public static final float RIGHT_X = 250f, LEFT_X = 430f, COLUMN_W = 170f, CELL_PAD = 8f, LINE_H = 18f, HEADER_H = 24f;
    private static final String ART = "images/HUD/touch/";
    /** The touch preview: two mini screens, the stick and the three buttons placed inside each (mirrored for the left hand). */
    public static final float PHONE_H = 112f, HINT_W = 560f, STICK_LABEL_W = 80f;
    private static final float PHONE_W = 260f, PHONE_RIGHT_X = 40f, PHONE_LEFT_X = 340f, STICK = 52f, BUTTON = 30f;
    private static final float STICK_INSET = 44f, KNOB_DRAW = 22f, STICK_LIFT = 8f, STICK_LABEL_LIFT = 22f;
    public static final float BUTTON_LABEL_W = 96f;
    private static final float BUTTON_INSET = 34f, BUTTON_TOP = 20f, BUTTON_STEP = 36f, BUTTON_LABEL_GAP = 24f, BUTTON_LABEL_LIFT = 9f;
    private static final int BLOCK_CAPACITY = 64, HANDS = 2, LABEL_COLUMN = 0, RIGHT_COLUMN = 1, LEFT_COLUMN = 2, BUTTON_COUNT = 3, HINT_LINES = 2;
    private static final float HINT_X = 40f, HINT_GAP = 8f;
    /** Source size of the touch art cut for the preview (the same files {@code TouchControls} cuts). */
    private static final int BASE_ART = TouchControls.BUTTON_ART_SIZE, KNOB_ART = (int) TouchControls.KNOB_SIZE;
    /** Text width of a table cell: the column minus the padding each side. */
    public static final float CELL_W = 154f;
    /** Wrap width of a hand header (the column minus one pad). */
    public static final float HEADER_W = 162f;
    private static final float BORDER = 1f, BORDER_BOTH = 2f, PANEL_TOP_PAD = 4f;
    /** Longest text cell or label, in lines, the table accepts ({@code ControlsViewTest}). */
    public static final int MAX_CELL_LINES = 2;

    private static final Color ACTIVE = Color.WHITE, DIM = new Color(0.45f, 0.45f, 0.5f, 1f), LABEL = new Color(0.78f, 0.78f, 0.82f, 1f);
    private static final Color GOLD = new Color(1f, 0.82f, 0.3f, 1f);
    private static final Color LIT_FILL = new Color(0.16f, 0.13f, 0.06f, 0.95f), DIM_FILL = new Color(0.08f, 0.08f, 0.11f, 0.9f);
    private static final Color DIM_BORDER = new Color(0.24f, 0.24f, 0.28f, 1f);
    private static final Color DIMMED_ART = new Color(1f, 1f, 1f, 0.4f);

    private final Overlay overlay;
    private final TextureRegion stickBase, stickKnob, disc;
    private final Array<BitmapFontCache> blocks = new Array<>(false, BLOCK_CAPACITY);
    private Mode builtMode;
    private Language builtLanguage;
    private boolean builtLeft, built;
    private float tableBottom;

    public ControlsView(Assets assets, Overlay overlay) {
        this.overlay = overlay;
        stickBase = assets.region(ART + "base.png", 0, 0, BASE_ART, BASE_ART);
        stickKnob = assets.region(ART + "knob.png", 0, 0, KNOB_ART, KNOB_ART);
        disc = assets.region(ART + "disc.png", 0, 0, BASE_ART, BASE_ART);
    }

    /** Touch first, then a connected pad, else the keyboard (the intro's skip hint picks its device the same way, D118). */
    public static Mode modeFor(boolean touch, boolean padConnected) {
        return touch ? Mode.TOUCH : padConnected ? Mode.GAMEPAD : Mode.KEYBOARD;
    }

    /** One row per action for the keyboard or the pad: label, right-hand cell, left-hand cell. Letters and Esc/Start stay as written. */
    public static String[][] rows(Mode mode, Language l) {
        String upDown = Msg.KEY_UP.t(l) + " / " + Msg.KEY_DOWN.t(l);
        if (mode == Mode.KEYBOARD) {
            return new String[][]{
                {Msg.MOVE.t(l), "A / D", Msg.KEY_ARROWS.t(l)},
                {Msg.CLIMB.t(l), "W / S", upDown},
                {Msg.PICK_UP_USE.t(l), "W", Msg.KEY_UP.t(l)},
                {Msg.PUT_DOWN.t(l), "S", Msg.KEY_DOWN.t(l)},
                {Msg.JUMP.t(l), "J", "Z"},
                {Msg.ATTACK.t(l), "K", "X"},
                {Msg.THROW_FIREBALL.t(l), "L", "C"},
                {Msg.PAUSE.t(l), "Esc", "Esc"}};
        }
        String dpad = Msg.PAD_DPAD.t(l), rightStick = Msg.RIGHT_STICK.t(l);
        return new String[][]{
            {Msg.MOVE.t(l), Msg.LEFT_STICK.t(l) + " / " + dpad, rightStick},
            {Msg.CLIMB.t(l), Msg.LEFT_STICK.t(l) + " / " + dpad, rightStick},
            {Msg.PICK_UP_USE.t(l), Msg.LEFT_STICK.t(l) + " / " + dpad + " " + Msg.UP.t(l), rightStick + " " + Msg.UP.t(l)},
            {Msg.PUT_DOWN.t(l), Msg.LEFT_STICK.t(l) + " / " + dpad + " " + Msg.DOWN.t(l), rightStick + " " + Msg.DOWN.t(l)},
            {Msg.JUMP.t(l), "A", dpad + " " + Msg.DOWN.t(l)},
            {Msg.ATTACK.t(l), "X / B", Msg.DPAD_ACTION.t(l)},
            {Msg.THROW_FIREBALL.t(l), "Y", Msg.DPAD_UP.t(l)},
            {Msg.PAUSE.t(l), "Start", "Start"}};
    }

    /** Bottom edge of the table in screen pixels for the rows' wrapped height; a test keeps it at or above {@link #MAX_BOTTOM}. */
    public float tableBottom(Mode mode, Language l) {
        float y = TOP + HEADER_H;
        for (String[] row : rows(mode, l)) y += rowLines(row) * LINE_H;
        return y;
    }

    private int rowLines(String[] row) {
        int lines = Math.max(1, overlay.bodyLines(row[LABEL_COLUMN], LABEL_W));
        lines = Math.max(lines, overlay.bodyLines(row[RIGHT_COLUMN], CELL_W));
        return Math.max(lines, overlay.bodyLines(row[LEFT_COLUMN], CELL_W));
    }

    private void rebuild(Mode mode, Language l, boolean left) {
        blocks.clear();
        Color rightColor = left ? DIM : ACTIVE, leftColor = left ? ACTIVE : DIM;
        blocks.add(overlay.textBlock(Msg.RIGHT_HAND.t(l), RIGHT_X, TOP, HEADER_W, Align.left, left ? DIM : GOLD));
        blocks.add(overlay.textBlock(Msg.LEFT_HAND.t(l), LEFT_X, TOP, HEADER_W, Align.left, left ? GOLD : DIM));
        if (mode == Mode.TOUCH) {
            buildTouch(l, left);
            return;
        }
        float y = TOP + HEADER_H;
        for (String[] row : rows(mode, l)) {
            blocks.add(overlay.textBlock(row[LABEL_COLUMN], LABEL_X, y, LABEL_W, Align.left, LABEL));
            blocks.add(overlay.textBlock(row[RIGHT_COLUMN], RIGHT_X, y, CELL_W, Align.left, rightColor));
            blocks.add(overlay.textBlock(row[LEFT_COLUMN], LEFT_X, y, CELL_W, Align.left, leftColor));
            y += rowLines(row) * LINE_H;
        }
        tableBottom = y;
    }

    private void buildTouch(Language l, boolean left) {
        String[] labels = {Msg.ATTACK.t(l), Msg.FIREBALL.t(l), Msg.JUMP.t(l)}; // BUTTON_COUNT of them, top to bottom
        for (int hand = 0; hand < HANDS; hand++) {
            boolean active = (hand == 1) == left, stickLeft = hand == 0;
            float x = hand == 0 ? PHONE_RIGHT_X : PHONE_LEFT_X;
            float buttonX = stickLeft ? x + PHONE_W - BUTTON_INSET : x + BUTTON_INSET;
            for (int i = 0; i < BUTTON_COUNT; i++) {
                float cy = TOP + HEADER_H + BUTTON_TOP + i * BUTTON_STEP;
                float labelX = stickLeft ? buttonX - BUTTON_LABEL_GAP - BUTTON_LABEL_W : buttonX + BUTTON_LABEL_GAP;
                blocks.add(overlay.textBlock(labels[i], labelX, cy - BUTTON_LABEL_LIFT, BUTTON_LABEL_W, stickLeft ? Align.right : Align.left, active ? ACTIVE : DIM));
            }
            float stickX = stickLeft ? x + STICK_INSET : x + PHONE_W - STICK_INSET;
            blocks.add(overlay.textBlock(Msg.STICK.t(l), stickX - STICK_LABEL_W / 2f, TOP + HEADER_H + PHONE_H - STICK_LABEL_LIFT, STICK_LABEL_W, Align.center, active ? ACTIVE : DIM));
        }
        blocks.add(overlay.textBlock(Msg.STICK_HINT.t(l), HINT_X, TOP + HEADER_H + PHONE_H + HINT_GAP, HINT_W, Align.center, LABEL));
        tableBottom = TOP + HEADER_H + PHONE_H + HINT_GAP + HINT_LINES * LINE_H;
    }

    /** Bottom of what the last {@link #draw} laid out. */
    public float bottom() { return tableBottom; }

    public void draw(Batch batch, Mode mode, Language language, boolean leftHanded) {
        if (!built || mode != builtMode || language != builtLanguage || leftHanded != builtLeft) {
            rebuild(mode, language, leftHanded);
            built = true;
            builtMode = mode;
            builtLanguage = language;
            builtLeft = leftHanded;
        }
        if (mode == Mode.TOUCH) drawPhones(batch, leftHanded);
        else {
            panel(batch, RIGHT_X - CELL_PAD, !leftHanded, tableBottom);
            panel(batch, LEFT_X - CELL_PAD, leftHanded, tableBottom);
        }
        for (int i = 0; i < blocks.size; i++) overlay.queueBlock(blocks.get(i), 1f);
    }

    private void panel(Batch batch, float x, boolean active, float bottom) {
        quad(batch, x, TOP - PANEL_TOP_PAD, COLUMN_W, bottom - TOP + PANEL_TOP_PAD, active ? GOLD : DIM_BORDER, active ? LIT_FILL : DIM_FILL);
    }

    private static void quad(Batch batch, float x, float y, float w, float h, Color border, Color fill) {
        batch.setColor(border);
        batch.draw(Shaders.whiteQuad(), x - BORDER, y - BORDER, w + BORDER_BOTH, h + BORDER_BOTH);
        batch.setColor(fill);
        batch.draw(Shaders.whiteQuad(), x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    private void drawPhones(Batch batch, boolean left) {
        for (int hand = 0; hand < HANDS; hand++) {
            boolean active = (hand == 1) == left, stickLeft = hand == 0;
            float x = hand == 0 ? PHONE_RIGHT_X : PHONE_LEFT_X, y = TOP + HEADER_H;
            quad(batch, x, y, PHONE_W, PHONE_H, active ? GOLD : DIM_BORDER, active ? LIT_FILL : DIM_FILL);
            batch.setColor(active ? Color.WHITE : DIMMED_ART);
            float stickX = stickLeft ? x + STICK_INSET : x + PHONE_W - STICK_INSET, stickY = y + PHONE_H / 2f - STICK_LIFT;
            batch.draw(stickBase, stickX - STICK / 2f, stickY - STICK / 2f, STICK, STICK);
            batch.draw(stickKnob, stickX - KNOB_DRAW / 2f, stickY - KNOB_DRAW / 2f, KNOB_DRAW, KNOB_DRAW);
            float buttonX = stickLeft ? x + PHONE_W - BUTTON_INSET : x + BUTTON_INSET;
            for (int i = 0; i < BUTTON_COUNT; i++) batch.draw(disc, buttonX - BUTTON / 2f, y + BUTTON_TOP + i * BUTTON_STEP - BUTTON / 2f, BUTTON, BUTTON);
            batch.setColor(Color.WHITE);
        }
    }
}
