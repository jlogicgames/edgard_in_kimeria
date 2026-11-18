package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * Falling leaves, quiet ambience of a Forest room (#473): a few muted leaves drift down and sway, in rooms whose {@code Room}
 * marker sets {@code leaves}. Pure ambience: they react to nothing, never start at a trap, and are cleared with the room.
 * One {@link KimeriaParticleSystem} of {@link KimeriaParticleSystem#KIND_LEAF}. World Y grows downward.
 */
public final class LeafFx {
    public static final int CAPACITY = 24;
    private static final float FALL_MIN = 12, FALL_MAX = 20, DRIFT_MAX = 8, SWAY_MIN = 4, SWAY_MAX = 9;
    private static final float LIFE_MIN = 9, LIFE_MAX = 14, SIZE_MIN = 5, SIZE_MAX = 6, TOP_BAND = 48, SPAWN_MARGIN = 8;
    private static final float MEAN_LIFE = (LIFE_MIN + LIFE_MAX) / 2f;
    /** Decor-tier leaves (D76): Oklab distance 0.14 to 0.17 from the Forest background `2c3f66`: grey-green `526666`, pale grey-green `5b6f70`, dull olive `616764`. */
    private static final float[] COLORS = {.322f, .400f, .400f, .357f, .435f, .439f, .380f, .404f, .392f};
    private static final int COLOR_STRIDE = 3, GREEN = 1, BLUE = 2;

    private final KimeriaParticleSystem leaves;
    private final Rectangle room = new Rectangle();
    /** Pairs of x ranges (min, max) where no leaf starts: the columns of the room's traps. */
    private final FloatArray avoid = new FloatArray();
    private static final int SPAWN_TRIES = 8, PAIR = 2;
    /** Leaves are started a little faster than they die, so the room holds its count (a leaf's life is random, 9 to 14 s). */
    private static final float SPAWN_PACE = .75f;
    private int target;
    private float spawnClock;

    public LeafFx() { this(TimeUtils.nanoTime()); }
    public LeafFx(long seed) { leaves = new KimeriaParticleSystem(CAPACITY, seed); }

    public KimeriaParticleSystem particles() { return leaves; }

    private float range(float min, float max) { return min + leaves.random().nextFloat() * (max - min); }

    /** Starts a room: {@code count} leaves (0 = none) over {@code bounds}, already part-way down so the room is not empty at the door. */
    public void start(Rectangle bounds, int count) { start(bounds, count, null); }

    /** As {@link #start(Rectangle, int)}; no leaf starts inside any (min, max) pair of {@code avoidX}, so none begins above a trap. */
    public void start(Rectangle bounds, int count, float[] avoidX) {
        leaves.clear();
        avoid.clear();
        if (avoidX != null) avoid.addAll(avoidX);
        room.set(bounds);
        target = Math.min(count, CAPACITY);
        spawnClock = 0;
        for (int i = 0; i < target; i++) spawn(leaves.random().nextFloat());
    }

    /** One leaf along a fresh path, entered {@code progress} (0 to 1) of the way through it. */
    private void spawn(float progress) {
        RandomXS128 random = leaves.random();
        float life = range(LIFE_MIN, LIFE_MAX);
        float startY = room.y + random.nextFloat() * TOP_BAND;
        float fall = range(FALL_MIN, FALL_MAX) * life;
        float drift = range(-DRIFT_MAX, DRIFT_MAX) * life;
        float startX = 0;
        boolean placed = false;
        for (int tries = 0; tries < SPAWN_TRIES && !placed; tries++) {
            startX = room.x + SPAWN_MARGIN + random.nextFloat() * (room.width - 2 * SPAWN_MARGIN);
            placed = !avoided(startX + drift * progress); // where the leaf first appears, not where its path began
        }
        if (!placed) return; // every try landed over a trap: no leaf this time, the next spawn tries again
        int c = random.nextInt(COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
        float endY = Math.min(startY + fall, room.y + room.height - SPAWN_MARGIN);
        leaves.spawnLeaf(startX + drift * progress, startY + (endY - startY) * progress, startX + drift, endY,
            life * (1 - progress), range(SIZE_MIN, SIZE_MAX), range(SWAY_MIN, SWAY_MAX), random.nextFloat(),
            COLORS[c], COLORS[c + GREEN], COLORS[c + BLUE]);
    }

    private boolean avoided(float x) {
        for (int i = 0; i + 1 < avoid.size; i += PAIR) if (x >= avoid.items[i] && x <= avoid.items[i + 1]) return true;
        return false;
    }

    public void update(float dt) {
        if (target > 0) {
            spawnClock += dt;
            float interval = MEAN_LIFE * SPAWN_PACE / target;
            while (spawnClock >= interval) { spawnClock -= interval; if (leaves.count() < target) spawn(0); }
        }
        leaves.update(dt);
    }

    public void render(Batch batch) { if (leaves.count() > 0) leaves.render(batch, Shaders.whiteQuad()); }

    /** Room change, death and retry. */
    public void clear() { leaves.clear(); target = 0; }
}
