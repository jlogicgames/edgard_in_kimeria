package com.jlogicsoftware.kimeria.ui;

/**
 * D70 (#335): every baked font size the views use, in one place. Each value must be in {@code FontCharacters.SIZES}
 * (checked by {@code FontSizesTest}); change both together and run {@code ./gradlew lwjgl3:bakeFonts}.
 */
public final class FontSizes {
    /** Labels and hints: hero label, FPS counter, menu hint, boss label. */
    public static final int LABEL = 16;
    /** Paragraph text: About, rules, menu body, story pages. */
    public static final int BODY = 16;
    /** Dialogue lines, choice buttons, checklist, speaker names (the box fits four lines of it). */
    public static final int DIALOGUE = 18;
    /** Touch button labels stay small: the words must fit the 50 px button face (D55); 15 and 16 overflow in de/be/uk. */
    public static final int TOUCH_LABEL = 13;
    /** Map-authored zone message (D50), menu buttons, coin count. */
    public static final int ZONE = 20, BUTTON = 20, COUNT = 20;
    public static final int TITLE = 30;

    private FontSizes() { }
}
