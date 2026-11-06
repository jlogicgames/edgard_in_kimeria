package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Explosive traps (D47, #233): the exploding stump ({@code ExplosiveStump}, 16x16) and the exploding root
 * ({@code ExplosiveRoot}, 16x24). They replace the glass-bottle bomb.
 *
 * <p>Sequence, one way: {@code IDLE} -> {@code WINDUP} -> {@code BLAST} -> {@code SPENT}.
 * <ul>
 *   <li><b>Touch</b>: the hero's body overlapping the trigger box (the bomb's inner 10x13 box) ignites it.
 *   <li><b>Distance</b>: a fireball or a thrown stone that reaches the same rectangle ignites it
 *       ({@link #igniteFromAfar()}). It starts the very same wind-up with the full warning: a hit from afar
 *       shortens nothing, so the hero can still step away, and a hit on a trap already winding up or spent
 *       changes nothing (and costs no ammunition: the projectile flies on).
 *   <li><b>Wind-up</b>: the shared D7 warning ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the
 *       assist, sampled once when it starts) through the three wind-up frames (cracks, smoke, glow). It always
 *       runs to the end: leaving the trigger box does not cancel it.
 *   <li><b>Blast</b>: one {@value #BLAST_SIZE} px square centred on the object, checked once at the instant of
 *       detonation; the hero inside it is killed ({@code kill}, i.e. {@code player.collidedWithActor(false)}
 *       as the old bomb did). The twelve blast frames then play for {@link #BLAST_SECONDS} without hurting.
 *   <li><b>Spent</b>: charred, harmless, non-solid scenery that stays where it is until the room is rebuilt
 *       by a retry or Restart Level; leaving the room alive and coming back keeps it spent (room-state retention, as the bomb).
 * </ul>
 * Timers tick inside {@link #update(float)} only, so pause stops them. Like every hazard the hero is only seen
 * while {@code Player.canLeaveRoom()} (the supplier returns null otherwise), but a running wind-up keeps going.
 *
 * <p>Authoring (docs/ROOMS.md): every explosive needs a <em>bypass</em>, a way past that never touches it, see
 * {@link #problem}. tools/check_tiled_links.py mirrors the rules for maps.
 */
public class ExplosiveTrap extends Entity {
    public enum Kind {
        /** 16x16 stump. */
        STUMP(SpriteSheets.TRAP_STUMP),
        /** 16x24 root. */
        ROOT(SpriteSheets.TRAP_ROOT);

        final SpriteSheets.Sheet sheet;

        Kind(SpriteSheets.Sheet sheet) { this.sheet = sheet; }
    }

    public enum Phase { IDLE, WINDUP, BLAST, SPENT }

    /** The existing bomb blast: a 64x64 square centred on the object. */
    public static final float BLAST_SIZE = 64f;
    /** How long the twelve blast frames play (cosmetic: the blast hurts only at the instant of detonation). */
    public static final float BLAST_SECONDS = 0.4f;
    private static final float TRIGGER_FRAME = 16f, TRIGGER_INSET_X = 3f, TRIGGER_INSET_TOP = 2f, TRIGGER_WIDTH = 10f, TRIGGER_HEIGHT = 13f;
    /** Author-tuned: how far UP (world Y is down) the blast frame is lifted; 48 = 64 px frame minus a 16 px stump (looks only). */
    private static final float BLAST_DRAW_OFFSET_Y = 48f;
    private static final int FRAME_IDLE = 0, FRAME_WINDUP_FIRST = 1, WINDUP_FRAMES = 3, FRAME_SPENT = 4, BLAST_FRAMES = 12, SHEET_FRAMES = 5;

    private final Kind kind;
    private final TrapWarnings warnings;
    private final Supplier<Rectangle> heroBody;
    private final Runnable kill, onBlast;
    private final TextureRegion[] frames = new TextureRegion[SHEET_FRAMES], blast = new TextureRegion[BLAST_FRAMES];
    private Phase phase = Phase.IDLE;
    private float phaseTime, warningTotal;

    public ExplosiveTrap(Assets assets, TrapWarnings warnings, Kind kind, float x, float y, float w, float h,
                         Supplier<Rectangle> heroBody, Runnable kill, Runnable onBlast) {
        super(x, y, w, h);
        this.kind = kind;
        this.warnings = warnings;
        this.heroBody = heroBody;
        this.kill = kill;
        this.onBlast = onBlast;
        for (int i = 0; i < frames.length; i++) frames[i] = kind.sheet.frame(assets, i, 0);
        for (int i = 0; i < blast.length; i++) blast[i] = SpriteSheets.TRAP_BLAST.frame(assets, i, 0);
    }

    public static boolean isExplosive(String type) { return "ExplosiveStump".equals(type) || "ExplosiveRoot".equals(type); }

    public static ExplosiveTrap fromMap(Assets assets, TrapWarnings warnings, TiledObject o,
                                        Supplier<Rectangle> heroBody, Runnable kill, Runnable onBlast) {
        return new ExplosiveTrap(assets, warnings, "ExplosiveRoot".equals(o.type) ? Kind.ROOT : Kind.STUMP,
            o.x, o.y, o.width, o.height, heroBody, kill, onBlast);
    }

    public Kind kind() { return kind; }
    public Phase phase() { return phase; }
    public boolean isIntact() { return phase == Phase.IDLE; }
    public boolean isSpent() { return phase == Phase.SPENT; }
    /** Seconds of the wind-up left (0 outside it). */
    public float warningRemaining() { return phase == Phase.WINDUP ? Math.max(0f, warningTotal - phaseTime) : 0f; }
    public float warningTotal() { return warningTotal; }

    /**
     * The trigger box: the same inner box as the old bomb's activation hitbox (10x13 inside a 16x16 frame, 3 px in per side
     * and 2 px down from the top, scaled with the object), excluding the empty corners of the art. Touching it, or hitting
     * it from afar, ignites the trap.
     */
    public Rectangle triggerBox() {
        float sx = size.x / TRIGGER_FRAME, sy = size.y / TRIGGER_FRAME;
        return new Rectangle(position.x + TRIGGER_INSET_X * sx, position.y + TRIGGER_INSET_TOP * sy,
            TRIGGER_WIDTH * sx, TRIGGER_HEIGHT * sy);
    }

    /** State kept when the hero leaves the room alive and returns (like the old bomb): a spent trap stays spent. */
    public record Snapshot(Phase phase, float phaseTime, float warningTotal) { }

    public Snapshot snapshot() { return new Snapshot(phase, phaseTime, warningTotal); }

    public void restore(Snapshot saved) {
        phase = saved.phase();
        phaseTime = saved.phaseTime();
        warningTotal = saved.warningTotal();
    }

    /** The blast square, centred on the object. */
    public Rectangle blastBox() { return blastBoxOf(position.x, position.y, size.x, size.y); }

    static Rectangle blastBoxOf(float x, float y, float w, float h) {
        return new Rectangle(x + w / 2f - BLAST_SIZE / 2f, y + h / 2f - BLAST_SIZE / 2f, BLAST_SIZE, BLAST_SIZE);
    }

    /** True when {@code area} is inside the blast square: a place the hero must be able to stand must not be. */
    public boolean everLethalIn(Rectangle area) { return blastBox().overlaps(area); }

    /**
     * A fireball or a thrown stone hit the trigger box. Returns true when this started the wind-up (the
     * projectile is then spent); false when the trap was already winding up or spent (it flies on).
     */
    public boolean igniteFromAfar() { return ignite(); }

    private boolean ignite() {
        if (phase != Phase.IDLE) return false;
        phase = Phase.WINDUP;
        phaseTime = 0f;
        warningTotal = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        return true;
    }

    @Override
    public void update(float dt) {
        if (isRemoved()) return;
        Rectangle hero = heroBody.get();
        if (phase == Phase.IDLE && hero != null && hero.overlaps(triggerBox())) ignite();
        if (phase == Phase.WINDUP) {
            phaseTime += dt;
            if (phaseTime >= warningTotal) {
                phase = Phase.BLAST;
                phaseTime -= warningTotal;
                onBlast.run();
                if (hero != null && hero.overlaps(blastBox())) kill.run();
            }
        } else if (phase == Phase.BLAST) {
            phaseTime += dt;
            if (phaseTime >= BLAST_SECONDS) phase = Phase.SPENT;
        }
    }

    /** Frame of the object's own sheet for the current phase. */
    public int frameIndex() {
        return switch (phase) {
            case IDLE -> FRAME_IDLE;
            case WINDUP -> FRAME_WINDUP_FIRST + Math.min(WINDUP_FRAMES - 1, (int) (phaseTime / warningTotal * WINDUP_FRAMES));
            case BLAST, SPENT -> FRAME_SPENT;
        };
    }

    /** Blast frame (0..11) while the blast plays, else -1. */
    public int blastFrameIndex() {
        return phase == Phase.BLAST ? Math.min(BLAST_FRAMES - 1, (int) (phaseTime / BLAST_SECONDS * BLAST_FRAMES)) : -1;
    }

    @Override
    public void render(Batch batch) {
        float w = kind.sheet.width(), h = kind.sheet.height();
        float spriteBottom = position.y + size.y - h;
        batch.draw(frames[frameIndex()], position.x + (size.x - w) / 2f, spriteBottom, w, h);
        int b = blastFrameIndex();
        if (b >= 0) {
            Rectangle box = blastBox();
            // 32x32 art drawn at twice its size (64 px). World Y grows DOWNWARD (camera.setToOrtho(true)), so a frame
            // drawn at spriteBottom hangs 64 px below the sprite's top: 48 px under a 16 px stump. Subtracting
            // BLAST_DRAW_OFFSET_Y lifts it so its bottom row sits on the stump's bottom (author-tuned, #255).
            // Looks only: the hit square (blastBox) stays centred on the object.
            batch.draw(blast[b], box.x, spriteBottom - BLAST_DRAW_OFFSET_Y, box.width, box.height);
        }
    }

    /**
     * Placement rules for {@code ExplosiveStump}/{@code ExplosiveRoot}; returns a message or null.
     *
     * <p><b>Bypass</b> (D47): a hero with no ammunition must be able to get past without touching the trap. The
     * trap must therefore stand on open floor with the thorn traps' jump envelope clear ({@link ThornTrap#openFloorProblem}):
     * floor under its footprint and {@link ThornTrap#MIN_ZONE} px either side (take-off and landing), {@link ThornTrap#HEADROOM}
     * px of free air over all of it (no ceiling, ledge, falling platform, wall or ladder: not a one-tile corridor, not under a
     * low roof), the envelope inside the room. The hero jumps over the trigger box and lands beyond; the blast is
     * not a factor because nothing was ignited. The blast square must also lie inside the room and must not cover a
     * place the hero has to stand ({@code standing}: entrance, exit, checkpoints), because a hit from afar may ignite
     * it while the hero waits there. An author may add an explicit route (a higher ledge, an alternative platform
     * line) on top; it is never counted instead of the floor bypass.
     */
    public static String problem(TiledObject o, List<TiledObject> collisions, List<TiledObject> spawn,
                                 Rectangle room, Map<String, Rectangle> standing) {
        if (!(o.width > 0) || !(o.height > 0)) return "needs positive bounds";
        String open = ThornTrap.openFloorProblem(o, 0f, 0f, collisions, spawn, room);
        if (open != null) return open;
        Rectangle blast = blastBoxOf(o.x, o.y, o.width, o.height);
        if (blast.x < room.x || blast.y < room.y || blast.x + blast.width > room.x + room.width
            || blast.y + blast.height > room.y + room.height) return "has its blast square leaving the room";
        for (Map.Entry<String, Rectangle> place : standing.entrySet())
            if (blast.overlaps(place.getValue())) return "has its blast square over the room " + place.getKey();
        return null;
    }
}
