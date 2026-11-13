package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.function.Supplier;

/**
 * The false honey fungus (D63, #295, D3): the look-alike whose burst is a lethal spore cloud. Tiled type
 * {@code FalseHoneyFungus}. For {@link #CLOUD_SECONDS} after the swell the {@code trap-spores} sprite (24x24, the lime cloud
 * of the puffball) covers {@link #cloudBounds()}, and the hero's body box overlapping that rectangle is instant death, the same
 * entry as the bomb blast and the puffball ({@code Level.killOnLethalHazards}). Only while the cloud is live and only inside its
 * rectangle: the hitbox is exactly the drawn size. It fires once and is spent afterwards (D26): no ammunition, no re-arming.
 * <p>
 * World Y grows downward: the cloud is centred on the mushroom and sits on its floor line, so its bottom edge is
 * {@code position.y + size.y} and its top is {@link #CLOUD_SIZE} px above that.
 */
public final class FalseHoneyFungus extends FungusTrap implements LethalHazard {
    public static final float CLOUD_SECONDS = 1f;
    /** Spore cloud sprite size ({@code SpriteSheets.TRAP_SPORES}); the lethal rectangle is the same size. */
    public static final float CLOUD_SIZE = 24f;
    private static final float CLOUD_FRAME_SECONDS = 0.25f;
    private static final int CLOUD_FRAMES = 3;

    private final TextureRegion[] cloud = new TextureRegion[CLOUD_FRAMES];
    private final Rectangle cloudBounds;

    public FalseHoneyFungus(Assets assets, TrapWarnings warnings, float x, float y, float w, float h,
                            float zoneLeft, float zoneRight, Supplier<Rectangle> heroBody) {
        super(assets, SpriteSheets.TRAP_FALSE_HONEY_FUNGUS, warnings, x, y, w, h, zoneLeft, zoneRight, heroBody);
        cloudBounds = cloudOf(x, y, w, h);
        for (int i = 0; i < cloud.length; i++) cloud[i] = SpriteSheets.TRAP_SPORES.frame(assets, i, 0);
    }

    public static FalseHoneyFungus fromMap(Assets assets, TrapWarnings warnings, TiledObject o, Supplier<Rectangle> heroBody) {
        float[] zone = zoneOf(o);
        return new FalseHoneyFungus(assets, warnings, o.x, o.y, o.width, o.height, zone[0], zone[1], heroBody);
    }

    /** The cloud rectangle of a mushroom with this footprint: centred on it, standing on its floor line. */
    public static Rectangle cloudOf(float x, float y, float w, float h) {
        return new Rectangle(x + w / 2f - CLOUD_SIZE / 2f, y + h - CLOUD_SIZE, CLOUD_SIZE, CLOUD_SIZE);
    }

    /** The cloud's rectangle (lethal only while {@link #cloudLive()}); a copy. */
    public Rectangle cloudBounds() { return new Rectangle(cloudBounds); }

    public boolean cloudLive() { return state() == State.BURST; }

    @Override
    protected float burstSeconds() { return CLOUD_SECONDS; }

    @Override
    protected void burst() { }

    @Override
    public boolean lethalTo(Rectangle heroBody) {
        return cloudLive() && cloudBounds.overlaps(heroBody);
    }

    @Override
    public boolean everLethalIn(Rectangle area) {
        return cloudBounds.overlaps(area);
    }

    @Override
    protected void renderBurst(Batch batch) {
        if (!cloudLive()) return;
        int frame = (int) (burstElapsed() / CLOUD_FRAME_SECONDS) % cloud.length;
        batch.draw(cloud[frame], cloudBounds.x, cloudBounds.y, cloudBounds.width, cloudBounds.height);
    }
}
