package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Forest falling branch (D45): hangs over the path as harmless scenery; the hero entering its zone makes it
 * rustle then sag for the shared warning, then it falls and kills on contact. A landed branch lies as
 * harmless scenery. Frames of {@link SpriteSheets#TRAP_BRANCH}: 0 resting, 1 rustle, 2 sag, 3 falling
 * (also used for the landed branch: the sheet has no separate landed frame).
 */
public class FallingBranch extends FallingTrap {
    /** The first half of the warning rustles (alternating frames), the second half sags. */
    private static final float RUSTLE_STEP_SECONDS = 0.1f;
    private static final int RESTING = 0, RUSTLE = 1, SAG = 2, FALLING = 3;
    private static final float SAG_FROM_PROGRESS = 0.5f;
    private static final int ALTERNATING_FRAMES = 2;

    private final Assets assets;

    public FallingBranch(Assets assets, Rectangle body, Rectangle zone, float landingBottom, TrapWarnings warnings,
                         Supplier<Rectangle> hero, BooleanSupplier heroGrounded, Runnable kill) {
        super(body, zone, landingBottom, warnings, hero, heroGrounded, kill);
        this.assets = assets;
    }

    @Override
    protected boolean sparesAirborneHero() { return true; }

    /** The sheet frame shown now (also read by tests). */
    public int frame() {
        return switch (state()) {
            case ARMED -> RESTING;
            case WARNING -> warningProgress() < SAG_FROM_PROGRESS
                ? ((int) (warningElapsedSeconds() / RUSTLE_STEP_SECONDS) % ALTERNATING_FRAMES == 0 ? RUSTLE : RESTING) : SAG;
            case FALLING, SPENT -> FALLING;
        };
    }

    @Override
    public void render(Batch batch) {
        TextureRegion region = SpriteSheets.TRAP_BRANCH.frame(assets, frame(), 0);
        batch.draw(region, position.x, position.y, size.x, size.y);
    }
}
