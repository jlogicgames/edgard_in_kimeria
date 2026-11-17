package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.ui.HealerDialogue;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import com.jlogicsoftware.kimeria.world.RoomFade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Death-to-control time and the first-death line (#341, D81), on the real game: a real KimeriaGame, Level and Player,
 * stepped through {@code KimeriaGame.updatePlay}, the gameplay half of every rendered frame. The hero is killed on frame
 * 0 while Right is held; control is the first frame his X moves again.
 *
 * Before #341 (measured the same way, 2026-11-16, origin/main ba1410b): a hit took 67 frames at 60 Hz (1.117 s: 0.70 s
 * slow motion, then the 0.40 s appear clip under a 0.35 s camera pan), a pit 25 frames (0.417 s). D81 pins the hit at
 * 58 frames (0.967 s) and keeps the pit at 25; neither may grow without a new decision.
 */
class KimeriaGameDeathTimingTest {
    private static final int HZ = 60, FAST_HZ = 144;
    private static final int HIT_CONTROL_FRAMES = 58, PIT_CONTROL_FRAMES = 25, BEFORE_HIT_CONTROL_FRAMES = 67;
    /** forest-1: no tutorial tale opens on a new run there, so the first death is the first dialogue. */
    private static final int LEVEL = 1;
    /** forest-2: its pre-boss healer offers the collection return. */
    private static final int BOSS_LEVEL = KimeriaGame.levelNames().indexOf("forest-2");
    private static final int MAX_ROOMS = 10;
    private static final int SETTLE_SECONDS = 1, MAX_SECONDS = 4;

    @TempDir Path directory;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private KimeriaGame game;
    private SavedProgress progress;
    private float step;

    private Object get(String name) throws Exception { Field f = KimeriaGame.class.getDeclaredField(name); f.setAccessible(true); return f.get(game); }
    private void set(String name, Object value) throws Exception { Field f = KimeriaGame.class.getDeclaredField(name); f.setAccessible(true); f.set(game, value); }
    private void call(String name) throws Exception { Method m = KimeriaGame.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(game); }
    private Player player() throws Exception { return (Player) get("player"); }
    private String state() throws Exception { return get("uiState").toString(); }

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void restore() { harness.restoreGlobals(); }

    /** A game at {@code hz} with a real save backend, before any run. */
    private void build(int hz) throws Exception {
        step = 1f / hz;
        final float frame = step;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (p, m, args) -> m.getName().equals("getDeltaTime") ? frame : m.getReturnType() == boolean.class ? false
                : m.getReturnType() == int.class ? 0 : m.getReturnType() == float.class ? 0f : m.getReturnType() == long.class ? 0L : null);
        game = new KimeriaGame();
        Assets assets = harness.assets();
        set("assets", assets);
        set("touchControls", new TouchControls(assets, game,
            new com.badlogic.gdx.utils.viewport.Viewport() { @Override public com.badlogic.gdx.math.Vector2 unproject(com.badlogic.gdx.math.Vector2 v) { return v; } },
            640, 360, true));
        set("overlay", new Overlay(assets, 640, 360));
        set("playSounds", false);
        HeadlessPreferences prefs = new HeadlessPreferences(new FileHandle(directory.resolve("settings-" + hz + ".xml").toFile()));
        progress = new SavedProgress(prefs, KimeriaGame.levelNames().size());
        set("savedProgress", progress);
        call("buildMenus"); // Restart Level resets the pause menu's selection
    }

    /** Starts a run on forest-1 ({@code newRun}: a New Game; otherwise Continue) and lets the hero settle on the floor. */
    private void start(boolean newRun) throws Exception { start(LEVEL, newRun); }

    private void start(int levelIndex, boolean newRun) throws Exception {
        Class<?> data = Class.forName(KimeriaGame.class.getName() + "$StartData");
        Constructor<?> make = data.getDeclaredConstructor(int.class, int.class, boolean.class); make.setAccessible(true);
        Method startGame = KimeriaGame.class.getDeclaredMethod("startGame", data); startGame.setAccessible(true);
        startGame.invoke(game, make.newInstance(levelIndex, 0, newRun));
        call("loadLevel"); call("beginLevelPlay");
        assertEquals("PLAYING", state());
        frames(Math.round(SETTLE_SECONDS / step));
    }

    private void frame() throws Exception {
        Method update = KimeriaGame.class.getDeclaredMethod("updatePlay", float.class, get("uiState").getClass(), boolean.class);
        update.setAccessible(true);
        Object atStart = get("uiState");
        update.invoke(game, step, atStart, "PLAYING".equals(atStart.toString()));
    }

    private void frames(int count) throws Exception { for (int i = 0; i < count; i++) frame(); }

    private void die(boolean hit) throws Exception {
        Player hero = player();
        if (hit) hero.collidedWithActor(true);
        else { Method die = Player.class.getDeclaredMethod("die", boolean.class); die.setAccessible(true); die.invoke(hero, false); }
    }

    /** Frames from the killing frame (0) until the hero, Right held, moves again; dialogue is answered as it opens. */
    private int controlFrames(boolean hit) throws Exception {
        harness.held.add(Input.Keys.RIGHT); harness.held.add(Input.Keys.D);
        Player dying = player();
        die(hit);
        float startX = Float.NaN;
        for (int f = 1; f <= MAX_SECONDS / step; f++) {
            frame();
            Player now = player();
            if (now != dying && Float.isNaN(startX)) startX = now.position.x;
            if (!Float.isNaN(startX) && now.position.x > startX) {
                harness.held.clear();
                return f;
            }
        }
        harness.held.clear();
        return fail("control never returned");
    }

    private HealerDialogue healer() throws Exception { return (HealerDialogue) get("healer"); }

    private void answerHealer() throws Exception {
        Method choose = KimeriaGame.class.getDeclaredMethod("chooseHealer", HealerDialogue.Choice.class); choose.setAccessible(true);
        choose.invoke(game, HealerDialogue.Choice.CONTINUE);
        assertEquals("PLAYING", state());
    }

    /** Kills the hero and runs to the room rebuild; returns the dialogue that opened, or null. */
    private HealerDialogue dieAndRebuild(boolean hit) throws Exception {
        Player dying = player();
        die(hit);
        for (int f = 0; f < MAX_SECONDS / step && player() == dying; f++) frame();
        assertNotSame(dying, player(), "the room was rebuilt");
        HealerDialogue opened = healer();
        if (opened != null) answerHealer();
        frames(Math.round(SETTLE_SECONDS / step));
        return opened;
    }

    @Test void aHitGivesControlBackAt58FramesAt60HzNineFewerThanBefore() throws Exception {
        build(HZ); start(false); // told already (a Continue with no key): no dialogue in the way
        int frames = controlFrames(true);
        assertEquals(HIT_CONTROL_FRAMES, frames, "0.967 s at 60 Hz (D81)");
        assertTrue(frames < BEFORE_HIT_CONTROL_FRAMES);
    }

    @Test void aHitTakesTheSameRealTimeAt144Hz() throws Exception {
        build(FAST_HZ); start(false);
        float seconds = controlFrames(true) * step;
        assertEquals(HIT_CONTROL_FRAMES / (float) HZ, seconds, 2f / HZ, "0.967 s at any frame rate, within a 60 Hz frame or two");
    }

    @Test void aPitStaysAt25Frames() throws Exception {
        build(HZ); start(false);
        assertEquals(PIT_CONTROL_FRAMES, controlFrames(false), "0.417 s, unchanged by #341");
    }

    @Test void theRoomIsRebuiltAtTheBlackOfTheDeathFadeAndTheCameraSnaps() throws Exception {
        build(HZ); start(false);
        Player dying = player();
        die(true);
        RoomFade fade = (RoomFade) get("deathFade");
        int fadeStart = -1, rebuilt = -1;
        for (int f = 1; f <= MAX_SECONDS * HZ && rebuilt < 0; f++) {
            frame();
            if (fadeStart < 0 && fade.active()) fadeStart = f;
            if (player() != dying) {
                rebuilt = f;
                assertTrue(fade.dark(), "rebuilt under black");
                assertEquals(1f, fade.alpha());
            }
        }
        assertEquals(Math.round(Player.THREAD_TAUT * HZ), fadeStart, 1, "the fade starts as the thread goes taut (0.45 s)");
        assertEquals(Math.round((Player.THREAD_TAUT + RoomFade.HALF_SECONDS) * HZ), rebuilt, 1, "black at 0.70 s, where the slow motion ended");
        assertFalse((boolean) get("cameraPanning"), "the jump is hidden by the fade, not shown as a pan");
        assertFalse((boolean) get("roomResetPending"));
    }

    @Test void theFirstDeathOfANewRunOpensTheLineOnceAndItIsSavedAtOnce() throws Exception {
        build(HZ); start(true);
        assertFalse(progress.firstDeathTold(), "New Game clears it");
        HealerDialogue first = dieAndRebuild(true);
        assertNotNull(first, "the first death opens the line");
        assertEquals(HealerDialogue.Kind.FIRST_DEATH, first.kind());
        assertTrue(progress.firstDeathTold(), "written when it opens, not at level completion");
        assertNull(dieAndRebuild(true), "never on a later death");
        assertNull(dieAndRebuild(false), "nor on a later pit");
    }

    @Test void aPitCanBeTheFirstDeath() throws Exception {
        build(HZ); start(true);
        HealerDialogue first = dieAndRebuild(false);
        assertNotNull(first);
        assertEquals(HealerDialogue.Kind.FIRST_DEATH, first.kind());
    }

    @Test void restartLevelIsNotADeath() throws Exception {
        build(HZ); start(true);
        call("restartLevel"); call("loadLevel"); call("beginLevelPlay");
        assertNull(healer());
        assertFalse(progress.firstDeathTold(), "still untold after Restart Level");
        assertNotNull(dieAndRebuild(true), "the first real death still opens it");
    }

    @Test void aCollectionReturnIsNotADeathAndLeavesTheLineUntold() throws Exception {
        build(HZ); start(BOSS_LEVEL, true);
        Method enter = KimeriaGame.class.getDeclaredMethod("enterNextRoom"); enter.setAccessible(true);
        for (int room = 0; room < MAX_ROOMS && healer() == null; room++) enter.invoke(game);
        assertNotNull(healer(), "the pre-boss healer meeting opened");
        assertEquals(HealerDialogue.Kind.PRE_BOSS, healer().kind());
        Method choose = KimeriaGame.class.getDeclaredMethod("chooseHealer", HealerDialogue.Choice.class); choose.setAccessible(true);
        Player before = player();
        choose.invoke(game, HealerDialogue.Choice.RETURN);
        assertNotSame(before, player(), "returned to the first entrance with a fresh hero");
        assertEquals("PLAYING", state());
        frames(Math.round(SETTLE_SECONDS / step));
        assertNull(healer(), "a collection return opens no first-death line");
        assertFalse(progress.firstDeathTold(), "and does not use it up");
        HealerDialogue first = dieAndRebuild(true);
        assertNotNull(first, "the first real death after it still opens the line");
        assertEquals(HealerDialogue.Kind.FIRST_DEATH, first.kind());
    }

    @Test void continueNeverRepeatsItAndADeathlessSaveStillHearsItOnce() throws Exception {
        build(HZ); start(true);
        progress.save(LEVEL); // a level was completed without a death
        build(HZ); start(false); // the app was closed, then Continue
        assertNotNull(dieAndRebuild(true), "not told yet in this run");
        build(HZ); start(false); // closed again before the next save, then Continue
        assertNull(dieAndRebuild(true), "Continue never repeats it");
        build(HZ); start(true); // a New Game is a new run
        assertNotNull(dieAndRebuild(true));
    }

    @Test void aSaveWithoutTheKeyCountsAsTold() throws Exception {
        build(HZ);
        progress.save(LEVEL); // written before the line existed: no first-death key
        assertTrue(progress.firstDeathTold());
        start(false);
        assertNull(dieAndRebuild(true));
    }
}
