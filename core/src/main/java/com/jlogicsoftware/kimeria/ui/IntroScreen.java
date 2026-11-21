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
 * <p>The ending (#416, D95) draws the Kimeria arms above its text: dark for a short hold, the sun lights up on it, and only then
 * does the text appear (D95, #573); it is lit whenever the page is shown whole (instant text, a press).
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
    /** The skip hint sits in the bottom right corner, under the page area ({@link IntroPages#PAGE_BOTTOM}) and clear of the cue. */
    public static final float SKIP_HINT_MARGIN = 12f, SKIP_HINT_TOP = 338f;
    /** The skip hint stays quiet: this fraction of full brightness (times the story's fade). */
    public static final float SKIP_HINT_ALPHA = 0.55f;
    /** Which skip hint is shown: the key of the input mode in use; touch shows none (its button is the hint, D77). */
    public enum SkipHint { NONE, KEYBOARD, GAMEPAD }
    /** The page-turn cue sprite (art/hud/touch/cue.grid): a white triangle pointing down, this many px square. */
    public static final int CUE_SIZE = 8;
    private static final float CUE_PULSE_SPEED = 4f, CUE_ALPHA_BASE = 0.55f, CUE_ALPHA_SWING = 0.45f;

    /** The arms on the ending page (#416, D95): 3x (72x108), top at the text top of other pages, 20 px above the ending's text. */
    public static final int ARMS_SCALE = 3;
    public static final float ARMS_TOP = IntroPages.TEXT_TOP, ARMS_GAP = 20f;
    public static final float ENDING_TEXT_TOP = ARMS_TOP + KimeriaArms.HEIGHT * ARMS_SCALE + ARMS_GAP;
    /**
     * The sun lights in these steps, each ending this many seconds after the dark hold (1.0 s in all), then holds
     * {@link KimeriaArms#LIT}. The flare is the light bursting out once before it settles. The text waits for it (D95, #573).
     */
    private static final int[] LIGHT_FRAMES = {KimeriaArms.EMBER, KimeriaArms.GLOW, KimeriaArms.KINDLE, KimeriaArms.FLARE};
    private static final float[] LIGHT_FRAME_ENDS = {0.2f, 0.4f, 0.6f, 1.0f};
    /** On the page with the sun the shield stays dark this long, then lights; the text starts {@code TEXT_BEAT_SECONDS} after it is lit. */
    public static final float ARMS_DARK_HOLD_SECONDS = 0.5f, TEXT_BEAT_SECONDS = 0.25f;
    /** When the sun is lit and when the first paragraph starts to fade in, from the start of its page. */
    public static final float ARMS_LIT_AT = ARMS_DARK_HOLD_SECONDS + LIGHT_FRAME_ENDS[LIGHT_FRAME_ENDS.length - 1];
    public static final float TEXT_START_AT = ARMS_LIT_AT + TEXT_BEAT_SECONDS;

    private static final String CUE_ART = "images/HUD/touch/cue.png";
    private final String text;
    private final IntroPages pages;
    private final BitmapFontCache[] blocks;
    private final boolean instant;
    private final float cueCenterX;
    private final TextureRegion cue;
    /** Null for a story without the arms (the opening). */
    private final KimeriaArms arms;
    /** True for the ending: its arms are drawn (or, headless, its page is split under them). */
    private final boolean sun;
    /** The paragraph that brings the light back, its page and when it starts to fade in on that page. */
    private final int lightParagraph, lightPage;
    private State state = State.FADE_IN;
    private Phase phase = Phase.REVEAL;
    private int page;
    private float phaseTime, lock, cueTime;
    private float visibility;
    private float fadeOutStart;
    private float fadeElapsed;
    private BitmapFontCache keyboardHint, gamepadHint;
    private SkipHint skipHint = SkipHint.NONE;

    /** For tests and headless flows; {@code blocks} may be null when nothing is drawn. The cue sits at {@code screenWidth / 2}, like the text. */
    public IntroScreen(String text, IntroPages pages, BitmapFontCache[] blocks, boolean instant, float screenWidth, TextureRegion cue) {
        this(text, pages, blocks, instant, screenWidth, cue, null);
    }

    /** As above, with the Kimeria arms drawn over the text when {@code arms} is not null (the ending). */
    public IntroScreen(String text, IntroPages pages, BitmapFontCache[] blocks, boolean instant, float screenWidth, TextureRegion cue,
                       KimeriaArms arms) {
        this.cue = cue;
        this.arms = arms;
        this.sun = arms != null || pages.top(0) == ENDING_TEXT_TOP;
        this.lightParagraph = lightParagraph(pages);
        int at = 0;
        while (pages.end(at) <= lightParagraph) at++;
        this.lightPage = at;
        this.cueCenterX = screenWidth / 2f;
        this.text = text;
        this.pages = pages;
        this.blocks = blocks;
        this.instant = instant;
    }

    /** Splits {@code text} into pages with the body font and caches one text block per paragraph (once, at load). */
    public static IntroScreen open(String text, Overlay overlay, Assets assets, float screenWidth, boolean instant) {
        return open(text, overlay, assets, screenWidth, instant, null);
    }

    /** As above; with {@code arms} (the ending) the text starts at {@link #ENDING_TEXT_TOP}, under the arms. */
    public static IntroScreen open(String text, Overlay overlay, Assets assets, float screenWidth, boolean instant, KimeriaArms arms) {
        float wrap = screenWidth - TEXT_MARGIN * 2f;
        IntroPages pages = split(text, paragraph -> overlay.bodyHeight(paragraph, wrap), arms != null);
        BitmapFontCache[] blocks = new BitmapFontCache[pages.paragraphCount()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = overlay.bodyBlock(pages.paragraph(i), screenWidth / 2f, pages.top(i), wrap);
        return new IntroScreen(text, pages, blocks, instant, screenWidth, assets.region(CUE_ART, 0, 0, CUE_SIZE, CUE_SIZE), arms);
    }

    /** The page split {@link #open} uses: under the arms ({@link #ENDING_TEXT_TOP}) or at the usual text top. */
    public static IntroPages split(String text, IntroPages.Measure measure, boolean withArms) {
        return IntroPages.split(text, measure, withArms ? ENDING_TEXT_TOP : IntroPages.TEXT_TOP);
    }

    /**
     * The paragraph that brings the light back: the last one before the closing line (THE END, КІНЕЦЬ, ENDE...), found
     * from the text, not by a fixed count. The closing line is every trailing paragraph written in capitals; a story
     * without one lights with its last paragraph. Both the ending on main ("Light has returned to the world.") and the
     * healer's of #372 ("Then the sun shone again...") put the light there; {@code IntroPagesTest} checks the structure and
     * the light words in all five shipped catalogs, so a translation that moves the light fails a test, not the game.
     * Runs once at load.
     */
    public static int lightParagraph(IntroPages pages) {
        int last = pages.paragraphCount() - 1;
        while (last > 0 && isClosingLine(pages.paragraph(last))) last--;
        return last;
    }

    /** A closing line such as THE END: it has a letter and no lower-case letter. */
    public static boolean isClosingLine(String paragraph) {
        boolean letter = false;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (Character.isLowerCase(c)) return false;
            letter |= Character.isLetter(c);
        }
        return letter;
    }

    /** The two cached skip hints, built once at load ({@link Overlay#hintBlock}); either may be null (tests). */
    public void setSkipHints(BitmapFontCache keyboard, BitmapFontCache gamepad) {
        keyboardHint = keyboard;
        gamepadHint = gamepad;
    }

    /** Chooses the hint for the input mode in use; call each frame, it only stores a value. */
    public void skipHint(SkipHint hint) { skipHint = hint; }
    public SkipHint skipHint() { return skipHint; }

    /** The mode order: touch (its button is the hint, no text) before a connected pad before the keyboard. */
    public static SkipHint skipHintFor(boolean touch, boolean padConnected) {
        return touch ? SkipHint.NONE : padConnected ? SkipHint.GAMEPAD : SkipHint.KEYBOARD;
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
            case REVEAL -> {
                // D95 (#573): a press while the sun lights completes the sun (the text then reveals as usual); it never skips it unseen.
                if (phaseTime < textStart()) { phaseTime = textStart(); lock = INPUT_LOCK_SECONDS; }
                else showWholePage();
            }
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
        return textStart() + (pages.end(page) - pages.first(page) - 1) * PARAGRAPH_STAGGER_SECONDS + PARAGRAPH_FADE_SECONDS;
    }

    /** Seconds after its page starts that the text begins to appear: after the sun on the page with the sun, else at once. */
    private float textStart() { return sun && page == lightPage ? TEXT_START_AT : 0f; }

    /** Opacity (0 to 1) of paragraph {@code index}: zero outside the current page and before its turn to appear. */
    public float paragraphAlpha(int index) {
        if (state == State.FADE_IN || state == State.DONE) return 0f;
        if (index < pages.first(page) || index >= pages.end(page)) return 0f;
        return switch (phase) {
            case WAIT -> 1f;
            case TURN -> 1f - Math.min(1f, phaseTime / PAGE_TURN_SECONDS);
            case REVEAL -> MathUtils.clamp((phaseTime - textStart() - (index - pages.first(page)) * PARAGRAPH_STAGGER_SECONDS) / PARAGRAPH_FADE_SECONDS, 0f, 1f);
        };
    }

    /**
     * The arms frame now: dark for a short hold when the page with the sun starts, then the lighting steps, then lit; the
     * text appears only after that (D95, #573). Lit at once
     * whenever the page is whole (instant text, a press during the reveal, the wait for the turn), so D77's final state holds.
     * An Escape during the reveal freezes the frame it had while the story fades out.
     */
    public int armsFrame() {
        if (instant || page > lightPage) return KimeriaArms.LIT;
        if (page < lightPage || state == State.FADE_IN) return KimeriaArms.DARK;
        if (phase != Phase.REVEAL) return KimeriaArms.LIT;
        float t = phaseTime - ARMS_DARK_HOLD_SECONDS;
        if (t < 0f) return KimeriaArms.DARK;
        for (int i = 0; i < LIGHT_FRAMES.length; i++) if (t < LIGHT_FRAME_ENDS[i]) return LIGHT_FRAMES[i];
        return KimeriaArms.LIT;
    }

    /** Queues the visible paragraphs (text, Y-up pass) and draws the arms and the page-turn cue (Y-down screen projection). */
    public void render(Batch batch, Overlay overlay) {
        if (arms != null) arms.draw(batch, armsFrame(), cueCenterX, ARMS_TOP, ARMS_SCALE);
        if (blocks == null) return;
        for (int i = pages.first(page); i < pages.end(page); i++) overlay.queueBlock(blocks[i], paragraphAlpha(i));
        if (state == State.READING && phase == Phase.WAIT && lock <= 0f) drawCue(batch);
        BitmapFontCache hint = skipHint == SkipHint.KEYBOARD ? keyboardHint : skipHint == SkipHint.GAMEPAD ? gamepadHint : null;
        if (hint != null && state != State.DONE) overlay.queueBlock(hint, SKIP_HINT_ALPHA * visibility);
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
    /** Index of the paragraph that lights the sun (see {@link #lightParagraph(IntroPages)}). */
    public int lightParagraph() { return lightParagraph; }
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
