package com.jlogicsoftware.kimeria.ui;

/** Real-time opening/ending story timeline in logical pixels; independent of gameplay time scaling. */
public final class IntroScreen {
    public enum State { FADE_IN, SCROLLING, FADE_OUT, DONE }

    public static final float SCROLL_SPEED = 24f;
    public static final float FADE_IN_SECONDS = 2f, FADE_OUT_SECONDS = 1f;
    public static final float TEXT_MARGIN = 64f;

    private final String text;
    private final float textHeight;
    private State state = State.FADE_IN;
    private float textTop;
    private float visibility;
    private float fadeOutStart;
    private float fadeElapsed;

    public IntroScreen(String text, float textHeight, float screenHeight) {
        this.text = text;
        this.textHeight = textHeight;
        textTop = screenHeight;
    }

    public void skip() {
        if (state == State.FADE_OUT || state == State.DONE) return;
        state = State.FADE_OUT;
        fadeOutStart = visibility;
        fadeElapsed = 0f;
    }

    public void update(float dt) {
        dt = Math.max(0f, dt);
        switch (state) {
            case FADE_IN -> {
                visibility = Math.min(1f, visibility + dt / FADE_IN_SECONDS);
                if (visibility == 1f) state = State.SCROLLING;
            }
            case SCROLLING -> {
                textTop -= SCROLL_SPEED * dt;
                if (textTop + textHeight <= 0f) skip();
            }
            case FADE_OUT -> {
                fadeElapsed = Math.min(FADE_OUT_SECONDS, fadeElapsed + dt);
                visibility = fadeOutStart * (1f - fadeElapsed / FADE_OUT_SECONDS);
                if (fadeElapsed == FADE_OUT_SECONDS) state = State.DONE;
            }
            case DONE -> { }
        }
    }

    public String text() { return text; }
    public float textTop() { return textTop; }
    public float alpha() { return 1f - visibility; }
    /** The same envelope drives the black overlay and the intro music volume. */
    public float volume() { return visibility; }
    public State state() { return state; }
    public boolean done() { return state == State.DONE; }
}
