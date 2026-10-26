package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;

/** A trigger-linked wall that can either disappear or travel vertically between two stops. */
public class Wall extends CollisionBlock implements Actionable {
    public static final float DEFAULT_MOVE_DURATION_SECONDS = 1f;

    private final String targetId;
    private final boolean verticalMovement;
    private final float lowerY;
    private final float raisedY;
    private final float moveDurationSeconds;
    private final TextureRegion sprite;
    private float targetY;
    private boolean targetRaised;

    public Wall(float x, float y, float w, float h, String targetId) {
        this(null, x, y, w, h, targetId, false, DEFAULT_MOVE_DURATION_SECONDS);
    }

    public Wall(Assets assets, float x, float y, float w, float h, String targetId,
                boolean verticalMovement, float moveDurationSeconds) {
        super(x, y, w, h);
        setWall(true);
        this.verticalMovement = verticalMovement;
        if (verticalMovement) setWallClimbable(false);
        this.targetId = targetId;
        this.lowerY = y;
        this.raisedY = y - h;
        this.targetY = y;
        this.moveDurationSeconds = Float.isFinite(moveDurationSeconds) && moveDurationSeconds > 0
            ? moveDurationSeconds : DEFAULT_MOVE_DURATION_SECONDS;
        this.sprite = verticalMovement ? SpriteSheets.WALL.frame(assets) : null;
    }

    @Override
    public String getTargetId() {
        return targetId;
    }

    public boolean isRaised() {
        return verticalMovement ? targetRaised : !isActive();
    }

    public boolean movesVertically() {
        return verticalMovement;
    }

    @Override
    public void update(float dt) {
        if (!verticalMovement) return;
        float distance = targetY - getY();
        if (distance == 0) return;
        float travel = getHeight() * Math.max(0f, dt) / moveDurationSeconds;
        if (Math.abs(distance) <= travel) position.y = targetY;
        else position.y += Math.copySign(travel, distance);
    }

    @Override
    public void render(Batch batch) {
        if (sprite != null && isActive()) batch.draw(sprite, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public void performAction() {
        if (!isActive()) return;
        if (verticalMovement) {
            targetRaised = !targetRaised;
            targetY = targetRaised ? raisedY : lowerY;
            return;
        }
        setActive(false);
        removeFromParent();
    }

    public void restoreState(boolean active, boolean removed, float y, boolean raised) {
        setActive(active);
        position.y = y;
        targetRaised = raised;
        targetY = raised ? raisedY : lowerY;
        this.removed = removed;
    }
}
