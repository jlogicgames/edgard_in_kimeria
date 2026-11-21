package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.effects.FireballFx;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.Breakable;
import com.jlogicsoftware.kimeria.entity.enemy.Remains;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.physics.ProjectileStep;

/**
 * A single paid charge. Terrain blocks it; enemies and the forest boss take one hit. In flight it sheds a flame trail and
 * where it stops it bursts ({@link FireballFx}, #397); both are cosmetic and read nothing back.
 */
public final class Fireball extends Entity {
    public static final float BODY_SIZE = 10;
    private static final float SPEED = 280, LIFETIME_SECONDS = 2;
    private static final float SPRITE_PADDING_X = (SpriteSheets.FIREBALL.width() - BODY_SIZE) / 2f;
    private static final float SPRITE_PADDING_Y = (SpriteSheets.FIREBALL.height() - BODY_SIZE) / 2f;
    /** The trail leaves the ball this far behind its centre, where the sprite's flame tail is. */
    private static final float TRAIL_BACK_PX = 4;

    private final Level level;
    private final float speed;
    private final TextureRegion sprite;
    private final Rectangle boundsScratch = new Rectangle(), trapBox = new Rectangle();
    private float life = LIFETIME_SECONDS;
    /** Pixels flown since the last trail step. */
    private float trailDistance;
    public Fireball(Level level, float x, float y, boolean right) {
        super(x, y, BODY_SIZE, BODY_SIZE);
        this.level = level;
        sprite = level.objectSprite(SpriteSheets.FIREBALL);
        speed = right ? SPEED : -SPEED;
    }
    @Override public void update(float dt) {
        // #503, D93: a ball in flight when the hero can no longer fight is seen and heard going out, like one that burns out.
        if (!level.player.canFight()) { level.fireballFx().fizzle(centerX(), centerY()); level.fireballFizzle(true); removeFromParent(); return; }
        if ((life -= dt) <= 0) { level.fireballFx().fizzle(centerX(), centerY()); level.fireballFizzle(true); removeFromParent(); return; }
        int steps = ProjectileStep.count(dt);
        float direction = Math.signum(speed);
        for (int i = 0; i < steps && !removed; i++) {
            float dx = speed * ProjectileStep.frameSeconds(dt) / steps;
            position.x += dx;
            // One trail step per TRAIL_SPACING_PX flown, placed where the ball's back was at that distance.
            for (trailDistance += Math.abs(dx); trailDistance >= FireballFx.TRAIL_SPACING_PX; trailDistance -= FireballFx.TRAIL_SPACING_PX)
                level.fireballFx().trail(centerX() - direction * (TRAIL_BACK_PX + trailDistance - FireballFx.TRAIL_SPACING_PX), centerY(), direction);
            Rectangle bounds = boundsScratch.set(getX(), getY(), size.x, size.y);
            // Terrain throws the spray back off the wall; a creature or an object takes it on along the blow.
            // The burst starts where this step began, the last free position, so it sits on the wall face, not inside the wall.
            if (level.blocksObject(bounds, false)) { burst(-direction, false, position.x - dx); level.fireballFizzle(false); break; }
            // D106: a fireball burns every snail's gob it reaches and flies on, unspent; tested before the enemies, so a ball spent
            // on an enemy in the same step still burns the gob it reached (the gob's hit lands on its snail's next frame).
            for (int o = 0; o < level.objects.size(); o++) if (level.objects.get(o) instanceof Breakable gob
                && gob.isBreakable() && gob.breakableInto(trapBox).overlaps(bounds)) gob.breakApart(true);
            for (int o = 0; o < level.objects.size(); o++) if (level.objects.get(o) instanceof Enemy<?> enemy
                && !enemy.isDefeated() && enemy.hitboxOverlaps(bounds)) {
                // A boss in its orange counter sequence (or abandoned) rejects the hit: the ball still bursts, lightly, as on a
                // wall or a mob. Read before the hit, since a landed hit itself starts that sequence.
                boolean heavy = enemy instanceof Boss boss && !boss.isCounterSequence() && !boss.isAbandoned();
                if (enemy instanceof Boss boss) boss.hitByFireball();
                else enemy.collidedWithActor(true);
                burst(direction, heavy, getX());
                break;
            }
            // D101, kept by D119: a fireball that passes a monster's body burns it up and flies on: a body never shields a monster or costs a ball.
            if (!removed) for (int o = 0; o < level.objects.size(); o++) if (level.objects.get(o) instanceof Remains body
                && body.isRemains() && body.remainsInto(trapBox).overlaps(bounds)) body.burnUp();
            // D47: a fireball that reaches an intact explosive trap lights it from afar and is spent doing so.
            if (!removed) for (int o = 0; o < level.objects.size(); o++) if (level.objects.get(o) instanceof ExplosiveTrap trap
                && trap.triggerBoxInto(trapBox).overlaps(bounds) && trap.igniteFromAfar()) {
                burst(direction, false, getX());
                break;
            }
            // #365 (D90): a fireball that reaches a hanging hive knocks it down and is spent doing so.
            if (!removed) for (int o = 0; o < level.objects.size(); o++) if (level.objects.get(o) instanceof HangingHive hive
                && hive.bodyInto(trapBox).overlaps(bounds) && hive.knockDown()) {
                burst(direction, false, getX());
                break;
            }
        }
    }

    /** The ball is spent: one burst at its leading edge with the ball's left side at {@code ballX}, then it leaves the level. */
    private void burst(float sprayDirection, boolean heavy, float ballX) {
        float x = speed > 0 ? ballX + BODY_SIZE : ballX;
        if (heavy) level.fireballFx().heavyHit(x, centerY(), sprayDirection);
        else level.fireballFx().hit(x, centerY(), sprayDirection);
        removeFromParent();
    }
    @Override public void render(Batch batch) {
        FireballFx.drawBallHalo(batch, centerX(), centerY());
        batch.draw(sprite, speed > 0 ? getX() - SPRITE_PADDING_X : getX() + (BODY_SIZE + SPRITE_PADDING_X),
            getY() - SPRITE_PADDING_Y, speed > 0 ? SpriteSheets.FIREBALL.width() : -SpriteSheets.FIREBALL.width(),
            SpriteSheets.FIREBALL.height());
    }
}
