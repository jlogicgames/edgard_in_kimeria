package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Preferences;
import com.jlogicsoftware.kimeria.localization.Language;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Names and defaults for persisted player settings. Shared between the game
 * and the desktop launcher, which has to read the fullscreen choice before
 * libGDX (and so {@code Gdx.app.getPreferences}) exists.
 */
public final class Settings {
    public static final String PREFS_NAME = "edgard-in-kimeria";
    /** Zero-based next level; absence means no saved run. */
    public static final String KEY_NEXT_LEVEL = "next-level";
    public static final String KEY_FULLSCREEN = "fullscreen";
    public static final boolean DEFAULT_FULLSCREEN = true;

    /** Mirrors the touch layout and swaps the keyboard bindings for the left-hand set. */
    public static final String KEY_LEFT_HANDED = "left-handed";
    public static final boolean DEFAULT_LEFT_HANDED = false;

    /** Optional assist; standard trap warning times remain the default. */
    public static final String KEY_LONGER_TRAP_WARNINGS = "longer-trap-warnings";
    public static final boolean DEFAULT_LONGER_TRAP_WARNINGS = false;

    /** Size of the window when the player opts out of fullscreen. */
    public static final int WINDOWED_WIDTH = 1280, WINDOWED_HEIGHT = 720;

    public static final String KEY_LANGUAGE = "language";
    /** First launch and unknown languages use English, independent of the system locale. */
    public static final Language DEFAULT_LANGUAGE = Language.ENGLISH;

    public static final Option<Language> LANGUAGE = new Option<>(KEY_LANGUAGE, DEFAULT_LANGUAGE,
        value -> Language.valueOf((String) value));
    public static final Option<Boolean> FULLSCREEN = bool(KEY_FULLSCREEN, DEFAULT_FULLSCREEN);
    public static final Option<Boolean> LEFT_HANDED = bool(KEY_LEFT_HANDED, DEFAULT_LEFT_HANDED);
    public static final Option<Boolean> LONGER_TRAP_WARNINGS =
        bool(KEY_LONGER_TRAP_WARNINGS, DEFAULT_LONGER_TRAP_WARNINGS);

    /** A typed key and its default/decoder; new options need no load/save wiring. */
    public static final class Option<T> {
        private final String key;
        private final T fallback;
        private final Function<Object, T> decode;

        private Option(String key, T fallback, Function<Object, T> decode) {
            this.key = key;
            this.fallback = fallback;
            this.decode = decode;
        }
    }

    private final Preferences preferences;
    private final Map<String, Object> values = new HashMap<>();

    /** Load a snapshot once. The factory also works before Gdx.app exists on desktop. */
    public static Settings load(Supplier<Preferences> factory) {
        Preferences prefs;
        try {
            prefs = factory.get();
        } catch (RuntimeException failure) {
            System.err.println("Player preferences unavailable: " + failure.getMessage());
            prefs = null;
        }
        return new Settings(prefs);
    }

    public Settings(Preferences preferences) {
        Preferences readable = preferences;
        try {
            if (preferences != null) values.putAll(preferences.get());
        } catch (RuntimeException failure) {
            System.err.println("Cannot read player settings; using defaults: " + failure.getMessage());
            readable = null;
        }
        this.preferences = readable;
    }

    /** The same backend is shared with SavedProgress; null means storage is unavailable. */
    public Preferences preferences() {
        return preferences;
    }

    public <T> T get(Option<T> option) {
        Object value = values.get(option.key);
        if (value == null) return option.fallback;
        try {
            return option.decode.apply(value);
        } catch (RuntimeException invalidValue) {
            return option.fallback;
        }
    }

    /** Apply immediately in memory and flush only this key, preserving saved progress. */
    public <T> void set(Option<T> option, T value) {
        if (value == null) throw new IllegalArgumentException("A setting cannot be null");
        Object stored = value instanceof Enum<?> ? ((Enum<?>) value).name() : value;
        values.put(option.key, stored);
        if (preferences == null) return;
        try {
            preferences.put(Map.of(option.key, stored));
            preferences.flush();
        } catch (RuntimeException failure) {
            System.err.println("Cannot persist player settings: " + failure.getMessage());
        }
    }

    private static Option<Boolean> bool(String key, boolean fallback) {
        return new Option<>(key, fallback, value -> {
            // Desktop/mobile backends may return strings, while web returns typed values.
            if (Boolean.TRUE.equals(value) || "true".equals(value)) return true;
            if (Boolean.FALSE.equals(value) || "false".equals(value)) return false;
            throw new IllegalArgumentException("Invalid boolean setting");
        });
    }
}
