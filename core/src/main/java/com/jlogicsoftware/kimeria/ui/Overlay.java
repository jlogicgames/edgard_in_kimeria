package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode UI for the menu/pause/game-over screens (Dart drew these as
 * Flutter widgets via Flame's overlay system, Rust as a {@code bevy_ui} node
 * tree; here they're plain draws + hit-tests, to avoid a Scene2D skin for a
 * handful of simple screens).
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

    private static final Color IDLE_BORDER = new Color(0.45f, 0.45f, 0.52f, 1f);
    private static final Color IDLE_FILL = new Color(0.10f, 0.10f, 0.14f, 0.92f);
    private static final Color HOVER_FILL = new Color(0.27f, 0.27f, 0.36f, 0.96f);
    private static final Color SELECTED_BORDER = new Color(1f, 0.82f, 0.3f, 1f);
    private static final Color IDLE_TEXT = new Color(0.78f, 0.78f, 0.82f, 1f);
    private static final Color HOVER_TEXT = Color.WHITE;
    private static final Color SELECTED_TEXT = new Color(1f, 0.82f, 0.3f, 1f);

    private final BitmapFont titleFont;
    private final BitmapFont bodyFont;
    private final BitmapFont buttonFont;
    private final BitmapFont hintFont;
    private final Matrix4 textProjection;
    private final float logicalHeight;
    private final List<Runnable> textQueue = new ArrayList<>();
    private Vector2 lastMouse;
    private final float markerW;

    public Overlay(Assets assets, float logicalWidth, float logicalHeight) {
        titleFont = assets.font(30);
        bodyFont = assets.font(15);
        buttonFont = assets.font(20);
        hintFont = assets.font(13);
        markerW = new GlyphLayout(buttonFont, "<").width;
        this.logicalHeight = logicalHeight;

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight); // standard Y-up
        textCamera.update();
        textProjection = textCamera.combined.cpy();
    }

    /** Converts "distance from the top" (this class's convention) to the Y-up text camera's y. */
    private float toTextY(float fromTop) {
        return logicalHeight - fromTop;
    }

    /** Call once before any other method each frame the overlay is drawn. */
    public void beginFrame() {
        textQueue.clear();
    }

    /** Call once, after every other call this frame, to actually draw the queued text. */
    public void flushText(SpriteBatch batch) {
        if (textQueue.isEmpty()) return;
        Matrix4 previous = batch.getProjectionMatrix().cpy();
        batch.setProjectionMatrix(textProjection);
        for (Runnable draw : textQueue) draw.run();
        batch.setProjectionMatrix(previous);
        textQueue.clear();
    }

    public void panel(SpriteBatch batch, float x, float y, float w, float h, float alpha) {
        batch.setColor(0f, 0f, 0f, alpha);
        batch.draw(Shaders.whiteQuad(), x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    public void title(SpriteBatch batch, String text, float centerX, float yFromTop) {
        GlyphLayout layout = new GlyphLayout(titleFont, text);
        textQueue.add(() -> titleFont.draw(batch, layout, centerX - layout.width / 2f, toTextY(yFromTop)));
    }

    public void body(SpriteBatch batch, String text, float centerX, float yFromTop, float wrapWidth) {
        textQueue.add(() ->
            bodyFont.draw(batch, text, centerX - wrapWidth / 2f, toTextY(yFromTop), wrapWidth, Align.center, true));
    }

    /** Height (px) that {@code text} will wrap to at {@code wrapWidth} in the body font, for layout math. */
    public float bodyHeight(String text, float wrapWidth) {
        GlyphLayout layout = new GlyphLayout(bodyFont, text, Color.WHITE, wrapWidth, Align.center, true);
        return layout.height;
    }

    public void hint(SpriteBatch batch, String text, float centerX, float yFromTop) {
        GlyphLayout layout = new GlyphLayout(hintFont, text);
        textQueue.add(() -> {
            hintFont.setColor(0.8f, 0.8f, 0.8f, 1f);
            hintFont.draw(batch, layout, centerX - layout.width / 2f, toTextY(yFromTop));
            hintFont.setColor(Color.WHITE);
        });
    }

    /** Right-aligned so a growing/shrinking string (a live FPS counter) doesn't drift across the screen. */
    public void topRight(SpriteBatch batch, String text, float rightX, float yFromTop) {
        GlyphLayout layout = new GlyphLayout(hintFont, text);
        textQueue.add(() -> {
            hintFont.setColor(0.8f, 0.8f, 0.8f, 1f);
            hintFont.draw(batch, layout, rightX - layout.width, toTextY(yFromTop));
            hintFont.setColor(Color.WHITE);
        });
    }

    /** Height a button list for {@code menu} will occupy, for callers laying out a screen around it. */
    public static float buttonsHeight(Menu menu) {
        int n = menu.items.size();
        return n * BUTTON_H + (n - 1) * BUTTON_GAP;
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
    public void buttons(SpriteBatch batch, Menu menu, float centerX, float startY,
                         Vector2 mouseLogical, boolean mouseClicked, boolean confirmPressed, boolean backPressed,
                         Runnable onHover, Runnable onActivate) {
        int n = menu.items.size();
        float bx = centerX - BUTTON_W / 2f;

        // Only a real mouse move may pull the selection onto the button under the cursor. A cursor
        // that merely rests over a button must not undo keyboard/gamepad navigation every frame.
        boolean mouseMoved = mouseLogical != null
            && (lastMouse == null || lastMouse.x != mouseLogical.x || lastMouse.y != mouseLogical.y);
        lastMouse = mouseLogical == null ? null : new Vector2(mouseLogical);

        for (int i = 0; i < n; i++) {
            float by = startY + i * (BUTTON_H + BUTTON_GAP);
            boolean hovered = mouseLogical != null
                && mouseLogical.x >= bx && mouseLogical.x <= bx + BUTTON_W
                && mouseLogical.y >= by && mouseLogical.y <= by + BUTTON_H;
            if (hovered && mouseMoved && menu.selected != i) {
                menu.selected = i;
                onHover.run();
            }
            if (hovered && mouseClicked) menu.selected = i;
            boolean selected = menu.selected == i;

            // Three distinct looks: idle (dark, quiet), hovered (lighter fill), selected (gold
            // frame, gold label and > < markers). Hovered + selected stack.
            if (selected) {
                batch.setColor(SELECTED_BORDER);
                batch.draw(Shaders.whiteQuad(), bx - 3, by - 3, BUTTON_W + 6, BUTTON_H + 6);
            } else {
                batch.setColor(IDLE_BORDER);
                batch.draw(Shaders.whiteQuad(), bx - 1, by - 1, BUTTON_W + 2, BUTTON_H + 2);
            }
            batch.setColor(hovered ? HOVER_FILL : IDLE_FILL);
            batch.draw(Shaders.whiteQuad(), bx, by, BUTTON_W, BUTTON_H);
            batch.setColor(Color.WHITE);

            String label = menu.items.get(i).label.get();
            GlyphLayout layout = new GlyphLayout(buttonFont, label);
            float labelX = bx + (BUTTON_W - layout.width) / 2f;
            float labelYFromTop = by + (BUTTON_H - layout.height) / 2f;
            Color textColor = selected ? SELECTED_TEXT : hovered ? HOVER_TEXT : IDLE_TEXT;
            textQueue.add(() -> {
                buttonFont.setColor(textColor);
                buttonFont.draw(batch, layout, labelX, toTextY(labelYFromTop));
                if (selected) {
                    buttonFont.draw(batch, ">", bx + 10f, toTextY(labelYFromTop));
                    buttonFont.draw(batch, "<", bx + BUTTON_W - 10f - markerW, toTextY(labelYFromTop));
                }
                buttonFont.setColor(Color.WHITE);
            });

            if (hovered && mouseClicked) {
                onActivate.run();
                menu.confirmSelected();
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
