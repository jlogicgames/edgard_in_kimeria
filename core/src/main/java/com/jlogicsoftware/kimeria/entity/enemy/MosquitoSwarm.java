package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.List;
import java.util.Map;

/**
 * The forest mosquito swarm (#645, D151): a loose cloud that flies to and fro along an authored horizontal or vertical line,
 * as the {@link Bat} does, and never reacts to the hero. It is lethal exactly where it is drawn: the oval its mosquitoes fill
 * ({@value #WIDTH} by {@value #HEIGHT} px). Touching it kills on any side, also during a swing and also from above (no
 * stomp, no bounce); the death lands on the swarm's next frame, so a hit of the touching frame wins (#503, #505). The sword
 * never kills it: a swing that reaches it blows the mosquitoes away from the hero, once per swing, stretching the oval by up
 * to {@value #SCATTER_REACH} px on the far side only, and they gather again; the blade goes on through it. A thrown stone, a
 * fireball or a thrown torch kills the whole swarm at once; it leaves no body, only its mosquitoes, which drop and fade.
 *
 * <p>Every mosquito's place is a fixed function of the swarm's own clock, so a retry is the same (D7). World Y grows
 * downward: the oval's bottom edge is the larger y.
 */
public class MosquitoSwarm extends Enemy<MosquitoSwarm.State> {
    public enum State { FLY, DEAD }

    /** The gathered cloud: the oval inscribed in this box, whose top-left is {@code position}. */
    public static final float WIDTH = 22, HEIGHT = 18;
    /** As the Bat flies (px/s). */
    public static final float SPEED = 50;
    /** A swing stretches the far side of the oval by up to this many px. */
    public static final float SCATTER_REACH = 12;
    /** The scatter: blown out, held, gathered again; together as long as the swing. */
    public static final float SCATTER_OUT = .1f, SCATTER_HOLD = .2f, SCATTER_GATHER = .4f;
    public static final float SCATTER_SECONDS = SCATTER_OUT + SCATTER_HOLD + SCATTER_GATHER;

    /** How many mosquitoes are drawn, and the size of each one's sprite: a dot of 1 or 2 px. */
    public static final int COUNT = 28;
    public static final int MOSQUITO_WIDTH = 2, MOSQUITO_HEIGHT = 1;
    /**
     * Each mosquito's top-left in the gathered box, and how far it circles from there on each axis. The first eighteen sit
     * round the rim, so the lethal edge is drawn all round (they only flicker, or stir a px along it); the rest circle inside.
     * Every drawn pixel stays inside the oval.
     */
    private static final int[] HOME_X = {20, 20, 18, 15, 12, 8, 5, 2, 0, 0, 0, 2, 5, 8, 12, 15, 18, 20, 4, 15, 4, 14, 6, 13, 9, 7, 13, 10};
    private static final int[] HOME_Y = {8, 11, 14, 16, 17, 17, 16, 14, 11, 9, 6, 3, 1, 0, 0, 1, 3, 6, 4, 4, 13, 13, 8, 9, 5, 11, 5, 12};
    private static final int[] REACH_X = {0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2};
    private static final int[] REACH_Y = {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2};
    /** Turns of its small path per second: base plus a per-mosquito step, so no two move together. */
    private static final float CIRCLE_RATE = 1.3f, CIRCLE_RATE_STEP = .17f, PHASE_STEP = .37f, VERTICAL_RATE_RATIO = 1.31f;
    private static final int RATE_STEPS = 7;
    /** Wing frames per second, and the sheet's layout: two wing frames for a dark mosquito, two for a fed one, then the dead and the burning one. */
    private static final float WING_RATE = 20;
    private static final int WING_FRAMES = 2, DEAD_FRAME = 4, EMBER_FRAME = 5, FRAMES = 6;
    /** Every this many mosquitoes one has a rose body (the fed frames). */
    private static final int FED_EVERY = 4;
    private static final int NO_SWING = -1;

    /** Load checks: a way under needs the oval this far above the floor, a way over its top at most this high with this headroom. */
    public static final float UNDER_CLEARANCE = 24, OVER_TOP = 32, OVER_HEADROOM = 80;
    /** A vertical line leaves one of the two ways open at least this long in every cycle (seconds). */
    public static final float MIN_OPEN_SECONDS = .75f;
    /** The load check samples the floor under the sweep in columns this wide. */
    private static final float CHECK_STEP = 1;
    private static final float HALF = .5f;

    /**
     * The death (D151): every mosquito drops where it was, drifting a little sideways and down (never under the surface below
     * it), whole for the first part, then fading; killed by fire, it burns as an ember first. All gone after
     * {@value #DEATH_SECONDS} s.
     */
    public static final float DEATH_SECONDS = .8f, EMBER_SECONDS = .25f;
    private static final float FADE_FROM = .35f;
    private static final int[] DRIFT = {-3, 4, 1, -2, 2, -4, 3, 0, -1, 3, -3, 1, 4, -2, 2, -1, 3, -4, 0, 4, -3, 1, -2, 2, -4, 3, -1, 0};
    private static final int[] FALL = {12, 8, 16, 6, 14, 10, 7, 15, 9, 13, 17, 11, 8, 14, 7, 12, 9, 16, 13, 6, 15, 10, 17, 8, 11, 14, 9, 12};

    /** The only two sounds, quiet whines: one mosquito darting out of the sword's way, several dying. No loop while it flies. */
    static final String SCATTER_SOUND = "mosquito_scatter", DIE_SOUND = "mosquito_die";

    public final boolean isVertical;
    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final float spawnX, spawnY;
    private Level level;
    private float clock;
    /** Seconds into the scatter, or negative when gathered; the side it is blown to (+1 right, -1 left). */
    private float scatterClock = -1;
    private int blow = 1;
    private int lastSwing = NO_SWING;
    private boolean contactDue;
    /** Where each mosquito was when the swarm died, how far each may fall, the seconds since, and whether fire took it. */
    private final float[] deadX = new float[COUNT], deadY = new float[COUNT], deadFall = new float[COUNT];
    private float deadClock;
    private boolean burnt;

    public MosquitoSwarm(Assets assets, GameContext game, float x, float y, boolean isVertical, float offNeg, float offPos) {
        super(assets, game, "mosquito", x, y, WIDTH, HEIGHT, offNeg, offPos, State.class);
        this.isVertical = isVertical;
        spawnX = x;
        spawnY = y;
        hitbox = Hitbox.rect(0, 0, WIDTH, HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_MOSQUITO.frame(assets, i, 0);
        rangeNeg = (isVertical ? y : x) - offNeg * TILE_SIZE;
        rangePos = (isVertical ? y : x) + offPos * TILE_SIZE;
        moveDirection = 1;
        facingRight = true; // the box is never mirrored: the mosquitoes face their own ways
        current = State.FLY;
    }

    public void setLevel(Level level) { this.level = level; }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // Left behind, it returns whole to its spawn (D6).
        position.set(spawnX, spawnY);
        moveDirection = 1;
        clock = 0;
        scatterClock = -1;
        lastSwing = NO_SWING;
        contactDue = false;
    }

    @Override
    public void update(float dt) {
        if (!game.isGameStarted() || isRemoved()) return;
        if (current == State.DEAD) {
            if ((deadClock += dt) >= DEATH_SECONDS) removeFromParent();
            return;
        }
        updateEnemy(dt);
    }

    @Override
    public void updateEnemy(float dt) {
        // A touch of the last frame lands now: a ball cast or a stone thrown on that frame has had its first step (#503, #505).
        if (contactDue) {
            contactDue = false;
            if (player != null) player.collidedWithActor(false);
        }
        clock += dt;
        if (scatterClock >= 0 && (scatterClock += dt) >= SCATTER_SECONDS) scatterClock = -1;
        fly(dt);
    }

    private void fly(float dt) {
        float at = isVertical ? position.y : position.x;
        if (at >= rangePos) moveDirection = -1;
        else if (at <= rangeNeg) moveDirection = 1;
        at = MathUtils.clamp(at + moveDirection * SPEED * dt, rangeNeg, rangePos);
        if (isVertical) position.y = at;
        else position.x = at;
    }

    /** How far the swarm is blown apart now: 0 gathered, 1 at its widest. */
    public float scatterAmount() {
        if (scatterClock < 0) return 0;
        if (scatterClock < SCATTER_OUT) return scatterClock / SCATTER_OUT;
        if (scatterClock < SCATTER_OUT + SCATTER_HOLD) return 1;
        return Math.max(0, 1 - (scatterClock - SCATTER_OUT - SCATTER_HOLD) / SCATTER_GATHER);
    }

    /** +1 while it is blown to the right, -1 to the left. */
    public int blowDirection() { return blow; }

    /** The box round the lethal oval now: the near side stays, the far side moves out while scattered. */
    public Rectangle shapeInto(Rectangle out) {
        float spread = scatterAmount() * SCATTER_REACH;
        return out.set(blow > 0 ? position.x : position.x - spread, position.y, WIDTH + spread, HEIGHT);
    }

    /** The body's bounds are the oval's box, scattered or not. */
    @Override
    public Rectangle boundsInto(Hitbox box, Rectangle out) {
        return box == hitbox ? shapeInto(out) : super.boundsInto(box, out);
    }

    /** What kills is what is drawn (D5, D10): the oval, not its box. Touching its edge is not a hit. */
    @Override
    public boolean hitboxOverlaps(Rectangle rect) {
        Rectangle shape = shapeInto(bodyScratch);
        float rx = shape.width * HALF, ry = shape.height * HALF, cx = shape.x + rx, cy = shape.y + ry;
        float dx = (MathUtils.clamp(cx, rect.x, rect.x + rect.width) - cx) / rx;
        float dy = (MathUtils.clamp(cy, rect.y, rect.y + rect.height) - cy) / ry;
        return dx * dx + dy * dy < 1;
    }

    /** A fireball or a thrown torch ({@link #hitByFire}) or a stone ({@code gotHit}) kills it; any touch kills the hero. */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) die(false);
        else contactDue = true;
    }

    @Override
    public void hitByFire() {
        if (!isDefeated()) die(true);
    }

    /** The sword only scatters it, once per swing, away from the hero (D151). */
    @Override
    public void hitBySword(Level level) {
        if (isDefeated()) return;
        int swing = level.player.swingNumber();
        if (swing == lastSwing) return;
        lastSwing = swing;
        Rectangle hero = level.player.hitboxBoundsInto(otherScratch);
        int away = position.x + WIDTH * HALF >= hero.x + hero.width * HALF ? 1 : -1;
        // Blown again the same way while still apart, it goes on from where it is; the other way, it starts gathered.
        float amount = away == blow ? scatterAmount() : 0;
        blow = away;
        scatterClock = amount * SCATTER_OUT;
        if (game.playSounds()) game.playSound(SCATTER_SOUND);
    }

    @Override public boolean stopsSword() { return false; }

    @Override public boolean touchesDuringSwing() { return true; }

    /** It is defeated at once, harmless and out of every weapon's way; only its falling mosquitoes stay to be seen. */
    private void die(boolean fire) {
        for (int i = 0; i < COUNT; i++) {
            deadX[i] = mosquitoX(i);
            deadY[i] = mosquitoY(i);
            deadFall[i] = fall(i);
        }
        current = State.DEAD;
        contactDue = false;
        pendingRemoval = true;
        burnt = fire;
        deadClock = 0;
        if (game.playSounds()) game.playSound(DIE_SOUND);
    }

    /** How far mosquito {@code i} falls: its own drop, cut at the surface under it. */
    private float fall(int i) {
        if (level == null) return FALL[i];
        float bottom = deadY[i] + MOSQUITO_HEIGHT;
        float surface = level.surfaceUnder(deadX[i] + MOSQUITO_WIDTH * HALF, bottom, bottom + FALL[i]);
        return Float.isNaN(surface) ? FALL[i] : surface - bottom;
    }

    /** The seconds since the death (0 while it lives). */
    public float deadSeconds() { return current == State.DEAD ? deadClock : 0; }

    @Override
    protected void setDefeated() { die(false); }

    public State state() { return current; }

    /** Left and right ends (top and bottom for a vertical one) of the line the box's top-left stays in. */
    public float rangeNeg() { return rangeNeg; }
    public float rangePos() { return rangePos; }

    /**
     * The left edge of mosquito {@code i}'s sprite now: whole px from the box, spread with the oval while scattered, and then
     * rounded towards the box's middle, so a dot on the rim never leaves the lethal oval.
     */
    public float mosquitoX(int i) {
        if (current == State.DEAD) return deadX[i] + MathUtils.round(DRIFT[i] * deadShare());
        float home = HOME_X[i] + circle(i, REACH_X[i], 1);
        float stretch = 1 + scatterAmount() * SCATTER_REACH / WIDTH;
        float centre = home + MOSQUITO_WIDTH * HALF;
        float spread = blow > 0 ? centre * stretch : WIDTH - (WIDTH - centre) * stretch;
        float left = spread - MOSQUITO_WIDTH * HALF;
        return position.x + (centre > WIDTH * HALF ? MathUtils.floor(left) : MathUtils.ceil(left));
    }

    /** The top edge of mosquito {@code i}'s sprite now. */
    public float mosquitoY(int i) {
        if (current == State.DEAD) return deadY[i] + MathUtils.round(deadFall[i] * deadShare());
        return position.y + HOME_Y[i] + circle(i, REACH_Y[i], VERTICAL_RATE_RATIO);
    }

    private float deadShare() { return Math.min(1, deadClock / DEATH_SECONDS); }

    /** The whole-px offset of mosquito {@code i} on one axis of its small path. */
    private int circle(int i, int reach, float rateRatio) {
        if (reach == 0) return 0;
        float turns = clock * (CIRCLE_RATE + CIRCLE_RATE_STEP * (i % RATE_STEPS)) * rateRatio + PHASE_STEP * i;
        return MathUtils.round(MathUtils.sin(turns * MathUtils.PI2) * reach);
    }

    /** The sheet frame of mosquito {@code i} now. */
    public int frameIndex(int i) {
        if (current == State.DEAD) return burnt && deadClock < EMBER_SECONDS ? EMBER_FRAME : DEAD_FRAME;
        return (i % FED_EVERY == 0 ? WING_FRAMES : 0) + ((int) (clock * WING_RATE) + i) % WING_FRAMES;
    }

    /** How opaque the mosquitoes are drawn: whole while they live, fading in the later part of the death. */
    public float opacity() {
        if (current != State.DEAD || deadClock <= FADE_FROM) return 1;
        return Math.max(0, 1 - (deadClock - FADE_FROM) / (DEATH_SECONDS - FADE_FROM));
    }

    @Override
    public void render(Batch batch) {
        float saved = batch.getPackedColor();
        float opacity = opacity();
        if (opacity < 1) batch.setColor(1, 1, 1, opacity);
        for (int i = 0; i < COUNT; i++) {
            TextureRegion frame = frames[frameIndex(i)];
            float x = mosquitoX(i), y = mosquitoY(i);
            if ((i & 1) == 0) batch.draw(frame, x, y, MOSQUITO_WIDTH, MOSQUITO_HEIGHT);
            else batch.draw(frame, x + MOSQUITO_WIDTH, y, -MOSQUITO_WIDTH, MOSQUITO_HEIGHT);
        }
        batch.setPackedColor(saved);
    }

    /**
     * The load checks (D151), on the {@value #WIDTH} by {@value #HEIGHT} swarm whatever the object's size. Along the whole
     * line, with the scatter's reach either side: inside the room and the camera's view, clear of {@code blockers} (solids,
     * platforms, hanging and moving objects, the falls of branches and stones), and over no standing place. A floor lies under
     * every column of that sweep. A free way past is open (D26): a horizontal line passes either high enough to walk under or
     * low enough to jump over with clear height; a vertical line leaves one of the two open at least
     * {@value #MIN_OPEN_SECONDS} s in every cycle. A way round is not judged. Null when it is fine, else what is wrong.
     */
    public static String problem(float x, float y, float w, float h, boolean vertical, float offNeg, float offPos,
                                 List<Rectangle> floors, List<Rectangle> blockers, Rectangle room, float cameraTop,
                                 Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        float left = x - (vertical ? 0 : offNeg * TILE_SIZE) - SCATTER_REACH;
        float right = x + (vertical ? 0 : offPos * TILE_SIZE) + WIDTH + SCATTER_REACH;
        float highest = y - (vertical ? offNeg * TILE_SIZE : 0), lowest = y + (vertical ? offPos * TILE_SIZE : 0);
        Rectangle sweep = new Rectangle(left, highest, right - left, lowest + HEIGHT - highest);
        if (sweep.x < room.x || sweep.x + sweep.width > room.x + room.width || sweep.y < room.y
            || sweep.y + sweep.height > room.y + room.height) return "has its sweep leaving the room";
        if (highest < cameraTop) return "flies above the camera's view of its room";
        for (Rectangle blocker : blockers) if (sweep.overlaps(blocker)) return "has something solid, one-way or moving on its line";
        for (Map.Entry<String, Rectangle> place : standing.entrySet())
            if (sweep.overlaps(place.getValue())) return "flies over the room " + place.getKey();
        // The floor under the sweep: its highest and lowest points.
        float near = Float.MAX_VALUE, far = -Float.MAX_VALUE;
        for (float column = left + HALF; column < right; column += CHECK_STEP) {
            float surface = surfaceAt(floors, column, lowest + HEIGHT);
            if (surface == Float.MAX_VALUE) return "needs a floor under x " + (int) column;
            near = Math.min(near, surface);
            far = Math.max(far, surface);
        }
        // Under: the oval's bottom at most near - UNDER_CLEARANCE. Over: its top at least far - OVER_TOP, with clear height.
        boolean headroom = true;
        Rectangle jump = new Rectangle(left, near - OVER_HEADROOM, right - left, OVER_HEADROOM);
        for (Rectangle blocker : blockers) if (jump.overlaps(blocker)) { headroom = false; break; }
        if (!vertical) {
            boolean under = y + HEIGHT <= near - UNDER_CLEARANCE, over = headroom && y >= far - OVER_TOP;
            return under || over ? null : "leaves no way past: pass at least " + (int) UNDER_CLEARANCE + " px above the floor, or at most "
                + (int) OVER_TOP + " px high with " + (int) OVER_HEADROOM + " px of clear height";
        }
        float underTo = near - UNDER_CLEARANCE - HEIGHT, overFrom = far - OVER_TOP;
        boolean under = openLongEnough(highest, Math.min(lowest, underTo), highest, lowest);
        boolean over = headroom && openLongEnough(Math.max(highest, overFrom), lowest, highest, lowest);
        return under || over ? null : "leaves no way past open for " + MIN_OPEN_SECONDS + " s of a cycle";
    }

    /**
     * Whether the box's top spends long enough in each cycle in {@code from..to} of its travel {@code highest..lowest}:
     * all of the travel is always enough; a part of it is flown twice a cycle.
     */
    private static boolean openLongEnough(float from, float to, float highest, float lowest) {
        if (from > to) return false;
        return from <= highest && to >= lowest || 2 * (to - from) / SPEED >= MIN_OPEN_SECONDS;
    }

    /** The highest surface at {@code x} no higher than {@code from}, or {@link Float#MAX_VALUE} over open air. */
    private static float surfaceAt(List<Rectangle> floors, float x, float from) {
        float surface = Float.MAX_VALUE;
        for (Rectangle f : floors) if (x >= f.x && x <= f.x + f.width && f.y >= from && f.y < surface) surface = f.y;
        return surface;
    }
}
