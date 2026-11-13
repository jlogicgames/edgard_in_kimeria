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
import com.jlogicsoftware.kimeria.entity.items.Wall;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.objects.FallingBranch;
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
 * The forest-0 wall lesson room (#197): ordinary keyboard controls drive the hero through the real
 * map. A lever raises a wall that blocks the corridor and lowers it again. Times are author (mastered)
 * measurements, not novice playtests. Recipe: docs/ROOMS.md, rule: D36.
 */
class WallLessonTest {
    private static final float STEP = 1f / 60f;

    private final java.util.List<String> sounds = new java.util.ArrayList<>(), loops = new java.util.ArrayList<>();
    private final java.util.List<Float> volumes = new java.util.ArrayList<>();
    private boolean soundsOn = true;
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
            case "playSounds" -> soundsOn;
            case "playSound" -> { if (args.length > 1) volumes.add((Float) args[1]); yield sounds.add((String) args[0]); }
            case "loopSound" -> loops.add("loop:" + args[0]);
            case "stopSound" -> loops.add("stop:" + args[0]);
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

    private static final float WALL_X = 2096, WALL_RIGHT = 2144, ROOM_X = 1472;
    /** The falling branch (D48): column x 1744-1776, trigger zone x 1728-1776. */
    private static final float BRANCH_X = 1744, BRANCH_RIGHT = 1776, BRANCH_ZONE_X = 1728;

    private FallingBranch branch(Level level) {
        return level.objects.stream().filter(FallingBranch.class::isInstance).map(FallingBranch.class::cast).findFirst().orElseThrow();
    }

    /** Geometry sweeps teleport the hero into the branch's column and let him stand there; the trap has its own tests. */
    private void withoutTheBranch(Level level) { level.objects.removeIf(FallingBranch.class::isInstance); }

    /** A level whose hero stands in the wall room (index 2) at its safe entrance. */
    private Level freshRoom() {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        level.enterNextRoom(new Player(assets, game, 0, 0));
        level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private Wall wall(Level level) {
        return level.objects.stream().filter(Wall.class::isInstance).map(Wall.class::cast).findFirst().orElseThrow();
    }

    private Lever lever(Level level) {
        return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow();
    }

    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }

    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }

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

    /** Up + Action, as the hint teaches: Action is a fresh press while Up is held. */
    private int operate(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    private int walkToExit(Level level) {
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
            assertTrue(++frames < 60 * 30, "exit unreachable from " + level.player.position + " wallY=" + wall(level).getY());
        }
        held.clear();
        return frames;
    }

    /** The whole taught route from a standing start; returns elapsed frames. */
    private int solve(Level level) {
        int frames = walkToLever(level);
        if (!wall(level).isRaised()) frames += operate(level);
        assertTrue(wall(level).isRaised());
        frames += walkToExit(level);
        return frames;
    }

    @Test void theRoomOffersOneLeverAndOneLoweredWallAndNothingElse() {
        Level level = freshRoom();
        assertEquals(1, level.objects.stream().filter(Wall.class::isInstance).count());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.triggers.size());
        assertTrue(level.ladders.isEmpty());
        assertFalse(wall(level).isRaised(), "The wall starts down, blocking the route");
        assertFalse(lever(level).isUp());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>));
        assertEquals("forest-0-wall", level.currentRoomName());
        assertEquals(320, wall(level).getY() + wall(level).getHeight(), 0.01f, "The lowered wall stands on the floor");
        assertTrue(wall(level).getHeight() >= 96, "Taller than a jump");
    }

    @Test void theLeverTogglesTheWallUpAndDownAndShowsIt() {
        Level level = freshRoom();
        walkToLever(level);
        float lowerY = wall(level).getY();
        for (int round = 0; round < 3; round++) {
            operate(level);
            assertTrue(wall(level).isRaised()); assertTrue(lever(level).isUp());
            settle(level, 120);
            assertEquals(lowerY - wall(level).getHeight(), wall(level).getY(), 0.01f, "Raised, it clears a full body height");
            assertTrue(wall(level).getY() + wall(level).getHeight() <= 320 - 90, "Gap of at least 90 px under the raised wall");
            operate(level);
            assertFalse(wall(level).isRaised()); assertFalse(lever(level).isUp());
            settle(level, 120);
            assertEquals(lowerY, wall(level).getY(), 0.01f, "The lever toggles; it never fires once and locks");
        }
        assertTrue(Math.abs(wall(level).getX() - level.player.centerX()) < 320, "The wall is in view of the lever");
    }

    @Test void withTheWallLoweredNeitherWalkingNorJumpingGetsPast() {
        for (float x = ROOM_X + 32; x <= WALL_X - 20; x += 12) {
            Level level = freshRoom();
            withoutTheBranch(level);
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 60 * 4; i++) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                if (i % 30 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(level);
                assertTrue(bodyRight(level) <= WALL_X + 1, "Hero got into/over the wall from x=" + x + " at " + level.player.position);
                assertFalse(level.atRoomExit(), "x=" + x);
            }
        }
    }

    @Test void theTaughtRouteRaisesTheWallAndWalksUnderItToTheExit() {
        Level level = freshRoom();
        solve(level);
        assertTrue(level.atRoomExit());
        assertTrue(wall(level).isRaised());
    }

    @Test void theWallNeverCrushesBecauseTheLeverIsOutOfReachBesideTheWallAndPastIt() {
        Level level = freshRoom();
        withoutTheBranch(level);
        for (boolean raised : new boolean[]{false, true}) {
            if (wall(level).isRaised() != raised) wall(level).performAction();
            settle(level, 120);
            for (float x = ROOM_X + 40; x <= ROOM_X + 744; x += 4) {
                level.player.position.set(x - 24, 272);
                settle(level, 1);
                if (level.player.canInteract())
                    assertTrue(x < WALL_X - 120, "The lever can only be pulled well clear of the wall (x=" + x + ")");
            }
        }
    }

    @Test void theExitIsReachableAfterRaisingLoweringPassingBackRetryDeathAndRestart() {
        // Raise, pass under, come back, lower it, raise it again, finish: the lever stays on the entrance side.
        Level level = freshRoom();
        walkToLever(level); operate(level);
        while (bodyRight(level) < WALL_RIGHT + 8) { held.clear(); held.add(Input.Keys.D); frame(level); assertFalse(level.player.isGotHit()); }
        held.clear();
        assertTrue(wall(level).isRaised(), "Passing under changes nothing");
        while (!level.player.canInteract()) {
            held.clear(); held.add(Input.Keys.A); frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
            assertTrue(level.player.position.x > ROOM_X, "stuck at " + level.player.position);
        }
        held.clear();
        operate(level);
        assertFalse(wall(level).isRaised(), "Lowering from the entrance side is always possible");
        settle(level, 120);
        walkToLever(level);
        solve(level);
        assertTrue(level.atRoomExit());

        // Death while standing under the raised wall: the room restarts with the authored lowered wall.
        Level under = freshRoom();
        walkToLever(under); operate(under);
        while (bodyRight(under) < WALL_X + 24) { held.clear(); held.add(Input.Keys.D); frame(under); }
        held.clear(); settle(under, 5);
        assertTrue(bodyRight(under) > WALL_X, "The hero is under the wall");
        under.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(wall(under).isRaised(), "Retry restores the authored lowered wall");
        assertEquals(224, wall(under).getY(), 0.01f);
        assertFalse(lever(under).isUp());
        assertTrue(under.player.position.x < WALL_X - 100, "and the hero starts on the entrance side");
        solve(under);

        // Retry mid-motion, before the wall finished rising.
        Level mid = freshRoom();
        walkToLever(mid); operate(mid); settle(mid, 20);
        mid.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(wall(mid).isRaised()); assertEquals(224, wall(mid).getY(), 0.01f);
        solve(mid);

        // Restart Level rebuilds the whole level from the map.
        Level restarted = freshRoom();
        assertFalse(wall(restarted).isRaised());
        solve(restarted);
    }

    @Test void raisingAndLoweringRepeatedlyAtAnyMomentNeverTrapsTheHero() {
        for (int delay = 0; delay <= 120; delay += 15) {
            Level level = freshRoom();
            walkToLever(level);
            for (int i = 0; i < 4; i++) { operate(level); settle(level, delay); }
            if (!wall(level).isRaised()) operate(level);
            assertTrue(wall(level).isRaised());
            solve(level);
            assertTrue(level.atRoomExit(), "delay " + delay);
        }
    }

    /**
     * #211: floor and jump apex, wall lowered and raised: wherever both channels show, the message and the
     * live label are at least the gap apart (camera 0 and 8 px down).
     */
    @Test void messageAndLiveLabelsNeverOverlapOnTheFloorOrInTheAir() {
        Level level = freshRoom();
        withoutTheBranch(level);
        HintGeometry geometry = new HintGeometry();
        Vector2 zoneAnchor = new Vector2(), contextAnchor = new Vector2();
        int both = 0;
        for (boolean raised : new boolean[]{false, true}) {
            if (wall(level).isRaised() != raised) wall(level).performAction();
            settle(level, 120);
            for (float standY : new float[]{272, 272 - 58})
                for (float x = ROOM_X + 24; x <= ROOM_X + 754; x += 4) {
                    if (!raised && x > WALL_X && x < WALL_RIGHT + 24) continue;
                    level.player.position.set(x - 24, standY);
                    settle(level, 2);
                    String zone = level.player.zoneHint(), context = level.player.contextHint();
                    if (zone.isEmpty() || context.isEmpty()) continue;
                    both++;
                    level.player.zoneHintPosition(zoneAnchor); level.player.contextHintPosition(contextAnchor);
                    for (float cameraY : new float[]{0, 8})
                        assertTrue(geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY) >= HintLayout.GAP - .001,
                            "x=" + x + " y=" + standY + " raised=" + raised + " cameraY=" + cameraY + " " + context.replace('\n', '|')
                                + " / " + zone + " gap=" + geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY));
                }
        }
        assertTrue(both > 5, "label and message do share positions in this room: " + both);
    }

    @Test void lessonHintsCoverEveryStandingPositionInBothWallStatesAndSurviveRetry() {
        Level level = freshRoom();
        withoutTheBranch(level);
        Set<String> messages = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (boolean raised : new boolean[]{false, true}) {
                for (float x = ROOM_X + 24; x < ROOM_X + 768; x += 8) {
                    if (wall(level).isRaised() != raised) wall(level).performAction();
                    settle(level, 120);
                    if (!raised && x > WALL_X - 24 && x < WALL_RIGHT + 24) continue; // inside the lowered wall
                    level.player.position.set(x - 24, 272);
                    for (int i = 0; i < 3; i++) frame(level);
                    String hint = level.player.contextHint(), zone = level.player.zoneHint();
                    String where = "x=" + x + " raised=" + raised + ": " + hint + " / " + zone;
                    // #211: the zone message is never replaced by the live lever label; both show together.
                    // Message zones know only position, so each text must be true in both wall states.
                    assertEquals(level.messageHint().message().t(Language.ENGLISH), zone, where);
                    messages.add(zone);
                    if (level.player.canInteract()) {
                        assertTrue(hint.contains(Msg.LEVER.t(Language.ENGLISH)), where);
                        assertTrue(hint.contains((raised ? Msg.TURN_OFF : Msg.TURN_ON).t(Language.ENGLISH)), where);
                    } else assertTrue(hint.isEmpty(), "Only the lever has a live label here: " + where);
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
            withoutTheBranch(level);
        }
        assertEquals(Set.of(Msg.HINT_WALL_LEVER.t(Language.ENGLISH), Msg.HINT_WALL_BRANCH.t(Language.ENGLISH), Msg.HINT_WALL_PASS.t(Language.ENGLISH)), messages);
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
        System.out.printf(java.util.Locale.ROOT, "forest-0-wall mastered route (walk, pull lever, walk under the wall, exit): %.2f s%n", seconds);
        assertTrue(seconds < 30, "Author check, not a novice playtest (#129-#131): " + seconds);
    }

    // ---- D48: the falling branch between the lever and the wall ----

    private void step(Level level, boolean right, boolean jump) {
        held.clear();
        if (right) held.add(Input.Keys.D);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(level);
    }

    private float bodyLeft(Level level) { return level.player.hitboxBounds().x; }
    private void placeBodyRight(Level level, float right) { level.player.position.set(right - 11 - 18, 272); settle(level, 2); }

    private Level otherRoom(int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    @Test void theBranchHangsBeforeTheLeverClearOfItAndOfTheWallAndIsTheOnlyFallingTrapHere() {
        Level level = freshRoom();
        FallingBranch branch = branch(level);
        assertEquals(1, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.FallingTrap.class::isInstance).count());
        assertEquals(FallingBranch.State.ARMED, branch.state());
        assertEquals(BRANCH_X, branch.bodyBounds().x, .01f);
        assertTrue(lever(level).getX() - (branch.bodyBounds().x + branch.bodyBounds().width) >= 128, "well clear of the lever (the branch cannot drop onto it) and before it");
        assertTrue(WALL_X - branch.bodyBounds().x - branch.bodyBounds().width >= 96, "and well clear of the wall");
        assertTrue(320 - (branch.bodyBounds().y + branch.bodyBounds().height) >= 48, "hangs at least 48 px above the floor, readable");
        assertEquals(320, branch.landingBottom(), .01f, "it falls to the floor");
        assertEquals(BRANCH_ZONE_X, branch.zone().x, .01f);
        assertTrue(otherRoom(1).objects.stream().noneMatch(FallingBranch.class::isInstance), "no leak into the ladder room");
        assertTrue(otherRoom(3).objects.stream().noneMatch(FallingBranch.class::isInstance), "nor into the platform room");
    }

    @Test void theBranchRustlesThenSagsForTheSharedWarningBeforeItFalls() {
        Level level = freshRoom();
        FallingBranch branch = branch(level);
        placeBodyRight(level, BRANCH_ZONE_X - 30);
        settle(level, 30);
        assertEquals(FallingBranch.State.ARMED, branch.state());
        assertEquals(0, branch.frame(), "resting");
        placeBodyRight(level, BRANCH_ZONE_X + 2);
        assertEquals(FallingBranch.State.WARNING, branch.state());
        java.util.Set<Integer> frames = new java.util.HashSet<>();
        while (branch.state() == FallingBranch.State.WARNING) { settle(level, 1); frames.add(branch.frame()); }
        assertTrue(frames.contains(1) && frames.contains(2), "rustle and sag are shown: " + frames);
        assertEquals(FallingBranch.State.FALLING, branch.state());
    }

    @Test void walkingOnPastTheBranchIsSafeFromEveryStartAndAJumpFromAnyPointOfTheZoneWorksToo() {
        // Walking: the hero enters the zone at any speed-of-play offset and keeps going.
        for (float from = BRANCH_ZONE_X - 60; from <= BRANCH_ZONE_X + 40; from += 4) {
            Level level = freshRoom();
            placeBodyRight(level, from);
            for (int i = 0; i < 60 * 3 && bodyLeft(level) < BRANCH_RIGHT + 30; i++) {
                step(level, true, false);
                assertFalse(level.player.isGotHit(), "walking on from " + from + " died at " + level.player.position);
            }
            settle(level, 80);
            assertFalse(level.player.isGotHit(), "from " + from);
            assertTrue(branch(level).isSpent(), "the branch fell behind the hero");
        }
        // Running jumps from the whole length of the zone and the column.
        for (float from = BRANCH_ZONE_X - 40; from <= BRANCH_RIGHT; from += 4) {
            Level level = freshRoom();
            placeBodyRight(level, from);
            step(level, true, true);
            for (int i = 0; i < 60 * 2 && bodyLeft(level) < BRANCH_RIGHT + 30; i++) {
                step(level, true, false);
                assertFalse(level.player.isGotHit(), "jumping on from " + from + " died at " + level.player.position);
            }
            settle(level, 80);
            assertFalse(level.player.isGotHit(), "jumping from " + from);
        }
    }

    @Test void standingStillUnderTheBranchCostsOneRetryAndTheRetryRestoresTheBranchAndTheWall() {
        Level level = freshRoom();
        placeBodyRight(level, BRANCH_X + 20);
        int frames = 0;
        while (!level.player.isGotHit() && frames++ < 60 * 4) step(level, false, false);
        assertTrue(level.player.isGotHit(), "a hero who waits under the branch is hit");
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertEquals(FallingBranch.State.ARMED, branch(level).state(), "retry re-arms the branch");
        assertFalse(wall(level).isRaised(), "and lowers the wall");
        assertFalse(lever(level).isUp());
        float seconds = solve(level) * STEP;
        assertTrue(level.atRoomExit());
        assertFalse(level.player.isGotHit());
        System.out.printf(java.util.Locale.ROOT, "forest-0-wall retry after the branch: %.2f s from the entrance to the exit%n", seconds);
        assertTrue(seconds < 15, "one cheap retry: " + seconds);
    }

    @Test void aHeroWhoStepsBackOutOfTheColumnAfterTheTriggerIsNotHurtAndTheRouteStaysOpen() {
        Level level = freshRoom();
        placeBodyRight(level, BRANCH_ZONE_X + 6);
        settle(level, 2);
        for (int i = 0; i < 60 * 2; i++) {
            held.clear(); held.add(Input.Keys.A); frame(level);
            assertFalse(level.player.isGotHit(), "at " + level.player.position);
            if (bodyLeft(level) < BRANCH_ZONE_X - 40) break;
        }
        held.clear();
        settle(level, 80);
        assertTrue(branch(level).isSpent());
        // The landed branch is scenery: the lever and the exit are still reached, wall raised.
        solve(level);
        assertTrue(level.atRoomExit());
        assertFalse(level.player.isGotHit());
    }

    @Test void theBranchIsIndependentOfTheWallLeverStateAndSoftLockFree() {
        for (boolean raisedFirst : new boolean[]{false, true}) {
            Level level = freshRoom();
            walkToLever(level);
            if (raisedFirst) operate(level);
            settle(level, 120);
            solve(level);
            assertTrue(level.atRoomExit(), "raisedFirst=" + raisedFirst);
            assertTrue(branch(level).isSpent() || branch(level).state() != FallingBranch.State.FALLING);
        }
    }

    @Test void aLeverThatMovesTheWallStartsOneShakeForItsMoveTime() {
        Level level = freshRoom();
        assertFalse(level.fx().shake().running());
        walkToLever(level);
        operate(level);
        assertTrue(wall(level).isRaised());
        assertTrue(level.fx().shake().running());
        // Started with the lever amplitude; at most the three operating frames have decayed it.
        float amplitude = level.fx().shake().currentAmplitude();
        assertTrue(amplitude > com.jlogicsoftware.kimeria.effects.ScreenShake.LEVER_AMPLITUDE_PX * .95f
            && amplitude <= com.jlogicsoftware.kimeria.effects.ScreenShake.LEVER_AMPLITUDE_PX, "amplitude " + amplitude);
        // It lasts the wall's own move time (authored 1.5 s in forest-0; 1 s when the map says nothing) and then stops.
        float seconds = wall(level).moveDurationSeconds();
        assertTrue(seconds >= Wall.DEFAULT_MOVE_DURATION_SECONDS);
        settle(level, Math.round(seconds * 60) - 10);
        assertTrue(level.fx().shake().running(), "still running shortly before the move time is over");
        settle(level, 20);
        assertFalse(level.fx().shake().running());
    }

    @Test void aWallThatCannotMoveStartsNoShake() {
        Level level = freshRoom();
        walkToLever(level);
        wall(level).setActive(false);
        operate(level);
        assertFalse(level.fx().shake().running());
    }

    @Test void aLeverThatLowersTheWallStartsOneGritBurstAndOneRumbleAndOneGritSound() {
        Level level = freshRoom();
        walkToLever(level);
        sounds.clear(); loops.clear();
        assertEquals(0, level.fx().grit().count());
        operate(level);
        assertTrue(level.fx().grit().count() > 0, "the burst started");
        assertEquals(1, loops.stream().filter("loop:wall_rumble"::equals).count());
        assertEquals(1, sounds.stream().filter("grit_fall"::equals).count());
        float top = level.currentRoomBounds().y;
        for (int i = 0; i < level.fx().grit().count(); i++) assertTrue(level.fx().grit().y(i) >= top);
        // The burst is a single one of 24: after it is over nothing is left and nothing more is requested.
        settle(level, 120);
        assertEquals(0, level.fx().grit().count());
        assertEquals(1, loops.stream().filter("loop:wall_rumble"::equals).count());
    }

    @Test void withSoundOffTheLeverRequestsNoSoundButStillDropsGrit() {
        soundsOn = false;
        Level level = freshRoom();
        walkToLever(level);
        operate(level);
        assertTrue(sounds.isEmpty() && loops.isEmpty(), sounds + " " + loops);
        assertTrue(level.fx().grit().count() > 0);
    }

    @Test void aWallThatCannotMoveStartsNoGritAndNoSound() {
        Level level = freshRoom();
        walkToLever(level);
        wall(level).setActive(false);
        sounds.clear(); loops.clear();
        operate(level);
        assertEquals(0, level.fx().grit().count());
        assertTrue(sounds.stream().noneMatch("grit_fall"::equals) && loops.isEmpty(), sounds + " " + loops);
    }

    @Test void aGroundJumpEmitsOnePuffAndRequestsOneDustSound() {
        Level level = freshRoom();
        settle(level, 30);
        assertTrue(level.player.isOnGround());
        sounds.clear();
        Rectangle body = level.player.hitboxBounds();
        float feetY = body.y + body.height, centreX = body.x + body.width / 2f;
        held.clear(); held.add(Input.Keys.J); pressed.add(Input.Keys.J);
        frame(level);
        held.clear();
        assertEquals(1, sounds.stream().filter("dust_puff"::equals).count());
        assertEquals(1, sounds.stream().filter("jump"::equals).count());
        assertEquals(com.jlogicsoftware.kimeria.effects.EnvironmentFx.DUST_PER_PUFF, level.fx().dust().count());
        for (int i = 0; i < level.fx().dust().count(); i++) {
            assertEquals(feetY, level.fx().dust().y(i), 3f, "puffs start at the feet line, not the sprite top");
            assertTrue(Math.abs(level.fx().dust().x(i) - centreX) <= 9f);
        }
    }

    @Test void withSoundOffAJumpRequestsNoDustSound() {
        soundsOn = false;
        Level level = freshRoom();
        settle(level, 30);
        held.clear(); held.add(Input.Keys.J); pressed.add(Input.Keys.J);
        frame(level);
        assertTrue(sounds.isEmpty(), sounds.toString());
        assertEquals(com.jlogicsoftware.kimeria.effects.EnvironmentFx.DUST_PER_PUFF, level.fx().dust().count());
    }

    // ---- landing dust (feel fix)

    @Test void aRealLandingEmitsAQuieterSmallerPuffAtTheFeet() {
        Level level = freshRoom();
        settle(level, 30);
        held.clear(); held.add(Input.Keys.J); pressed.add(Input.Keys.J);
        frame(level);
        held.clear();
        sounds.clear(); volumes.clear(); level.fx().clear();
        int landed = -1;
        for (int i = 0; i < 200 && landed < 0; i++) {
            frame(level);
            if (sounds.contains("dust_puff")) landed = i;
        }
        assertTrue(landed >= 0, "a landing sound and puff must follow the jump");
        assertEquals(1, sounds.stream().filter("dust_puff"::equals).count(), "one scuff for one landing");
        assertEquals(1, volumes.size());
        assertTrue(volumes.get(0) > 0 && volumes.get(0) < 1, "quieter than the jump's scuff: " + volumes);
        assertEquals(com.jlogicsoftware.kimeria.effects.EnvironmentFx.DUST_PER_LANDING, level.fx().dust().count());
        Rectangle body = level.player.hitboxBounds();
        for (int i = 0; i < level.fx().dust().count(); i++) {
            assertEquals(body.y + body.height, level.fx().dust().y(i), 3f, "landing puffs start at the floor line");
            assertTrue(Math.abs(level.fx().dust().x(i) - (body.x + body.width / 2f)) <= 9f);
        }
    }

    @Test void standingOrWalkingOnTheGroundMakesNoLandingPuff() {
        Level level = freshRoom();
        settle(level, 60);
        level.fx().clear(); sounds.clear();
        held.add(Input.Keys.D);
        settle(level, 60);
        held.clear();
        settle(level, 60);
        assertEquals(0, level.fx().dust().count());
        assertFalse(sounds.contains("dust_puff"), sounds.toString());
    }

    @Test void aShortStepDownBelowTheMinimumFallSpeedMakesNoPuff() {
        Level level = freshRoom();
        settle(level, 30);
        level.fx().clear(); sounds.clear();
        // Lift the hero 8 px: it falls about 8 px, far slower than LANDING_MIN_FALL_SPEED when it touches the floor.
        level.player.position.y -= 8;
        level.player.setOnGround(false);
        settle(level, 20);
        assertTrue(level.player.isOnGround());
        assertEquals(0, level.fx().dust().count());
        assertFalse(sounds.contains("dust_puff"), sounds.toString());
    }

    @Test void landingWithSoundOffStillPuffsButIsSilent() {
        Level level = freshRoom();
        settle(level, 30);
        soundsOn = false; sounds.clear();
        held.clear(); held.add(Input.Keys.J); pressed.add(Input.Keys.J);
        frame(level);
        held.clear();
        for (int i = 0; i < 200 && !level.player.isOnGround(); i++) frame(level);
        assertTrue(sounds.isEmpty(), sounds.toString());
    }

    // ---- the wall rumble lasts as long as the wall moves

    @Test void theRumbleLoopsForTheWallsMoveTimeThenStopsOnce() {
        Level level = freshRoom();
        walkToLever(level);
        loops.clear();
        operate(level);
        assertEquals(java.util.List.of("loop:wall_rumble"), loops);
        float seconds = wall(level).moveDurationSeconds();
        int frames = Math.round(seconds / STEP);
        settle(level, frames - 8);
        assertEquals(java.util.List.of("loop:wall_rumble"), loops, "still rumbling while the wall moves");
        settle(level, 20);
        assertEquals(java.util.List.of("loop:wall_rumble", "stop:wall_rumble"), loops, "stopped once, when the wall stopped");
        settle(level, 120);
        assertEquals(2, loops.size());
    }

    @Test void clearingTheCameraEffectsForPauseDeathOrRoomChangeStopsTheRumbleOnce() {
        Level level = freshRoom();
        walkToLever(level);
        loops.clear();
        operate(level);
        level.clearCameraEffects();
        assertEquals(java.util.List.of("loop:wall_rumble", "stop:wall_rumble"), loops);
        level.clearCameraEffects();
        settle(level, 120);
        assertEquals(2, loops.size(), "no second stop, and the paused rumble is not resumed");
    }

    @Test void withSoundOffNoRumbleLoopStarts() {
        soundsOn = false;
        Level level = freshRoom();
        walkToLever(level);
        loops.clear();
        operate(level);
        level.clearCameraEffects();
        assertTrue(loops.isEmpty(), loops.toString());
    }
}
