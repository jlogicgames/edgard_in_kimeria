package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.physics.CollisionUtils;
import com.jlogicsoftware.kimeria.physics.GravityBody;

/** Port of Dart's {@code Enemy} base class. */
public abstract class Enemy<S extends Enum<S>> extends Actor<S> implements GravityBody {
    protected static final float TILE_SIZE = 16f;
    /** Upward speed the player gets from stomping an enemy. */
    private static final float BOUNCE_HEIGHT = 260f;
    /** How far (px) the player's feet may sit below the enemy's top on the previous frame and still count as above it. */
    private static final float STOMP_TOLERANCE = 2f;

    protected final Assets assets;
    protected final GameContext game;
    protected final String spriteName;
    protected final float offNeg, offPos;
    public final Vector2 velocity = new Vector2();
    private boolean onGround = false;
    protected float rangeNeg, rangePos;
    protected float moveDirection = 0;
    protected Player player;
    /** Set true once a hit animation starts; removes the enemy once it finishes playing. */
    protected boolean pendingRemoval = false;

    protected Enemy(Assets assets, GameContext game, String spriteName, float x, float y, float w, float h,
                     float offNeg, float offPos, Class<S> stateType) {
        super(x, y, w, h, stateType);
        this.assets = assets;
        this.game = game;
        this.spriteName = spriteName;
        this.offNeg = offNeg;
        this.offPos = offPos;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    protected com.badlogic.gdx.graphics.g2d.Animation<com.badlogic.gdx.graphics.g2d.TextureRegion> spriteAnimation(
        int amount, float stepTime, float frameW, float frameH, float originX, float originY) {
        return assets.animation("images/enemy/" + spriteName + ".png", amount, stepTime, frameW, frameH, originX, originY);
    }

    @Override
    public Vector2 position() {
        return position;
    }

    @Override
    public Vector2 velocity() {
        return velocity;
    }

    @Override
    public boolean isOnGround() {
        return onGround;
    }

    @Override
    public void setOnGround(boolean value) {
        onGround = value;
    }

    /**
     * What touching this enemy costs the player, when different from the body {@link #hitbox}. The
     * body is the generous box: sword hits, stomps and terrain use it. The hurtbox is what actually
     * kills, and must not reach past the drawn art, or the enemy kills before it visibly touches.
     * Null means the body is also the hurtbox.
     */
    protected Hitbox hurtbox;

    /** The area that kills the player on touch, in world space. */
    public Rectangle hurtBounds() {
        return boundsOf(hurtbox != null ? hurtbox : hitbox);
    }

    public boolean hasSeparateHurtbox() {
        return hurtbox != null;
    }

    private boolean hurtsPlayer() {
        Hitbox shape = hurtbox != null ? hurtbox : hitbox;
        return CollisionUtils.overlaps(shape, hurtBounds(), player.hitboxBounds());
    }

    /**
     * Contact with the player (the caller has checked that the player overlaps the body). A sword
     * hit ({@code gotHit}) or a stomp from above defeats the enemy; any other contact kills the
     * player only if the hurtbox is touched, and leaves the enemy where it is.
     */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (pendingRemoval) return;
        boolean stomped = !gotHit && stompedByPlayer();
        if (gotHit || stomped) {
            if (game.playSounds()) game.playSound("bounce");
            if (stomped) player.velocity.y = -BOUNCE_HEIGHT;
            setDefeated();
            pendingRemoval = true;
        } else if (hurtsPlayer()) {
            player.collidedWithActor(false);
        }
    }

    /** True when the player is moving down and was entirely above this enemy's hitbox on the previous frame. */
    private boolean stompedByPlayer() {
        return isStomp(player.velocity.y, player.previousFeetY(), hitboxBounds().y);
    }

    /** Y grows downwards: a stomp needs the player to be falling and to have started the frame above the enemy's top edge. */
    static boolean isStomp(float playerVelocityY, float playerPreviousFeetY, float enemyTop) {
        return playerVelocityY > 0 && playerPreviousFeetY <= enemyTop + STOMP_TOLERANCE;
    }

    /**
     * -1 or 1 towards the player, judged by hitbox centres. Within a small dead zone it returns
     * {@code current}, so an enemy standing right on the player doesn't flip every frame.
     */
    protected float directionToPlayer(float current) {
        Rectangle me = hitboxBounds();
        Rectangle target = player.hitboxBounds();
        float dx = (target.x + target.width / 2f) - (me.x + me.width / 2f);
        return Math.abs(dx) < 2f ? current : Math.signum(dx);
    }

    /** True when the player's body is inside this enemy's patrol range and on its level, judged by hitboxes. */
    protected boolean playerInRange() {
        Rectangle me = hitboxBounds();
        Rectangle target = player.hitboxBounds();
        float centre = target.x + target.width / 2f;
        return centre >= rangeNeg && centre <= rangePos
            && target.y + target.height > me.y && target.y < me.y + me.height;
    }

    /** The area that currently hurts the player on top of the body hitbox, or null if this enemy has no melee attack. */
    public Rectangle attackBounds() {
        return null;
    }

    /** True while {@link #attackBounds()} is actually dangerous (the strike frame, not the wind-up). */
    public boolean isAttackActive() {
        return false;
    }

    /** Switches to the enemy's defeat animation; the enemy is removed once it finishes. */
    protected abstract void setDefeated();

    /** Subclasses implement their own AI/physics tick here. */
    public abstract void updateEnemy(float dt);

    @Override
    public void update(float dt) {
        if (!game.isGameStarted()) return;

        if (pendingRemoval) {
            tickAnimation(dt);
            if (isCurrentAnimationFinished()) {
                removeFromParent();
            }
            return;
        }
        updateEnemy(dt);
        tickAnimation(dt);
    }
}
