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
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
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

    private final java.util.List<String> sounds = new java.util.ArrayList<>();
    private boolean shakeDisabled;
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
            case "playSounds" -> true;
            case "playSound" -> sounds.add((String) args[0]);
            case "screenShakeDisabled" -> shakeDisabled;
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
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            assertEquals(ExpectedWarnings.standard(longer), warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS), 1e-6f);
            for (boolean stone : new boolean[]{false, true}) {
                Level l = level();
                FallingTrap t = stone ? stone(l) : branch(l);
                l.player.position.set((stone ? ZONE_STONE_LEFT : ZONE_BRANCH_LEFT - 5) - 18, 272);
                while (t.state() == FallingTrap.State.ARMED) frame(l); // the frame that arms it counts as 0 elapsed
                assertEquals(ExpectedWarnings.standard(longer), t.warningTotalSeconds(), 1e-6f);
                // Exact: the trap adds one 1/60 s step per update (float), so the count is that sum crossing the total.
                // The count is not seconds/step rounded: float steps can sum to just under the total, so one more update is needed.
                int expected = framesToReach(0f, t.warningTotalSeconds());
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
        assertEquals(framesToReach(elapsed, TrapWarnings.DEFAULT_WARNING_SECONDS), warningFrames(l, b), "the remaining warning is what was left, not a fresh full warning");
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

    /** #347: every stone landing kicks up dust, chips and fading debris, all on or above the floor line (Y grows downward), and a small shake. */
    @Test void aLandingStoneThrowsDustChipsAndDebrisOnTheFloorLine() {
        Level l = level();
        FallingStone s = stone(l);
        place(l, ZONE_STONE_LEFT - 11 + 1 + 40);
        assertEquals(0, l.fx().dust().count() + l.fx().grit().count());
        framesUntil(l, s, FallingTrap.State.SPENT, 600);
        var dust = l.fx().dust();
        var grit = l.fx().grit();
        assertTrue(dust.count() >= com.jlogicsoftware.kimeria.effects.EnvironmentFx.STONE_DUST, "dust: " + dust.count());
        assertTrue(grit.count() >= com.jlogicsoftware.kimeria.effects.EnvironmentFx.STONE_CHIPS
            + com.jlogicsoftware.kimeria.effects.EnvironmentFx.STONE_DEBRIS, "chips and debris: " + grit.count());
        float floor = s.bodyBounds().y + s.bodyBounds().height;
        assertEquals(320f, floor, 1e-3f);
        for (int i = 0; i < dust.count(); i++) {
            // The particle square's bottom edge (y + size/2) is the floor line at the start: nothing is drawn under the floor.
            assertTrue(dust.y(i) + dust.drawSize(i) / 2f <= floor + 1e-3f, "dust " + i + " sits on the floor line");
            assertTrue(dust.x(i) >= s.bodyBounds().x - 1 && dust.x(i) <= s.bodyBounds().x + s.bodyBounds().width + 1);
        }
        float lowest = 0;
        for (int i = 0; i < grit.count(); i++) {
            lowest = Math.max(lowest, grit.y(i) + grit.drawSize(i) / 2f);
            assertTrue(grit.y(i) + grit.drawSize(i) / 2f <= floor + 1e-3f, "grit " + i + " is not below the floor");
        }
        assertEquals(floor, lowest, 1e-3f, "the debris rests exactly on the floor line");
        assertTrue(l.fx().shake().running(), "a small shake starts on impact");
        assertEquals(com.jlogicsoftware.kimeria.effects.ScreenShake.STONE_AMPLITUDE_PX, l.fx().shake().currentAmplitude(), 0.1f);
        // The debris fades: it lies there 2 to 3 s (still there at 1.5 s), the dust is gone long before.
        for (int i = 0; i < 90; i++) frame(l);
        assertEquals(0, dust.count());
        assertTrue(grit.count() > 0, "the debris still lies there after 1.5 s");
        for (int i = 0; i < 120; i++) frame(l);
        assertEquals(0, grit.count(), "the debris has faded");
    }

    @Test void theImpactPlaysStoneImpactOnceAndOnlyAtLanding() {
        Level l = level();
        FallingStone s = stone(l);
        place(l, ZONE_STONE_LEFT - 11 + 1 + 40);
        sounds.clear();
        framesUntil(l, s, FallingTrap.State.FALLING, 600);
        assertFalse(sounds.contains("stone_impact"), "silent before the landing");
        framesUntil(l, s, FallingTrap.State.SPENT, 600);
        for (int i = 0; i < 30; i++) frame(l);
        assertEquals(1, sounds.stream().filter("stone_impact"::equals).count(), sounds.toString());
        assertTrue(java.util.Arrays.asList(Assets.SOUND_EFFECTS).contains("stone_impact"), "the sound is preloaded");
    }

    @Test void withScreenShakeOffTheLandingDoesNotMoveTheCameraButDustAndDebrisStillShow() {
        shakeDisabled = true;
        Level l = level();
        place(l, ZONE_STONE_LEFT - 11 + 1 + 40);
        framesUntil(l, stone(l), FallingTrap.State.SPENT, 600);
        assertFalse(l.fx().shake().enabled());
        Rectangle view = new Rectangle(0, 0, 320, 180);
        assertEquals(0f, l.cameraShakeOffset(view).len(), 0f);
        assertTrue(l.fx().dust().count() > 0 && l.fx().grit().count() > 0);
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

    @Test void theFallingTrapsAreTheTutorialsTheForestTwoApproachsAndForestOnesFiveReworkedRoomsTraps() {
        // #500 (D105): forest-1 has two branches, one in forest-1-mob and one in forest-1-bramble, and three stones, one each in
        // forest-1-deadfall, forest-1-spider and forest-1-climb (before the rework, none).
        String one = Gdx.files.internal("tiles/forest-1.tmx").readString();
        assertEquals(2, one.split("type=\"FallingBranch\"", -1).length - 1, "forest-1: two branches (#500)");
        assertEquals(3, one.split("type=\"FallingStone\"", -1).length - 1, "forest-1: three stones (#500)");
        // #261 (D59): forest-2 has no branch; its stones are at the end of forest-canopy and, since #330, at the end of forest-roost.
        String two = Gdx.files.internal("tiles/forest-2.tmx").readString();
        assertFalse(two.contains("FallingBranch"), "forest-2 has no branch");
        assertEquals(2, two.split("type=\"FallingStone\"", -1).length - 1, "forest-2: two stones (#330)");
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
        assertTrue(loadError("falling-bad-lip-test").contains("off screen"), "#360: a lipTop above the camera's top edge with the hero on the floor");
        assertTrue(loadError("falling-bad-lipfloat-test").contains("floats"), "#360: a lipTop in an open column, under nothing");
        assertTrue(loadError("falling-bad-liptypo-test").contains("unknown property 'liptop'"), "#360: a misspelled key is refused, not ignored");
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

    // ---- #360: the loose overhang (the at-rest cue) -------------------------------------------------------------------

    private static final float STONE_CENTRE = (STONE_LEFT + STONE_RIGHT) / 2f, FAR_LEFT = 40;

    /** Every speck the stone's trickle spawns over {@code frames} frames, as (x, y) of its first sampled position. */
    private java.util.List<float[]> specks(Level l, int frames) {
        java.util.List<float[]> seen = new java.util.ArrayList<>();
        var grit = stone(l).grit();
        for (int i = 0; i < frames; i++) {
            frame(l);
            // A speck spawned during this frame's update has aged one step at most (specks are TRICKLE_SPACING = 6 steps apart).
            for (int k = 0; k < grit.count(); k++)
                if (grit.maxLife(k) - grit.life(k) < STEP * 1.5f) seen.add(new float[]{grit.x(k), grit.y(k), grit.red(k)});
        }
        return seen;
    }

    @Test void atRestTheLipIsLooseAndGritTricklesFromItsTwoCracksEveryPeriod() {
        Level l = level();
        place(l, FAR_LEFT);
        FallingStone s = stone(l);
        assertEquals(FallingStone.LIP_LOOSE, s.lipFrame());
        int periods = 4;
        var seen = specks(l, Math.round(periods * FallingStone.TRICKLE_PERIOD / STEP));
        assertEquals(FallingTrap.State.ARMED, s.state(), "the hero stayed far");
        assertEquals(periods * FallingStone.TRICKLE_SPECKS, seen.size(), "four specks per 1.5 s");
        int left = 0, right = 0, dark = 0;
        for (float[] p : seen) {
            float dx = p[0] - STONE_CENTRE;
            assertTrue(Math.abs(Math.abs(dx) - FallingStone.CRACK_OFFSET_PX) <= FallingStone.CRACK_JITTER_PX + 0.01f, "from a crack: " + dx);
            if (dx < 0) left++; else right++;
            if (p[2] < 0.5f) dark++;
            // World Y grows downward: a speck starts just below the lip's top and only falls.
            float start = s.lipBounds().y + FallingStone.GRIT_START_PX;
            assertTrue(p[1] >= start && p[1] < start + 2, "starts in the lip: " + p[1]);
        }
        assertEquals(left, right, "the cracks alternate");
        assertEquals(seen.size() / 2, dark, "dark and light specks alternate");
        assertTrue(s.grit().count() <= FallingStone.GRIT_CAPACITY);
    }

    @Test void theTrickleNeverOverflowsItsPoolAndEverySpeckFallsAboutFortyEightPixels() {
        Level l = level();
        place(l, FAR_LEFT);
        var grit = stone(l).grit();
        float start = stone(l).lipBounds().y + FallingStone.GRIT_START_PX;
        int peak = 0;
        float lowest = 0;
        for (int i = 0; i < Math.round(20 * FallingStone.TRICKLE_PERIOD / STEP); i++) {
            frame(l);
            peak = Math.max(peak, grit.count());
            for (int k = 0; k < grit.count(); k++) lowest = Math.max(lowest, grit.y(k));
        }
        assertEquals(FallingStone.TRICKLE_SPECKS, peak, "a trickle lives 0.8 s of a 1.5 s period: one trickle at a time");
        assertTrue(peak < FallingStone.GRIT_CAPACITY, "headroom: the oldest speck is never dropped");
        assertTrue(lowest <= start + FallingStone.TRICKLE_FALL_PX + FallingStone.FALL_JITTER_PX, "fall " + lowest);
        assertTrue(lowest >= start + FallingStone.TRICKLE_FALL_PX - FallingStone.FALL_JITTER_PX - 2, "fall " + lowest);
    }

    @Test void theTrickleIsDeterministic() {
        Level a = level(), b = level();
        place(a, FAR_LEFT);
        place(b, FAR_LEFT);
        var sa = specks(a, 300);
        var sb = specks(b, 300);
        assertEquals(sa.size(), sb.size());
        for (int i = 0; i < sa.size(); i++) assertArrayEquals(sa.get(i), sb.get(i), 0f, "speck " + i);
    }

    @Test void theTriggerStopsTheTrickleAndTheLipBreaksWhenTheStoneFalls() {
        Level l = level();
        place(l, FAR_LEFT);
        FallingStone s = stone(l);
        settle(l, Math.round(0.2f / STEP));
        assertTrue(s.grit().count() > 0, "a trickle is falling");
        place(l, ZONE_STONE_LEFT);
        assertEquals(FallingTrap.State.WARNING, s.state());
        assertEquals(FallingStone.LIP_LOOSE, s.lipFrame(), "the warning is unchanged: the shadow grows under the loose lip");
        int maxAfter = s.grit().count();
        for (int i = 0; i < Math.round(FallingStone.TRICKLE_LIFE / STEP) + 1; i++) {
            frame(l);
            assertTrue(s.grit().count() <= maxAfter, "no new speck after the trigger");
            maxAfter = s.grit().count();
        }
        assertEquals(0, s.grit().count(), "the last specks have died");
        framesUntil(l, s, FallingTrap.State.FALLING, 600);
        assertEquals(FallingStone.LIP_BROKEN, s.lipFrame(), "the socket is empty once the stone falls");
        framesUntil(l, s, FallingTrap.State.SPENT, 600);
        assertEquals(FallingStone.LIP_BROKEN, s.lipFrame());
        settle(l, Math.round(3 * FallingStone.TRICKLE_PERIOD / STEP));
        assertEquals(0, s.grit().count(), "a spent stone trickles no more");
    }
}
