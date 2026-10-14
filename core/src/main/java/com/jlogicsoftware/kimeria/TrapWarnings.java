package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Preferences;

/**
 * One source of truth for timed trap warnings (D7/D11). Every trap passes its
 * base warning through {@link #duration(float)} when the warning starts.
 * Movement, cooldowns and lethal contact without a timed warning are not scaled.
 */
public final class TrapWarnings {
    public static final float FALLING_PLATFORM_SECONDS = 1f;
    /** Default warning before a trap's lethal event; reusable by new traps. */
    public static final float DEFAULT_WARNING_SECONDS = 0.8f;
    private static final float ASSIST_MULTIPLIER = 1.5f;
    private boolean longer = Settings.DEFAULT_LONGER_TRAP_WARNINGS;

    public void load(Preferences prefs) {
        longer = prefs.getBoolean(Settings.KEY_LONGER_TRAP_WARNINGS,
            Settings.DEFAULT_LONGER_TRAP_WARNINGS);
    }

    public boolean longer() {
        return longer;
    }

    /** Toggle only this assist, persisting it alongside the other player settings. */
    public void toggle(Preferences prefs) {
        longer = !longer;
        prefs.putBoolean(Settings.KEY_LONGER_TRAP_WARNINGS, longer);
        prefs.flush();
    }

    public float duration(float baseSeconds) {
        return baseSeconds * (longer ? ASSIST_MULTIPLIER : 1f);
    }
}
