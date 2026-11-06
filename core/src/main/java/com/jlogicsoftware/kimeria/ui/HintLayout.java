package com.jlogicsoftware.kimeria.ui;

/**
 * Vertical placement of the two hint channels (#211): the map-authored zone message and the immediate
 * contextual label are drawn together and must not overlap. All values are in Y-up screen/text coordinates:
 * a label's {@code top} is the y its first line is drawn at, and it extends down by its height.
 * <p>
 * The zone message never moves: it sits at its own anchor (see {@link #top}). The contextual label keeps
 * its anchor unless that would put it closer than {@link #GAP} below the message; then it is lowered to
 * {@code GAP} under it (and stays on screen). The reservation depends only on whether a message is present
 * (shown, pending or lingering), not on its momentary opacity, so the label does not jump when it
 * crossfades or the message fades.
 */
public final class HintLayout {
    /**
     * Smallest clear space between the zone message's bottom and the contextual label's top, in px. Larger
     * than the message's slide-in (22 px at zero opacity, see {@code HintTransition}), so even its faint
     * start cannot reach an already shown label; the label's own 12 px slide moves away from the message.
     */
    public static final float GAP = 24;
    public static final float SIDE_MARGIN = 8, BOTTOM_MARGIN = 40, TOP_MARGIN = 52;

    private HintLayout() { }

    /**
     * Top edge of a label whose anchor projects to {@code anchorY} on a screen {@code screenHeight} high: the
     * anchor clamped on screen, grown upward so a second line never covers the hero's head below the label.
     */
    public static float top(float anchorY, float height, float capHeight, float screenHeight) {
        float y = Math.max(BOTTOM_MARGIN, Math.min(screenHeight - TOP_MARGIN, anchorY));
        return Math.min(screenHeight - TOP_MARGIN, y + Math.max(0, height - capHeight));
    }

    /**
     * Top edge of the contextual label for the given anchors (screen y, Y-up). {@code zoneHeight} is the height of
     * the zone message that is present (or about to be); it is ignored when {@code zonePresent} is false.
     */
    public static float contextTop(float contextAnchorY, float contextHeight, boolean zonePresent,
                                   float zoneAnchorY, float zoneHeight, float capHeight, float screenHeight) {
        return contextTop(contextAnchorY, contextHeight, zonePresent, zoneAnchorY, zoneHeight, capHeight, capHeight, screenHeight);
    }

    /** As above, with each label's own cap height: the two channels use different font sizes (#249). */
    public static float contextTop(float contextAnchorY, float contextHeight, boolean zonePresent,
                                   float zoneAnchorY, float zoneHeight, float capHeight, float zoneCapHeight, float screenHeight) {
        float zoneBottom = top(zoneAnchorY, zoneHeight, zoneCapHeight, screenHeight) - zoneHeight;
        return contextTop(top(contextAnchorY, contextHeight, capHeight, screenHeight), contextHeight, zonePresent, zoneBottom);
    }

    /** Top of the contextual label: its own top, lowered if needed to stay {@link #GAP} under the zone message. */
    public static float contextTop(float top, float height, boolean zonePresent, float zoneBottom) {
        if (!zonePresent) return top;
        return Math.max(height, Math.min(top, zoneBottom - GAP));
    }
}
