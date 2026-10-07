package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.BombExplosionEffect;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.WorldObject;

import java.util.function.Consumer;

/** Port of Dart's {@code Bomb}: kills the player and spawns an explosion effect on contact. */
public class Bomb extends Entity {
    private static final float STEP_TIME = 0.1f;

    private final GameContext game;
    private final Consumer<WorldObject> spawner;
    private final Animation<TextureRegion> animation;
    private float stateTime = 0f;
    private boolean exploded = false;

    public Bomb(Assets assets, GameContext game, Consumer<WorldObject> spawner, float x, float y, float w, float h) {
        super(x, y, w, h);
        this.game = game;
        this.spawner = spawner;
        animation = assets.animation("images/Items.png", 2, STEP_TIME, 16, 16, 32, 0);
        animation.setPlayMode(Animation.PlayMode.LOOP);
    }

    /** The bomb as drawn (10x13 inside its 16x16 frame), so grazing the empty corners of the frame is not lethal. */
    public Rectangle hitboxBounds() {
        float sx = size.x / 16f, sy = size.y / 16f;
        return new Rectangle(position.x + 3 * sx, position.y + 2 * sy, 10 * sx, 13 * sy);
    }

    public void collideWithPlayer() {
        if (exploded) return;
        exploded = true;
        if (game.playSounds()) game.playSound("bounce");
        spawner.accept(new BombExplosionEffect(centerX(), centerY(), 64f, 0.7f));
        removeFromParent();
    }

    @Override
    public void update(float dt) {
        stateTime += dt;
    }

    @Override
    public void render(Batch batch) {
        batch.draw(animation.getKeyFrame(stateTime), position.x, position.y, size.x, size.y);
    }
}
