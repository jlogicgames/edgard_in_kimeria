package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-0 lever lesson room (#186): ordinary keyboard controls drive the hero through the real
 * map. Times are author (mastered) measurements, not novice playtests. Recipe: docs/ROOMS.md.
 */
class LeverLessonTest {
    private static final float STEP = 1f / 60f;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "language" -> Language.ENGLISH;
            case "isGameStarted" -> true;
            case "artifacts" -> artifacts;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? STEP : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private Level freshLevel() {
        return new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
    }

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private Torch torch(Level level) {
        return level.objects.stream().filter(Torch.class::isInstance).map(Torch.class::cast).findFirst().orElseThrow();
    }

    private Lever lever(Level level) {
        return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow();
    }

    /** Holds Right until the hero can operate the lever; returns elapsed frames. */
    private int walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertTrue(++frames < 60 * 20, "lever never came into reach at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Up + Action, as the hint teaches: Action is a fresh press while Up is held. */
    private int operate(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    /** Walks Right to the room exit; returns elapsed frames. */
    private int walkToExit(Level level) {
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
            assertTrue(++frames < 60 * 30, "exit unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    @Test void theRoomOffersOneLeverAndOneDarkTorchAndNothingElse() {
        Level level = freshLevel();
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.objects.stream().filter(Torch.class::isInstance).count());
        assertEquals(1, level.triggers.size());
        assertFalse(torch(level).isBurning(), "The effect starts off, so pressing visibly changes it");
        assertFalse(lever(level).isUp());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>));
        Rectangle torchBounds = new Rectangle(torch(level).getX(), torch(level).getY(), 16, 16);
        assertTrue(Math.abs(torchBounds.x - lever(level).getX()) < 160, "Torch is in the lever's view");
    }

    @Test void walkingToTheLeverAndPressingUpActionTogglesTheTorchBothWays() {
        Level level = freshLevel();
        walkToLever(level);
        Torch torch = torch(level);
        Lever lever = lever(level);
        assertFalse(torch.isBurning());
        operate(level);
        assertTrue(torch.isBurning()); assertTrue(lever.isUp());
        // The effect is observable from the lever's position: the torch is on the same screen.
        assertTrue(Math.abs(torch.centerX() - level.player.centerX()) < 320);
        operate(level);
        assertFalse(torch.isBurning()); assertFalse(lever.isUp());
        operate(level);
        assertTrue(torch.isBurning(), "The lever toggles; it never fires once and locks");
    }

    @Test void exitIsReachableWithTheLeverOffOnAndAfterRetryAndRestart() {
        Level off = freshLevel();
        walkToExit(off);

        Level on = freshLevel();
        walkToLever(on); operate(on);
        assertTrue(torch(on).isBurning());
        walkToExit(on);

        // Death / room retry: the room is not yet secured, so it returns to its authored (dark) state.
        Level retry = freshLevel();
        walkToLever(retry); operate(retry);
        assertTrue(torch(retry).isBurning());
        retry.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(torch(retry).isBurning(), "Retry restores the authored state");
        assertFalse(lever(retry).isUp());
        walkToExit(retry);
        retry.resetCurrentRoom(new Player(assets, game, 0, 0));
        walkToLever(retry); operate(retry);
        walkToExit(retry);

        // Restart Level rebuilds the whole level from the map.
        Level restarted = freshLevel();
        assertFalse(torch(restarted).isBurning());
        walkToExit(restarted);
    }

    @Test void lessonHintsCoverEveryStandingPositionInBothTorchStatesAndSurviveRetry() {
        Level level = freshLevel();
        Set<String> messages = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (boolean lit : new boolean[]{false, true}) {
                for (float x = 24; x < 1216; x += 8) {
                    torch(level).toggleFire(lit);
                    level.player.position.set(x, 272);
                    for (int i = 0; i < 3; i++) frame(level);
                    String hint = level.player.contextHint();
                    String where = "x=" + x + " lit=" + lit + ": " + hint;
                    if (level.player.canInteract()) {
                        assertTrue(hint.contains(Msg.LEVER.t(Language.ENGLISH)), where);
                        assertTrue(hint.contains((lit ? Msg.TURN_OFF : Msg.TURN_ON).t(Language.ENGLISH)), where);
                    } else {
                        // Message zones know only position, so each text must be true in both torch states.
                        assertEquals(level.messageHint().message().t(Language.ENGLISH), hint, where);
                        messages.add(hint);
                    }
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
        }
        assertEquals(Set.of(Msg.HINT_TUTORIAL_WALK.t(Language.ENGLISH), Msg.HINT_LEVER_TORCH.t(Language.ENGLISH),
            Msg.HINT_LEVER_EXIT.t(Language.ENGLISH)), messages);
        for (String text : messages) assertFalse(text.toLowerCase().contains("dark"), "State-dependent wording: " + text);
    }

    @Test void theLeverHintNamesTheActionAndFlipsWithTheState() {
        Level level = freshLevel();
        walkToLever(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_ON.t(Language.ENGLISH)), level.player.contextHint());
        assertTrue(level.player.contextHint().contains("W + K") || level.player.contextHint().contains("Up"), level.player.contextHint());
        operate(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_OFF.t(Language.ENGLISH)), level.player.contextHint());
    }

    @Test void masteredRouteTimeIsMeasured() {
        Level level = freshLevel();
        int frames = walkToLever(level);
        frames += operate(level);
        frames += walkToExit(level);
        float seconds = frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-0-lever mastered route (walk, operate lever, exit): %.2f s%n", seconds);
        assertTrue(seconds < 30, "Author check, not a novice playtest (#129-#131): " + seconds);
    }
}
