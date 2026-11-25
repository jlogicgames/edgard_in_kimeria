package com.jlogicsoftware.kimeria.ui;

/**
 * The touch tutorial (#496, D139): when and how long a hand, a ghost knob or a ring shows the player how to use the stick and the
 * buttons. Pure state and timers, no drawing and no libGDX: {@link TouchControls} feeds it once a frame ({@link #update}) and draws
 * what it reports, because only TouchControls knows where the stick and buttons are in the portrait strip, the side bars, over the
 * picture and left-handed. Nothing here allocates; {@link Signals} is one reused object.
 *
 * <p>Three steps, authored by the {@code coach} property of a {@code Hint} zone in the map:
 * <ul>
 * <li>{@link Step#STICK_DRAG}: in the first room, after {@value #APPEAR_DELAY} s of standing still a hand presses the knob, drags it
 * {@value #SLIDE_UNITS} units to the screen right with amber chevrons, releases, pauses and loops ({@value #CYCLE} s). It ends for
 * the session when the real stick is touched or the hero has walked {@value #WALKED_UNITS} px.</li>
 * <li>{@link Step#STICK_UP}: the same loop pushing the knob up, while a lever (or anything hand-operated) is in reach. It ends when
 * the hero uses it.</li>
 * <li>{@link Step#RANGED}: a pulsing ring round the Ranged button while the hero carries something. It ends on a throw, or when
 * the hero walks out of the zone; a death and respawn (he reappears outside it, empty-handed) does not end it, it waits for him to return.</li>
 * </ul>
 * A step that has ended never returns in the same session (this object lives as long as the game does; saves are level-only, so a
 * new session shows step 1 again). A keyboard or a gamepad moving the hero ends the whole coach for good: a hybrid laptop's
 * player is not a touch player ({@link #nonTouchSeen()}).
 */
public final class TouchCoach {
    /** What the coach can show; {@link #property()} is the value of a Hint's {@code coach} property. */
    public enum Step {
        STICK_DRAG("stick-drag"), STICK_UP("stick-up"), RANGED("ranged");

        private final String property;

        Step(String property) { this.property = property; }

        public String property() { return property; }

        /** The step a map value names; a mistyped value fails at load. */
        public static Step parse(String value) {
            for (Step step : values()) if (step.property.equals(value)) return step;
            throw new IllegalArgumentException("Unknown coach step '" + value + "' (stick-drag, stick-up or ranged)");
        }

        int bit() { return 1 << ordinal(); }
    }

    /** What the game tells the coach each frame. One instance, cleared and refilled, never reallocated. */
    public static final class Signals {
        /** One bit per {@link Step} whose zone holds the hero ({@link #zone(Step)}). */
        public int zones;
        /** The hero's x, in world units. */
        public float heroX;
        /** A keyboard or a gamepad is moving the hero this frame. */
        public boolean nonTouchMoved;
        /** A lever or another hand-operated object is in the hero's reach. */
        public boolean leverInReach;
        /** The hero carries something (a stone). */
        public boolean carrying;
        /** The hero is dying, being reset or arriving (D81): a respawn carries him out of the zone, which is not his choice. */
        public boolean respawning;
        /** A throw was pressed this frame, on any device. */
        public boolean threw;
        /** An interaction (a fresh Up) was pressed this frame, on any device. */
        public boolean interacted;

        public void clear() {
            zones = 0; heroX = 0f;
            nonTouchMoved = leverInReach = carrying = respawning = threw = interacted = false;
        }

        public void zone(Step step) { zones |= step.bit(); }
    }

    /** The hero must stand still this long (or the carried stone / the lever be in reach) before anything shows. */
    public static final float APPEAR_DELAY = 0.35f;
    /** One loop of the stick hand: fade in, press, drag, release, pause. */
    public static final float CYCLE = 1.6f;
    /** Phase ends within a loop, in seconds: the hand fades in, presses, drags, releases, then a pause with the hand fading out. */
    public static final float FADE_IN_END = 0.25f, PRESS_END = 0.40f, DRAG_END = 1.05f, RELEASE_END = 1.20f, FADE_OUT_END = 1.40f;
    /** The ghost knob's drag, in controls units. */
    public static final float SLIDE_UNITS = 36f;
    /** Walked this far from where the first step began and the first step is over. */
    public static final float WALKED_UNITS = 96f;
    /** One pulse of the Ranged ring, in seconds. */
    public static final float PULSE_PERIOD = 1.2f;
    /** The ring fades in over this long, so it never pops. */
    public static final float RING_FADE_IN = 0.2f;
    /** The chevrons fade in over this long once the drag begins. */
    private static final float CHEVRON_FADE_IN = 0.15f;
    /** The ring gets this much fainter by the end of each pulse (it grows as it fades). */
    private static final float RING_PULSE_DIP = 0.55f;
    /** Smoothstep 3u^2 - 2u^3: the drag eases in and out. */
    private static final float SMOOTHSTEP_CUBIC = 3f, SMOOTHSTEP_QUADRATIC = 2f;
    /** Less than this is standing still (world units per frame). */
    private static final float STILL = 0.01f;

    private final boolean[] over = new boolean[Step.values().length];
    private Step candidate;
    private float dwell, lastX, anchorX;
    private boolean placed, anchored, nonTouchSeen;
    private Step shownOnce;

    /**
     * @param dt            seconds this frame (0 while paused: the caller does not call during pauses)
     * @param touchDevice   the device can be touched at all
     * @param stickHeld     a finger is on the real stick
     * @param signals       what the game reports this frame
     */
    public void update(float dt, boolean touchDevice, boolean stickHeld, Signals signals) {
        if (!touchDevice) { candidate = null; dwell = 0f; return; }
        if (signals.nonTouchMoved) nonTouchSeen = true;
        if (nonTouchSeen) { candidate = null; dwell = 0f; return; }
        boolean still = !placed || Math.abs(signals.heroX - lastX) < STILL;
        lastX = signals.heroX;
        placed = true;

        boolean inDrag = (signals.zones & Step.STICK_DRAG.bit()) != 0;
        if (!over[Step.STICK_DRAG.ordinal()]) {
            if (inDrag && !anchored) { anchored = true; anchorX = signals.heroX; }
            if (stickHeld || (anchored && Math.abs(signals.heroX - anchorX) >= WALKED_UNITS)) over[Step.STICK_DRAG.ordinal()] = true;
        }
        boolean inUp = (signals.zones & Step.STICK_UP.bit()) != 0;
        if (inUp && signals.interacted && signals.leverInReach) over[Step.STICK_UP.ordinal()] = true;
        boolean inRanged = (signals.zones & Step.RANGED.bit()) != 0;
        if (signals.respawning) shownOnce = null; // a respawn is no way out of the zone: the ring waits for the stone again
        if (shownOnce == Step.RANGED && !inRanged) over[Step.RANGED.ordinal()] = true;
        if (inRanged && signals.threw) over[Step.RANGED.ordinal()] = true;

        Step next = null;
        if (inDrag && !over[Step.STICK_DRAG.ordinal()] && still) next = Step.STICK_DRAG;
        else if (inUp && !over[Step.STICK_UP.ordinal()] && signals.leverInReach && !stickHeld) next = Step.STICK_UP;
        else if (inRanged && !over[Step.RANGED.ordinal()] && signals.carrying && !signals.respawning) next = Step.RANGED;
        if (next != candidate) { candidate = next; dwell = 0f; }
        if (candidate != null) {
            dwell += dt;
            if (dwell >= APPEAR_DELAY && candidate == Step.RANGED) shownOnce = Step.RANGED;
        }
    }

    /** The step on screen now (its delay has passed), or null. */
    public Step step() { return candidate != null && dwell >= APPEAR_DELAY ? candidate : null; }

    /** True when a step has ended for the session. */
    public boolean over(Step step) { return over[step.ordinal()]; }

    /** True once a keyboard or a gamepad has moved the hero: this player is not guided by touch any more. */
    public boolean nonTouchSeen() { return nonTouchSeen; }

    /** Seconds into the current step, from the moment it appeared. */
    public float time() { return Math.max(0f, dwell - APPEAR_DELAY); }

    private float loop() { return time() % CYCLE; }

    /** 0 to 1: how visible the hand and ghost knob are in this part of the loop (0 when the step is not a stick step). */
    public float handAlpha() {
        if (!stickStep()) return 0f;
        float t = loop();
        if (t < FADE_IN_END) return t / FADE_IN_END;
        if (t < RELEASE_END) return 1f;
        if (t < FADE_OUT_END) return 1f - (t - RELEASE_END) / (FADE_OUT_END - RELEASE_END);
        return 0f;
    }

    /** True while the hand presses the knob (the pressed hand and the pressed ghost knob). */
    public boolean pressed() {
        if (!stickStep()) return false;
        float t = loop();
        return t >= FADE_IN_END && t < DRAG_END;
    }

    /** 0 to 1: how far the ghost knob has been dragged (eased); it stays at 1 through the release. */
    public float slide() {
        if (!stickStep()) return 0f;
        float t = loop();
        if (t < PRESS_END) return 0f;
        if (t >= DRAG_END) return 1f;
        float u = (t - PRESS_END) / (DRAG_END - PRESS_END);
        return u * u * (SMOOTHSTEP_CUBIC - SMOOTHSTEP_QUADRATIC * u);
    }

    /** 0 to 1: the amber chevrons, shown once the drag has begun and gone with the release. */
    public float chevronAlpha() {
        if (!stickStep()) return 0f;
        float t = loop();
        if (t < PRESS_END || t >= DRAG_END) return 0f;
        return Math.min(1f, (t - PRESS_END) / CHEVRON_FADE_IN);
    }

    /** 0 to 1 over each pulse of the Ranged ring (0 when it is not the Ranged step). */
    public float pulse() {
        return step() == Step.RANGED ? (time() % PULSE_PERIOD) / PULSE_PERIOD : 0f;
    }

    /** 0 to 1: the Ranged ring fades in once and then breathes with {@link #pulse()}. */
    public float ringAlpha() {
        if (step() != Step.RANGED) return 0f;
        float fade = Math.min(1f, time() / RING_FADE_IN);
        return fade * (1f - RING_PULSE_DIP * pulse());
    }

    private boolean stickStep() {
        Step step = step();
        return step == Step.STICK_DRAG || step == Step.STICK_UP;
    }
}
