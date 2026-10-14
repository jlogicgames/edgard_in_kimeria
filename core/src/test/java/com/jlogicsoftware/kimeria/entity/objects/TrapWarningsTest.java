package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.Settings;
import com.jlogicsoftware.kimeria.TrapWarnings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TrapWarningsTest {
    @TempDir Path directory;

    private Preferences preferences() {
        return new HeadlessPreferences(new FileHandle(directory.resolve("settings.xml").toFile()));
    }

    @Test
    void absentSettingKeepsTheStandardWarning() {
        TrapWarnings warnings = new TrapWarnings();
        warnings.load(preferences());
        assertFalse(warnings.longer());
        assertEquals(1f, warnings.duration(TrapWarnings.FALLING_PLATFORM_SECONDS));
    }

    @Test
    void assistPersistsAcrossLaunchesAndCanBeTurnedOffWithoutChangingOtherSettings() {
        Preferences prefs = preferences();
        prefs.putBoolean(Settings.KEY_LEFT_HANDED, true);
        TrapWarnings warnings = new TrapWarnings();
        warnings.load(prefs);
        warnings.toggle(prefs);

        TrapWarnings relaunched = new TrapWarnings();
        relaunched.load(preferences());
        assertTrue(relaunched.longer());
        assertEquals(1.5f, relaunched.duration(1f));
        assertEquals(3f, relaunched.duration(2f));
        relaunched.toggle(preferences());

        TrapWarnings afterDisable = new TrapWarnings();
        afterDisable.load(preferences());
        assertFalse(afterDisable.longer());
        assertEquals(2f, afterDisable.duration(2f));
        assertTrue(preferences().getBoolean(Settings.KEY_LEFT_HANDED));
    }

    @Test
    void platformsUseTheSharedWarningAndRepeatedContactDoesNotRestartIt() {
        TrapWarnings warnings = new TrapWarnings();
        FallingPlatform normal = platform(warnings);
        warnings.toggle(preferences());
        FallingPlatform assisted = platform(warnings);
        assisted.collideWithActor();
        normal.update(1f);
        assisted.update(1f);
        assisted.collideWithActor();
        normal.update(0.25f);
        assisted.update(0.25f);
        assertTrue(normal.getY() > 100f);
        assertEquals(100f, assisted.getY());
        assisted.update(0.25f);
        assisted.update(0.1f);
        assertTrue(assisted.getY() > 100f);
    }

    private FallingPlatform platform(TrapWarnings warnings) {
        // Animation frames without textures let the real platform update run without OpenGL.
        Assets assets = new Assets() {
            @Override
            public Animation<TextureRegion> animation(String path, int amount, float stepTime,
                    float frameW, float frameH, float originX, float originY, int amountPerRow) {
                return new Animation<>(stepTime, new TextureRegion());
            }
        };
        FallingPlatform platform = new FallingPlatform(assets, 0f, 100f, 32f, 10f,
            object -> {}, warnings);
        platform.collideWithActor();
        return platform;
    }
}
