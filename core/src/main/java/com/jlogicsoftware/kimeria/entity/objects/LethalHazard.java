package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.math.Rectangle;

/** A world object that kills the hero on contact while it is dangerous; Level asks it every frame. */
public interface LethalHazard {
    /** True when {@code heroBody} touches the part of this object that is lethal right now. */
    boolean lethalTo(Rectangle heroBody);

    /** True when {@code area} is touched by the lethal part at any moment of its rhythm (a hero must never be unable to stand there). */
    boolean everLethalIn(Rectangle area);
}
