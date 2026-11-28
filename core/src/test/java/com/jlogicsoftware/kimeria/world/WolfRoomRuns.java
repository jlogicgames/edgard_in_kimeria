package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole runs of a scripted hero through a wolf's room (D165), shared by {@code ForestGladeTest} and {@code ForestRidgeTest}: the
 * mastered route or a careless walker ({@link WolfPerchRoute}), from the entrance to the exit or the hit.
 */
final class WolfRoomRuns {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int LIMIT = 60 * 40;
    /** A perched hero jumps in place this often at the soonest (frames: a jump lasts 53), then rarer in these steps. */
    private static final int JUMP_SOONEST = 54, JUMP_RAREST = 240, JUMP_STEP = 6, JUMP_FIRST = 10, JUMP_WATCH = 60 * 30;

    /** How a run ended: {@code out} false is a hit; {@code killedIn} the wolf's state on the frame before it died, null if it lived. */
    record Run(boolean out, float seconds, Wolf.State killedIn, boolean pounced) { }

    /** A swept wait: 0 to {@link #WAIT_CAP} frames (8 s) in steps of {@link #WAIT_STEP}; counts are split after {@link #WAIT_SHORT} (4 s). */
    static final int WAIT_STEP = 6, WAIT_SHORT = 240, WAIT_CAP = 480;

    /** The play tileset's ground tops: left, middle, right (22 to 24) and the one-tile pillar (25). */
    private static final int TILE = 16, FIRST_TOP_TILE = 22, PILLAR_TOP_TILE = 25;

    private WolfRoomRuns() { }

    /**
     * Open ground is drawn as ground: every top cell of a solid of {@code room} with nothing drawn above it carries a ground top
     * tile (soil left there, as by a mound moved without repainting, fails). At least {@code atLeast} cells must have been seen.
     */
    static void assertOpenGroundHasTopTiles(HeadlessLevelHarness h, String room, int atLeast) {
        Rectangle bounds = h.room("forest-2", room).currentRoomBounds();
        TiledMapData map = TiledMapData.load("tiles/forest-2.tmx", h.assets());
        int tops = 0;
        for (TiledObject c : map.layer("Collisions")) {
            if ("Platform".equals(c.type) || "QuickSand".equals(c.type) || c.x < bounds.x || c.x >= bounds.x + bounds.width || c.y < TILE) continue;
            int row = (int) c.y / TILE;
            for (int col = (int) c.x / TILE; col < (int) (c.x + c.width) / TILE; col++) {
                if (map.backgroundGrid[row - 1][col] != 0) continue;
                int tile = map.backgroundGrid[row][col];
                assertTrue(tile >= FIRST_TOP_TILE && tile <= PILLAR_TOP_TILE,
                    room + ": soil where a ground top belongs: x " + col * TILE + ", y " + c.y + ", tile " + tile);
                tops++;
            }
        }
        assertTrue(tops > atLeast, room + ": its open ground was looked at: " + tops);
    }

    static Wolf wolf(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Wolf w) return w;
        return null;
    }

    /**
     * Runs {@code route} in {@code level} until the exit or the hit. He sets off after {@code delay} frames. {@code afterBay} not
     * negative: on the perch he first waits for the wolf to bay, then that many frames more, before the route goes on. Fails when a
     * walking hero stands still (a direction held, no jump pressed, no swing, and he moves neither way): a bite there says nothing.
     */
    static Run run(HeadlessLevelHarness h, Level level, WolfPerchRoute route, int delay, int afterBay) {
        Wolf w = wolf(level);
        Wolf.State killedIn = null;
        boolean pounced = false, seenBay = false;
        int waited = 0;
        for (int f = 0; f < LIMIT; f++) {
            h.held.clear(); h.pressed.clear();
            if (f >= delay) {
                boolean perched = afterBay >= 0 && route.onPerch(level);
                seenBay |= perched && w.state() == Wolf.State.BAY;
                if (perched && !seenBay) { /* he waits for it */ }
                else if (perched && waited < afterBay) waited++;
                else route.drive(level, h.held, h.pressed);
            }
            Wolf.State before = w.state();
            boolean walking = (h.held.contains(Input.Keys.D) || h.held.contains(Input.Keys.A)) && !h.pressed.contains(Input.Keys.J);
            float fromX = level.player.position.x, fromY = level.player.position.y;
            level.update(STEP);
            if (walking && !level.player.isSwinging() && !level.player.isGotHit() && level.player.position.x == fromX && level.player.position.y == fromY)
                fail("the walker stands still at x " + fromX + ", frame " + f + ", delay " + delay);
            pounced |= w.state() == Wolf.State.POUNCE_COIL;
            if (killedIn == null && w.state() == Wolf.State.DEAD) killedIn = before;
            if (level.player.isGotHit()) return new Run(false, (f + 1 - delay) * STEP, killedIn, pounced);
            if (level.atRoomExit()) return new Run(true, (f + 1 - delay) * STEP, killedIn, pounced);
        }
        return fail("the run never ended");
    }

    /**
     * One run in {@code room} for every swept wait: {@code walker} makes the hero for a wait of so many frames, {@code afterBay} as in
     * {@link #run}. Returns {bitten, out} for the waits up to {@link #WAIT_SHORT}, then {bitten, out} for the longer ones; fails on a
     * wolf killed outside its recovery.
     */
    static int[] sweepWaits(HeadlessLevelHarness h, String room, BiFunction<Level, Integer, WolfPerchRoute> walker, int afterBay) {
        int[] counts = new int[4];
        for (int wait = 0; wait <= WAIT_CAP; wait += WAIT_STEP) {
            Level level = h.room("forest-2", room);
            Run run = run(h, level, walker.apply(level, wait), 0, afterBay);
            assertTrue(run.killedIn() == null || run.killedIn() == Wolf.State.RECOVER, "no stomp outside its recovery: wait " + wait + " " + run);
            counts[(wait > WAIT_SHORT ? 2 : 0) + (run.out() ? 1 : 0)]++;
        }
        return counts;
    }

    /**
     * A hero put on a perch from {@code left} to {@code right} (its top at {@code top}) who jumps in place, at several places and
     * rhythms, for half a minute each: never bitten, whatever the wolf is doing as he leaves and comes back to it.
     */
    static void assertAJumpingPerchedHeroIsNeverBitten(HeadlessLevelHarness h, String room, float left, float right, float top) {
        for (float centre = left + 6; centre <= right - 6; centre += 13)
            for (int every = JUMP_SOONEST; every <= JUMP_RAREST; every += JUMP_STEP) {
                Level level = h.room("forest-2", room);
                level.player.position.set(centre - 23.5f, top - 48);
                level.player.velocity().setZero();
                for (int f = 0; f < JUMP_WATCH; f++) {
                    h.held.clear(); h.pressed.clear();
                    if (f % every == JUMP_FIRST) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }
                    level.update(STEP);
                    assertFalse(level.player.isGotHit(), "bitten on the perch: centre " + centre + ", a jump every " + every + " frames, frame " + f);
                }
            }
    }
}
