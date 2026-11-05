package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.math.Interpolation;

import com.badlogic.gdx.math.Vector2;

/**
 * Real-time text transition; an outgoing label retains its own world anchor.
 * <p>
 * An <em>attention</em> label (a map-authored tutorial message, D33) is first held back for
 * {@link #ATTENTION_DELAY} seconds of request, then fades in more slowly and settles with a small
 * overshoot slide (the "pulse"), so it draws the eye instead of sitting on screen from the first frame.
 * Once shown it stays readable: it lingers {@link #LINGER} seconds after the hero leaves its zone and is
 * not replaced by another tutorial message before it has been up {@link #MIN_SHOW} seconds, so walking
 * back and forth over a zone boundary neither flickers nor makes the player search for the text.
 * A short gap in the request ({@link #REQUEST_GRACE}) does not restart the appear delay.
 * A tutorial message that replaces another label skips the appear delay (a quick crossfade). Contextual
 * interaction labels ("Pick up") stay immediate and replace a lingering message at once, but never one
 * that is still fading in: that finishes first (at most {@link #ATTENTION_FADE_IN} seconds), so a message
 * is never erased before it was readable.
 */
public final class HintTransition {
    /** Seconds a tutorial message must stay requested before it appears. */
    public static final float ATTENTION_DELAY = .35f;
    /** Seconds an attention label stays after its request ends. */
    public static final float LINGER = 2.5f;
    /** Seconds an attention label stays up before another attention label may replace it. */
    public static final float MIN_SHOW = .7f;
    /** A request may drop out for this long (boundary jitter) without restarting the appear delay. */
    public static final float REQUEST_GRACE = .25f;
    private static final float FADE_IN = .18f, FADE_OUT = .25f, ATTENTION_FADE_IN = .45f;
    private static final float SLIDE = 12, ATTENTION_SLIDE = 22;
    /** Overshoots the resting place by roughly a quarter of the slide (about 5-6 px). */
    private static final Interpolation PULSE = new Interpolation.SwingOut(4f);
    /** Longest frame step the HUD feeds the timer, so a load hitch cannot skip the delay. */
    public static final float MAX_FRAME = .05f;
    private final Vector2 anchor = new Vector2();
    private String text = "", pending = "";
    private float waited, gap, shown, absent;
    private float progress = 1, alpha, offsetY = SLIDE, startAlpha, startOffset = SLIDE;
    private boolean exiting, attention, skipDelay;

    /** A real frame time limited to {@link #MAX_FRAME}. */
    public static float clampFrame(float dt) { return Math.max(0, Math.min(MAX_FRAME, dt)); }

    public void update(String requested, Vector2 requestedAnchor, float dt) {
        update(requested, requestedAnchor, dt, false);
    }

    /** @param requestedAttention whether {@code requested} is a delayed, attention-grabbing tutorial message */
    public void update(String requested, Vector2 requestedAnchor, float dt, boolean requestedAttention) {
        dt = Math.max(0, dt);
        if (text.isEmpty()) {
            if (requested.isEmpty()) {
                gap += dt;
                if (gap > REQUEST_GRACE) { pending = ""; waited = 0; }
                return;
            }
            if (requestedAttention && !skipDelay) {
                if (requested.equals(pending)) { waited += dt; gap = 0; } else { waited = dt; gap = 0; }
                pending = requested;
                if (waited < ATTENTION_DELAY) return;
            }
            pending = ""; waited = 0; gap = 0; shown = 0; absent = 0; skipDelay = false;
            text = requested;
            anchor.set(requestedAnchor);
            attention = requestedAttention;
            begin(false);
            if (attention) dt = 0; // The wait ends transparent; the fade starts on the next frame.
        }
        boolean held = false;
        if (attention && !exiting) {
            shown += dt;
            if (text.equals(requested)) absent = 0;
            else if (requested.isEmpty()) { absent += dt; held = absent < LINGER; }
            else if (requestedAttention) held = shown < MIN_SHOW;
            else held = progress < 1; // An immediate label waits for the message to finish fading in.
            if (held) requested = text; // Keep the label (and its anchor) up for now.
        }
        boolean leaving = !text.equals(requested);
        if (leaving != exiting) begin(leaving);
        if (!leaving && !held) anchor.set(requestedAnchor);
        float duration = exiting ? FADE_OUT : attention ? ATTENTION_FADE_IN : FADE_IN;
        progress = Math.min(1, progress + dt / duration);
        float eased = Interpolation.smooth.apply(progress);
        alpha = startAlpha + ((exiting ? 0 : 1) - startAlpha) * eased;
        if (exiting) offsetY = startOffset + (-SLIDE - startOffset) * eased;
        else if (attention) offsetY = startOffset * (1 - PULSE.apply(progress));
        else offsetY = startOffset + (0 - startOffset) * eased;
        if (exiting && progress == 1) {
            // Swap only while invisible. A new label starts below its own anchor.
            clear();
            // A message replacing any shown label crossfades without the delay; a first one waits it out.
            skipDelay = requestedAttention && !requested.isEmpty();
            update(requested, requestedAnchor, 0, requestedAttention);
        }
    }

    private void begin(boolean leaving) {
        exiting = leaving;
        startAlpha = alpha;
        startOffset = !leaving && attention && alpha == 0 ? ATTENTION_SLIDE : offsetY;
        progress = 0;
    }

    public String text() { return text; }
    public float alpha() { return alpha; }
    /**
     * Logical UI pixels, positive downward: enter from below, exit above. An attention label overshoots
     * slightly above its resting place (negative) before settling.
     */
    public float offsetY() { return offsetY; }
    public void position(Vector2 out) { out.set(anchor); }
    public void clear() {
        text = pending = ""; waited = gap = shown = absent = 0; attention = false;
        alpha = startAlpha = 0; offsetY = startOffset = SLIDE;
        progress = 1; exiting = false; anchor.setZero();
    }
}
