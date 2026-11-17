package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;

/**
 * The silver thread on death (#341, D81; the image of #228): a line between two moving points, Edgard's wrist and
 * the room start. Its colours are authored ({@code art/objects/silver-thread.grid}: a 1x2 strand, silver over ink, and
 * the D78 glint star); only the curve is computed, because its length changes with every death. The strand is stamped
 * once per step along a chord with a parabolic sag, so the thread stays pixel art at any slope and nothing allocates.
 * World Y grows down: a positive sag hangs the middle lower on screen.
 */
public final class SilverThread {
    private static final int STRAND_X = 0, STRAND_Y = 0, STRAND_W = 1, STRAND_H = 2;
    private static final int GLINT_X = 1, GLINT_Y = 0, GLINT_SIZE = 5;
    private static final float GLINT_HALF = GLINT_SIZE / 2;
    /** A parabola through the chord's ends with its peak {@code sag} below the midpoint: sag * 4u(1 - u). */
    private static final float SAG_SHAPE = 4f;
    /** Longest curve stamped, in steps: a thread across the widest room (1280 px) plus a full sag fits well inside. */
    private static final int MAX_STEPS = 4096;
    /** Fraction of the slack thread drawn as the arc from the wrist; the rest is the coil lying on the floor. */
    private static final float SLACK_ARC_SHARE = .5f;
    /** Slack thread: where the drape reaches the floor and where the coil ends, in px from the wrist along X. */
    public static final float SLACK_ARC_REACH = 22f, SLACK_COIL_REACH = 34f;

    private final TextureRegion strand, glint;

    public SilverThread(Assets assets) {
        String path = SpriteSheets.SILVER_THREAD.path();
        strand = assets.region(path, STRAND_X, STRAND_Y, STRAND_W, STRAND_H);
        glint = assets.region(path, GLINT_X, GLINT_Y, GLINT_SIZE, GLINT_SIZE);
    }

    /**
     * Draws the part {@code length} (0..1, from A) of the thread from A to B, hanging {@code sag} px at its middle,
     * with the glint at {@code glintAt} (0..1 along it; negative for none). The sag never takes the strand's bottom
     * below {@code floorY} (world Y down: a larger Y is lower); where the chord itself is lower, the thread follows the
     * chord, since it must reach both ends. {@link Float#POSITIVE_INFINITY} leaves the sag free.
     */
    public void draw(Batch batch, float ax, float ay, float bx, float by, float sag, float length, float glintAt, float floorY) {
        if (length <= 0) return;
        int steps = steps(ax, ay, bx, by, sag);
        int last = Math.round(steps * Math.min(1f, length));
        for (int i = 0; i <= last; i++) {
            float u = (float) i / steps;
            batch.draw(strand, pixel(x(ax, bx, u)), pixel(clampedY(ay, by, sag, u, floorY)), STRAND_W, STRAND_H);
        }
        if (glintAt >= 0) {
            float u = Math.min(1f, glintAt);
            batch.draw(glint, pixel(x(ax, bx, u)) - GLINT_HALF, pixel(clampedY(ay, by, sag, u, floorY)) - GLINT_HALF,
                GLINT_SIZE, GLINT_SIZE);
        }
    }

    /**
     * The slack thread after the pull: a drape from the wrist down to the floor (it falls slowly near the hand and
     * meets the floor at its lowest point, never below it), then a short coil lying on the floor line. {@code shown}
     * (1..0) dissolves it from the far end toward the wrist.
     */
    public void drawSlack(Batch batch, float wristX, float wristY, float floorY, float shown) {
        if (shown <= 0) return;
        float restY = floorY - STRAND_H;
        int steps = steps(wristX, wristY, wristX + SLACK_ARC_REACH, restY, 0);
        int last = Math.round(steps * Math.min(1f, shown / SLACK_ARC_SHARE));
        for (int i = 0; i <= last; i++) {
            float u = (float) i / steps;
            batch.draw(strand, pixel(x(wristX, wristX + SLACK_ARC_REACH, u)), pixel(wristY + (restY - wristY) * u * u),
                STRAND_W, STRAND_H);
        }
        if (shown > SLACK_ARC_SHARE) {
            draw(batch, wristX + SLACK_ARC_REACH, restY, wristX + SLACK_COIL_REACH, restY, 0,
                (shown - SLACK_ARC_SHARE) / (1f - SLACK_ARC_SHARE), -1, floorY);
        }
    }

    /** One step per pixel of the longer axis, plus the sag's extra path. Package-visible for the curve test. */
    static int steps(float ax, float ay, float bx, float by, float sag) {
        int steps = (int) (Math.max(Math.abs(bx - ax), Math.abs(by - ay)) + 2 * Math.abs(sag));
        return Math.max(1, Math.min(MAX_STEPS, steps));
    }

    static float x(float ax, float bx, float u) { return ax + (bx - ax) * u; }

    /** The pixel a point lies in: points are pixel centres (Player.pullWrist adds 0.5), so floor, not round. */
    static float pixel(float coordinate) { return (float) Math.floor(coordinate); }

    static float y(float ay, float by, float sag, float u) { return ay + (by - ay) * u + sag * SAG_SHAPE * u * (1 - u); }

    /** {@link #y}, with the sag stopped at the floor: never lower than the floor allows, nor than the chord itself. */
    static float clampedY(float ay, float by, float sag, float u, float floorY) {
        float chord = ay + (by - ay) * u;
        return Math.min(y(ay, by, sag, u), Math.max(chord, floorY - STRAND_H));
    }
}
