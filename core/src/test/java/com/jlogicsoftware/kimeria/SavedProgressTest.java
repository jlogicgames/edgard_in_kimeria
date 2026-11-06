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
    void savesFromTheChickenAndKeyEraStillLoadBecauseIngredientsAreRunOnly() {
        // Ingredients were never persisted (#229): a pre-Bat/Mushroom save holds only the next level,
        // so it needs no mapping. Even stray ingredient-like keys are ignored, not interpreted.
        Preferences prefs = preferences();
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, 1);
        prefs.putBoolean("chicken", true);
        prefs.putBoolean("key", true);
        prefs.flush();
        SavedProgress progress = new SavedProgress(preferences(), 3);
        assertTrue(progress.exists());
        assertEquals(2, progress.nextLevelIndex()); // The existing forest-0 index shift, nothing ingredient-related.
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
        assertEquals(3, preferences().get().size()); // Setting + level + order key; no coins or artifacts.
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
    void levelCompletionPersistsNextLevelWhileDeathLeavesItUntouched() throws Exception {
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

    }

    @Test
    void savesWithoutAnOrderKeyPredateForestZeroAndShiftToTheSameLevel() {
        // Original order: 0 = forest-1, 1 = forest-2 (then named forest-3). Current order: 0 = forest-0, 1 = forest-1, 2 = forest-2.
        Preferences prefs = preferences();
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, 1); // "Continue at the boss level"
        assertEquals(2, new SavedProgress(prefs, 3).nextLevelIndex());
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, 0);
        assertEquals(1, new SavedProgress(prefs, 3).nextLevelIndex(), "Old forest-1 stays forest-1, not the tutorial");
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, -1);
        assertFalse(new SavedProgress(prefs, 3).exists());
    }

    @Test
    void saveStampsTheOrderSoItIsNotShiftedTwice() {
        Preferences prefs = preferences();
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, 1);
        SavedProgress progress = new SavedProgress(prefs, 3);
        progress.save(2);
        assertEquals(2, new SavedProgress(preferences(), 3).nextLevelIndex());
        progress.save(1);
        assertEquals(1, new SavedProgress(preferences(), 3).nextLevelIndex());
        progress.clear();
        assertFalse(preferences().contains(Settings.KEY_LEVEL_ORDER));
    }

    @Test
    void corruptedOrNegativeValueWithAnOrderKeyHidesContinue() {
        Preferences prefs = preferences();
        prefs.putInteger(Settings.KEY_LEVEL_ORDER, Settings.LEVEL_ORDER);
        for (int index : new int[]{-1, -7, 3, 99}) {
            prefs.putInteger(Settings.KEY_NEXT_LEVEL, index);
            assertFalse(new SavedProgress(prefs, 3).exists(), "index " + index);
        }
        prefs.putString(Settings.KEY_NEXT_LEVEL, "broken");
        assertFalse(new SavedProgress(prefs, 3).exists());
    }

    @Test
    void legacyBossSaveContinuesAtForestThreeThroughTheGamesLevelList() throws Exception {
        Preferences prefs = preferences();
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, 1); // Written when the list was forest-1, forest-3 (now forest-2).
        var names = KimeriaGame.class.getDeclaredField("LEVEL_NAMES");
        names.setAccessible(true);
        @SuppressWarnings("unchecked") var levels = (java.util.List<String>) names.get(null);
        SavedProgress progress = new SavedProgress(prefs, levels.size());
        assertEquals("forest-2", levels.get(progress.nextLevelIndex()));
        prefs.putInteger(Settings.KEY_NEXT_LEVEL, 0);
        assertEquals("forest-1", levels.get(new SavedProgress(prefs, levels.size()).nextLevelIndex()));
    }
}
