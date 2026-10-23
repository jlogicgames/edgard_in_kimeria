package com.jlogicsoftware.kimeria.entity.items;

import com.jlogicsoftware.kimeria.entity.Entity;

/** Invisible zone that, when the player interacts on it, activates matching {@link Actionable}s. */
public class Trigger extends Entity {
    public final String targetId;
    public final int id;

    public Trigger(float x, float y, float w, float h, String targetId) {
        this(0, x, y, w, h, targetId);
    }

    public Trigger(int id, float x, float y, float w, float h, String targetId) {
        super(x, y, w, h);
        this.id = id;
        this.targetId = targetId;
    }

    @Override
    public void update(float dt) {
    }
}
