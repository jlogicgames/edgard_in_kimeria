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
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
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
 * The forest-0 platform lesson room (#198): ordinary keyboard controls drive the hero through the real
 * map. A lever starts a vertical moving platform (the existing Escalator) that is the only way up to the
 * ledge holding the exit, and stops it again where it is. Times are author (mastered) measurements, not
 * novice playtests. Recipe: docs/ROOMS.md, rule: D37.
 */
class PlatformLessonTest {
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

    private static final float ROOM_X = 1920, PLATFORM_X = 2384, PLATFORM_RIGHT = 2416, BOTTOM = 304, TOP = 176, LEDGE_TOP = 208, LEDGE_X = 2416, ROOM_END = 2560;

    /** A level whose hero stands in the platform room (index 3) at its safe entrance. */
    private Level freshRoom() {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        for (int i = 0; i < 3; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private Escalator platform(Level level) { return level.escalators.get(0); }

    private Lever lever(Level level) {
        return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow();
    }

    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }

    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }

    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }

    private boolean riding(Level level) { return level.player.currentEscalator() == platform(level); }

    private void checkAlive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position); }

    private int walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); checkAlive(level);
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
            frame(level); checkAlive(level);
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

    private void jump(Level level) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }

    /**
     * From the lever to the exit with ordinary keys: walk right, jump onto the platform as it comes down
     * (or sits) near the bottom, hold Right while it carries the hero up, walk off onto the ledge to the exit.
     */
    private int rideToExit(Level level) {
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.D);
            boolean overPlatform = centerX(level) >= PLATFORM_X + 4;
            if (!riding(level) && feet(level) > 300 && overPlatform && platform(level).getY() >= 292) jump(level);
            if (!riding(level) && feet(level) > 300 && !overPlatform) held.add(Input.Keys.D);
            frame(level); checkAlive(level);
            assertTrue(++frames < 60 * 40, "exit unreachable from " + level.player.position + " platformY=" + platform(level).getY());
        }
        held.clear();
        return frames;
    }

    /** The whole taught route from a standing start; returns elapsed frames. */
    private int solve(Level level) {
        int frames = walkToLever(level);
        if (!platform(level).isRunning()) frames += operate(level);
        assertTrue(platform(level).isRunning());
        frames += rideToExit(level);
        return frames;
    }

    @Test void theRoomOffersOneLeverAndOneStoppedPlatformAndNothingElse() {
        Level level = freshRoom();
        assertEquals(1, level.escalators.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.triggers.size());
        assertTrue(level.ladders.isEmpty());
        assertTrue(level.fallingPlatforms.isEmpty());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Wall));
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>));
        assertEquals("forest-0-platform", level.currentRoomName());
        assertTrue(platform(level).isVertical);
        assertFalse(platform(level).isRunning(), "The platform starts stopped at the bottom");
        assertFalse(lever(level).isUp());
        assertEquals(BOTTOM, platform(level).getY(), 0.01f);
        assertEquals(PLATFORM_X, platform(level).getX(), 0.01f);
        walkToLever(level);
        assertTrue(Math.abs(platform(level).getX() - level.player.centerX()) < 320, "The platform is in view of the lever");
    }

    @Test void theLeverStartsAndStopsThePlatformWhereItIsAndShowsIt() {
        Level level = freshRoom();
        walkToLever(level);
        operate(level);
        assertTrue(platform(level).isRunning()); assertTrue(lever(level).isUp());
        settle(level, 60);
        float y = platform(level).getY();
        assertTrue(y < BOTTOM - 40, "Started: it rises 50 px/s, y=" + y);
        operate(level);
        assertFalse(platform(level).isRunning()); assertFalse(lever(level).isUp());
        float stopped = platform(level).getY();
        settle(level, 120);
        assertEquals(stopped, platform(level).getY(), 0.01f, "Stopping freezes it where it is; it does not return to an end");
        operate(level);
        settle(level, 30);
        assertTrue(platform(level).getY() < stopped, "Starting again continues the way it was going");
        settle(level, 60 * 3);
        // It shuttles between its two ends for as long as it runs.
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < 60 * 8; i++) { frame(level); min = Math.min(min, platform(level).getY()); max = Math.max(max, platform(level).getY()); }
        assertEquals(TOP, min, 1.5f); assertEquals(BOTTOM, max, 1.5f);
    }

    @Test void withThePlatformStoppedAtTheBottomNeitherWalkingNorJumpingGetsUpTheLedge() {
        for (float x = ROOM_X + 24; x <= LEDGE_X - 8; x += 8) {
            Level level = freshRoom();
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 60 * 3; i++) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                if (i % 30 == 0) jump(level);
                frame(level);
                assertTrue(feet(level) > LEDGE_TOP + 4, "Hero got up the ledge unaided from x=" + x + " at " + level.player.position);
                assertFalse(level.atRoomExit(), "x=" + x);
            }
        }
    }

    @Test void theTaughtRouteStartsThePlatformRidesItAndWalksToTheExit() {
        Level level = freshRoom();
        solve(level);
        assertTrue(level.atRoomExit());
        assertTrue(platform(level).isRunning(), "The hero never needs to stop the platform");
    }

    @Test void theHeroIsCarriedUpAndDownByThePlatformAndNeverHurt() {
        Level level = freshRoom();
        walkToLever(level); operate(level);
        boolean carriedUp = false, carriedDown = false;
        float last = -1;
        for (int i = 0; i < 60 * 40 && !(carriedUp && carriedDown); i++) {
            held.clear();
            if (!riding(level)) { held.add(Input.Keys.D); if (centerX(level) >= PLATFORM_X + 4 && platform(level).getY() >= 292) jump(level); }
            frame(level); checkAlive(level);
            if (riding(level)) {
                float y = platform(level).getY();
                if (last >= 0 && y < last) carriedUp = true;
                if (last >= 0 && y > last && y < BOTTOM - 8 && feet(level) < 300) carriedDown = true;
                assertEquals(y, feet(level), 2f, "The hero stands on the platform while it moves");
                last = y;
            }
        }
        assertTrue(carriedUp, "carried up"); assertTrue(carriedDown, "and carried down when staying on");
    }

    @Test void stoppingThePlatformUnderTheHeroNeverStrandsThemAndEveryStopHeightStillReachesTheExit() {
        // Even though the lever is far from the platform, stop it at every height with the hero standing on it.
        for (float stopAt = BOTTOM - 8; stopAt >= TOP; stopAt -= 16) {
            Level level = freshRoom();
            walkToLever(level); operate(level);
            // board and ride until the platform passes stopAt, then stop it by the same toggle the lever performs
            for (int i = 0; i < 60 * 40 && !(riding(level) && platform(level).getY() <= stopAt); i++) {
                held.clear();
                if (!riding(level)) { held.add(Input.Keys.D); if (centerX(level) >= PLATFORM_X + 4 && platform(level).getY() >= 292) jump(level); }
                frame(level); checkAlive(level);
            }
            held.clear();
            assertTrue(riding(level), "boarded for stop height " + stopAt);
            platform(level).performAction();
            settle(level, 60);
            assertFalse(platform(level).isRunning());
            // Either the ledge is in reach from the platform, or the hero drops to the floor beside it and starts over.
            for (int i = 0; i < 60 * 40 && !level.atRoomExit(); i++) {
                held.clear(); held.add(Input.Keys.D);
                if (i % 40 == 0) jump(level);
                frame(level); checkAlive(level);
                if (centerX(level) >= LEDGE_X + 8 && feet(level) < LEDGE_TOP + 2) break;
            }
            if (!level.atRoomExit()) {
                if (feet(level) > LEDGE_TOP + 2 || centerX(level) < LEDGE_X) {
                    // Not on the ledge: leave the stopped platform to the left, walk back to the lever, start it, finish.
                    for (int i = 0; i < 60 * 10 && riding(level); i++) { held.clear(); held.add(Input.Keys.A); frame(level); checkAlive(level); }
                    walkBackToLever(level);
                    if (!platform(level).isRunning()) operate(level);
                    rideToExit(level);
                } else {
                    held.clear(); held.add(Input.Keys.D);
                    for (int i = 0; i < 60 * 10 && !level.atRoomExit(); i++) frame(level);
                }
            }
            assertTrue(level.atRoomExit(), "stop height " + stopAt);
        }
    }

    @Test void walkingOffTheLedgeOrTheTopOfThePlatformIsHarmlessAndLeavesTheRouteOpen() {
        Level level = freshRoom();
        solve(level);
        // From the exit side, walk left off the ledge: a 112 px drop onto the floor or the platform, not a hurt.
        level.player.position.set(LEDGE_X + 8, LEDGE_TOP - 48);
        settle(level, 5);
        for (int i = 0; i < 60 * 6; i++) { held.clear(); held.add(Input.Keys.A); frame(level); checkAlive(level); }
        held.clear(); settle(level, 60);
        assertFalse(level.player.isGotHit());
        assertTrue(feet(level) > LEDGE_TOP + 40 || riding(level), "The hero is back down on the floor or on the platform: " + level.player.position);
        // and the lever is still reachable on foot, so the platform can be stopped or started again
        for (int i = 0; i < 60 * 10; i++) { held.clear(); held.add(Input.Keys.A); frame(level); checkAlive(level); }
        assertTrue(level.player.position.x < ROOM_X + 40, "back at the entrance wall: " + level.player.position);
        walkToLever(level);
        if (!platform(level).isRunning()) operate(level);
        rideToExit(level);
        assertTrue(level.atRoomExit());
    }

    @Test void theExitIsReachableAfterTogglingRetryDeathAndRestart() {
        // Start, stop, start, stop at the lever, then finish.
        Level level = freshRoom();
        walkToLever(level);
        for (int i = 0; i < 4; i++) { operate(level); settle(level, 37 * (i + 1)); }
        if (!platform(level).isRunning()) operate(level);
        solve(level);
        assertTrue(level.atRoomExit());

        // Death while riding: the room restarts with the stopped, bottom platform and the hero at the entrance.
        Level riding = freshRoom();
        walkToLever(riding); operate(riding);
        for (int i = 0; i < 60 * 40 && !(riding(riding) && platform(riding).getY() < 260); i++) {
            held.clear();
            if (!riding(riding)) { held.add(Input.Keys.D); if (centerX(riding) >= PLATFORM_X + 4 && platform(riding).getY() >= 292) jump(riding); }
            frame(riding);
        }
        held.clear();
        assertTrue(riding(riding) && platform(riding).getY() < 260, "riding high up");
        riding.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(platform(riding).isRunning(), "Retry restores the authored stopped platform");
        assertEquals(BOTTOM, platform(riding).getY(), 0.01f);
        assertFalse(lever(riding).isUp());
        assertTrue(riding.player.position.x < PLATFORM_X - 200, "and the hero starts at the entrance");
        solve(riding);

        // Retry with the platform stopped halfway up (platform saved mid-way, hero at the lever).
        Level mid = freshRoom();
        walkToLever(mid); operate(mid); settle(mid, 40); operate(mid);
        assertFalse(platform(mid).isRunning()); assertTrue(platform(mid).getY() < BOTTOM);
        mid.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertEquals(BOTTOM, platform(mid).getY(), 0.01f);
        solve(mid);

        // Restart Level rebuilds the whole level from the map.
        Level restarted = freshRoom();
        assertFalse(platform(restarted).isRunning());
        solve(restarted);
    }

    @Test void startingAndStoppingAtAnyMomentNeverTrapsTheHero() {
        for (int delay = 0; delay <= 150; delay += 15) {
            Level level = freshRoom();
            walkToLever(level);
            for (int i = 0; i < 5; i++) { operate(level); settle(level, delay); }
            if (!platform(level).isRunning()) operate(level);
            solve(level);
            assertTrue(level.atRoomExit(), "delay " + delay);
        }
    }

    /**
     * #211: floor and jump apex, platform stopped and running: wherever both channels show, the message and the
     * live label are at least the gap apart (camera 0 and 8 px down).
     */
    @Test void messageAndLiveLabelsNeverOverlapOnTheFloorOrInTheAir() {
        Level level = freshRoom();
        HintGeometry geometry = new HintGeometry();
        Vector2 zoneAnchor = new Vector2(), contextAnchor = new Vector2();
        int both = 0;
        for (boolean running : new boolean[]{false, true}) {
            if (platform(level).isRunning() != running) platform(level).performAction();
            for (float standY : new float[]{272, 272 - 58})
                for (float x = ROOM_X + 24; x <= PLATFORM_X - 8; x += 4) {
                    level.player.position.set(x - 24, standY);
                    settle(level, 2);
                    String zone = level.player.zoneHint(), context = level.player.contextHint();
                    if (zone.isEmpty() || context.isEmpty()) continue;
                    both++;
                    level.player.zoneHintPosition(zoneAnchor); level.player.contextHintPosition(contextAnchor);
                    for (float cameraY : new float[]{0, 8})
                        assertTrue(geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY) >= HintLayout.GAP - .001,
                            "x=" + x + " y=" + standY + " running=" + running + " cameraY=" + cameraY + " " + context.replace('\n', '|')
                                + " / " + zone + " gap=" + geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY));
                }
        }
        assertTrue(both > 5, "label and message do share positions in this room: " + both);
    }

    @Test void lessonHintsCoverEveryStandingPositionInBothPlatformStatesAndSurviveRetry() {
        Level level = freshRoom();
        Set<String> messages = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (boolean running : new boolean[]{false, true}) {
                for (float x = ROOM_X + 24; x < ROOM_END - 16; x += 8) {
                    if (platform(level).isRunning() != running) platform(level).performAction();
                    boolean onLedge = x > LEDGE_X + 8;
                    level.player.position.set(x - 24, onLedge ? LEDGE_TOP - 48 : 272);
                    for (int i = 0; i < 3; i++) frame(level);
                    String hint = level.player.contextHint(), zone = level.player.zoneHint();
                    String where = "x=" + x + " running=" + running + ": " + hint + " / " + zone;
                    // #211: the zone message is never replaced by the live lever label; both show together.
                    // Message zones know only position, so each text must be true in both platform states.
                    assertEquals(level.messageHint().message().t(Language.ENGLISH), zone, where);
                    messages.add(zone);
                    if (level.player.canInteract()) {
                        assertTrue(hint.contains(Msg.LEVER.t(Language.ENGLISH)), where);
                        assertTrue(hint.contains((running ? Msg.TURN_OFF : Msg.TURN_ON).t(Language.ENGLISH)), where);
                    } else assertTrue(hint.isEmpty(), "Only the lever has a live label here: " + where);
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
        }
        assertEquals(Set.of(Msg.HINT_PLATFORM_LEVER.t(Language.ENGLISH), Msg.HINT_PLATFORM_RIDE.t(Language.ENGLISH)), messages);
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
        System.out.printf(java.util.Locale.ROOT, "forest-0-platform mastered route (walk, pull lever, board the platform, ride up, walk to the exit): %.2f s%n", seconds);
        assertTrue(seconds < 30, "Author check, not a novice playtest (#129-#131): " + seconds);
    }
}
