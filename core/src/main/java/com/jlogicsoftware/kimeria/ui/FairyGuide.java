package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.effects.SoftGlowFx;
import com.jlogicsoftware.kimeria.entity.player.Player;
import java.util.function.Supplier;

/**
 * The fairy helper (D82, #338): the healer's gift shows the target Up + Action will affect, in place of the corner
 * brackets of D29/D30. She only reads the hero's selection ({@link Player#interactionFeedbackTarget()}) and passive
 * set ({@link Player#collectPassiveTargets}); eligibility, the single target and look suppression stay in Player.
 * The selection reads on the frame it happens: the glow and the glints do not wait for her flight.
 * Linked mechanisms off screen (D87, #419): while a lever is selected, and for {@link #CHANGED_SECONDS} after a pull, a
 * mechanism it drives that is outside the camera view sends her to the screen edge in its direction, where she twinkles;
 * the in-view ones keep a clear glow for the same time after the pull, so the change is marked where it happens.
 * World Y grows downward: "above" is a smaller y.
 */
public final class FairyGuide {
    public enum Mode { HIDDEN, APPEAR, IDLE, FLY, POINT, TWINKLE }

    private static final String BODY_SHEET = "images/objects/fairy-body.png", GLINT_SHEET = "images/objects/fairy-glint.png";
    /** Frame layout of fairy-body.png (tools/sprite_layouts.py FAIRY_SHEETS). */
    static final int FRAME = 16, IDLE_FIRST = 0, IDLE_FRAMES = 4, FLY_FIRST = 4, FLY_FRAMES = 2, POINT_FIRST = 6,
        POINT_LEFT_FIRST = 9, POINT_FRAMES = 3, APPEAR_FIRST = 12, APPEAR_FRAMES = 2, BODY_FRAMES = 14;
    static final float IDLE_FRAME_SECONDS = .12f, FLY_FRAME_SECONDS = .06f, POINT_FRAME_SECONDS = .1f,
        APPEAR_FRAME_SECONDS = .2f, GLINT_FRAME_SECONDS = .25f, FADE_SECONDS = .1f;
    static final int GLINT_SIZE = 5, GLINT_FRAMES = 2, CORNERS = 4;
    /** The selected glow covers the target grown by this much on each side; the glints sit on its corners grown by 2. */
    static final float GLOW_PAD = 6f, GLINT_GROW = 2f;
    /** Passive targets get the same glow, dimmer; the selected one is drawn at full strength. */
    static final float SELECTED_ALPHA = 1f, PASSIVE_ALPHA = .3f;
    /** D87: how long the pulled lever's mechanisms stay marked, the glow strength then (fading over the last part), and the edge inset of her centre. */
    public static final float CHANGED_SECONDS = 1.5f, CHANGED_ALPHA = .8f, CHANGED_FADE_SECONDS = .4f, EDGE_MARGIN = 16f,
        TWINKLE_RANGE = 8f; // she twinkles within this of the edge point, which keeps moving while the lift runs
    /**
     * D87 update (#434): several linked mechanisms off screen are shown in turn. She dwells {@code DWELL_SECONDS} at each edge point (two
     * loops of the twinkle), then flies to the next in clockwise order around the view centre; one hop never lasts longer than
     * {@code HOP_MAX_SECONDS} (a target she cannot reach must not hold the cycle). The post-pull cue gets {@code HOP_FLIGHT_SECONDS}
     * of flight for each target besides the dwell, and never less than {@link #CHANGED_SECONDS}.
     */
    public static final float DWELL_SECONDS = APPEAR_FRAME_SECONDS * APPEAR_FRAMES * 2, HOP_FLIGHT_SECONDS = .4f, HOP_MAX_SECONDS = 2f;
    /**
     * Her own light and the target marks are soft animated lights ({@link SoftGlowFx}, #544, D124 amending D82): hers is {@link SoftGlowFx#FAIRY_SELF}
     * (D76: without it her ink contour falls below the floor on dark tiles), at {@link #SELF_RADIUS} px; each marked target takes one of {@link #MARKS}
     * slots, an oval hugging it grown by {@link #GLOW_PAD}. More marks than slots are not lit (the glints still show them).
     */
    static final float SELF_RADIUS = 4f;
    private static final long SELF_SEED = 0xFA1121L;
    static final int MARKS = 12;
    /** Idle: frame centre beside his face, on the side away from where he faces, bobbing. */
    static final float IDLE_SIDE = 14, IDLE_DROP = 16, BOB = 1.5f, BOB_PERIOD = 1.6f;
    /** Smoothing rates (1/s): following the hero, flying to a target. Within ARRIVED px she points or idles. */
    static final float FOLLOW_RATE = 10, FLY_RATE = 14, ARRIVED = 2, FLYING = 6;
    /** The wand tip in a right-pointing frame (the left frames are flipped): 2 px off the target's upper corner. */
    static final int TIP_X = 15, TIP_Y = 13;
    static final float TIP_GAP = 2;
    /** Room for the passive marks of one frame; more grow the array once (an event, not a frame cost). */
    private static final int PASSIVE_CAPACITY = 8;
    /** The glint corners clockwise from the top left: which of them sit on the right and on the bottom edge. */
    private static final boolean[] CORNER_RIGHT = {false, true, true, false}, CORNER_BOTTOM = {false, false, true, true};

    private final TextureRegion[] body = new TextureRegion[BODY_FRAMES];
    private final SoftGlowFx[] marks = new SoftGlowFx[MARKS];
    private final Entity[] markTarget = new Entity[MARKS];
    private final float[] markStrength = new float[MARKS];
    private final boolean[] markSeen = new boolean[MARKS];
    private final SoftGlowFx self = new SoftGlowFx(SoftGlowFx.FAIRY_SELF, SELF_SEED);
    private final TextureRegion[] glint = new TextureRegion[GLINT_FRAMES];
    private final Array<Entity> passive = new Array<>(false, PASSIVE_CAPACITY);
    /** The mechanisms of the lever just pulled (filled by {@code Level.activateTrigger}) and those of the selected lever. */
    private final Array<Entity> changed = new Array<>(false, PASSIVE_CAPACITY), linked = new Array<>(false, PASSIVE_CAPACITY);
    /** The post-pull copy of {@link #changed} the off-screen cue works from; it can outlive the in-view marks when several are off screen (D87 update (#434)). */
    private final Array<Entity> cue = new Array<>(false, PASSIVE_CAPACITY);
    private final Vector2 edge = new Vector2();
    private Supplier<Rectangle> viewSource;
    private float changedLeft, cueLeft;
    private boolean cueSizing;
    /** The off-screen mechanism she is flying to the edge for, or null; how long she has twinkled at it and how long the hop has lasted (D87 update (#434)). */
    private Entity offscreen;
    private float dwell, hopTime;
    /**
     * Where the last target was (its centre, x, y) when it was chosen, frozen then and kept so the next hop starts after its place even once it left
     * the cycle or a lift moved on. Its angle is taken round the view centre at the hop, so a moving camera does not make the key stale.
     */
    private boolean hasKey;
    private float keyPX, keyPY, keyX, keyY, keyA;
    /** Scratch of the next-target scans (no allocation per hop). */
    private float bestD, afterA, afterX, afterY, firstA, firstX, firstY;
    private Entity after, first;
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
        for (int i = 0; i < MARKS; i++) marks[i] = new SoftGlowFx(SoftGlowFx.FAIRY_TARGET, SELF_SEED + 1 + i);
        for (int i = 0; i < GLINT_FRAMES; i++) glint[i] = assets.region(GLINT_SHEET, i * GLINT_SIZE, 0, GLINT_SIZE, GLINT_SIZE);
    }

    /** The camera's visible world rect (top left, Y down); without it, or with an empty one, no mechanism counts as off screen. */
    public void setView(Supplier<Rectangle> view) { viewSource = view; }

    /**
     * A lever was pulled (D87): clears the marked set, starts its timer and returns the array for the caller to fill with the
     * mechanisms the lever drives ({@code Level.linkedMechanismsInto}).
     */
    public Array<Entity> beginChanged() {
        changed.clear();
        changedLeft = CHANGED_SECONDS;
        cueLeft = CHANGED_SECONDS;
        cueSizing = true; // the first update copies the set into the cue and sizes the window (D87 update (#434))
        return changed;
    }

    /**
     * A room change (D87): the marks, the selection and the off-screen cue belong to the room that was left. She drops them and
     * comes back to Edgard's shoulder without a flight on the next update.
     */
    public void reset() {
        changed.clear(); linked.clear(); passive.clear(); cue.clear();
        changedLeft = 0; cueLeft = 0; cueSizing = false; hasKey = false; dwell = 0; hopTime = 0;
        offscreen = null; selected = null; pointedAt = null;
        setMode(Mode.HIDDEN);
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
        step(dt, hero);
        refreshMarks(dt);
    }

    /** Places the lights on this frame's targets and advances them; a mark that is gone is cleared at once, as the old glow vanished. Index loops, no allocation. */
    private void refreshMarks(float dt) {
        java.util.Arrays.fill(markSeen, false);
        for (int i = 0; i < passive.size; i++) mark(passive.get(i), Math.max(PASSIVE_ALPHA, changedStrength(passive.get(i))));
        for (int i = 0; i < changed.size; i++) {
            Entity e = changed.get(i);
            if (e != selected && !e.isRemoved() && !passive.contains(e, true)) mark(e, changedStrength(e));
        }
        if (selected != null) mark(selected, SELECTED_ALPHA);
        for (int i = 0; i < MARKS; i++) {
            if (!markSeen[i]) { markTarget[i] = null; marks[i].clear(); } else marks[i].update(dt);
        }
        if (mode == Mode.HIDDEN || alpha == 0) self.clear();
        else { self.place(x, y, SELF_RADIUS, SELF_RADIUS); self.update(dt); }
    }

    private void mark(Entity target, float strength) {
        if (strength <= 0) return;
        int slot = -1;
        for (int i = 0; i < MARKS && slot < 0; i++) if (markTarget[i] == target) slot = i;
        for (int i = 0; i < MARKS && slot < 0; i++) if (markTarget[i] == null) slot = i;
        if (slot < 0) return;
        markTarget[slot] = target;
        markStrength[slot] = strength;
        markSeen[slot] = true;
        marks[slot].place(target.centerX(), target.centerY(), target.getWidth() / 2 + GLOW_PAD, target.getHeight() / 2 + GLOW_PAD);
    }

    private void step(float dt, Player hero) {
        time += dt;
        modeTime += dt;
        boolean present = hero != null && !hero.isRemoved() && hero.canFight();
        selected = present ? hero.interactionFeedbackTarget() : null;
        if (present) {
            hero.collectPassiveTargets(passive, selected);
            hero.collectLinkedTargets(linked, selected);
            changedLeft = Math.max(0, changedLeft - dt);
            if (changedLeft == 0) changed.clear();
            cueLeft = Math.max(0, cueLeft - dt);
            if (cueLeft == 0) cue.clear();
        } else {
            passive.clear();
            linked.clear();
            changed.clear();
            cue.clear();
            changedLeft = 0; cueLeft = 0; cueSizing = false; hasKey = false;
            offscreen = null;
        }
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
        Rectangle view = viewSource != null ? viewSource.get() : null;
        if (cueSizing) sizeCue(view);
        offscreen = offscreenTarget(view);
        if (offscreen != null) {
            edgePoint(view, EDGE_MARGIN, offscreen.centerX(), offscreen.centerY(), edge);
            goalX = edge.x; goalY = edge.y;
        } else if (selected != null) hoverPoint(hero, selected);
        boolean sent = offscreen != null || selected != null;
        float k = 1 - (float) Math.exp(-(sent ? FLY_RATE : FOLLOW_RATE) * dt);
        x += (goalX - x) * k;
        y += (goalY - y) * k;
        float distance = distanceToGoal();
        Mode next = offscreen != null && distance <= TWINKLE_RANGE ? Mode.TWINKLE
            : selected != null && offscreen == null && distance <= ARRIVED ? Mode.POINT
            : distance > FLYING || sent ? Mode.FLY : Mode.IDLE;
        if (next != mode || next == Mode.POINT && selected != pointedAt) setMode(next);
        pointedAt = mode == Mode.POINT ? selected : null;
        if (offscreen != null) { // the dwell counts from her arrival, not from the start of the flight
            hopTime += dt;
            if (mode == Mode.TWINKLE) dwell += dt;
        }
    }

    /**
     * The first update after a pull (D87 update (#434)): copies the marked set into the cue and, when several of them are off screen, gives the
     * cue time for a dwell and a flight at each, so every one is shown at least once; never less than {@link #CHANGED_SECONDS}.
     * One off-screen mechanism needs {@code DWELL + FLIGHT} = 1.2 s, under the 1.5 s, so a single one behaves as in D87.
     */
    private void sizeCue(Rectangle view) {
        cueSizing = false;
        cue.clear();
        cue.addAll(changed);
        int n = 0;
        if (view != null && view.width > 0 && view.height > 0)
            for (int i = 0; i < cue.size; i++) if (!cue.get(i).isRemoved() && !overlapsView(cue.get(i), view)) n++;
        cueLeft = Math.max(cueLeft, n * (DWELL_SECONDS + HOP_FLIGHT_SECONDS));
    }

    /**
     * The mechanism to send her to (D87 update (#434)). With none, or none off screen, null. The first is the nearest to the view centre (ties:
     * smaller x, then y) of the selected lever's mechanisms and the post-pull cue's that do not overlap {@code view}. She keeps it for
     * {@link #DWELL_SECONDS} of twinkle (or {@link #HOP_MAX_SECONDS}); then, if another is off screen, the next in clockwise order
     * round the view centre (Y down, so increasing {@code atan2}), the first again after the last. One that comes into view, is
     * removed or leaves the lists ends its dwell at once and the cycle goes on after its place in the order; one that joins waits for
     * the next hop. A single off-screen target is kept as long as it stays off screen, exactly as in D87.
     */
    private Entity offscreenTarget(Rectangle view) {
        if (view == null || view.width <= 0 || view.height <= 0) { hasKey = false; return null; }
        Entity cur = offscreen;
        boolean listed = cur != null && (linked.contains(cur, true) || cue.contains(cur, true));
        boolean valid = listed && !cur.isRemoved() && !overlapsView(cur, view);
        if (valid) {
            if (dwell < DWELL_SECONDS && hopTime < HOP_MAX_SECONDS) return cur; // the key stays as frozen on arrival, even if a lift moves her target's angle
        } else if (!listed) hasKey = false; // another lever or the end of the cue: a new cycle, from the nearest
        Entity next = hasKey ? nextAfterKey(view) : nearestOffscreen(view);
        if (next == null) { hasKey = false; return null; }
        dwell = 0; hopTime = 0; // a lone target gets a new dwell and does not move
        setKey(next, view);
        return next;
    }

    private void setKey(Entity e, Rectangle view) {
        keyPX = e.centerX(); keyPY = e.centerY(); keyX = e.getX(); keyY = e.getY();
        hasKey = true;
    }

    private static float angle(Entity e, Rectangle view) {
        return MathUtils.atan2(e.centerY() - (view.y + view.height / 2f), e.centerX() - (view.x + view.width / 2f));
    }

    private static boolean keyLess(float a1, float x1, float y1, float a2, float x2, float y2) {
        return a1 < a2 || a1 == a2 && (x1 < x2 || x1 == x2 && y1 < y2);
    }

    /** The off-screen candidate with the smallest key greater than the last one's, or the smallest of all (the wrap); null with none. */
    private Entity nextAfterKey(Rectangle view) {
        after = null; first = null;
        keyA = MathUtils.atan2(keyPY - (view.y + view.height / 2f), keyPX - (view.x + view.width / 2f));
        scanKeys(linked, view);
        scanKeys(cue, view);
        return after != null ? after : first;
    }

    private void scanKeys(Array<Entity> list, Rectangle view) {
        for (int i = 0; i < list.size; i++) {
            Entity e = list.get(i);
            if (e.isRemoved() || overlapsView(e, view)) continue;
            float a = angle(e, view), x = e.getX(), y = e.getY();
            if (first == null || keyLess(a, x, y, firstA, firstX, firstY)) { first = e; firstA = a; firstX = x; firstY = y; }
            if (keyLess(keyA, keyX, keyY, a, x, y) && (after == null || keyLess(a, x, y, afterA, afterX, afterY))) { after = e; afterA = a; afterX = x; afterY = y; }
        }
    }

    /** The nearest off-screen candidate to the view centre (the start of a cycle); null when all are in view. */
    private Entity nearestOffscreen(Rectangle view) {
        bestD = Float.MAX_VALUE;
        return nearestOffscreen(cue, view, nearestOffscreen(linked, view, null));
    }

    /** {@code best} or, when one of {@code list} is off screen and nearer to the view centre, that one (the nearest of the list). */
    private Entity nearestOffscreen(Array<Entity> list, Rectangle view, Entity best) {
        float cx = view.x + view.width / 2f, cy = view.y + view.height / 2f;
        for (int i = 0; i < list.size; i++) {
            Entity e = list.get(i);
            if (e.isRemoved() || overlapsView(e, view)) continue;
            float d = Vector2.dst2(cx, cy, e.centerX(), e.centerY());
            if (best == null || d < bestD || d == bestD && (e.getX() < best.getX() || e.getX() == best.getX() && e.getY() < best.getY())) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private static boolean overlapsView(Entity e, Rectangle view) {
        return e.getX() < view.x + view.width && e.getX() + e.getWidth() > view.x
            && e.getY() < view.y + view.height && e.getY() + e.getHeight() > view.y;
    }

    /**
     * Where she waits for an off-screen target at ({@code tx}, {@code ty}): the point on the ray from the view centre to the
     * target where it leaves the view inset by {@code margin}, so the direction on screen is the target's true direction.
     * World Y grows downward: a target below the view gives a larger y. A target inside the inset comes back unchanged.
     */
    public static Vector2 edgePoint(Rectangle view, float margin, float tx, float ty, Vector2 out) {
        float cx = view.x + view.width / 2f, cy = view.y + view.height / 2f;
        float dx = tx - cx, dy = ty - cy;
        float halfW = Math.max(0, view.width / 2f - margin), halfH = Math.max(0, view.height / 2f - margin);
        float scale = 1;
        if (dx != 0) scale = Math.min(scale, halfW / Math.abs(dx));
        if (dy != 0) scale = Math.min(scale, halfH / Math.abs(dy));
        return out.set(cx + dx * scale, cy + dy * scale);
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

    /** The target lights, drawn behind the objects: the selected one at full strength from its first frame, the passive ones dim. */
    public void renderBehind(Batch batch) {
        for (int i = 0; i < MARKS; i++) if (markSeen[i]) marks[i].render(batch, markStrength[i]);
    }

    /** The glints on the selected target, then her light and the fairy herself, drawn over the hero. */
    public void renderFront(Batch batch) {
        if (selected != null) drawGlints(batch, selected);
        for (int i = 0; i < changed.size; i++) {
            Entity e = changed.get(i);
            if (e != selected && !e.isRemoved() && changedStrength(e) > 0) drawGlints(batch, e);
        }
        if (mode == Mode.HIDDEN || alpha == 0) return;
        float left = MathUtils.round(x - FRAME / 2f), top = MathUtils.round(y - FRAME / 2f);
        self.render(batch, alpha); // her light, under every frame (D76)
        batch.setColor(1, 1, 1, alpha);
        batch.draw(body[frame()], left, top, FRAME, FRAME);
        batch.setColor(Color.WHITE);
        if (mode == Mode.TWINKLE) drawGlints(batch, left, top, FRAME, FRAME); // the twinkle at the screen edge (D87)
    }

    /** Glow strength of the just-changed mark on {@code target}: {@link #CHANGED_ALPHA}, fading out over the last {@link #CHANGED_FADE_SECONDS}; 0 when not marked. */
    private float changedStrength(Entity target) {
        if (changedLeft <= 0 || !changed.contains(target, true)) return 0;
        return CHANGED_ALPHA * Math.min(1, changedLeft / CHANGED_FADE_SECONDS);
    }

    /** The body frame index in the sheet for the current mode and time (read by tests). */
    public int frame() {
        return switch (mode) {
            case APPEAR -> APPEAR_FIRST + Math.min(APPEAR_FRAMES - 1, (int) (modeTime / APPEAR_FRAME_SECONDS));
            case FLY -> FLY_FIRST + (int) (time / FLY_FRAME_SECONDS) % FLY_FRAMES;
            case POINT -> (pointLeft ? POINT_LEFT_FIRST : POINT_FIRST) + Math.min(POINT_FRAMES - 1, (int) (modeTime / POINT_FRAME_SECONDS));
            case TWINKLE -> APPEAR_FIRST + (int) (modeTime / APPEAR_FRAME_SECONDS) % APPEAR_FRAMES;
            case IDLE, HIDDEN -> IDLE_FIRST + (int) (time / IDLE_FRAME_SECONDS) % IDLE_FRAMES;
        };
    }

    /** Big glints on one diagonal, small on the other, swapping every {@link #GLINT_FRAME_SECONDS}. */
    private void drawGlints(Batch batch, Entity target) { drawGlints(batch, target.getX(), target.getY(), target.getWidth(), target.getHeight()); }

    private void drawGlints(Batch batch, float boxX, float boxY, float boxW, float boxH) {
        int phase = (int) (time / GLINT_FRAME_SECONDS) % GLINT_FRAMES;
        float left = boxX - GLINT_GROW, right = boxX + boxW + GLINT_GROW - 1;
        float top = boxY - GLINT_GROW, bottom = boxY + boxH + GLINT_GROW - 1;
        for (int corner = 0; corner < CORNERS; corner++) {
            float cx = CORNER_RIGHT[corner] ? right : left, cy = CORNER_BOTTOM[corner] ? bottom : top;
            TextureRegion frame = glint[(corner + phase) % GLINT_FRAMES];
            batch.draw(frame, MathUtils.round(cx - GLINT_SIZE / 2f), MathUtils.round(cy - GLINT_SIZE / 2f), GLINT_SIZE, GLINT_SIZE);
        }
    }

    /** How many target marks are lit this frame (for tests). */
    public int marksLit() {
        int n = 0;
        for (int i = 0; i < MARKS; i++) if (markSeen[i]) n++;
        return n;
    }
    /** The target the selection glow and her flight show; always the hero's {@code interactionFeedbackTarget()}. */
    public Entity selected() { return selected; }
    /** The passive marks of this frame (read only). */
    public Array<Entity> passiveTargets() { return passive; }
    /** Glow strength drawn behind {@code target} this frame: 1 when selected, 0.3 when passive, 0 otherwise. */
    public float glowAlpha(Entity target) {
        if (target != null && target == selected) return SELECTED_ALPHA;
        return Math.max(passive.contains(target, true) ? PASSIVE_ALPHA : 0, changedStrength(target));
    }
    /** The mechanisms of the lever just pulled, marked until {@link #changedSecondsLeft()} runs out (read only). */
    public Array<Entity> changedTargets() { return changed; }
    public float changedSecondsLeft() { return changedLeft; }
    /** The off-screen mechanism she is flying to the screen edge for, or null (D87). */
    public Entity offscreenTarget() { return offscreen; }
    public Mode mode() { return mode; }
    /** Her frame's centre and the point she is heading for (world coordinates, Y down). */
    public float x() { return x; }
    public float y() { return y; }
    public float goalX() { return goalX; }
    public float goalY() { return goalY; }
    public float distanceToGoal() { return (float) Math.hypot(goalX - x, goalY - y); }
}
