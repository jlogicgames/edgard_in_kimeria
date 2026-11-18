package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.Rectangle;

/**
 * The body a killed monster leaves (D101): harmless, in nobody's way, rocked by the hero passing, and finished by a weapon.
 * The sword and a thrown stone {@link #crumble()} it; a fireball (and a thrown torch, fire too) {@link #burnUp()}s it. A
 * weapon is neither spent nor stopped by a body, so a body never shields a monster or costs ammunition. The combat loops
 * test bodies apart from live enemies: a body counts as defeated ({@link Enemy#isDefeated()}).
 */
public interface Remains {
    /** True while a body lies here that a weapon can reach (not while it burns, not once it is gone). */
    boolean isRemains();

    /** The body's box in world space, written into {@code out} (returned). */
    Rectangle remainsInto(Rectangle out);

    /** The sword or a thrown stone: the body crumbles and is gone. */
    void crumble();

    /** A fireball or a thrown torch: the body burns up and is gone. */
    void burnUp();
}
