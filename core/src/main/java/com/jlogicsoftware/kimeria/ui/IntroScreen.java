package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.badlogic.gdx.math.MathUtils;

/**
 * Real-time opening/ending story in logical pixels; independent of gameplay time scaling. Static pages (#336): after a
 * black fade-in the paragraphs of a page appear one after another, top to bottom, and stay; the player turns the page
 * and the last turn fades to black. With instant text (#342) a page appears whole and turns at once.
 */
public final class IntroScreen {
    public enum State { FADE_IN, READING, FADE_OUT, DONE }
    /** What the current page is doing while the state is {@link State#READING}. */
    public enum Phase { REVEAL, WAIT, TURN }

    public static final float FADE_IN_SECONDS = 2f, FADE_OUT_SECONDS = 1f;
    /** Side margin of the text column (the wrap width is the screen width minus both). */
    public static final float TEXT_MARGIN = 64f;
    /** A paragraph fades in over {@code PARAGRAPH_FADE_SECONDS}; the next starts {@code PARAGRAPH_STAGGER_SECONDS} after it. */
    public static final float PARAGRAPH_FADE_SECONDS = 0.9f, PARAGRAPH_STAGGER_SECONDS = 1.6f;
    /** The page fades out this long before the next one starts. */
    public static final float PAGE_TURN_SECONDS = 0.4f;
    /** Input is ignored this long after a page starts and after it is fully shown, so a held button cannot skip pages. */
    public static final float INPUT_LOCK_SECONDS = 0.6f;
    private static final float CUE_TOP = 330f;
    /** The page-turn cue sprite (art/hud/touch/cue.grid): a white triangle pointing down, this many px square. */
    public static final int CUE_SIZE = 8;
    private static final float CUE_PULSE_SPEED = 4f, CUE_ALPHA_BASE = 0.55f, CUE_ALPHA_SWING = 0.45f;

    private static final String CUE_ART = "images/HUD/touch/cue.png";
    private final String text;
    private final IntroPages pages;
    private final BitmapFontCache[] blocks;
    private final boolean instant;
    private final float cueCenterX;
    private final TextureRegion cue;
    private State state = State.FADE_IN;
    private Phase phase = Phase.REVEAL;
    private int page;
    private float phaseTime, lock, cueTime;
    private float visibility;
    private float fadeOutStart;
    private float fadeElapsed;

    /** For tests and headless flows; {@code blocks} may be null when nothing is drawn. The cue sits at {@code screenWidth / 2}, like the text. */
    public IntroScreen(String text, IntroPages pages, BitmapFontCache[] blocks, boolean instant, float screenWidth, TextureRegion cue) {
        this.cue = cue;
        this.cueCenterX = screenWidth / 2f;
        this.text = text;
        this.pages = pages;
        this.blocks = blocks;
        this.instant = instant;
    }

    /** Splits {@code text} into pages with the body font and caches one text block per paragraph (once, at load). */
    public static IntroScreen open(String text, Overlay overlay, Assets assets, float screenWidth, boolean instant) {
        float wrap = screenWidth - TEXT_MARGIN * 2f;
        IntroPages pages = IntroPages.split(text, paragraph -> overlay.bodyHeight(paragraph, wrap));
        BitmapFontCache[] blocks = new BitmapFontCache[pages.paragraphCount()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = overlay.bodyBlock(pages.paragraph(i), screenWidth / 2f, pages.top(i), wrap);
        return new IntroScreen(text, pages, blocks, instant, screenWidth, assets.region(CUE_ART, 0, 0, CUE_SIZE, CUE_SIZE));
    }

    /** Fades everything out now (Escape, Back): the one way to leave the story before its last page. */
    public void skip() {
        if (state == State.FADE_OUT || state == State.DONE) return;
        state = State.FADE_OUT;
        fadeOutStart = visibility;
        fadeElapsed = 0f;
    }

    /**
     * The page-turn button. While the page is appearing it shows the rest at once; once shown it turns the page (the last
     * page ends the story). Ignored while the input lock runs, during the fades and between pages.
     * @return true if the press did something
     */
    public boolean turn() {
        if (state != State.READING || lock > 0f) return false;
        switch (phase) {
            case REVEAL -> showWholePage();
            case WAIT -> {
                if (page == pages.pageCount() - 1) skip();
                else if (instant) startPage(page + 1);
                else { phase = Phase.TURN; phaseTime = 0f; }
            }
            case TURN -> { return false; }
        }
        return true;
    }

    public void update(float dt) {
        dt = Math.max(0f, dt);
        switch (state) {
            case FADE_IN -> {
                visibility = Math.min(1f, visibility + dt / FADE_IN_SECONDS);
                if (visibility == 1f) { state = State.READING; startPage(0); }
            }
            case READING -> {
                lock = Math.max(0f, lock - dt);
                cueTime += dt;
                phaseTime += dt;
                if (phase == Phase.REVEAL && phaseTime >= revealSeconds()) showWholePage();
                else if (phase == Phase.TURN && phaseTime >= PAGE_TURN_SECONDS) startPage(page + 1);
            }
            case FADE_OUT -> {
                fadeElapsed = Math.min(FADE_OUT_SECONDS, fadeElapsed + dt);
                visibility = fadeOutStart * (1f - fadeElapsed / FADE_OUT_SECONDS);
                if (fadeElapsed == FADE_OUT_SECONDS) state = State.DONE;
            }
            case DONE -> { }
        }
    }

    private void startPage(int index) {
        page = index;
        phaseTime = 0f;
        lock = INPUT_LOCK_SECONDS;
        phase = Phase.REVEAL;
        if (instant) showWholePage();
    }

    private void showWholePage() {
        phase = Phase.WAIT;
        phaseTime = 0f;
        lock = INPUT_LOCK_SECONDS;
        cueTime = 0f;
    }

    private float revealSeconds() {
        return (pages.end(page) - pages.first(page) - 1) * PARAGRAPH_STAGGER_SECONDS + PARAGRAPH_FADE_SECONDS;
    }

    /** Opacity (0 to 1) of paragraph {@code index}: zero outside the current page and before its turn to appear. */
    public float paragraphAlpha(int index) {
        if (state == State.FADE_IN || state == State.DONE) return 0f;
        if (index < pages.first(page) || index >= pages.end(page)) return 0f;
        return switch (phase) {
            case WAIT -> 1f;
            case TURN -> 1f - Math.min(1f, phaseTime / PAGE_TURN_SECONDS);
            case REVEAL -> MathUtils.clamp((phaseTime - (index - pages.first(page)) * PARAGRAPH_STAGGER_SECONDS) / PARAGRAPH_FADE_SECONDS, 0f, 1f);
        };
    }

    /** Queues the visible paragraphs (text, Y-up pass) and draws the page-turn cue (Y-down screen projection). */
    public void render(Batch batch, Overlay overlay) {
        if (blocks == null) return;
        for (int i = pages.first(page); i < pages.end(page); i++) overlay.queueBlock(blocks[i], paragraphAlpha(i));
        if (state == State.READING && phase == Phase.WAIT && lock <= 0f) drawCue(batch);
    }

    /** The cue sprite, pulsing: the page is complete and waits for the button (no words, so no catalog string). */
    private void drawCue(Batch batch) {
        float alpha = CUE_ALPHA_BASE + CUE_ALPHA_SWING * MathUtils.sin(cueTime * CUE_PULSE_SPEED);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(cue, cueCenterX - CUE_SIZE / 2f, CUE_TOP, CUE_SIZE, CUE_SIZE);
        batch.setColor(Color.WHITE);
    }

    /** Horizontal centre of the page-turn cue: the same centre the text blocks use. */
    public float cueCenterX() { return cueCenterX; }
    public String text() { return text; }
    public IntroPages pages() { return pages; }
    public int pageIndex() { return page; }
    public Phase phase() { return phase; }
    /** True while a press would do nothing because of the input lock. */
    public boolean locked() { return lock > 0f; }
    public float alpha() { return 1f - visibility; }
    /** The same envelope drives the black overlay and the intro music volume. */
    public float volume() { return visibility; }
    public State state() { return state; }
    public boolean done() { return state == State.DONE; }
}
