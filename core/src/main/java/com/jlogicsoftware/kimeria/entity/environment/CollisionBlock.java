package com.jlogicsoftware.kimeria.entity.environment;

import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Invisible collision volume spawned from the "Collisions" object layer. */
public class CollisionBlock extends Entity implements Collidable {
    private boolean platform;
    private boolean quickSand;
    private boolean wall;
    private boolean active = true;

    public CollisionBlock(float x, float y, float w, float h) {
        super(x, y, w, h);
    }

    public static CollisionBlock plain(float x, float y, float w, float h) {
        return new CollisionBlock(x, y, w, h);
    }

    public static CollisionBlock platform(float x, float y, float w, float h) {
        CollisionBlock b = new CollisionBlock(x, y, w, h);
        b.platform = true;
        return b;
    }

    public static CollisionBlock quickSand(float x, float y, float w, float h) {
        CollisionBlock b = new CollisionBlock(x, y, w, h);
        b.quickSand = true;
        return b;
    }

    public static CollisionBlock wall(float x, float y, float w, float h) {
        CollisionBlock b = new CollisionBlock(x, y, w, h);
        b.wall = true;
        return b;
    }

    @Override
    public void update(float dt) {
    }

    @Override
    public boolean isPlatform() {
        return platform;
    }

    @Override
    public boolean isQuickSand() {
        return quickSand;
    }

    @Override
    public boolean isWall() {
        return wall;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    protected void setWall(boolean wall) {
        this.wall = wall;
    }

    @Override
    public boolean isEscalator() {
        return false;
    }
}
