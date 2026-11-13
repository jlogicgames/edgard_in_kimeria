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
        boolean fullscreen = Settings.load(() -> new Lwjgl3Preferences(Settings.PREFS_NAME, ".prefs/"))
            .get(Settings.FULLSCREEN);
        if (fullscreen) {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            config.setWindowedMode(Settings.WINDOWED_WIDTH, Settings.WINDOWED_HEIGHT);
        }
        config.setResizable(true);
        KimeriaGame game = new KimeriaGame(false, Boolean.getBoolean("kimeria.debug"));
        // The Lwjgl3 audio config (sources, buffer size/count) is left at its defaults: buffer size and count only apply to
        // AudioDevice streaming, not to Sound, whose latency is the output device's wake-up time (#316).
        game.setAudioKeepAlive(!Boolean.getBoolean("kimeria.nokeepalive"));
        new Lwjgl3Application(game, config);
    }
}
