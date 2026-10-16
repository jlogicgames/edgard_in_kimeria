package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SavedProgressTest {
    @TempDir Path directory;

    private Preferences preferences() {
        return new HeadlessPreferences(new FileHandle(directory.resolve("settings.xml").toFile()));
    }

    @Test
    void freshRunHasNoSaveAndCannotContinue() {
        SavedProgress progress = new SavedProgress(preferences(), 2);
        assertFalse(progress.exists());
        assertThrows(IllegalStateException.class, progress::nextLevelIndex);
    }

    @Test
    void progressSurvivesRelaunchAndNewGameClearsOnlyTheSave() {
        Preferences prefs = preferences();
        prefs.putBoolean(Settings.KEY_LEFT_HANDED, true);
        SavedProgress progress = new SavedProgress(prefs, 2);
        progress.save(1);
        SavedProgress relaunched = new SavedProgress(preferences(), 2);
        assertTrue(relaunched.exists());
        assertEquals(1, relaunched.nextLevelIndex());
        assertEquals(2, preferences().get().size()); // Setting + level; no coins or artifacts.
        relaunched.clear();
        assertFalse(new SavedProgress(preferences(), 2).exists());
        assertTrue(preferences().getBoolean(Settings.KEY_LEFT_HANDED));
    }

    @Test
    void removedNegativeAndMalformedLevelsHideContinue() {
        Preferences prefs = preferences();
        for (int index : new int[]{-1, 2, 99}) {
            prefs.putInteger(Settings.KEY_NEXT_LEVEL, index);
            assertFalse(new SavedProgress(prefs, 2).exists());
        }
        prefs.putString(Settings.KEY_NEXT_LEVEL, "broken");
        assertFalse(new SavedProgress(prefs, 2).exists());
    }

    @Test
    void levelCompletionPersistsNextLevelAndWrapWhileDeathLeavesItUntouched() throws Exception {
        SavedProgress progress = new SavedProgress(preferences(), 2);
        KimeriaGame game = new KimeriaGame();
        Field assets = KimeriaGame.class.getDeclaredField("assets");
        assets.setAccessible(true);
        assets.set(game, new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                return new TextureRegion();
            }
        });
        Field save = KimeriaGame.class.getDeclaredField("savedProgress");
        save.setAccessible(true);
        save.set(game, progress);
        game.loadNextLevel();
        assertEquals(1, new SavedProgress(preferences(), 2).nextLevelIndex());
        game.resetRoom();
        assertEquals(1, new SavedProgress(preferences(), 2).nextLevelIndex());
        game.loadNextLevel();
        assertEquals(0, new SavedProgress(preferences(), 2).nextLevelIndex());
    }
}
