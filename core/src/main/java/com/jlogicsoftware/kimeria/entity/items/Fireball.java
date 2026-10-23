package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.world.Level;

/** A single paid charge. Terrain and the sword-only boss stop it; ordinary enemies take one hit. */
public final class Fireball extends Entity {
    private final Level level;
    private final float speed;
    private final TextureRegion sprite;
    private float life = 2;
    public Fireball(Level level, float x, float y, boolean right) {
        super(x, y, 10, 10);
        this.level = level;
        sprite = level.objectSprite("fireball");
        speed = right ? 280 : -280;
    }
    @Override public void update(float dt) {
        if (!level.player.canFight() || (life -= dt) <= 0) { removeFromParent(); return; }
        int steps = Math.max(1, (int) Math.ceil(Math.min(dt, .05f) / .008f));
        for (int i = 0; i < steps && !removed; i++) {
            position.x += speed * Math.min(dt, .05f) / steps;
            Rectangle bounds = new Rectangle(getX(), getY(), size.x, size.y);
            if (level.blocksObject(bounds, false)) { removeFromParent(); break; }
            for (var object : level.objects) if (object instanceof Enemy<?> enemy
                && !enemy.isDefeated() && enemy.hitboxOverlaps(bounds)) {
                if (!(enemy instanceof Boss)) enemy.collidedWithActor(true);
                removeFromParent();
                break;
            }
        }
    }
    @Override public void render(Batch batch) {
        batch.draw(sprite, speed > 0 ? getX() - 3 : getX() + 13, getY() - 3, speed > 0 ? 16 : -16, 16);
    }
}
