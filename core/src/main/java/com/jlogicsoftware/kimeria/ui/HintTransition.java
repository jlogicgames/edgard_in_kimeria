package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.math.Vector2;

/** Real-time text transition; an outgoing label retains its own world anchor. */
public final class HintTransition {
    private static final float FADE_IN = .18f, FADE_OUT = .25f;
    private final Vector2 anchor = new Vector2();
    private String text = "";
    private static final float SLIDE = 12;
    private float progress = 1, alpha, offsetY = SLIDE, startAlpha, startOffset = SLIDE;
    private boolean exiting;

    public void update(String requested, Vector2 requestedAnchor, float dt) {
        dt = Math.max(0, dt);
        if (text.isEmpty()) {
            if (requested.isEmpty()) return;
            text = requested;
            anchor.set(requestedAnchor);
            begin(false);
        }
        boolean leaving = !text.equals(requested);
        if (leaving != exiting) begin(leaving);
        if (!leaving) anchor.set(requestedAnchor);
        progress = Math.min(1, progress + dt / (exiting ? FADE_OUT : FADE_IN));
        float eased = progress * progress * (3 - 2 * progress);
        alpha = startAlpha + ((exiting ? 0 : 1) - startAlpha) * eased;
        offsetY = startOffset + ((exiting ? -SLIDE : 0) - startOffset) * eased;
        if (exiting && progress == 1) {
            // Swap only while invisible. A new label starts below its own anchor.
            clear();
            text = requested;
            if (!requested.isEmpty()) { anchor.set(requestedAnchor); begin(false); }
        }
    }

    private void begin(boolean leaving) {
        exiting = leaving;
        startAlpha = alpha;
        startOffset = offsetY;
        progress = 0;
    }

    public String text() { return text; }
    public float alpha() { return alpha; }
    /** Logical UI pixels, positive downward: enter from below, exit above. */
    public float offsetY() { return offsetY; }
    public void position(Vector2 out) { out.set(anchor); }
    public void clear() {
        text = ""; alpha = startAlpha = 0; offsetY = startOffset = SLIDE;
        progress = 1; exiting = false; anchor.setZero();
    }
}
