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
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
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
 * The forest-0 stone lesson room (#199): ordinary keyboard controls drive the hero through the real map.
 * A chasm cuts the room in two; the lever that starts the moving platform over it stands on the far side, out of
 * walking reach, and a thrown stone operates it. Times are author (mastered) measurements, not novice playtests.
 * Recipe: docs/ROOMS.md, rule: D39.
 */
class StoneLessonTest {
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

    private static final float ROOM_X = 3056, LIP = 3616, FAR = 3712, PLATFORM_END = 3680, ROOM_RIGHT = 3792;
    /** The exploding stump (D48): x 3488-3504 on the near floor; its blast square is x 3464-3528. */
    private static final float STUMP_X = 3488, THROW_ZONE_END = 3322, STUMP_ZONE_END = 3576;
    private static final float STONE_X = 3328, STONE_Y = 308;
    /** The route throws with the body's right edge here, standing on the parked platform. */
    private static final float THROW_FROM = 3644;

    /** A level whose hero stands in the stone room (index 4) at its safe entrance. */
    private Level freshRoom() {
        Level level = freshRoomWithStump();
        withoutTheStump(level);
        return level;
    }

    /** The room as shipped, the exploding stump (D48) included. */
    private Level freshRoomWithStump() {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        for (int i = 0; i < 4; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    /** The stone/lever lesson tests below walk, teleport and throw all over the near floor; the stump has its own tests. */
    private void withoutTheStump(Level level) { level.objects.removeIf(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::isInstance); }

    private Escalator platform(Level level) { return level.escalators.get(0); }

    private Lever lever(Level level) {
        return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow();
    }

    private Carryable stone(Level level) {
        return level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
    }

    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private float bodyLeft(Level level) { return level.player.hitboxBounds().x; }
    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    /** Stands the hero with the body's right edge at x. */
    private void place(Level level, float bodyRight) { level.player.position.set(bodyRight - 11 - 18, 272); settle(level, 2); }

    private void parkPlatformAt(Level level, float x, boolean running) {
        platform(level).restore(new Escalator.Snapshot(x, 320, 1, true, running ? Escalator.State.RUN : Escalator.State.IDLE, true, 0, false));
    }

    private int walkRightTo(Level level, java.util.function.BooleanSupplier arrived) {
        int frames = 0;
        while (!arrived.getAsBoolean()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertTrue(alive(level), "died at " + level.player.position);
            assertTrue(++frames < 60 * 30, "never arrived, hero at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    private int walkLeftTo(Level level, java.util.function.BooleanSupplier arrived) {
        int frames = 0;
        while (!arrived.getAsBoolean()) {
            held.clear(); held.add(Input.Keys.A);
            frame(level);
            assertTrue(alive(level), "died at " + level.player.position);
            assertTrue(++frames < 60 * 30, "never arrived, hero at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Up + Action, as the hint teaches. */
    private int upAction(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    private int throwNow(Level level) {
        held.clear(); held.add(Input.Keys.L); pressed.add(Input.Keys.L); frame(level);
        held.clear(); frame(level);
        return 2;
    }

    private boolean canPickUp(Level level) { return level.player.selectedCarryable() != null; }
    /** Walks to the resting stone from either side and picks it up. */
    private void fetchStone(Level level) {
        int f = 0;
        while (level.player.carriedObject() == null) {
            if (canPickUp(level)) { held.clear(); upAction(level); break; }
            held.clear(); held.add(stone(level).getX() + 6 > level.player.centerX() ? Input.Keys.D : Input.Keys.A); frame(level);
            assertTrue(alive(level) && ++f < 60 * 20, "stone unreachable from " + level.player.position + " stone " + stone(level).getX());
        }
        held.clear();
    }

    private int walkToStone(Level level) { return walkRightTo(level, () -> canPickUp(level)); }

    /** Waits for the platform to show the hit. */
    private int waitForPlatform(Level level, boolean running) {
        int frames = 0;
        while (platform(level).isRunning() != running) {
            frame(level);
            assertTrue(++frames < 90, "the platform never reached running=" + running);
        }
        return frames;
    }

    /** Walks onto the parked platform and stops with the body's right edge at THROW_FROM, then throws. */
    private int goAndThrow(Level level) {
        int frames = walkRightTo(level, () -> bodyRight(level) >= THROW_FROM);
        return frames + throwNow(level);
    }

    private int walkToExit(Level level) {
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertTrue(alive(level), "died at " + level.player.position);
            assertTrue(++frames < 60 * 30, "exit unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Stands on the running platform until it touches the far lip, then walks off. */
    private int rideAcross(Level level) {
        int frames = 0;
        while (platform(level).getX() < PLATFORM_END - 1) {
            held.clear(); frame(level);
            assertTrue(alive(level), "died while riding at " + level.player.position);
            assertTrue(++frames < 60 * 10, "platform never arrived");
        }
        return frames + walkRightTo(level, () -> bodyLeft(level) >= FAR + 4);
    }

    /** The whole taught route from a standing start; returns elapsed frames. */
    private int solve(Level level) {
        int frames = walkToStone(level);
        frames += upAction(level);
        assertSame(stone(level), level.player.carriedObject());
        frames += goAndThrow(level);
        frames += waitForPlatform(level, true);
        assertTrue(lever(level).isUp(), "The thrown stone flipped the lever");
        frames += rideAcross(level);
        frames += walkToExit(level);
        return frames;
    }

    private void rest(Level level) { settle(level, 60 * 4); }

    private boolean atHome(Level level) {
        return Math.abs(stone(level).getX() - STONE_X) < .01f && Math.abs(stone(level).getY() - STONE_Y) < .01f;
    }

    /** Wherever a throw ends the stone is back home or lying on the entrance side where the hero can pick it up. */
    private void assertRetrievable(Level level, String where) {
        Carryable stone = stone(level);
        assertTrue(stone.available(), where);
        assertTrue(level.currentRoomBounds().contains(stone.bounds()), where);
        assertTrue(atHome(level) || (stone.getX() + 12 <= FAR && stone.getY() + 12 == 320),
            where + ": neither home nor on the entrance side floor or platform: " + stone.getX() + "," + stone.getY());
    }

    private void giveStoneAt(Level level, float bodyRight) {
        place(level, bodyRight);
        Carryable stone = stone(level);
        stone.restore(new Carryable.Snapshot(bodyRight - 11, STONE_Y, 0, 0, false, false));
        settle(level, 1);
        held.clear(); held.add(Input.Keys.D); frame(level); held.clear(); // face the chasm
        upAction(level);
        assertSame(stone, level.player.carriedObject(), "picked up at " + bodyRight);
    }

    @Test void theRoomOffersOneStoneOneLeverAndOneStoppedPlatformOverAChasmAndNothingElse() {
        Level level = freshRoom();
        assertEquals("forest-0-stone", level.currentRoomName());
        assertEquals(1, level.objects.stream().filter(Carryable.class::isInstance).count(), "exactly one stone");
        assertEquals(Carryable.Kind.STONE, stone(level).kind);
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.triggers.size());
        assertTrue(level.triggers.get(0).throwActivated, "The lever answers to a thrown object");
        assertEquals(1, level.escalators.size());
        assertFalse(platform(level).isVertical, "The platform crosses the chasm sideways");
        assertFalse(platform(level).isRunning(), "and starts stopped");
        assertEquals(LIP, platform(level).getX(), .01f);
        assertEquals(320, platform(level).getY(), .01f);
        assertTrue(level.ladders.isEmpty()); assertTrue(level.fallingPlatforms.isEmpty());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>));
        assertFalse(lever(level).isUp());
        assertEquals(STONE_X, stone(level).getX(), .01f); assertEquals(STONE_Y, stone(level).getY(), .01f);
        assertTrue(stone(level).getX() - level.player.getX() >= 160, "The stone lies well ahead of the entrance so its hint can be read first");
        // The chasm has no floor at all between the two lips.
        Rectangle chasm = new Rectangle(LIP + 1, 320, FAR - LIP - 2, 48);
        for (var block : level.collisionBlocks) if (!block.isEscalator())
            assertFalse(chasm.overlaps(new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight())), "a floor in the chasm");
    }

    @Test void theLeverIsOutOfWalkingReachAndNeitherJumpingNorTheParkedOrStoppedPlatformCrossesTheChasm() {
        for (float stop : new float[]{LIP, LIP + 16, LIP + 32, LIP + 48, LIP + 64, PLATFORM_END}) {
            for (float x = ROOM_X + 508; x <= (stop == LIP ? LIP + 32 - 4 : LIP + 4); x += 6) {
                Level level = freshRoom();
                parkPlatformAt(level, stop, false);
                level.player.position.set(x - 18, 272);
                settle(level, 3);
                if (!alive(level)) continue;
                for (int i = 0; i < 60 * 4 && alive(level); i++) {
                    held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                    if (i % 20 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                    held.add(Input.Keys.J);
                    frame(level);
                    assertFalse(level.player.canInteract(), "The lever must not be in reach at " + level.player.position + " platform " + stop);
                    assertFalse(bodyRight(level) > FAR + 2 && alive(level), "crossed the chasm from x=" + x + " platform at " + stop + ": " + level.player.position);
                }
            }
        }
    }

    @Test void aHeroStrandedOnAStoppedPlatformAlwaysReachesTheNearerLipByJumping() {
        for (float stop = LIP; stop <= PLATFORM_END; stop += 8) for (float onPlatform : new float[]{stop + 2, stop + 21}) {
            Level level = freshRoom();
            parkPlatformAt(level, stop, false);
            level.player.position.set(onPlatform - 18, 272);
            settle(level, 20);
            if (!alive(level)) continue;
            boolean right = (FAR - (stop + 32)) < (stop - LIP);
            // Walk to the platform's near edge first, as anyone would, then jump.
            for (int i = 0; i < 60 * 3 && alive(level) && (right ? bodyRight(level) < stop + 32 - .5f : bodyLeft(level) > stop + .5f); i++) {
                held.clear(); held.add(right ? Input.Keys.D : Input.Keys.A); frame(level);
            }
            for (int i = 0; i < 60 * 3 && alive(level); i++) {
                held.clear(); held.add(right ? Input.Keys.D : Input.Keys.A); held.add(Input.Keys.J);
                if (i == 0) pressed.add(Input.Keys.J);
                frame(level);
            }
            held.clear(); settle(level, 30);
            assertTrue(alive(level), level.player.position + " fell from a platform stopped at " + stop + " standing at " + onPlatform);
            assertTrue(right ? bodyLeft(level) >= FAR - 2 : bodyRight(level) <= LIP + 2, "stop " + stop + " standing " + onPlatform + ": " + level.player.position);
        }
    }

    @Test void aStoneThrownFromTheWindowFlipsTheLeverAndFromElsewhereDoesNot() {
        float first = Float.NaN, last = Float.NaN;
        StringBuilder log = new StringBuilder();
        for (float nominal = LIP - 100; nominal <= FAR + 2; nominal += 2) {
            Level level = freshRoom();
            giveStoneAt(level, nominal);
            float at = bodyRight(level);
            throwNow(level); rest(level);
            boolean hit = lever(level).isUp();
            log.append(hit ? 'H' : '.');
            assertEquals(hit, platform(level).isRunning(), "the platform follows the lever, x=" + at);
            if (hit) { if (Float.isNaN(first)) first = at; last = at; assertTrue(atHome(level), "a stone that hit comes home"); }
            assertRetrievable(level, "throw from " + at);
        }
        System.out.println("forest-0-stone throw window (body right edge, near lip at " + LIP + "): " + first + " .. " + last
            + " (" + (first - LIP) + " .. " + (last - LIP) + " px from the lip); sweep " + log);
        assertTrue(last - first >= 40, "A forgiving window, at least 40 px wide: " + first + " .. " + last);
        assertTrue(first <= THROW_FROM && THROW_FROM <= last, "The route throws from inside the window");
        assertTrue(first <= LIP, "A throw from the floor edge (body right edge at the lip) already reaches the lever: window starts at " + first);
        assertTrue(last >= FAR - 2, "and the window runs on to the lever itself, the end of what the hero can stand on: " + last);
        String hits = log.toString().replaceAll("^\\.*", "").replaceAll("\\.*$", "");
        assertFalse(hits.contains("."), "contiguous window: " + log);
    }

    /** Pressed flush against either side wall, a throw starts inside the wall: the stone must still come home (or lie where it can be taken). */
    @Test void aThrowWhilePressedAgainstASideWallNeverLosesTheStone() {
        for (boolean left : new boolean[]{true, false}) for (float gap = 2; gap <= 14; gap += 1) {
            Level level = freshRoom();
            if (left) giveStoneAt(level, ROOM_X + 16 + 11 + gap); else giveStoneAt(level, ROOM_RIGHT - 16 - gap);
            held.clear(); held.add(left ? Input.Keys.A : Input.Keys.D); frame(level); held.clear();
            throwNow(level); rest(level);
            Carryable stone = stone(level);
            String where = (left ? "left" : "right") + " wall, gap " + gap + ": " + stone.getX() + "," + stone.getY();
            assertTrue(stone.available(), where);
            assertTrue(level.currentRoomBounds().contains(stone.bounds()), where);
            assertTrue(atHome(level) || Math.abs(stone.getY() + 12 - 320) < .01f, where);
        }
    }

    @Test void aThrowFromTheFarFloorBesideTheLeverLeavesTheStoneWithTheHero() {
        for (float at = FAR + 2; at <= ROOM_RIGHT - 20; at += 4) {
            Level level = freshRoom();
            giveStoneAt(level, at);
            throwNow(level); rest(level);
            assertTrue(stone(level).available());
            assertTrue(atHome(level) || (stone(level).getX() >= FAR && Math.abs(stone(level).getY() + 12 - 320) < .01f), "far-side throw from " + at + ": " + stone(level).getX());
        }
    }

    @Test void theTaughtRouteThrowsTheStoneRidesTheStartedPlatformAcrossAndWalksToTheExit() {
        Level level = freshRoom();
        solve(level);
        assertTrue(level.atRoomExit());
        assertTrue(platform(level).isRunning());
        assertTrue(lever(level).isUp(), "The lever shows the hit");
        assertTrue(atHome(level), "The stone that hit is back where it lay");
    }

    @Test void fallingIntoTheChasmIsTheDeadlineAndRetryRestoresTheAuthoredRoom() {
        Level level = freshRoom();
        walkToStone(level); upAction(level);
        // Walk off the end of the parked platform.
        int frames = 0;
        while (alive(level)) {
            held.clear(); held.add(Input.Keys.D); frame(level);
            assertTrue(++frames < 60 * 20, "never fell");
        }
        held.clear();
        assertTrue(level.player.position.y > 272 + 20 || level.touchesDeadline(level.player.hitboxBounds()), "fell into the chasm");
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(platform(level).isRunning()); assertEquals(LIP, platform(level).getX(), .01f);
        assertFalse(lever(level).isUp());
        assertTrue(atHome(level)); assertTrue(stone(level).available());
        assertNull(level.player.carriedObject());
        assertTrue(level.player.position.x < STONE_X - 150);
        solve(level);
    }

    @Test void aMissLosesNothingBecauseTheStoneComesHomeOrLiesOnTheEntranceSide() {
        // Too short (it drops into the chasm), too long, away from the lever, in the air: always a stone to pick up again.
        for (float from : new float[]{3196, 3296, 3366, LIP - 40, LIP - 25, THROW_FROM + 40, PLATFORM_END + 25}) {
            Level level = freshRoom();
            giveStoneAt(level, from);
            throwNow(level); rest(level);
            assertRetrievable(level, "throw right from " + from);
            if (!lever(level).isUp()) {
                int f = 0;
                while (!canPickUp(level)) {
                    held.clear(); held.add(stone(level).getX() + 6 > level.player.centerX() ? Input.Keys.D : Input.Keys.A); frame(level);
                    assertTrue(alive(level) && ++f < 60 * 20, "stone unreachable from " + level.player.position + " stone " + stone(level).getX());
                }
                held.clear(); upAction(level);
                assertSame(stone(level), level.player.carriedObject());
                walkRightTo(level, () -> bodyRight(level) >= THROW_FROM);
                if (bodyRight(level) > THROW_FROM + 1) walkLeftTo(level, () -> bodyRight(level) <= THROW_FROM);
                held.clear(); held.add(Input.Keys.D); frame(level); held.clear();
                throwNow(level); rest(level);
                assertTrue(lever(level).isUp(), "Second try after a miss from " + from);
            }
        }
        for (float from : new float[]{3196, 3336, LIP - 30, THROW_FROM}) {   // away from the lever (to the left)
            Level level = freshRoom();
            giveStoneAt(level, from);
            held.clear(); held.add(Input.Keys.A); frame(level); held.clear();
            throwNow(level); rest(level);
            assertFalse(lever(level).isUp());
            assertRetrievable(level, "throw left from " + from);
        }
        // Jump throws, both ways, at every phase of the jump.
        for (boolean left : new boolean[]{true, false}) for (int phase = 0; phase <= 40; phase += 4) for (float from : new float[]{3296, LIP - 20, THROW_FROM}) {
            Level level = freshRoom();
            giveStoneAt(level, from);
            held.clear(); held.add(left ? Input.Keys.A : Input.Keys.D); frame(level);
            held.add(Input.Keys.J); pressed.add(Input.Keys.J); frame(level); held.remove(Input.Keys.A); held.remove(Input.Keys.D);
            settle(level, phase);
            throwNow(level); rest(level);
            String where = (left ? "left" : "right") + " jump throw phase " + phase + " from " + from;
            assertEquals(lever(level).isUp(), platform(level).isRunning(), where);
            assertTrue(stone(level).available(), where);
            if (!lever(level).isUp() && alive(level)) assertRetrievable(level, where);
        }
    }

    @Test void aStoneNeverEndsUpOnTheFarSideOrAnywhereTheHeroCannotTakeItFrom() {
        int throwsDone = 0;
        for (boolean left : new boolean[]{true, false}) for (boolean running : new boolean[]{false, true})
            for (float from = ROOM_X + 508; from <= PLATFORM_END + 28; from += 3) {
                Level level = freshRoom();
                giveStoneAt(level, from);
                if (running) { parkPlatformAt(level, LIP, true); }
                held.clear(); held.add(left ? Input.Keys.A : Input.Keys.D); frame(level); held.clear();
                throwNow(level);
                // The hero stays on a running platform; he may ride off the end or not: only the stone is checked.
                settle(level, 60 * 4); throwsDone++;
                String where = "left=" + left + " running=" + running + " from " + from;
                Carryable stone = stone(level);
                assertTrue(stone.available(), where);
                assertTrue(atHome(level) || (stone.getX() + 12 <= FAR && stone.getY() + 12 == 320), where + ": " + stone.getX() + "," + stone.getY());
            }
        assertTrue(throwsDone > 150, "swept " + throwsDone + " throws");
    }

    /** Two real throws from the floor edge: the second stops the platform wherever it is; the hero still gets across. */
    @Test void aSecondThrowStopsThePlatformWhereItIsAndAThirdOneRestartsItSoTheHeroStillGetsAcross() {
        Level level = freshRoom();
        walkToStone(level); upAction(level);
        walkRightTo(level, () -> bodyRight(level) >= LIP - 2);       // the floor edge, not the platform
        assertTrue(level.player.position.y == 272 && bodyRight(level) < LIP + 6);
        throwNow(level); waitForPlatform(level, true);
        assertTrue(atHome(level) || stone(level).available());
        settle(level, 70);                                           // the platform is out over the chasm
        assertTrue(platform(level).getX() > LIP + 8, "platform left the hero behind: " + platform(level).getX());
        for (int round = 0; round < 3; round++) {
            // walk back to the stone and take it again
            fetchStone(level);
            walkRightTo(level, () -> bodyRight(level) >= LIP - 2);
            boolean wasRunning = platform(level).isRunning();
            throwNow(level); rest(level);
            assertEquals(!wasRunning, platform(level).isRunning(), "round " + round + ": a real throw toggles the platform");
            assertEquals(platform(level).isRunning(), lever(level).isUp());
            assertTrue(atHome(level), "the stone is home again");
            assertTrue(alive(level));
        }
        // Whatever state the three throws left (running here), wait for the platform to come to the lip, board it and ride on.
        if (!platform(level).isRunning()) {
            fetchStone(level); walkRightTo(level, () -> bodyRight(level) >= LIP - 2); throwNow(level); waitForPlatform(level, true);
        }
        int f = 0;
        while (platform(level).getX() > LIP + 1) { held.clear(); frame(level); assertTrue(++f < 60 * 10); }
        walkRightTo(level, () -> bodyRight(level) >= LIP + 14);
        // Anyone boarding late moves to the platform's far end while it crosses, so that stepping off at the far lip is one step.
        walkRightTo(level, () -> bodyRight(level) >= platform(level).getX() + 30);
        rideAcross(level); walkToExit(level);
        assertTrue(level.atRoomExit());
    }

    /** A stone put down on any platform (parked, running or stopped mid-chasm) falls through it and comes home. */
    @Test void aStonePutDownOnAPlatformNeverStaysOnItAndIsFetchedFromHome() {
        for (float stop : new float[]{LIP, LIP + 16, LIP + 32, LIP + 48, PLATFORM_END}) for (boolean running : new boolean[]{false, true}) {
            Level level = freshRoom();
            parkPlatformAt(level, stop, running);
            float stand = Math.min(stop + 8, FAR - 1);
            giveStoneAt(level, stand + 11);
            if (stop > LIP) { level.player.position.set(stop + 8 - 18, 272); settle(level, 2); }
            held.clear(); held.add(Input.Keys.S); frame(level); held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level); held.clear();
            rest(level);
            String where = "stop " + stop + " running " + running;
            assertTrue(stone(level).available(), where);
            // At the far lip the stone lies beside the hero, who has already crossed; elsewhere it is home.
            assertTrue(atHome(level) || (stop == PLATFORM_END && stone(level).getX() >= PLATFORM_END), where + ": " + stone(level).getX() + "," + stone(level).getY());
            // And the hero, wherever he stands, can walk to it again (when not stranded over the chasm).
            if (alive(level) && bodyRight(level) <= LIP + 2) { walkToStone(level); upAction(level); assertSame(stone(level), level.player.carriedObject(), where); }
        }
    }

    @Test void theLeverWorksByHandFromTheFarSideAndTogglesTheStartedPlatform() {
        Level level = freshRoom();
        solve(level);
        // The route ended at the exit; walk back to the lever on the far floor and use it.
        walkLeftTo(level, () -> level.player.canInteract());
        boolean running = platform(level).isRunning();
        upAction(level);
        assertEquals(!running, platform(level).isRunning(), "Up + Action toggles the platform from the lever's side");
        upAction(level);
        assertEquals(running, platform(level).isRunning());
    }

    @Test void deathRetryAndRestartPutEverythingBackAndNeverLeakTheStone() {
        Level level = freshRoom();
        solve(level);
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(platform(level).isRunning()); assertFalse(lever(level).isUp());
        assertEquals(LIP, platform(level).getX(), .01f);
        assertTrue(atHome(level)); assertNull(level.player.carriedObject());
        solve(level);

        // Death while carrying, and mid-throw.
        Level carrying = freshRoom();
        walkToStone(carrying); upAction(carrying);
        carrying.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertNull(carrying.player.carriedObject());
        assertTrue(atHome(carrying));
        walkToStone(carrying); upAction(carrying); goAndThrow(carrying); settle(carrying, 8);
        carrying.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(platform(carrying).isRunning()); assertTrue(atHome(carrying));
        assertEquals(1, carrying.objects.stream().filter(Carryable.class::isInstance).count());
        solve(carrying);

        Level restarted = freshRoom();
        assertFalse(platform(restarted).isRunning());
        solve(restarted);
    }

    @Test void theStoneNeverLeavesTheRoomItBelongsTo() {
        // Carry it across the chasm to the exit (platform started by the set-up), then cross: the next room has no stone and an empty hand.
        Level level = freshRoom();
        walkToStone(level); upAction(level);
        walkRightTo(level, () -> bodyRight(level) >= LIP + 20);
        platform(level).performAction();
        rideAcross(level); walkToExit(level);
        assertSame(stone(level), level.player.carriedObject(), "still in hand at the exit");
        level.enterNextRoom(level.player);
        assertEquals("forest-0-traps", level.currentRoomName());
        assertNull(level.player.carriedObject(), "the hand is empty in the next room");
        assertTrue(level.objects.stream().noneMatch(Carryable.class::isInstance), "no stone in the next room");
        Level other = freshRoom();
        walkToStone(other); upAction(other);
        walkRightTo(other, () -> bodyRight(other) >= LIP + 20); platform(other).performAction(); rideAcross(other); walkToExit(other);
        other.enterNextRoom(new Player(assets, game, 0, 0));
        assertNull(other.player.carriedObject());
        assertTrue(other.objects.stream().noneMatch(Carryable.class::isInstance));
    }

    @Test void aPlacedStoneAndAThrowAtAnOrdinaryLeverDoNotOperateIt() {
        Level level = freshRoom();
        giveStoneAt(level, THROW_FROM);
        held.clear(); held.add(Input.Keys.S); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level); held.clear();   // Down + Action puts it down
        rest(level);
        assertFalse(lever(level).isUp(), "Putting a stone down is not a throw");
        Level plain = freshRoom();
        plain.triggers.get(0).throwActivated = false;
        giveStoneAt(plain, THROW_FROM);
        throwNow(plain); rest(plain);
        assertFalse(lever(plain).isUp()); assertFalse(platform(plain).isRunning());
    }

    @Test void lessonHintsCoverEveryStandingPositionInEveryStateAndSurviveRetry() {
        Level level = freshRoom();
        Set<String> messages = new HashSet<>();
        String throwText = Msg.HINT_STONE_THROW.t(Language.ENGLISH), exitText = Msg.HINT_STONE_EXIT.t(Language.ENGLISH),
            acrossText = Msg.HINT_STONE_ACROSS.t(Language.ENGLISH), stumpText = Msg.HINT_STONE_STUMP.t(Language.ENGLISH);
        for (int pass = 0; pass < 2; pass++) {
            for (boolean carrying : new boolean[]{false, true}) {
                for (float x = ROOM_X + 40; x < ROOM_X + 728; x += 8) {
                    if (x > LIP + 36 && x < FAR - 4) continue;     // over the chasm
                    parkPlatformAt(level, LIP, false);
                    place(level, x);
                    if (carrying && level.player.carriedObject() == null) {
                        stone(level).restore(new Carryable.Snapshot(x - 6, STONE_Y, 0, 0, false, false));
                        settle(level, 1);
                        upAction(level);
                    } else if (!carrying && level.player.carriedObject() != null) {
                        held.clear(); held.add(Input.Keys.S); frame(level); held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level); held.clear();
                        stone(level).restore(new Carryable.Snapshot(STONE_X, STONE_Y, 0, 0, false, false));
                    }
                    place(level, x);
                    settle(level, 3);
                    String zone = level.player.zoneHint(), hint = level.player.contextHint();
                    String where = "x=" + x + " carrying=" + carrying + ": " + hint.replace('\n', '|') + " / " + zone;
                    Rectangle body = level.player.hitboxBounds();
                    float centre = body.x + body.width / 2;
                    assertEquals(centre <= THROW_ZONE_END ? throwText : centre <= STUMP_ZONE_END ? stumpText : centre <= FAR ? exitText : acrossText, zone, where);
                    assertEquals(level.messageHint().message().t(Language.ENGLISH), zone, where);
                    messages.add(zone);
                    assertFalse(zone.contains("Up") || zone.contains("Action") || zone.contains("Ranged"), "no key names in the text");
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
        }
        assertEquals(Set.of(throwText, stumpText, exitText, acrossText), messages);
    }

    @Test void everyThrowSpotIsInsideTheZoneThatStillTellsToThrow() {
        Level level = freshRoom();
        String exitText = Msg.HINT_STONE_EXIT.t(Language.ENGLISH);
        assertTrue(exitText.contains("stone") && exitText.contains("lever"), exitText);
        for (float x = LIP - 12; x <= PLATFORM_END + 20; x += 4) {
            parkPlatformAt(level, LIP, false); place(level, x); settle(level, 2);
            assertEquals(exitText, level.player.zoneHint(), "x=" + x);
        }
    }

    @Test void theLiveLabelsTeachPickUpAndThrowBesideTheZoneMessage() {
        Level level = freshRoom();
        walkToStone(level);
        String pick = level.player.contextHint();
        assertTrue(pick.contains(Msg.PICK_UP.t(Language.ENGLISH)) && pick.contains(Msg.STONE.t(Language.ENGLISH)), pick);
        assertFalse(level.player.zoneHint().isEmpty(), "The pickup label never hides the zone message (#211)");
        upAction(level);
        String carrying = level.player.contextHint();
        assertTrue(carrying.contains(Msg.PUT_DOWN.t(Language.ENGLISH)) && carrying.contains(Msg.THROW.t(Language.ENGLISH)), carrying);
        assertEquals(Msg.HINT_STONE_THROW.t(Language.ENGLISH), level.player.zoneHint());
        assertEquals(Msg.THROW, level.player.rangedLabel());
    }

    /** #211: wherever both channels show, they are at least the gap apart (camera 0 and 8 px down). */
    @Test void messageAndLiveLabelsNeverOverlapOnTheFloorOrInTheAir() {
        Level level = freshRoom();
        HintGeometry geometry = new HintGeometry();
        Vector2 zoneAnchor = new Vector2(), contextAnchor = new Vector2();
        int both = 0;
        for (float standY : new float[]{272, 272 - 26}) for (boolean carrying : new boolean[]{false, true})
            for (float x = ROOM_X + 40; x <= ROOM_X + 728; x += 4) {
                if (x > LIP + 36 && x < FAR - 4) continue;
                parkPlatformAt(level, LIP, false);
                if (carrying && level.player.carriedObject() == null) {
                    place(level, x);
                    stone(level).restore(new Carryable.Snapshot(x - 6, STONE_Y, 0, 0, false, false));
                    settle(level, 1); upAction(level);
                }
                if (!carrying && level.player.carriedObject() != null) level.resetCurrentRoom(new Player(assets, game, 0, 0));
                level.player.position.set(x - 11 - 18, standY);
                settle(level, 2);
                String zone = level.player.zoneHint(), context = level.player.contextHint();
                if (zone.isEmpty() || context.isEmpty()) continue;
                both++;
                level.player.zoneHintPosition(zoneAnchor); level.player.contextHintPosition(contextAnchor);
                for (float cameraY : new float[]{0, 8})
                    assertTrue(geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY) >= HintLayout.GAP - .001,
                        "x=" + x + " y=" + standY + " carrying=" + carrying + " cameraY=" + cameraY + " " + context.replace('\n', '|') + " / " + zone);
            }
        assertTrue(both > 5, "label and message do share positions in this room: " + both);
    }

    @Test void masteredRouteTimeIsMeasured() {
        Level level = freshRoom();
        int frames = solve(level);
        float seconds = frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-0-stone mastered route (walk, pick up the stone, step on the platform, throw, ride across, exit): %.2f s%n", seconds);
        assertTrue(seconds < 30, "Author check, not a novice playtest (#129-#131): " + seconds);
    }

    // ---- D48: the exploding stump on the near floor, between the stone and the chasm ----

    private com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap stump(Level level) {
        return level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::cast).findFirst().orElseThrow();
    }

    private static final com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.Phase IDLE = com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.Phase.IDLE;

    private boolean lit(Level level) { return stump(level).phase() != IDLE; }

    private void waitUntil(Level level, java.util.function.BooleanSupplier done, int maxFrames, String what) {
        int n = 0;
        while (!done.getAsBoolean()) { held.clear(); frame(level); assertTrue(++n < maxFrames, "never: " + what); }
    }

    private void step(Level level, boolean right, boolean jump) {
        held.clear();
        if (right) held.add(Input.Keys.D);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(level);
    }

    /** Pick the stone up where it lies and walk with it to the given body-right edge (on the near floor). */
    private void carryStoneTo(Level level, float bodyRight) {
        walkToStone(level); upAction(level);
        assertSame(stone(level), level.player.carriedObject());
        walkRightTo(level, () -> bodyRight(level) >= bodyRight);
    }

    @Test void theStumpStandsOnTheNearFloorBetweenTheStoneAndTheChasmWithItsBlastClearOfEverythingThatMatters() {
        Level level = freshRoomWithStump();
        var stump = stump(level);
        assertEquals(1, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::isInstance).count());
        assertEquals(STUMP_X, stump.getX(), .01f);
        assertEquals(320, stump.getY() + stump.getHeight(), .01f, "on the floor");
        assertTrue(STONE_X + 12 < stump.getX() - 100, "the stone lies well before the stump, so the stone can be thrown at it");
        assertTrue(LIP - (stump.getX() + 16) >= 100, "and the chasm lies well beyond it");
        var blast = stump.blastBox();
        assertFalse(blast.overlaps(level.player.hitboxBounds()), "the blast never reaches the entrance");
        assertFalse(blast.overlaps(stone(level).bounds()), "nor the stone's home");
        assertFalse(blast.overlaps(new Rectangle(LIP - 4, 272, 40, 48)), "nor the lip or the throwing spot");
        assertFalse(blast.overlaps(level.roomLayout.rooms.get(4).exit()), "nor the exit");
        assertEquals(IDLE, stump.phase());
        // A bypass exists by construction (open floor 64 px either side, 96 px of headroom): the loader refuses otherwise.
        assertEquals(1, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::isInstance).count());
    }

    @Test void aThrownStoneSetsTheStumpOffFromAfarFromAForgivingWindowWhoseSafeEndIsOutsideTheBlast() {
        float first = Float.NaN, last = Float.NaN;
        for (float nominal = STONE_X + 20; nominal <= STUMP_X - 4; nominal += 2) {
            Level level = freshRoomWithStump();
            giveStoneAt(level, nominal);
            float at = bodyRight(level);
            throwNow(level);
            for (int i = 0; i < 60 && !lit(level); i++) frame(level);
            if (lit(level)) {
                if (Float.isNaN(first)) first = at;
                last = at;
                assertTrue(stone(level).available(), "the stone is not consumed: it lies there to be picked up again");
            }
        }
        System.out.println("forest-0-stone stump throw window (body right edge, stump at " + STUMP_X + "): " + first + " .. " + last
            + " (" + (STUMP_X - last) + " .. " + (STUMP_X - first) + " px before the stump)");
        assertFalse(Float.isNaN(first), "some throw lights the stump");
        assertTrue(last - first >= 20, "a forgiving window of at least 20 px: " + first + " .. " + last);
        assertTrue(first < STUMP_X - 36, "a hit from outside the blast square (36+ px away) exists: " + first);
    }

    @Test void afterAHitFromAfarTheHeroWaitsForTheBlastFetchesTheStoneAndFinishesTheLesson() {
        Level level = freshRoomWithStump();
        // Throw from the middle of the safe part of the window.
        float from = STUMP_X - 70;
        giveStoneAt(level, from);
        throwNow(level);
        waitUntil(level, () -> lit(level), 120, "stump lit");
        assertTrue(stone(level).available());
        assertTrue(level.player.hitboxBounds().x + 11 < stump(level).blastBox().x, "the thrower stands outside the blast");
        int start = 0;
        waitUntil(level, () -> stump(level).isSpent(), 60 * 3, "stump spent");
        assertFalse(level.player.isGotHit(), "the thrower was safe");
        // The stone lies near the stump on the near floor: retrievable and not leaking to the far side.
        Carryable stone = stone(level);
        assertTrue(stone.getX() + 12 < LIP && stone.getY() + 12 == 320, "lies on the near floor: " + stone.getX() + "," + stone.getY());
        fetchStone(level);
        assertSame(stone, level.player.carriedObject());
        int frames = goAndThrow(level);
        frames += waitForPlatform(level, true);
        assertTrue(lever(level).isUp(), "the same stone still works the lever");
        rideAcross(level); walkToExit(level);
        assertTrue(level.atRoomExit());
        assertFalse(level.player.isGotHit());
    }

    @Test void walkingOnThroughTheLitStumpAndJumpingOverItBothFinishTheLessonWithoutAHit() {
        for (boolean jump : new boolean[]{false, true}) {
            Level level = freshRoomWithStump();
            walkToStone(level); upAction(level);
            int frames = 0;
            while (bodyLeft(level) < STUMP_X + 16 + 40) {
                boolean j = jump && level.player.isOnGround() && bodyRight(level) >= STUMP_X - 36 && bodyRight(level) < STUMP_X - 20;
                step(level, true, j);
                assertTrue(alive(level), "jump=" + jump + " died at " + level.player.position);
                assertTrue(++frames < 60 * 20);
            }
            assertEquals(jump, stump(level).phase() == IDLE, "a jump leaves it untouched, a walk lights it");
            frames += goAndThrow(level);
            frames += waitForPlatform(level, true);
            frames += rideAcross(level);
            frames += walkToExit(level);
            assertTrue(level.atRoomExit() && alive(level));
        }
    }

    @Test void withNoAmmunitionAtAllTheHeroJumpsTheStumpFromAnyPointOfTheTakeOffWindowAndNeverLightsIt() {
        int ok = 0, tried = 0;
        for (float takeOff = STUMP_X - 70; takeOff <= STUMP_X - 4; takeOff += 2) {
            Level level = freshRoomWithStump();
            place(level, takeOff);
            step(level, true, true);
            tried++;
            boolean landed = false;
            for (int i = 0; i < 60 * 3 && !landed; i++) {
                step(level, true, false);
                if (!alive(level)) break;
                landed = bodyLeft(level) > STUMP_X + 16 + 4 && level.player.isOnGround();
            }
            if (landed && stump(level).phase() == IDLE) ok++;
        }
        System.out.println("forest-0-stone zero-ammo stump jump: " + ok + " of " + tried + " take-offs 4 .. 70 px before the stump clear it without lighting it");
        assertTrue(ok >= 20, "a wide jump window with no ammunition: " + ok + " of " + tried);
    }

    @Test void aHeroWhoStandsOnTheStumpIsHitAndTheRetryRestoresTheStumpTheStoneAndThePlatform() {
        Level level = freshRoomWithStump();
        place(level, STUMP_X + 10);
        int frames = 0;
        while (!level.player.isGotHit() && frames++ < 60 * 3) frame(level);
        assertTrue(level.player.isGotHit(), "staying on a lit stump costs one retry");
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertEquals(IDLE, stump(level).phase(), "retry makes the stump whole");
        assertTrue(atHome(level), "and the stone lies at home");
        assertFalse(platform(level).isRunning());
        float seconds = solve(level) * STEP;
        assertTrue(level.atRoomExit());
        System.out.printf(java.util.Locale.ROOT, "forest-0-stone retry after the stump: %.2f s from the entrance to the exit%n", seconds);
    }

    @Test void theWholeLessonStillWorksWithTheStumpInEveryOrderOfStoneAndStumpAndAfterRetryAndRestart() {
        for (int variant = 0; variant < 3; variant++) {
            Level level = freshRoomWithStump();
            if (variant == 1) { // light it first from afar with the stone, then do the lesson
                giveStoneAt(level, STUMP_X - 70); throwNow(level);
                waitUntil(level, () -> stump(level).isSpent(), 60 * 3, "spent");
                fetchStone(level);
            } else if (variant == 2) { // mid-lesson retry
                walkToStone(level); upAction(level); walkRightTo(level, () -> bodyRight(level) >= STUMP_X - 10);
                level.resetCurrentRoom(new Player(assets, game, 0, 0));
                assertEquals(IDLE, stump(level).phase());
            }
            if (level.player.carriedObject() == null) { walkToStone(level); upAction(level); }
            walkRightTo(level, () -> bodyRight(level) >= THROW_FROM);
            throwNow(level);
            waitForPlatform(level, true);
            rideAcross(level); walkToExit(level);
            assertTrue(level.atRoomExit() && alive(level), "variant " + variant);
        }
    }

    @Test void theStumpHintComesAfterTheThrowHintAndNeverContradictsTheLeverLesson() {
        Level level = freshRoomWithStump();
        walkToStone(level);
        assertEquals(Msg.HINT_STONE_THROW.t(Language.ENGLISH), level.player.zoneHint(), "the lever lesson is told first, at the stone");
        upAction(level);
        assertEquals(Msg.HINT_STONE_THROW.t(Language.ENGLISH), level.player.zoneHint(), "and still while the stone is picked up");
        walkRightTo(level, () -> bodyRight(level) >= STUMP_X - 100);
        String text = level.player.zoneHint();
        assertEquals(Msg.HINT_STONE_STUMP.t(Language.ENGLISH), text);
        assertFalse(text.toLowerCase().contains("lever"), "the stump text does not talk about the lever: " + text);
        assertTrue(text.toLowerCase().contains("stone") && text.toLowerCase().contains("jump"), "it names both answers: " + text);
    }

    @Test void noOtherRoomOfTheTutorialBeforeTheTrapsRoomHoldsAnExplosiveTrap() {
        for (int room : new int[]{0, 1, 2, 3}) {
            Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
            for (int i = 0; i < room; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
            assertTrue(level.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::isInstance), "room " + room);
        }
    }

    @Test void masteredRoutesWithTheStumpAreMeasured() {
        // Walk through the lit stump, jump over it, and set it off from afar with the stone then wait for the blast.
        float[] seconds = new float[3];
        for (int variant = 0; variant < 3; variant++) {
            Level level = freshRoomWithStump();
            int frames = walkToStone(level) + upAction(level);
            if (variant == 2) {
                frames += walkRightTo(level, () -> bodyRight(level) >= STUMP_X - 70);
                frames += throwNow(level);
                int waited = 0;
                while (!stump(level).isSpent()) { held.clear(); frame(level); waited++; }
                frames += waited;
                frames += walkToStone(level) + upAction(level);
            } else {
                while (bodyLeft(level) < STUMP_X + 16 + 40) {
                    boolean j = variant == 1 && level.player.isOnGround() && bodyRight(level) >= STUMP_X - 36 && bodyRight(level) < STUMP_X - 20;
                    step(level, true, j); frames++;
                }
            }
            frames += goAndThrow(level) + waitForPlatform(level, true) + rideAcross(level) + walkToExit(level);
            assertTrue(level.atRoomExit() && alive(level));
            seconds[variant] = frames * STEP;
        }
        System.out.printf(java.util.Locale.ROOT, "forest-0-stone mastered routes with the stump: walk through %.2f s, jump over %.2f s, set off from afar and wait %.2f s%n", seconds[0], seconds[1], seconds[2]);
        for (float t : seconds) assertTrue(t < 30, "Author check, not a novice playtest: " + t);
    }
}
