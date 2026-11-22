package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.math.RandomXS128;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * The lance root (#562, D129): the forest's clocked column. A burl of bark on the floor line whose crimson heartwood pulses;
 * on the level clock it shows the shared warning (the lips lift and the bone tip shows, soil specks hop), then thrusts a
 * sharpened root straight up for the last {@code jet} seconds of the period and sinks it back. Everything but the look is
 * {@link EmberFissure}'s (D91): the clock, the 8 px lethal column, the load rules and the free counters, so the two share one
 * element, {@code clocked-column}. Only the sparks differ: soil specks in the warning and bark chips as the root sinks
 * (D93), {@code KIND_GRIT} of its own {@link KimeriaParticleSystem}.
 */
public class LanceRoot extends EmberFissure {
    /** Soil and bark colours from the grid's B 8a5a30, b 4a2e1c and bone t efe6c4, in turn (red, green, blue channels). */
    private static final float[] CHIP_R = {0.541f, 0.290f, 0.937f}, CHIP_G = {0.353f, 0.180f, 0.902f}, CHIP_B = {0.188f, 0.110f, 0.769f};
    public static final float HOP_PERIOD = 0.06f, HOP_LIFE = 0.35f, HOP_MIN_PX = 4f, HOP_MAX_PX = 10f, HOP_SPREAD_PX = 5f;
    public static final int CHIPS = 6;
    /** The specks start this far over the floor line, at the burl's lips. */
    public static final float LIP_RISE_PX = 2f;
    public static final float CHIP_LIFE = 0.5f, CHIP_DROP_PX = 28f, CHIP_SPREAD_PX = 10f, CHIP_PX = 1f;
    private static final float TWO = 2f;
    private int emittedChips;

    public LanceRoot(Assets assets, float x, float y, TrapWarnings warnings, float period, float phase, float jet, float height) {
        super(assets, x, y, warnings, period, phase, jet, height, SpriteSheets.TRAP_LANCE_ROOT, SpriteSheets.TRAP_LANCE_SHAFT);
    }

    /** Reads and checks the map properties exactly as the ember fissure's ({@link EmberFissure#fromMap}). */
    public static LanceRoot fromMap(Assets assets, TiledObject o, TrapWarnings warnings, Iterable<TiledObject> collisions, String label) {
        return fromMap("LanceRoot", o, collisions, label,
            (period, phase, jet, height) -> new LanceRoot(assets, o.x, o.y, warnings, period, phase, jet, height));
    }

    @Override
    protected void riseSparks(float dt) {
        RandomXS128 random = sparks().random();
        float cx = centerX(), lip = floorY() - LIP_RISE_PX;
        riseClock += dt;
        while (riseClock >= HOP_PERIOD) {
            riseClock -= HOP_PERIOD;
            float reach = HOP_MIN_PX + random.nextFloat() * (HOP_MAX_PX - HOP_MIN_PX);
            chip(cx, lip, cx + (random.nextFloat() * TWO - 1f) * HOP_SPREAD_PX, lip - reach, HOP_LIFE);
        }
    }

    /** D93's visible consequence of every thrust, hit or miss: bark chips fall from the upper column as the root sinks. */
    @Override
    protected void fallSparks() {
        RandomXS128 random = sparks().random();
        float cx = centerX();
        for (int i = 0; i < CHIPS; i++) {
            float y0 = floorY() - columnHeight() * (FALL_FROM_FRACTION + random.nextFloat() * (1f - FALL_FROM_FRACTION)), side = random.nextFloat() * TWO - 1f;
            chip(cx + side * JET_WIDTH / TWO, y0, cx + side * CHIP_SPREAD_PX, y0 + CHIP_DROP_PX, CHIP_LIFE);
        }
    }

    private void chip(float x0, float y0, float x1, float y1, float life) {
        int c = emittedChips++ % CHIP_R.length;
        sparks().spawn(KimeriaParticleSystem.KIND_GRIT, x0, y0, x1, y1, life, CHIP_PX, CHIP_R[c], CHIP_G[c], CHIP_B[c]);
    }
}
