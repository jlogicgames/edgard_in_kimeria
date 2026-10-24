package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.physics.ProjectileStep;

/** A single paid charge. Terrain blocks it; enemies and the forest boss take one hit. */
public final class Fireball extends Entity {
    public static final float BODY_SIZE = 10;
    private static final float SPEED = 280, LIFETIME_SECONDS = 2;
    private static final float SPRITE_PADDING_X = (SpriteSheets.FIREBALL.width() - BODY_SIZE) / 2f;
    private static final float SPRITE_PADDING_Y = (SpriteSheets.FIREBALL.height() - BODY_SIZE) / 2f;

    private final Level level;
    private final float speed;
    private final TextureRegion sprite;
    private float life = LIFETIME_SECONDS;
    public Fireball(Level level, float x, float y, boolean right) {
        super(x, y, BODY_SIZE, BODY_SIZE);
        this.level = level;
        sprite = level.objectSprite(SpriteSheets.FIREBALL);
        speed = right ? SPEED : -SPEED;
    }
    @Override public void update(float dt) {
        if (!level.player.canFight() || (life -= dt) <= 0) { removeFromParent(); return; }
        int steps = ProjectileStep.count(dt);
        for (int i = 0; i < steps && !removed; i++) {
            position.x += speed * ProjectileStep.frameSeconds(dt) / steps;
            Rectangle bounds = new Rectangle(getX(), getY(), size.x, size.y);
            if (level.blocksObject(bounds, false)) { removeFromParent(); break; }
            for (var object : level.objects) if (object instanceof Enemy<?> enemy
                && !enemy.isDefeated() && enemy.hitboxOverlaps(bounds)) {
                if (enemy instanceof Boss boss) boss.hitByFireball();
                else enemy.collidedWithActor(true);
                removeFromParent();
                break;
            }
        }
    }
    @Override public void render(Batch batch) {
        batch.draw(sprite, speed > 0 ? getX() - SPRITE_PADDING_X : getX() + (BODY_SIZE + SPRITE_PADDING_X),
            getY() - SPRITE_PADDING_Y, speed > 0 ? SpriteSheets.FIREBALL.width() : -SpriteSheets.FIREBALL.width(),
            SpriteSheets.FIREBALL.height());
    }
}
