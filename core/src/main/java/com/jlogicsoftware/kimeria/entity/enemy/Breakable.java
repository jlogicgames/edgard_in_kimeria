package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.Rectangle;

/**
 * Something in flight that every weapon breaks and flies on through (D106): the forest snail's gob. The sword's live box
 * and a thrown stone {@link #breakApart(boolean) break it apart}, a fireball and a thrown torch burn it away; none of them is
 * spent or stopped by it, so a gob never costs the hero a charge. The combat loops test it apart from live enemies and bodies;
 * the thing that owns it (the snail) stays an ordinary enemy.
 */
public interface Breakable {
    /** True while there is something in flight that a weapon can reach. */
    boolean isBreakable();

    /** Its box in world space, written into {@code out} (returned). */
    Rectangle breakableInto(Rectangle out);

    /** A weapon reaches it: {@code fire} for a fireball or a thrown torch, else the sword or a thrown stone. It is gone. */
    void breakApart(boolean fire);
}
