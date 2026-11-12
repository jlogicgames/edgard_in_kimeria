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
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.entity.objects.ThornVine;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three traps placed in forest-2's approach to the boss by #261 (D59): a swinging thorn vine, an exploding root and a
 * falling stone in the 448 px stretch added to the end of forest-canopy. Ordinary keyboard input drives the hero through the
 * real shipped map. Times are author (mastered) measurements of a scripted hero, not novice playtests. Plan and numbers:
 * docs/ROOMS.md.
 *
 * <p>Y grows downward: the floor top is y 320; a trap standing on it has y = 320 - height.
 */
class Forest2TrapsTest {
    private static final float STEP = 1f / 60f;
    // Authored geometry (docs/ROOMS.md, "forest-2 trap placement plan").
    static final float VINE_X = 1896, ROOT_X = 2064, STONE_X = 2224, EXIT_X = 2320, ENTRANCE_X = 1344, BAT_X = 1760;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
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
        HeadlessFiles files = new HeadlessFiles();
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


    private void setLonger(boolean longer) {
        try {
            var field = TrapWarnings.class.getDeclaredField("longer");
            field.setAccessible(true);
            field.setBoolean(warnings, longer);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private Player player() { return new Player(assets, game, 0, 0); }

    private RangedStock emptyStock() { RangedStock s = new RangedStock(); s.restore(0); return s; }

    /** The shipped forest-2 with the hero in forest-canopy at its entrance and no ammunition. */
    private Level level() {
        Level l = new Level(assets, game, player(), "forest-2", Rectangle::new, emptyStock());
        l.enterNextRoom(player());
        settle(l, 2);
        return l;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }

    private ThornVine vine(Level l) { return l.objects.stream().filter(ThornVine.class::isInstance).map(ThornVine.class::cast).findFirst().orElseThrow(); }
    private ExplosiveTrap root(Level l) { return l.objects.stream().filter(ExplosiveTrap.class::isInstance).map(ExplosiveTrap.class::cast).findFirst().orElseThrow(); }
    private FallingStone stone(Level l) { return l.objects.stream().filter(FallingStone.class::isInstance).map(FallingStone.class::cast).findFirst().orElseThrow(); }

    private float bodyRight(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width; }
    private boolean dead(Level l) { return l.touchesDeadline(l.player.hitboxBounds()) || l.player.isGotHit(); }
    private void settle(Level l, int frames) { for (int i = 0; i < frames; i++) frame(l); }
    /** Body right edge at {@code right}, standing on the floor, hero facing right. */
    private void place(Level l, float right) { l.player.position.set(right - 29, 272); settle(l, 3); }
    private void retry(Level l) { l.resetCurrentRoom(player()); settle(l, 2); }

    private boolean mobAhead(Level l) {
        for (var o : l.objects) if (o instanceof Enemy<?> e) {
            Rectangle en = e.hitboxBounds(), me = l.player.hitboxBounds();
            if (l.player.isOnGround() && en.x >= me.x && en.x - (me.x + me.width) < 18
                && en.y < me.y + me.height && en.y + en.height > me.y) return true;
        }
        return false;
    }

    /** What the scripted hero does; the canopy's old part (RedMob at 1472) is the same for all. */
    private static final class Plan {
        boolean jumpRoot = true;
        float rootJumpRight = ROOT_X - 30, startWaitSeconds = 0;
        int waitAtVine = 0, delayFrames = 0;
    }

    private record Run(int frames, boolean exited, boolean died, float x) {}

    /** Walks from where the hero stands to the room exit with real keys; {@code stopRight} halts the hero at a body edge. */
    private Run run(Level l, Plan plan, int maxFrames, float stopRight) {
        Player p = l.player;
        int lastJump = -2;
        boolean jumpedStep = p.position.x > 1500, actionWasDown = false, wasMechanism = false;
        for (int f = 0; f < maxFrames; f++) {
            held.clear(); pressed.clear();
            float r = bodyRight(l);
            boolean doJump = false, right = r < stopRight && f >= plan.delayFrames;
            if (!jumpedStep && p.position.x >= 1400 && p.isOnGround()) { doJump = true; jumpedStep = true; }
            if (plan.jumpRoot && r >= plan.rootJumpRight && r < plan.rootJumpRight + 6 && p.isOnGround() && f - lastJump > 1) doJump = true;
            if (right) held.add(Input.Keys.D);
            if (doJump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); lastJump = f; }
            boolean mechanism = p.canInteract(), action = mechanism;
            if (mobAhead(l)) { action = true; mechanism = false; }
            if (mechanism) held.add(Input.Keys.W);
            if (action && (!actionWasDown || wasMechanism != mechanism)) pressed.add(Input.Keys.K);
            wasMechanism = mechanism; actionWasDown = action;
            frame(l);
            if (dead(l)) return new Run(f + 1, false, true, p.position.x);
            if (l.atRoomExit() || p.getState() == Player.State.DISAPPEARING) return new Run(f + 1, true, false, p.position.x);
        }
        return new Run(maxFrames, false, false, p.position.x);
    }

    // ---- geometry and census ----

    @Test void theStretchHoldsExactlyTheThreeNewTrapsAtTheirAuthoredPlaces() {
        Level l = level();
        assertEquals(1, l.objects.stream().filter(ThornVine.class::isInstance).count());
        assertEquals(VINE_X, vine(l).getX(), .01f);
        assertEquals(256, vine(l).anchorY(), .01f, "the top edge is the anchor (y grows downward)");
        assertEquals(320, vine(l).anchorY() + 64, .01f, "the floor is 64 px below the anchor, the D46 recipe");
        assertEquals(ThornVine.DEFAULT_PERIOD_SECONDS, vine(l).period(), .001f);
        assertEquals(1, l.objects.stream().filter(ExplosiveTrap.class::isInstance).count());
        assertEquals(ExplosiveTrap.Kind.ROOT, root(l).kind());
        assertEquals(ROOT_X, root(l).getX(), .01f);
        assertEquals(320, root(l).getY() + root(l).getHeight(), .01f, "stands on the floor top");
        assertEquals(1, l.objects.stream().filter(FallingStone.class::isInstance).count());
        assertEquals(STONE_X, stone(l).getX(), .01f);
        assertEquals(0, stone(l).bodyBounds().y, .01f, "starts flush with the room top");
        assertEquals(320, stone(l).landingBottom(), .01f);
        assertTrue(l.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.items.Bomb.class::isInstance), "no Bomb shares the canopy");
        String text = Gdx.files.internal("tiles/forest-2.tmx").readString();
        assertEquals(1, text.split("type=\"Bomb\"", -1).length - 1, "the one Bomb stays, in the entry room");
        assertTrue(text.contains("<object id=\"54\" name=\"Bomb\" type=\"Bomb\" x=\"656\" y=\"320\""), "unmoved");
        assertEquals(0, l.objects.stream().filter(FallingTrap.class::isInstance).filter(o -> !(o instanceof FallingStone)).count());
        Rectangle room = l.currentRoomBounds();
        assertEquals(1280, room.x); assertEquals(1120, room.width, "the canopy grew by 448 px");
    }

    @Test void nothingSharesAnEnvelopeOrATelegraphAndEveryGapIsAtLeast48Px() {
        Level l = level();
        float anchor = vine(l).anchorX(), reach = vine(l).length() + 4;
        float vineLeft = anchor - reach, vineRight = anchor + reach;
        Rectangle blast = root(l).blastBox();
        float envLeft = ROOT_X - 64, envRight = ROOT_X + 16 + 64;
        Rectangle zone = stone(l).zone();
        float batReachEnd = BAT_X + 24;
        assertTrue(vineLeft - batReachEnd >= 48, "Bat reach to vine swept area");
        assertTrue(envLeft - vineRight >= 48, "vine swept area to the root's jump envelope");
        assertTrue(zone.x - envRight >= 48, "root envelope to the stone zone");
        assertTrue(EXIT_X - (zone.x + zone.width) >= 48, "stone zone to the RoomExit");
        assertTrue(EXIT_X - (blast.x + blast.width) >= 48, "root blast square to the RoomExit");
        assertTrue(blast.x - ENTRANCE_X >= 48);
        // No enemy stands or patrols between the Bat and the exit; the only walking enemies of the room are before the Bat.
        for (var o : l.objects) if (o instanceof Enemy<?> e && e.getX() > 1500 && e.getX() < 1790)
            assertTrue(e.getX() < vineLeft - 48, "a flying enemy only at the Bat's x");
    }

    @Test void theBossRoomWasOnlyTranslatedBy448() {
        Level l = new Level(assets, game, player(), "forest-2", Rectangle::new, emptyStock());
        l.enterNextRoom(player()); l.enterNextRoom(player());
        assertEquals(2560, l.currentRoomBounds().x, .01f);
        assertEquals(448, l.currentRoomBounds().width, .01f);
        assertEquals(2640, l.player.position.x, .01f);
        assertEquals(304, l.player.position.y, .01f);
    }

    // ---- the mastered route (zero ammunition, D26) ----

    private float routeSeconds(boolean longer, boolean jumpRoot) {
        setLonger(longer);
        Level l = level();
        float first = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            held.clear(); pressed.clear();
            if (attempt > 0) retry(l);
            assertEquals(0, l.rangedStock.count(), "no ammunition on the route");
            Plan plan = new Plan(); plan.jumpRoot = jumpRoot;
            Run r = run(l, plan, 60 * 30, Float.MAX_VALUE);
            assertTrue(r.exited(), "reaches the exit, died=" + r.died() + " x=" + r.x());
            assertTrue(l.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance), "no projectile");
            float seconds = r.frames() * STEP;
            if (attempt == 0) first = seconds; else assertEquals(first, seconds, STEP, "a retry replays the same route");
        }
        return first;
    }

    @Test void theMasteredZeroAmmunitionRouteJumpsTheRootAndFitsTheBudget() {
        float s = routeSeconds(false, true);
        System.out.printf(java.util.Locale.ROOT, "forest-canopy jumping the root: %.2f s%n", s);
        assertTrue(s <= 20f, "D3 budget");
    }

    @Test void walkingThroughTheLitRootIsAlsoSurvived() {
        float s = routeSeconds(false, false);
        System.out.printf(java.util.Locale.ROOT, "forest-canopy walking through the root: %.2f s%n", s);
        assertTrue(s <= 20f);
    }

    @Test void theWholeRouteStillPassesWithTheLongerWarningsAssist() {
        float s = routeSeconds(true, true);
        System.out.printf(java.util.Locale.ROOT, "forest-canopy with the assist: %.2f s%n", s);
        assertTrue(s <= 20f);
    }

    @Test void theMasteredRouteArrivesAtTheVineWithSlackAndABlindWalkerThatIsAboutOneSecondLateIsKilled() {
        // Measured with the walker held at the entrance first (0.1 s steps): 0 to 0.8 s late passes, 0.9 to 1.3 s late dies.
        for (int delay : new int[]{0, 18, 48}) {
            Level l = level(); Plan p = new Plan(); p.delayFrames = delay;
            assertTrue(run(l, p, 60 * 40, Float.MAX_VALUE).exited(), "delay " + delay);
        }
        Level l = level(); Plan p = new Plan(); p.delayFrames = 66;
        assertTrue(run(l, p, 60 * 40, Float.MAX_VALUE).died(), "a blind walker 1.1 s late meets the lethal vine");
    }

    // ---- T1: the vine ----

    @Test void aHeroStandingUnderTheVineIsKilledWithinOnePeriodAndOneWalkingInAtTheWrongTimeToo() {
        Level l = level();
        place(l, VINE_X + 12 + 5);
        int frames = 0;
        while (!dead(l) && frames < 60 * 10) { held.clear(); frame(l); frames++; }
        assertTrue(dead(l), "standing in the swept column dies when the vine hangs low");
        assertTrue(frames < 60 * (ThornVine.DEFAULT_PERIOD_SECONDS + 0.1f));
    }

    @Test void aWalkerAtEveryStartOffsetEitherPassesTheVineOrDiesAndBothHappen() {
        int passed = 0, killed = 0;
        for (int offset = 0; offset < 60 * 4.5f; offset += 6) {
            Level l = level();
            place(l, 1830);
            // Pass time at the start with the hero standing clear, then walk straight through (no jump, no wait).
            for (int i = 0; i < offset; i++) { held.clear(); frame(l); }
            Plan plan = new Plan(); plan.jumpRoot = false;
            Run r = run(l, plan, 60 * 6, VINE_X + 100);
            if (r.died()) killed++; else passed++;
        }
        assertTrue(passed > 0 && killed > 0, "passed " + passed + ", killed " + killed);
        assertTrue(passed >= killed / 2, "a safe window of about 1.2 s per swing: passed " + passed + ", killed " + killed);
    }

    // ---- T2: the exploding root ----

    @Test void aJumperWithNoAmmunitionPassesTheRootWithoutLightingIt() {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float takeoff = ROOT_X - 56; takeoff <= ROOT_X - 8; takeoff += 2) {
                Level l = level();
                place(l, ROOT_X - 89);
                Plan plan = new Plan(); plan.rootJumpRight = takeoff;
                Run r = run(l, plan, 60 * 8, ROOT_X + 90);
                assertFalse(r.died(), "takeoff " + takeoff + " longer=" + longer);
                assertTrue(root(l).isIntact(), "the jump never touched the trigger box, takeoff " + takeoff);
            }
        }
    }

    // Measured (every pixel, both warnings): a running jump whose takeoff has the body edge 5 to 57 px before the root leaves it
    // unlit; earlier lands on it, later touches it, and neither kills a hero who keeps moving. The test uses 8 to 56 px.

    @Test void aWalkerThroughTheLitRootIsClearWhenItGoesOff() {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level l = level();
            place(l, ROOT_X - 100);
            Plan plan = new Plan(); plan.jumpRoot = false;
            Run r = run(l, plan, 60 * 8, ROOT_X + 140);
            assertFalse(r.died(), "longer=" + longer);
            for (int i = 0; i < 90; i++) frame(l);
            assertTrue(root(l).isSpent(), "it lit and went off behind the hero");
            assertFalse(dead(l));
        }
    }

    @Test void aHitFromAfarGivesTheFullWarningThenBlastsAndLeavesHarmlessRemains() {
        Level l = level();
        place(l, ROOT_X - 100);
        assertTrue(root(l).igniteFromAfar());
        int frames = 0;
        while (root(l).phase() == ExplosiveTrap.Phase.WINDUP && frames < 200) { frame(l); frames++; }
        assertEquals(TrapWarnings.DEFAULT_WARNING_SECONDS, frames * STEP, 2 * STEP, "the shared warning");
        assertFalse(dead(l), "the hero stood 100 px away, outside the 64 px blast square");
        for (int i = 0; i < 60; i++) frame(l);
        assertTrue(root(l).isSpent());
        Run r = run(l, new Plan(), 60 * 12, Float.MAX_VALUE);
        assertTrue(r.exited(), "the spent root never blocks the way: died=" + r.died());
    }

    // ---- T3: the stone ----

    @Test void aWalkerAlwaysOutrunsTheStoneAndAHeroWhoStandsInItsColumnDies() {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level l = level();
            place(l, STONE_X - 60);
            Plan plan = new Plan(); plan.jumpRoot = false;
            Run r = run(l, plan, 60 * 8, STONE_X + 80);
            assertFalse(r.died(), "longer=" + longer);
            Level k = level();
            place(k, STONE_X + 12);
            int frames = 0;
            while (!dead(k) && frames < 60 * 4) { held.clear(); frame(k); frames++; }
            assertTrue(dead(k), "standing under the shadow dies, longer=" + longer);
            assertEquals(ExpectedWarnings.standard(longer) + FallingTrap.flightSeconds(320), frames * STEP, 0.15f);
        }
    }

    @Test void theStoneWarnsForTheSharedWarningLength() {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level l = level();
            place(l, STONE_X + 40);
            int frames = 0;
            while (stone(l).state() == FallingTrap.State.ARMED && frames < 60) { held.clear(); held.add(Input.Keys.A); frame(l); frames++; }
            held.clear();
            int warn = 0;
            while (stone(l).state() == FallingTrap.State.WARNING && warn < 200) { frame(l); warn++; }
            assertEquals(ExpectedWarnings.standard(longer) / STEP, warn, 2, "updates of warning");
        }
    }
}
