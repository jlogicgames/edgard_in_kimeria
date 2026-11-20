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
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
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
 * The trap course of forest-1 (x 640-1280, one screen): a spore puffball, the second RedMob and a rotten log over a pit.
 * #260 (D58) placed them in one 1280 px room with a thorn tuft; #500 (D110) split that room at x 640, moved the escalators
 * and their lever into {@code forest-1-escalators}, removed the tuft and put the puffball first. Ordinary keyboard input drives the hero
 * through the real shipped map. Times are author
 * (mastered) measurements of a scripted hero, not novice playtests. Plan and numbers: docs/ROOMS.md.
 *
 * <p>Y grows downward: the floor top is y 320; a trap standing on it has y = 320 - height.
 */
class Forest1TrapsTest {
    private static final float STEP = 1f / 60f;
    /** Pinned measurements (D105, #500): scripted hero, 60 frames per second, within 0.1 s. */
    private static final float PIN_MARGIN = 0.1f, COURSE_S = 6.00f, RETRY_AT_LOG_S = 6.47f;
    // Authored geometry (docs/ROOMS.md, "forest-1 trap placement plan").
    static final float START_X = 656, MOB_X = 928, PUFF_X = 768, LIP = 1024, PIT_END = 1168, LOG_X = 1088, LOG_END = 1120;
    static final float CHECKPOINT_X = 1216; // x of the room exit (the old checkpoint spot, D67)

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

    private Level level() {
        Level l = new Level(assets, game, player(), "forest-1", Rectangle::new, emptyStock());
        l.enterNextRoom(player()); // the trap course is room 4: #375 put the first RedMob in a room of its own before it,
        l.enterNextRoom(player()); // #363 the deadfall room after that,
        l.enterNextRoom(player()); // and #500 (D110) the escalator room, split off the course, right before it
        rememberMob(l);
        theLog = l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).findFirst().orElseThrow();
        return l;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }

    private Puffball puff(Level l) { return l.objects.stream().filter(Puffball.class::isInstance).map(Puffball.class::cast).findFirst().orElseThrow(); }
    private FallingPlatform theLog;
    private FallingPlatform log(Level l) { return theLog; }
    /** The new RedMob (the one authored at x 928), remembered at load because a slain mob leaves the list. */
    private RedMob mob;
    private RedMob newMob(Level l) { return mob; }
    private void rememberMob(Level l) {
        mob = l.objects.stream().filter(RedMob.class::isInstance).map(RedMob.class::cast).filter(m -> m.getX() == MOB_X).findFirst().orElseThrow();
    }

    private float bodyRight(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width; }
    private float bodyLeft(Level l) { return l.player.hitboxBounds().x; }
    private boolean dead(Level l) { return l.touchesDeadline(l.player.hitboxBounds()) || l.player.isGotHit(); }
    /** Reached the end of the course: the room's exit, where the checkpoint stood before #187 appended the route room (D67). */
    private boolean won(Level l) { return l.atRoomExit(); }
    private void settle(Level l, int frames) { for (int i = 0; i < frames; i++) frame(l); }
    /** Body right edge at {@code right}, standing on the floor, hero facing right. */
    private void place(Level l, float right) { l.player.position.set(right - 29, 272); settle(l, 3); }
    private void retry(Level l) { l.resetCurrentRoom(player()); settle(l, 2); }

    /** What the scripted hero does about the traps; the early lever, block and wall part is the same for all. */
    private static final class Plan {
        boolean killMob = true, waitForPuff = true, crossLog = true;
        float logStand = 0;
        int dawdleOnLog = 0, waitedFrames = 0;
        float stopAtRight = Float.MAX_VALUE;
    }

    /** Result of a run: frames used, and why it stopped. */
    private record Run(int frames, boolean won, boolean died, float x) {}

    private boolean mobAhead(Level l) {
        for (var o : l.objects) if (o instanceof Enemy<?> e) {
            Rectangle en = e.hitboxBounds(), me = l.player.hitboxBounds();
            if (l.player.isOnGround() && en.x >= me.x && en.x - (me.x + me.width) < 18
                && en.y < me.y + me.height && en.y + en.height > me.y) return true;
        }
        return false;
    }

    /** Safe to start across the cloud: calm now and enough calm left to clear the 31 px column at 100 px/s. */
    private boolean puffClear(Level l) {
        Puffball p = puff(l);
        return p.currentPhase() == Puffball.Phase.CALM && p.cycleTime() + 0.6f < p.period() - p.cloudSeconds() - p.swellSeconds();
    }

    private Run run(Level l, Plan plan, int maxFrames) {
        Player p = l.player;
        int lastJump = -2, onLog = 0;
        boolean actionWasDown = false, wasMechanism = false;
        for (int f = 0; f < maxFrames; f++) {
            held.clear(); pressed.clear();
            float r = bodyRight(l);
            boolean right = true, doJump = false;
            // Wait in front of the cloud column (x 764-788; body right <= 762) until the calm is long enough.
            if (plan.waitForPuff && r > 752 && r <= 762 && !puffClear(l)) { right = false; plan.waitedFrames++; }
            if (plan.crossLog) {
                if (r >= LIP - 2 && r < LIP + 8 && p.isOnGround() && f - lastJump > 1) doJump = true;
                if (r >= LOG_END - 2 && r < LOG_END + 18 && p.isOnGround() && f - lastJump > 1) {
                    if (onLog++ >= plan.dawdleOnLog) doJump = true; else right = false;
                }
            }
            if (r >= plan.stopAtRight) right = false;
            if (right) held.add(Input.Keys.D);
            if (doJump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); lastJump = f; }
            boolean mechanism = p.canInteract();
            boolean action = mechanism || (plan.killMob && mobAhead(l));
            if (plan.killMob && mobAhead(l)) mechanism = false;
            if (mechanism) held.add(Input.Keys.W);
            if (action && (!actionWasDown || wasMechanism != mechanism)) pressed.add(Input.Keys.K);
            wasMechanism = mechanism; actionWasDown = action;
            frame(l);
            if (dead(l)) return new Run(f + 1, false, true, p.position.x);
            if (won(l)) return new Run(f + 1, true, false, p.position.x);
        }
        return new Run(maxFrames, false, false, p.position.x);
    }

    // ---- geometry and census ----

    @Test void theCourseHoldsExactlyThePuffballTheSecondRedMobAndTheLogAtTheirAuthoredPlaces() {
        Level l = level();
        assertEquals("forest-1-traps", l.currentRoomName());
        assertEquals(new Rectangle(640, 0, 640, 368), l.currentRoomBounds(), "exactly one 640 px screen: the whole course is in view at once (D84)");
        assertEquals(0, l.objects.stream().filter(ThornTrap.class::isInstance).count(), "the thorn tuft left with the split (#500, D110)");
        assertEquals(1, l.objects.stream().filter(Puffball.class::isInstance).count(), "one puffball");
        assertEquals(320, puff(l).getY() + puff(l).getHeight(), .01f);
        assertEquals(PUFF_X, puff(l).getX(), .01f);
        assertEquals(1, l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).count(), "one log");
        assertEquals(0, l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.PLATFORM).count(), "the four platforms are in the escalator room now");
        assertEquals(0, l.escalators.size(), "the escalators and their lever are in the escalator room");
        assertEquals(LOG_X, log(l).getX(), .01f);
        assertEquals(320, log(l).getY(), .01f, "flush with the floor");
        assertEquals(1, l.objects.stream().filter(RedMob.class::isInstance).count(), "only the trap-course RedMob: the first one is in forest-1-mob (#375)");
        assertEquals(0, l.objects.stream().filter(o -> o.getClass().getSimpleName().matches("FallingBranch|FallingStone|ExplosiveTrap|ThornVine")).count());
        // The pit has no floor between the lips.
        Rectangle pit = new Rectangle(LIP + 1, 320, PIT_END - LIP - 2, 48);
        for (var b : l.collisionBlocks) if (!b.isEscalator())
            assertFalse(pit.overlaps(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())), "a floor in the pit");
    }

    /** The escalator room after the split (#500, D110): its four falling platforms, two escalators, the wall and the lever that opens it, and nothing lethal. */
    @Test void theEscalatorRoomHoldsItsFourPlatformsTwoEscalatorsWallAndLeverAndNothingElse() {
        Level l = new Level(assets, game, player(), "forest-1", Rectangle::new, emptyStock());
        l.enterNextRoom(player());
        l.enterNextRoom(player());
        assertEquals("forest-1-escalators", l.currentRoomName());
        assertEquals(new Rectangle(0, 0, 640, 368), l.currentRoomBounds());
        assertEquals(4, l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.PLATFORM).count(), "the four falling platforms");
        assertEquals(4, l.fallingPlatforms.size(), "and no log");
        assertEquals(2, l.escalators.size(), "two escalators: lever 33 and its overhead escalator 34 are gone");
        assertEquals(1, l.escalators.stream().filter(e -> e.isVertical).count());
        assertEquals(1, l.escalators.stream().filter(e -> !e.isVertical).count());
        assertEquals(1, l.triggers.size(), "one lever");
        assertEquals(1, l.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Actionable.class::isInstance).count(), "and the wall it opens");
        assertEquals(0, l.objects.stream().filter(o -> o instanceof Puffball || o instanceof RedMob || o instanceof ThornTrap).count(), "nothing that kills");
        assertEquals(32, l.player.position.x, .01f);
        assertEquals(272, l.player.position.y, .01f);
    }

    /** The course's own walls (#500): the seam walls at x 624 and 640 and the floor from x 640 stand where the card and the drawn seam say. */
    @Test void theSeamWallsAndTheFloorStandWhereTheCardSaysAndTheEntranceIsSafe() {
        Level l = level();
        Rectangle left = null;
        for (var b : l.collisionBlocks) if (b.getX() == 640 && b.getWidth() == 16 && b.getY() == 0) left = new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight());
        assertNotNull(left, "the course's left wall (640, 0, 16 x 320)");
        assertEquals(320, left.height, .01f);
        assertEquals(START_X, l.player.position.x, .01f);
        assertEquals(272, l.player.position.y, .01f);
        assertFalse(l.player.canInteract());
        Puffball p = puff(l);
        float cloudLeft = p.getX() + p.getWidth() / 2f - 12;
        assertTrue(cloudLeft - (START_X + 48) >= 48, "the cloud is clear of the entrance's 48 px square: " + (cloudLeft - (START_X + 48)));
        assertTrue(cloudLeft - (START_X + 29) >= 60, "from the body's right edge the cloud is about 80 px away");
    }

    @Test void nothingSharesAnEnvelopeOrATelegraphWithAnythingElse() {
        Level l = level();
        Rectangle mobWorst = mobReach(l);
        Rectangle cloud = new Rectangle(puff(l).getX() + puff(l).getWidth() / 2f - 12, 288, 24, 32);
        Rectangle pit = new Rectangle(LIP, 300, PIT_END - LIP, 80);
        Rectangle checkpoint = new Rectangle(CHECKPOINT_X, 272, 32, 48); // the RoomExit
        Rectangle[] areas = {cloud, mobWorst, pit};
        for (int i = 0; i < areas.length; i++) for (int j = i + 1; j < areas.length; j++)
            assertFalse(areas[i].overlaps(areas[j]), "areas " + i + " and " + j + " overlap: " + areas[i] + " " + areas[j]);
        assertTrue(mobWorst.x - (cloud.x + cloud.width) >= 24, "24 px between the cloud and the mob's reach: " + (mobWorst.x - (cloud.x + cloud.width)));
        assertTrue(pit.x - (mobWorst.x + mobWorst.width) >= 48, "a run-up between the mob's reach and the lip: " + (pit.x - (mobWorst.x + mobWorst.width)));
        assertTrue(checkpoint.x - (pit.x + pit.width) >= 48, "D44-D47: the room exit is 48 px clear of the pit");
        // The entrance is far away from everything (the loader also enforces this).
        assertTrue(cloud.x - (START_X + 48) >= 48);
        assertFalse(new Rectangle(CHECKPOINT_X - 48, 272, 112, 48).overlaps(cloud));
    }

    /** The mob's worst-case reach: where its body and the whip can be while it chases inside its patrol range. */
    private Rectangle mobReach(Level l) {
        // offNeg 2 / offPos 2 tiles around x 928: the hero's centre must be in x 896-960; the body centre follows it,
        // the body is 20 px wide and the whip reaches 6 px past the body.
        float left = 896 - 10 - 6, right = 960 + 10 + 6;
        return new Rectangle(left, 288, right - left, 32);
    }

    // ---- the mastered route ----

    @Test void theMasteredRouteReachesTheRoomExitWithZeroAmmunitionInUnderTwentySeconds() {
        Level l = level();
        assertEquals(0, l.rangedStock.count());
        Plan plan = new Plan();
        Run r = run(l, plan, 60 * 30);
        assertTrue(r.won, "reached the room exit: " + r);
        float seconds = r.frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-1 mastered route (zero ammunition): %.2f s, of which %.2f s waiting for the puffball%n", seconds, plan.waitedFrames * STEP);
        assertTrue(seconds <= 20f, "the 20 s budget: " + seconds);
        assertEquals(COURSE_S, seconds, PIN_MARGIN, "the pinned mastered route (D105)");
        assertEquals(0, plan.waitedFrames, "the mastered walker never waits for the puffball: it is sunk before he arrives");
        assertTrue(l.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance), "no projectile");
        assertEquals(0, l.rangedStock.count());
        assertTrue(newMob(l).isDefeated(), "the sword took the second RedMob");
    }

    @Test void aRetryRebuildsEveryTrapAndTheSameRoutePassesWithTheSameTime() {
        Level l = level();
        float first = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                retry(l); rememberMob(l); rememberLog(l);
                assertFalse(log(l).isFalling());
                assertFalse(newMob(l).isDefeated(), "the mob is back");
                assertEquals(puff(level()).cycleTime(), puff(l).cycleTime(), 2 * STEP, "the rhythm restarts at its authored phase");
                assertEquals(START_X, l.player.position.x, 8f, "back at the spawn");
            }
            Run r = run(l, new Plan(), 60 * 30);
            assertTrue(r.won, "attempt " + attempt + ": " + r);
            if (attempt == 0) first = r.frames * STEP;
            else assertEquals(first, r.frames * STEP, 3 * STEP, "a retry reproduces the route time");
        }
    }

    @Test void aRetryAfterADeathAtTheLogCostsTheWholeRouteAndIsMeasured() {
        Level l = level();
        Plan plan = new Plan(); plan.dawdleOnLog = 400; // stands on the log until it falls
        Run died = run(l, plan, 60 * 40);
        assertTrue(died.died, "standing on the log is fatal: " + died);
        float costToLog = died.frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-1 retry after dying at the log: %.2f s to get back there%n", costToLog);
        assertTrue(costToLog <= 20f, "a death at the log replays at most the whole 20 s budget: " + costToLog);
        assertEquals(RETRY_AT_LOG_S, costToLog, PIN_MARGIN, "the card's retry cost for a death at the log (was 12.73 s before the split, #500)");
        retry(l); rememberMob(l); rememberLog(l);
        assertTrue(run(l, new Plan(), 60 * 30).won);
    }

    // ---- each trap by itself ----

    @Test void theSecondRedMobCanBeSteppedOnOrSlainAndNeverNeedsAmmunition() {
        // Sword: the default plan. Stomp: jump on its head.
        Level l = level();
        place(l, 906);
        RedMob mob = newMob(l);
        assertFalse(mob.isDefeated());
        Plan sword = new Plan(); sword.crossLog = false;
        // Start next to the mob (past the cloud), sword it.
        for (int i = 0; i < 60 * 6 && !mob.isDefeated() && !dead(l); i++) {
            held.clear(); pressed.clear();
            if (mobAhead(l)) pressed.add(Input.Keys.K); else held.add(Input.Keys.D);
            frame(l);
        }
        assertTrue(mob.isDefeated() && !dead(l), "the sword takes the mob without a hit");
        assertEquals(0, l.rangedStock.count());
        // Stomp: some takeoff distance in front of the mob always works (swept), and none needs ammunition.
        int ok = 0;
        for (int gap = 8; gap <= 90; gap += 2) {
            Level s = level();
            place(s, 806);
            RedMob m2 = newMob(s);
            for (int i = 0; i < 60 * 4 && !m2.isDefeated() && !dead(s); i++) {
                held.clear(); pressed.clear();
                held.add(Input.Keys.D);
                float g = m2.hitboxBounds().x - bodyRight(s);
                if (s.player.isOnGround() && g <= gap && g > gap - 4) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(s);
            }
            if (m2.isDefeated() && !dead(s)) ok++;
        }
        System.out.println("forest-1 second RedMob: stomp works from " + ok + " of 42 takeoff gaps");
        assertTrue(ok >= 3, "a stomp takes the mob from several takeoff distances: " + ok);
    }

    @Test void theMobStaysInsideItsPatrolAndOutsideTheCloudColumnAndShortOfThePit() {
        Level l = level();
        place(l, 906); // hero inside the mob's range on its left edge, mob never killed, hero stands still
        float minX = Float.MAX_VALUE, maxX = -1;
        for (int i = 0; i < 60 * 10 && !dead(l); i++) {
            held.clear();
            if (i % 120 < 60) held.add(Input.Keys.A); else held.add(Input.Keys.D);
            frame(l);
            Rectangle b = newMob(l).hitboxBounds();
            minX = Math.min(minX, b.x); maxX = Math.max(maxX, b.x + b.width);
            Rectangle reach = mobReach(l);
            assertTrue(b.x >= reach.x && b.x + b.width <= reach.x + reach.width, "mob body " + b + " inside the reach " + reach);
        }
        assertTrue(minX > puff(l).getX() + puff(l).getWidth() / 2f + 12, "the mob never reaches the cloud column: " + minX);
        assertTrue(maxX < LIP, "the mob never reaches the pit's lip: " + maxX);
    }

    @Test void thePuffballCloudKillsAHeroTimedIntoItAndALateWalkerFindsTheTellSwelling() {
        Level l = level();
        Puffball p = puff(l);
        // Wait for the cloud then step in.
        place(l, PUFF_X - 14);
        int guard = 0;
        while (p.currentPhase() != Puffball.Phase.CLOUD && guard++ < 60 * 10) frame(l);
        assertEquals(Puffball.Phase.CLOUD, p.currentPhase());
        held.add(Input.Keys.D);
        for (int i = 0; i < 40 && !dead(l); i++) frame(l);
        assertTrue(dead(l), "a hero in the cloud is killed");
        // The swell comes before the cloud, for the shared warning.
        Level m = level();
        Puffball q = puff(m);
        int swell = 0; boolean sawSwell = false;
        for (int i = 0; i < 60 * 10 && q.currentPhase() == Puffball.Phase.CLOUD; i++) frame(m); // the cloud is out at the entry (phase 3.4): let it sink
        for (int i = 0; i < 60 * 10; i++) {
            frame(m);
            if (q.currentPhase() == Puffball.Phase.SWELL) { swell++; sawSwell = true; }
            if (q.currentPhase() == Puffball.Phase.CLOUD) break;
        }
        assertTrue(sawSwell);
        assertEquals(TrapWarnings.DEFAULT_WARNING_SECONDS, swell * STEP, 3 * STEP, "swell before the cloud");
    }

    /**
     * The puffball's first sight (#500, D110; D84: no gotcha): its clock starts at the room's entry at phase 3.4, so the cloud is out at the
     * entry, sinks at 0.6 s, the mastered walker crosses its column in the calm (0.8 to 1.2 s), and a hero who dawdles meets the swell at 2.8 s
     * (the tell) before the next cloud at 3.6 s.
     */
    @Test void thePuffballIsOutAtTheEntrySinksBeforeTheWalkerArrivesAndSwellsBeforeItsNextCloud() {
        Level idle = level();
        Puffball p = puff(idle);
        assertEquals(Puffball.Phase.CLOUD, p.currentPhase(), "the cloud is out at the entry: the player sees the trap act");
        float sank = -1, swelled = -1, clouded = -1;
        for (int i = 1; i <= 60 * 5; i++) {
            frame(idle);
            float t = i * STEP;
            if (sank < 0 && p.currentPhase() != Puffball.Phase.CLOUD) sank = t;
            if (sank >= 0 && swelled < 0 && p.currentPhase() == Puffball.Phase.SWELL) swelled = t;
            if (swelled >= 0 && clouded < 0 && p.currentPhase() == Puffball.Phase.CLOUD) clouded = t;
        }
        assertEquals(0.6f, sank, 3 * STEP, "the cloud sinks at 0.6 s");
        assertEquals(2.8f, swelled, 3 * STEP, "the tell comes at 2.8 s");
        assertEquals(3.6f, clouded, 3 * STEP, "the next cloud at 3.6 s");
        Level walk = level();
        Puffball q = puff(walk);
        float cloudLeft = q.getX() + q.getWidth() / 2f - 12, cloudRight = cloudLeft + 24;
        float first = -1, last = -1;
        for (int i = 1; i <= 60 * 3 && !won(walk); i++) {
            held.clear(); held.add(Input.Keys.D);
            frame(walk);
            assertFalse(walk.player.isGotHit(), "the mastered walker is never hit by the cloud at all: frame " + i);
            assertFalse(dead(walk), "and never dies on the way across: frame " + i);
            if (bodyRight(walk) > cloudLeft && bodyLeft(walk) < cloudRight) {
                if (first < 0) first = i * STEP;
                last = i * STEP;
                assertEquals(Puffball.Phase.CALM, q.currentPhase(), "he crosses the column in the calm");
            }
            if (bodyLeft(walk) > cloudRight + 4) break;
        }
        assertTrue(first > sank, "he reaches the column after the cloud has sunk: " + first + " vs " + sank);
        assertTrue(last < swelled, "and is across before the swell: " + last + " vs " + swelled);
        System.out.printf(java.util.Locale.ROOT, "forest-1 puffball: sinks %.2f s, walker in the column %.2f to %.2f s, swell %.2f s, next cloud %.2f s%n", sank, first, last, swelled, clouded);
    }

    @Test void theLogHoldsForTheSharedWarningThenFallsAndALingererFallsIntoThePitWhichIsARetry() {
        for (boolean assist : new boolean[]{false, true}) {
            warnings = new TrapWarnings();
            if (assist) setLonger(true);
            Level l = level();
            place(l, LIP - 40);
            // Jump on the log from the lip.
            int f = 0;
            boolean onLog = false;
            while (!log(l).isFalling() && f < 600) {
                held.clear(); pressed.clear();
                if (bodyRight(l) < LIP - 2) held.add(Input.Keys.D);
                else if (!onLog && l.player.isOnGround() && bodyRight(l) >= LIP - 2) { held.add(Input.Keys.D); held.add(Input.Keys.J); pressed.add(Input.Keys.J); onLog = true; }
                else held.add(Input.Keys.D);
                frame(l); f++;
                if (dead(l)) break;
            }
            assertTrue(log(l).isFalling(), "landing lights the log (assist " + assist + ")");
            int fall = 0;
            held.clear();
            while (!dead(l) && fall < 60 * 6) { frame(l); fall++; }
            assertTrue(dead(l), "a hero who stands on the log is dropped into the pit, which is the retry");
            retry(l); rememberLog(l);
            assertFalse(log(l).isFalling());
            assertTrue(alive(l));
        }
    }

    private void rememberLog(Level l) {
        theLog = l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).findFirst().orElseThrow();
    }

    private boolean alive(Level l) { return !dead(l); }

    @Test void aLogThatHasFallenLeavesTheRetryNotAStuckHero() {
        Level l = level();
        place(l, LIP - 20);
        log(l).collideWithActor(); // landed once and stepped back
        for (int i = 0; i < 60 * 4; i++) frame(l);
        assertTrue(log(l).isRemoved() || log(l).isFalling());
        // From the near lip the pit cannot be jumped (wider than any jump) so the hero falls: a retry restores the log.
        held.clear(); held.add(Input.Keys.D);
        for (int i = 0; i < 60 * 3 && !dead(l); i++) {
            if (bodyRight(l) >= LIP - 2 && l.player.isOnGround()) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(l);
        }
        assertTrue(dead(l), "the pit is the retry");
        retry(l); rememberLog(l);
        assertFalse(log(l).isRemoved());
        assertFalse(log(l).isFalling());
    }

    @Test void theWholeRouteStillPassesWithTheLongerWarningsAssist() {
        warnings = new TrapWarnings();
        setLonger(true);
        Level l = level();
        Run r = run(l, new Plan(), 60 * 30);
        assertTrue(r.won, "assist route: " + r);
        System.out.printf(java.util.Locale.ROOT, "forest-1 mastered route with the assist: %.2f s%n", r.frames * STEP);
        assertTrue(r.frames * STEP <= 20f);
    }
}
