package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.FloatArray;
import com.jlogicsoftware.kimeria.world.Wind;

import java.util.ArrayList;
import java.util.List;

/**
 * Wind in the crowns (#593, D163; docs/ART_DIRECTION.md, "Wind in the crowns"): as a {@link Wind} gust's front passes a leaf
 * spot, most of the spots light with a glint, a small ragged patch of leaf pixels that stands on the leaves, fades in and out and
 * moves nothing, so a wave of light crosses the foliage. Pure ambience. Three depths, each its own {@link KimeriaParticleSystem} (so draw order and live cap are per depth):
 * the {@link #CROWN decor crowns} in world coordinates, and the {@link #MID mid} and {@link #GROVE grove} scenery layers, whose
 * spots and particles are in layer coordinates and move with the layer's base ({@link #setLayerBase}); those two run behind the
 * front and are bluer, with a contrast against their own near-black leaves comparable to the crowns' (D163).
 *
 * <p>A spot is looked at once per gust, when the front (run by the depth's lag and the spot's own fixed delay) crosses it: a spot
 * that scrolls in behind the front does not pop. Whether it lights and whether it takes the warm fleck colour are
 * {@link Wind#hash} draws of (room seed, gust, spot), independent of the camera and the frame rate, so a retry shows the same
 * gusts. A spot lights only while it stands within the room, and only if its glint can finish within the sweep, so no glint comes
 * before the gust enters or outlives it. No glint starts off screen, over a trap's column or, for a crown, near what the room's
 * protected boxes hold (levers, pickups, monsters, the lever-driven wall, escalators, the checkpoint); a full pool skips the new
 * glint and never cuts a live one short. World Y grows downward. Only the room's load allocates ({@link #start}, {@link #clear}
 * and {@link Spots}); {@link #update} and {@link #render} do not.
 */
public final class CrownGlintFx {
    /** The depths, near to far. */
    public static final int CROWN = 0, MID = 1, GROVE = 2, DEPTHS = 3;
    private static final int CROWN_CAPACITY = 64, MID_CAPACITY = 24, GROVE_CAPACITY = 16;
    private static final int[] CAPACITY = {CROWN_CAPACITY, MID_CAPACITY, GROVE_CAPACITY};
    /** The most glints alive at once, over all depths. */
    public static final int TOTAL_CAPACITY = CROWN_CAPACITY + MID_CAPACITY + GROVE_CAPACITY;

    /** How far behind the gust's front each depth runs, s. */
    public static final float MID_LAG = .35f, GROVE_LAG = .70f;
    private static final float[] LAG = {0f, MID_LAG, GROVE_LAG};
    /** A spot's own fixed delay is 0 to this, s: the ragged front. */
    public static final float MAX_SPOT_DELAY = .2f;

    private static final int FOREST_DIRECTION = 1;
    private static final float FOREST_SPEED = 180f, FOREST_MIN_WIDTH = 72f, FOREST_MAX_WIDTH = 96f, FOREST_MIN_PERIOD = 8f, FOREST_MAX_PERIOD = 12f,
        FOREST_FIRST_DELAY = 1.5f, FOREST_EDGE_STRENGTH = .35f;
    /** The forest's wind, left to right in every room (the card's numbers). */
    public static final Wind.Profile FOREST_WIND = new Wind.Profile(FOREST_DIRECTION, FOREST_SPEED, FOREST_MIN_WIDTH, FOREST_MAX_WIDTH,
        FOREST_MIN_PERIOD, FOREST_MAX_PERIOD, FOREST_FIRST_DELAY, FOREST_EDGE_STRENGTH, GROVE_LAG + MAX_SPOT_DELAY);

    /**
     * The glint colours, RRGGBB: the leaf's own green made lighter, and the rare warm fleck; deeper layers blue-teal and grey-blue
     * (D120), no fleck on the grove. tools/wind_spots.py reads these and holds each within its OKLab distance of every leaf pixel
     * it may light.
     */
    public static final int CROWN_LEAF = 0x3d5538, CROWN_FLECK = 0x545a2b, MID_LEAF = 0x0a343a, MID_FLECK = 0x1a3626, GROVE_LEAF = 0x213d49;
    private static final int NO_FLECK = -1;
    private static final int[] LEAF_COLOUR = {CROWN_LEAF, MID_LEAF, GROVE_LEAF};
    private static final int[] FLECK_COLOUR = {CROWN_FLECK, MID_FLECK, NO_FLECK};
    private static final float FLECK_SHARE = 1f / 8f;

    /** The share of the spots the front passes that light: {@code SHARE_BASE + SHARE_GAIN * strength}. */
    private static final float SHARE_BASE = .45f, SHARE_GAIN = .30f;
    /** A glint's peak alpha: {@code PEAK_FLOOR + (1 - PEAK_FLOOR) * strength}, so the wave still reads where the gust is faint. */
    private static final float PEAK_FLOOR = .5f;

    /** How far a layer drifts across the world during one glint, px, at the hero's speed: a trap column is widened by it on that depth. */
    private static final float[] COLUMN_DRIFT = {0f, 32f, 40f};
    /** No crown glint starts within this of a lever, a pickup or a monster's placed box, px. */
    public static final float PROTECTED_MARGIN = 16f;

    /** The draws a spot makes, each with its own salt. */
    private static final int DELAY_DRAW = 0, LIGHT_DRAW = 1, FLECK_DRAW = 2, DRAWS = 3;
    /** A glint starts only with this much of the sweep to spare beyond its life, s: float rounding never keeps one into the calm. */
    private static final float END_SLACK_SECONDS = .001f;
    /** The "gust" of a draw that never changes in a room: the spot's own delay. */
    private static final int EVERY_GUST = -1;
    private static final int NEVER = -1, XY = 2, PAIR = 2, RED_SHIFT = 16, GREEN_SHIFT = 8, BYTE = 0xff;
    private static final int RUN_DY = 1, RUN_WIDTH = 2;
    private static final float CHANNEL_MAX = 255f;

    /** One depth's patches of a room, gathered at load: the top-left of each and its runs ({@link KimeriaParticleSystem#spawnGlint}). */
    public static final class Spots {
        private final FloatArray xy = new FloatArray();
        private final List<float[]> runs = new ArrayList<>();

        public void add(float x, float y, float[] patch) {
            xy.add(x, y);
            runs.add(patch);
        }

        public int size() { return runs.size(); }
        public float x(int i) { return xy.get(i * XY); }
        public float y(int i) { return xy.get(i * XY + 1); }
        public float[] runs(int i) { return runs.get(i); }

        /** The patch's extent right of its top-left, px. */
        public static float width(float[] patch) {
            float width = 0;
            for (int k = 0; k + RUN_WIDTH < patch.length; k += KimeriaParticleSystem.GLINT_RUN) width = Math.max(width, patch[k] + patch[k + RUN_WIDTH]);
            return width;
        }

        /** The patch's extent below its top-left, px. */
        public static float height(float[] patch) {
            float height = 0;
            for (int k = 0; k + RUN_WIDTH < patch.length; k += KimeriaParticleSystem.GLINT_RUN) height = Math.max(height, patch[k + RUN_DY] + 1);
            return height;
        }

        /** Drops the patches that reach into the rectangle. */
        public void removeTouching(float left, float top, float width, float height) {
            for (int i = size() - 1; i >= 0; i--) {
                float[] patch = runs(i);
                if (x(i) + width(patch) > left && x(i) < left + width && y(i) + height(patch) > top && y(i) < top + height) {
                    xy.removeRange(i * XY, i * XY + 1);
                    runs.remove(i);
                }
            }
        }
    }

    private final KimeriaParticleSystem[] systems = new KimeriaParticleSystem[DEPTHS];
    /** Per depth: the spots' top-left, sorted by x then y, their extents, their own delays and the last gust that crossed each. */
    private final float[][] spotX = new float[DEPTHS][0], spotY = new float[DEPTHS][0], spotWidth = new float[DEPTHS][0],
        spotHeight = new float[DEPTHS][0], delay = new float[DEPTHS][0];
    /** Per depth and spot: the patch's runs. */
    private final float[][][] patch = new float[DEPTHS][0][];
    private final int[][] lastGust = new int[DEPTHS][0];
    private final float[] baseX = new float[DEPTHS], baseY = new float[DEPTHS];
    /** Pairs of x ranges (min, max) of the room's traps. */
    private final FloatArray columns = new FloatArray();
    /** Rectangles (x, y, width, height) a crown glint keeps out of, already grown by the margin. */
    private final FloatArray guarded = new FloatArray();
    private static final int RECT = 4, RECT_Y = 1, RECT_W = 2, RECT_H = 3;
    /** A glint draws nothing from its system's random generator, so the seed carries nothing. */
    private static final long SYSTEM_SEED = 593;
    private int seed, spawned, skipped;

    public CrownGlintFx() {
        for (int d = 0; d < DEPTHS; d++) systems[d] = new KimeriaParticleSystem(CAPACITY[d], SYSTEM_SEED);
    }

    public static int capacity(int depth) { return CAPACITY[depth]; }

    /**
     * Starts a room. {@code spots}: per depth, the patches (world coordinates for the crowns, layer coordinates for mid and
     * grove); {@code trapColumns}: (min, max) x pairs in world px where no glint starts;
     * {@code protectedBoxes}: the placed boxes of levers, pickups and monsters, x, y, width, height. Clears every live glint.
     */
    public void start(int roomSeed, Spots[] spots, float[] trapColumns, FloatArray protectedBoxes) {
        clear();
        seed = roomSeed;
        columns.addAll(trapColumns);
        for (int i = 0; i + RECT <= protectedBoxes.size; i += RECT) {
            guarded.add(protectedBoxes.get(i) - PROTECTED_MARGIN);
            guarded.add(protectedBoxes.get(i + RECT_Y) - PROTECTED_MARGIN);
            guarded.add(protectedBoxes.get(i + RECT_W) + PAIR * PROTECTED_MARGIN);
            guarded.add(protectedBoxes.get(i + RECT_H) + PAIR * PROTECTED_MARGIN);
        }
        for (int d = 0; d < DEPTHS; d++) load(d, spots[d]);
    }

    private void load(int d, Spots spots) {
        int n = spots.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            int byX = Float.compare(spots.x(a), spots.x(b));
            return byX != 0 ? byX : Float.compare(spots.y(a), spots.y(b));
        });
        resize(d, n);
        for (int i = 0; i < n; i++) {
            float[] runs = spots.runs(order[i]);
            spotX[d][i] = spots.x(order[i]);
            spotY[d][i] = spots.y(order[i]);
            patch[d][i] = runs;
            spotWidth[d][i] = Spots.width(runs);
            spotHeight[d][i] = Spots.height(runs);
            delay[d][i] = Wind.hash(seed, EVERY_GUST, salt(d, i, DELAY_DRAW)) * MAX_SPOT_DELAY;
            lastGust[d][i] = NEVER;
        }
    }

    private void resize(int d, int n) {
        spotX[d] = new float[n]; spotY[d] = new float[n]; spotWidth[d] = new float[n]; spotHeight[d] = new float[n];
        delay[d] = new float[n]; lastGust[d] = new int[n];
        patch[d] = new float[n][];
    }

    private static int salt(int depth, int spot, int draw) { return (spot * DEPTHS + depth) * DRAWS + draw; }

    /** No spots and no glints: a room without wind. Called at a room's load; it allocates. */
    public void clear() {
        for (int d = 0; d < DEPTHS; d++) {
            systems[d].clear();
            resize(d, 0);
            baseX[d] = baseY[d] = 0;
        }
        columns.clear();
        guarded.clear();
        spawned = skipped = 0;
    }

    /** The world position of a parallax depth's layer origin for the camera of this frame; the crowns' stays 0. */
    public void setLayerBase(int depth, float x, float y) { baseX[depth] = x; baseY[depth] = y; }

    /** Ages the glints, then lights the spots the front crossed since the last call. {@code view} is the visible world rect; null shows nothing. */
    public void update(float dt, Wind wind, Rectangle view) {
        for (int d = 0; d < DEPTHS; d++) systems[d].update(dt);
        if (!wind.blowing()) return;
        int gust = wind.gust();
        float width = wind.width();
        for (int d = 0; d < DEPTHS; d++) {
            int n = spotX[d].length;
            for (int i = 0; i < n; i++) {
                if (lastGust[d][i] == gust) continue;
                float lag = LAG[d] + delay[d][i];
                float past = wind.direction() * (wind.frontX(lag) - (baseX[d] + spotX[d][i]));
                if (past < 0) continue;
                lastGust[d][i] = gust;
                // Further past than the band is wide: the spot came behind the front (the camera moved it), the wave is gone. Outside the
                // room: the gust has not entered there, or has left.
                // Too late to finish before the calm (a frame longer than the sweep's margin): the spot stays dark.
                if (past <= width && view != null && wind.covers(baseX[d] + spotX[d][i]) && width / wind.speed() + END_SLACK_SECONDS <= wind.remainingSeconds())
                    spawn(d, i, gust, wind.strength(lag), width / wind.speed(), view);
            }
        }
    }

    private void spawn(int d, int i, int gust, float strength, float lifeSeconds, Rectangle view) {
        if (Wind.hash(seed, gust, salt(d, i, LIGHT_DRAW)) >= SHARE_BASE + SHARE_GAIN * strength) return;
        float left = baseX[d] + spotX[d][i], top = baseY[d] + spotY[d][i], width = spotWidth[d][i], height = spotHeight[d][i];
        if (left + width <= view.x || left >= view.x + view.width || top + height <= view.y || top >= view.y + view.height) return;
        if (excluded(d, left, top, width, height)) return;
        KimeriaParticleSystem system = systems[d];
        if (system.count() == system.capacity()) { skipped++; return; }
        boolean fleck = FLECK_COLOUR[d] != NO_FLECK && Wind.hash(seed, gust, salt(d, i, FLECK_DRAW)) < FLECK_SHARE;
        int rgb = fleck ? FLECK_COLOUR[d] : LEAF_COLOUR[d];
        system.spawnGlint(spotX[d][i], spotY[d][i], lifeSeconds, PEAK_FLOOR + (1f - PEAK_FLOOR) * strength,
            (rgb >> RED_SHIFT & BYTE) / CHANNEL_MAX, (rgb >> GREEN_SHIFT & BYTE) / CHANNEL_MAX, (rgb & BYTE) / CHANNEL_MAX,
            patch[d][i]);
        spawned++;
    }

    private boolean excluded(int d, float left, float top, float width, float height) {
        float drift = COLUMN_DRIFT[d];
        for (int i = 0; i + 1 < columns.size; i += PAIR)
            if (left + width >= columns.items[i] - drift && left <= columns.items[i + 1] + drift) return true;
        if (d != CROWN) return false;
        for (int i = 0; i + RECT <= guarded.size; i += RECT)
            if (left + width > guarded.items[i] && left < guarded.items[i] + guarded.items[i + RECT_W]
                && top + height > guarded.items[i + RECT_Y] && top < guarded.items[i + RECT_Y] + guarded.items[i + RECT_H]) return true;
        return false;
    }

    /** One depth's live glints, moved by its layer's base ({@code 0, 0} for the crowns). */
    public void render(Batch batch, int depth, float offsetX, float offsetY) {
        if (systems[depth].count() > 0) systems[depth].render(batch, Shaders.whiteQuad(), offsetX, offsetY);
    }

    public KimeriaParticleSystem particles(int depth) { return systems[depth]; }
    public int spotCount(int depth) { return spotX[depth].length; }
    public float spotX(int depth, int i) { return spotX[depth][i]; }
    public float spotY(int depth, int i) { return spotY[depth][i]; }
    public float spotWidth(int depth, int i) { return spotWidth[depth][i]; }
    public float spotHeight(int depth, int i) { return spotHeight[depth][i]; }
    /** The runs of a spot's patch (shared, not to be written). */
    public float[] spotRuns(int depth, int i) { return patch[depth][i]; }
    public float spotDelay(int depth, int i) { return delay[depth][i]; }
    public float layerBaseX(int depth) { return baseX[depth]; }
    public float layerBaseY(int depth) { return baseY[depth]; }
    /** Glints lit since the room started. */
    public int spawned() { return spawned; }
    /** Glints skipped since the room started because their depth's pool was full. */
    public int skipped() { return skipped; }
}
