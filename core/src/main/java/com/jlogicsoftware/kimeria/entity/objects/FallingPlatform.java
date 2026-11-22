package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.WorldObject;

import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A one-shot platform that lights up, then collapses after a shared warning delay once the player lands on it. */
public class FallingPlatform extends Actor<FallingPlatform.State> implements Collidable {
    public enum State {IDLE, FALLING}

    /**
     * Look only; behaviour is identical. LOG is the rotten forest log (D46), the falling platform's look, chosen by the map
     * property {@code skin=log} or by none; BRANCH is the rotten tree branch (#349, D80), {@code skin=branch}, drawn on the left of its trunk with {@code mirror}.
     */
    public enum Skin {
        LOG, BRANCH;

        public static Skin fromMap(String value) {
            return switch (value) {
                case "", "log" -> LOG;
                case "branch" -> BRANCH;
                default -> throw new IllegalArgumentException("Unknown FallingPlatform skin '" + value + "' (expected log or branch)");
            };
        }
    }

    private final TrapWarnings warnings;
    private boolean falling = false;
    private float fallDelay = -1f;
    private Torch torch;
    private final Consumer<WorldObject> spawner;
    private float fallVelocity = 0f;
    public static final float FALL_DISTANCE = 200f;
    private static final float FALL_DURATION = 1.5f;
    private static final int WARNING_TORCH_INTENSITY = 5;

    /** The log and the branch share the frame order intact, crumble1, crumble2; the branch's -left copies follow. */
    private static final int CRUMBLE_FRAME_COUNT = 3, CRUMBLE_FIRST = 1, CRUMBLE_SECOND = 2;
    private final Skin skin;
    private final TextureRegion[] crumbleFrames = new TextureRegion[CRUMBLE_FRAME_COUNT];

    public FallingPlatform(Assets assets, float x, float y, float w, float h, Consumer<WorldObject> spawner,
                           TrapWarnings warnings) {
        this(assets, x, y, w, h, spawner, warnings, Skin.LOG);
    }

    public FallingPlatform(Assets assets, float x, float y, float w, float h, Consumer<WorldObject> spawner,
                           TrapWarnings warnings, Skin skin) {
        this(assets, x, y, w, h, spawner, warnings, skin, false);
    }

    public FallingPlatform(Assets assets, float x, float y, float w, float h, Consumer<WorldObject> spawner,
                           TrapWarnings warnings, Skin skin, boolean mirror) {
        super(x, y, w, h, State.class);
        this.spawner = spawner;
        this.warnings = warnings;
        this.skin = skin;
        if (mirror && skin != Skin.BRANCH) throw new IllegalArgumentException("mirror applies to the branch skin only");
        SpriteSheets.Sheet sheet = skin == Skin.LOG ? SpriteSheets.TRAP_LOG : SpriteSheets.TRAP_ROTTEN_PERCH;
        int first = mirror ? CRUMBLE_FRAME_COUNT : 0;
        for (int i = 0; i < crumbleFrames.length; i++) crumbleFrames[i] = sheet.frame(assets, first + i, 0);
        setState(State.IDLE);
    }

    public boolean isFalling() {
        return falling;
    }

    public record Snapshot(float x, float y, boolean falling, float warningSeconds,
                           float fallSeconds, boolean facingRight, float animationTime,
                           boolean removed) { }

    public Snapshot snapshot() {
        return new Snapshot(position.x, position.y, falling, fallDelay, fallVelocity,
            facingRight, animationTime(), removed);
    }

    /** Restores platform progress and reconstructs only a live warning light. */
    public void restore(Snapshot snapshot) {
        removeWarningTorch();
        position.set(snapshot.x(), snapshot.y());
        falling = snapshot.falling();
        fallDelay = snapshot.warningSeconds();
        fallVelocity = snapshot.fallSeconds();
        facingRight = snapshot.facingRight();
        setState(falling ? State.FALLING : State.IDLE);
        restoreAnimationTime(snapshot.animationTime());
        removed = snapshot.removed();
        if (!removed && falling && fallDelay > 0f) {
            torch = new Torch(centerX(), centerY(), size.x, size.y, WARNING_TORCH_INTENSITY, "");
            spawner.accept(torch);
        }
    }

    public Skin skin() {
        return skin;
    }

    /** Intact until landed on; the first half of the warning shows the first crack, the rest and the fall the second. */
    public int crumbleFrame() {
        if (!falling) return 0;
        float total = warnings.duration(TrapWarnings.FALLING_PLATFORM_SECONDS);
        return fallDelay > total / 2f ? CRUMBLE_FIRST : CRUMBLE_SECOND;
    }

    @Override
    public void render(Batch batch) {
        batch.draw(crumbleFrames[crumbleFrame()], position.x, position.y, size.x, size.y);
    }

    public void collideWithActor() {
        if (!falling) {
            triggerFall();
        }
    }

    private void triggerFall() {
        falling = true;
        setState(State.FALLING);
        torch = new Torch(centerX(), centerY(), size.x, size.y, WARNING_TORCH_INTENSITY, "");
        spawner.accept(torch);
        fallDelay = warnings.duration(TrapWarnings.FALLING_PLATFORM_SECONDS);
    }

    @Override
    public void update(float dt) {
        tickAnimation(dt);
        if (fallDelay > 0) {
            fallDelay -= dt;
            if (fallDelay <= 0) {
                if (torch != null) torch.toggleFire(false);
                fallVelocity = 0.0001f; // marks "now actually falling"
            }
        } else if (fallVelocity > 0) {
            fallVelocity += dt;
            position.y += (FALL_DISTANCE / FALL_DURATION) * dt;
            if (fallVelocity >= FALL_DURATION) {
                removeFromParent();
                removeWarningTorch();
            }
        }
        if (torch != null) {
            torch.update(dt);
        }
    }

    private void removeWarningTorch() {
        if (torch == null) return;
        torch.removeFromParent();
        torch = null;
    }

    @Override
    public float getX() {
        return position.x;
    }

    @Override
    public float getY() {
        return position.y;
    }

    @Override
    public float getWidth() {
        return size.x;
    }

    @Override
    public float getHeight() {
        return size.y;
    }

    @Override
    public boolean isQuickSand() {
        return false;
    }

    @Override
    public boolean isPlatform() {
        return true;
    }

    @Override
    public boolean isWall() {
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isEscalator() {
        return false;
    }

    // ---- Tree branches (#349, D80): the two load checks of a rotten branch, mirrored in tools/check_tiled_links.py ----

    /** A support may be at most this much higher than the one the hero jumps from (a jump rises about 58 px, D35). */
    public static final float BRANCH_JUMP_RISE = 48f;
    /** The widest horizontal gap between two supports that the check lets a jump or a drop cross. */
    public static final float BRANCH_JUMP_GAP = 32f;
    /** How far beside a rotten branch a one-way platform must stay (a hero standing on its end overhangs it). */
    public static final float BRANCH_SIDE_MARGIN = 16f;
    /** The hero's body box inside the 48x48 frame, as FallingTrap pins it: where the room entrance puts the feet. */
    private static final float HERO_FRAME = 48f;

    /**
     * Why a rotten branch ({@code skin=branch}) may not stand where it is, or null. D69: no one-way platform lies under
     * it within its fall ({@link #FALL_DISTANCE}, the span widened by {@link #BRANCH_SIDE_MARGIN}), so a falling branch
     * never carries the hero onto a sound one. D26: a fallen branch stays fallen, and a fall on a climb usually does not
     * kill, so the room's goal (its exit or checkpoint) must be reachable from the entrance with every rotten branch
     * gone: over plain blocks and one-way platforms, each step at most {@link #BRANCH_JUMP_RISE} higher and
     * {@link #BRANCH_JUMP_GAP} across. Ladders, lifts and other falling platforms are not counted as a way up.
     * <p>
     * <b>What the D26 part does not model:</b> it is a coarse reachability over top edges, not a movement simulation. Every
     * plain block counts as a step (a wall's top or the room's ceiling slab included), and nothing blocks a jump: no
     * ceiling, wall or block in the way, no head room, no run-up length. So it can pass a room the hero cannot actually
     * climb; it only catches a rotten branch that is the sole support in reach. Trust a room's D26 claim only together with
     * a route test that climbs it with real input (for {@code forest-1-tree}: {@code TreeClimbTest}). Mirrored in
     * {@code tools/check_tiled_links.py} ({@code branch_problems}); the two must change together.
     */
    public static String branchProblem(TiledObject o, List<TiledObject> collisions, Rectangle room, float entranceX,
                                       float entranceY, List<Rectangle> goals) {
        if (!(o.width > 0) || !(o.height > 0)) return "needs positive bounds";
        for (TiledObject c : collisions) {
            if (!"Platform".equals(c.type) || !inside(room, c)) continue;
            boolean under = c.y > o.y && c.y - o.y <= FALL_DISTANCE;
            boolean beside = c.x < o.x + o.width + BRANCH_SIDE_MARGIN && c.x + c.width > o.x - BRANCH_SIDE_MARGIN;
            if (under && beside)
                return "falls past the one-way platform [" + c.id + "] at (" + (int) c.x + ", " + (int) c.y + ") (D69: no rotten branch above a sound one)";
        }
        List<Support> supports = new ArrayList<>();
        for (TiledObject c : collisions) {
            boolean plain = c.type == null || c.type.isEmpty();
            if ((plain || "Platform".equals(c.type)) && inside(room, c)) supports.add(new Support(c.x, c.x + c.width, c.y));
        }
        float feet = entranceY + HERO_FRAME, bodyLeft = entranceX + FallingTrap.HERO_BODY_OFFSET_X;
        float bodyRight = bodyLeft + FallingTrap.HERO_BODY_WIDTH;
        boolean[] reached = new boolean[supports.size()];
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < supports.size(); i++) {
            Support s = supports.get(i);
            if (Math.abs(s.top() - feet) <= 1f && s.left() < bodyRight && s.right() > bodyLeft) { reached[i] = true; open.add(i); }
        }
        if (open.isEmpty()) return "has no support under the room entrance to start the D26 check from";
        while (!open.isEmpty()) {
            Support from = supports.get(open.remove(open.size() - 1));
            for (int j = 0; j < supports.size(); j++) {
                Support to = supports.get(j);
                float gap = Math.max(0f, Math.max(to.left() - from.right(), from.left() - to.right()));
                if (!reached[j] && to.top() >= from.top() - BRANCH_JUMP_RISE && gap <= BRANCH_JUMP_GAP) { reached[j] = true; open.add(j); }
            }
        }
        for (Rectangle goal : goals)
            for (int i = 0; i < supports.size(); i++) {
                Support s = supports.get(i);
                if (reached[i] && s.top() >= goal.y && s.top() <= goal.y + goal.height + 1f && s.left() < goal.x + goal.width && s.right() > goal.x)
                    return null;
            }
        return "is the only way up: the room's goal cannot be reached with every rotten branch fallen (D26)";
    }

    /** A walkable top for the D26 check: a block's or a one-way platform's horizontal span and its top edge. */
    private record Support(float left, float right, float top) { }

    private static boolean inside(Rectangle room, TiledObject c) {
        float cx = c.x + c.width / 2f, cy = c.y + c.height / 2f;
        return cx >= room.x && cx < room.x + room.width && cy >= room.y && cy < room.y + room.height;
    }
}
