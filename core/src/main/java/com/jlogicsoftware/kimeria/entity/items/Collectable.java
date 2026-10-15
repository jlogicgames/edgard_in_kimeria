package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.WorldObject;

import java.util.function.Consumer;

/** Animated coins and artifacts, sharing pickup sound and ripple. */
public class Collectable extends Entity {
    private static final float STEP_TIME = 0.3f;

    private final ItemType type;
    private final GameContext game;
    private final Consumer<WorldObject> spawner;
    private final Animation<TextureRegion> animation;
    private float stateTime = 0f;
    private boolean collected = false;
    private final Runnable onPickup;

    public Collectable(Assets assets, GameContext game, Consumer<WorldObject> spawner,
                        String name, float x, float y, float w, float h) {
        this(assets, game, spawner, name, x, y, w, h, () -> {});
    }

    public Collectable(Assets assets, GameContext game, Consumer<WorldObject> spawner,
                        String name, float x, float y, float w, float h, Runnable onPickup) {
        super(x, y, w, h);
        this.onPickup = onPickup;
        this.type = ItemType.fromMapName(name);
        this.game = game;
        this.spawner = spawner;
        animation = assets.animation("images/Items.png", 2, STEP_TIME, 16, 16, type.spriteX, type.spriteY);
        animation.setPlayMode(Animation.PlayMode.LOOP);
    }

    public boolean isCollected() {
        return collected;
    }

    public ItemType type() { return type; }

    public void collideWithPlayer() {
        if (collected) return;
        collected = true;
        onPickup.run();
        if (game.playSounds()) game.playSound("collect");

        if (type == ItemType.COIN) {
            game.addCoin();
        }
        spawner.accept(new RippleEffect(centerX(), centerY(), 0.75f, 300f, 12f, 60f, 20f));
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
