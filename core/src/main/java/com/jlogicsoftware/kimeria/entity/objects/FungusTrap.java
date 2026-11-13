package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
 * The shared rules of the honey fungus pair (D63, #295): a tuft of mushrooms on the floor that glows gently (brightness only) until
 * the hero's body enters its trigger zone, swells for the shared warning ({@link TrapWarnings#DEFAULT_WARNING_SECONDS},
 * longer with the assist; never a literal), bursts once, and is spent for good. Leaving the zone does not cancel the
 * swell, and nothing re-arms it (D26): the room has to be rebuilt (retry, Restart Level, room re-entry).
 * <p>
 * The two subclasses differ only in the burst: {@link HoneyFungus} releases harmless golden particles,
 * {@link FalseHoneyFungus} releases a lethal sprite cloud. Their idle glow is the same, from the object's own clock,
 * with no randomness and no dependence on the hero, so a live mushroom can be told from a static one.
 * <p>
 * Every timer ticks in {@link #update} only, which {@code Level.update} calls, so pause freezes it. The hero is seen
 * only while {@code Player.canLeaveRoom()} (the supplier returns null otherwise), like every other proximity trap.
 * World Y grows downward: {@code position.y + size.y} is the floor line and the zone reaches {@link #ZONE_ABOVE} px
 * above the footprint.
 */
public abstract class FungusTrap extends Entity {
    /** Where the mushroom is in its one-way sequence. {@code BURST} lasts {@link #burstSeconds()}. */
    public enum State { IDLE, SWELL, BURST, SPENT }

    /** Frames of the sheet: idle1..idle3, swell1, swell2, spent (tools/sprite_layouts.py FOREST_TRAP_SHEETS). */
    public static final int FRAME_COUNT = 6;
    public static final int FRAME_IDLE_COUNT = 3, FRAME_SWELL_FIRST = 3, FRAME_SWELL_SECOND = 4, FRAME_SPENT = 5;
    /** The one frame drawn while idle; idle2 and idle3 are currently unused (the idle is a smooth glow, not a frame swap). */
    public static final int FRAME_IDLE = 0;
    /** Seconds per full glow breath of the idle (dim, bright, dim); slow so it reads as a living glow, not a blink. */
    public static final float GLOW_PERIOD_SECONDS = 2.5f;
    /** The dimmest brightness factor of the idle glow (1 is the sprite as drawn); brightness only, never hue. */
    public static final float GLOW_MIN = 0.55f;
    /** Zone reach in px beyond each side of the footprint when the map does not say; also the smallest side of the lethal one. */
    public static final float DEFAULT_ZONE = 64f;
    /** Smallest used zone side of the harmless one (it needs no reaction time, only to be seen). */
    public static final float MIN_HARMLESS_ZONE = 24f;
    /** The zone starts this far above the footprint, so a hero on a ledge well above does not trigger it. */
    public static final float ZONE_ABOVE = 32f;
    /** Distance kept between the lethal cloud and an entrance, an exit or a checkpoint (the rule of D44 to D47). */
    public static final float CLEARANCE = 48f;

    private final SpriteSheets.Sheet sheet;
    private final TrapWarnings warnings;
    private final Supplier<Rectangle> heroBody;
    private final float zoneLeft, zoneRight;
    private final TextureRegion[] frames = new TextureRegion[FRAME_COUNT];
    private State state = State.IDLE;
    private float clock, stateTime, warningTotal;

    protected FungusTrap(Assets assets, SpriteSheets.Sheet sheet, TrapWarnings warnings, float x, float y, float w, float h,
                         float zoneLeft, float zoneRight, Supplier<Rectangle> heroBody) {
        super(x, y, w, h);
        this.sheet = sheet;
        this.warnings = warnings;
        this.heroBody = heroBody;
        this.zoneLeft = zoneLeft;
        this.zoneRight = zoneRight;
        for (int i = 0; i < frames.length; i++) frames[i] = sheet.frame(assets, i, 0);
    }

    /** Called once when the swell ends. */
    protected abstract void burst();

    /** How long the burst lasts before the mushroom is spent (the cloud's life, or the golden spores'). */
    protected abstract float burstSeconds();

    /** Draws what the burst adds to the sprite (the cloud, the particles). */
    protected abstract void renderBurst(Batch batch);

    /** Hook called every update after the state machine, for a subclass with its own moving parts. */
    protected void updateBurst(float dt) { }

    public State state() { return state; }
    public boolean isSpent() { return state == State.SPENT; }
    public float warningTotal() { return warningTotal; }
    public float clock() { return clock; }
    /** Seconds spent in the current state. */
    public float stateTime() { return stateTime; }
    protected float burstElapsed() { return state == State.BURST ? stateTime : 0f; }

    /** The area that starts the swell: the footprint widened by the zone, from {@value #ZONE_ABOVE} px above it to its floor. */
    public Rectangle triggerZone() { return ThornTrap.zoneOf(position.x, position.y, size.x, size.y, zoneLeft, zoneRight); }

    /**
     * The idle brightness factor at {@code time} seconds: a sine between {@link #GLOW_MIN} (at 0) and 1, one breath per
     * {@link #GLOW_PERIOD_SECONDS}. Smooth and deterministic in {@code time} alone.
     */
    public static float glow(float time) {
        float wave = (1f - MathUtils.cos(MathUtils.PI2 * time / GLOW_PERIOD_SECONDS)) / 2f;
        return GLOW_MIN + (1f - GLOW_MIN) * wave;
    }

    /** The brightness factor drawn now: the glow while idle, full brightness in the swell, burst and spent. */
    public float brightness() { return state == State.IDLE ? glow(clock) : 1f; }

    /** The sheet frame drawn now. */
    public int frameIndex() {
        return switch (state) {
            case IDLE -> FRAME_IDLE;
            case SWELL -> stateTime < warningTotal / 2f ? FRAME_SWELL_FIRST : FRAME_SWELL_SECOND;
            case BURST, SPENT -> FRAME_SPENT;
        };
    }

    private boolean heroInZone(Rectangle hero) {
        float left = position.x - zoneLeft, right = position.x + size.x + zoneRight, top = position.y - ZONE_ABOVE;
        return hero.x < right && left < hero.x + hero.width && hero.y < position.y + size.y && top < hero.y + hero.height;
    }

    @Override
    public void update(float dt) {
        if (isRemoved()) return;
        clock += dt;
        switch (state) {
            case IDLE -> {
                Rectangle hero = heroBody.get();
                if (hero != null && heroInZone(hero)) {
                    state = State.SWELL;
                    stateTime = 0f;
                    warningTotal = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
                }
            }
            case SWELL -> {
                stateTime += dt;
                if (stateTime >= warningTotal) {
                    stateTime -= warningTotal;
                    state = State.BURST;
                    burst();
                }
            }
            case BURST -> {
                stateTime += dt;
                if (stateTime >= burstSeconds()) state = State.SPENT;
            }
            case SPENT -> { }
        }
        updateBurst(dt);
    }

    @Override
    public void render(Batch batch) {
        float w = sheet.width(), h = sheet.height();
        Color tint = batch.getColor();
        float r = tint.r, g = tint.g, b = tint.b, a = tint.a, glow = brightness();
        batch.setColor(r * glow, g * glow, b * glow, a);
        batch.draw(frames[frameIndex()], position.x + (size.x - w) / 2f, position.y + size.y - h, w, h);
        batch.setColor(r, g, b, a);
        renderBurst(batch);
    }

    // -- authoring --

    /** The zone sides of a map object: {@code zone} for both, or {@code zoneLeft} / {@code zoneRight}; strict plain numbers. */
    public static float[] zoneOf(TiledObject o) {
        float zone = RhythmProps.number(o, "zone", DEFAULT_ZONE);
        return new float[]{RhythmProps.number(o, "zoneLeft", zone), RhythmProps.number(o, "zoneRight", zone)};
    }

    /**
     * Placement rules for both types; returns a message or null. The footprint must have positive bounds and stand on
     * open floor with its jump envelope clear (the thorn rule, so the lethal one always has a bypass: jump it, outrun
     * the swell, or wait out the cloud). The zone must not cover the entrance (the hero would start triggered). The lethal
     * one's zone sides are at least {@link ThornTrap#MIN_ZONE} px, and its cloud (inside the jump envelope, so inside the room)
     * must stay {@link #CLEARANCE} px away from every standing place ({@code standing}: entrance, exit, checkpoints).
     */
    public static String problem(TiledObject o, boolean lethal, List<TiledObject> collisions, List<TiledObject> spawn,
                                 Rectangle room, Map<String, Rectangle> standing) {
        if (!(o.width > 0) || !(o.height > 0)) return "needs positive bounds";
        float left, right;
        try {
            float[] sides = zoneOf(o);
            left = sides[0];
            right = sides[1];
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (!(left >= 0) || !(right >= 0) || left + right <= 0) return "needs a zone (zone, zoneLeft, zoneRight): not negative, at least one side used";
        float min = lethal ? ThornTrap.MIN_ZONE : MIN_HARMLESS_ZONE;
        if ((left > 0 && left < min) || (right > 0 && right < min))
            return "needs each used zone side to be at least " + (int) min + " px (zone, zoneLeft, zoneRight; 0 leaves a side unused)";
        String open = ThornTrap.openFloorProblem(o, left, right, collisions, spawn, room);
        if (open != null) return open;
        Rectangle zone = ThornTrap.zoneOf(o.x, o.y, o.width, o.height, left, right);
        Rectangle entrance = standing.get("entrance");
        if (entrance != null && zone.overlaps(entrance)) return "has its trigger zone over the room entrance (the hero would start triggered)";
        if (lethal) {
            Rectangle cloud = FalseHoneyFungus.cloudOf(o.x, o.y, o.width, o.height);
            Rectangle wide = new Rectangle(cloud.x - CLEARANCE, cloud.y - CLEARANCE, cloud.width + 2 * CLEARANCE, cloud.height + 2 * CLEARANCE);
            for (Map.Entry<String, Rectangle> place : standing.entrySet())
                if (wide.overlaps(place.getValue())) return "has its spore cloud within " + (int) CLEARANCE + " px of the room " + place.getKey();
        }
        return null;
    }
}
