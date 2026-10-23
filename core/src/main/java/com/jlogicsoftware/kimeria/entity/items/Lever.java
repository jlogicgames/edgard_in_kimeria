package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Visible control at its authored interaction zone; the linked torch owns its state. */
public final class Lever extends Entity {
    public final Trigger trigger;
    private final Torch torch;
    private final TextureRegion up, down;

    public Lever(Assets assets, Trigger trigger, Torch torch) {
        super(trigger.centerX() - 8, trigger.getY() + trigger.getHeight() - 16, 16, 16);
        this.trigger = trigger;
        this.torch = torch;
        up = assets.region("images/objects/lever-up.png", 0, 0, 16, 16);
        down = assets.region("images/objects/lever-down.png", 0, 0, 16, 16);
    }

    public boolean isUp() { return torch.isBurning(); }
    @Override public void update(float dt) { }
    @Override public void render(Batch batch) { batch.draw(isUp() ? up : down, getX(), getY(), 16, 16); }
}
