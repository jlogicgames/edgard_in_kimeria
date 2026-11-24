package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;

/**
 * The open book the intro is read on (#536, D133): one authored image of the book on black (art/ui/intro-book, exported by
 * {@code tools/intro_book_sprites.py}) and the two leaves, the right one lifting over the spine and landing on the left as the
 * page turns. Everything is cut once at load, so a frame only draws cached regions (Y-down projection, nearest filter).
 */
public final class IntroBook {
    public static final String BOOK_ART = "images/ui/intro-book.png", LEAVES_ART = "images/ui/intro-leaves.png";
    public static final int SCREEN_WIDTH = 640, SCREEN_HEIGHT = 360;
    /** A leaf: its top-left corner (the left leaf's x, the spine at {@link #SPINE_X}) and its size. */
    /** The cover's bottom edge: the page-turn cue and the skip hint sit below it, on the black. */
    public static final int COVER_BOTTOM = 326;
    public static final int LEAF_WIDTH = 290, LEAF_HEIGHT = 298, LEAF_TOP = 20, SPINE_X = 320;
    /** The text columns: left edge on each leaf and the wrap width (the 20 px story font, {@link FontSizes#STORY}). */
    public static final float COLUMN_LEFT = 56f, COLUMN_RIGHT = 348f, COLUMN_WIDTH = 236f;
    /** Blank between paragraphs: one line of the story font. */
    public static final float PARAGRAPH_GAP = FontSizes.STORY;
    /** The turning leaf darkens to this much of full brightness at the middle of its flight. */
    private static final float FLIGHT_SHADE = 0.8f;
    /** The leaf is edge-on halfway through its flight: it narrows over the first half and widens over the second. */
    private static final float EDGE_ON = 0.5f;

    private final TextureRegion book, leftLeaf, rightLeaf;

    public IntroBook(Assets assets) {
        book = assets.region(BOOK_ART, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        leftLeaf = assets.region(LEAVES_ART, 0, 0, LEAF_WIDTH, LEAF_HEIGHT);
        rightLeaf = assets.region(LEAVES_ART, LEAF_WIDTH, 0, LEAF_WIDTH, LEAF_HEIGHT);
    }

    /** The left edge of the text column of {@code leaf} (0 is the first leaf of the book, a left one). */
    public static float columnLeft(int leaf) { return (leaf & 1) == 0 ? COLUMN_LEFT : COLUMN_RIGHT; }

    /** True for the leaf on the right of its spread. */
    public static boolean isRight(int leaf) { return (leaf & 1) == 1; }

    /**
     * Draws the book, with the leaf in flight when {@code flight} is in (0, 1): the right leaf narrows to the spine over the
     * first half, the left one widens from it over the second. Allocation free.
     */
    public void draw(Batch batch, float flight) {
        batch.draw(book, 0f, 0f, SCREEN_WIDTH, SCREEN_HEIGHT);
        if (flight <= 0f || flight >= 1f) return;
        boolean lifting = flight < EDGE_ON;
        float across = lifting ? 1f - 2f * flight : 2f * flight - 1f; // 1 flat, 0 edge-on
        float width = Math.round(LEAF_WIDTH * across);
        if (width < 1f) return;
        float shade = 1f - (1f - FLIGHT_SHADE) * (1f - across);
        batch.setColor(shade, shade, shade, 1f);
        batch.draw(lifting ? rightLeaf : leftLeaf, lifting ? SPINE_X : SPINE_X - width, LEAF_TOP, width, LEAF_HEIGHT);
        batch.setColor(Color.WHITE);
    }
}
