package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.FlyingBeetle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool sizes of {@link BeetleFx} (#327): the worst case the game can make is a few beetles winding up at once at the
 * assist's longest warning and bursting together, while others die under fireballs (their bodies' crumble and burn are
 * {@link RemainsFx}, measured in RemainsFxTest). The flying beetle (#330, D145) shares the pools: the same for three of
 * them, with specks along their dive lines and bursts in the air. The peak over many seeds and frame
 * rates must leave at least 25 % headroom, since a full pool silently drops the oldest particle.
 */
class BeetleFxTest {
    private static final int BEETLES = 3, SEEDS = 40;
    private static final float[] RATES = {30, 60, 144};
    private static final float SPECKS_START = Beetle.SPECKS_START, SPECKS_END = Beetle.SPECKS_END, STAGGER = .1f, TAIL = 1f, HEADROOM = 1.25f;

    private static int[] peaks(long seed, float fps) {
        BeetleFx fx = new BeetleFx(seed);
        float dt = 1f / fps, warning = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        float[] due = new float[BEETLES];
        boolean[] burst = new boolean[BEETLES];
        int soft = 0, hard = 0;
        for (float t = 0; t < warning + BEETLES * STAGGER + TAIL; t += dt) {
            for (int k = 0; k < BEETLES; k++) {
                float local = t - k * STAGGER;
                if (local < 0 || burst[k]) continue;
                if (local >= warning) {
                    burst[k] = true;
                    fx.burst(100 + k * 30, 200);
                    fx.poof(400 + k * 30, 192, 200);   // other beetles dying under fireballs at the same moment
                    continue;
                }
                due[k] += (SPECKS_START + (SPECKS_END - SPECKS_START) * local / warning) * dt;
                while (due[k] >= 1) { due[k] -= 1; fx.speck(100 + k * 30, 200); }
            }
            fx.update(dt);
            soft = Math.max(soft, fx.soft().count());
            hard = Math.max(hard, fx.hard().count());
        }
        return new int[]{soft, hard};
    }

    /**
     * Three flying beetles winding up at the assist's longest warning, three others dying; their bursts are half domes on a
     * surface, or ovals in the air.
     */
    private static int[] flyingPeaks(long seed, float fps, float surfaceBelow) {
        BeetleFx fx = new BeetleFx(seed);
        float dt = 1f / fps, warning = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        float[] due = new float[BEETLES], line = new float[BEETLES];
        boolean[] burst = new boolean[BEETLES];
        int soft = 0, hard = 0;
        for (float t = 0; t < warning + BEETLES * STAGGER + TAIL; t += dt) {
            for (int k = 0; k < BEETLES; k++) {
                float local = t - k * STAGGER, x = 100 + k * 30;
                if (local < 0 || burst[k]) continue;
                if (local >= warning) {
                    burst[k] = true;
                    fx.burstAt(x, 150, 150 + surfaceBelow);
                    fx.poof(400 + k * 30, 150, 200);
                    continue;
                }
                due[k] += (FlyingBeetle.SPECKS_START + (FlyingBeetle.SPECKS_END - FlyingBeetle.SPECKS_START) * local / warning) * dt;
                while (due[k] >= 1) { due[k] -= 1; fx.gather(x, 150); }
                line[k] += FlyingBeetle.LINE_SPECKS * dt;
                while (line[k] >= 1) { line[k] -= 1; fx.lineSpeck(x + 40, 152, x, 192); }
            }
            fx.update(dt);
            soft = Math.max(soft, fx.soft().count());
            hard = Math.max(hard, fx.hard().count());
        }
        return new int[]{soft, hard};
    }

    private static final float RIM_TOLERANCE = .005f;

    /** How far out of the ground beetle's dome a point is: 1 on its rim, less inside (D149). */
    private static float dome(float x, float y, float centreX, float floor) {
        float across = (x - centreX) / (BeetleFx.BLAST_WIDTH / 2), up = (floor - y) / BeetleFx.BLAST_HEIGHT;
        return across * across + up * up;
    }

    /** D149: every wind-up speck is born on the dome's rim, over the floor line (Y grows downward), and drawn in to the body. */
    @Test void everySpeckIsBornOnTheDomesRimAndDrawnInToTheBody() {
        float centreX = 200, floor = 150, bodyY = floor - BeetleFx.BODY_CENTRE_ABOVE_FLOOR;
        boolean top = false, leftFoot = false, rightFoot = false;
        for (long seed = 1; seed <= SEEDS; seed++) {
            BeetleFx fx = new BeetleFx(seed);
            for (int n = 0; n < 100; n++) {
                fx.clear();
                fx.speck(centreX, floor);
                KimeriaParticleSystem hard = fx.hard();
                float x = hard.x(0), y = hard.y(0);
                assertEquals(1, dome(x, y, centreX, floor), RIM_TOLERANCE, "on the rim: " + x + ", " + y);
                assertTrue(y <= floor + .01f, "over the floor line: " + y);
                top |= y < floor - BeetleFx.BLAST_HEIGHT + 1;
                leftFoot |= x < centreX - 30 && y > floor - 10;
                rightFoot |= x > centreX + 30 && y > floor - 10;
                float life = hard.life(0);
                for (int f = 0; f < 19; f++) {
                    hard.update(life / 20);
                    assertTrue(dome(hard.x(0), hard.y(0), centreX, floor) <= 1 + RIM_TOLERANCE, "flies inwards");
                }
                hard.update(life / 20 * .99f);
                assertEquals(centreX, hard.x(0), 1, "to the middle of the body");
                assertEquals(bodyY, hard.y(0), 1);
            }
            fx.clear();
            for (int n = 0; n < 100; n++) fx.speck(centreX, floor);
            fx.update(1f);
            assertEquals(0, fx.hard().count(), "they die out within their life");
        }
        assertTrue(top && leftFoot && rightFoot, "along the whole rim");
    }

    /** D149: every particle of the ground beetle's burst stays inside the dome that kills, over the floor line, all its life. */
    @Test void everyParticleOfTheGroundBurstStaysInsideTheDome() {
        float centreX = 200, floor = 150, halfWidth = BeetleFx.BLAST_WIDTH / 2;
        for (long seed = 1; seed <= SEEDS; seed++) {
            BeetleFx fx = new BeetleFx(seed);
            fx.burst(centreX, floor);
            assertEquals(2, count(fx.soft(), KimeriaParticleSystem.KIND_DOME), "the flash and its core lie on the floor");
            assertEquals(0, count(fx.soft(), KimeriaParticleSystem.KIND_GLOW), "no round flash reaching under it");
            while (fx.soft().count() + fx.hard().count() > 0) {
                for (KimeriaParticleSystem system : new KimeriaParticleSystem[]{fx.soft(), fx.hard()})
                    for (int i = 0; i < system.count(); i++) {
                        float x = system.x(i), y = system.y(i);
                        assertTrue(dome(x, y, centreX, floor) <= 1 + RIM_TOLERANCE && y <= floor + .01f,
                            "kind " + system.kind(i) + " outside the dome: " + x + ", " + y);
                        float half = system.drawSize(i) / 2;
                        if (system.kind(i) == KimeriaParticleSystem.KIND_DOME) {
                            assertEquals(floor, y, .01f, "flat edge on the floor");
                            // Drawn from y up, half a size to either side: its upper corners are the farthest out.
                            assertTrue(dome(x - half, y - half, centreX, floor) <= 1 && dome(x + half, y - half, centreX, floor) <= 1,
                                "the half glow is drawn inside the dome: " + half);
                        } else if (system == fx.hard()) {   // the steam is a soft puff round its centre; the squares are drawn whole
                            assertTrue(x - half >= centreX - halfWidth && x + half <= centreX + halfWidth, "x " + x);
                            assertTrue(y - half >= floor - BeetleFx.BLAST_HEIGHT && y + half <= floor + .01f, "y " + y);
                        }
                    }
                fx.update(1 / 240f);
            }
        }
    }

    /** D148: no flying particle ends or travels outside the shape that kills. */
    @Test void everyParticleOfTheFlyingBurstStaysInsideTheShapeThatKills() {
        float centreX = 200, centreY = 150;
        for (float below : new float[]{BeetleFx.BODY_CENTRE_ABOVE_FLOOR, 14, BeetleFx.BLAST_AIR_HALF_HEIGHT}) {
            float bottom = centreY + below;
            boolean dome = BeetleFx.onSurface(centreY, bottom);
            float origin = dome ? bottom : centreY, high = dome ? BeetleFx.BLAST_HEIGHT : BeetleFx.BLAST_AIR_HALF_HEIGHT;
            for (long seed = 1; seed <= SEEDS; seed++) {
                BeetleFx fx = new BeetleFx(seed);
                fx.burstAt(centreX, centreY, bottom);
                for (int f = 0; f < 60; f++) {
                    KimeriaParticleSystem hard = fx.hard();
                    for (int i = 0; i < hard.count(); i++) {
                        float across = (hard.x(i) - centreX) / (BeetleFx.BLAST_WIDTH / 2), up = (hard.y(i) - origin) / high;
                        assertTrue(across * across + up * up <= 1.001f, "outside the shape under " + below + ": " + hard.x(i) + ", " + hard.y(i));
                    }
                    fx.update(1 / 60f);
                }
            }
        }
    }

    @Test void theBurstInTheAirStaysInsideItsBox() {
        float centreX = 200, centreY = 150;
        for (float below : new float[]{BeetleFx.BLAST_AIR_HALF_HEIGHT, 14, BeetleFx.BODY_CENTRE_ABOVE_FLOOR}) {   // over open air, and cut by a surface
            float bottom = centreY + below, top = centreY - BeetleFx.BLAST_AIR_HALF_HEIGHT;
            for (long seed = 1; seed <= SEEDS; seed++) {
                BeetleFx fx = new BeetleFx(seed);
                fx.burstAt(centreX, centreY, bottom);
                for (int f = 0; f < 60; f++) {
                    KimeriaParticleSystem hard = fx.hard();
                    for (int i = 0; i < hard.count(); i++) {
                        assertTrue(hard.x(i) >= centreX - BeetleFx.BLAST_WIDTH / 2 - .01f && hard.x(i) <= centreX + BeetleFx.BLAST_WIDTH / 2 + .01f, "x " + hard.x(i));
                        assertTrue(hard.y(i) >= top - .01f && hard.y(i) <= bottom + .01f, "y " + hard.y(i) + " in " + top + ".." + bottom);
                    }
                    fx.update(1 / 60f);
                }
            }
        }
    }

    /** D147: off a surface the burst is a half dome: every particle flies up from it, and none is drawn under it. */
    @Test void aBurstOnASurfaceFliesOnlyUpAndAwayFromIt() {
        float centreX = 200, centreY = 150;
        for (float below : new float[]{BeetleFx.BODY_CENTRE_ABOVE_FLOOR, 14, BeetleFx.BLAST_AIR_HALF_HEIGHT - 1}) {
            float floor = centreY + below;
            for (long seed = 1; seed <= SEEDS; seed++) {
                BeetleFx fx = new BeetleFx(seed);
                fx.burstAt(centreX, centreY, floor);
                assertEquals(2, count(fx.soft(), KimeriaParticleSystem.KIND_DOME), "the flash and its core lie on the surface");
                assertEquals(0, count(fx.soft(), KimeriaParticleSystem.KIND_GLOW), "no round flash reaching into it");
                for (KimeriaParticleSystem system : new KimeriaParticleSystem[]{fx.soft(), fx.hard()})
                    for (int i = 0; i < system.count(); i++) assertTrue(system.y(i) <= floor + .01f, "born over the surface: " + system.y(i));
                float[] before = new float[BeetleFx.HARD_CAPACITY];
                for (int i = 0; i < fx.hard().count(); i++) before[i] = fx.hard().y(i);
                for (int f = 0; f < 60; f++) {
                    for (KimeriaParticleSystem system : new KimeriaParticleSystem[]{fx.soft(), fx.hard()})
                        for (int i = 0; i < system.count(); i++) {
                            float lowest = system.kind(i) == KimeriaParticleSystem.KIND_DOME ? system.y(i) : system.y(i) + system.drawSize(i) / 2;
                            assertTrue(lowest <= floor + .01f, "nothing drawn under the surface: kind " + system.kind(i) + " reaches " + lowest);
                        }
                    fx.update(1 / 600f);
                    int live = fx.hard().count();
                    if (f == 0) for (int i = 0; i < live; i++) assertTrue(fx.hard().y(i) <= before[i] + .001f, "flies up, not down");
                }
            }
        }
    }

    /** D147: the wind-up's energy comes in from a ring all round the monster. */
    @Test void theGatheringSpecksComeFromARingRoundTheMonsterAndEndAtIt() {
        float centreX = 200, centreY = 150;
        BeetleFx fx = new BeetleFx(1);
        boolean above = false, below = false, left = false, right = false;
        for (int n = 0; n < 100; n++) {
            fx.clear();
            fx.gather(centreX, centreY);
            KimeriaParticleSystem hard = fx.hard();
            float dx = hard.x(0) - centreX, dy = hard.y(0) - centreY, from = (float) Math.sqrt(dx * dx + dy * dy);
            assertTrue(from >= BeetleFx.GATHER_RADIUS_MIN - .01f && from <= BeetleFx.GATHER_RADIUS_MAX + .01f, "born on the ring: " + from);
            above |= dy < -20; below |= dy > 20; left |= dx < -20; right |= dx > 20;
            hard.update(hard.life(0) * .99f);
            assertEquals(centreX, hard.x(0), 1, "drawn in to the middle");
            assertEquals(centreY, hard.y(0), 1);
        }
        assertTrue(above && below && left && right, "all round it");
    }

    private static int count(KimeriaParticleSystem system, int kind) {
        int n = 0;
        for (int i = 0; i < system.count(); i++) if (system.kind(i) == kind) n++;
        return n;
    }

    @Test void theFlyingWorstCaseLeavesHeadroomInBothPools() {
        int soft = 0, hard = 0;
        for (float below : new float[]{BeetleFx.BODY_CENTRE_ABOVE_FLOOR, BeetleFx.BLAST_AIR_HALF_HEIGHT})
            for (float fps : RATES) for (long seed = 1; seed <= SEEDS; seed++) {
                int[] p = flyingPeaks(seed, fps, below);
                soft = Math.max(soft, p[0]);
                hard = Math.max(hard, p[1]);
            }
        System.out.println("BeetleFx flying peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= BeetleFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= BeetleFx.HARD_CAPACITY, "hard peak " + hard);
    }

    @Test void theWorstCaseLeavesHeadroomInBothPools() {
        int soft = 0, hard = 0;
        for (float fps : RATES) for (long seed = 1; seed <= SEEDS; seed++) {
            int[] p = peaks(seed, fps);
            soft = Math.max(soft, p[0]);
            hard = Math.max(hard, p[1]);
        }
        System.out.println("BeetleFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= BeetleFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= BeetleFx.HARD_CAPACITY, "hard peak " + hard);
    }
}
