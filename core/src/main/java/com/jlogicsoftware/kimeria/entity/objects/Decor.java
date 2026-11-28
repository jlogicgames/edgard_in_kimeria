package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.Locale;

/**
 * Cosmetic scenery (#239 step 4): a hand-placed sprite that plays its brush-past frames once when the hero walks
 * through it. No collision, not saved, never read by gameplay; {@code Level} draws it behind everything but the
 * background. Tiled type {@code Decor}, properties {@code kind} (see {@link #KINDS}) and optional boolean {@code mirror}.
 * <p>
 * A {@code still} kind (#299) has one frame, ignores {@link #notice} and {@link #update}, and so never moves.
 * <p>
 * Frame 0 is the rest pose; frames 1.. play at {@link #FPS} and the object returns to frame 0. It restarts only when
 * finished, so it cannot flicker. Timing depends only on hero position and {@code dt}.
 * <p>
 * Y grows downward: {@code position.y + size.y} is the floor line. The sprite is drawn at its native size, centred on the
 * rectangle and standing on its bottom edge. A {@code hangs} kind (a crown, #421, #635) is placed by its top edge instead (the
 * room's top or under a solid, tools/check_tiled_links.py); its rectangle is its sprite's size, so it is drawn the same way.
 */
public final class Decor extends Entity {
    /** Frames per strip: idle, brush1, brush2, settle (tools/sprite_layouts.py DECOR_SHEETS). */
    public static final int FRAMES = 4;
    public static final float FPS = 10f;
    /**
     * The visible body of the bush inside its 32x24 frame (opaque pixels of every frame: x 3..30, y 6..24, measured on
     * {@code decor-bush.png}). The brush starts only when the hero's body box overlaps this body, not the whole rectangle.
     */
    public static final float BODY_INSET_LEFT = 3f, BODY_INSET_RIGHT = 2f, BODY_INSET_TOP = 6f;
    /** Horizontal hero speed (px/s) below which the hero counts as standing. */
    public static final float MIN_SPEED = 1f;
    /**
     * One kind: its sheet, its frame count, whether it {@code brushes} (plays its brush-past frames when the hero walks
     * through it, D64) and whether it is {@code still}. A still kind (#299, the pale look-alike mushroom) has one frame and
     * never reacts to the hero, so motion stays the cue that an active mushroom is alive. A kind that neither brushes nor is
     * still (the trunk, #349) is plain scenery: one frame, no reaction, and none of the look-alike's placement rules. A kind
     * that {@code hangs} (a crown, #421, #635) is attached by its top edge, not its bottom; read only by tools/check_tiled_links.py.
     * A kind drawn {@code behindTiles} (the trunk, D122) is drawn before the play tiles, so a tree passes behind a ledge.
     */
    private record Kind(SpriteSheets.Sheet sheet, int frames, boolean brushes, boolean still, boolean hangs, boolean behindTiles) { }

    /**
     * One entry per kind: a new kind is one sheet and one table entry. The names and the {@code still} and {@code hangs} flags
     * are read by tools/check_tiled_links.py, which keeps still kinds away from active mushrooms, hint zones and pickups, and
     * stands every kind on a solid except a hanging one, which it hangs from the room's top or a solid's underside.
     */
    private static final java.util.Map<String, Kind> KINDS = java.util.Map.ofEntries(
        java.util.Map.entry("bush", new Kind(SpriteSheets.DECOR_BUSH, FRAMES, true, false, false, false)),
        java.util.Map.entry("lookalike", new Kind(SpriteSheets.DECOR_LOOKALIKE, 1, false, true, false, false)),
        // #349 (D80): the trunk behind tree branches. No brush, and not `still`, which means a look-alike mushroom. D122: every
        // branch grows from one, and it is drawn behind the play tiles (a ledge in front of it hides it).
        java.util.Map.entry("trunk", new Kind(SpriteSheets.DECOR_TRUNK, 1, false, false, false, true)),
        // D122: a dead tree snapped off 200 px up, where a full trunk would cross the moon; drawn like the trunk.
        java.util.Map.entry("snag", new Kind(SpriteSheets.DECOR_SNAG, 1, false, false, false, true)),
        // #635: the second trunk, a hollow and a burl on the same bark; a trunk kind like the trunk.
        java.util.Map.entry("trunk-knot", new Kind(SpriteSheets.DECOR_TRUNK_KNOT, 1, false, false, false, true)),
        // #635 (D162): the leaf crowns over a trunk, hung from the room's top and centred on the trunk (they replace the 96x40
        // crown of #421): weeping, broad and round, each also drawn mirrored.
        java.util.Map.entry("crown-droop", new Kind(SpriteSheets.DECOR_CROWN_DROOP, 1, false, false, true, false)),
        java.util.Map.entry("crown-broad", new Kind(SpriteSheets.DECOR_CROWN_BROAD, 1, false, false, true, false)),
        java.util.Map.entry("crown-round", new Kind(SpriteSheets.DECOR_CROWN_ROUND, 1, false, false, true, false)),
        // The same three in the fine-clump style (D162, author 2026-11-28: both styles stay, some trees take each).
        java.util.Map.entry("crown-droop-fine", new Kind(SpriteSheets.DECOR_CROWN_DROOP_FINE, 1, false, false, true, false)),
        java.util.Map.entry("crown-broad-fine", new Kind(SpriteSheets.DECOR_CROWN_BROAD_FINE, 1, false, false, true, false)),
        java.util.Map.entry("crown-round-fine", new Kind(SpriteSheets.DECOR_CROWN_ROUND_FINE, 1, false, false, true, false)));

    public static java.util.Set<String> kindNames() { return KINDS.keySet(); }

    /** Whether a kind never animates and never reacts ({@code lookalike}); an unknown kind is not still. */
    public static boolean isStillKind(String kind) {
        Kind k = KINDS.get(kind.toLowerCase(Locale.ROOT));
        return k != null && k.still();
    }

    private final String kind;
    private final SpriteSheets.Sheet sheet;
    private final boolean still, brushes, behindTiles;
    private final TextureRegion[] frames;
    private final boolean mirror;
    private boolean playing, leanLeft;
    private float elapsed;

    public Decor(Assets assets, String kind, float x, float y, float w, float h, boolean mirror) {
        super(x, y, w, h);
        Kind k = KINDS.get(kind.toLowerCase(Locale.ROOT));
        if (k == null) throw new IllegalArgumentException("Unknown decor kind '" + kind + "', known: " + KINDS.keySet());
        this.kind = kind.toLowerCase(Locale.ROOT);
        sheet = k.sheet();
        still = k.still();
        brushes = k.brushes();
        behindTiles = k.behindTiles();
        frames = new TextureRegion[k.frames()];
        for (int i = 0; i < frames.length; i++) frames[i] = sheet.frame(assets, i, 0);
        this.mirror = mirror;
    }

    public static Decor fromMap(Assets assets, TiledObject o, String label) {
        if (!(o.width > 0 && o.height > 0)) throw new IllegalArgumentException("Decor " + label + ": needs a positive size");
        try {
            return new Decor(assets, o.prop("kind", ""), o.x, o.y, o.width, o.height, o.propBool("mirror", false));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Decor " + label + ": " + e.getMessage());
        }
    }

    /**
     * Starts the brush when the hero's body box overlaps the bush's visible body (the rectangle inset by
     * {@link #BODY_INSET_LEFT}, {@link #BODY_INSET_RIGHT} and {@link #BODY_INSET_TOP}; the bottom is the floor) and the
     * hero moves horizontally ({@code heroVelocityX}, px/s); the sprite leans the way the hero is heading. Touching
     * edges do not count. Does nothing while playing.
     */
    public void notice(Rectangle heroBody, float heroVelocityX) {
        if (!brushes || playing || Math.abs(heroVelocityX) < MIN_SPEED) return;
        if (heroBody.x >= position.x + size.x - BODY_INSET_RIGHT || heroBody.x + heroBody.width <= position.x + BODY_INSET_LEFT
            || heroBody.y >= position.y + size.y || heroBody.y + heroBody.height <= position.y + BODY_INSET_TOP) return;
        playing = true;
        elapsed = 0f;
        leanLeft = heroVelocityX < 0;
    }

    public boolean playing() { return playing; }
    /** The map's {@code kind}, lower case (a key of {@link #KINDS}: {@code bush}, {@code trunk}, {@code crown-droop}, ...). */
    public String kind() { return kind; }
    /** True for a kind that never animates (#299). */
    public boolean isStill() { return still; }
    /** True for a kind drawn before the play tiles (the trunk, D122): a ledge in front of a tree hides it. */
    public boolean behindTiles() { return behindTiles; }
    /** True when the frame is drawn flipped left to right (the map's {@code mirror}). */
    public boolean mirror() { return mirror; }
    /** True for a kind that plays its brush-past frames when the hero walks through it (the bush, D64). */
    public boolean brushes() { return brushes; }
    /** The frame drawn now: 0 at rest, then 1..frames-1 while playing. */
    public int frameIndex() { return playing ? Math.min(1 + (int) (elapsed * FPS), frames.length - 1) : 0; }
    public int frameCount() { return frames.length; }

    @Override
    public void update(float dt) {
        if (!brushes || !playing) return;
        elapsed += dt;
        if (elapsed * FPS >= frames.length - 1) { playing = false; elapsed = 0f; }
    }

    @Override
    public void render(Batch batch) {
        float w = sheet.width(), h = sheet.height();
        float x = position.x + (size.x - w) / 2f, y = position.y + size.y - h;
        // The strip is drawn leaning right; mirroring turns it to lean left (heading) and `mirror` flips the whole bush.
        if ((playing && leanLeft) != mirror) batch.draw(frames[frameIndex()], x + w, y, -w, h);
        else batch.draw(frames[frameIndex()], x, y, w, h);
    }
}
