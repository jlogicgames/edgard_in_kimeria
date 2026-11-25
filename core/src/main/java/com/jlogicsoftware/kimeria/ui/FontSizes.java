package com.jlogicsoftware.kimeria.ui;

/**
 * D70 (#335), D137 (#491): every baked font size the views use, in one place. Each value must be in {@code FontCharacters.SIZES}
 * (checked by {@code FontSizesTest}); change both together and run {@code ./gradlew lwjgl3:bakeFonts}.
 *
 * <p>Two tiers (D137). The nominal sizes below are what the layouts were drawn for at one CSS pixel per picture unit or more.
 * When the picture is shown smaller than that ({@link #large}: a phone, a small window) the three small roles, the label,
 * the body and the dialogue, take the larger baked size of the same role ({@link #label}, {@link #body}, {@link #dialogue}); the
 * other roles already are as large as their layouts allow. The tier is chosen from the picture's scale alone, never from the
 * device (a phone cannot be told from a tablet or a touch laptop).
 */
public final class FontSizes {
    /** Labels and hints: hero label, FPS counter, menu hint, boss label. */
    public static final int LABEL = 16;
    /** Paragraph text: About, rules, menu body, story pages. */
    public static final int BODY = 16;
    /** Dialogue lines, choice buttons, checklist, speaker names (the box fits four lines of it). */
    public static final int DIALOGUE = 18;
    /**
     * The large tier of {@link #LABEL}: the hero label and the menu hint still fit their boxes in five languages. 20 would put the
     * label's last line 3 px nearer a hero hanging under a zone message (the ladder lesson's measured bound of 8 px became 8.4);
     * 18 is 1 px nearer and stays inside it.
     */
    public static final int LABEL_LARGE = 18;
    /** The large tier of {@link #BODY}: 18 is the most the About page (134 px of 140) and the Controls table hold. */
    public static final int BODY_LARGE = 18;
    /** The large tier of {@link #DIALOGUE}: four lines of it fill the text box, which grows to {@link HealerDialogueView#BOX_H_LARGE}. */
    public static final int DIALOGUE_LARGE = 20;
    /**
     * The portrait strip's "rotate the phone" prompt (D140, #493), the one text left in the touch controls (D138, #492: the buttons show pictograms).
     * It is the title's baked size and is drawn in the strip's own units, 360 per window width: 1.08 CSS px per unit on a 390 px phone, so 32.5 CSS px
     * there, and 30 at the narrowest window the strip exists in, far over {@link #MIN_CSS_PX}. Two lines in all five languages.
     */
    public static final int ROTATE_PROMPT = 30;
    /** Map-authored zone message (D50), menu buttons, coin count. */
    public static final int ZONE = 20, BUTTON = 20, COUNT = 20;
    public static final int TITLE = 30;
    /**
     * The story on the book's leaves (D133, #615): the title's size, an already baked one, so no rebake. Its strokes are two pixels
     * wide, which the parchment shows; the 20 px face (one pixel strokes) faded into it. Twelve leaves carry the tale at this size.
     */
    public static final int STORY = 30;

    /**
     * D137: the picture is drawn below this many CSS pixels per picture unit, and the large tier is used. 1 is the scale the layouts
     * were authored at (a 640x360 window); below it nominal text is under its designed size on the screen.
     */
    public static final float LARGE_BELOW_CSS_PER_UNIT = 1f;
    /**
     * D137: the minimum on-screen text size, in CSS pixels (the baked size times the picture's CSS pixels per unit), for all text but
     * the rotate prompt (which is measured by its own strip scale, {@code TextMinimumSizeTest}), at every window whose picture is at least {@link #MIN_RULE_FROM_CSS_PER_UNIT} wide per unit.
     * 11 is the smallest text size of iOS (HIG) and of Material 3 (Label Small, 11 sp); the About page and the Controls table cap the
     * body at 18, which on a 390 px portrait phone is 10.97.
     */
    public static final float MIN_CSS_PX = 11f;
    /** D137: the rule is tested down to this picture scale, a 384 px wide portrait window (the 390 px phone is 0.609). */
    public static final float MIN_RULE_FROM_CSS_PER_UNIT = 0.6f;

    private FontSizes() { }

    /** Whether the picture, drawn at {@code cssPerUnit} CSS pixels per picture unit, is small enough for the large tier. */
    public static boolean large(float cssPerUnit) { return cssPerUnit < LARGE_BELOW_CSS_PER_UNIT; }

    public static int label(boolean large) { return large ? LABEL_LARGE : LABEL; }

    public static int body(boolean large) { return large ? BODY_LARGE : BODY; }

    public static int dialogue(boolean large) { return large ? DIALOGUE_LARGE : DIALOGUE; }

    /** The size of {@code baked}-px text on the screen, in CSS pixels, when the picture is drawn at {@code cssPerUnit}. */
    public static float onScreen(int baked, float cssPerUnit) { return baked * cssPerUnit; }

    /** Whether {@code baked}-px text meets {@link #MIN_CSS_PX} at {@code cssPerUnit}, to the nearest whole CSS pixel (a browser's own minimum font size is whole pixels too). */
    public static boolean meetsMinimum(int baked, float cssPerUnit) {
        return Math.round(onScreen(baked, cssPerUnit)) >= MIN_CSS_PX;
    }
}
