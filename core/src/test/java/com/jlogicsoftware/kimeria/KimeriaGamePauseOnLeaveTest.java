package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.ui.HealerDialogue;
import com.jlogicsoftware.kimeria.ui.HealerDialogueView;
import com.jlogicsoftware.kimeria.ui.FontSizes;
import com.jlogicsoftware.kimeria.ui.IntroScreen;
import com.jlogicsoftware.kimeria.ui.KimeriaArms;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.ui.PlayfieldViewport;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Leaving the game and coming back (D164), on the real KimeriaGame: {@code pause()} and {@code resume()} as the backends
 * call them (minimized, focus lost, tab hidden), with {@code updatePlay} and {@code renderOverlay} as the frames after.
 */
class KimeriaGamePauseOnLeaveTest {
    /** forest-1: no tutorial tale opens on a run there. */
    private static final int LEVEL = 1;
    private static final int SETTLE_FRAMES = 60, AWAY_FRAMES = 240, MAX_FRAMES = 600;
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** One update longer than any fade, reveal or input lock of the dialogue and story screens. */
    private static final float LONG_ENOUGH = 30f;
    /** The pause menu's rows, in order. */
    private static final int RESUME_ROW = 0, RESTART_ROW = 1, EXIT_ROW = 2;

    /** What each screen becomes when the player leaves. Every UiState must be listed: a new one needs a decision. */
    private static final Map<String, String> AFTER_LEAVING = new LinkedHashMap<>();
    static {
        for (String unchanged : new String[]{"WEB_START", "MAIN_MENU", "CONFIRM_NEW_GAME", "INTRO", "ENDING", "ABOUT", "OPTIONS",
            "CONTROLS", "ASSIST", "LOADING", "HEALER", "PAUSED"}) AFTER_LEAVING.put(unchanged, unchanged);
        AFTER_LEAVING.put("PLAYING", "PAUSED");
    }

    @TempDir Path directory;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final Vector2 cursor = new Vector2();
    private boolean clicking, padConfirm;
    private KimeriaGame game;
    private Overlay overlay;
    private Batch batch;

    private Object get(String name) throws Exception { Field f = KimeriaGame.class.getDeclaredField(name); f.setAccessible(true); return f.get(game); }
    private void set(String name, Object value) throws Exception { Field f = KimeriaGame.class.getDeclaredField(name); f.setAccessible(true); f.set(game, value); }
    private void call(String name) throws Exception { Method m = KimeriaGame.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(game); }
    private Player player() throws Exception { return (Player) get("player"); }
    private Menu pauseMenu() throws Exception { return (Menu) get("pauseMenu"); }
    private String state() throws Exception { return String.valueOf(get("uiState")); }
    private boolean running() throws Exception { return (boolean) get("gameStarted"); }

    private void setState(String name) throws Exception {
        Field f = KimeriaGame.class.getDeclaredField("uiState");
        f.setAccessible(true);
        for (Object value : f.getType().getEnumConstants()) if (value.toString().equals(name)) f.set(game, value);
        assertEquals(name, state());
    }

    @BeforeEach void setUp() throws Exception {
        harness.setUp();
        Input scripted = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (p, m, args) -> switch (m.getName()) {
            case "getX" -> (int) cursor.x;
            case "getY" -> (int) cursor.y;
            case "justTouched" -> clicking;
            default -> m.invoke(scripted, args);
        });
        harness.font = new MetricsFont(FontSizes.BUTTON);
        game = new KimeriaGame();
        Assets assets = harness.assets();
        OrthographicCamera camera = new OrthographicCamera();
        camera.setToOrtho(true, 640, 360);
        // One window pixel is one logical unit, and the cursor's Y grows downward like the menu's.
        PlayfieldViewport viewport = new PlayfieldViewport(640, 360, camera) {
            @Override public Vector2 unproject(Vector2 v) { return v; }
        };
        overlay = new Overlay(assets, 640, 360, new KimeriaArms(assets));
        set("assets", assets);
        set("uiViewport", viewport);
        set("touchControls", new TouchControls(assets, game, viewport, 640, 360, false));
        set("overlay", overlay);
        set("gamepad", new GamepadInput() {
            @Override protected Controller controller() { return null; }
            @Override public boolean confirm() { return padConfirm; }
        });
        set("playSounds", false);
        set("savedProgress", new SavedProgress(new HeadlessPreferences(new FileHandle(directory.resolve("save.xml").toFile())), KimeriaGame.levelNames().size()));
        call("buildMenus");
        batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
            (p, m, args) -> {
                if (m.getReturnType() == boolean.class) return false;
                if (m.getReturnType() == int.class) return 0;
                if (m.getReturnType() == float.class) return 0f;
                return null;
            });
    }

    @AfterEach void restore() { harness.restoreGlobals(); }

    private void load(int levelIndex, boolean newRun) throws Exception {
        Class<?> data = Class.forName(KimeriaGame.class.getName() + "$StartData");
        Constructor<?> make = data.getDeclaredConstructor(int.class, int.class, boolean.class); make.setAccessible(true);
        Method startGame = KimeriaGame.class.getDeclaredMethod("startGame", data); startGame.setAccessible(true);
        startGame.invoke(game, make.newInstance(levelIndex, 0, newRun));
    }

    /** The level is ready, as {@code render()} does it when the loading delay runs out. */
    private void finishLoading() throws Exception { call("loadLevel"); call("beginLevelPlay"); }

    private void startPlaying() throws Exception {
        load(LEVEL, false);
        finishLoading();
        frames(SETTLE_FRAMES);
        assertEquals("PLAYING", state());
    }

    /** The gameplay half of a frame, then the overlay half, as {@code render()} orders them. */
    private void frame() throws Exception {
        Method update = KimeriaGame.class.getDeclaredMethod("updatePlay", float.class, get("uiState").getClass(), boolean.class);
        update.setAccessible(true);
        Object atStart = get("uiState");
        update.invoke(game, STEP, atStart, "PLAYING".equals(atStart.toString()) || "PAUSED".equals(atStart.toString()));
        if (!"PLAYING".equals(state())) menuFrame(atStart.equals(get("uiState")));
        else set("justReturned", false);
        harness.pressed.clear();
        clicking = padConfirm = false;
    }

    private void menuFrame(boolean acceptActions) {
        overlay.beginFrame();
        game.renderOverlay(batch, acceptActions);
    }

    private void frames(int count) throws Exception { for (int i = 0; i < count; i++) frame(); }

    private void press(int key) { harness.held.add(key); harness.pressed.add(key); }

    private void leaveAndReturn() throws Exception {
        game.pause();
        frames(AWAY_FRAMES);
        game.resume();
        frame(); // the return frame
    }

    private void chooseResume() throws Exception {
        press(Input.Keys.ENTER);
        frame();
        harness.held.remove(Input.Keys.ENTER);
        assertEquals("PLAYING", state());
    }

    @Test void onlyARunningLevelChangesScreenWhenThePlayerLeaves() throws Exception {
        Field f = KimeriaGame.class.getDeclaredField("uiState");
        assertEquals(f.getType().getEnumConstants().length, AFTER_LEAVING.size(), "every UiState needs an expectation");
        for (Map.Entry<String, String> screen : AFTER_LEAVING.entrySet()) {
            setState(screen.getKey());
            boolean playing = screen.getKey().equals("PLAYING");
            set("gameStarted", playing);
            game.pause();
            assertEquals(screen.getValue(), state(), "leaving from " + screen.getKey());
            assertFalse(running(), screen.getKey());
            game.pause(); // a second report of the same departure (focus lost, then minimized), or the exit
            assertEquals(screen.getValue(), state(), "leaving twice from " + screen.getKey());
            game.resume();
            game.resume();
            assertEquals(screen.getValue(), state(), "returning to " + screen.getKey());
            assertFalse(running(), "returning never starts play: " + screen.getKey());
        }
    }

    @Test void theBackendsMayCallBeforeCreateAndWithoutADeparture() throws Exception {
        KimeriaGame unborn = new KimeriaGame(); // a browser tab hidden or blurred while the assets still load
        assertDoesNotThrow(() -> { unborn.pause(); unborn.resume(); });
        startPlaying();
        game.resume(); // a focus or a key with nobody gone
        assertFalse((boolean) get("justReturned"));
        assertEquals("PLAYING", state());
    }

    @Test void theWorldWaitsUntilThePlayerChoosesResume() throws Exception {
        startPlaying();
        harness.held.add(Input.Keys.D);
        frames(5);
        float x = player().position.x;
        leaveAndReturn();
        frames(AWAY_FRAMES);
        assertEquals("PAUSED", state());
        assertEquals(x, player().position.x, "nothing moved, though Right stayed held");
        chooseResume();
        frames(5);
        assertTrue(player().position.x > x, "held movement keeps working, as after an Esc pause");
    }

    @Test void theAutomaticPauseOpensOnResume() throws Exception {
        startPlaying();
        pauseMenu().selected = EXIT_ROW; // left there by an earlier Exit to Menu
        game.pause();
        assertEquals(RESUME_ROW, pauseMenu().selected);
    }

    @Test void aPauseMenuAlreadyOpenKeepsItsRow() throws Exception {
        startPlaying();
        game.togglePause();
        pauseMenu().selected = RESTART_ROW;
        leaveAndReturn();
        assertEquals(RESTART_ROW, pauseMenu().selected);
        assertEquals("PAUSED", state());
    }

    @Test void thePauseMenuTakesNoInputWhileThePlayerIsAway() throws Exception {
        startPlaying();
        game.pause();
        frame();
        for (int key : new int[]{Input.Keys.ENTER, Input.Keys.SPACE, Input.Keys.ESCAPE, Input.Keys.DOWN}) {
            press(key);
            frame();
            harness.held.remove(key);
            assertEquals("PAUSED", state(), Input.Keys.toString(key));
            assertEquals(RESUME_ROW, pauseMenu().selected, Input.Keys.toString(key));
        }
        padConfirm = true; // a pad is read in the background on desktop
        frame();
        assertEquals("PAUSED", state());
    }

    @Test void theClickOrKeyThatBringsThePlayerBackActsOnNothing() throws Exception {
        startPlaying();
        float restartY = rowY(RESTART_ROW);
        game.pause();
        frame();
        // The window is clicked where the second row, Restart Level, is drawn.
        cursor.set(320, restartY);
        game.resume();
        clicking = true;
        press(Input.Keys.ENTER);
        frame();
        harness.held.clear();
        assertEquals("PAUSED", state(), "neither Restart Level nor Resume");
        assertEquals(RESUME_ROW, pauseMenu().selected);
        frames(5);
        assertEquals(RESUME_ROW, pauseMenu().selected, "a cursor resting there takes no selection");
        cursor.x++;
        frame();
        assertEquals(RESTART_ROW, pauseMenu().selected, "a real move does");
        clicking = true;
        frame();
        assertEquals("LOADING", state(), "and the next click is an ordinary one");
    }

    /** A cursor height on the given pause row, found with real pause frames before the player leaves. */
    private float rowY(int row) throws Exception {
        game.togglePause();
        float found = -1;
        for (int y = 0; y < 360 && found < 0; y++) {
            cursor.set(320, y);
            menuFrame(true);
            if (pauseMenu().selected == row) found = y + 4;
        }
        assertTrue(found >= 0, "row " + row + " is drawn");
        cursor.set(0, 0);
        menuFrame(true);
        pauseMenu().selected = RESUME_ROW;
        game.togglePause();
        assertEquals("PLAYING", state());
        return found;
    }

    @Test void aHeldJumpAndSwordNeedANewPressAfterTheReturn() throws Exception {
        startPlaying();
        float floor = player().position.y;
        game.pause();
        frame();
        harness.held.add(Input.Keys.SPACE);
        harness.held.add(Input.Keys.J);
        harness.held.add(Input.Keys.K);
        game.resume();
        frame();
        chooseResume();
        for (int f = 0; f < SETTLE_FRAMES; f++) {
            frame();
            assertEquals(floor, player().position.y, .5f, "no jump from a key held across the pause, frame " + f);
            assertFalse(player().isSwinging(), "no swing from a key held across the pause, frame " + f);
        }
        harness.held.clear();
        frame();
        press(Input.Keys.J);
        float highest = floor;
        for (int f = 0; f < SETTLE_FRAMES / 4; f++) { frame(); highest = Math.min(highest, player().position.y); }
        assertTrue(highest < floor - 1, "a new press jumps (a smaller Y is higher)");
    }

    @Test void aLevelThatBecomesReadyWhileThePlayerIsAwayStartsPaused() throws Exception {
        load(LEVEL, false);
        game.pause();
        assertEquals("LOADING", state());
        finishLoading(); // desktop keeps loading while minimized
        float x = player().position.x, y = player().position.y;
        frames(AWAY_FRAMES);
        assertEquals("PAUSED", state());
        assertEquals(x, player().position.x);
        assertEquals(y, player().position.y, "not even gravity ran");
        game.resume();
        frame();
        assertEquals("PAUSED", state());
        chooseResume();
    }

    @Test void aLevelThatBecomesReadyAfterTheReturnStartsPlaying() throws Exception {
        load(LEVEL, false);
        leaveAndReturn();
        finishLoading();
        frame();
        assertEquals("PLAYING", state());
    }

    @Test void aDialogueAnsweredInTheBackgroundDoesNotStartPlay() throws Exception {
        load(KimeriaGame.levelNames().indexOf("forest-0"), true);
        finishLoading();
        assertEquals("HEALER", state());
        game.pause();
        Method choose = KimeriaGame.class.getDeclaredMethod("chooseHealer", HealerDialogue.Choice.class); choose.setAccessible(true);
        choose.invoke(game, HealerDialogue.Choice.CONTINUE); // a gamepad press can reach the dialogue while minimized
        frame();
        assertEquals("PAUSED", state());
    }

    @Test void leavingDuringADeathOrARoomChangeHoldsItAndResumeFinishesIt() throws Exception {
        startPlaying();
        Player dying = player();
        dying.collidedWithActor(true);
        RoomFade death = (RoomFade) get("deathFade");
        for (int f = 0; f < MAX_FRAMES && !death.active(); f++) frame();
        assertTrue(death.active());
        float alpha = death.alpha();
        leaveAndReturn();
        assertEquals("PAUSED", state());
        assertEquals(alpha, death.alpha(), "the fade waits");
        chooseResume();
        for (int f = 0; f < MAX_FRAMES && (player() == dying || death.active()); f++) frame();
        assertNotSame(dying, player());
        assertFalse(death.active());

        frames(SETTLE_FRAMES);
        RoomFade room = (RoomFade) get("roomFade");
        room.start();
        frame();
        float roomAlpha = room.alpha();
        leaveAndReturn();
        assertEquals(roomAlpha, room.alpha(), "the room change waits");
        chooseResume();
        for (int f = 0; f < MAX_FRAMES && room.active(); f++) frame();
        assertFalse(room.active());
    }

    @Test void thePressThatBringsThePlayerBackDoesNotAnswerTheHealer() throws Exception {
        load(KimeriaGame.levelNames().indexOf("forest-0"), true);
        finishLoading();
        set("healerView", new HealerDialogueView((Assets) get("assets")));
        HealerDialogue healer = (HealerDialogue) get("healer");
        Method update = KimeriaGame.class.getDeclaredMethod("updateHealer", float.class); update.setAccessible(true);
        update.invoke(game, LONG_ENOUGH); // the screen opens
        update.invoke(game, LONG_ENOUGH); // and its first line is complete
        assertTrue(healer.fullyRevealed(Language.ENGLISH));
        Msg line = healer.currentLine();
        leaveAndReturnTo("HEALER");
        press(Input.Keys.ENTER);
        update.invoke(game, STEP);
        assertEquals(line, healer.currentLine(), "the return frame answers nothing");
        set("justReturned", false);
        harness.pressed.clear();
        update.invoke(game, STEP);
        assertEquals(line, healer.currentLine(), "the key still held counts as held, not as a new press");
        harness.held.clear();
        update.invoke(game, STEP);
        press(Input.Keys.ENTER);
        update.invoke(game, STEP);
        assertNotEquals(line, healer.currentLine(), "a new press advances");
    }

    @Test void thePressThatBringsThePlayerBackNeitherTurnsNorSkipsTheStory() throws Exception {
        set("introMusic", Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class}, (p, m, args) -> null));
        call("newGame");
        IntroScreen intro = (IntroScreen) get("intro");
        Method update = KimeriaGame.class.getDeclaredMethod("updateStory", float.class); update.setAccessible(true);
        for (int i = 0; i < 3; i++) update.invoke(game, LONG_ENOUGH); // faded in, page shown, input unlocked
        assertEquals(IntroScreen.Phase.WAIT, intro.phase());
        assertFalse(intro.locked());
        for (int key : new int[]{Input.Keys.ENTER, Input.Keys.ESCAPE}) {
            leaveAndReturnTo("INTRO");
            press(key);
            update.invoke(game, 0f);
            set("justReturned", false);
            harness.pressed.clear();
            update.invoke(game, 0f);
            assertEquals(IntroScreen.State.READING, intro.state(), "not skipped by " + Input.Keys.toString(key));
            assertEquals(IntroScreen.Phase.WAIT, intro.phase(), "not turned by " + Input.Keys.toString(key));
            assertEquals(0, intro.pageIndex(), Input.Keys.toString(key));
            harness.held.clear();
            update.invoke(game, 0f);
        }
        int page = intro.pageIndex();
        press(Input.Keys.ENTER);
        update.invoke(game, 0f);
        harness.pressed.clear();
        update.invoke(game, LONG_ENOUGH);
        assertEquals(page + 1, intro.pageIndex(), "a new press turns the page");
    }

    /** Leaves and comes back without running a frame, so the next update is the return frame. */
    private void leaveAndReturnTo(String screen) throws Exception {
        game.pause();
        game.resume();
        assertEquals(screen, state());
    }
}
