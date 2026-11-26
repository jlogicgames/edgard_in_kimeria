package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.objects.Decor;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.objects.LethalHazard;
import com.jlogicsoftware.kimeria.entity.objects.StaticPlatform;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-1 tree room (#349, D80): two trees. The nearer one holds a column of rotten branches straight up (the fast line: each branch gives way
 * a warning after it is landed on and falls behind the climber); the farther one a zig-zag of sound branches that starts on its far side (the sure
 * line, D26). Both end on the sound tree's crown branch, which holds the checkpoint. A rotten branch never kills: a hero who stops on one rides it
 * down to the floor and climbs the sound tree. The room is forest-1's one reward room (D105, #500): nothing in it can kill (the beetle that
 * crawled at the sound tree's foot, #327, left it), and a death is only a deadline fall.
 * Ordinary keyboard input drives a scripted hero through the real shipped map; times are author (mastered) measurements, not novice playtests.
 *
 * <p>Y grows downward: the floor top is y 320, the branch tops are y 272, 224, 176, 128 and 80, 48 px apart (a jump rises about 58 px), and the
 * crown's top is y 32.
 * Everything below is a map coordinate (room 4 starts at x 2880).
 */
class TreeClimbTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 2880, ROOM_W = 640, START_X = 2912, FLOOR = 304;
    /** The rotten column (right of trunk 3168) and the sound tree (trunk 3264): left branches x 3232, right ones x 3280. */
    private static final float ROTTEN_X = 3184, SOUND_LEFT_X = 3232, SOUND_RIGHT_X = 3280, BRANCH_W = 32;
    private static final float[] TOPS = {256, 208, 160, 112, 64};
    /** The crown: the sound tree's top branch (left of its trunk), reached from the rotten column's top or the zig-zag's; the checkpoint stands on it. */
    private static final float CROWN_X = 3232, CROWN = 32, CHECKPOINT_X = 3240;
    /** D19: about 20 s for a mastered route. */
    private static final float BUDGET = 20f;
    /** The fast line by the rotten column: the room card's beat time (D105). */
    static final float FAST_S = 4.95f;
    private static final int LIMIT = 60 * 40;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }

    private Level room() {
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new);
        for (int i = 0; i < 11; i++) level.enterNextRoom(player()); // mob, deadfall, escalators, traps, lever, spider, bramble, climb, route, guard, hive, tree (#375, #418, #363, #364, #365, #484)
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    private boolean won(Level level) { return level.player.getState() == Player.State.DISAPPEARING; }
    private void alive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position); }
    private void jump() { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
    private boolean standingAt(Level level, float top) { return level.player.isOnGround() && Math.abs(feet(level) - top) < 0.5f; }
    private List<FallingPlatform> rotten(Level level) { return level.fallingPlatforms; }
    /** The rotten branch whose top is {@code top}, or null once it has fallen out of the list. */
    private FallingPlatform rottenAt(Level level, float top) {
        for (FallingPlatform f : level.fallingPlatforms) if (!f.isFalling() && f.getY() == top) return f;
        return null;
    }

    /** One support of a scripted climb: walk or fly to {@code x} (the body's centre), land on {@code top}, then jump on at once. */
    private record Hold(float x, float top) { }

    /** The fast line: under the rotten column, up its five branches without stopping, then up and right onto the crown. */
    private static List<Hold> fastLine() {
        List<Hold> holds = new ArrayList<>();
        for (float top : TOPS) holds.add(new Hold(ROTTEN_X + BRANCH_W / 2, top));
        holds.add(new Hold(CHECKPOINT_X + 8, CROWN));
        return holds;
    }

    /** The sure line: the sound branches, right and left of the second trunk in turn (it starts on the far side), then the crown. */
    private static List<Hold> soundLine() {
        List<Hold> holds = new ArrayList<>();
        for (int i = 0; i < TOPS.length; i++) holds.add(new Hold(soundX(i) + BRANCH_W / 2, TOPS[i]));
        holds.add(new Hold(CHECKPOINT_X + 8, CROWN));
        return holds;
    }

    /** The x of the sound branch at TOPS[i]: the far (right) side first. */
    private static float soundX(int i) { return i % 2 == 0 ? SOUND_RIGHT_X : SOUND_LEFT_X; }

    /**
     * Drives the hero along {@code holds} from where he stands with ordinary keys: steer towards the next hold's x, jump as soon as he stands on the
     * support he is leaving (the floor or the last hold) and is within jumping distance. Returns frames, or -1 if {@code stopOn} (a top) is reached
     * and the caller wants to stop there.
     */
    private int climb(Level level, List<Hold> holds, float stopOn) {
        int frames = 0, next = 0;
        float from = feet(level);
        while (!won(level)) {
            Hold target = holds.get(next);
            held.clear();
            float dx = target.x() - centerX(level);
            if (Math.abs(dx) > 1.5f) held.add(dx > 0 ? Input.Keys.D : Input.Keys.A);
            if (standingAt(level, target.top())) {
                if (target.top() == stopOn) { held.clear(); return frames; }
                from = target.top();
                if (next < holds.size() - 1) next++;
            } else if (standingAt(level, from) && Math.abs(dx) < 40 && target.top() < from) jump();
            frame(level); alive(level);
            assertTrue(++frames < LIMIT, "climb stuck at " + level.player.position + " towards " + target);
        }
        held.clear();
        return frames;
    }

    private int climb(Level level, List<Hold> holds) { return climb(level, holds, -1); }

    /** Stands still on a rotten branch until it has given way and carried the hero to the floor; returns the frames. */
    private int rideDown(Level level) {
        int frames = 0;
        held.clear();
        do {
            frame(level); alive(level);
            assertTrue(++frames < LIMIT, "ride stuck at " + level.player.position);
        } while (!settledOnFloor(level));
        return frames;
    }

    private boolean settledOnFloor(Level level) { return standingAt(level, FLOOR) && level.player.velocity().y >= 0; }

    private void report(String what, int frames) {
        System.out.printf(Locale.ROOT, "TreeClimbTest %s: %d frames = %.2f s%n", what, frames, frames * STEP);
    }

    // ---- census and geometry ----

    @Test void theRoomIsTwoTreesFiveRottenBranchesSixSoundOnesAndTheCheckpointOnTheCrownAndNothingElse() {
        Level level = room();
        assertEquals("forest-1-tree", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, ROOM_W, 368), level.currentRoomBounds());
        assertFalse(level.hasNextRoom(), "the tree room is forest-1's last: its checkpoint ends the level");
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(TOPS.length, rotten(level).size());
        for (int i = 0; i < TOPS.length; i++) {
            FallingPlatform f = rotten(level).get(i);
            assertEquals(FallingPlatform.Skin.BRANCH, f.skin());
            assertEquals(ROTTEN_X, f.getX(), 0.01f);
            assertEquals(TOPS[i], f.getY(), 0.01f);
            assertEquals(0, f.crumbleFrame(), "intact at rest: its rot is the at-rest cue (D71)");
        }
        List<StaticPlatform> sound = level.staticPlatforms();
        assertEquals(TOPS.length + 1, sound.size());
        for (int i = 0; i <= TOPS.length; i++) {
            StaticPlatform s = sound.get(i);
            assertEquals(StaticPlatform.Skin.BRANCH, s.skin());
            assertEquals(i < TOPS.length ? soundX(i) : CROWN_X, s.getX(), 0.01f);
            assertEquals(i < TOPS.length ? TOPS[i] : CROWN, s.getY(), 0.01f);
            assertTrue(level.collisionBlocks.stream().anyMatch(b -> b.isPlatform() && b.getX() == s.getX() && b.getY() == s.getY()),
                "a sound branch is a one-way platform");
        }
        assertEquals(2, level.decor.size());
        for (Decor trunk : level.decor) {
            assertEquals("trunk", trunk.kind());
            assertFalse(trunk.brushes() || trunk.isStill(), "a trunk neither brushes nor follows the look-alike's rules");
            trunk.notice(new Rectangle(trunk.getX(), trunk.getY() + 100, 11, 22), 100f); // a hero body inside the trunk, moving
            assertFalse(trunk.playing(), "a trunk never reacts to the hero");
            assertEquals(FLOOR, trunk.getY() + trunk.getHeight(), 0.01f, "a trunk stands on the floor");
            assertTrue(level.collisionBlocks.stream().noneMatch(b -> b.getX() == trunk.getX() && b.getY() == trunk.getY()), "a trunk is not solid");
        }
        var checkpoint = level.objects.stream().filter(Checkpoint.class::isInstance).map(Checkpoint.class::cast).findFirst().orElseThrow();
        assertEquals(CHECKPOINT_X, checkpoint.getX(), 0.01f);
        assertEquals(CROWN, checkpoint.getY() + checkpoint.getHeight(), 0.01f, "it stands on the crown");
        assertTrue(level.objects.stream().noneMatch(LethalHazard.class::isInstance), "no lethal trap");
        assertEquals(0, level.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "no monster: the room is forest-1's one reward room (D105, #500)");
        var coins = level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).toList();
        assertEquals(1, coins.size(), "#417 (D96): one first coin, nothing else to collect");
        assertEquals(com.jlogicsoftware.kimeria.entity.items.ItemType.COIN, coins.get(0).type());
        assertEquals(FLOOR, coins.get(0).getY() + coins.get(0).getHeight(), 0.01f, "on the floor line");
        assertTrue(coins.get(0).getX() < 3168 - 16, "off the climb: left of the first trunk");
        assertEquals("", level.player.zoneHint(), "no hints");
    }

    @Test void theTreeRoomIsEnteredFromTheHiveRoomExitAndARetryRestoresEveryRottenBranch() {
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new);
        level.enterNextRoom(player()); // forest-1-mob (#375) -> forest-1-deadfall (#363)
        level.enterNextRoom(player()); // -> forest-1-escalators
        level.enterNextRoom(player()); // -> forest-1-traps
        level.enterNextRoom(player());
        level.enterNextRoom(player());
        level.enterNextRoom(player()); // -> forest-1-spider (#484)
        level.enterNextRoom(player()); // -> forest-1-bramble (#364)
        level.enterNextRoom(player()); // -> forest-1-climb (#484)
        level.enterNextRoom(player()); // traps, lever, spider, bramble, climb, route, guard (#418, #364, #484)
        assertEquals("forest-1-guard", level.currentRoomName());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player()); // -> forest-1-hive (#365)
        assertEquals("forest-1-hive", level.currentRoomName());
        level.enterNextRoom(player());
        assertEquals("forest-1-tree", level.currentRoomName());
        climb(level, fastLine(), TOPS[2]);
        for (int i = 0; i < 120; i++) frame(level);
        assertTrue(rotten(level).size() < TOPS.length || rotten(level).stream().anyMatch(FallingPlatform::isFalling), "branches gave way");
        level.resetCurrentRoom(player());
        assertEquals(TOPS.length, rotten(level).size(), "a retry (death or a first visit's reset) rebuilds every rotten branch (D26)");
        assertTrue(rotten(level).stream().noneMatch(FallingPlatform::isFalling));
        assertEquals(START_X, level.player.position.x, 0.01f);
    }

    /** The D69 corner is ruled out by placement: nothing one-way lies under the rotten column, even counting a hero overhanging its end. */
    @Test void noSoundBranchLiesUnderTheRottenColumnAndTheTwoTreesAreSixteenPixelsApart() {
        Level level = room();
        for (CollisionBlock b : level.collisionBlocks) {
            if (!b.isPlatform()) continue;
            assertTrue(b.getX() >= ROTTEN_X + BRANCH_W + FallingPlatform.BRANCH_SIDE_MARGIN || b.getX() + b.getWidth() <= ROTTEN_X - FallingPlatform.BRANCH_SIDE_MARGIN,
                "a one-way platform at x " + b.getX() + " lies under the rotten column");
        }
        assertEquals(16f, CROWN_X - (ROTTEN_X + BRANCH_W), 0.01f, "the last hop, from the rotten column's top to the crown, 48 px up");
    }

    // ---- routes (mastered, D19) ----

    @Test void theFastLineClimbsTheRottenColumnWithoutStoppingAndEveryBranchFallsBehindTheClimber() {
        Level level = room();
        int frames = climb(level, fastLine());
        report("fast line (rotten column)", frames);
        assertTrue(frames * STEP <= BUDGET, "fast line " + frames * STEP + " s");
        assertEquals(FAST_S, frames * STEP, 0.1f, "the fast line (D105's beat time), pinned within 0.1 s");
        for (int i = 0; i < 60 * 3; i++) frame(level);
        assertTrue(rotten(level).isEmpty(), "every rotten branch the hero stood on gave way after he left it");
    }

    @Test void theSureLineClimbsTheSoundTreeAndNoSoundBranchEverGivesWay() {
        Level level = room();
        int frames = climb(level, soundLine());
        report("sure line (sound tree)", frames);
        assertTrue(frames * STEP <= BUDGET, "sure line " + frames * STEP + " s");
        assertEquals(TOPS.length + 1, level.staticPlatforms().size());
        assertTrue(rotten(level).stream().noneMatch(FallingPlatform::isFalling), "the sound line never touches a rotten branch");
    }

    @Test void theFastLineIsFasterThanTheSureLine() {
        int fast = climb(room(), fastLine()), sure = climb(room(), soundLine());
        report("fast line saves", sure - fast);
        assertTrue(fast < sure, "the rotten column must pay for its risk: fast " + fast + " frames, sure " + sure);
    }

    /**
     * The develop (D72): stopping on a rotten branch costs height, not life. For each branch of the column the hero climbs to it, stands still, is
     * carried down to the floor, then finishes by the sound tree. The worst of the five is the room's fall cost.
     */
    @Test void stoppingOnAnyRottenBranchRidesTheHeroToTheFloorAliveAndTheSoundTreeStillReachesTheTop() {
        int fast = climb(room(), fastLine()), worst = 0;
        for (float top : TOPS) {
            Level level = room();
            int frames = climb(level, fastLine(), top);
            frames += rideDown(level);
            assertEquals(FLOOR, feet(level), 0.5f, "stands on the floor line (D69), never sunk into it");
            frames += climb(level, soundLine());
            report("stop on the rotten branch at y " + (int) top + ", ride down, sure line", frames);
            assertTrue(frames * STEP <= BUDGET, "a mistake still finishes inside the budget");
            worst = Math.max(worst, frames - fast);
        }
        report("worst fall cost", worst);
    }

    // ---- D26: the exit is reachable from every state supported actions produce ----

    /** Every subset of the rotten column already gone (32 states): the sure line still reaches the top. */
    @Test void withAnySubsetOfRottenBranchesGoneTheSoundTreeStillReachesTheTop() {
        for (int mask = 0; mask < 1 << TOPS.length; mask++) {
            Level level = room();
            for (int i = TOPS.length - 1; i >= 0; i--) if ((mask & 1 << i) != 0) level.fallingPlatforms.remove(i);
            climb(level, soundLine());
            assertTrue(won(level), "mask " + mask);
        }
    }

    /** Walking off a rotten branch sideways (Left or Right held while it sinks) lands alive on the floor or on a sound branch. */
    @Test void leavingASinkingRottenBranchSidewaysIsNeverLethalAndNeverEndsInsideASolid() {
        for (float top : TOPS) {
            for (int key : new int[]{Input.Keys.A, Input.Keys.D}) {
                for (int delay = 0; delay <= 90; delay += 15) {
                    Level level = room();
                    climb(level, fastLine(), top);
                    for (int i = 0; i < delay; i++) frame(level);
                    int frames = 0;
                    // Walk off; once off the branch let go, and ride whatever caught him (another sinking rotten branch) until he rests.
                    while (!(level.player.isOnGround() && (feet(level) == FLOOR || isSoundTop(level, feet(level))) && frames > 5)) {
                        held.clear();
                        if (frames < 30) held.add(key);
                        frame(level); alive(level);
                        assertTrue(++frames < LIMIT, "stuck at " + level.player.position);
                    }
                    Rectangle body = level.player.hitboxBounds();
                    for (CollisionBlock b : level.collisionBlocks)
                        if (b.isActive() && !b.isPlatform() && !b.isQuickSand())
                            assertFalse(body.overlaps(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())),
                                "inside a solid after leaving y " + top + " key " + key + " delay " + delay + ": " + body);
                    assertTrue(feet(level) == FLOOR || isSoundTop(level, feet(level)), "landed on " + feet(level));
                }
            }
        }
    }

    private boolean isSoundTop(Level level, float y) {
        for (StaticPlatform s : level.staticPlatforms()) if (Math.abs(s.getY() - y) < 0.5f) return true;
        return false;
    }

    // ---- the rotten branch's telegraph (D7) ----

    @Test void aRottenBranchCracksForTheSharedWarningThenFallsAndRisingThroughItFromBelowTriggersNothing() {
        Level level = room();
        FallingPlatform lowest = rottenAt(level, TOPS[0]);
        int frames = 0;
        // Walk under it and jump: the head passes up through it first (one-way), which must not start it.
        boolean wasBelow = false;
        while (!standingAt(level, TOPS[0])) {
            held.clear();
            float dx = ROTTEN_X + BRANCH_W / 2 - centerX(level);
            if (Math.abs(dx) > 1.5f) held.add(dx > 0 ? Input.Keys.D : Input.Keys.A);
            else if (standingAt(level, FLOOR)) jump();
            Rectangle body = level.player.hitboxBounds();
            if (body.y < TOPS[0] + 16 && feet(level) > TOPS[0]) { wasBelow = true; assertFalse(lowest.isFalling(), "rising through it from below starts nothing"); }
            frame(level); alive(level);
            assertTrue(++frames < LIMIT);
        }
        held.clear();
        assertTrue(wasBelow, "the hero rose through the branch from below");
        assertTrue(lowest.isFalling());
        float warning = TrapWarnings.FALLING_PLATFORM_SECONDS;
        int moved = -1;
        for (int i = 1; i < 60 * 3 && moved < 0; i++) {
            frame(level);
            if (i == 2) assertEquals(1, lowest.crumbleFrame(), "the split opens at once");
            if (i * STEP > warning / 2 + 2 * STEP && i * STEP < warning) assertEquals(2, lowest.crumbleFrame(), "then it snaps");
            if (lowest.getY() > TOPS[0]) moved = i;
        }
        // The frame the warning runs out only marks the fall; the platform first moves on the next one.
        assertEquals(warning + STEP, moved * STEP, 1.5f * STEP, "it falls after the shared warning (TrapWarnings.FALLING_PLATFORM_SECONDS)");
    }

    // ---- the load checks (D69, D26) ----

    private static TiledObject object(int id, String type, float x, float y, float w, float h, String skin) {
        TiledObject o = new TiledObject();
        o.id = id; o.type = type; o.x = x; o.y = y; o.width = w; o.height = h;
        if (skin != null) o.properties.put("skin", skin);
        return o;
    }

    private static final Rectangle ROOM = new Rectangle(0, 0, 640, 368);
    private static final List<Rectangle> TOP_GOAL = List.of(new Rectangle(200, 48, 16, 32));

    /** The synthetic tree of the load rule tests, over its own floor at y 320 (not a shipped room). */
    private static final float[] FIXTURE_TOPS = {272, 224, 176, 128, 80};

    private static List<TiledObject> floorAndSoundTree() {
        List<TiledObject> c = new ArrayList<>();
        c.add(object(1, "", 0, 320, 640, 48, null));
        for (int i = 0; i < FIXTURE_TOPS.length; i++) c.add(object(10 + i, "Platform", i % 2 == 0 ? 192 : 240, FIXTURE_TOPS[i], 32, 16, "branch"));
        return c;
    }

    @Test void aRottenBranchBesideASoundTreeLoads() {
        TiledObject branch = object(99, "FallingPlatform", 128, 176, 32, 16, "branch");
        assertNull(FallingPlatform.branchProblem(branch, floorAndSoundTree(), ROOM, 32, 272, TOP_GOAL));
    }

    @Test void aRottenBranchAboveASoundOneFailsTheLoad() {
        TiledObject above = object(99, "FallingPlatform", 200, 128, 32, 16, "branch");
        String problem = FallingPlatform.branchProblem(above, floorAndSoundTree(), ROOM, 32, 272, TOP_GOAL);
        assertNotNull(problem);
        assertTrue(problem.contains("D69"), problem);
        TiledObject overhanging = object(98, "FallingPlatform", 192 - 32 - 15, 128, 32, 16, "branch"); // its end 15 px short of a sound branch below
        assertNotNull(FallingPlatform.branchProblem(overhanging, floorAndSoundTree(), ROOM, 32, 272, TOP_GOAL), "within the side margin");
    }

    /**
     * The D69 boundary, pinned: a one-way platform under a rotten branch (within its 200 px fall) must start at least
     * {@link FallingPlatform#BRANCH_SIDE_MARGIN} (16 px) beside its span; 16 px passes, 15 px fails, on either side. The shipped
     * map sits exactly on the boundary (the crown and the zig-zag start 16 px right of the rotten column).
     */
    @Test void theSideMarginBoundaryIsSixteenPixelsOnEitherSideAndTheFallDepthIsTwoHundred() {
        float x = 300, y = 80, w = 32, margin = FallingPlatform.BRANCH_SIDE_MARGIN, fall = FallingPlatform.FALL_DISTANCE;
        assertEquals(16f, margin, 0f);
        assertEquals(200f, fall, 0f);
        TiledObject branch = object(99, "FallingPlatform", x, y, w, 16, "branch");
        List<Rectangle> goal = List.of(new Rectangle(0, 288, 16, 32)); // on the floor: D26 holds, only D69 is judged
        record Case(float platformX, float platformY, boolean fails) { }
        for (Case c : List.of(
            new Case(x + w + margin, y + 48, false), new Case(x + w + margin - 1, y + 48, true),      // right side: 16 px passes, 15 fails
            new Case(x - margin - 32, y + 48, false), new Case(x - margin - 32 + 1, y + 48, true),    // left side: the same
            new Case(x, y + fall, true), new Case(x, y + fall + 1, false),                            // depth: within the fall fails
            new Case(x, y, false), new Case(x, y - 48, false))) {                                     // level with it or above: allowed
            List<TiledObject> c2 = new ArrayList<>();
            c2.add(object(1, "", 0, 320, 640, 48, null));
            c2.add(object(2, "Platform", c.platformX(), c.platformY(), 32, 16, "branch"));
            String problem = FallingPlatform.branchProblem(branch, c2, ROOM, 0, 272, goal);
            assertEquals(c.fails(), problem != null && problem.contains("D69"), c + ": " + problem);
        }
    }

    @Test void aRottenBranchThatIsTheOnlyWayUpFailsTheLoad() {
        List<TiledObject> c = floorAndSoundTree();
        c.removeIf(o -> o.y == FIXTURE_TOPS[2]); // the sound tree's middle branch is missing: from 224 the next sound top is 128, 96 px up
        TiledObject bridge = object(99, "FallingPlatform", 128, 176, 32, 16, "branch");
        String problem = FallingPlatform.branchProblem(bridge, c, ROOM, 32, 272, TOP_GOAL);
        assertNotNull(problem);
        assertTrue(problem.contains("D26"), problem);
    }

    @Test void theShippedMapPassesBothChecksAndAnUnknownSkinOrAStrayMirrorFailsTheLoad() {
        assertDoesNotThrow(this::room);
        assertThrows(IllegalArgumentException.class, () -> StaticPlatform.Skin.fromMap("twig"));
        assertThrows(IllegalArgumentException.class, () -> FallingPlatform.Skin.fromMap("twig"));
        assertThrows(IllegalArgumentException.class, () -> new StaticPlatform(h.assets(), 0, 0, 32, 16, StaticPlatform.Skin.PLATFORM, true));
        assertThrows(IllegalArgumentException.class, () -> new StaticPlatform(h.assets(), 0, 0, 64, 16, StaticPlatform.Skin.BRANCH, false));
        assertThrows(IllegalArgumentException.class, () -> new FallingPlatform(h.assets(), 0, 0, 32, 16, o -> { }, new TrapWarnings(),
            FallingPlatform.Skin.LOG, true));
    }
}
