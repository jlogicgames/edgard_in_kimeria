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
 * The traps and the second RedMob placed in forest-1 by #260 (D58): a thorn tuft, a RedMob, a spore puffball and a
 * rotten log over a pit, in the 620 px between the wall and the checkpoint. Ordinary keyboard input drives the hero
 * through the real shipped map (read under an alias that drops only the decorative sky). Times are author
 * (mastered) measurements of a scripted hero, not novice playtests. Plan and numbers: docs/ROOMS.md.
 *
 * <p>Y grows downward: the floor top is y 320; a trap standing on it has y = 320 - height.
 */
class Forest1TrapsTest {
    private static final float STEP = 1f / 60f;
    // Authored geometry (docs/ROOMS.md, "forest-1 trap placement plan").
    static final float TUFT_X = 696, MOB_X = 864, PUFF_X = 944, LIP = 1024, PIT_END = 1168, LOG_X = 1088, LOG_END = 1120;
    static final float CHECKPOINT_X = 1216;

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
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class}, (proxy, method, args) -> {
            // The exact shipped map under an alias, omitting only forest-1's decorative sky/rain (as RoomReplayTest).
            if (method.getName().equals("internal") && args[0].equals("tiles/replay-forest-1.tmx"))
                return files.internal("tiles/forest-1.tmx");
            return method.invoke(files, args);
        });
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
        Level l = new Level(assets, game, player(), "replay-forest-1", Rectangle::new, emptyStock());
        rememberMob(l);
        theLog = l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).findFirst().orElseThrow();
        return l;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }

    private ThornTrap tuft(Level l) { return l.objects.stream().filter(ThornTrap.class::isInstance).map(ThornTrap.class::cast).findFirst().orElseThrow(); }
    private Puffball puff(Level l) { return l.objects.stream().filter(Puffball.class::isInstance).map(Puffball.class::cast).findFirst().orElseThrow(); }
    private FallingPlatform theLog;
    private FallingPlatform log(Level l) { return theLog; }
    /** The new RedMob (the one authored at x 864), remembered at load because a slain mob leaves the list. */
    private RedMob mob;
    private RedMob newMob(Level l) { return mob; }
    private void rememberMob(Level l) {
        mob = l.objects.stream().filter(RedMob.class::isInstance).map(RedMob.class::cast).filter(m -> m.getX() == MOB_X).findFirst().orElseThrow();
    }

    private float bodyRight(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width; }
    private float bodyLeft(Level l) { return l.player.hitboxBounds().x; }
    private boolean dead(Level l) { return l.touchesDeadline(l.player.hitboxBounds()) || l.player.isGotHit(); }
    private boolean won(Level l) { return l.player.getState() == Player.State.DISAPPEARING; }
    private void settle(Level l, int frames) { for (int i = 0; i < frames; i++) frame(l); }
    /** Body right edge at {@code right}, standing on the floor, hero facing right. */
    private void place(Level l, float right) { l.player.position.set(right - 29, 272); settle(l, 3); }
    private void retry(Level l) { l.resetCurrentRoom(player()); settle(l, 2); }

    /** What the scripted hero does about the traps; the early lever, block and wall part is the same for all. */
    private static final class Plan {
        boolean jumpTuft = true, killMob = true, waitForPuff = true, crossLog = true;
        float tuftJumpRight = TUFT_X - 30, logStand = 0;
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
        int jump = 0, lastJump = -2, onLog = 0;
        float[] early = {125, 260, 540}; // position.x takeoffs for the unchanged part (blocks, wall, lever 544)
        boolean actionWasDown = false, wasMechanism = false;
        for (int f = 0; f < maxFrames; f++) {
            held.clear(); pressed.clear();
            float r = bodyRight(l);
            boolean right = true, doJump = false;
            if (jump < early.length && p.position.x >= early[jump] && p.isOnGround() && f - lastJump > 1) { doJump = true; jump++; }
            if (plan.jumpTuft && r >= plan.tuftJumpRight && r < plan.tuftJumpRight + 6 && p.isOnGround() && f - lastJump > 1) doJump = true;
            // Wait in front of the cloud column (body right <= 928) until the calm is long enough.
            if (plan.waitForPuff && r > 920 && r <= 930 && !puffClear(l)) { right = false; plan.waitedFrames++; }
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

    @Test void theStretchHoldsExactlyTheThreeTrapsAndTheSecondRedMobAtTheirAuthoredPlaces() {
        Level l = level();
        assertEquals(1, l.objects.stream().filter(ThornTrap.class::isInstance).count(), "one tuft");
        ThornTrap t = tuft(l);
        assertEquals(ThornTrap.Kind.TUFT, t.kind());
        assertEquals(TUFT_X, t.getX(), .01f);
        assertEquals(320, t.getY() + t.getHeight(), .01f, "stands on the floor top (y grows downward)");
        assertEquals(1, l.objects.stream().filter(Puffball.class::isInstance).count(), "one puffball");
        assertEquals(320, puff(l).getY() + puff(l).getHeight(), .01f);
        assertEquals(PUFF_X, puff(l).getX(), .01f);
        assertEquals(1, l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).count(), "one log");
        assertEquals(4, l.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.PLATFORM).count(), "the four platforms are unchanged");
        assertEquals(LOG_X, log(l).getX(), .01f);
        assertEquals(320, log(l).getY(), .01f, "flush with the floor");
        assertEquals(2, l.objects.stream().filter(RedMob.class::isInstance).count(), "the first RedMob and the new one");
        assertEquals(0, l.objects.stream().filter(o -> o.getClass().getSimpleName().matches("FallingBranch|FallingStone|ExplosiveTrap|ThornVine")).count());
        // The pit has no floor between the lips.
        Rectangle pit = new Rectangle(LIP + 1, 320, PIT_END - LIP - 2, 48);
        for (var b : l.collisionBlocks) if (!b.isEscalator())
            assertFalse(pit.overlaps(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())), "a floor in the pit");
    }

    @Test void nothingSharesAnEnvelopeOrATelegraphWithAnythingElse() {
        Level l = level();
        Rectangle zone = tuft(l).triggerZone();
        Rectangle mobWorst = mobReach(l);
        Rectangle cloud = new Rectangle(puff(l).getX() + puff(l).getWidth() / 2f - 12, 288, 24, 32);
        Rectangle pit = new Rectangle(LIP, 300, PIT_END - LIP, 80);
        Rectangle checkpoint = new Rectangle(CHECKPOINT_X, 272, 16, 32);
        Rectangle[] areas = {zone, mobWorst, cloud, pit};
        for (int i = 0; i < areas.length; i++) for (int j = i + 1; j < areas.length; j++)
            assertFalse(areas[i].overlaps(areas[j]), "areas " + i + " and " + j + " overlap: " + areas[i] + " " + areas[j]);
        assertTrue(mobWorst.x - (zone.x + zone.width) >= 24, "the mob's reach starts 24 px past the tuft zone: " + (mobWorst.x - (zone.x + zone.width)));
        assertTrue(cloud.x - (mobWorst.x + mobWorst.width) >= 24, "24 px between the mob's reach and the cloud");
        assertTrue(pit.x - (cloud.x + cloud.width) >= 48, "a run-up between the cloud and the lip");
        assertTrue(checkpoint.x - (pit.x + pit.width) >= 48, "D44-D47: the checkpoint is 48 px clear of the pit");
        assertTrue(zone.x - 608 >= 8, "the tuft zone starts past the last escalator's travel (x 608)");
        // The entrance is far away from everything (the loader also enforces this).
        assertTrue(zone.x - 80 > 32 + 48);
        assertFalse(new Rectangle(CHECKPOINT_X - 48, 272, 112, 48).overlaps(cloud));
    }

    /** The mob's worst-case reach: where its body and the whip can be while it chases inside its patrol range. */
    private Rectangle mobReach(Level l) {
        // offNeg 2 / offPos 2 tiles around x 864: the hero's centre must be in x 832-896; the body centre follows it,
        // the body is 20 px wide and the whip reaches 6 px past the body.
        float left = 832 - 10 - 6, right = 896 + 10 + 6;
        return new Rectangle(left, 288, right - left, 32);
    }

    // ---- the mastered route ----

    @Test void theMasteredRouteReachesTheCheckpointWithZeroAmmunitionInUnderTwentySeconds() {
        Level l = level();
        assertEquals(0, l.rangedStock.count());
        Plan plan = new Plan();
        Run r = run(l, plan, 60 * 30);
        assertTrue(r.won, "reached the checkpoint: " + r);
        float seconds = r.frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-1 mastered route (zero ammunition): %.2f s, of which %.2f s waiting for the puffball%n", seconds, plan.waitedFrames * STEP);
        assertTrue(seconds <= 20f, "the 20 s budget: " + seconds);
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
                assertEquals(ThornTrap.Phase.DORMANT, tuft(l).phase(), "the tuft is hidden again");
                assertFalse(log(l).isFalling());
                assertFalse(newMob(l).isDefeated(), "the mob is back");
                assertEquals(puff(level()).cycleTime(), puff(l).cycleTime(), 2 * STEP, "the rhythm restarts at its authored phase");
                assertEquals(32, l.player.position.x, 8f, "back at the spawn");
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
        retry(l); rememberMob(l); rememberLog(l);
        assertTrue(run(l, new Plan(), 60 * 30).won);
    }

    // ---- each trap by itself ----

    @Test void aWalkerWhoKeepsGoingIsKilledByTheBristlingTuftAndAJumperIsNot() {
        Level l = level();
        Plan walk = new Plan(); walk.jumpTuft = false; walk.crossLog = false; walk.killMob = false;
        Run r = run(l, walk, 60 * 30);
        assertTrue(r.died && l.player.isGotHit(), "the walker is hit by the tuft: " + r);
        assertTrue(tuft(l).isLethal(), "it was the risen tuft");
        assertTrue(bodyRight(l) < 740, "the death is at the tuft, not later: " + bodyRight(l));
        retry(l);
        Plan jump = new Plan(); jump.crossLog = false; jump.stopAtRight = 800;
        Run j = run(l, jump, 60 * 30);
        assertFalse(j.died, "the jumper clears the tuft: " + j);
        assertTrue(tuft(l).isLethal(), "the tuft stays up behind the hero");
        assertTrue(bodyRight(l) >= 790);
    }

    @Test void theTuftBristlesForTheSharedWarningBeforeItIsLethal() {
        for (boolean assist : new boolean[]{false, true}) {
            warnings = new TrapWarnings();
            if (assist) setLonger(true);
            Level l = level();
            place(l, tuft(l).triggerZone().x + 8);
            int frames = 0;
            while (!tuft(l).isLethal() && frames < 200) { frame(l); frames++; }
            float expected = assist ? 1.2f : 0.8f;
            assertEquals(expected, frames * STEP, 4 * STEP, "bristle warning, assist " + assist);
        }
    }

    @Test void theSecondRedMobCanBeSteppedOnOrSlainAndNeverNeedsAmmunition() {
        // Sword: the default plan. Stomp: jump on its head.
        Level l = level();
        place(l, 842);
        RedMob mob = newMob(l);
        assertFalse(mob.isDefeated());
        Plan sword = new Plan(); sword.jumpTuft = false; sword.crossLog = false;
        // Start next to the mob (past the tuft), sword it.
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
            place(s, 790);
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

    @Test void theMobStaysInsideItsPatrolAndOutsideTheTuftZoneAndTheCloudColumn() {
        Level l = level();
        place(l, 842); // hero inside the mob's range on its left edge, mob never killed, hero stands still
        Rectangle zone = tuft(l).triggerZone();
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
        assertTrue(minX > zone.x + zone.width, "the mob never enters the tuft zone: " + minX);
        assertTrue(maxX < puff(l).getX() - 12, "the mob never reaches the cloud column: " + maxX);
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
        for (int i = 0; i < 60 * 10; i++) {
            frame(m);
            if (q.currentPhase() == Puffball.Phase.SWELL) { swell++; sawSwell = true; }
            if (q.currentPhase() == Puffball.Phase.CLOUD) break;
        }
        assertTrue(sawSwell);
        assertEquals(0.8f, swell * STEP, 3 * STEP, "swell before the cloud");
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
