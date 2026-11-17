package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.player.Player;

/**
 * The fairy helper (D82, #338): the healer's gift shows the target Up + Action will affect, in place of the corner
 * brackets of D29/D30. She only reads the hero's selection ({@link Player#interactionFeedbackTarget()}) and passive
 * set ({@link Player#collectPassiveTargets}); eligibility, the single target and look suppression stay in Player.
 * The selection reads on the frame it happens: the glow and the glints do not wait for her flight.
 * World Y grows downward: "above" is a smaller y.
 */
public final class FairyGuide {
    public enum Mode { HIDDEN, APPEAR, IDLE, FLY, POINT }

    private static final String BODY_SHEET = "images/objects/fairy-body.png", GLOW_SHEET = "images/objects/fairy-glow.png",
        GLINT_SHEET = "images/objects/fairy-glint.png";
    /** Frame layout of fairy-body.png (tools/sprite_layouts.py FAIRY_SHEETS). */
    static final int FRAME = 16, IDLE_FIRST = 0, IDLE_FRAMES = 4, FLY_FIRST = 4, FLY_FRAMES = 2, POINT_FIRST = 6,
        POINT_LEFT_FIRST = 9, POINT_FRAMES = 3, APPEAR_FIRST = 12, APPEAR_FRAMES = 2, BODY_FRAMES = 14;
    static final float IDLE_FRAME_SECONDS = .12f, FLY_FRAME_SECONDS = .06f, POINT_FRAME_SECONDS = .1f,
        APPEAR_FRAME_SECONDS = .2f, GLINT_FRAME_SECONDS = .25f, FADE_SECONDS = .1f;
    /** The glow sheet: a 24 px square drawn as a nine-slice with 8 px corners, so its rings keep their width. */
    static final int GLOW_SIZE = 24, GLOW_BORDER = 8, GLINT_SIZE = 5, GLINT_FRAMES = 2, CORNERS = 4;
    /** The selected glow covers the target grown by this much on each side; the glints sit on its corners grown by 2. */
    static final float GLOW_PAD = 6f, GLINT_GROW = 2f;
    /** Passive targets get the same glow, dimmer; the selected one is drawn at full strength. */
    static final float SELECTED_ALPHA = 1f, PASSIVE_ALPHA = .3f;
    /** Her own light (D76: without it her ink contour falls to 0.187 on dark tiles): the glow at 20 px, breathing. */
    static final int LIGHT_SIZE = 20;
    static final float LIGHT_ALPHA_MIN = .6f, LIGHT_ALPHA_MAX = .9f;
    /** Idle: frame centre beside his face, on the side away from where he faces, bobbing. */
    static final float IDLE_SIDE = 14, IDLE_DROP = 16, BOB = 1.5f, BOB_PERIOD = 1.6f;
    /** Smoothing rates (1/s): following the hero, flying to a target. Within ARRIVED px she points or idles. */
    static final float FOLLOW_RATE = 10, FLY_RATE = 14, ARRIVED = 2, FLYING = 6;
    /** The wand tip in a right-pointing frame (the left frames are flipped): 2 px off the target's upper corner. */
    static final int TIP_X = 15, TIP_Y = 13;
    static final float TIP_GAP = 2;
    /** The glow sheet is cut into a SLICES x SLICES grid: corners, edges and the middle. */
    private static final int SLICES = 3, MIDDLE = 1;
    /** Room for the passive marks of one frame; more grow the array once (an event, not a frame cost). */
    private static final int PASSIVE_CAPACITY = 8;
    /** The glint corners clockwise from the top left: which of them sit on the right and on the bottom edge. */
    private static final boolean[] CORNER_RIGHT = {false, true, true, false}, CORNER_BOTTOM = {false, false, true, true};

    private final TextureRegion[] body = new TextureRegion[BODY_FRAMES];
    private final TextureRegion[] glow = new TextureRegion[SLICES * SLICES];
    private final TextureRegion[] glint = new TextureRegion[GLINT_FRAMES];
    private final Array<Entity> passive = new Array<>(false, PASSIVE_CAPACITY);
    private Entity selected;
    /** The target she is pointing at, so a new target restarts the point clip even where she already hovers. */
    private Entity pointedAt;
    private Mode mode = Mode.HIDDEN;
    private boolean pointLeft, arrivePending;
    /** Frame centre, and where she is heading. */
    private float x, y, goalX, goalY;
    private float time, modeTime, alpha;

    public FairyGuide(Assets assets) {
        for (int i = 0; i < BODY_FRAMES; i++) body[i] = assets.region(BODY_SHEET, i * FRAME, 0, FRAME, FRAME);
        int[] cuts = {0, GLOW_BORDER, GLOW_SIZE - GLOW_BORDER, GLOW_SIZE};
        for (int row = 0; row < SLICES; row++) for (int col = 0; col < SLICES; col++)
            glow[row * SLICES + col] = assets.region(GLOW_SHEET, cuts[col], cuts[row], cuts[col + 1] - cuts[col], cuts[row + 1] - cuts[row]);
        for (int i = 0; i < GLINT_FRAMES; i++) glint[i] = assets.region(GLINT_SHEET, i * GLINT_SIZE, 0, GLINT_SIZE, GLINT_SIZE);
    }

    /** The one-time twinkle she arrives in after the forest-0 start tale (D82): played at his shoulder on the next update. */
    public void arrive() { arrivePending = true; }

    /**
     * Call order (D82): after {@link Player#update} and the level's own objects, from {@code Level.finishUpdate}. The
     * selection is asked again here on purpose: {@code Player.update} reads it before it moves the hero (for look
     * suppression), so its copy can be one step stale; the label and the next Up + Action see the target of the hero's
     * new position, and so does she, as the corner brackets did at render time. {@code DirectionalActionsTest} pins it
     * (the hero walks into reach with Up held). The passive set is computed only here.
     */
    public void update(float dt, Player hero) {
        time += dt;
        modeTime += dt;
        boolean present = hero != null && !hero.isRemoved() && hero.canFight();
        selected = present ? hero.interactionFeedbackTarget() : null;
        if (present) hero.collectPassiveTargets(passive, selected);
        else passive.clear();
        if (!present) {
            alpha = Math.max(0, alpha - dt / FADE_SECONDS);
            if (alpha == 0) setMode(Mode.HIDDEN);
            arrivePending &= hero != null;
            return;
        }
        idlePoint(hero);
        if (mode == Mode.HIDDEN || arrivePending) { // back at his shoulder: no flight from where she vanished
            x = goalX; y = goalY;
            setMode(arrivePending ? Mode.APPEAR : Mode.IDLE);
            arrivePending = false;
        }
        alpha = Math.min(1, alpha + dt / FADE_SECONDS);
        if (mode == Mode.APPEAR) {
            x = goalX; y = goalY;
            if (modeTime < APPEAR_FRAME_SECONDS * APPEAR_FRAMES) return;
            setMode(Mode.IDLE);
        }
        if (selected != null) hoverPoint(hero, selected);
        float k = 1 - (float) Math.exp(-(selected != null ? FLY_RATE : FOLLOW_RATE) * dt);
        x += (goalX - x) * k;
        y += (goalY - y) * k;
        float distance = distanceToGoal();
        Mode next = selected != null && distance <= ARRIVED ? Mode.POINT
            : distance > FLYING || (selected != null) ? Mode.FLY : Mode.IDLE;
        if (next != mode || next == Mode.POINT && selected != pointedAt) setMode(next);
        pointedAt = mode == Mode.POINT ? selected : null;
    }

    private void setMode(Mode next) { mode = next; modeTime = 0; }

    private void idlePoint(Player hero) {
        goalX = hero.centerX() + (hero.facingRight ? -IDLE_SIDE : IDLE_SIDE);
        goalY = hero.getY() + IDLE_DROP + BOB * MathUtils.sin(MathUtils.PI2 * time / BOB_PERIOD);
    }

    /** Beside the target on the side away from the hero, the wand tip just off its upper corner. */
    private void hoverPoint(Player hero, Entity target) {
        pointLeft = hero.centerX() < target.centerX(); // she is right of the target and points back to the left
        float tipY = target.getY() - TIP_GAP;
        float left = pointLeft ? target.getX() + target.getWidth() + TIP_GAP - 1 - (FRAME - 1 - TIP_X)
            : target.getX() - TIP_GAP - TIP_X;
        goalX = left + FRAME / 2f;
        goalY = tipY - TIP_Y + FRAME / 2f;
    }

    /** The target glows, drawn behind the objects: the selected one at full strength from its first frame, the passive ones dim. */
    public void renderBehind(Batch batch) {
        if (selected == null && passive.size == 0) return;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int i = 0; i < passive.size; i++) drawGlow(batch, passive.get(i), PASSIVE_ALPHA);
        if (selected != null) drawGlow(batch, selected, SELECTED_ALPHA);
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** The glints on the selected target, then her light and the fairy herself, drawn over the hero. */
    public void renderFront(Batch batch) {
        if (selected != null) drawGlints(batch, selected);
        if (mode == Mode.HIDDEN || alpha == 0) return;
        float left = MathUtils.round(x - FRAME / 2f), top = MathUtils.round(y - FRAME / 2f);
        float breath = (1 - MathUtils.cos(MathUtils.PI2 * time / BOB_PERIOD)) / 2; // her light, under every frame (D76)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(1, 1, 1, alpha * (LIGHT_ALPHA_MIN + (LIGHT_ALPHA_MAX - LIGHT_ALPHA_MIN) * breath));
        nineSlice(batch, left + FRAME / 2f - LIGHT_SIZE / 2f, top + FRAME / 2f - LIGHT_SIZE / 2f, LIGHT_SIZE, LIGHT_SIZE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(1, 1, 1, alpha);
        batch.draw(body[frame()], left, top, FRAME, FRAME);
        batch.setColor(Color.WHITE);
    }

    /** The body frame index in the sheet for the current mode and time (read by tests). */
    public int frame() {
        return switch (mode) {
            case APPEAR -> APPEAR_FIRST + Math.min(APPEAR_FRAMES - 1, (int) (modeTime / APPEAR_FRAME_SECONDS));
            case FLY -> FLY_FIRST + (int) (time / FLY_FRAME_SECONDS) % FLY_FRAMES;
            case POINT -> (pointLeft ? POINT_LEFT_FIRST : POINT_FIRST) + Math.min(POINT_FRAMES - 1, (int) (modeTime / POINT_FRAME_SECONDS));
            case IDLE, HIDDEN -> IDLE_FIRST + (int) (time / IDLE_FRAME_SECONDS) % IDLE_FRAMES;
        };
    }

    private void drawGlow(Batch batch, Entity target, float strength) {
        batch.setColor(1, 1, 1, strength);
        nineSlice(batch, target.getX() - GLOW_PAD, target.getY() - GLOW_PAD,
            target.getWidth() + GLOW_PAD * 2, target.getHeight() + GLOW_PAD * 2);
    }

    /** Big glints on one diagonal, small on the other, swapping every {@link #GLINT_FRAME_SECONDS}. */
    private void drawGlints(Batch batch, Entity target) {
        int phase = (int) (time / GLINT_FRAME_SECONDS) % GLINT_FRAMES;
        float left = target.getX() - GLINT_GROW, right = target.getX() + target.getWidth() + GLINT_GROW - 1;
        float top = target.getY() - GLINT_GROW, bottom = target.getY() + target.getHeight() + GLINT_GROW - 1;
        for (int corner = 0; corner < CORNERS; corner++) {
            float cx = CORNER_RIGHT[corner] ? right : left, cy = CORNER_BOTTOM[corner] ? bottom : top;
            TextureRegion frame = glint[(corner + phase) % GLINT_FRAMES];
            batch.draw(frame, MathUtils.round(cx - GLINT_SIZE / 2f), MathUtils.round(cy - GLINT_SIZE / 2f), GLINT_SIZE, GLINT_SIZE);
        }
    }

    private void nineSlice(Batch batch, float left, float top, float w, float h) {
        float middleW = Math.max(0, w - GLOW_BORDER * 2), middleH = Math.max(0, h - GLOW_BORDER * 2);
        for (int row = 0; row < SLICES; row++) {
            float rowY = row < MIDDLE ? top : row == MIDDLE ? top + GLOW_BORDER : top + GLOW_BORDER + middleH;
            float rowH = row == MIDDLE ? middleH : GLOW_BORDER;
            for (int col = 0; col < SLICES; col++) {
                float colX = col < MIDDLE ? left : col == MIDDLE ? left + GLOW_BORDER : left + GLOW_BORDER + middleW;
                float colW = col == MIDDLE ? middleW : GLOW_BORDER;
                if (rowH > 0 && colW > 0) batch.draw(glow[row * SLICES + col], colX, rowY, colW, rowH);
            }
        }
    }

    /** The target the selection glow and her flight show; always the hero's {@code interactionFeedbackTarget()}. */
    public Entity selected() { return selected; }
    /** The passive marks of this frame (read only). */
    public Array<Entity> passiveTargets() { return passive; }
    /** Glow strength drawn behind {@code target} this frame: 1 when selected, 0.3 when passive, 0 otherwise. */
    public float glowAlpha(Entity target) {
        if (target != null && target == selected) return SELECTED_ALPHA;
        return passive.contains(target, true) ? PASSIVE_ALPHA : 0;
    }
    public Mode mode() { return mode; }
    /** Her frame's centre and the point she is heading for (world coordinates, Y down). */
    public float x() { return x; }
    public float y() { return y; }
    public float goalX() { return goalX; }
    public float goalY() { return goalY; }
    public float distanceToGoal() { return (float) Math.hypot(goalX - x, goalY - y); }
}
