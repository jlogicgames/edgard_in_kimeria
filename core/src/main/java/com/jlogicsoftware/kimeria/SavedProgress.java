package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Preferences;

/** Level-only progress using the platform's Preferences backend. */
public final class SavedProgress {
    /** Levels inserted before the original first level (forest-1) since saves had no order key. */
    static final int LEGACY_INDEX_SHIFT = 1;
    private final Preferences preferences;
    private final int levelCount;
    private int nextLevel = -1;

    public SavedProgress(Preferences preferences, int levelCount) {
        if (levelCount <= 0) throw new IllegalArgumentException("At least one level is required");
        this.preferences = preferences;
        this.levelCount = levelCount;
        reload();
    }

    /** Invalid or removed levels hide Continue, without modifying other preferences. */
    public void reload() {
        nextLevel = -1;
        if (preferences == null || !preferences.contains(Settings.KEY_NEXT_LEVEL)) return;
        try {
            int saved = preferences.getInteger(Settings.KEY_NEXT_LEVEL);
            // A save without a level-order key predates forest-0 (index 0 = forest-1, 1 = forest-2, then named forest-3):
            // every such index moves by the one level inserted in front.
            if (saved >= 0 && !preferences.contains(Settings.KEY_LEVEL_ORDER)) saved += LEGACY_INDEX_SHIFT;
            if (saved >= 0 && saved < levelCount) nextLevel = saved;
        } catch (ClassCastException | NumberFormatException ignored) {
            // A malformed value is not a playable save either.
        }
    }

    public boolean exists() {
        return nextLevel >= 0;
    }

    public int nextLevelIndex() {
        if (!exists()) throw new IllegalStateException("No playable save");
        return nextLevel;
    }

    public void save(int nextLevelIndex) {
        if (nextLevelIndex < 0 || nextLevelIndex >= levelCount) {
            throw new IllegalArgumentException("Invalid next level");
        }
        if (preferences != null) {
            preferences.putInteger(Settings.KEY_NEXT_LEVEL, nextLevelIndex);
            preferences.putInteger(Settings.KEY_LEVEL_ORDER, Settings.LEVEL_ORDER);
            preferences.flush();
        }
        nextLevel = nextLevelIndex;
    }

    /** New Game removes the old run; no new save exists until a level is completed. */
    public void clear() {
        if (preferences != null) {
            preferences.remove(Settings.KEY_NEXT_LEVEL);
            preferences.remove(Settings.KEY_LEVEL_ORDER);
            preferences.flush();
        }
        nextLevel = -1;
    }
}
