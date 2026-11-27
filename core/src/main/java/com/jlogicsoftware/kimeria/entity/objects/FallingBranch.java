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
 * harmless scenery. Frames of {@link SpriteSheets#TRAP_BRANCH}: 0 resting, 1 rustle, 2 sag, 3 falling (also used for
 * the landed branch: the sheet has no separate landed frame). Resting and rustle carry the at-rest cue, a split joint
 * with a bunch of leaves under it (#361, D71). The trunk side of the split stays behind: from the moment the branch falls
 * the leftmost {@link #STUB_WIDTH} columns of the resting frame are drawn where it hung (#556).
 */
public class FallingBranch extends FallingTrap {
    /** The first half of the warning rustles (alternating frames), the second half sags. */
    private static final float RUSTLE_STEP_SECONDS = 0.1f;
    private static final int RESTING = 0, RUSTLE = 1, FALLING = 3;
    /** The sag frame, the second half of the warning (also read by tests). */
    public static final int SAG = 2;
    private static final float SAG_FROM_PROGRESS = 0.5f;
    private static final int ALTERNATING_FRAMES = 2, SHEET_FRAMES = 4;
    /** Sheet columns of the resting frame left on the trunk: the split's trunk side and the leaf bunch under it. */
    public static final int STUB_WIDTH = 11;

    /** The four frames, cut once: {@code Assets.region} builds a new region on every call. */
    private final TextureRegion[] frames = new TextureRegion[SHEET_FRAMES];
    private final TextureRegion stub;
    /** Where the branch hangs: its top edge before the fall, and the stub's after it. */
    private final float hungY;

    public FallingBranch(Assets assets, Rectangle body, Rectangle zone, float landingBottom, TrapWarnings warnings,
                         Supplier<Rectangle> hero, BooleanSupplier heroGrounded, Runnable kill) {
        super(body, zone, landingBottom, warnings, hero, heroGrounded, kill);
        SpriteSheets.Sheet sheet = SpriteSheets.TRAP_BRANCH;
        for (int i = 0; i < frames.length; i++) frames[i] = sheet.frame(assets, i, 0);
        stub = assets.region(sheet.path(), RESTING * sheet.width(), 0, STUB_WIDTH, sheet.height());
        hungY = body.y;
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
        if (state() == State.FALLING || state() == State.SPENT)
            batch.draw(stub, position.x, hungY, size.x * STUB_WIDTH / SpriteSheets.TRAP_BRANCH.width(), size.y);
        batch.draw(frames[frame()], position.x, position.y, size.x, size.y);
    }
}
