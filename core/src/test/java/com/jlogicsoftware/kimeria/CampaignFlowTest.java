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
        progress = new SavedProgress(prefs, 2); set("savedProgress", progress); call("buildMenus");
    }

    @AfterEach void restore() { Gdx.input = oldInput; Gdx.files = oldFiles; Gdx.app = oldApp; }

    @Test void openingStartsRoomBasedForestAndOnlyFirstCompletionSavesTheSecondLevel() throws Exception {
        assertEquals(List.of("forest", "forest-1"), get("LEVEL_NAMES"));
        call("newGame"); assertEquals("INTRO", state());
        update(2); update(100); update(1);
        assertEquals("LOADING", state()); assertEquals(0, get("currentLevelIndex"));
        call("loadLevel");
        Level first = (Level) get("level");
        assertEquals("forest", first.name); assertTrue(first.hasBossEncounter()); assertTrue(first.hasNextRoom());
        // The boss/victory/dialogue boundary is exercised by BossCombatTest.
        game.loadNextLevel();
        assertEquals("LOADING", state()); assertEquals(1, get("currentLevelIndex"));
        assertEquals(1, new SavedProgress(prefs, 2).nextLevelIndex());
    }

    @Test void endingScrollUsesIntroMusicThenDeletesOnlyProgressAndOffersNewGame() throws Exception {
        for (Language language : Language.values()) {
            set("language", language); progress.save(1); set("currentLevelIndex", 1);
            held.add(Input.Keys.K); held.add(Input.Keys.D);
            game.loadNextLevel();
            IntroScreen ending = (IntroScreen) get("intro");
            assertEquals("ENDING", state()); assertEquals(Msg.ENDING_STORY.t(language), ending.text());
            assertNull(get("level")); assertNull(get("player")); assertFalse(game.isGameStarted());
            assertEquals(1, new SavedProgress(prefs, 2).nextLevelIndex());
            game.loadNextLevel(); assertSame(ending, get("intro"), "Duplicate callbacks cannot restart the ending");
            update(1); assertEquals(IntroScreen.State.FADE_IN, ending.state());
            assertEquals(.5f, musicVolume); assertTrue(musicStarts > 0);
            update(1); update(100);
            assertEquals(IntroScreen.State.FADE_OUT, ending.state());
            update(.5f); assertEquals(.5f, musicVolume);
            assertTrue(progress.exists(), "Save is cleared after the ending fades, not on entry");
            update(.5f);
            assertEquals("MAIN_MENU", state()); assertNull(get("intro")); assertEquals(0, musicVolume);
            assertTrue(musicStops > 0); assertFalse(new SavedProgress(prefs, 2).exists());
            assertEquals(Language.UKRAINIAN.name(), prefs.getString(Settings.KEY_LANGUAGE));
            assertTrue(prefs.getBoolean(Settings.KEY_LEFT_HANDED)); assertEquals(2, prefs.get().size());
            Menu menu = (Menu) get("mainMenu");
            assertFalse(menu.items.get(0).visible.getAsBoolean());
            assertEquals(Msg.NEW_GAME.t(language), menu.items.get(menu.selected).label.get());
            held.clear();
        }
    }

    @Test void freshEndingSkipReturnsToMenuInsteadOfStartingAnotherLevel() throws Exception {
        progress.save(1); set("currentLevelIndex", 1);
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
