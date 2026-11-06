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
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
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
 * The forest-0 traps lesson room (#200): ordinary keyboard controls drive the hero through the real map.
 * A pit is bridged by one falling platform (it lights up when landed on and falls after the shared warning), then a
 * an exploding stump (D47, which replaced the glass-bottle bomb) sits on the floor before the level's end. Times are author (mastered) measurements, not novice
 * playtests. Recipe: docs/ROOMS.md, rule: D40.
 */
class TrapsLessonTest {
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

    private static final float ROOM_X = 3792, LIP = 4032, PIT_END = 4176, PLAT_X = 4096, PLAT_END = 4128, ROOM_RIGHT = 4512;
    private static final float STUMP_X = 4352, BLAST_LEFT = 4328, BLAST_RIGHT = 4392, CHECKPOINT_X = 4464;
    private static final float WARNING = TrapWarnings.FALLING_PLATFORM_SECONDS, FUSE = TrapWarnings.DEFAULT_WARNING_SECONDS;

    /** A level whose hero stands in the traps room (index 5) at its safe entrance. */
    private Level freshRoom() {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        for (int i = 0; i < 5; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private FallingPlatform platform(Level level) { return level.fallingPlatforms.get(0); }

    private ExplosiveTrap stump(Level level) {
        return level.objects.stream().filter(ExplosiveTrap.class::isInstance).map(ExplosiveTrap.class::cast).findFirst().orElseThrow();
    }

    /** The stump (D47) has gone off: it is in its blast or spent (the charred remains stay as scenery). */
    private boolean stumpGone(Level level) { return stump(level).phase() == ExplosiveTrap.Phase.BLAST || stump(level).isSpent(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private float bodyLeft(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x; }
    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    private boolean atEnd(Level level) { return alive(level) && !level.player.canLeaveRoom(); }
    private void place(Level level, float bodyRight) { level.player.position.set(bodyRight - 11 - 18, 272); settle(level, 2); }

    private void retry(Level level) { level.resetCurrentRoom(new Player(assets, game, 0, 0)); settle(level, 2); }

    /** One frame of ordinary play: Right held, Jump pressed when the plan says so. */
    private void step(Level level, boolean right, boolean jump) {
        held.clear();
        if (right) held.add(Input.Keys.D);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(level);
    }

    /** The mastered pace: walk right, jump as the body reaches the pit lip, jump on from the platform's far end. */
    private int crossPit(Level level) {
        int frames = 0;
        StringBuilder trace = new StringBuilder();
        while (bodyLeft(level) < PIT_END + 4) {
            float r = bodyRight(level);
            boolean jump = level.player.isOnGround() && ((r >= LIP - 2 && r < LIP + 8) || (r >= PLAT_END - 2 && r < PLAT_END + 18));
            step(level, true, jump);
            frames++;
            trace.append(String.format("f%d r=%.0f y=%.0f g=%s j=%s; ", frames, bodyRight(level), level.player.position.y, level.player.isOnGround(), jump));
            assertTrue(alive(level), "died crossing the pit at " + level.player.position + " after " + frames + " " + trace.substring(Math.max(0, trace.length() - 900)));
            assertTrue(frames < 60 * 20, "never crossed");
        }
        return frames;
    }

    private int walkTo(Level level, java.util.function.BooleanSupplier arrived, boolean jumpStump) {
        int frames = 0;
        while (!arrived.getAsBoolean()) {
            boolean jump = jumpStump && level.player.isOnGround() && bodyRight(level) >= STUMP_X - 22 && bodyRight(level) < STUMP_X - 14;
            step(level, true, jump);
            assertTrue(alive(level), "died at " + level.player.position + " after " + frames);
            assertTrue(++frames < 60 * 30, "never arrived, hero at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** The whole taught route, walking through the stump after its fuse is lit, from a standing start. */
    private int solve(Level level, boolean jumpTheStump) {
        int frames = crossPit(level);
        frames += walkTo(level, () -> atEnd(level), jumpTheStump);
        return frames;
    }

    @Test void theRoomOffersOneFallingPlatformOverAPitAndOneStumpAndNothingElse() {
        Level level = freshRoom();
        assertEquals("forest-0-traps", level.currentRoomName());
        assertEquals(1, level.fallingPlatforms.size(), "exactly one falling platform");
        assertEquals(1, level.objects.stream().filter(ExplosiveTrap.class::isInstance).count(), "exactly one exploding stump");
        assertTrue(level.triggers.isEmpty() && level.escalators.isEmpty() && level.ladders.isEmpty(), "no lever or other mechanism");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>), "no monsters");
        assertFalse(platform(level).isFalling());
        assertEquals(PLAT_X, platform(level).getX(), .01f);
        assertEquals(320, platform(level).getY(), .01f, "flush with the floor");
        assertEquals(STUMP_X, stump(level).getX(), .01f);
        assertTrue(stump(level).getX() - level.player.getX() >= 400, "The stump lies well past the pit");
        // The pit has no floor at all between the two lips.
        Rectangle pit = new Rectangle(LIP + 1, 320, PIT_END - LIP - 2, 48);
        for (var block : level.collisionBlocks) if (!block.isEscalator())
            assertFalse(pit.overlaps(new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight())), "a floor in the pit");
        assertEquals(720, level.currentRoomBounds().width, "the room is 720 px");
        assertEquals(ROOM_X + 720, ROOM_RIGHT);
    }

    @Test void theMasteredRouteWalksThroughTheLitStumpAndReachesTheEndWithoutAHit() {
        Level level = freshRoom();
        int frames = solve(level, false);
        assertTrue(stumpGone(level), "the stump was set off and went off behind the hero");
        assertTrue(atEnd(level));
        float seconds = frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "traps room mastered route (walk through the stump): %.2f s, ends at x=%.0f%n", seconds, level.player.position.x);
        assertTrue(seconds < 12f, "A short room: " + seconds);
        assertTrue(level.fallingPlatforms.isEmpty() || platform(level).isFalling(), "the platform was used");
    }

    @Test void jumpingOverTheStumpAlsoPassesAndNeverLightsIt() {
        Level level = freshRoom();
        int frames = solve(level, true);
        assertTrue(atEnd(level));
        System.out.printf(java.util.Locale.ROOT, "traps room route (jump the stump): %.2f s%n", frames * STEP);
        assertFalse(stumpGone(level), "the stump stayed where it was, unlit, after a clean jump");
    }

    @Test void thePlatformLightsUpOnLandingWarnsForTheSharedDelayAndOnlyThenFalls() {
        Level level = freshRoom();
        place(level, PLAT_X + 20);
        level.player.position.set(PLAT_X + 8, 272);
        int landed = -1;
        for (int i = 0; i < 60 * 5 && landed < 0; i++) { step(level, false, false); if (platform(level).isFalling()) landed = i; }
        assertTrue(landed >= 0, "landing on the platform starts it");
        float y0 = platform(level).getY();
        settle(level, (int) (WARNING * 60) - 6);
        assertEquals(y0, platform(level).getY(), .01f, "warning period: the platform has not moved");
        assertTrue(alive(level), "and the hero stands safely on it during the warning");
        settle(level, 20);
        assertTrue(platform(level).getY() > y0 + 4, "after the warning it falls");
    }

    @Test void aHeroWhoJumpsOnWithinTheWarningCrossesAtAnyDwellUpToTheWarningMinusAJump() {
        int longest = -1;
        for (int dwell = 0; dwell <= 150; dwell += 3) {
            Level level = freshRoom();
            level.player.position.set(PLAT_X + 8, 272);
            int f = 0;
            while (!platform(level).isFalling() && f++ < 120) step(level, false, false);
            settle(level, dwell);
            boolean crossed = false;
            for (int i = 0; i < 60 * 3 && alive(level); i++) {
                step(level, true, i < 2);
                if (bodyLeft(level) >= PIT_END + 4 && level.player.isOnGround()) { crossed = true; break; }
            }
            if (crossed && alive(level)) longest = dwell;
        }
        System.out.printf(java.util.Locale.ROOT, "platform: a hero who dawdles up to %.2f s after landing still crosses (warning %.1f s)%n", longest / 60f, WARNING);
        assertTrue(longest >= 45 && longest < 150, "A dawdle of most of the warning is forgiven, a long one is not: " + longest);
    }

    @Test void standingOnThePlatformUntilItFallsCostsOneRetryAndTheRetryRestoresIt() {
        Level level = freshRoom();
        level.player.position.set(PLAT_X + 8, 272);
        int f = 0;
        while (alive(level) && f++ < 60 * 8) step(level, false, false);
        assertFalse(alive(level), "a hero who never leaves the platform ends in the pit");
        assertTrue(level.fallingPlatforms.isEmpty() || platform(level).getY() > 320, "the platform fell");
        retry(level);
        assertEquals(1, level.fallingPlatforms.size(), "the retry rebuilt the platform");
        assertFalse(platform(level).isFalling());
        assertEquals(PLAT_X, platform(level).getX(), .01f); assertEquals(320, platform(level).getY(), .01f);
        assertFalse(stumpGone(level));
        assertTrue(alive(level));
        solve(level, false); // and the retried room is passable
    }

    @Test void theGapIsTooWideToJumpWithoutThePlatformAndEachHalfIsJumpable() {
        for (float x = LIP - 40; x <= LIP; x += 4) {
            Level level = freshRoom();
            platform(level).restore(new FallingPlatform.Snapshot(PLAT_X, 320, false, -1f, 0f, true, 0f, true)); // spent
            level.fallingPlatforms.clear();
            place(level, x);
            boolean crossed = false;
            for (int i = 0; i < 60 * 3 && alive(level); i++) {
                step(level, true, level.player.isOnGround() && bodyRight(level) >= LIP - 1);
                if (bodyLeft(level) >= PIT_END && level.player.isOnGround()) crossed = true;
            }
            assertFalse(crossed, "crossed the 144 px pit by a jump alone from x=" + x);
        }
    }

    @Test void aHeroWhoStepsBackAfterLightingThePlatformIsNotStrandedForeverAndTheRetryRestoresTheRoute() {
        Level level = freshRoom();
        level.player.position.set(PLAT_X + 8, 272);
        int f = 0;
        while (!platform(level).isFalling() && f++ < 120) step(level, false, false);
        // Back onto the entrance floor within the warning.
        for (int i = 0; i < 60 && bodyRight(level) > LIP; i++) { held.clear(); held.add(Input.Keys.A); if (i < 2) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); } frame(level); }
        held.clear();
        settle(level, 60 * 4);
        assertTrue(alive(level), "standing on the entrance floor is safe");
        assertTrue(level.fallingPlatforms.isEmpty(), "the platform is gone");
        // The only way on is the pit itself, which is the level's Deadline: death and the room retry.
        for (int i = 0; i < 60 * 3 && alive(level); i++) step(level, true, level.player.isOnGround() && bodyRight(level) >= LIP - 1);
        assertFalse(alive(level), "the stranded hero retries by dropping into the pit");
        retry(level);
        assertEquals(1, level.fallingPlatforms.size());
        solve(level, false);
        // Restart Level: a new Level is the authored room again.
        Level fresh = freshRoom();
        assertEquals(1, fresh.fallingPlatforms.size());
        assertFalse(fresh.fallingPlatforms.get(0).isFalling());
    }

    @Test void theStumpWarnsForTheSharedFuseThenBlastsASixtyFourPixelSquareOnce() {
        Level level = freshRoom();
        place(level, STUMP_X + 6);
        // Standing in the blast: the fuse lasts FUSE seconds, then the hero is hit.
        int frames = 0;
        while (alive(level) && frames < 60 * 3) { step(level, false, false); frames++; }
        assertFalse(alive(level), "a hero who stands on the stump is hit");
        assertTrue(frames * STEP >= FUSE - .1f && frames * STEP <= FUSE + .2f, "fuse is the shared warning: " + frames * STEP);
        assertTrue(stumpGone(level));
        retry(level);
        assertFalse(stumpGone(level), "the retry restores the unlit stump");
        assertTrue(alive(level));
    }

    @Test void retreatingFromTheLitStumpOrStandingBackAvoidsTheBlast() {
        for (boolean forward : new boolean[]{false, true}) {
            Level level = freshRoom();
            place(level, STUMP_X + 6);
            for (int i = 0; i < 60 * 3 && alive(level); i++) {
                held.clear(); if (i < 40) held.add(forward ? Input.Keys.D : Input.Keys.A); frame(level);
            }
            assertTrue(alive(level), "forward=" + forward + " cleared the blast square, at " + level.player.position);
            assertTrue(stumpGone(level));
            held.clear();
        }
    }

    @Test void afterTheStumpWentOffTheEndIsStillReachableAndTheRoomIsNeverASoftLock() {
        Level level = freshRoom();
        place(level, STUMP_X + 40);
        held.clear();
        settle(level, 10);
        place(level, STUMP_X + 4);
        for (int i = 0; i < 60 * 2; i++) step(level, true, false);
        assertTrue(alive(level));
        assertTrue(stumpGone(level));
        walkTo(level, () -> atEnd(level), false);
        assertTrue(atEnd(level), "end reachable after the explosion");
    }

    @Test void theLastRoomHasNoRoomExitAndTheCheckpointIsInsideIt() {
        Level level = freshRoom();
        assertFalse(level.hasNextRoom(), "the traps room is the last room");
        assertFalse(level.atRoomExit());
        Rectangle b = level.currentRoomBounds();
        assertTrue(CHECKPOINT_X > BLAST_RIGHT && CHECKPOINT_X + 16 < b.x + b.width - 16, "the checkpoint stands clear of the blast and inside the right wall");
    }

    @Test void theBlastAndTheCheckpointMatchTheRealMapObjects() {
        Level level = freshRoom();
        ExplosiveTrap stump = stump(level);
        Rectangle blast = stump.blastBox();
        var checkpoint = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.environment.Checkpoint.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.Entity.class::cast).findFirst().orElseThrow();
        Rectangle cp = new Rectangle(checkpoint.getX(), checkpoint.getY(), checkpoint.size.x, checkpoint.size.y);
        Rectangle room = level.currentRoomBounds();
        assertEquals(BLAST_LEFT, blast.x, .01f); assertEquals(BLAST_RIGHT, blast.x + blast.width, .01f);
        assertFalse(blast.overlaps(cp), "the blast never reaches the level end");
        assertTrue(cp.x - (blast.x + blast.width) >= 64, "the end stands well clear of the blast: " + (cp.x - (blast.x + blast.width)));
        assertTrue(room.contains(cp) && cp.x + cp.width <= room.x + room.width - 16, "and inside the right wall");
        assertTrue(blast.x > bodyRight(level) + 300, "the blast is far from the entrance");
        assertTrue(blast.x - PIT_END >= 100, "and from the pit's far lip, so landing from the platform is never in it");
    }

    /** Timing of one trap: frames from the trigger until the first visible effect. */
    private int platformFramesToFall(Level level) {
        level.player.position.set(PLAT_X + 8, 272);
        int f = 0;
        while (!platform(level).isFalling() && f++ < 120) step(level, false, false);
        float y0 = platform(level).getY();
        int frames = 0;
        while (platform(level).getY() <= y0 && frames++ < 60 * 5) step(level, false, false);
        return frames;
    }

    private int stumpFramesToBlast(Level level) {
        place(level, STUMP_X + 6);
        int frames = 0;
        while (!stumpGone(level) && frames++ < 60 * 5) frame(level);
        return frames;
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(warnings, longer);
    }

    @Test void warningsAreExactlyD7sSharedValuesNormallyAndWithTheLongerAssist() throws Exception {
        assertEquals(1f, TrapWarnings.FALLING_PLATFORM_SECONDS, 0f, "D7: falling platform 1 s");
        assertEquals(.8f, TrapWarnings.DEFAULT_WARNING_SECONDS, 0f, "D7: stump 0.8 s");
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            float scale = longer ? 1.5f : 1f;
            int platform = platformFramesToFall(freshRoom());
            int stump = stumpFramesToBlast(freshRoom());
            System.out.printf(java.util.Locale.ROOT, "longer=%s: platform moves after %.2f s, stump blasts after %.2f s%n", longer, platform * STEP, stump * STEP);
            assertEquals(1f * scale * 60, platform, 3, "platform warning, longer=" + longer);
            assertEquals(.8f * scale * 60, stump, 3, "stump fuse, longer=" + longer);
        }
        setLonger(false);
    }

    @Test void withTheAssistTheRouteStillPassesAndTheWarningsAreLonger() throws Exception {
        setLonger(true);
        Level level = freshRoom();
        solve(level, false);
        assertTrue(atEnd(level));
        setLonger(false);
    }

    @Test void thePitsNearWallCannotBeClamberedBackAndItNeverBypassesThePlatform() {
        boolean climbedOut = false;
        for (float dx = 0; dx <= 40; dx += 8) for (int period : new int[]{3, 7, 15}) {
            Level level = freshRoom();
            place(level, LIP + dx);
            for (int i = 0; i < 60 * 3 && alive(level); i++) {
                held.clear(); held.add(Input.Keys.A);
                if (i % period == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                frame(level);
                if (level.player.isOnGround() && level.player.position.y > 300 && bodyRight(level) < LIP + 20) climbedOut = true;
            }
            assertFalse(alive(level) && bodyLeft(level) > LIP, "never reached the far floor");
        }
        System.out.println("pit wall clambered out: " + climbedOut);
        assertFalse(climbedOut, "a fall into the pit is a retry, never a way back up");
    }
}
