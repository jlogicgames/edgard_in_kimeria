package com.jlogicsoftware.kimeria;

/**
 * Names and defaults for persisted player settings. Shared between the game
 * and the desktop launcher, which has to read the fullscreen choice before
 * libGDX (and so {@code Gdx.app.getPreferences}) exists.
 */
public final class Settings {
    public static final String PREFS_NAME = "edgard-in-kimeria";
    public static final String KEY_FULLSCREEN = "fullscreen";
    public static final boolean DEFAULT_FULLSCREEN = true;

    /** Size of the window when the player opts out of fullscreen. */
    public static final int WINDOWED_WIDTH = 1280, WINDOWED_HEIGHT = 720;

    private Settings() {
    }
}
