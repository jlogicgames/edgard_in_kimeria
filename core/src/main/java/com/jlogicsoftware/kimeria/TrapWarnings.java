package com.jlogicsoftware.kimeria;

/**
 * One source of truth for timed trap warnings (D7/D11). Every trap passes its
 * base warning through {@link #duration(float)} when the warning starts.
 * Movement, cooldowns and lethal contact without a timed warning are not scaled.
 */
public final class TrapWarnings {
    public static final float FALLING_PLATFORM_SECONDS = 1f;
    /** Default warning before a trap's lethal event; reusable by new traps. */
    public static final float DEFAULT_WARNING_SECONDS = 0.8f;
    /** The forest spider's bite wind-up (D102): shorter than the default, since its target is locked and the dodge is one step. */
    public static final float BITE_WARNING_SECONDS = 0.5f;
    /** The forest boss's special attacks (#625, D157): the warning of each falling rock, of the roots and of the blow. */
    public static final float BOSS_ROCKS_WARNING_SECONDS = 1f, BOSS_ROOTS_WARNING_SECONDS = 0.7f, BOSS_BLOW_WARNING_SECONDS = 0.9f;
    /**
     * The forest wolf's other tells (D158): reading a jump, gathering a jump bite, coiling away from a swing. The assist turns
     * those moves off instead of lengthening them; the coil before a leap on its route is movement, never scaled.
     */
    public static final float WOLF_READ_SECONDS = 0.25f, WOLF_POUNCE_SECONDS = 0.25f, WOLF_FLINCH_SECONDS = 0.1f, WOLF_LEAP_SECONDS = 0.3f;
    private static final float ASSIST_MULTIPLIER = 1.5f;
    private boolean longer = Settings.DEFAULT_LONGER_TRAP_WARNINGS;

    public void load(Settings settings) {
        longer = settings.get(Settings.LONGER_TRAP_WARNINGS);
    }

    public boolean longer() {
        return longer;
    }

    /** Toggle only this assist, persisting it alongside the other player settings. */
    public void toggle(Settings settings) {
        longer = !longer;
        settings.set(Settings.LONGER_TRAP_WARNINGS, longer);
    }

    public float duration(float baseSeconds) {
        return baseSeconds * (longer ? ASSIST_MULTIPLIER : 1f);
    }

    /** The longest the assist can make a warning, for authoring checks that must hold with it on. */
    public static float longestDuration(float baseSeconds) {
        return baseSeconds * ASSIST_MULTIPLIER;
    }
}
