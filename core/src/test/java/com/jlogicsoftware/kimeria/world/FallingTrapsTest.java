package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.GL20;
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
import com.jlogicsoftware.kimeria.entity.objects.FallingBranch;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Position-triggered falling traps (D45, #232) in the fixture {@code falling-traps-test}: one room 640 px wide,
 * floor top y 320, the hero 11 px wide x 22 tall running at 100 px/s. The branch hangs at x 160-192, y 224-240
 * (zone x 144-192), the stone starts flush with the room top at x 400-416 (zone x 352-416).
 * Recipe: docs/ROOMS.md "Falling traps".
 */
class FallingTrapsTest {
    private static final float STEP = 1f / 60f;
    private static final float BRANCH_LEFT = 160, BRANCH_RIGHT = 192, STONE_LEFT = 400, STONE_RIGHT = 416;
    private static final float ZONE_BRANCH_LEFT = 144, ZONE_STONE_LEFT = 352;

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
    private GL20 previousGl, previousGl20;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        previousGl = Gdx.gl; previousGl20 = Gdx.gl20;
        GdxNativesLoader.load();
        // forest-1 builds a sky texture: decode the real PNG, stub only the GPU upload.
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (proxy, method, args) -> {
                Class<?> r = method.getReturnType();
                if (r == int.class) return 1;
                if (r == boolean.class) return false;
                if (r == float.class) return 0f;
                if (r == long.class) return 0L;
                return null;
            });
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getDeltaTime")) return STEP;
                Class<?> r = method.getReturnType();
                return r == boolean.class ? false : r == int.class ? 0 : r == float.class ? 0f : r == long.class ? 0L : null;
            });
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getType" -> Application.ApplicationType.HeadlessDesktop;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
        Gdx.gl = previousGl; Gdx.gl20 = previousGl20;
        held.clear(); pressed.clear();
    }

    private Level load(String map) { return new Level(assets, game, new Player(assets, game, 0, 0), map, Rectangle::new); }
    private Level level() { return load("falling-traps-test"); }
    private FallingBranch branch(Level l) { return l.objects.stream().filter(FallingBranch.class::isInstance).map(FallingBranch.class::cast).findFirst().orElseThrow(); }
    private FallingStone stone(Level l) { return l.objects.stream().filter(FallingStone.class::isInstance).map(FallingStone.class::cast).findFirst().orElseThrow(); }

    private void frame(Level l) { l.update(STEP); pressed.clear(); }
    private void settle(Level l, int frames) { held.clear(); for (int i = 0; i < frames; i++) frame(l); }
    /** Puts the hero's body (11 px wide) with its left edge at {@code bodyLeft}, standing on the floor. */
    private void place(Level l, float bodyLeft) { l.player.position.set(bodyLeft - 18, 272); settle(l, 2); }
    private float bodyLeft(Level l) { return l.player.hitboxBounds().x; }
    private boolean dead(Level l) { return l.player.isGotHit(); }
    private void run(Level l, boolean right, boolean jump) {
        held.clear();
        held.add(right ? Input.Keys.D : Input.Keys.A);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(l);
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(warnings, longer);
    }

    private int framesUntil(Level l, FallingTrap t, FallingTrap.State target, int limit) {
        int n = 0;
        while (t.state() != target && n < limit) { frame(l); n++; }
        return n;
    }

    @Test void bothTrapsLoadArmedWithTheirLandingAndZone() {
        Level l = level();
        FallingBranch b = branch(l);
        FallingStone s = stone(l);
        assertEquals(FallingTrap.State.ARMED, b.state());
        assertEquals(FallingTrap.State.ARMED, s.state());
        assertEquals(320f, b.landingBottom(), 0.001f);
        assertEquals(320f, s.landingBottom(), 0.001f);
        assertEquals(new Rectangle(144, 224, 48, 96), b.zone());
        assertNull(b.lethalBounds(), "a resting branch is scenery");
        assertNull(s.lethalBounds());
        assertTrue(l.objects.stream().noneMatch(Carryable.class::isInstance), "the trap stone is not the hero's throwable stone");
    }

    @Test void theZoneTriggersOnEntryAndNotBefore() {
        Level l = level();
        place(l, 40);
        settle(l, 120);
        assertEquals(FallingTrap.State.ARMED, branch(l).state());
        assertEquals(FallingTrap.State.ARMED, stone(l).state());
        place(l, ZONE_BRANCH_LEFT - 11 - 1); // body right edge 1 px short of the zone
        settle(l, 2);
        assertEquals(FallingTrap.State.ARMED, branch(l).state());
        place(l, ZONE_BRANCH_LEFT - 11 + 1); // overlaps by 1 px
        assertEquals(FallingTrap.State.WARNING, branch(l).state());
        assertEquals(FallingTrap.State.ARMED, stone(l).state(), "a trap reacts only to its own zone");
    }

    /** Frames the trap spends in WARNING after the frame that arms it: the update count is deterministic. */
    private int warningFrames(Level l, FallingTrap t) {
        int n = 0;
        while (t.state() == FallingTrap.State.WARNING && n < 500) { frame(l); n++; }
        return n;
    }

    private static int framesToReach(float elapsed, float total) {
        int n = 0;
        while (elapsed < total) { elapsed += STEP; n++; }
        return n;
    }

    @Test void warningLastsTheSharedD7ValueAndLongerWithTheAssist() throws Exception {
        assertEquals(0.8f, TrapWarnings.DEFAULT_WARNING_SECONDS, 0f, "pinned base value");
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            assertEquals(longer ? 1.2f : 0.8f, warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS), 1e-6f);
            for (boolean stone : new boolean[]{false, true}) {
                Level l = level();
                FallingTrap t = stone ? stone(l) : branch(l);
                l.player.position.set((stone ? ZONE_STONE_LEFT : ZONE_BRANCH_LEFT - 5) - 18, 272);
                while (t.state() == FallingTrap.State.ARMED) frame(l); // the frame that arms it counts as 0 elapsed
                assertEquals(longer ? 1.2f : 0.8f, t.warningTotalSeconds(), 1e-6f);
                // Exact: the trap adds one 1/60 s step per update (float), so the count is that sum crossing the total.
                // 0.8 s is 49 updates, not 48, because 48 float steps sum to just under 0.8; the assist gives 73 for the same reason.
                int expected = framesToReach(0f, t.warningTotalSeconds());
                assertEquals(longer ? 73 : 49, expected, "pinned literal, longer=" + longer);
                assertEquals(expected, warningFrames(l, t), (stone ? "stone" : "branch") + " warning frames, longer=" + longer);
            }
        }
    }

    @Test void pausingFreezesTheWarningAndTheFallAndResumingContinuesRatherThanRestarts() {
        Level l = level();
        FallingBranch b = branch(l);
        place(l, 148);
        for (int i = 0; i < 20; i++) frame(l);
        float elapsed = b.warningElapsedSeconds();
        assertTrue(elapsed > 0.3f);
        // KimeriaGame stops calling Level.update while paused: nothing else advances the trap, however long.
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertEquals(elapsed, b.warningElapsedSeconds(), 0f);
        assertEquals(FallingTrap.State.WARNING, b.state());
        assertEquals(framesToReach(elapsed, 0.8f), warningFrames(l, b), "the remaining warning is what was left, not a fresh 48 frames");
        float y = b.bodyBounds().y;
        for (int i = 0; i < 5; i++) frame(l);
        float fallen = b.bodyBounds().y;
        assertTrue(fallen > y);
        assertEquals(fallen, b.bodyBounds().y, 0f, "paused mid-fall: the position holds");
        assertEquals(FallingTrap.State.SPENT, b.state() == FallingTrap.State.FALLING ? runOut(l, b) : b.state());
    }

    private FallingTrap.State runOut(Level l, FallingTrap t) { framesUntil(l, t, FallingTrap.State.SPENT, 300); return t.state(); }

    @Test void branchTellsRustlesThenSagsAndFallsWithARealFlightTime() {
        Level l = level();
        FallingBranch b = branch(l);
        assertEquals(0, b.frame());
        // Stand outside the column but inside the zone: left of the branch, body right 159.
        place(l, 148);
        Set<Integer> early = new HashSet<>(), late = new HashSet<>();
        while (b.state() == FallingTrap.State.WARNING) {
            (b.warningProgress() < 0.5f ? early : late).add(b.frame());
            frame(l);
        }
        assertEquals(Set.of(0, 1), early, "rustle: leaves shake against the resting frame");
        assertEquals(Set.of(2), late, "sag");
        assertEquals(FallingTrap.State.FALLING, b.state());
        assertEquals(3, b.frame());
        int flight = framesUntil(l, b, FallingTrap.State.SPENT, 300);
        float expected = FallingTrap.flightSeconds(320 - 240);
        assertEquals(0.42f, expected, 0.01f);
        assertEquals(expected / STEP, flight, 3, "80 px drop under 900 px/s^2");
        assertEquals(320f, b.bodyBounds().y + b.bodyBounds().height, 0.001f);
        assertFalse(dead(l), "hero left of the column is not hit");
    }

    @Test void aHeroWhoRunsAwayInTimeSurvivesAndOneWhoStandsStillDies() {
        Level run = level();
        place(run, 150); // under the foliage: body 150-161 overlaps the column from 160
        for (int i = 0; i < 85; i++) { run(run, true, false); assertFalse(dead(run), "frame " + i); }
        assertTrue(branch(run).isSpent());
        assertTrue(bodyLeft(run) > BRANCH_RIGHT);

        Level back = level();
        place(back, 150);
        for (int i = 0; i < 85; i++) { run(back, false, false); assertFalse(dead(back), "frame " + i); }
        assertTrue(branch(back).isSpent());

        Level still = level();
        place(still, 150);
        settle(still, 240);
        assertTrue(dead(still), "standing under the branch is lethal");
    }

    @Test void impactKillsOnOverlapOnly() {
        for (float left : new float[]{148, 160 - 11}) { // body right 159 and 160 (touching the column)
            Level l = level();
            place(l, left);
            settle(l, 240);
            assertTrue(branch(l).isSpent(), "left=" + left);
            assertFalse(dead(l), "no overlap at left=" + left);
        }
        for (float left : new float[]{150, 175, 191}) {
            Level l = level();
            place(l, left);
            settle(l, 240);
            assertTrue(dead(l), "overlap at left=" + left);
        }
    }

    @Test void aSpentBranchIsHarmlessSingleShotSceneryAndRetryRearmsIt() {
        Level l = level();
        place(l, 148);
        settle(l, 200);
        FallingBranch b = branch(l);
        assertTrue(b.isSpent());
        assertNull(b.lethalBounds());
        place(l, 170); // stands exactly where it landed, and re-enters its zone
        settle(l, 240);
        assertFalse(dead(l), "a landed branch does not hurt");
        assertTrue(b.isSpent(), "single shot until the room is rebuilt");
        assertEquals(3, b.frame());
        l.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertEquals(FallingTrap.State.ARMED, branch(l).state());
        assertEquals(224f, branch(l).bodyBounds().y, 0.001f);
        assertNotSame(b, branch(l));
        assertEquals(FallingTrap.State.ARMED, load("falling-traps-test").objects.stream().filter(FallingBranch.class::isInstance)
            .map(FallingBranch.class::cast).findFirst().orElseThrow().state(), "Restart Level builds a new level");
    }

    @Test void aRunningHeroPassesFromTheZoneEdgeWhetherHeWalksOrJumpsAtAnyFrame() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (int jumpAt = -1; jumpAt < 40; jumpAt++) {
                Level l = level();
                place(l, ZONE_BRANCH_LEFT - 11 + 1); // the first pixel inside the zone
                for (int i = 0; i < 120; i++) {
                    run(l, true, i == jumpAt && l.player.isOnGround());
                    assertFalse(dead(l), "longer=" + longer + " jumpAt=" + jumpAt + " frame " + i);
                }
                assertTrue(branch(l).isSpent());
            }
        }
    }

    @Test void theBranchHasMarginForANaturalWalkNotJustAnOptimalOne() {
        // Walking right from the zone edge, the hero's body is clear of the column this long before it falls.
        Level l = level();
        place(l, ZONE_BRANCH_LEFT - 11 + 1);
        int clearAt = 0;
        while (bodyLeft(l) < BRANCH_RIGHT) { run(l, true, false); clearAt++; }
        float margin = TrapWarnings.DEFAULT_WARNING_SECONDS - clearAt * STEP;
        assertTrue(margin >= 0.15f, "margin before the fall starts: " + margin);
    }

    @Test void stoneShadowGrowsThroughTheWarningThenTheStoneFallsAndKillsOnlyOnOverlap() {
        Level l = level();
        FallingStone s = stone(l);
        assertFalse(s.shadowVisible());
        assertFalse(s.stoneVisible());
        place(l, ZONE_STONE_LEFT - 11 + 1 + 40); // inside the zone, short of the column
        float last = 0; int lastFrame = 0;
        Set<Integer> frames = new HashSet<>();
        while (s.state() == FallingTrap.State.WARNING) {
            assertTrue(s.shadowVisible());
            assertFalse(s.stoneVisible(), "only the shadow warns");
            assertTrue(s.shadowScale() >= last, "never shrinks");
            assertTrue(s.shadowFrame() >= lastFrame);
            last = s.shadowScale(); lastFrame = s.shadowFrame();
            frames.add(s.shadowFrame());
            frame(l);
        }
        assertEquals(Set.of(0, 1, 2), frames);
        assertTrue(last > 0.95f, "nearly full at the end of the warning: " + last);
        assertEquals(FallingStone.SHADOW_START_SCALE, 0.35f, 0f);
        assertTrue(s.stoneVisible());
        assertEquals(1f, s.shadowScale(), 1e-6f);
        assertNotNull(s.lethalBounds());
        int flight = framesUntil(l, s, FallingTrap.State.SPENT, 300);
        assertEquals(FallingTrap.flightSeconds(320 - 16) / STEP, flight, 3);
        assertEquals(1, s.stoneFrame(), "cracked on impact");
        assertNull(s.lethalBounds());
        assertFalse(dead(l));
    }

    @Test void aHeroWhoStandsUnderTheShadowDiesAndOneBesideItDoesNot() {
        for (float left : new float[]{STONE_LEFT - 11 + 1, 405, STONE_RIGHT - 1}) {
            Level l = level();
            place(l, left);
            settle(l, 300);
            assertTrue(dead(l), "under the stone at left=" + left);
        }
        for (float left : new float[]{STONE_LEFT - 11}) {
            Level l = level();
            place(l, left);
            settle(l, 300);
            assertTrue(stone(l).isSpent());
            assertFalse(dead(l), "beside the stone at left=" + left);
        }
    }

    @Test void aRunningHeroPassesTheStoneColumnWithTheTelegraphInView() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (int jumpAt = -1; jumpAt < 40; jumpAt += 3) {
                Level l = level();
                place(l, ZONE_STONE_LEFT - 11 + 1);
                boolean shadowSeen = false;
                for (int i = 0; i < 200; i++) {
                    run(l, true, i == jumpAt && l.player.isOnGround());
                    shadowSeen |= stone(l).shadowVisible();
                    assertFalse(dead(l), "longer=" + longer + " jumpAt=" + jumpAt + " frame " + i);
                }
                assertTrue(shadowSeen);
                assertTrue(stone(l).isSpent());
                assertTrue(bodyLeft(l) > STONE_RIGHT);
            }
        }
    }

    @Test void theStoneIsSingleShotUntilRetryAndSpentStoneIsHarmlessScenery() {
        Level l = level();
        place(l, ZONE_STONE_LEFT);
        settle(l, 300);
        FallingStone s = stone(l);
        assertTrue(s.isSpent());
        place(l, 402); // right on it
        settle(l, 300);
        assertFalse(dead(l));
        assertTrue(s.isSpent());
        assertEquals(1, s.stoneFrame());
        l.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertEquals(FallingTrap.State.ARMED, stone(l).state());
        assertFalse(stone(l).stoneVisible());
        assertTrue(l.objects.stream().noneMatch(Carryable.class::isInstance));
    }

    @Test void stoneSkinsResolveByNameAndUnknownOnesFail() {
        assertSame(com.jlogicsoftware.kimeria.SpriteSheets.TRAP_STONE, FallingStone.skin("forest").stone());
        assertSame(com.jlogicsoftware.kimeria.SpriteSheets.TRAP_SHADOW, FallingStone.skin("Forest").shadow());
        assertEquals("forest", FallingStone.DEFAULT_SKIN);
        assertThrows(IllegalArgumentException.class, () -> FallingStone.skin("castle"));
    }

    @Test void authoringMistakesFailTheLevelLoadWithTheObjectNamed() {
        var ceiling = assertThrows(IllegalArgumentException.class, () -> load("falling-bad-ceiling-test"));
        assertTrue(ceiling.getMessage().contains("outside rooms"), "a stone above the room top is in the D38 slab: " + ceiling.getMessage());
        var shortDrop = assertThrows(IllegalArgumentException.class, () -> load("falling-bad-short-test"));
        assertTrue(shortDrop.getMessage().contains("too short"), shortDrop.getMessage());
        var skin = assertThrows(IllegalArgumentException.class, () -> load("falling-bad-skin-test"));
        assertTrue(skin.getMessage().contains("castle"), skin.getMessage());
        var zone = assertThrows(IllegalArgumentException.class, () -> load("falling-bad-zone-test"));
        assertTrue(zone.getMessage().contains("zoneY"), zone.getMessage());
    }

    @Test void theMinimumFlightTimeAndGravityArePinned() {
        assertEquals(900f, FallingTrap.GRAVITY, 0f);
        assertEquals(0.3f, FallingTrap.MIN_FLIGHT_SECONDS, 0f);
        assertEquals(0.3f, FallingTrap.flightSeconds(40.5f), 0.001f);
    }

    @Test void theInvisibleCeilingStillClosesTheRoomAboveTheStoneStart() {
        Level l = level();
        Rectangle room = l.currentRoomBounds();
        assertTrue(l.collisionBlocks.stream().anyMatch(b -> b.getY() + b.getHeight() == room.y),
            "the D38 slab sits above the room");
        assertTrue(stone(l).bodyBounds().y >= room.y, "the stone starts inside the room, flush with its top");
    }

    @Test void onlyForestZeroUsesTheFallingTrapsOneBranchInTheWallRoomAndOneStoneInThePlatformRoom() {
        for (String map : new String[]{"forest-1", "forest-2"}) {
            String text = Gdx.files.internal("tiles/" + map + ".tmx").readString();
            assertFalse(text.contains("FallingBranch") || text.contains("FallingStone"), map);
            assertTrue(load(map).objects.stream().noneMatch(FallingTrap.class::isInstance), map);
        }
        String text = Gdx.files.internal("tiles/forest-0.tmx").readString();
        assertEquals(1, text.split("type=\"FallingBranch\"", -1).length - 1, "D48: one branch");
        assertEquals(1, text.split("type=\"FallingStone\"", -1).length - 1, "D48: one stone");
    }

    // ---- Jump rule for the branch (D45): lethal at impact for anyone overlapping the landed box; in the fall only
    // a hero standing on the ground is hurt, so a hero airborne above the landed branch at impact survives. ----

    /** Frames from the first update in FALLING to the first in SPENT, i.e. the impact update's index after arming. */
    private int impactFrame() {
        Level l = level();
        place(l, 170);
        int n = 0;
        while (!branch(l).isSpent() && n < 400) { frame(l); n++; }
        return n;
    }

    /** Hero stands in the column; presses Jump {@code before} frames before the impact update. */
    private boolean survivesJumpPressedBefore(int before) {
        Level l = level();
        place(l, 170);
        int impact = impactFrame() - 2; // place() already ran two updates
        for (int i = 0; i < impact + 10; i++) {
            held.clear();
            if (i == impact - before) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(l);
            if (dead(l)) return false;
        }
        return branch(l).isSpent();
    }

    private int[] jumpWindow() {
        int lo = -1, hi = -1;
        for (int before = 0; before <= 80; before++) {
            if (survivesJumpPressedBefore(before)) { if (lo < 0) lo = before; hi = before; }
        }
        for (int before = lo; before <= hi; before++) assertTrue(survivesJumpPressedBefore(before), "contiguous: " + before);
        return new int[]{lo, hi};
    }

    @Test void aJumpTimedToBeAirborneAboveTheLandedBranchAtImpactSurvivesWithARealWindow() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            int[] w = jumpWindow();
            assertEquals(EXPECTED_JUMP_WINDOW_LO, w[0], "longer=" + longer);
            assertEquals(EXPECTED_JUMP_WINDOW_HI, w[1], "longer=" + longer);
            assertFalse(survivesJumpPressedBefore(w[0] - 1), "too late: still below the landed branch's top at impact");
            assertFalse(survivesJumpPressedBefore(w[1] + 1), "too early: back on the ground under the falling branch");
        }
    }

    @Test void standingStillDiesEvenIfTheHeroJumpsAfterImpact() {
        assertFalse(survivesJumpPressedBefore(-1));
        assertFalse(survivesJumpPressedBefore(1000));
    }

    private static final int EXPECTED_JUMP_WINDOW_LO = 3, EXPECTED_JUMP_WINDOW_HI = 48;

    // ---- Load-time validation of every room (D45) ----

    private String loadError(String map) { return assertThrows(IllegalArgumentException.class, () -> load(map)).getMessage(); }

    @Test void aBadTrapInALaterRoomFailsTheLevelLoadNotTheWalkIntoThatRoom() {
        String message = loadError("falling-bad-late-test");
        assertTrue(message.contains("no solid floor"), message);
        assertTrue(message.contains("[11]"), message);
    }

    @Test void authoringGapsThatWouldLoadButBreakAreRejectedAtLoad() {
        assertTrue(loadError("falling-bad-zonelow-test").contains("never trigger"), "zone too high for a walking hero");
        assertTrue(loadError("falling-bad-zoneapart-test").contains("lethal column"), "zone not under the trap");
        assertTrue(loadError("falling-bad-entrance-test").contains("entrance"), "armed at the room start with the hero in the column");
        assertTrue(loadError("falling-bad-midair-test").contains("mid-air"), "stone below the room top");
        assertTrue(loadError("falling-bad-wall-test").contains("Actionable"), "lever-raised wall in the fall");
    }

    @Test void badNumbersAreRejectedNotSilentlyZero() {
        assertTrue(loadError("falling-bad-nan-test").contains("zoneX"));
        assertTrue(loadError("falling-bad-inf-test").contains("finite"));
        assertTrue(loadError("falling-bad-text-test").contains("not a number"));
        assertTrue(loadError("falling-bad-negative-test").contains("positive"));
    }

    @Test void theHeroBodyConstantsMatchThePlayerHitbox() {
        Level l = level();
        place(l, 100);
        Rectangle b = l.player.hitboxBounds();
        assertEquals(l.player.position.x + FallingTrap.HERO_BODY_OFFSET_X, b.x, 0.001f);
        assertEquals(l.player.position.y + FallingTrap.HERO_BODY_OFFSET_Y, b.y, 0.001f);
        assertEquals(FallingTrap.HERO_BODY_WIDTH, b.width, 0.001f);
        assertEquals(FallingTrap.HERO_BODY_HEIGHT, b.height, 0.001f);
    }

    @Test void theSkinListIsTheForestOnlyAndThePythonCheckerReadsItFromThisClass() {
        assertEquals(Set.of("forest"), FallingStone.skinNames());
    }

    @Test void theStoneHurtsAJumpingHeroToo() {
        for (int before = 0; before <= 60; before += 3) {
            Level probe = level();
            place(probe, 405);
            int n = 0;
            while (!stone(probe).isSpent() && n < 400) { frame(probe); n++; }
            Level l = level();
            place(l, 405);
            int impact = n - 2;
            for (int i = 0; i < impact + 10; i++) {
                held.clear();
                if (i == impact - before) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(l);
            }
            assertTrue(dead(l), "a stone does not spare a jumper, jump " + before + " frames before impact");
        }
    }
}
