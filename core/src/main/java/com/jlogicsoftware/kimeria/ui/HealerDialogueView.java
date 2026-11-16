package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.IntArray;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.List;

/**
 * The dialogue screen (D78, #337, playtest F3/F10): two portrait windows face each other in a carved walnut frame, joined
 * by the silver thread; the speaker is lit and bobs 1 px, the listener is dimmed; one text box holds every line. Logical
 * 640x360, Y down (the batch's camera). Text goes through {@link Overlay}'s Y-up pass as prebuilt blocks, rebuilt only when
 * a line, the language, a label or the checklist changes, so a frame allocates nothing. Portrait textures are owned by the
 * game's Assets cache; the frame art and the font are fetched on the first render.
 */
public final class HealerDialogueView {
    static final float SCREEN_W = 640f, SCREEN_H = 360f;
    /** Portrait windows: an 8 px band around a 160 px opening; the portrait is 158 px with 1 px of air for the bob. */
    static final float WINDOW = 176f, BAND = 8f, PORTRAIT = 158f, PORTRAIT_INSET = BAND + 1f;
    static final float EDGARD_WINDOW_X = 20f, HEALER_WINDOW_X = SCREEN_W - 20f - WINDOW, WINDOW_Y = 14f;
    static final float WINDOW_INSET = 12f;
    /** The crest sits on the window's top edge, centred on the 176 px window (79 px from its left). */
    static final float CREST_W = 17f, CREST_H = 7f, CREST_Y = WINDOW_Y - 6f, CREST_OFFSET = 79f;
    static final float PLATE_W = 128f, PLATE_H = 22f, PLATE_Y = 192f, PLATE_INSET = 6f, NAME_Y = PLATE_Y + 3f;
    /** The text box (its ink inside the band): 556 px of text, four lines of the 18 px dialogue font, one of them spare. */
    static final float BOX_X = 24f, BOX_Y = 226f, BOX_W = 592f, BOX_H = 86f, BOX_BAND_OUT = 4f, BOX_INSET = 10f;
    static final float TEXT_X = BOX_X + 18f, TEXT_W = BOX_W - 36f, TEXT_Y = BOX_Y + 7f;
    static final int TEXT_LINES = 4;
    static final float TEXT_H = TEXT_LINES * FontSizes.DIALOGUE;
    static final float NOTCH_W = 13f, NOTCH_H = 8f, NOTCH_Y = BOX_Y - BOX_BAND_OUT - NOTCH_H + 1f;
    static final float HINT_Y = BOX_Y + BOX_H + BOX_BAND_OUT + 4f;
    static final float PAGE_INSET = 10f;
    /** The middle column between the windows: the checklist and the choices of the pre-boss stop. */
    public static final float MIDDLE_X = EDGARD_WINDOW_X + WINDOW + 8f, MIDDLE_W = HEALER_WINDOW_X - MIDDLE_X - 8f;
    public static final float CHECKLIST_Y = 20f, CHOICE_Y = 84f, MARKER_INSET = 8f;
    static final float CHOICE_H = 26f, CHOICE_STEP = 28f, CHOICE_INSET = 4f;
    /** The thread art's top-left, and the curve of its span (art/ui/dialogue/thread.grid is drawn on the same curve). */
    static final float THREAD_X = 178f, THREAD_Y = 154f, THREAD_W = 284f, THREAD_H = 32f;
    static final float THREAD_X0 = 196f, THREAD_Y0 = 168f, THREAD_X1 = 444f, THREAD_Y1 = 164f, THREAD_SAG = 16f;
    static final float GLINT = 5f, GLINT_SECONDS = 0.8f;
    /** t(1-t) peaks at 1/4: this factor makes the sag the drop at the middle of the span. */
    private static final float PARABOLA_PEAK = 4f;
    /** The speaker lifts 1 px for the second half of every period. */
    static final float BOB_PERIOD = 1.2f;
    /** The listener's portrait multiplier: dim and moonlit, its outline still reads on the dusk window (test_dialogue_frame_sprites.py). */
    static final Color LISTENER = new Color(.55f, .58f, .70f, 1f);
    /** Opening, in fractions of {@link HealerDialogue#OPEN_SECONDS}: the windows unfold, the box opens, the thread draws in last. */
    static final float WINDOWS_OPEN = .5f, BOX_OPEN = .66f, THREAD_START = .33f;

    private static final Color HEALER_GREEN = new Color(.42f, .88f, .48f, 1f);
    /** The listener's name: dimmed, still at least 4.5:1 on the dimmed plate (healer green 5.0:1, white 7.9:1). */
    private static final float NAME_DIM = .65f;
    private static final Color EDGARD_NAME_DIM = new Color(NAME_DIM, NAME_DIM, NAME_DIM, 1f);
    private static final Color HEALER_NAME_DIM = new Color(HEALER_GREEN.r * NAME_DIM, HEALER_GREEN.g * NAME_DIM, HEALER_GREEN.b * NAME_DIM, 1f);
    private static final Color CHECKLIST = new Color(.88f, .89f, .93f, 1f);
    private static final Color CHOICE_IDLE = new Color(.75f, .77f, .82f, 1f), CHOICE_SELECTED = new Color(1f, .91f, .63f, 1f);
    private static final Color HINT = new Color(.67f, .69f, .75f, 1f);
    private static final String UI = "images/ui/dialogue-";
    /** Frame sizes of the authored strips (tools/sprite_layouts.py DIALOGUE_SHEETS); each strip's second frame starts one width in. */
    private static final float WINDOW_ART = 32f, PLATE_ART_W = 16f, BAND_ART = 24f, CHOICE_ART_W = 16f;
    private static final int MAX_CHOICES = 3;

    private final Assets assets;
    private final TextureRegion edgard, healer;
    // Fetched on the first render (tests build the view with stub assets for its layout and checklist).
    private Patch windowLit, windowDim, plateLit, plateDim, box, page, choiceIdle, choiceSelected;
    private TextureRegion crestLit, crestDim, notch, thread, glint;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private BitmapFontCache lineBlock, edgardName, healerName, checklistBlock, hintBlock, markerLeft, markerRight;
    private final BitmapFontCache[] choiceBlocks = new BitmapFontCache[MAX_CHOICES];
    private final float[] choiceTextY = new float[MAX_CHOICES];
    private Language blockLanguage;
    private Msg blockLine;
    private String blockChecklist;
    private HealerDialogue blockDialogue;
    /** Which side the name colours were set for: 1 Edgard lit, 0 the healer lit, -1 not yet. */
    private int namesLit = -1;
    private float markerRightWidth;
    private int choicesSelected = -1;
    // The checklist's inputs, read once per dialogue: the world is paused while it is open, so no pickup or coin changes
    // (the inventory and coin queries stream and would allocate every frame).
    private boolean hasBat, hasMushroom, allCoins;
    private int coinsSecured, coinsTotal;
    /**
     * Starting room of the glyph map: above the longest line in the catalogs (de HEALER_TALE_FIREGIFT, 257 characters;
     * {@code HealerDialogueTest} pins it), so it never grows in practice; a longer line grows it when the line changes.
     */
    static final int LINE_GLYPHS = 320;
    private final IntArray lineGlyphSource = new IntArray(true, LINE_GLYPHS);

    public HealerDialogueView(Assets assets) {
        this.assets = assets;
        edgard = assets.region("images/portraits/edgard.png", Texture.TextureFilter.Linear);
        healer = assets.region("images/portraits/healer.png", Texture.TextureFilter.Linear);
    }

    /** The choice under {@code p} (logical, Y down), or -1. */
    public int choiceAt(Vector2 p, int count) {
        if (p.x < MIDDLE_X || p.x > MIDDLE_X + MIDDLE_W) return -1;
        for (int i = 0; i < count; i++) {
            float top = CHOICE_Y + i * CHOICE_STEP;
            if (p.y >= top && p.y < top + CHOICE_H) return i;
        }
        return -1;
    }

    /** Where the notch sits: the centre of the speaker's window. */
    static float speakerCentreX(boolean edgardSpeaking) {
        return (edgardSpeaking ? EDGARD_WINDOW_X : HEALER_WINDOW_X) + WINDOW / 2f;
    }

    /** 1 when the speaker is lifted this frame, else 0. */
    static int bobLift(float time) {
        return time % BOB_PERIOD >= BOB_PERIOD / 2f ? 1 : 0;
    }

    /** The thread's centre line at screen x on its span (the glint rides it). */
    static float threadY(float x) {
        float t = (x - THREAD_X0) / (THREAD_X1 - THREAD_X0);
        return THREAD_Y0 + (THREAD_Y1 - THREAD_Y0) * t + THREAD_SAG * PARABOLA_PEAK * t * (1f - t);
    }

    public void render(Batch batch, Overlay overlay, HealerDialogue dialogue, Language lang,
                       Level level, ArtifactInventory inventory) {
        load();
        boolean edgardSpeaking = dialogue.edgardSpeaking();
        float open = Math.min(1f, dialogue.openProgress());
        batch.setColor(Color.WHITE);
        page.draw(batch, 0, 0, SCREEN_W, SCREEN_H);

        // Windows unfold from their middle line; the portraits, crests, plates and thread wait for them.
        float unfold = Math.min(1f, open / WINDOWS_OPEN);
        float windowH = Math.max(WINDOW_INSET * 2f, WINDOW * unfold);
        float windowY = WINDOW_Y + (WINDOW - windowH) / 2f;
        (edgardSpeaking ? windowLit : windowDim).draw(batch, EDGARD_WINDOW_X, windowY, WINDOW, windowH);
        (edgardSpeaking ? windowDim : windowLit).draw(batch, HEALER_WINDOW_X, windowY, WINDOW, windowH);
        boolean framed = unfold >= 1f;
        if (framed) {
            int lift = bobLift(dialogue.time());
            portrait(batch, edgard, EDGARD_WINDOW_X, edgardSpeaking, edgardSpeaking ? lift : 0);
            portrait(batch, healer, HEALER_WINDOW_X, !edgardSpeaking, edgardSpeaking ? 0 : lift);
            float crestOffset = CREST_OFFSET;
            batch.draw(edgardSpeaking ? crestLit : crestDim, EDGARD_WINDOW_X + crestOffset, CREST_Y, CREST_W, CREST_H);
            batch.draw(edgardSpeaking ? crestDim : crestLit, HEALER_WINDOW_X + crestOffset, CREST_Y, CREST_W, CREST_H);
            float plateOffset = (WINDOW - PLATE_W) / 2f;
            (edgardSpeaking ? plateLit : plateDim).draw(batch, EDGARD_WINDOW_X + plateOffset, PLATE_Y, PLATE_W, PLATE_H);
            (edgardSpeaking ? plateDim : plateLit).draw(batch, HEALER_WINDOW_X + plateOffset, PLATE_Y, PLATE_W, PLATE_H);
            thread(batch, open, dialogue, edgardSpeaking);
        }

        float boxOpen = Math.min(1f, open / BOX_OPEN);
        float boxH = Math.max(BOX_INSET * 2f, (BOX_H + BOX_BAND_OUT * 2f) * boxOpen);
        float boxY = BOX_Y - BOX_BAND_OUT + (BOX_H + BOX_BAND_OUT * 2f - boxH) / 2f;
        box.draw(batch, BOX_X - BOX_BAND_OUT, boxY, BOX_W + BOX_BAND_OUT * 2f, boxH);
        if (boxOpen >= 1f) batch.draw(notch, Math.round(speakerCentreX(edgardSpeaking) - NOTCH_W / 2f), NOTCH_Y, NOTCH_W, NOTCH_H);

        if (open < 1f) return; // text, choices and the checklist come once the frame is whole
        rebuildBlocks(overlay, dialogue, lang, level, inventory);
        if (framed) {
            overlay.queueBlock(edgardName, 1f);
            overlay.queueBlock(healerName, 1f);
        }
        overlay.queueBlock(lineBlock, glyphsFor(dialogue.visibleCharacters(lang)));
        if (dialogue.showsChecklist()) overlay.queueBlock(checklistBlock, 1f);
        if (dialogue.choosing()) {
            List<HealerDialogue.Choice> choices = dialogue.choices();
            for (int i = 0; i < choices.size(); i++) {
                boolean selected = i == dialogue.selected();
                (selected ? choiceSelected : choiceIdle).draw(batch, MIDDLE_X, CHOICE_Y + i * CHOICE_STEP, MIDDLE_W, CHOICE_H);
                overlay.queueBlock(choiceBlocks[i], 1f);
            }
            int selected = dialogue.selected();
            if (selected != choicesSelected) selectChoice(overlay, selected, choices.size());
            overlay.queueBlock(markerLeft, 1f);
            overlay.queueBlock(markerRight, 1f);
        } else if (dialogue.fullyRevealed(lang)) {
            overlay.queueBlock(hintBlock, 1f);
        }
    }

    private void thread(Batch batch, float open, HealerDialogue dialogue, boolean edgardSpeaking) {
        float drawn = Math.max(0f, Math.min(1f, (open - THREAD_START) / (1f - THREAD_START)));
        if (drawn <= 0f) return;
        if (drawn >= 1f) {
            batch.draw(thread, THREAD_X, THREAD_Y, THREAD_W, THREAD_H);
        } else { // the thread draws in from Edgard's wrist towards the healer: the left part of the art, same UVs as draw(region)
            float width = Math.max(1f, Math.round(THREAD_W * drawn));
            float u2 = thread.getU() + (thread.getU2() - thread.getU()) * width / THREAD_W;
            batch.draw(thread.getTexture(), THREAD_X, THREAD_Y, width, THREAD_H, thread.getU(), thread.getV2(), u2, thread.getV());
            return;
        }
        float run = dialogue.lineTime() / GLINT_SECONDS;
        if (run >= 1f) return;
        float t = edgardSpeaking ? run : 1f - run; // from the speaker to the listener
        float x = THREAD_X0 + (THREAD_X1 - THREAD_X0) * t;
        batch.draw(glint, Math.round(x - GLINT / 2f), Math.round(threadY(x) - GLINT / 2f), GLINT, GLINT);
    }

    private void portrait(Batch batch, TextureRegion image, float windowX, boolean lit, int lift) {
        batch.setColor(lit ? Color.WHITE : LISTENER);
        // Fit the whole canvas, including non-square replacements; Y down, so the lift subtracts.
        float scale = Math.min(PORTRAIT / image.getRegionWidth(), PORTRAIT / image.getRegionHeight());
        float width = image.getRegionWidth() * scale, height = image.getRegionHeight() * scale;
        batch.draw(image, windowX + PORTRAIT_INSET + (PORTRAIT - width) / 2f,
            WINDOW_Y + PORTRAIT_INSET + (PORTRAIT - height) / 2f - lift, width, height);
        batch.setColor(Color.WHITE);
    }

    /** Rebuilds the text blocks whose source changed: an event (a new line, a pickup, a language), not a frame. */
    private void rebuildBlocks(Overlay overlay, HealerDialogue dialogue, Language lang, Level level, ArtifactInventory inventory) {
        boolean languageChanged = lang != blockLanguage;
        if (languageChanged || dialogue != blockDialogue) {
            centred(edgardName, overlay, Msg.EDGARD.t(lang), EDGARD_WINDOW_X + (WINDOW - PLATE_W) / 2f, NAME_Y, PLATE_W, Color.WHITE);
            centred(healerName, overlay, Msg.HEALER.t(lang), HEALER_WINDOW_X + (WINDOW - PLATE_W) / 2f, NAME_Y, PLATE_W, HEALER_GREEN);
            String hint = Msg.HEALER_HINT.t(lang);
            layout.setText(font, hint, HINT, TEXT_W, Align.right, false);
            hintBlock.setText(layout, TEXT_X, overlay.textY(HINT_Y));
            List<HealerDialogue.Choice> choices = dialogue.choices();
            for (int i = 0; i < choices.size(); i++) {
                String label = choiceLabel(choices.get(i)).t(lang);
                layout.setText(font, label, CHOICE_IDLE, MIDDLE_W, Align.center, false);
                choiceTextY[i] = CHOICE_Y + i * CHOICE_STEP + (CHOICE_H - layout.height) / 2f;
                choiceBlocks[i].setText(layout, MIDDLE_X, overlay.textY(choiceTextY[i]));
            }
            layout.setText(font, ">");
            markerLeft.setText(layout, 0, 0);
            layout.setText(font, "<");
            markerRight.setText(layout, 0, 0);
            markerRightWidth = layout.width;
            choicesSelected = -1;
            namesLit = -1;
            blockLine = null; // a new dialogue rebuilds its line even when it opens on the same Msg
            if (dialogue.showsChecklist()) {
                hasBat = inventory.hasIngredient(level.name, ItemType.BAT);
                hasMushroom = inventory.hasIngredient(level.name, ItemType.MUSHROOM);
                coinsTotal = level.forestCoinTotal();
                coinsSecured = level.forestCoinsSecured();
                allCoins = coinsTotal > 0 && coinsSecured == coinsTotal;
            }
            blockDialogue = dialogue;
        }
        if (languageChanged || dialogue.currentLine() != blockLine) {
            boolean edgardSpeaking = dialogue.edgardSpeaking();
            String text = dialogue.completeText(lang);
            layout.setText(font, text, edgardSpeaking ? Color.WHITE : HEALER_GREEN, TEXT_W, edgardSpeaking ? Align.left : Align.right, true);
            lineBlock.setText(layout, TEXT_X, overlay.textY(TEXT_Y));
            mapGlyphs(layout, text, lineGlyphSource);
            blockLine = dialogue.currentLine();
        }
        boolean lit = dialogue.edgardSpeaking();
        if ((lit ? 1 : 0) != namesLit) {
            edgardName.setColors(lit ? Color.WHITE : EDGARD_NAME_DIM);
            healerName.setColors(lit ? HEALER_NAME_DIM : HEALER_GREEN);
            namesLit = lit ? 1 : 0;
        }
        if (dialogue.showsChecklist()) {
            String checklist = checklistText(lang, dialogue.paid(), hasBat, hasMushroom, allCoins, coinsSecured, coinsTotal);
            if (checklist != blockChecklist) {
                layout.setText(font, checklist, CHECKLIST, MIDDLE_W, Align.center, true);
                checklistBlock.setText(layout, MIDDLE_X, overlay.textY(CHECKLIST_Y));
                blockChecklist = checklist;
            }
        }
        blockLanguage = lang;
    }

    private void centred(BitmapFontCache block, Overlay overlay, String text, float x, float yFromTop, float width, Color color) {
        layout.setText(font, text, color, width, Align.center, false);
        block.setText(layout, x, overlay.textY(yFromTop));
    }

    private void selectChoice(Overlay overlay, int selected, int count) {
        for (int i = 0; i < count; i++) choiceBlocks[i].setColors(i == selected ? CHOICE_SELECTED : CHOICE_IDLE);
        float y = choiceTextY[selected];
        markerLeft.setColors(CHOICE_SELECTED);
        markerRight.setColors(CHOICE_SELECTED);
        float textY = overlay.textY(y);
        markerLeft.setPosition(MIDDLE_X + MARKER_INSET, textY);
        markerRight.setPosition(MIDDLE_X + MIDDLE_W - MARKER_INSET - markerRightWidth, textY);
        choicesSelected = selected;
    }

    private static Msg choiceLabel(HealerDialogue.Choice choice) {
        return switch (choice) {
            case RETURN -> Msg.HEALER_RETURN;
            case BREW -> Msg.HEALER_BREW;
            case FIGHT -> Msg.HEALER_FIGHT;
            case CONTINUE -> throw new IllegalStateException("Continuation has no choice button");
        };
    }

    /**
     * For each laid-out glyph of {@code layout}, the index of its source character in {@code text}: wrapping drops the
     * spaces at line breaks, so the typewriter's letter count maps to a glyph count through this table (built when the line
     * changes, read per frame). Every glyph is mapped; {@code out} grows if a line is longer than its room. A glyph that is
     * not in the text (a layout of another string, a substituted glyph) fails loudly instead of hiding the rest of the line.
     */
    static void mapGlyphs(GlyphLayout layout, String text, IntArray out) {
        out.clear();
        int source = 0;
        for (int r = 0; r < layout.runs.size; r++) {
            GlyphLayout.GlyphRun run = layout.runs.get(r);
            for (int g = 0; g < run.glyphs.size; g++) {
                char id = (char) run.glyphs.get(g).id;
                while (source < text.length() && text.charAt(source) != id) source++;
                if (source >= text.length()) throw new IllegalStateException("Glyph '" + id + "' is not in the line: " + text);
                out.add(source++);
            }
        }
    }

    /** How many glyphs of a mapped line show when {@code characters} letters of its source are revealed. */
    static int glyphsFor(IntArray map, int characters) {
        int shown = 0;
        while (shown < map.size && map.get(shown) < characters) shown++;
        return shown;
    }

    private int glyphsFor(int characters) {
        return glyphsFor(lineGlyphSource, characters);
    }

    private void load() {
        if (font != null) return;
        windowLit = new Patch(assets, UI + "window.png", 0, WINDOW_ART, WINDOW_ART, WINDOW_INSET, WINDOW_INSET);
        windowDim = new Patch(assets, UI + "window.png", WINDOW_ART, WINDOW_ART, WINDOW_ART, WINDOW_INSET, WINDOW_INSET);
        plateLit = new Patch(assets, UI + "plate.png", 0, PLATE_ART_W, PLATE_H, PLATE_INSET, PLATE_H / 2f);
        plateDim = new Patch(assets, UI + "plate.png", PLATE_ART_W, PLATE_ART_W, PLATE_H, PLATE_INSET, PLATE_H / 2f);
        box = new Patch(assets, UI + "box.png", 0, BAND_ART, BAND_ART, BOX_INSET, BOX_INSET);
        page = new Patch(assets, UI + "page.png", 0, BAND_ART, BAND_ART, PAGE_INSET, PAGE_INSET);
        choiceIdle = new Patch(assets, UI + "choice.png", 0, CHOICE_ART_W, CHOICE_H, CHOICE_INSET, CHOICE_H / 2f);
        choiceSelected = new Patch(assets, UI + "choice.png", CHOICE_ART_W, CHOICE_ART_W, CHOICE_H, CHOICE_INSET, CHOICE_H / 2f);
        crestLit = assets.region(UI + "crest.png", 0, 0, CREST_W, CREST_H);
        crestDim = assets.region(UI + "crest.png", CREST_W, 0, CREST_W, CREST_H);
        notch = assets.region(UI + "notch.png", 0, 0, NOTCH_W, NOTCH_H);
        thread = assets.region(UI + "thread.png", 0, 0, THREAD_W, THREAD_H);
        glint = assets.region(UI + "glint.png", 0, 0, GLINT, GLINT);
        font = assets.font(FontSizes.DIALOGUE);
        boolean integer = font.usesIntegerPositions();
        lineBlock = new BitmapFontCache(font, integer);
        edgardName = new BitmapFontCache(font, integer);
        healerName = new BitmapFontCache(font, integer);
        checklistBlock = new BitmapFontCache(font, integer);
        hintBlock = new BitmapFontCache(font, integer);
        markerLeft = new BitmapFontCache(font, integer);
        markerRight = new BitmapFontCache(font, integer);
        for (int i = 0; i < MAX_CHOICES; i++) choiceBlocks[i] = new BitmapFontCache(font, integer);
    }

    private Language checklistLanguage;
    private boolean checklistPaid, checklistBat, checklistMushroom, checklistCoinsDone;
    private int checklistSecured = -1, checklistTotal = -1;
    private String checklist = "";

    /**
     * The three-line ingredient checklist (or the paid message). The text is rebuilt only when one of its inputs changes
     * (a pickup, a coin, the language), so the per-frame call concatenates nothing.
     */
    public String checklistText(Language lang, boolean paid, boolean bat, boolean mushroom, boolean coinsDone, int secured, int total) {
        if (lang == checklistLanguage && paid == checklistPaid && bat == checklistBat && mushroom == checklistMushroom
            && coinsDone == checklistCoinsDone && secured == checklistSecured && total == checklistTotal) return checklist;
        checklistLanguage = lang; checklistPaid = paid; checklistBat = bat; checklistMushroom = mushroom;
        checklistCoinsDone = coinsDone; checklistSecured = secured; checklistTotal = total;
        checklist = paid ? Msg.PAID.t(lang)
            : mark(bat) + Msg.BAT.t(lang) + "\n" + mark(mushroom) + Msg.MUSHROOM.t(lang) + "\n" + mark(coinsDone)
                + Msg.FOREST_COINS_COUNT.format(lang, "count", Integer.toString(secured), "total", Integer.toString(total));
        return checklist;
    }
    private static String mark(boolean present) { return present ? "[+] " : "[-] "; }

    /**
     * A nine-patch from one frame of an authored strip, drawn through the Y-down camera: nine regions cut once, each
     * flipped by {@link Assets#region(String, float, float, float, float)}; the corners keep their size, the edges and
     * the middle stretch. A frame whose height equals its two insets is a three-patch.
     */
    static final class Patch {
        private static final int SIDE = 3; // left/middle/right, top/middle/bottom
        private final TextureRegion[] parts = new TextureRegion[SIDE * SIDE];
        private final float insetX, insetY, middleW, middleH;

        Patch(Assets assets, String path, float frameX, float w, float h, float insetX, float insetY) {
            this.insetX = insetX; this.insetY = insetY;
            middleW = w - insetX * 2f; middleH = h - insetY * 2f;
            float[] xs = {0, insetX, w - insetX}, ws = {insetX, middleW, insetX};
            float[] ys = {0, insetY, h - insetY}, hs = {insetY, middleH, insetY};
            for (int row = 0; row < SIDE; row++) for (int col = 0; col < SIDE; col++)
                parts[row * SIDE + col] = hs[row] > 0 && ws[col] > 0
                    ? assets.region(path, frameX + xs[col], ys[row], ws[col], hs[row]) : null;
        }

        void draw(Batch batch, float x, float y, float w, float h) {
            float midW = Math.max(0f, w - insetX * 2f), midH = Math.max(0f, h - insetY * 2f);
            float top = Math.min(insetY, h / 2f), side = Math.min(insetX, w / 2f);
            for (int row = 0; row < SIDE; row++) {
                float py = row == 0 ? y : row == 1 ? y + top : y + h - top;
                float ph = row == 1 ? midH : top;
                if (ph <= 0f) continue;
                for (int col = 0; col < SIDE; col++) {
                    TextureRegion part = parts[row * SIDE + col];
                    if (part == null) continue;
                    float px = col == 0 ? x : col == 1 ? x + side : x + w - side;
                    float pw = col == 1 ? midW : side;
                    if (pw > 0f) batch.draw(part, px, py, pw, ph);
                }
            }
        }
    }
}
