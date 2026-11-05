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
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
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
 * The forest-0 ladder lesson room (#196): ordinary keyboard controls drive the hero through the real
 * map. A lever lowers a retracted ladder; the exit stands on a ledge only the ladder reaches. Times are
 * author (mastered) measurements, not novice playtests. Recipe: docs/ROOMS.md, rule: D35.
 */
class LadderLessonTest {
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

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private static final float LEDGE_TOP = 208, LADDER_X = 1032;

    /** A level whose hero stands in the ladder room (index 1) at its safe entrance. */
    private Level freshRoom() {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private Ladder ladder(Level level) { return level.ladders.get(0); }

    private Lever lever(Level level) {
        return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow();
    }

    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }

    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }

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

    /** Walks Right until the body is over the ladder's rungs; returns elapsed frames. */
    private int walkToLadder(Level level) {
        int frames = 0;
        while (level.player.hitboxBounds().x + level.player.hitboxBounds().width / 2 < 1025) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertTrue(++frames < 60 * 20, "ladder unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Holds Up (waiting for the ladder to finish lowering if need be) until the hero stands on the ledge. */
    private int climb(Level level) {
        int frames = 0;
        boolean grabbed = false;
        while (!(grabbed && !level.player.onLadder() && feet(level) <= LEDGE_TOP + 1)) {
            held.clear(); held.add(Input.Keys.W);
            frame(level);
            grabbed |= level.player.onLadder();
            assertTrue(++frames < 60 * 20, "climb stuck at " + level.player.position + " ladderY=" + ladder(level).getY());
        }
        held.clear(); frame(level);
        return frames;
    }

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

    /** The whole taught route from a standing start; returns elapsed frames. */
    private int solve(Level level) {
        int frames = walkToLever(level);
        if (!ladder(level).isLowered()) frames += operate(level);
        assertTrue(ladder(level).isLowered());
        frames += walkToLadder(level);
        frames += climb(level);
        frames += walkToExit(level);
        return frames;
    }

    @Test void theRoomOffersOneLeverAndOneRetractedLadderAndNothingElse() {
        Level level = freshRoom();
        assertEquals(1, level.ladders.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.triggers.size());
        assertFalse(ladder(level).isLowered(), "The ladder starts retracted");
        assertFalse(lever(level).isUp());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>));
        assertEquals("forest-0-ladder", level.currentRoomName());
        assertTrue(ladder(level).bottomY() <= 208, "The retracted ladder hangs a full jump above the floor");
        assertEquals(LEDGE_TOP, ladder(level).bottomY() + 0, "Retracted bottom sits at the ledge's top");
    }

    @Test void theLeverToggleLowersAndRaisesTheLadderAndTheLeverShowsIt() {
        Level level = freshRoom();
        walkToLever(level);
        operate(level);
        assertTrue(ladder(level).isLowered()); assertTrue(lever(level).isUp());
        settle(level, 60);
        assertEquals(320, ladder(level).bottomY(), 0.01f, "Lowered, it reaches the floor");
        assertEquals(LEDGE_TOP, ladder(level).topY(), 0.01f, "and its top meets the ledge");
        operate(level);
        assertFalse(ladder(level).isLowered()); assertFalse(lever(level).isUp());
        settle(level, 60);
        assertEquals(96, ladder(level).topY(), 0.01f);
        operate(level);
        assertTrue(ladder(level).isLowered(), "The lever toggles; it never fires once and locks");
        assertTrue(Math.abs(ladder(level).centerX() - level.player.centerX()) < 320, "The ladder is in view of the lever");
    }

    @Test void withTheLadderRetractedNoJumpingReachesTheLedge() {
        for (float x = 608; x <= 1034; x += 6) {
            Level level = freshRoom();
            level.player.position.set(x - 24, 272);
            float minFeet = 1000;
            for (int i = 0; i < 60 * 4; i++) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                if (i % 40 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(level);
                minFeet = Math.min(minFeet, feet(level));
                assertFalse(level.player.onLadder(), "A retracted ladder cannot be grabbed from x=" + x);
                assertFalse(level.atRoomExit(), "x=" + x);
            }
            assertTrue(minFeet < 320 - 30, "The hero really jumped (feet " + minFeet + ") from x=" + x);
            assertTrue(minFeet > LEDGE_TOP + 40, "Jumping cannot clear the ledge from x=" + x + " (feet " + minFeet + ")");
        }
    }

    @Test void theTaughtRouteClimbsTheLoweredLadderToTheExit() {
        Level level = freshRoom();
        solve(level);
        assertTrue(level.atRoomExit());
        assertTrue(feet(level) <= LEDGE_TOP + 1, "The exit is on the ledge");
    }

    @Test void theExitIsReachableAfterRaisingAgainDroppingOffTheLedgeRetryAndRestart() {
        // Walk up, step back off the ledge, and the lever is still within reach of the floor.
        Level level = freshRoom();
        walkToLever(level); operate(level);
        walkToLadder(level); climb(level);
        assertFalse(level.player.canInteract(), "The lever cannot be pulled from the ledge or ladder");
        while (!(level.player.isOnGround() && feet(level) > 300)) {
            held.clear(); held.add(Input.Keys.A);
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
            assertTrue(level.player.position.x > 608, "stuck at " + level.player.position);
        }
        held.clear();
        assertTrue(ladder(level).isLowered(), "Walking off the ledge changes nothing");
        // Raise it from the floor, then lower it and finish.
        walkBackToLever(level); operate(level);
        assertFalse(ladder(level).isLowered());
        solve(level);
        assertTrue(level.atRoomExit());

        // Death / room retry: the room is not secured, so it returns to the retracted ladder.
        Level retry = freshRoom();
        walkToLever(retry); operate(retry); settle(retry, 60);
        retry.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(ladder(retry).isLowered(), "Retry restores the authored retracted ladder");
        assertEquals(96, ladder(retry).topY(), 0.01f);
        assertFalse(lever(retry).isUp());
        solve(retry);

        // Retry from partway up the ladder: death while climbing starts the room again, still solvable.
        Level mid = freshRoom();
        walkToLever(mid); operate(mid); walkToLadder(mid);
        for (int i = 0; i < 60 * 3 && !mid.player.onLadder(); i++) { held.clear(); held.add(Input.Keys.W); frame(mid); }
        held.clear();
        mid.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(mid.player.onLadder());
        solve(mid);

        // Restart Level rebuilds the whole level from the map.
        Level restarted = freshRoom();
        assertFalse(ladder(restarted).isLowered());
        solve(restarted);
    }

    @Test void jumpingBackOffTheLedgeEdgeAtTheLadderNeverSoftLocks() {
        // A retracted ladder cannot be met from the ledge (only the lowered ladder lets the hero up, and the
        // lever is out of reach up there), so the regrab case is: ledge edge -> jump left -> the lowered
        // ladder is caught or missed. Either way the hero gets back down, reaches the lever and finishes.
        int regrabs = 0;
        for (int delay = 0; delay <= 40; delay += 4) {
            Level level = freshRoom();
            walkToLever(level); operate(level);
            walkToLadder(level); climb(level);
            while (level.player.position.x + 24 > 1046) { held.clear(); held.add(Input.Keys.A); frame(level); }
            held.clear(); settle(level, 5);
            assertTrue(level.player.isOnGround() && feet(level) <= LEDGE_TOP + 1, "On the ledge edge, delay " + delay);
            for (int i = 0; i < 90; i++) {
                // Jump with Left held for a few frames (varied), then only Up: drift over the rungs and try to grab.
                held.clear(); held.add(Input.Keys.W);
                if (i < delay / 4 + 2) held.add(Input.Keys.A);
                if (i == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(level);
                if (level.player.onLadder() || onLadderTop(level)) regrabs++;
            }
            int guard = 0;
            while (!(level.player.isOnGround() && !level.player.onLadder() && feet(level) > 300)) {
                held.clear();
                // Climb down from the rungs or the ladder's top (alternating with a step off the side).
                if (level.player.onLadder() || (onLadderTop(level) && guard % 120 < 60)) held.add(Input.Keys.S); else held.add(Input.Keys.A);
                frame(level);
                assertFalse(level.player.isGotHit(), "died at " + level.player.position);
                assertTrue(++guard < 60 * 20, "stuck at " + level.player.position + " delay " + delay);
            }
            held.clear();
            assertTrue(ladder(level).isLowered(), "Nothing the hero did changes the ladder");
            walkBackToLever(level); operate(level);
            assertFalse(ladder(level).isLowered());
            solve(level);
            assertTrue(level.atRoomExit(), "delay " + delay);
        }
        assertTrue(regrabs > 0, "At least one jump landed on or caught the lowered ladder");
    }

    /** Standing on the lowered ladder's top rung, left of the ledge. */
    private boolean onLadderTop(Level level) {
        Rectangle b = level.player.hitboxBounds();
        return level.player.isOnGround() && feet(level) <= LEDGE_TOP + 1 && b.x + b.width / 2 < 1040;
    }

    /** Walks Left until the lever is in reach. */
    private void walkBackToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(Input.Keys.A);
            frame(level);
            assertTrue(++frames < 60 * 20, "lever never came into reach at " + level.player.position);
        }
        held.clear();
    }

    @Test void theLeverIsOnlyEverWithinReachOnTheFloorAwayFromTheLadder() {
        Level level = freshRoom();
        Ladder ladder = ladder(level);
        for (float standY : new float[]{272, 160, 100}) // floor, ledge, climbing height
            for (float x = 600; x <= 1240; x += 4) {
                level.player.position.set(x - 24, standY);
                settle(level, 1);
                if (level.player.canInteract()) {
                    assertEquals(272, standY, "Only reachable standing on the floor, x=" + x);
                    assertTrue(Math.abs(ladder.centerX() - level.player.centerX()) > 120,
                        "Lever is well clear of the ladder column, x=" + x);
                }
            }
    }

    @Test void lessonHintsCoverEveryStandingPositionInBothLadderStatesAndSurviveRetry() {
        Level level = freshRoom();
        Set<String> messages = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (boolean lowered : new boolean[]{false, true}) {
                for (float x = 608; x < 1256; x += 8) {
                    if (ladder(level).isLowered() != lowered) ladder(level).performAction();
                    settle(level, 60);
                    if (x > 1032 && x < 1048) continue; // inside the ledge block: not a standing position
                    boolean onLedge = x >= 1048;
                    level.player.position.set(x - 24, onLedge ? 160 : 272);
                    for (int i = 0; i < 3; i++) frame(level);
                    String hint = level.player.contextHint();
                    String where = "x=" + x + " lowered=" + lowered + ": " + hint;
                    if (level.player.canInteract()) {
                        assertTrue(hint.contains(Msg.LEVER.t(Language.ENGLISH)), where);
                        assertTrue(hint.contains((lowered ? Msg.TURN_OFF : Msg.TURN_ON).t(Language.ENGLISH)), where);
                    } else if (level.messageHint() == null || !hint.equals(level.messageHint().message().t(Language.ENGLISH))) {
                        assertTrue(hint.endsWith(": " + Msg.CLIMB.t(Language.ENGLISH)) && lowered && !onLedge,
                            "Only a lowered ladder invites climbing: " + where);
                    } else {
                        // Message zones know only position, so each text must be true in both ladder states.
                        assertEquals(level.messageHint().message().t(Language.ENGLISH), hint, where);
                        messages.add(hint);
                    }
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
        }
        assertEquals(Set.of(Msg.HINT_LADDER_LEVER.t(Language.ENGLISH), Msg.HINT_LADDER_CLIMB.t(Language.ENGLISH),
            Msg.HINT_LADDER_EXIT.t(Language.ENGLISH)), messages);
        for (String text : messages) assertFalse(text.toLowerCase().contains("is down") && text.toLowerCase().contains("is up"), text);
    }

    @Test void theLeverHintNamesTheActionAndFlipsWithTheState() {
        Level level = freshRoom();
        walkToLever(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_ON.t(Language.ENGLISH)), level.player.contextHint());
        assertFalse(level.player.contextHintIsTutorialMessage(), "Inside the controls zone the lever label is immediate");
        operate(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_OFF.t(Language.ENGLISH)), level.player.contextHint());
    }

    @Test void masteredRouteTimeIsMeasured() {
        Level level = freshRoom();
        int frames = solve(level);
        float seconds = frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-0-ladder mastered route (walk, pull lever, climb, exit): %.2f s%n", seconds);
        assertTrue(seconds < 30, "Author check, not a novice playtest (#129-#131): " + seconds);
    }
}
