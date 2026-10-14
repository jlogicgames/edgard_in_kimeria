package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BombExplosionEffect;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.WorldObject;

import java.util.function.Consumer;

/** One-shot contact bomb: a small trigger starts a warning, then a 64x64 blast. */
public class Bomb extends Entity {
    private static final float STEP_TIME = 0.1f;
    private static final float BLAST_SIZE = 64f;
    private static final float WARNING_FLASH_INTERVAL_SECONDS = 0.15f;
    private enum State { IDLE, WARNING, EXPLODED }

    private final GameContext game;
    private final Consumer<WorldObject> spawner;
    private final Consumer<Rectangle> onExplosion;
    private final Animation<TextureRegion> animation;
    private float stateTime = 0f;
    private State state = State.IDLE;
    private float warningRemaining;
    private float warningElapsed;

    public Bomb(Assets assets, GameContext game, Consumer<WorldObject> spawner,
                Consumer<Rectangle> onExplosion, float x, float y, float w, float h) {
        super(x, y, w, h);
        this.game = game;
        this.spawner = spawner;
        this.onExplosion = onExplosion;
        animation = assets.animation("images/Items.png", 2, STEP_TIME, 16, 16, 32, 0);
        animation.setPlayMode(Animation.PlayMode.LOOP);
    }

    /** Activation follows the art (10x13 inside the 16x16 frame), excluding empty corners. */
    public Rectangle hitboxBounds() {
        float sx = size.x / 16f, sy = size.y / 16f;
        return new Rectangle(position.x + 3 * sx, position.y + 2 * sy, 10 * sx, 13 * sy);
    }

    public void collideWithPlayer() {
        if (state != State.IDLE || isRemoved()) return;
        state = State.WARNING;
        warningRemaining = game.trapWarnings().duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        warningElapsed = 0f;
    }

    private void explode() {
        state = State.EXPLODED;
        if (game.playSounds()) game.playSound("bounce");
        Rectangle body = hitboxBounds();
        spawner.accept(new BombExplosionEffect(centerX(), body.y + body.height, BLAST_SIZE, 0.7f));
        removeFromParent();
        // One overlap check at detonation; the lingering shader effect is cosmetic.
        onExplosion.accept(new Rectangle(centerX() - BLAST_SIZE / 2f,
            centerY() - BLAST_SIZE / 2f, BLAST_SIZE, BLAST_SIZE));
    }

    @Override
    public void update(float dt) {
        if (isRemoved()) return;
        stateTime += dt;
        if (state == State.WARNING) {
            warningElapsed += dt;
            warningRemaining -= dt;
            if (warningRemaining <= 0f) explode();
        }
    }

    @Override
    public void render(Batch batch) {
        if (isRemoved()) return;
        float previousColor = batch.getPackedColor();
        if (state == State.WARNING && (int) (warningElapsed / WARNING_FLASH_INTERVAL_SECONDS) % 2 == 0) {
            // Alternate warm tint and normal art; never hide the activation footprint.
            var color = batch.getColor();
            batch.setColor(color.r, color.g * 0.35f, color.b * 0.2f, color.a);
        }
        batch.draw(animation.getKeyFrame(stateTime), position.x, position.y, size.x, size.y);
        batch.setPackedColor(previousColor);
    }
}
