package com.jlogicsoftware.kimeria.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.jlogicsoftware.kimeria.KimeriaGame;
import com.jlogicsoftware.kimeria.Settings;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Edgard in Kimeria");
        config.useVsync(true);
        config.setForegroundFPS(60);
        // Fullscreen unless the player opted out in Options. The choice is read straight
        // from the preferences file the game writes, since Gdx.app doesn't exist yet.
        boolean fullscreen = new Lwjgl3Preferences(Settings.PREFS_NAME, ".prefs/")
            .getBoolean(Settings.KEY_FULLSCREEN, Settings.DEFAULT_FULLSCREEN);
        if (fullscreen) {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            config.setWindowedMode(Settings.WINDOWED_WIDTH, Settings.WINDOWED_HEIGHT);
        }
        config.setResizable(true);
        new Lwjgl3Application(new KimeriaGame(), config);
    }
}
