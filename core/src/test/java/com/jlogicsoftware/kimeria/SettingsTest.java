package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.jlogicsoftware.kimeria.localization.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir Path directory;

    private Preferences preferences() {
        return new HeadlessPreferences(new FileHandle(directory.resolve("settings.xml").toFile()));
    }

    @Test void fiveLanguagesCycleAndSurviveRelaunchWithLegacyNamesUnchanged() {
        assertEquals("ENGLISH", Language.ENGLISH.name());
        assertEquals("UKRAINIAN", Language.UKRAINIAN.name());
        Language next = Language.ENGLISH;
        for (Language expected : Language.values()) {
            assertEquals(expected, next);
            Settings settings = new Settings(preferences());
            settings.set(Settings.LANGUAGE, expected);
            assertEquals(expected, Settings.load(this::preferences).get(Settings.LANGUAGE));
            assertEquals(expected.name(), preferences().getString(Settings.KEY_LANGUAGE));
            next = next.next();
        }
        assertEquals(Language.ENGLISH, next);
    }

    @Test
    void firstLaunchUsesDocumentedDefaults() {
        Settings settings = new Settings(preferences());
        assertEquals(Language.ENGLISH, settings.get(Settings.LANGUAGE));
        assertTrue(settings.get(Settings.FULLSCREEN));
        assertFalse(settings.get(Settings.LEFT_HANDED));
        assertFalse(settings.get(Settings.LONGER_TRAP_WARNINGS));
        assertTrue(preferences().get().isEmpty());
    }

    @Test
    void allOptionsSurviveRelaunchAndKeepProgress() {
        Preferences prefs = preferences();
        new SavedProgress(prefs, 2).save(1);
        Settings settings = new Settings(prefs);
        settings.set(Settings.LANGUAGE, Language.ENGLISH.next());
        settings.set(Settings.FULLSCREEN, false);
        settings.set(Settings.LEFT_HANDED, true);
        settings.set(Settings.LONGER_TRAP_WARNINGS, true);

        Settings relaunched = Settings.load(this::preferences);
        assertEquals(Language.ENGLISH.next(), relaunched.get(Settings.LANGUAGE));
        assertEquals(Language.ENGLISH.next().name(), preferences().getString(Settings.KEY_LANGUAGE));
        assertFalse(relaunched.get(Settings.FULLSCREEN));
        assertTrue(relaunched.get(Settings.LEFT_HANDED));
        assertTrue(relaunched.get(Settings.LONGER_TRAP_WARNINGS));
        SavedProgress progress = new SavedProgress(preferences(), 2);
        assertEquals(1, progress.nextLevelIndex());
        progress.clear();
        assertEquals(Language.ENGLISH.next(), new Settings(preferences()).get(Settings.LANGUAGE));
    }

    @Test
    void invalidValuesFallBackIndependentlyAndCanBeReplaced() {
        Preferences prefs = preferences();
        prefs.putString(Settings.KEY_LANGUAGE, "REMOVED_LANGUAGE");
        prefs.putString(Settings.KEY_FULLSCREEN, "not-a-boolean");
        prefs.putInteger(Settings.KEY_LEFT_HANDED, 3);
        prefs.putBoolean(Settings.KEY_LONGER_TRAP_WARNINGS, true);
        Settings settings = new Settings(prefs);
        assertEquals(Language.ENGLISH, settings.get(Settings.LANGUAGE));
        assertTrue(settings.get(Settings.FULLSCREEN));
        assertFalse(settings.get(Settings.LEFT_HANDED));
        assertTrue(settings.get(Settings.LONGER_TRAP_WARNINGS));
        settings.set(Settings.LANGUAGE, Language.ENGLISH.next());
        assertEquals(Language.ENGLISH.next(), new Settings(preferences()).get(Settings.LANGUAGE));
    }

    @Test
    void corruptFileDoesNotPreventStartupOrLaterChanges() throws Exception {
        Files.writeString(directory.resolve("settings.xml"), "not valid XML");
        Settings settings = Settings.load(this::preferences);
        assertEquals(Language.ENGLISH, settings.get(Settings.LANGUAGE));
        assertTrue(settings.get(Settings.FULLSCREEN));
        settings.set(Settings.LANGUAGE, Language.ENGLISH.next());
        assertEquals(Language.ENGLISH.next(), new Settings(preferences()).get(Settings.LANGUAGE));
    }

    @Test
    void storageAcquisitionReadAndWriteFailuresDoNotCrashSettings() {
        Settings unavailable = Settings.load(() -> { throw new IllegalStateException("unavailable"); });
        unavailable.set(Settings.LEFT_HANDED, true);
        assertTrue(unavailable.get(Settings.LEFT_HANDED));
        assertFalse(new SavedProgress(unavailable.preferences(), 2).exists());

        Preferences failing = (Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(),
            new Class<?>[]{Preferences.class}, (proxy, method, args) -> {
                throw new IllegalStateException("storage blocked");
            });
        Settings unreadable = new Settings(failing);
        assertEquals(Language.ENGLISH, unreadable.get(Settings.LANGUAGE));
        assertDoesNotThrow(() -> unreadable.set(Settings.LANGUAGE, Language.ENGLISH.next()));
        assertEquals(Language.ENGLISH.next(), unreadable.get(Settings.LANGUAGE));
        assertFalse(new SavedProgress(unreadable.preferences(), 2).exists());

        for (String failedMethod : new String[]{"put", "flush"}) {
            Preferences unwritable = (Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(),
                new Class<?>[]{Preferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) return Map.of();
                    if (method.getName().equals(failedMethod)) throw new IllegalStateException("disk full");
                    return proxy;
                });
            Settings settings = new Settings(unwritable);
            assertDoesNotThrow(() -> settings.set(Settings.LEFT_HANDED, true));
            assertTrue(settings.get(Settings.LEFT_HANDED));
        }
    }

    @Test
    void typedWebValuesAndDesktopStringsHaveTheSameMeaning() {
        for (Object value : new Object[]{true, "true", false, "false"}) {
            Preferences backend = (Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(),
                new Class<?>[]{Preferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) return Map.of(Settings.KEY_FULLSCREEN, value);
                    throw new AssertionError("Settings should load just one snapshot");
                });
            Settings settings = new Settings(backend);
            assertEquals(Boolean.parseBoolean(value.toString()), settings.get(Settings.FULLSCREEN));
            assertEquals(Boolean.parseBoolean(value.toString()), settings.get(Settings.FULLSCREEN));
        }
    }
}
