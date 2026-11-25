package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.jlogicsoftware.kimeria.world.RoomFade;

/**
 * The ripple of a room change (#553, part of #396): where the hero vanishes into the exit and where he appears in the next room,
 * three violet rings spread out one after another (the existing {@code shockwave.frag} through {@link RingPool}, as the pickup's
 * ring) while motes of light are pulled into the spot on leaving and fly out of it on entering. It takes the look of patch-gg/pixelfx's
 * {@code water/ripples} (rings out fast, slowing, fading; MIT, see {@code THIRD_PARTY_NOTICES.md}) and of the portal's inward pull,
 * with no code of theirs. The screen-distorting {@link RippleEffect} rides on the same two moments with {@link #DISTORT_SECONDS} and
 * the numbers below: one object owned here and restarted at each leave and enter ({@link #distortion}), which {@code KimeriaGame} applies through its post pass.
 *
 * <p>One per {@code Level}. Fixed pools only; {@link #update} advances it, so pause freezes it. World Y grows downward; the rings
 * are centred on the point. Cosmetic: the room change itself is {@code RoomFade} and the room switch, which this does not touch.
 * The pause on leaving ({@link #LEAVE_HOLD_SECONDS}) is the time the effect is given before the fade to black starts. The
 * entering ripple waits for the fade-in to clear ({@link #ENTER_DELAY_SECONDS}), the hero is not drawn until it has run
 * {@link #REVEAL_SECONDS} ({@link #heroUnseen}), and the world stays held for {@link #ARRIVE_HOLD_SECONDS} of it (#606).
 */
public final class RoomRippleFx {
    /** The pause before the fade starts, so the leaving ripple can play; ignores input (D-number of #553). */
    public static final float LEAVE_HOLD_SECONDS = .2f;
    /** From the room switch to the entering ripple: the whole fade-in, so none of it plays under the black panel (#606). */
    public static final float ENTER_DELAY_SECONDS = RoomFade.HALF_SECONDS;
    /** The pause after the fade-in, so the entering ripple plays on a clear screen before the world moves; ignores input (#606). */
    public static final float ARRIVE_HOLD_SECONDS = .35f;
    /** How far into the entering ripple the hero is first drawn, once the screen ripple has mostly left him: he appears out of it, as he vanishes into the leaving one (#606). */
    public static final float REVEAL_SECONDS = .15f;
    public static final int RING_CAPACITY = 8, MOTE_CAPACITY = 48, RINGS = 3, MOTES = 16;
    /** Ring k starts {@code k * RING_STAGGER} after the first, lives {@code RING_LIFE} and reaches {@code RING_RADIUS + k * RING_STEP}; {@code RING_WIDTH} px thick. */
    public static final float RING_STAGGER = .07f, RING_LIFE = .45f, RING_RADIUS = 26, RING_STEP = 10, RING_WIDTH = 3;
    /** {@code shockwave.frag} intensity of the violet ring: the portal's violet, over 1 because the shader's own ring peaks near a quarter of it. */
    private static final float[] RING_COLOR = {1.87f, .94f, 3.23f};
    /** The distortion {@link RippleEffect} takes: duration, max radius, strength, frequency, decay (the F3 coin ripple's numbers scaled down). The strength stays low: the lens is centred on the hero and swells him. */
    public static final float DISTORT_SECONDS = .6f, DISTORT_RADIUS = 90, DISTORT_STRENGTH = 6, DISTORT_FREQUENCY = 60, DISTORT_DECAY = 30;
    private static final long MOTE_SEED = 0x7195L;
    private static final float MOTE_SIZE = 2, MOTE_REACH = 24, LEAVE_LIFE_MIN = .22f, LEAVE_LIFE_MAX = .36f, ENTER_LIFE_MIN = .3f, ENTER_LIFE_MAX = .5f;
    private static final float LEAVE_FROM = 1.6f, ENTER_TO = 1.4f, SWEEP = .3f;
    private static final float[] MOTE_COLOR = {.776f, .518f, 1f, .925f, .784f, 1f, 1f, 1f, 1f};
    private static final int STRIDE = 3, GREEN = 1, BLUE = 2, COLOURS = 3;

    private final RingPool rings = new RingPool(RING_CAPACITY, RING_COLOR);
    private final KimeriaParticleSystem motes = new KimeriaParticleSystem(MOTE_CAPACITY, MOTE_SEED);
    private final float[] wait = new float[RINGS];
    private float x, y;
    private boolean queued;
    /** One screen ripple for the object's whole life, restarted at each leave and enter, so a room change allocates nothing. */
    private final RippleEffect distortion = new RippleEffect(0, 0, DISTORT_SECONDS, DISTORT_RADIUS, DISTORT_STRENGTH, DISTORT_FREQUENCY, DISTORT_DECAY);
    private boolean distorting;
    /** An entering ripple was asked for and the hero is not drawn yet: where, how long ago, and whether the ripple has started. */
    private float arriveX, arriveY, arriveClock;
    private boolean arriving, started;

    public KimeriaParticleSystem motes() { return motes; }
    public int ringCount() { return rings.count(); }

    /** The screen distortion of the live ripple, or null; {@code KimeriaGame} applies it through {@code screen_effects.frag}. */
    public RippleEffect distortion() { return distorting && !distortion.isRemoved() ? distortion : null; }

    /** True from {@link #enter} until the hero is first drawn, {@link #REVEAL_SECONDS} into the entering ripple. */
    public boolean heroUnseen() { return arriving; }

    /** The hero vanishes at ({@code px}, {@code py}), his centre: rings spread and motes are pulled in. */
    public void leave(float px, float py) {
        arriving = false;
        begin(px, py, true);
    }

    /** The hero appears at ({@code px}, {@code py}) in the new room: after {@link #ENTER_DELAY_SECONDS} rings spread and motes fly out. */
    public void enter(float px, float py) {
        arriveX = px; arriveY = py;
        arriveClock = 0;
        arriving = true;
        started = false;
    }

    private void begin(float px, float py, boolean inward) {
        x = px; y = py;
        distortion.restart(px, py);
        distorting = true;
        for (int k = 0; k < RINGS; k++) wait[k] = k * RING_STAGGER;
        queued = true;
        for (int n = 0; n < MOTES; n++) {
            float turn = motes.random().nextFloat(), life = inward ? LEAVE_LIFE_MIN + motes.random().nextFloat() * (LEAVE_LIFE_MAX - LEAVE_LIFE_MIN)
                : ENTER_LIFE_MIN + motes.random().nextFloat() * (ENTER_LIFE_MAX - ENTER_LIFE_MIN);
            int c = motes.random().nextInt(COLOURS) * STRIDE;
            motes.spawnOrbit(px, py, MOTE_REACH, MOTE_REACH, turn, inward ? SWEEP : -SWEEP, inward ? LEAVE_FROM : 0, inward ? 0 : ENTER_TO, life, MOTE_SIZE,
                MOTE_COLOR[c], MOTE_COLOR[c + GREEN], MOTE_COLOR[c + BLUE]);
        }
    }

    private void advanceArrival(float dt) {
        arriveClock += dt;
        if (!started && arriveClock >= ENTER_DELAY_SECONDS) {
            started = true;
            begin(arriveX, arriveY, false);
        }
        if (arriveClock >= ENTER_DELAY_SECONDS + REVEAL_SECONDS) arriving = false;
    }

    public void update(float dt) {
        if (arriving) advanceArrival(dt);
        if (queued) {
            boolean any = false;
            for (int k = 0; k < RINGS; k++) {
                if (wait[k] < 0) continue;
                wait[k] -= dt;
                if (wait[k] <= 0) { rings.start(x, y, RING_LIFE, RING_RADIUS + k * RING_STEP, RING_WIDTH); wait[k] = -1; }
                else any = true;
            }
            queued = any;
        }
        rings.update(dt);
        motes.update(dt);
        if (distorting) distortion.update(dt);
    }

    /** Rings, then the motes over them. */
    public void render(Batch batch) {
        rings.render(batch);
        if (motes.count() > 0) motes.render(batch, Shaders.whiteQuad());
    }

    public void clear() {
        rings.clear();
        motes.clear();
        queued = false;
        distorting = false;
        arriving = false;
    }
}
