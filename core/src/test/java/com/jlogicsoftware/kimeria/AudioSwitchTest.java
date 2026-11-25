package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.ui.FontSizes;
import com.jlogicsoftware.kimeria.ui.IntroScreen;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.Overlay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Options Sound and Music switches (#494) on the real {@link KimeriaGame}: each is applied at once and after a relaunch,
 * a switched-off channel starts nothing, and neither switch touches the other channel. The audio backend records every call.
 */
class AudioSwitchTest {
    @TempDir Path directory;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private Audio previousAudio;
    private KimeriaGame game;
    /** Every Sound call that makes noise ({@code play}, {@code loop}) and every {@code stop}, in order. */
    private final List<String> effects = new ArrayList<>(), effectStops = new ArrayList<>();
    private final List<String> menuTrack = new ArrayList<>(), storyTrack = new ArrayList<>();
    private float menuVolume, storyVolume;

    private Object get(String name) throws Exception { Field f = KimeriaGame.class.getDeclaredField(name); f.setAccessible(true); return f.get(game); }
    private void set(String name, Object value) throws Exception { Field f = KimeriaGame.class.getDeclaredField(name); f.setAccessible(true); f.set(game, value); }
    private void call(String name) throws Exception { Method m = KimeriaGame.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(game); }
    private void menuMusic(float dt) throws Exception {
        Method m = KimeriaGame.class.getDeclaredMethod("updateMenuMusic", float.class, boolean.class);
        m.setAccessible(true); m.invoke(game, dt, true);
    }

    private Preferences preferences() { return new HeadlessPreferences(new FileHandle(directory.resolve("settings.xml").toFile())); }

    private Music music(List<String> calls, boolean story) {
        return (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "setVolume" -> { if (story) storyVolume = (float) a[0]; else menuVolume = (float) a[0]; }
                case "play", "stop" -> calls.add(m.getName());
            }
            return null;
        });
    }

    @BeforeEach void setUp() throws Exception {
        harness.setUp();
        previousAudio = Gdx.audio;
        Gdx.audio = (Audio) Proxy.newProxyInstance(Audio.class.getClassLoader(), new Class<?>[]{Audio.class}, (p, m, a) -> {
            if (!m.getName().equals("newSound")) return null;
            String name = ((FileHandle) a[0]).nameWithoutExtension();
            return Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class}, (sound, method, args) -> {
                switch (method.getName()) {
                    case "play", "loop" -> effects.add(method.getName() + " " + name);
                    case "stop" -> effectStops.add(name);
                }
                return method.getReturnType() == long.class ? 1L : null;
            });
        });
        build();
    }

    /** A game as {@code create} leaves it for audio: settings loaded from disk, both switches applied, menus built. */
    private void build() throws Exception {
        game = new KimeriaGame();
        set("assets", harness.assets());
        set("menuMusic", music(menuTrack, false));
        set("introMusic", music(storyTrack, true));
        Settings settings = new Settings(preferences());
        set("settings", settings);
        set("savedProgress", new SavedProgress(settings.preferences(), KimeriaGame.levelNames().size()));
        call("loadAudioSwitches");
        call("buildMenus");
    }

    @AfterEach void restore() { Gdx.audio = previousAudio; harness.restoreGlobals(); }

    private Menu options() throws Exception { return (Menu) get("optionsMenu"); }
    /** Presses the Options row whose label starts with {@code label}. */
    private void press(Msg label) throws Exception {
        for (var item : options().items) if (item.label.get().startsWith(label.t(Language.ENGLISH) + ":")) { item.action.run(); return; }
        fail("no Options row " + label);
    }
    private List<String> labels() throws Exception { return options().items.stream().map(item -> item.label.get()).toList(); }

    /** Every way the game starts an effect: one-shot, scaled, looped, the menu hover blip and the desktop keep-alive loop. */
    private void everyEffect() throws Exception {
        game.playSound("jump");
        game.playSound("collect", .5f);
        game.loopSound("wall_rumble");
        call("playHoverSound");
        game.setAudioKeepAlive(true);
        call("updateAudioKeepAlive");
    }

    @Test void bothAreOnAtFirstStartAndBothChannelsPlay() throws Exception {
        assertTrue(labels().containsAll(List.of("Sound: On", "Music: On")), labels().toString());
        assertTrue(game.playSounds());
        everyEffect();
        assertEquals(List.of("play jump", "play collect", "loop wall_rumble", "play button_click", "loop keepalive"), effects);
        menuMusic(1f);
        assertEquals(List.of("play"), menuTrack);
    }

    @Test void soundOffPlaysNoEffectEndsRunningLoopsAndLeavesTheMusic() throws Exception {
        everyEffect();
        effects.clear();
        press(Msg.SOUND_LABEL);
        assertTrue(labels().containsAll(List.of("Sound: Off", "Music: On")), labels().toString());
        assertFalse(game.playSounds());
        assertEquals(List.of("keepalive", "wall_rumble"), effectStops.stream().sorted().toList(), "the loops end at once");
        everyEffect();
        assertEquals(List.of(), effects);
        menuMusic(1f);
        assertEquals(List.of("play"), menuTrack);
        assertTrue(menuVolume > 0f);

        build(); // relaunch
        assertTrue(labels().contains("Sound: Off"));
        everyEffect();
        assertEquals(List.of(), effects, "still silent after a relaunch");
        press(Msg.SOUND_LABEL);
        game.playSound("jump");
        assertEquals(List.of("play jump"), effects, "back on at once");
        assertTrue(new Settings(preferences()).get(Settings.SOUND));
    }

    @Test void musicOffStopsTheMenuTrackAtOnceAndLeavesTheEffects() throws Exception {
        menuMusic(1f);
        press(Msg.MUSIC_LABEL);
        assertTrue(labels().containsAll(List.of("Sound: On", "Music: Off")), labels().toString());
        menuMusic(1f / 60f); // one frame: no fade out
        assertEquals(List.of("play", "stop"), menuTrack);
        assertEquals(0f, menuVolume);
        menuMusic(1f);
        assertEquals(List.of("play", "stop"), menuTrack);
        game.playSound("jump");
        assertEquals(List.of("play jump"), effects);

        menuTrack.clear();
        build(); // relaunch
        for (int frame = 0; frame < 60; frame++) menuMusic(1f / 60f);
        assertEquals(List.of(), menuTrack, "never started after a relaunch");
        press(Msg.MUSIC_LABEL);
        menuMusic(1f);
        assertEquals(List.of("play"), menuTrack, "back on at once");
        assertTrue(menuVolume > 0f);
    }

    @Test void theStoryTrackFollowsTheMusicSwitchOnly() throws Exception {
        Overlay overlay = new Overlay(new Assets() {
            @Override public BitmapFont font(int size) { return new MetricsFont(size); }
        }, 640, 360, null);
        IntroScreen story = IntroScreen.open(Msg.ENDING_STORY.t(Language.ENGLISH), overlay, harness.assets(), 640, false, null);
        story.update(2f); // faded in
        assertTrue(story.volume() > 0f);
        set("intro", story);
        press(Msg.SOUND_LABEL); // effects off: the story still has its music
        call("updateIntroMusic");
        assertEquals(List.of("play"), storyTrack);
        press(Msg.MUSIC_LABEL);
        call("updateIntroMusic");
        assertEquals(List.of("play", "stop"), storyTrack);
        assertEquals(0f, storyVolume);
        call("updateIntroMusic");
        assertEquals(List.of("play", "stop"), storyTrack);
    }

    /**
     * The seven rows of the desktop Options list (#494) sit between the title and the menu hint: neither the first nor the last
     * row's gold frame touches them, in every language and both text tiers (D137).
     */
    @Test void theSevenOptionRowsFitBetweenTheTitleAndTheHint() throws Exception {
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (p, m, a) -> m.getName().equals("getType") ? Application.ApplicationType.Desktop : null);
        call("buildMenus");
        Menu menu = options();
        assertEquals(7, menu.visibleCount());
        float top = KimeriaGame.optionsButtonsY(menu), bottom = top + Overlay.buttonsHeight(menu);
        BitmapFont title = new MetricsFont(FontSizes.TITLE);
        for (Language language : Language.values()) for (boolean large : new boolean[]{false, true}) {
            String where = language.code() + (large ? " large" : "");
            GlyphLayout heading = new GlyphLayout(title, Msg.OPTIONS.t(language));
            assertTrue(KimeriaGame.OPTIONS_TITLE_Y + heading.height <= top - Overlay.SELECTED_FRAME, where + " title " + heading.height);
            BitmapFont hint = new MetricsFont(FontSizes.label(large));
            GlyphLayout line = new GlyphLayout(hint, Msg.MENU_HINT.t(language), com.badlogic.gdx.graphics.Color.WHITE, 608,
                com.badlogic.gdx.utils.Align.center, true);
            float hintTop = Overlay.hintTop(360 - Overlay.HINT_BOTTOM_MARGIN, line.height, 360);
            assertTrue(bottom + Overlay.SELECTED_FRAME <= hintTop, where + " list ends " + bottom + ", hint starts " + hintTop);
        }
    }
}
