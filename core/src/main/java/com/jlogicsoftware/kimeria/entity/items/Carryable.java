package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.physics.ProjectileStep;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.world.Level;

/** One reusable object: rest, held, placed, thrown, then rest again. */
public final class Carryable extends Entity {
    public static final float BODY_SIZE = 12;
    private static final float THROW_SPEED = 220, THROW_UPWARD_SPEED = 110;
    private static final float GRAVITY = 600, MAX_FALL_SPEED = 300;
    private static final float PLATFORM_TOLERANCE = .1f;
    private static final float HELD_BODY_OFFSET_X = 5, HELD_BODY_OFFSET_Y = 8;
    private static final float FLAME_BOUNDS_WIDTH = 8, FLAME_BOUNDS_HEIGHT = 12;
    private static final int FLAME_INTENSITY = 80;
    private static final float FLAME_OFFSET_X = 1, FLAME_OFFSET_Y = -7;

    public enum Kind { STONE, TORCH }
    public final int id;
    public final Kind kind;
    private final Level level;
    private final Vector2 velocity = new Vector2();
    private final Torch flame;
    private final TextureRegion sprite;
    private final SpriteSheets.Sheet artwork;
    private Player holder;
    private boolean thrown;
    /** True from a throw until the object comes to rest: a throw-activated Trigger (a lever) touched meanwhile fires once (D39). */
    private boolean leverFlight;
    private final float homeX, homeY;
    /** Tiled bool {@code respawn}: the object returns to where it was authored when it falls out of the room or hits a lever (D39). */
    private boolean respawns;

    public Carryable(Level level, int id, Kind kind, float x, float y) {
        super(x, y, BODY_SIZE, BODY_SIZE);
        this.level = level;
        this.id = id;
        this.kind = kind;
        homeX = x;
        homeY = y;
        artwork = kind == Kind.STONE ? SpriteSheets.STONE : SpriteSheets.TORCH;
        sprite = level.objectSprite(artwork);
        flame = kind == Kind.TORCH ? new Torch(x, y, FLAME_BOUNDS_WIDTH, FLAME_BOUNDS_HEIGHT, FLAME_INTENSITY, "") : null;
    }

    public Rectangle bounds() { return new Rectangle(getX(), getY(), size.x, size.y); }
    public Msg label() { return kind == Kind.STONE ? Msg.STONE : Msg.TORCH; }
    public boolean available() { return holder == null && !thrown && !removed; }
    public void pickUp(Player player) { holder = player; velocity.setZero(); followHolder(); }
    public void release(float x, float y, boolean throwObject, boolean right) {
        holder = null;
        position.set(x, y);
        thrown = throwObject;
        leverFlight = throwObject;
        velocity.set(throwObject ? (right ? THROW_SPEED : -THROW_SPEED) : 0, throwObject ? -THROW_UPWARD_SPEED : 0);
    }

    public void setRespawns(boolean respawns) { this.respawns = respawns; }

    private void returnHome() {
        position.set(homeX, homeY);
        velocity.setZero();
        thrown = false;
        leverFlight = false;
    }

    public boolean isHeld() { return holder != null; }

    public record Snapshot(float x, float y, float velocityX, float velocityY,
                           boolean thrown, boolean removed) { }

    public Snapshot snapshot() {
        return new Snapshot(getX(), getY(), velocity.x, velocity.y, thrown, removed);
    }

    public void restore(Snapshot snapshot) {
        holder = null;
        position.set(snapshot.x(), snapshot.y());
        velocity.set(snapshot.velocityX(), snapshot.velocityY());
        thrown = snapshot.thrown();
        leverFlight = false;
        removed = snapshot.removed();
        if (flame != null) positionFlame();
    }

    public void followHolder() {
        if (holder == null) return;
        holder.heldObjectGrip(position);
        // Native 8x16 torch: grip is (3,12), sprite origin is (bounds.x+2,bounds.y-4).
        position.sub(HELD_BODY_OFFSET_X, HELD_BODY_OFFSET_Y);
    }

    @Override public void update(float dt) {
        if (holder != null) followHolder();
        else {
            // Substeps prevent fast throws crossing a thin wall or an enemy in one frame.
            int steps = ProjectileStep.count(dt);
            float step = ProjectileStep.frameSeconds(dt) / steps;
            for (int i = 0; i < steps; i++) {
                float oldY = position.y;
                boolean landed = false;
                position.x += velocity.x * step;
                if (level.blocksObject(bounds(), false)) { position.x -= velocity.x * step; velocity.x = 0; thrown = false; }
                velocity.y = Math.min(MAX_FALL_SPEED, velocity.y + GRAVITY * step);
                position.y += velocity.y * step;
                for (var block : level.collisionBlocks) {
                    if (!block.isActive() || block.isQuickSand()) continue;
                    Rectangle b = new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight());
                    if (!bounds().overlaps(b)) continue;
                    if (block.isPlatform() && (velocity.y < 0 || oldY + size.y > b.y + PLATFORM_TOLERANCE)) continue;
                    position.y = velocity.y >= 0 ? b.y - size.y : b.y + b.height;
                    velocity.setZero(); thrown = false; landed = true;
                    break;
                }
                // Checked before a landing ends the flight, so a stone dropping onto the lever still counts.
                if (leverFlight && level.throwHitsLever(this)) {
                    leverFlight = false;
                    if (respawns) { returnHome(); break; }
                }
                if (landed) leverFlight = false;
                if (thrown && level.player.canFight()) for (var object : level.objects) {
                    if (object instanceof Enemy<?> enemy && !enemy.isDefeated() && enemy.hitboxOverlaps(bounds())) {
                        if (!(enemy instanceof Boss)) enemy.collidedWithActor(true);
                        velocity.x = 0; thrown = false;
                        break;
                    }
                }
            }
            // A throw that starts inside a wall the hero presses against is lifted onto that wall's top, outside the room.
            if (respawns && getY() + size.y <= level.currentRoomBounds().y) returnHome();
            if (getY() > level.currentRoomBounds().y + level.currentRoomBounds().height) {
                if (respawns) returnHome(); else removeFromParent();
            }
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
        batch.draw(sprite, getX() + (BODY_SIZE - artwork.width()) / 2f,
            getY() + (BODY_SIZE - artwork.height()), artwork.width(), artwork.height());
        if (kind == Kind.TORCH) {
            positionFlame();
            flame.render(batch);
        }
    }

    private void positionFlame() {
        // Flame emitter belongs at the torch head, not at the grip or the hero's waist.
        flame.position.set(getX() + FLAME_OFFSET_X, getY() + FLAME_OFFSET_Y);
    }
}
