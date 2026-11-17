package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Entity;

import java.util.function.BooleanSupplier;

/** Visible control at its authored interaction zone; the linked mechanism owns its state. */
public final class Lever extends Entity {
    public final Trigger trigger;
    private final BooleanSupplier active;
    private final TextureRegion on, off;

    public Lever(Assets assets, Trigger trigger, Torch torch) {
        this(assets, trigger, torch::isBurning);
    }

    public Lever(Assets assets, Trigger trigger, BooleanSupplier active) {
        super(trigger.centerX() - SpriteSheets.LEVER.width() / 2f,
            trigger.getY() + trigger.getHeight() - SpriteSheets.LEVER.height(),
            SpriteSheets.LEVER.width(), SpriteSheets.LEVER.height());
        this.trigger = trigger;
        this.active = active;
        // One sheet, a floor lever (#413): frame 0 = on (handle right), frame 1 = off (handle left).
        on = SpriteSheets.LEVER.frame(assets, 0, 0);
        off = SpriteSheets.LEVER.frame(assets, 1, 0);
    }

    /** True when the lever is on (handle right); the name predates the floor lever (#413), when on meant handle up. */
    public boolean isUp() { return active.getAsBoolean(); }
    @Override public void update(float dt) { }
    @Override public void render(Batch batch) { batch.draw(isUp() ? on : off, getX(), getY(), getWidth(), getHeight()); }
}
