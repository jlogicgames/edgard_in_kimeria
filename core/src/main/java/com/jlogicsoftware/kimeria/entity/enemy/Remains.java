package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.world.Level;

/**
 * The body a killed monster leaves (D101, amended by D119): harmless, in nobody's way, rocked by any actor passing, and finished
 * by the sword or a stomp ({@link #crumble()}) or a fireball or thrown torch ({@link #burnUp()}), which fly on. A thrown stone and every
 * other thrown object pass straight through it: not stopped, not spent, and it does not crumble or burn. The combat loops test bodies
 * apart from live enemies: a body counts as defeated ({@link Enemy#isDefeated()}).
 */
public interface Remains {
    /** True while a body lies here that a weapon can reach (not while it burns, not once it is gone). */
    boolean isRemains();

    /** The body's box in world space, written into {@code out} (returned). */
    Rectangle remainsInto(Rectangle out);

    /** The sword or a stomp: the body crumbles and is gone. */
    void crumble();

    /** A fireball or a thrown torch (both fly on): the body burns up and is gone. */
    void burnUp();

    /**
     * D119: the first live actor other than {@code self} (a monster, the boss) that is moving sideways and whose body touches
     * {@code near}, or null. It rocks a body as the hero does. {@code scratch} receives each candidate's box; no allocation.
     */
    static Enemy<?> moverNear(Level level, Enemy<?> self, Rectangle near, Rectangle scratch) {
        if (level == null) return null;
        for (int i = 0; i < level.objects.size(); i++)
            if (level.objects.get(i) instanceof Enemy<?> other && other != self && !other.isDefeated() && other.velocity.x != 0
                && other.hitboxBoundsInto(scratch).overlaps(near)) return other;
        return null;
    }
}
