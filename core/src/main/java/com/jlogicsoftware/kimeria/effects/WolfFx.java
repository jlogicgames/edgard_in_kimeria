package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The forest wolf's effects (#331, D113): the pale breath streaks of its wind-up along the whole line its jaws will cover, the
 * dust of its skid and its bite, the two jaw glints of the bite and its death. The end of the body it leaves (D101) is
 * {@link RemainsFx}. One per {@code Level}, like {@link SpiderFx}; two {@link KimeriaParticleSystem}s with their own seeded
 * streams. {@link #soft()} holds the bite's jaw glints ({@code KIND_GLOW}, additive) and the dust and death poof
 * ({@code KIND_SMOKE}), drawn with {@link SoftDot}; {@link #hard()} holds the breath streaks ({@code KIND_FLAME}, opaque squares
 * that shrink out) and the fur flecks ({@code KIND_GRIT}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>The breath streaks run along the floor line from the wolf's jaws to where its lunge will end, so the reach is read before it
 * happens (D7, D113), as the spider's silk glints show its bite. Colours: white, the pale and grey of the smoke ({@code d8d6e8},
 * {@code 9a97ad}), a dust brown, the wolf's crimson {@code 86101f} and dusty-rose leg {@code b07c8c} for the death's fur flecks
 * (the danger family, #236), and its body's muted palette for the body's crumble ({@link #WOLF_CHIPS}). No reserved hue.
 * Cosmetic: nothing reads it back. World Y grows downward: a rising particle ends at a smaller Y. Particles advance only in
 * {@link #update}, so pause freezes them.
 */
public final class WolfFx {
    /**
     * Measured peaks (WolfFxTest: three wolves braking and winding up 0.1 s apart at the assist's 0.75 s warning, then biting, while
     * three others die, 40 seeds, 30/60/144 fps): 50 soft, 55 hard; with at least 25 % headroom. A full pool only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 64, HARD_CAPACITY = 72;
    private static final long HARD_SEED_MIX = 0xC2B2AE3D27D4EB4FL;

    private static final float WHITE = 1f;
    private static final int RED = 0, GREEN = 1, BLUE = 2;
    private static final float[] WHITE_RGB = {WHITE, WHITE, WHITE};
    private static final float[] PALE = {.847f, .839f, .910f}, GREY = {.604f, .592f, .678f}, DUST = {.420f, .353f, .227f};
    private static final float[] CRIMSON = {.525f, .063f, .122f}, LEG = {.690f, .486f, .549f};
    private static final float[] BODY_COAT = {.176f, .063f, .004f}, BODY_FLANK = {.184f, .071f, .008f};
    private static final float[] BODY_BELLY = {.204f, .106f, .047f}, BODY_SHADE = {.145f, .039f, .000f};
    /** The wolf body's crumble chips (D101, {@link RemainsFx#crumble}): wolf-body.grid's coat, flank, belly and shade. */
    public static final float[][] WOLF_CHIPS = {BODY_COAT, BODY_BELLY, BODY_FLANK, BODY_SHADE};

    // Wind-up breath: 1 px streaks born between the jaws and the line's end on the floor line, running to the end.
    private static final float BREATH_SIZE = 1, BREATH_LIFE_MIN = .22f, BREATH_LIFE_MAX = .32f, BREATH_WHITE_CHANCE = .5f;
    /** The streaks land within this many px of the line's end, so they gather where the jaws will stop. */
    private static final float BREATH_END_JITTER = 2;
    /** Above the floor line, so a streak sits on the floor's top row and not inside it. */
    private static final float BREATH_ABOVE_FLOOR = 1;

    // Skid and bite dust: a puff of floor dust, thrown back the way the wolf was going.
    private static final int SKID_DUST = 2, BITE_DUST = 4;
    private static final float SKID_HALF_WIDTH = 5, SKID_THROW = 6, SKID_LOW = 1, SKID_HIGH = 3, SKID_RISE_MIN = 2, SKID_RISE_MAX = 5;
    private static final float SKID_LIFE_MIN = .2f, SKID_LIFE_MAX = .3f, SKID_SIZE_MIN = 2, SKID_SIZE_MAX = 4;

    // Bite (hit or miss, D93): two white jaw glints and a puff of dust at its feet.
    private static final int JAW_GLINTS = 2;
    private static final float JAW_SIZE = 7, JAW_LIFE = .1f, JAW_SPREAD = 3, JAW_ABOVE_FLOOR = 11;
    private static final float BITE_DUST_HALF_WIDTH = 8, BITE_DUST_LOW = 1, BITE_DUST_HIGH = 3, BITE_DUST_RISE_MIN = 3, BITE_DUST_RISE_MAX = 6;
    private static final float BITE_DUST_LIFE_MIN = .25f, BITE_DUST_LIFE_MAX = .35f, BITE_DUST_SIZE_MIN = 3, BITE_DUST_SIZE_MAX = 5;

    // Death (a fireball, a stone, the sword or a stomp): a pale poof in a ring and a few fur flecks to the floor.
    private static final int POOF = 12, FLECKS = 4;
    /** One puff in this many is grey, the rest pale: the poof reads lighter than the fireball's ring behind it. */
    private static final int POOF_GREY_EVERY = 3;
    private static final float POOF_DIST_MIN = 10, POOF_DIST_MAX = 16, POOF_LIFE_MIN = .4f, POOF_LIFE_MAX = .55f;
    private static final float POOF_SIZE_MIN = 5, POOF_SIZE_MAX = 8, POOF_ANGLE_JITTER = 15, CIRCLE = 360;
    private static final float FLECK_SPREAD = 12, FLECK_DRIFT = 10, FLECK_LIFE = .45f, FLECK_SIZE = 1, FLECK_FLOOR_INSET = 1;

    private final KimeriaParticleSystem soft, hard;

    public WolfFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** Jaw glints, dust and poof ({@link SoftDot}). */
    public KimeriaParticleSystem soft() { return soft; }
    /** Breath streaks and flecks ({@link Shaders#whiteQuad()}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /**
     * One wind-up breath streak for a wolf whose jaws are at {@code jawX}, its lunge to end with the jaws at {@code endX}, standing
     * on {@code floorY}: born on the floor line between the two and running to the end.
     */
    public void breath(float jawX, float endX, float floorY) {
        RandomXS128 random = hard.random();
        float y = floorY - BREATH_ABOVE_FLOOR;
        float x = jawX + random.nextFloat() * (endX - jawX);
        float[] c = random.nextFloat() < BREATH_WHITE_CHANCE ? WHITE_RGB : PALE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, endX + range(random, -BREATH_END_JITTER, BREATH_END_JITTER), y,
            range(random, BREATH_LIFE_MIN, BREATH_LIFE_MAX), BREATH_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /** A puff of dust under a braking wolf at {@code x} on {@code floorY}, thrown back the way it was going ({@code direction}, 1 for right). */
    public void skid(float x, float floorY, float direction) {
        RandomXS128 random = soft.random();
        for (int i = 0; i < SKID_DUST; i++) {
            float px = x - direction * range(random, 0, SKID_HALF_WIDTH), y = floorY - range(random, SKID_LOW, SKID_HIGH);
            float[] c = (i & 1) == 0 ? DUST : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, px, y, px - direction * range(random, 0, SKID_THROW), y - range(random, SKID_RISE_MIN, SKID_RISE_MAX),
                range(random, SKID_LIFE_MIN, SKID_LIFE_MAX), range(random, SKID_SIZE_MIN, SKID_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The bite at {@code jawX} on {@code floorY}, hit or miss: two white jaw glints and a puff of dust at its feet. */
    public void bite(float jawX, float floorY) {
        float y = floorY - JAW_ABOVE_FLOOR;
        for (int i = 0; i < JAW_GLINTS; i++) {
            float x = jawX + (i == 0 ? -JAW_SPREAD : JAW_SPREAD);
            soft.spawn(KimeriaParticleSystem.KIND_GLOW, x, y, x, y, JAW_LIFE, JAW_SIZE, WHITE, WHITE, WHITE);
        }
        RandomXS128 random = soft.random();
        for (int i = 0; i < BITE_DUST; i++) {
            float x = jawX + range(random, -BITE_DUST_HALF_WIDTH, BITE_DUST_HALF_WIDTH), dy = floorY - range(random, BITE_DUST_LOW, BITE_DUST_HIGH);
            float[] c = (i & 1) == 0 ? DUST : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, dy, x, dy - range(random, BITE_DUST_RISE_MIN, BITE_DUST_RISE_MAX),
                range(random, BITE_DUST_LIFE_MIN, BITE_DUST_LIFE_MAX), range(random, BITE_DUST_SIZE_MIN, BITE_DUST_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The death of a wolf centred on ({@code centreX}, {@code centreY}) standing on {@code floorY}: a pale poof and fur flecks. */
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

    /** Glints, dust and poof, then the breath and flecks on top; the shared regions are fetched only when something is alive. */
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
