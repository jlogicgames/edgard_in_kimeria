package com.jlogicsoftware.kimeria.ui;

/**
 * The portrait "rotate the phone" prompt (#493, D140): where it sits in the controls strip and how its pictogram turns. Pure arithmetic in the
 * strip's own units (360 wide, Y down from the picture's bottom edge), so it is tested without a window.
 *
 * <p>The prompt fills the strip from the top down to the highest thing the controls cluster can ever show ({@link #CLUSTER_REACH} above the
 * cluster centre: the stick's grab circle, the Ranged button's tutorial ring, the up-push chevrons), minus {@link #GAP_TO_CONTROLS}. In that
 * band sit the pictogram ({@link #ICON_MAX} units, shrinking to {@link #ICON_MIN} before it is dropped), {@link #ICON_TEXT_GAP}, and the text,
 * the whole block centred. The band depends on the strip's height alone, so the prompt does not move between a menu and play.
 *
 * <p>A strip too short for the band to hold the text (a portrait window about 360x480 or less) draws no prompt while the controls are drawn
 * (they win); with no controls (a menu, the story, a dialogue) the text alone is drawn at the top of the strip.
 */
public final class RotatePrompt {
    /** The strip's margins (units): above the prompt, and between the prompt and the highest control. */
    static final float TOP_MARGIN = 12f, GAP_TO_CONTROLS = 10f;
    /** How far above the cluster's centre anything of the controls reaches: the Ranged ring (76.8) and the stick's grab circle (76), rounded up. */
    static final float CLUSTER_REACH = 78f;
    /** The pictogram's drawn size (units; its art is 64 px) when there is room, and the smallest it is drawn at. */
    static final float ICON_MAX = 72f, ICON_MIN = 40f;
    static final float ICON_TEXT_GAP = 6f;
    /** Margin left and right of the text. */
    static final float SIDE_MARGIN = 16f;
    /** Text width, in units. */
    public static final float TEXT_WIDTH = TouchLayout.SPACE_WIDTH - SIDE_MARGIN * 2f;

    /** One loop of the pictogram, in seconds: the phone holds upright, turns to landscape, holds, and turns back. */
    public static final float CYCLE = 2f;
    static final float HOLD = 0.25f, TURN = 0.75f;
    /** The phone turns clockwise on the screen by this much (the way the amber arrow bends). */
    static final float TURN_DEGREES = 90f;

    /** Smoothstep 3u^2 - 2u^3: the turn eases in and out. */
    private static final float SMOOTHSTEP_CUBIC = 3f, SMOOTHSTEP_QUADRATIC = 2f;

    boolean shown, icon;
    float iconSize, iconY, textTop;

    /**
     * Places the prompt.
     *
     * @param spaceHeight the strip's height in units
     * @param clusterY    centre Y of the controls cluster ({@link TouchControls#portraitClusterY})
     * @param controls    whether the controls are drawn too
     * @param textHeight  height of the laid-out text
     */
    void fit(float spaceHeight, float clusterY, boolean controls, float textHeight) {
        float bottom = clusterY - CLUSTER_REACH - GAP_TO_CONTROLS, band = bottom - TOP_MARGIN;
        iconSize = Math.min(ICON_MAX, band - textHeight - ICON_TEXT_GAP);
        icon = iconSize >= ICON_MIN;
        if (!icon) iconSize = 0f;
        float content = textHeight + (icon ? iconSize + ICON_TEXT_GAP : 0f);
        shown = content <= band || !controls;
        if (!shown) return;
        float top = content <= band ? TOP_MARGIN + (band - content) / 2f : TOP_MARGIN;
        iconY = top;
        textTop = top + (icon ? iconSize + ICON_TEXT_GAP : 0f);
    }

    /** Seconds into the loop for a clock in seconds. */
    static float phase(float seconds) {
        float p = seconds % CYCLE;
        return p < 0f ? p + CYCLE : p;
    }

    /** True while the phone is on its way back to upright (the arrow is mirrored then, to point the way the phone turns). */
    static boolean returning(float seconds) {
        return phase(seconds) >= HOLD + TURN + HOLD;
    }

    /** The phone's turn in degrees at {@code seconds} into the loop: 0 held, eased to {@link #TURN_DEGREES}, held, eased back. */
    static float angle(float seconds) {
        float p = phase(seconds);
        if (p < HOLD) return 0f;
        if (p < HOLD + TURN) return TURN_DEGREES * smooth((p - HOLD) / TURN);
        if (p < HOLD + TURN + HOLD) return TURN_DEGREES;
        return TURN_DEGREES * (1f - smooth((p - HOLD - TURN - HOLD) / TURN));
    }

    private static float smooth(float u) { return u * u * (SMOOTHSTEP_CUBIC - SMOOTHSTEP_QUADRATIC * u); }
}
