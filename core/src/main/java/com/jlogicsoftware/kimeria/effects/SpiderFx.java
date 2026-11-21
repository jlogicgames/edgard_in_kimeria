package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The forest spider's effects (#328, D102): the silk glints of its wind-up, the silk line it leaves in the air when it leaps,
 * the snap of its bite and its death. The end of the body it leaves (D101) is {@link RemainsFx}. One per {@code Level}, like
 * {@link BeetleFx}; two {@link KimeriaParticleSystem}s with their own seeded streams. {@link #soft()} holds the bite's fang
 * glints ({@code KIND_GLOW}, additive) and the dust and death poof ({@code KIND_SMOKE}), drawn with {@link SoftDot};
 * {@link #hard()} holds the silk glints ({@code KIND_FLAME}, opaque squares that shrink out), the silk line and the leg flecks
 * ({@code KIND_GRIT}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>The silk glints run along the floor line from the spider's fangs and gather on the spot its bite will land, so the reach
 * is read before it happens (D7, D102), as the beetle's specks show its blast. Colours: white, the pale and grey of the smoke
 * ({@code d8d6e8}, {@code 9a97ad}), a dust brown, the spider's crimson {@code 86101f} and dusty-rose leg {@code b07c8c} for the
 * death's flecks (the danger family, #236), and its body's live palette for the body's crumble (D117) ({@link #SPIDER_CHIPS}). No reserved hue. Cosmetic: nothing reads it back. World Y grows downward: a rising particle ends at
 * a smaller Y. Particles advance only in {@link #update}, so pause freezes them.
 */
public final class SpiderFx {
    /**
     * Measured peaks (SpiderFxTest: three spiders winding up 0.1 s apart at the assist's 0.75 s warning, leaping and biting,
     * while three others die, 40 seeds, 30/60/144 fps): 50 soft, 83 hard; with at least 25 % headroom. A full pool only drops
     * the oldest.
     */
    public static final int SOFT_CAPACITY = 64, HARD_CAPACITY = 104;
    private static final long HARD_SEED_MIX = 0x9E3779B97F4A7C15L;

    private static final float WHITE = 1f;
    private static final int RED = 0, GREEN = 1, BLUE = 2;
    private static final float[] WHITE_RGB = {WHITE, WHITE, WHITE};
    private static final float[] PALE = {.847f, .839f, .910f}, GREY = {.604f, .592f, .678f}, DUST = {.420f, .353f, .227f};
    private static final float[] CRIMSON = {.525f, .063f, .122f}, LEG = {.690f, .486f, .549f};
    private static final float[] BODY_ABDOMEN = {.525f, .063f, .122f}, BODY_HEAD = {.416f, .055f, .118f};
    private static final float[] BODY_LEG = {.690f, .486f, .549f}, BODY_FANG = {.910f, .878f, .800f};
    /** The spider body's crumble chips (D101, {@link RemainsFx#crumble}): spider-body.grid's abdomen, legs, head and fangs. */
    public static final float[][] SPIDER_CHIPS = {BODY_ABDOMEN, BODY_LEG, BODY_HEAD, BODY_FANG};

    // Wind-up silk: 1 px glints born between the fangs and the landing spot on the floor line, running to the spot.
    private static final float SILK_SIZE = 1, SILK_LIFE_MIN = .22f, SILK_LIFE_MAX = .32f, SILK_WHITE_CHANCE = .5f;
    /** The glints land within this many px of the spot, so they gather where the bite will land. */
    private static final float SILK_SPOT_JITTER = 2;
    /** Above the floor line, so a glint sits on the floor's top row and not inside it. */
    private static final float SILK_ABOVE_FLOOR = 1;

    // Leap: the silk line left in the air, one pale dot a frame that fades where it was laid.
    private static final float LINE_SIZE = 1, LINE_LIFE = .3f;

    // Bite (hit or miss, D93): two white fang glints and a puff of floor dust.
    private static final int FANG_GLINTS = 2, BITE_DUST = 4;
    private static final float FANG_SIZE = 7, FANG_LIFE = .1f, FANG_SPREAD = 2, FANG_ABOVE_FLOOR = 4;
    private static final float BITE_DUST_HALF_WIDTH = 6, BITE_DUST_LOW = 1, BITE_DUST_HIGH = 3, BITE_DUST_RISE_MIN = 3, BITE_DUST_RISE_MAX = 6;
    private static final float BITE_DUST_LIFE_MIN = .25f, BITE_DUST_LIFE_MAX = .35f, BITE_DUST_SIZE_MIN = 3, BITE_DUST_SIZE_MAX = 5;

    // Death (a fireball, a stone, the sword or a stomp): a pale poof in a ring and a few leg flecks to the floor.
    private static final int POOF = 12, FLECKS = 4;
    /** One puff in this many is grey, the rest pale: the poof reads lighter than the fireball's ring behind it. */
    private static final int POOF_GREY_EVERY = 3;
    private static final float POOF_DIST_MIN = 10, POOF_DIST_MAX = 16, POOF_LIFE_MIN = .4f, POOF_LIFE_MAX = .55f;
    private static final float POOF_SIZE_MIN = 5, POOF_SIZE_MAX = 8, POOF_ANGLE_JITTER = 15, CIRCLE = 360;
    private static final float FLECK_SPREAD = 8, FLECK_DRIFT = 10, FLECK_LIFE = .45f, FLECK_SIZE = 1, FLECK_FLOOR_INSET = 1;

    // The silk drop (#483, D103): the wind-up glints run down the drop column to the thread's end; specks shed from the thread
    // as it pays out; a snapped thread sheds a few specks that drift down.
    private static final float DROP_SPOT_JITTER = 1, SHED_DRIFT = 3, SHED_FALL = 10, SHED_LIFE = .35f;
    private static final int SNAP_SPECKS = 5;
    private static final float SNAP_FALL_MIN = 10, SNAP_FALL_MAX = 22, SNAP_LIFE_MIN = .35f, SNAP_LIFE_MAX = .55f, SNAP_DRIFT = 4;

    private final KimeriaParticleSystem soft, hard;

    public SpiderFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** Fang glints, dust and poof ({@link SoftDot}). */
    public KimeriaParticleSystem soft() { return soft; }
    /** Silk glints, the silk line and flecks ({@link Shaders#whiteQuad()}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /**
     * One wind-up silk glint for a spider whose fangs are at {@code fangX}, its bite to land at {@code spotX}, standing on
     * {@code floorY}: born on the floor line between the two and running to the spot.
     */
    public void silk(float fangX, float spotX, float floorY) {
        RandomXS128 random = hard.random();
        float y = floorY - SILK_ABOVE_FLOOR;
        float x = fangX + random.nextFloat() * (spotX - fangX);
        float[] c = random.nextFloat() < SILK_WHITE_CHANCE ? WHITE_RGB : PALE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, spotX + range(random, -SILK_SPOT_JITTER, SILK_SPOT_JITTER), y,
            range(random, SILK_LIFE_MIN, SILK_LIFE_MAX), SILK_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /**
     * One drop wind-up glint (D103) for a spider hanging at the lip with its fangs at ({@code x}, {@code fromY}): born in the drop
     * column between the fangs and {@code spotY}, the thread's end, and running down to it, as the pounce's glints run along the floor.
     */
    public void silkDown(float x, float fromY, float spotY) {
        RandomXS128 random = hard.random();
        float y = fromY + random.nextFloat() * (spotY - fromY);
        float[] c = random.nextFloat() < SILK_WHITE_CHANCE ? WHITE_RGB : PALE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, x, spotY + range(random, -DROP_SPOT_JITTER, DROP_SPOT_JITTER),
            range(random, SILK_LIFE_MIN, SILK_LIFE_MAX), SILK_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /** One speck shed by the thread at ({@code x}, {@code y}) as it pays out in the drop: it drifts aside and sinks a little. */
    public void shed(float x, float y) {
        RandomXS128 random = hard.random();
        hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x + range(random, -SHED_DRIFT, SHED_DRIFT), y + SHED_FALL, SHED_LIFE,
            LINE_SIZE, PALE[RED], PALE[GREEN], PALE[BLUE]);
    }

    /** The thread from {@code topY} to {@code bottomY} at {@code x} snaps when its spider is killed: a few specks drift down. */
    public void snap(float x, float topY, float bottomY) {
        RandomXS128 random = hard.random();
        for (int i = 0; i < SNAP_SPECKS; i++) {
            float y = topY + (i + random.nextFloat()) / SNAP_SPECKS * (bottomY - topY);
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x + range(random, -SNAP_DRIFT, SNAP_DRIFT), y + range(random, SNAP_FALL_MIN, SNAP_FALL_MAX),
                range(random, SNAP_LIFE_MIN, SNAP_LIFE_MAX), LINE_SIZE, PALE[RED], PALE[GREEN], PALE[BLUE]);
        }
    }

    /** One dot of the silk line a leaping spider leaves at ({@code x}, {@code y}); it stays where it was laid and fades. */
    public void line(float x, float y) {
        hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x, y, LINE_LIFE, LINE_SIZE, PALE[RED], PALE[GREEN], PALE[BLUE]);
    }

    /** The bite at {@code fangX} on {@code floorY}, hit or miss: two white fang glints and a puff of dust at its feet. */
    public void bite(float fangX, float floorY) {
        float y = floorY - FANG_ABOVE_FLOOR;
        for (int i = 0; i < FANG_GLINTS; i++) {
            float x = fangX + (i == 0 ? -FANG_SPREAD : FANG_SPREAD);
            soft.spawn(KimeriaParticleSystem.KIND_GLOW, x, y, x, y, FANG_LIFE, FANG_SIZE, WHITE, WHITE, WHITE);
        }
        RandomXS128 random = soft.random();
        for (int i = 0; i < BITE_DUST; i++) {
            float x = fangX + range(random, -BITE_DUST_HALF_WIDTH, BITE_DUST_HALF_WIDTH), dy = floorY - range(random, BITE_DUST_LOW, BITE_DUST_HIGH);
            float[] c = (i & 1) == 0 ? DUST : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, dy, x, dy - range(random, BITE_DUST_RISE_MIN, BITE_DUST_RISE_MAX),
                range(random, BITE_DUST_LIFE_MIN, BITE_DUST_LIFE_MAX), range(random, BITE_DUST_SIZE_MIN, BITE_DUST_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The death of a spider centred on ({@code centreX}, {@code centreY}) standing on {@code floorY}: a pale poof and flecks. */
    public void poof(float centreX, float centreY, float floorY) {
        RandomXS128 random = soft.random();
        for (int i = 0; i < POOF; i++) {
            float angle = i * (CIRCLE / (float) POOF) + range(random, -POOF_ANGLE_JITTER, POOF_ANGLE_JITTER);
            float dist = range(random, POOF_DIST_MIN, POOF_DIST_MAX);
            float[] c = i % POOF_GREY_EVERY == 0 ? GREY : PALE;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, centreX, centreY, centreX + MathUtils.cosDeg(angle) * dist,
                centreY + MathUtils.sinDeg(angle) * dist, range(random, POOF_LIFE_MIN, POOF_LIFE_MAX),
                range(random, POOF_SIZE_MIN, POOF_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 fleckRandom = hard.random();
        for (int i = 0; i < FLECKS; i++) {
            float x = centreX + range(fleckRandom, -FLECK_SPREAD, FLECK_SPREAD);
            float[] c = (i & 1) == 0 ? LEG : CRIMSON;
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, centreY, x + range(fleckRandom, -FLECK_DRIFT, FLECK_DRIFT), floorY - FLECK_FLOOR_INSET,
                FLECK_LIFE, FLECK_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
    }

    public void update(float dt) {
        soft.update(dt);
        hard.update(dt);
    }

    /** Glints, dust and poof, then the silk and flecks on top; the shared regions are fetched only when something is alive. */
    public void render(Batch batch) {
        if (soft.count() > 0) soft.render(batch, SoftDot.region());
        if (hard.count() > 0) hard.render(batch, Shaders.whiteQuad());
    }

    /** Room change, retry and level restart: nothing carries over. */
    public void clear() {
        soft.clear();
        hard.clear();
    }
}
