package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;

/**
 * Immediate-mode UI for the menu/pause/game-over screens (Dart drew these as
 * Flutter widgets via Flame's overlay system, Rust as a {@code bevy_ui} node
 * tree; here they're plain draws + hit-tests, to avoid a Scene2D skin for a
 * handful of simple screens).
 *
 * <p>A menu frame allocates nothing (#464): a string is laid out the first time it is shown and the layout is kept, and the
 * queued draws live in reused arrays. The kept layouts assume the menu's strings come from a fixed set (the catalogs and
 * the labels built from them, and the story paragraphs {@link #bodyHeight} measures); the one changing string, the FPS
 * counter, has a single layout of its own.
 *
 * <p>Panels/buttons are solid-colour quads, drawn immediately through the
 * shared Y-down screen projection. Text is different: a {@link BitmapFont}'s
 * glyph quads are built from UVs baked into each {@code Glyph} at generation
 * time, not from its page {@code TextureRegion} -- flipping that region (the
 * trick used everywhere else for the Y-down camera) has no effect on it. So
 * every text draw is instead queued and flushed once, at the very end of a
 * frame's UI pass (see {@link #flushText}), through a plain Y-up projection
 * sized to the same logical resolution, where an unmodified font is correct
 * by construction. Queuing (rather than switching the projection back and
 * forth per element like the background quads do) avoids the repeated
 * {@code SpriteBatch} flushes that come with each projection swap.
 */
public class Overlay {
    public static final float BUTTON_W = 220f, BUTTON_H = 40f, BUTTON_GAP = 8f;
    /** The tighter rows of a long list (Options, #494): as tall as the selection arms, so these still sit inside the row. */
    public static final float COMPACT_BUTTON_H = 36f, COMPACT_BUTTON_GAP = 5f;
    /**
     * #415: the selected button is marked by the Kimeria arms (24x36, native), one on each side, outside the gold frame
     * (3 px) with a 6 px gap, centred on the button's row. {@link #MAX_BUTTONS_W} keeps both inside the 640 px screen with an
     * 8 px margin: 640 - 2 * (8 + 3 + 6 + 24) = 558.
     */
    public static final float SELECTED_FRAME = 3f, SELECTION_ARMS_GAP = 6f;
    /** The thin border of an unselected button. */
    private static final float IDLE_FRAME = 1f;
    public static final float SELECTION_ARMS_W = KimeriaArms.WIDTH, SELECTION_ARMS_H = KimeriaArms.HEIGHT;
    public static final float SCREEN_MARGIN = 8f;
    public static final float MAX_BUTTONS_W = 640f - 2f * (SCREEN_MARGIN + SELECTED_FRAME + SELECTION_ARMS_GAP + SELECTION_ARMS_W);

    private static final Color IDLE_BORDER = new Color(0.45f, 0.45f, 0.52f, 1f);
    private static final Color IDLE_FILL = new Color(0.10f, 0.10f, 0.14f, 0.92f);
    private static final Color HOVER_FILL = new Color(0.27f, 0.27f, 0.36f, 0.96f);
    private static final Color SELECTED_BORDER = new Color(1f, 0.82f, 0.3f, 1f);
    private static final Color HINT_GREY = new Color(0.8f, 0.8f, 0.8f, 1f);
    /** Space the menu hint leaves at the two sides of the screen together. */
    private static final float HINT_SIDE_ROOM = 32f;
    /** Padding of the widest label inside its button, both sides together. */
    private static final float LABEL_PADDING = 48f;

    /**
     * D137 (#491): the bottom of the menu hint's text stays this far above the screen's bottom edge (it was 6 units, hard against it: 3.7 CSS
     * pixels on a 390 px portrait phone). 12 units is 7 CSS pixels there and 12 at one pixel per unit; the About page's buttons end
     * 9 units above the hint's top, so it cannot go much higher.
     */
    public static final float HINT_BOTTOM_MARGIN = 12f;

    private final Assets assets;
    private final BitmapFont titleFont;
    /** The body and label fonts follow the text tier (D137): refreshed by {@link #syncFonts} when {@link Assets#textGeneration()} moves. */
    private BitmapFont bodyFont, hintFont;
    private int fontsGeneration = -1;
    private final BitmapFont buttonFont;
    /** The story font of the intro book (D133) and its dark ink on the parchment leaves. */
    private final BitmapFont storyFont;
    private static final Color STORY_INK = new Color(0x2a / 255f, 0x1a / 255f, 0x10 / 255f, 1f);
    /** The arms beside the selected button (#415): frame {@link KimeriaArms#LIT} at scale 1; the game's one shared instance. */
    private final KimeriaArms arms;
    private final Matrix4 textProjection;
    private final float logicalHeight;
    /** Text draws queued in one frame without the arrays growing: a title, a body, eight buttons, the hint and the counter. */
    private static final int TEXT_QUEUE_CAPACITY = 16;
    /** The queued text draws, as parallel arrays: the font, its layout and the Y-up position (x, y pairs). */
    private final Array<BitmapFont> textFonts = new Array<>(TEXT_QUEUE_CAPACITY);
    private final Array<GlyphLayout> textLayouts = new Array<>(TEXT_QUEUE_CAPACITY);
    private final FloatArray textPositions = new FloatArray(2 * TEXT_QUEUE_CAPACITY);
    /** Kept one-line layouts by string, per font; white, as {@code BitmapFont.draw} takes the colour baked into a layout. */
    private final ObjectMap<String, GlyphLayout> titleLayouts = new ObjectMap<>(), buttonLayouts = new ObjectMap<>();
    /** Kept wrapped, centred layouts by string; dropped when the text tier changes the two fonts. */
    private final ObjectMap<String, Wrapped> bodyLayouts = new ObjectMap<>(), hintLayouts = new ObjectMap<>();
    /** The one layout of {@link #topRight}, laid out again only when its string or the font changes. */
    private final GlyphLayout topRightLayout = new GlyphLayout();
    private String topRightText;
    /**
     * Text blocks queued in one frame without the array growing. The dialogue screen queues 8 at most (two names, the line,
     * the checklist, two choices, two markers); 16 leaves room for #341's line and #338's fairy without an allocation (D78).
     */
    private static final int BLOCK_QUEUE_CAPACITY = 16;
    /** Prebuilt text blocks ({@link #bodyBlock}) drawn after the queued text; queuing one allocates nothing. */
    private final Array<BitmapFontCache> blockQueue = new Array<>(false, BLOCK_QUEUE_CAPACITY);
    /** Per queued block, how many glyphs to draw ({@link #ALL_GLYPHS} for all); a typewriter reveal draws a prefix. */
    private final IntArray blockGlyphs = new IntArray(false, BLOCK_QUEUE_CAPACITY);
    private static final int ALL_GLYPHS = -1;
    private final Matrix4 savedProjection = new Matrix4();
    private final Vector2 lastMouse = new Vector2();
    private boolean hasLastMouse;

    /**
     * @param arms the game's shared {@link KimeriaArms} (cut once at load), drawn beside the selected menu button. A test
     *             that never draws a button list may pass null.
     */
    public Overlay(Assets assets, float logicalWidth, float logicalHeight, KimeriaArms arms) {
        this.assets = assets;
        titleFont = assets.font(FontSizes.TITLE);
        buttonFont = assets.font(FontSizes.BUTTON);
        storyFont = assets.font(FontSizes.STORY);
        syncFonts();
        this.arms = arms;
        this.logicalHeight = logicalHeight;

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight); // standard Y-up
        textCamera.update();
        textProjection = textCamera.combined.cpy();
    }

    /** Takes the body and label fonts of the current text tier (D137); an int compare unless the tier changed, so it is called freely. */
    private void syncFonts() {
        int generation = assets.textGeneration();
        if (generation == fontsGeneration) return;
        fontsGeneration = generation;
        boolean large = assets.largeText();
        bodyFont = assets.font(FontSizes.body(large));
        hintFont = assets.font(FontSizes.label(large));
        bodyLayouts.clear();
        hintLayouts.clear();
        topRightText = null;
    }

    /** A wrapped layout with what it was laid out for, so a caller asking for another width or colour gets a new one. */
    private static final class Wrapped {
        final GlyphLayout layout;
        final float width;
        final int color;

        Wrapped(BitmapFont font, String text, Color color, float width) {
            layout = new GlyphLayout(font, text, color, width, Align.center, true);
            this.width = width;
            this.color = color.toIntBits();
        }
    }

    private static GlyphLayout wrapped(ObjectMap<String, Wrapped> kept, BitmapFont font, String text, Color color, float width) {
        Wrapped entry = kept.get(text);
        if (entry == null || entry.width != width || entry.color != color.toIntBits()) {
            entry = new Wrapped(font, text, color, width);
            kept.put(text, entry);
        }
        return entry.layout;
    }

    private static GlyphLayout line(ObjectMap<String, GlyphLayout> kept, BitmapFont font, String text) {
        GlyphLayout layout = kept.get(text);
        if (layout == null) {
            layout = new GlyphLayout(font, text, Color.WHITE, 0f, Align.left, false);
            kept.put(text, layout);
        }
        return layout;
    }

    /** Converts "distance from the top" (this class's convention) to the Y-up text camera's y. */
    private float toTextY(float fromTop) {
        return logicalHeight - fromTop;
    }

    /** {@link #toTextY} for views that build their own {@link BitmapFontCache} blocks (the dialogue screen). */
    public float textY(float fromTop) {
        return toTextY(fromTop);
    }

    /** Call once before any other method each frame the overlay is drawn. */
    public void beginFrame() {
        syncFonts();
        clearText();
        blockQueue.clear();
        blockGlyphs.clear();
    }

    /** Call once, after every other call this frame, to actually draw the queued text. */
    public void flushText(Batch batch) {
        if (textLayouts.size == 0 && blockQueue.size == 0) return;
        savedProjection.set(batch.getProjectionMatrix());
        batch.setProjectionMatrix(textProjection);
        for (int i = 0; i < textLayouts.size; i++) {
            textFonts.get(i).draw(batch, textLayouts.get(i), textPositions.get(2 * i), textPositions.get(2 * i + 1));
        }
        for (int i = 0; i < blockQueue.size; i++) {
            int glyphs = blockGlyphs.get(i);
            if (glyphs == ALL_GLYPHS) blockQueue.get(i).draw(batch);
            else blockQueue.get(i).draw(batch, 0, glyphs);
        }
        batch.setProjectionMatrix(savedProjection);
        clearText();
        blockQueue.clear();
        blockGlyphs.clear();
    }

    private void clearText() {
        textFonts.clear();
        textLayouts.clear();
        textPositions.clear();
    }

    /** Queues one text draw with its left edge at {@code x} and its first line's top {@code fromTop} px below the screen's. */
    private void queueText(BitmapFont font, GlyphLayout layout, float x, float fromTop) {
        textFonts.add(font);
        textLayouts.add(layout);
        textPositions.add(x, toTextY(fromTop));
    }

    public void panel(Batch batch, float x, float y, float w, float h, float alpha) {
        batch.setColor(0f, 0f, 0f, alpha);
        batch.draw(Shaders.whiteQuad(), x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    public void title(String text, float centerX, float yFromTop) {
        GlyphLayout layout = line(titleLayouts, titleFont, text);
        queueText(titleFont, layout, centerX - layout.width / 2f, yFromTop);
    }

    public void body(String text, float centerX, float yFromTop, float wrapWidth) {
        body(text, centerX, yFromTop, wrapWidth, Color.WHITE);
    }

    public void body(String text, float centerX, float yFromTop, float wrapWidth, Color color) {
        syncFonts();
        queueText(bodyFont, wrapped(bodyLayouts, bodyFont, text, color, wrapWidth), centerX - wrapWidth / 2f, yFromTop);
    }

    /**
     * Lays {@code text} out once (centred, wrapped, body font) into a cache whose vertices stay fixed, so a screen that
     * shows the same paragraph for many frames (the paged intro) pays for the layout at load, not per frame.
     * {@code yFromTop} is the top of the first line, as for {@link #body}. Draw it with {@link #queueBlock}.
     */
    public BitmapFontCache bodyBlock(String text, float centerX, float yFromTop, float wrapWidth) {
        syncFonts();
        GlyphLayout layout = new GlyphLayout(bodyFont, text, Color.WHITE, wrapWidth, Align.center, true);
        BitmapFontCache block = new BitmapFontCache(bodyFont, bodyFont.usesIntegerPositions());
        block.setText(layout, centerX - wrapWidth / 2f, toTextY(yFromTop));
        return block;
    }

    /**
     * Lays {@code text} out once in the body font, wrapped at {@code wrapWidth} and aligned {@code align} ({@link Align}) in
     * {@code color}, into a cache for {@link #queueBlock(BitmapFontCache, float)}. {@code leftX} is the left edge of the
     * wrap box and {@code yFromTop} the top of the first line (the Controls table, D135).
     */
    public BitmapFontCache textBlock(String text, float leftX, float yFromTop, float wrapWidth, int align, Color color) {
        syncFonts();
        GlyphLayout layout = new GlyphLayout(bodyFont, text, color, wrapWidth, align, true);
        BitmapFontCache block = new BitmapFontCache(bodyFont, bodyFont.usesIntegerPositions());
        block.setText(layout, leftX, toTextY(yFromTop));
        return block;
    }

    /** Number of lines {@code text} wraps to at {@code wrapWidth} in the body font, for layout math. */
    public int bodyLines(String text, float wrapWidth) {
        syncFonts();
        return new GlyphLayout(bodyFont, text, Color.WHITE, wrapWidth, Align.left, true).runs.size;
    }

    /** Queues a {@link #bodyBlock} at {@code alpha}; allocation free, nothing is drawn for an alpha of zero. */
    public void queueBlock(BitmapFontCache block, float alpha) {
        if (alpha <= 0f) return;
        block.setAlphas(alpha);
        blockQueue.add(block);
        blockGlyphs.add(ALL_GLYPHS);
    }

    /**
     * Queues the first {@code glyphs} glyphs of a prebuilt block as they are (colours untouched); allocation free. The dialogue
     * screen builds its blocks when a line, a name or a label changes and reveals a line through the glyph count.
     */
    public void queueBlock(BitmapFontCache block, int glyphs) {
        if (glyphs <= 0) return;
        blockQueue.add(block);
        blockGlyphs.add(glyphs);
    }

    /**
     * Lays {@code text} out once in the small label font, grey and right-aligned at {@code rightX}, into a cache for
     * {@link #queueBlock(BitmapFontCache, float)} (the intro's skip hint, D118). {@code yFromTop} is the top of the line.
     */
    public BitmapFontCache hintBlock(String text, float rightX, float yFromTop) {
        syncFonts();
        GlyphLayout layout = new GlyphLayout(hintFont, text, HINT_GREY, 0f, Align.right, false);
        BitmapFontCache block = new BitmapFontCache(hintFont, hintFont.usesIntegerPositions());
        block.setText(layout, rightX, toTextY(yFromTop));
        return block;
    }

    /**
     * As {@link #bodyBlock} for the intro book (D133): the story font, dark ink and left-aligned lines, the column's left edge
     * at {@code leftX}. Draw it with {@link #queueBlock(BitmapFontCache, float)}.
     */
    public BitmapFontCache storyBlock(String text, float leftX, float yFromTop, float wrapWidth) {
        GlyphLayout layout = new GlyphLayout(storyFont, text, STORY_INK, wrapWidth, Align.left, true);
        BitmapFontCache block = new BitmapFontCache(storyFont, storyFont.usesIntegerPositions());
        block.setText(layout, leftX, toTextY(yFromTop));
        return block;
    }

    /** Height (px) that {@code text} will wrap to at {@code wrapWidth} in the story font, for the book's layout math. */
    public float storyHeight(String text, float wrapWidth) {
        return new GlyphLayout(storyFont, text, Color.WHITE, wrapWidth, Align.left, true).height;
    }

    /** Height (px) that {@code text} will wrap to at {@code wrapWidth} in the body font, for layout math. */
    public float bodyHeight(String text, float wrapWidth) {
        syncFonts();
        return wrapped(bodyLayouts, bodyFont, text, Color.WHITE, wrapWidth).height;
    }

    public void hint(String text, float centerX, float yFromTop) {
        syncFonts();
        float width = centerX * 2f - HINT_SIDE_ROOM;
        GlyphLayout layout = wrapped(hintLayouts, hintFont, text, Color.WHITE, width);
        queueText(hintFont, layout, centerX - width / 2f, hintTop(yFromTop, layout.height, logicalHeight));
    }

    /** Where the hint's first line starts: {@code requestedTop}, but never so low that the bottom of its text is nearer than {@link #HINT_BOTTOM_MARGIN} to the bottom edge. */
    public static float hintTop(float requestedTop, float textHeight, float screenHeight) {
        return Math.min(requestedTop, screenHeight - HINT_BOTTOM_MARGIN - textHeight);
    }

    /**
     * Right-aligned so a growing/shrinking string (a live FPS counter) doesn't drift across the screen. One string a frame:
     * all calls share one layout.
     */
    public void topRight(String text, float rightX, float yFromTop) {
        syncFonts();
        if (!text.equals(topRightText)) {
            topRightText = text;
            topRightLayout.setText(hintFont, text, Color.WHITE, 0f, Align.left, false);
        }
        queueText(hintFont, topRightLayout, rightX - topRightLayout.width, yFromTop);
    }

    private void buttonLabel(String label, float x, float y, float width, float height) {
        GlyphLayout layout = line(buttonLayouts, buttonFont, label);
        queueText(buttonFont, layout, x + (width - layout.width) / 2f, y + (height - layout.height) / 2f);
    }

    /** Left edge of the arms left of a selected button whose left edge is {@code buttonX} (#415). */
    public static float selectionArmsLeftX(float buttonX) {
        return buttonX - SELECTED_FRAME - SELECTION_ARMS_GAP - SELECTION_ARMS_W;
    }

    /** Left edge of the arms right of a selected button {@code buttonW} wide (#415). */
    public static float selectionArmsRightX(float buttonX, float buttonW) {
        return buttonX + buttonW + SELECTED_FRAME + SELECTION_ARMS_GAP;
    }

    /** Top edge (Y-down) of both arms beside a selected button of the default height whose top edge is {@code buttonY}. */
    public static float selectionArmsTop(float buttonY) {
        return selectionArmsTop(buttonY, BUTTON_H);
    }

    /** As above for a row {@code buttonH} tall: the arms are centred on it. */
    public static float selectionArmsTop(float buttonY, float buttonH) {
        return buttonY + (buttonH - SELECTION_ARMS_H) / 2f;
    }

    /**
     * The arms on both sides of the selected button at ({@code buttonX}, {@code buttonY}), {@code buttonW} wide (#415).
     */
    public void drawSelectionArms(Batch batch, float buttonX, float buttonY, float buttonW) {
        drawSelectionArms(batch, buttonX, buttonY, buttonW, BUTTON_H);
    }

    /** As above beside a row {@code buttonH} tall. */
    public void drawSelectionArms(Batch batch, float buttonX, float buttonY, float buttonW, float buttonH) {
        float top = selectionArmsTop(buttonY, buttonH), half = SELECTION_ARMS_W / 2f;
        arms.draw(batch, KimeriaArms.LIT, selectionArmsLeftX(buttonX) + half, top, 1);
        arms.draw(batch, KimeriaArms.LIT, selectionArmsRightX(buttonX, buttonW) + half, top, 1);
    }

    /** Height a button list for {@code menu} will occupy, for callers laying out a screen around it. */
    public static float buttonsHeight(Menu menu) {
        int n = menu.visibleCount();
        return n * menu.buttonHeight + Math.max(0, n - 1) * menu.buttonGap;
    }

    /**
     * Leaves 24 px of padding each side of the widest label; drawing and mouse hit areas use the same width. Capped at
     * {@link #MAX_BUTTONS_W} so the selection arms beside the button stay on screen (#415).
     */
    public float buttonsWidth(Menu menu) {
        float width = menu.buttonWidth;
        for (int i = 0; i < menu.items.size(); i++) {
            MenuItem item = menu.items.get(i);
            if (item.visible.getAsBoolean()) width = Math.max(width, line(buttonLayouts, buttonFont, item.label.get()).width + LABEL_PADDING);
        }
        return Math.min(MAX_BUTTONS_W, width);
    }

    /**
     * Draws {@code menu}'s button list starting at {@code startY} (top edge
     * of the first button), handles mouse hover/click and keyboard/gamepad
     * confirm/back, and fires whichever action was activated this frame.
     * Labels are queued -- call {@link #flushText} once, after the whole
     * screen (title/body/buttons/hint) has been laid out.
     *
     * @param mouseLogical current mouse position in this overlay's logical
     *                     (640x360, Y-down) space, or null if unavailable
     * @param mouseClicked true the frame the mouse was pressed
     * @param onHover a quiet blip played when the mouse moves onto a
     *                different button -- keyboard/gamepad nav plays its own
     *                copy in {@code KimeriaGame} right after moving the
     *                selection, before this method is even called
     * @param onActivate the louder press sound, played the frame any button
     *                    (or Back) actually fires
     */
    public void buttons(Batch batch, Menu menu, float centerX, float startY,
                         Vector2 mouseLogical, boolean mouseClicked, boolean confirmPressed, boolean backPressed,
                         Runnable onHover, Runnable onActivate) {
        int n = menu.items.size();
        menu.ensureVisibleSelection();
        float buttonW = buttonsWidth(menu), buttonH = menu.buttonHeight;
        float bx = centerX - buttonW / 2f;

        // Only a real mouse move may pull the selection onto the button under the cursor. A cursor
        // that merely rests over a button must not undo keyboard/gamepad navigation every frame.
        boolean mouseMoved = mouseLogical != null
            && (!hasLastMouse || lastMouse.x != mouseLogical.x || lastMouse.y != mouseLogical.y);
        hasLastMouse = mouseLogical != null;
        if (hasLastMouse) lastMouse.set(mouseLogical);

        int row = 0;
        for (int i = 0; i < n; i++) {
            if (!menu.items.get(i).visible.getAsBoolean()) continue;
            float by = startY + row++ * (buttonH + menu.buttonGap);
            boolean hovered = mouseLogical != null
                && mouseLogical.x >= bx && mouseLogical.x <= bx + buttonW
                && mouseLogical.y >= by && mouseLogical.y <= by + buttonH;
            if (hovered && mouseMoved && menu.selected != i) {
                menu.selected = i;
                onHover.run();
            }
            if (hovered && mouseClicked) menu.selected = i;
            boolean selected = menu.selected == i;

            // Three distinct looks: idle (dark, quiet), hovered (lighter fill), selected (gold
            // frame and the Kimeria arms outside it, #415). Hovered + selected stack. Every label is white.
            if (selected) {
                batch.setColor(SELECTED_BORDER);
                batch.draw(Shaders.whiteQuad(), bx - SELECTED_FRAME, by - SELECTED_FRAME, buttonW + 2 * SELECTED_FRAME, buttonH + 2 * SELECTED_FRAME);
            } else {
                batch.setColor(IDLE_BORDER);
                batch.draw(Shaders.whiteQuad(), bx - IDLE_FRAME, by - IDLE_FRAME, buttonW + 2 * IDLE_FRAME, buttonH + 2 * IDLE_FRAME);
            }
            batch.setColor(hovered ? HOVER_FILL : IDLE_FILL);
            batch.draw(Shaders.whiteQuad(), bx, by, buttonW, buttonH);
            batch.setColor(Color.WHITE);
            if (selected) drawSelectionArms(batch, bx, by, buttonW, buttonH);

            buttonLabel(menu.items.get(i).label.get(), bx, by, buttonW, buttonH);

            if (hovered && mouseClicked) {
                onActivate.run();
                menu.confirmSelected();
                return; // An action may change the menu or its visibility; never activate twice.
            }
        }

        if (confirmPressed) {
            onActivate.run();
            menu.confirmSelected();
        }
        if (backPressed) {
            onActivate.run();
            menu.back();
        }
    }
}
