package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.HealerDialogue;
import com.jlogicsoftware.kimeria.ui.IntroScreen;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CampaignFlowTest {
    @TempDir Path directory;
    private final Set<Integer> held = new HashSet<>();
    private Input oldInput;
    private Files oldFiles;
    private Application oldApp;
    private KimeriaGame game;
    private SavedProgress progress;
    private HeadlessPreferences prefs;
    private float musicVolume;
    private int musicStarts, musicStops;

    private void set(String name, Object value) throws Exception {
        var field = KimeriaGame.class.getDeclaredField(name); field.setAccessible(true); field.set(game, value);
    }
    private Object get(String name) throws Exception {
        var field = KimeriaGame.class.getDeclaredField(name); field.setAccessible(true); return field.get(game);
    }
    private void call(String name) throws Exception {
        var method = KimeriaGame.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(game);
    }
    private void update(float dt) throws Exception {
        var method = KimeriaGame.class.getDeclaredMethod("updateStory", float.class);
        method.setAccessible(true); method.invoke(game, dt);
    }
    private String state() throws Exception { return get("uiState").toString(); }

    @BeforeEach void setup() throws Exception {
        oldInput = Gdx.input; oldFiles = Gdx.files; oldApp = Gdx.app;
        GdxNativesLoader.load(); Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},
            (p, m, a) -> m.getName().equals("isKeyPressed") ? held.contains((int) a[0])
                : m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class[]{Application.class},
            (p, m, a) -> m.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        game = new KimeriaGame();
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
            @Override public BitmapFont font(int size) { return null; }
        };
        set("assets", assets);
        set("overlay", new Overlay(assets, 640, 360) {
            @Override public float bodyHeight(String text, float width) { return 120; }
        });
        set("introMusic", Proxy.newProxyInstance(Music.class.getClassLoader(), new Class[]{Music.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "setVolume" -> musicVolume = (float) a[0];
                case "play" -> musicStarts++;
                case "stop" -> musicStops++;
            }
            return null;
        }));
        set("playSounds", true);
        prefs = new HeadlessPreferences(new FileHandle(directory.resolve("settings.xml").toFile()));
        prefs.putString(Settings.KEY_LANGUAGE, Language.UKRAINIAN.name());
        prefs.putBoolean(Settings.KEY_LEFT_HANDED, true); prefs.flush();
        progress = new SavedProgress(prefs, 3); set("savedProgress", progress); call("buildMenus");
    }

    @AfterEach void restore() { Gdx.input = oldInput; Gdx.files = oldFiles; Gdx.app = oldApp; }

    @Test void languageMenuChangesEveryLabelImmediatelyAndPersistsAllFiveChoices() throws Exception {
        set("settings", new Settings(prefs));
        set("language", Language.ENGLISH);
        Menu options = (Menu) get("optionsMenu");
        Language expected = Language.ENGLISH;
        for (int i = 0; i < Language.values().length; i++) {
            assertEquals(Msg.LANGUAGE_LABEL.t(expected) + ": " + expected.nativeName(), options.items.get(0).label.get());
            assertEquals(Msg.OPTIONS.t(expected), options.titleText());
            options.items.get(0).action.run();
            expected = expected.next();
            assertEquals(expected, get("language"));
            assertEquals(expected, new Settings(prefs).get(Settings.LANGUAGE));
            assertEquals(Msg.NEW_GAME.t(expected), ((Menu) get("mainMenu")).items.get(1).label.get());
        }
        assertEquals(Language.ENGLISH, expected);
    }

    private HealerDialogue healer() throws Exception { return (HealerDialogue) get("healer"); }
    /** Presses through the open scripted dialogue (reveal, then advance, per line) and applies CONTINUE once. */
    private void finishScripted() throws Exception {
        HealerDialogue dialogue = healer();
        Language language = (Language) get("language");
        HealerDialogue.Choice result = null;
        for (int press = 0; press < 40 && result == null; press++) result = dialogue.advance(language);
        assertEquals(HealerDialogue.Choice.CONTINUE, result);
        var method = KimeriaGame.class.getDeclaredMethod("chooseHealer", HealerDialogue.Choice.class);
        method.setAccessible(true); method.invoke(game, result);
    }
    private void startNewGameOnTutorial() throws Exception {
        call("newGame"); update(2); update(100); update(1);
        assertEquals("LOADING", state()); assertEquals(0, get("currentLevelIndex"));
        call("loadLevel"); call("beginLevelPlay");
    }

    @Test void newGameOpensTheStartTaleOnceAndFinishingItReturnsControl() throws Exception {
        startNewGameOnTutorial();
        assertEquals("HEALER", state()); assertFalse(game.isGameStarted());
        assertEquals(HealerDialogue.Kind.TUTORIAL_START, healer().kind());
        finishScripted();
        assertNull(get("healer")); assertEquals("PLAYING", state()); assertTrue(game.isGameStarted());
        assertFalse((boolean) get("tutorialIntroPending"));
    }

    @Test void startTaleNeverRepeatsOnRestartLevelDeathOrAnotherLoadOfTheSameRun() throws Exception {
        startNewGameOnTutorial(); finishScripted();
        game.resetRoom(); // death
        call("restartLevel"); assertEquals("LOADING", state());
        call("loadLevel"); call("beginLevelPlay");
        assertEquals("PLAYING", state()); assertNull(get("healer"));
        // Restart Level pressed while the tale is still open also consumes it.
        call("newGame"); update(2); update(100); update(1);
        call("loadLevel"); call("beginLevelPlay"); assertEquals("HEALER", state());
        call("restartLevel"); call("loadLevel"); call("beginLevelPlay");
        assertEquals("PLAYING", state()); assertNull(get("healer"));
    }

    @Test void continueAndLegacySavesNeverReplayTheStartTale() throws Exception {
        progress.save(1); call("continueGame");
        assertEquals(1, get("currentLevelIndex")); assertFalse((boolean) get("tutorialIntroPending"));
        // A save written before forest-0 existed carries no order key and resolves past the tutorial.
        HeadlessPreferences legacy = new HeadlessPreferences(new FileHandle(directory.resolve("legacy.xml").toFile()));
        legacy.putInteger(Settings.KEY_NEXT_LEVEL, 0); legacy.flush();
        SavedProgress old = new SavedProgress(legacy, 3);
        set("savedProgress", old); call("continueGame");
        assertEquals(1, get("currentLevelIndex")); assertFalse((boolean) get("tutorialIntroPending"));
        // Even a hypothetical save pointing at forest-0 is a continuation, not a new game.
        progress.save(0); set("savedProgress", progress); call("continueGame");
        assertEquals(0, get("currentLevelIndex")); assertFalse((boolean) get("tutorialIntroPending"));
    }

    @Test void endTaleOpensOnceBeforeForestOneAndSavesOnlyAfterItFinishes() throws Exception {
        startNewGameOnTutorial(); finishScripted();
        game.loadNextLevel();
        assertEquals("HEALER", state()); assertEquals(HealerDialogue.Kind.TUTORIAL_END, healer().kind());
        assertFalse(game.isGameStarted()); assertFalse(progress.exists(), "No save while the farewell is open");
        HealerDialogue open = healer();
        game.loadNextLevel(); assertSame(open, healer(), "Repeated Checkpoint callbacks cannot restart or finish it");
        assertEquals("HEALER", state());
        finishScripted();
        assertEquals("LOADING", state()); assertEquals(1, get("currentLevelIndex"));
        assertEquals(1, progress.nextLevelIndex());
    }

    @Test void restartingDuringTheEndTaleReplaysTheFarewellButNeverTheStartTale() throws Exception {
        startNewGameOnTutorial(); finishScripted();
        game.loadNextLevel(); assertEquals("HEALER", state());
        call("restartLevel"); call("loadLevel"); call("beginLevelPlay");
        assertEquals("PLAYING", state()); assertNull(get("healer"), "the start tale stays one-shot");
        game.resetRoom(); // a retry never reopens anything
        assertEquals("PLAYING", state());
        game.loadNextLevel(); // finishing the tutorial again: the farewell was not lost
        assertEquals("HEALER", state()); assertEquals(HealerDialogue.Kind.TUTORIAL_END, healer().kind());
        assertFalse(progress.exists());
        finishScripted();
        assertEquals("LOADING", state()); assertEquals(1, get("currentLevelIndex"));
        assertEquals(1, progress.nextLevelIndex());
    }

    @Test void otherLevelsNeverOpenTheFarewell() throws Exception {
        startNewGameOnTutorial(); finishScripted(); game.loadNextLevel(); finishScripted();
        // forest-1 needs real textures (sky), so it is completed without a loaded level.
        assertNull(get("level"));
        game.loadNextLevel();
        assertEquals("LOADING", state()); assertEquals(2, get("currentLevelIndex")); assertNull(get("healer"));
        call("loadLevel"); // forest-3, the boss level: its own scenes are unchanged
        assertEquals("forest-3", ((Level) get("level")).name);
        game.loadNextLevel();
        assertNotEquals("HEALER", state());
        assertNull(get("healer"));
    }

    @Test void scriptedDialoguesAreSkippablePressByPressWithAtMostTwoPressesPerLine() throws Exception {
        for (HealerDialogue dialogue : List.of(HealerDialogue.tutorialStart(), HealerDialogue.tutorialEnd())) {
            int presses = 0; HealerDialogue.Choice result = null;
            while (result == null && presses < 40) { result = dialogue.advance(Language.ENGLISH); presses++; }
            assertEquals(HealerDialogue.Choice.CONTINUE, result);
            assertTrue(presses <= 12, "skippable in a handful of presses: " + presses);
        }
    }

    @Test void openingStartsTheFirstLevelAndOnlyFirstCompletionSavesTheBossLevel() throws Exception {
        // Order (D32): forest-0 (tutorial) -> forest-1 -> (forest-2?) -> forest-3 (boss).
        assertEquals(List.of("forest-0", "forest-1", "forest-3"), get("LEVEL_NAMES"));
        call("newGame"); assertEquals("INTRO", state());
        update(2); update(100); update(1);
        assertEquals("LOADING", state()); assertEquals(0, get("currentLevelIndex"));
        // forest-0 has no sky, so it loads headless; it is the first level.
        call("loadLevel");
        assertEquals("forest-0", ((Level) get("level")).name);
        game.loadNextLevel(); // D41: the tutorial's last room first opens the healer's farewell, saving nothing
        assertEquals("HEALER", state()); assertFalse(progress.exists());
        finishScripted(); // Checkpoint -> farewell -> forest-1
        assertEquals("LOADING", state()); assertEquals(1, get("currentLevelIndex"));
        assertEquals(1, new SavedProgress(prefs, 3).nextLevelIndex());
        // forest-1 needs real textures (sky), so completion is driven without loading it.
        game.loadNextLevel();
        assertEquals("LOADING", state()); assertEquals(2, get("currentLevelIndex"));
        assertEquals(2, new SavedProgress(prefs, 3).nextLevelIndex());
        call("loadLevel");
        Level boss = (Level) get("level");
        assertEquals("forest-3", boss.name); assertTrue(boss.hasBossEncounter()); assertTrue(boss.hasNextRoom());
    }

    @Test void endingScrollUsesIntroMusicThenDeletesOnlyProgressAndOffersNewGame() throws Exception {
        for (Language language : Language.values()) {
            set("language", language); progress.save(2); set("currentLevelIndex", 2);
            held.add(Input.Keys.K); held.add(Input.Keys.D);
            game.loadNextLevel();
            IntroScreen ending = (IntroScreen) get("intro");
            assertEquals("ENDING", state()); assertEquals(Msg.ENDING_STORY.t(language), ending.text());
            assertNull(get("level")); assertNull(get("player")); assertFalse(game.isGameStarted());
            assertEquals(2, new SavedProgress(prefs, 3).nextLevelIndex());
            game.loadNextLevel(); assertSame(ending, get("intro"), "Duplicate callbacks cannot restart the ending");
            update(1); assertEquals(IntroScreen.State.FADE_IN, ending.state());
            assertEquals(.5f, musicVolume); assertTrue(musicStarts > 0);
            update(1); update(100);
            assertEquals(IntroScreen.State.FADE_OUT, ending.state());
            update(.5f); assertEquals(.5f, musicVolume);
            assertTrue(progress.exists(), "Save is cleared after the ending fades, not on entry");
            update(.5f);
            assertEquals("MAIN_MENU", state()); assertNull(get("intro")); assertEquals(0, musicVolume);
            assertTrue(musicStops > 0); assertFalse(new SavedProgress(prefs, 3).exists());
            assertEquals(Language.UKRAINIAN.name(), prefs.getString(Settings.KEY_LANGUAGE));
            assertTrue(prefs.getBoolean(Settings.KEY_LEFT_HANDED)); assertEquals(2, prefs.get().size());
            Menu menu = (Menu) get("mainMenu");
            assertFalse(menu.items.get(0).visible.getAsBoolean());
            assertEquals(Msg.NEW_GAME.t(language), menu.items.get(menu.selected).label.get());
            held.clear();
        }
    }

    @Test void freshEndingSkipReturnsToMenuInsteadOfStartingAnotherLevel() throws Exception {
        progress.save(2); set("currentLevelIndex", 2);
        game.loadNextLevel(); update(2);
        held.add(Input.Keys.SPACE); update(.25f);
        assertEquals(IntroScreen.State.FADE_OUT, ((IntroScreen) get("intro")).state());
        update(1);
        assertEquals("MAIN_MENU", state()); assertEquals(-1f, get("levelLoadDelay"));
        assertFalse(progress.exists());
        held.clear();
        ((Menu) get("mainMenu")).confirmSelected();
        assertEquals("INTRO", state()); assertEquals(Msg.INTRO_STORY.t(Language.ENGLISH), ((IntroScreen) get("intro")).text());
        update(2); update(100); update(1);
        assertEquals("LOADING", state()); assertEquals(0, get("currentLevelIndex"));
        assertFalse(progress.exists());
    }
}
