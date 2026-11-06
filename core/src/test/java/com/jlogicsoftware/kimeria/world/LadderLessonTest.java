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
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.HintGeometry;
import com.jlogicsoftware.kimeria.ui.HintLayout;
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

    private static final float LEDGE_TOP = 208, LADDER_X = 1216, LEDGE_X = 1232;
    /** The thorn root (D48, first thing in the room, before the lever at 1080): footprint x 896-912, zone 64 px each side. */
    private static final float ROOT_X = 896, ROOT_RIGHT = 912, ROOT_ZONE_LEFT = 832;

    private ThornTrap root(Level level) {
        return level.objects.stream().filter(ThornTrap.class::isInstance).map(ThornTrap.class::cast).findFirst().orElseThrow();
    }

    /** Geometry sweeps teleport the hero onto the thorn's own column; the trap has its own tests below. */
    private void withoutTheThorns(Level level) { level.objects.removeIf(ThornTrap.class::isInstance); }

    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private float bodyLeft(Level level) { return level.player.hitboxBounds().x; }

    /** Mastered jump: Jump pressed about 30 px before the root, going right (body right edge) or left (body left edge). */
    private boolean jumpsTheRoot(Level level, boolean right) {
        if (!level.player.isOnGround()) return false;
        return right ? bodyRight(level) >= ROOT_X - 32 && bodyRight(level) < ROOT_X - 8
            : bodyLeft(level) <= ROOT_RIGHT + 32 && bodyLeft(level) > ROOT_RIGHT + 8;
    }

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
            if (jumpsTheRoot(level, true)) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
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
        while (level.player.hitboxBounds().x + level.player.hitboxBounds().width / 2 < LADDER_X + 1) {
            held.clear(); held.add(Input.Keys.D);
            if (jumpsTheRoot(level, true)) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
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
        for (float x = 608; x <= LADDER_X + 10; x += 6) {
            Level level = freshRoom();
            withoutTheThorns(level);
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
            while (level.player.position.x + 24 > LEDGE_X + 6) { held.clear(); held.add(Input.Keys.A); frame(level); }
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
        return level.player.isOnGround() && feet(level) <= LEDGE_TOP + 1 && b.x + b.width / 2 < LEDGE_X;
    }

    /** Walks Left until the lever is in reach. */
    private void walkBackToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(Input.Keys.A);
            if (jumpsTheRoot(level, false)) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
            assertTrue(++frames < 60 * 20, "lever never came into reach at " + level.player.position);
        }
        held.clear();
    }

    /**
     * #211: floor, jump apex, ledge and climbing heights, ladder raised and lowered: wherever both channels
     * show, the message and the live label are at least the gap apart (camera 0 and 8 px down).
     */
    @Test void messageAndLiveLabelsNeverOverlapOnTheFloorInTheAirOnTheLedgeOrOnTheLadder() {
        Level level = freshRoom();
        withoutTheThorns(level);
        HintGeometry geometry = new HintGeometry();
        Vector2 zoneAnchor = new Vector2(), contextAnchor = new Vector2();
        int both = 0;
        for (boolean lowered : new boolean[]{false, true}) {
            if (ladder(level).isLowered() != lowered) ladder(level).performAction();
            settle(level, 60);
            for (float standY : new float[]{272, 272 - 58, 160, 160 - 58, 130})
                for (float x = 600; x <= 1432; x += 4) {
                    level.player.position.set(x - 24, standY);
                    settle(level, 2);
                    String zone = level.player.zoneHint(), context = level.player.contextHint();
                    if (zone.isEmpty() || context.isEmpty()) continue;
                    both++;
                    level.player.zoneHintPosition(zoneAnchor); level.player.contextHintPosition(contextAnchor);
                    for (float cameraY : new float[]{0, 8})
                        assertTrue(geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY) >= HintLayout.GAP - .001,
                            "x=" + x + " y=" + standY + " lowered=" + lowered + " cameraY=" + cameraY + " " + context.replace('\n', '|')
                                + " / " + zone + " gap=" + geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY));
                    // The label never crosses a hero standing on the floor. Only a hero airborne or climbing right
                    // under the message has too little room for the 24 px gap, and then the lowered label may touch
                    // the top of the hitbox by a few px (D33: the one case not avoided; measured 10 px on the ledge since the
                    // 39 px message of #249 is taller, so the bound is 12 instead of 8, see D49).
                    Rectangle hero = level.player.hitboxBounds();
                    for (float cameraY : new float[]{0, 8}) {
                        float over = geometry.heroOverlap(contextAnchor, context, zoneAnchor, zone, cameraY, hero.x, hero.x + hero.width, hero.y, hero.y + hero.height);
                        assertTrue(over <= (standY == 272 ? 0 : 8), "label crosses the hero by " + over + " at x=" + x + " y=" + standY + " " + context.replace('\n', '|'));
                    }
                }
        }
        assertTrue(both > 20, "label and message do share positions in this room: " + both);
    }

    @Test void theLeverIsOnlyEverWithinReachOnTheFloorAwayFromTheLadder() {
        Level level = freshRoom();
        withoutTheThorns(level);
        Ladder ladder = ladder(level);
        for (float standY : new float[]{272, 160, 100}) // floor, ledge, climbing height
            for (float x = 600; x <= 1432; x += 4) {
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
        withoutTheThorns(level);
        Set<String> messages = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (boolean lowered : new boolean[]{false, true}) {
                for (float x = 608; x < 1448; x += 8) {
                    if (ladder(level).isLowered() != lowered) ladder(level).performAction();
                    settle(level, 60);
                    if (x > LADDER_X && x < LEDGE_X + 8) continue; // inside the ledge block: not a standing position
                    boolean onLedge = x >= LEDGE_X + 8;
                    level.player.position.set(x - 24, onLedge ? 160 : 272);
                    for (int i = 0; i < 3; i++) frame(level);
                    String hint = level.player.contextHint(), zone = level.player.zoneHint();
                    String where = "x=" + x + " lowered=" + lowered + ": " + hint + " / " + zone;
                    // #211: the zone message is never replaced by the live lever/ladder label; both show together.
                    // Message zones know only position, so each text must be true in both ladder states.
                    assertEquals(level.messageHint().message().t(Language.ENGLISH), zone, where);
                    messages.add(zone);
                    if (level.player.canInteract()) {
                        assertTrue(hint.contains(Msg.LEVER.t(Language.ENGLISH)), where);
                        assertTrue(hint.contains((lowered ? Msg.TURN_OFF : Msg.TURN_ON).t(Language.ENGLISH)), where);
                    } else if (!hint.isEmpty()) {
                        assertTrue(hint.endsWith(": " + Msg.CLIMB.t(Language.ENGLISH)) && lowered && !onLedge,
                            "Only a lowered ladder invites climbing: " + where);
                    }
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
            withoutTheThorns(level);
        }
        assertEquals(Set.of(Msg.HINT_LADDER_LEVER.t(Language.ENGLISH), Msg.HINT_LADDER_ROOT.t(Language.ENGLISH),
            Msg.HINT_LADDER_CLIMB.t(Language.ENGLISH), Msg.HINT_LADDER_EXIT.t(Language.ENGLISH)), messages);
        for (String text : messages) assertFalse(text.toLowerCase().contains("is down") && text.toLowerCase().contains("is up"), text);
    }

    @Test void theLeverHintNamesTheActionAndFlipsWithTheState() {
        Level level = freshRoom();
        walkToLever(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_ON.t(Language.ENGLISH)), level.player.contextHint());
        assertFalse(level.player.zoneHint().isEmpty(), "The lever label never hides the zone message (#211)");
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

    // ---- D48: the thorn root, the first thing in the room (before the lever) ----

    private Level room(int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    /** One frame of ordinary play. */
    private void step(Level level, boolean right, boolean jump) {
        held.clear();
        if (right) held.add(Input.Keys.D);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(level);
    }

    private void placeBodyRight(Level level, float right) { level.player.position.set(right - 11 - 18, 272); settle(level, 2); }

    /** The hero stands with the body's right edge at {@code takeOff}, presses Right + Jump at once and runs on; returns true when alive well past the root. */
    private boolean jumpFrom(float takeOff, boolean waitForTheThornsFirst) {
        Level level = freshRoom();
        placeBodyRight(level, takeOff);
        if (waitForTheThornsFirst) for (int i = 0; i < 60 * 2 && root(level).phase() != ThornTrap.Phase.ARMED; i++) step(level, false, false);
        step(level, true, true);
        for (int i = 0; i < 60 * 3; i++) {
            step(level, true, false);
            if (level.player.isGotHit()) return false;
            if (bodyLeft(level) > ROOT_RIGHT + 4 && level.player.isOnGround()) return true;
        }
        return false;
    }

    @Test void theThornRootStandsOnOpenFloorBeforeTheLeverAndTheLadderAndNowhereElseInTheTutorialUpToHere() {
        Level level = freshRoom();
        ThornTrap root = root(level);
        assertEquals(1, level.objects.stream().filter(ThornTrap.class::isInstance).count(), "exactly one thorn trap");
        assertEquals(ThornTrap.Kind.ROOT, root.kind());
        assertEquals(ROOT_X, root.getX(), .01f);
        assertEquals(320, root.getY() + root.getHeight(), .01f, "standing on the floor");
        assertTrue(root.getX() - (level.player.position.x + 48) >= 150, "well after the entrance: the hero can see it");
        assertTrue(lever(level).getX() - (root.getX() + root.getWidth() + 64) >= 64, "the lever is clear of the root's zone");
        assertTrue(ladder(level).getX() - (root.getX() + root.getWidth()) >= 64, "64 px of floor beyond the root, jumpable");
        assertTrue(root.isDormant(), "quiet until the hero comes near");
        assertTrue(room(0).objects.stream().noneMatch(ThornTrap.class::isInstance), "the lever room stays pure");
        assertTrue(room(2).objects.stream().noneMatch(ThornTrap.class::isInstance), "no leak into the next room");
        assertEquals(ROOT_ZONE_LEFT, root.triggerZone().x, .01f);
    }

    @Test void theRootTelegraphsBeforeItRisesAndRisesOnlyAfterTheSharedWarning() {
        Level level = freshRoom();
        ThornTrap root = root(level);
        placeBodyRight(level, ROOT_ZONE_LEFT - 40);
        settle(level, 60);
        assertTrue(root.isDormant(), "far from the root nothing happens");
        assertEquals(0, root.frameIndex());
        placeBodyRight(level, ROOT_ZONE_LEFT + 2);
        assertEquals(ThornTrap.Phase.WARNING, root.phase(), "entering the zone starts the warning");
        assertEquals(TrapWarnings.DEFAULT_WARNING_SECONDS, root.warningTotal(), 1e-4f);
        assertTrue(root.frameIndex() == 1 || root.frameIndex() == 2, "the tip wobbles: " + root.frameIndex());
        assertFalse(root.isLethal(), "harmless while it warns");
        settle(level, (int) (TrapWarnings.DEFAULT_WARNING_SECONDS * 60) + 12);
        assertTrue(root.isLethal() && root.phase() == ThornTrap.Phase.ARMED);
        assertEquals(4, root.frameIndex());
    }

    @Test void aRunningJumpFromAnyPointOfTheWindowClearsTheRootAndAHeroWhoWaitsForItToRiseCanJumpToo() {
        int first = -1, last = -1;
        for (float takeOff = ROOT_X - 70; takeOff <= ROOT_X - 2; takeOff += 2) {
            boolean ok = jumpFrom(takeOff, false);
            if (ok) { if (first < 0) first = (int) takeOff; last = (int) takeOff; }
        }
        System.out.println("forest-0-ladder root jump window (body right edge at take-off, root at " + ROOT_X + "): " + first + " .. " + last
            + " (" + (ROOT_X - last) + " .. " + (ROOT_X - first) + " px before the root)");
        assertTrue(first >= 0 && first <= ROOT_X - 56 && last >= ROOT_X - 12, "a generous window: " + first + " .. " + last);
        for (float takeOff = first; takeOff <= last; takeOff += 2) assertTrue(jumpFrom(takeOff, false), "inside the window at " + takeOff);
        for (float stand : new float[]{ROOT_X - 56, ROOT_X - 40, ROOT_X - 24, ROOT_X - 12})
            assertTrue(jumpFrom(stand, true), "waiting for the thorns to be up, then jumping from " + stand);
    }

    @Test void walkingStraightIntoTheRootCostsOneVeryCheapRetryAndTheRetryRestoresTheTrap() {
        Level level = freshRoom();
        int frames = 0;
        while (!level.player.isGotHit()) {
            step(level, true, false);
            assertTrue(++frames < 60 * 10, "never hit at " + level.player.position);
        }
        System.out.printf(java.util.Locale.ROOT, "forest-0-ladder: walking straight into the root hits after %.2f s%n", frames * STEP);
        assertTrue(root(level).isLethal(), "the thorns were up when they hit");
        assertTrue(bodyRight(level) > ROOT_X, "hit on the root, not elsewhere: " + level.player.position);
        assertTrue(frames * STEP < 4f, "the mistake is made within the first seconds: " + frames * STEP);
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertTrue(root(level).isDormant(), "retry restores the hidden root");
        assertFalse(ladder(level).isLowered());
        assertTrue(level.player.position.x < 700, "the hero starts at the entrance");
        float seconds = solve(level) * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-0-ladder retry after the thorn root: %.2f s from the entrance to the exit%n", seconds);
        assertTrue(level.atRoomExit());
        assertTrue(seconds < 15, "one cheap retry: " + seconds);
    }

    @Test void theRootNeverStrandsTheHeroAfterTheTrapIsUp() {
        Level level = freshRoom();
        placeBodyRight(level, ROOT_X - 24);
        for (int i = 0; i < 60 * 2; i++) step(level, false, false);
        assertTrue(root(level).isLethal());
        assertTrue(level.player.canFight() && !level.player.isGotHit());
        solve(level); // jumps the raised thorns, pulls the lever, climbs, exits
        assertTrue(level.atRoomExit());
        assertFalse(level.player.isGotHit());
    }

    /**
     * The hero who has passed the root, climbed the ladder and wants to go back for the lever: holding Left through the
     * whole drop off the ledge (item from the #235 review) or Left + Jump after landing never meets the armed root,
     * which now stands more than 200 px from the ladder.
     */
    @Test void holdingLeftThroughTheWholeDropFromTheLadderTopOrLeftAndJumpAfterwardsNeverReachesTheRoot() {
        for (boolean jumpAfterLanding : new boolean[]{false, true}) {
            Level level = freshRoom();
            solveUpToTheLedge(level);
            assertTrue(root(level).isLethal(), "the root has been up since the way in");
            int frames = 0;
            boolean landed = false;
            while (frames++ < 60 * 8) {
                held.clear(); held.add(Input.Keys.A);
                if (landed && jumpAfterLanding && level.player.isOnGround()) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(level);
                assertFalse(level.player.isGotHit(), "hit at " + level.player.position + " after " + frames + " frames");
                if (level.player.isOnGround() && feet(level) > 300) landed = true;
                if (landed && bodyLeft(level) < LADDER_X - 90) break;
            }
            assertTrue(landed && bodyLeft(level) < LADDER_X - 90, "walked on to the left");
            assertTrue(bodyLeft(level) > ROOT_RIGHT + 40, "and was nowhere near the root yet: " + bodyLeft(level));
        }
    }

    private void solveUpToTheLedge(Level level) {
        walkToLever(level); operate(level);
        walkToLadder(level); climb(level);
    }
}
