package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.world.Level;

/** One reusable object: rest, held, placed, thrown, then rest again. */
public final class Carryable extends Entity {
    public enum Kind { STONE, TORCH }
    public final int id;
    public final Kind kind;
    private final Level level;
    private final Vector2 velocity = new Vector2();
    private final Torch flame;
    private final TextureRegion sprite;
    private Player holder;
    private boolean thrown;

    public Carryable(Level level, int id, Kind kind, float x, float y) {
        super(x, y, 12, 12);
        this.level = level;
        this.id = id;
        this.kind = kind;
        sprite = level.objectSprite(kind == Kind.STONE ? "stone" : "torch");
        flame = kind == Kind.TORCH ? new Torch(x, y, 8, 12, 80, "") : null;
    }

    public Rectangle bounds() { return new Rectangle(getX(), getY(), size.x, size.y); }
    public Msg label() { return kind == Kind.STONE ? Msg.STONE : Msg.TORCH; }
    public boolean available() { return holder == null && !thrown && !removed; }
    public void pickUp(Player player) { holder = player; velocity.setZero(); followHolder(); }
    public void release(float x, float y, boolean throwObject, boolean right) {
        holder = null;
        position.set(x, y);
        thrown = throwObject;
        velocity.set(throwObject ? (right ? 220 : -220) : 0, throwObject ? -110 : 0);
    }

    public void followHolder() {
        if (holder == null) return;
        holder.heldObjectGrip(position);
        // Native 8x16 torch: grip is (3,12), sprite origin is (bounds.x+2,bounds.y-4).
        position.sub(5, 8);
    }

    @Override public void update(float dt) {
        if (holder != null) followHolder();
        else {
            // Substeps prevent fast throws crossing a thin wall or an enemy in one frame.
            int steps = Math.max(1, (int) Math.ceil(Math.min(dt, .05f) / .008f));
            float step = Math.min(dt, .05f) / steps;
            for (int i = 0; i < steps; i++) {
                float oldY = position.y;
                position.x += velocity.x * step;
                if (level.blocksObject(bounds(), false)) { position.x -= velocity.x * step; velocity.x = 0; thrown = false; }
                velocity.y = Math.min(300, velocity.y + 600 * step);
                position.y += velocity.y * step;
                for (var block : level.collisionBlocks) {
                    if (!block.isActive() || block.isQuickSand()) continue;
                    Rectangle b = new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight());
                    if (!bounds().overlaps(b)) continue;
                    if (block.isPlatform() && (velocity.y < 0 || oldY + size.y > b.y + .1f)) continue;
                    position.y = velocity.y >= 0 ? b.y - size.y : b.y + b.height;
                    velocity.setZero(); thrown = false;
                    break;
                }
                if (thrown && level.player.canFight()) for (var object : level.objects) {
                    if (object instanceof Enemy<?> enemy && !enemy.isDefeated() && enemy.hitboxOverlaps(bounds())) {
                        if (!(enemy instanceof Boss)) enemy.collidedWithActor(true);
                        velocity.x = 0; thrown = false;
                        break;
                    }
                }
            }
            if (getY() > level.currentRoomBounds().y + level.currentRoomBounds().height) removeFromParent();
        }
        if (flame != null) { positionFlame(); flame.update(dt); }
    }

    @Override public void render(Batch batch) {
        if (holder != null) return;
        renderSprite(batch);
    }

    /** The one held object stays visible in the player's foreground pass (D23). */
    public void renderHeld(Batch batch) {
        if (holder != null) renderSprite(batch);
    }

    private void renderSprite(Batch batch) {
        if (kind == Kind.STONE) batch.draw(sprite, getX() + 2, getY() + 4, 8, 8);
        else {
            batch.draw(sprite, getX() + 2, getY() - 4, 8, 16);
            positionFlame();
            flame.render(batch);
        }
    }

    private void positionFlame() {
        // Flame emitter belongs at the torch head, not at the grip or the hero's waist.
        flame.position.set(getX() + 1, getY() - 7);
    }
}
