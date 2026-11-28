package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.tiled.ForegroundGroup;
import com.jlogicsoftware.kimeria.tiled.SceneryLayer;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;

/**
 * Draws a map's scenery layers behind the play layer and its room's foreground in front of the world (docs/ART_DIRECTION.md,
 * "Forest look and parallax layers"; #236). The back pass runs before {@link Level#renderBackground}, the front pass after the
 * fairy, the HUD stays its own later pass.
 *
 * <p>The maths is the card's, in world coordinates (the camera is Y-down): with the camera's true left edge {@code camX} and top edge
 * {@code camTop}, a point of a layer of factor {@code f} sits on screen at {@code x - f * (camX - originX)} horizontally and
 * {@code (y - originY) - f * (camTop - originY)} vertically, so a factor of 1 is the play layer. Scenery layers cover the whole map,
 * their {@code originX} is 0; a foreground group's is its room's left edge ({@code roomx}) and its objects are positioned relative to
 * that room. {@code originY} is the room's floor-camera top (see {@link #originY}), where every layer shows as authored. A clouds layer ({@code driftx}, {@code wrapx}) also moves by its drift,
 * accumulated over game time ({@code update} runs inside {@code Level.update}: it stops while the game is paused, during a room fade and
 * after black in a death fade, and slows with the death slow motion) and wrapped, independent of the camera.
 *
 * <p>No offset is snapped to whole pixels (D154, #678): the camera moves by fractions of a pixel, and a snapped offset would step every
 * layer back and forth by up to a pixel while it does. The cloud drift is not snapped either, so the clouds glide (the world is drawn
 * at screen resolution, on the screen or in the scene buffer), and the two copies of a drifting layer are exactly {@code wrapx} apart,
 * so the seam stays closed.
 *
 * <p>Scenery layers use the room's {@code originY} (a vertical shift measured from the floor camera), so across a room change a
 * map-wide layer holds its place only while the rooms share a floor camera top, as every forest room does (rooms of 368 px at y 0, top
 * 8; {@code ParallaxRendererTest.everyForestRoomSharesOneFloorCameraTop} pins it); a room with another floor camera top would shift them
 * on the change, which {@code RoomFade} would hide. Horizontally they never jump (origin 0). The cull rectangle is the view grown by one
 * tile on every side, because the screen shake moves the camera by up to 5 px after {@code visibleWorldRect} was taken.
 *
 * <p>Nothing here allocates per frame: regions are resolved at load, loops are indexed.
 */
public final class ParallaxRenderer {
    private static final float SAME_ROOM_X = 0.5f;
    /** Tiles drawn past each edge of the view: the camera's shake (up to 5 px) is applied after the view rectangle is taken. */
    private static final int CULL_TILES = 1;
    private final TiledMapData map;
    /** Accumulated drift in px per scenery layer, kept below the layer's wrap length. */
    private final float[] drift;

    public ParallaxRenderer(TiledMapData map) {
        this.map = map;
        drift = new float[map.sceneryLayers.size()];
    }

    public boolean isEmpty() { return map.sceneryLayers.isEmpty() && map.foregroundGroups.isEmpty(); }

    /**
     * World x of a layer point whose x from {@code originX} is {@code x}: screen x {@code x - factor * (camX - originX)} plus the
     * camera's left edge, not snapped; written so that a factor of 1 gives exactly {@code originX + x}.
     */
    public static float worldX(float x, double factor, float camX, double originX) { return x + (float) (originX + (1 - factor) * (camX - originX)); }

    /**
     * World y of a layer point whose map y is {@code y}: screen y {@code (y - originY) - factor * (camTop - originY)} plus the camera's
     * top, not snapped; written so that a factor of 1 gives exactly {@code y}.
     */
    public static float worldY(float y, double factor, float camTop, double originY) { return y + (float) ((1 - factor) * (camTop - originY)); }

    /**
     * The floor camera's top of a room (room y plus its height minus the view's; a room shorter than the view is centred in it): the
     * camera's top while the hero stands on the floor, the reference every layer's vertical shift is measured from, so at that camera
     * every layer shows as authored. It is in map coordinates, like a foreground group's {@code y}.
     */
    public static float originY(Rectangle room, float viewHeight) {
        return FallingTrapLayout.floorCameraTop(room, viewHeight);
    }

    /**
     * A cloud layer's offset in px at an accumulated drift: kept fractional (never snapped) and wrapped into [0, {@code wrap}). The
     * world is drawn at screen resolution, so a fractional offset glides by sub-pixel steps instead of jumping a whole world pixel.
     */
    public static float driftOffset(float driftPx, float wrap) {
        if (wrap <= 0) return 0f;
        float offset = driftPx % wrap;
        if (offset < 0f) offset += wrap;
        if (offset >= wrap) offset -= wrap;   // -1e-6f + 1024f rounds to 1024f
        return offset;
    }

    /** The fractional drift of scenery layer {@code index} now (0 for a still layer). */
    float driftOf(int index) { return driftOffset(drift[index], map.sceneryLayers.get(index).wrapX); }

    /** Advances the clouds' drift by real time. */
    public void update(float dt) {
        for (int i = 0; i < drift.length; i++) {
            SceneryLayer layer = map.sceneryLayers.get(i);
            if (layer.driftX == 0 || layer.wrapX <= 0) continue;
            drift[i] += layer.driftX * dt;
            if (drift[i] >= layer.wrapX) drift[i] -= layer.wrapX;
            else if (drift[i] < 0) drift[i] += layer.wrapX;
        }
    }

    /** World x of a scenery layer's origin (its cell 0) for the camera's true left edge (D154, not snapped), before any cloud drift. */
    public static float layerBaseX(SceneryLayer layer, float camX) { return worldX(layer.offsetX, layer.parallaxX, camX, 0); }

    /** World y of a scenery layer's origin for the camera's true top edge (D154, not snapped) in the room whose floor camera top is {@code originY}. */
    public static float layerBaseY(SceneryLayer layer, float camTop, float originY) { return worldY(layer.offsetY, layer.parallaxY, camTop, originY); }

    /** What is drawn between two scenery layers: called after each layer, with the layer's origin in the world (the wind's glints, #593). */
    public interface LayerOverlay {
        void afterLayer(Batch batch, int layerIndex, float baseX, float baseY);
    }

    /** The scenery layers, in file order, for the camera view {@code view} and the room whose floor camera top is {@code originY}. */
    public void renderBack(Batch batch, Rectangle view, float originY) { renderBack(batch, view, originY, null); }

    /** As {@link #renderBack(Batch, Rectangle, float)}, with {@code overlay} (may be null) drawn after every layer, so a later layer covers it. */
    public void renderBack(Batch batch, Rectangle view, float originY, LayerOverlay overlay) {
        float packed = batch.getPackedColor();
        for (int i = 0; i < drift.length; i++) {
            SceneryLayer layer = map.sceneryLayers.get(i);
            float baseX = layerBaseX(layer, view.x);
            float baseY = layerBaseY(layer, view.y, originY);
            if (layer.opacity != 1f) batch.setColor(1f, 1f, 1f, layer.opacity);
            if (layer.wrapX > 0) {
                float shiftX = baseX + driftOffset(drift[i], layer.wrapX);
                drawLayer(batch, layer, shiftX - layer.wrapX, baseY, view);
                drawLayer(batch, layer, shiftX, baseY, view);
            } else {
                drawLayer(batch, layer, baseX, baseY, view);
            }
            if (layer.opacity != 1f) batch.setPackedColor(packed);
            if (overlay != null) {
                overlay.afterLayer(batch, i, baseX, baseY);
                batch.setPackedColor(packed);
            }
        }
    }

    private void drawLayer(Batch batch, SceneryLayer layer, float originX, float originY, Rectangle view) {
        int tw = map.tileWidth, th = map.tileHeight;
        int c0 = Math.max(0, (int) Math.floor((view.x - originX) / tw) - CULL_TILES);
        int c1 = Math.min(layer.columns - 1, (int) Math.floor((view.x + view.width - originX) / tw) + CULL_TILES);
        int r0 = Math.max(0, (int) Math.floor((view.y - originY) / th) - CULL_TILES);
        int r1 = Math.min(layer.rows - 1, (int) Math.floor((view.y + view.height - originY) / th) + CULL_TILES);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                TextureRegion region = layer.cell(c, r);
                if (region != null) batch.draw(region, originX + c * tw, originY + r * th, tw, th);
            }
        }
    }

    /** The foreground groups of the room whose left edge is {@code room.x}, over the world in file order, each at its own factor. */
    public void renderFront(Batch batch, Rectangle view, Rectangle room, float originY) {
        float cullX = CULL_TILES * map.tileWidth, cullY = CULL_TILES * map.tileHeight;
        for (int g = 0; g < map.foregroundGroups.size(); g++) {
            ForegroundGroup group = map.foregroundGroups.get(g);
            if (Math.abs(group.roomX - room.x) > SAME_ROOM_X) continue;
            float baseX = worldX(0, group.factorX, view.x, group.roomX);   // the objects are relative to the room: screen x = x - f * (camX - roomX)
            float baseY = worldY(0, group.factorY, view.y, originY);
            for (int i = 0; i < group.count; i++) {
                float x = baseX + group.x[i], y = baseY + group.y[i];
                if (x > view.x + view.width + cullX || x + group.width[i] < view.x - cullX) continue;
                if (y > view.y + view.height + cullY || y + group.height[i] < view.y - cullY) continue;
                float w = group.width[i], h = group.height[i];
                // a negative size mirrors the region and extends it to the left (up), so a flipped piece is drawn from its far edge
                batch.draw(group.regions[i], group.flipX[i] ? x + w : x, group.flipY[i] ? y + h : y, group.flipX[i] ? -w : w, group.flipY[i] ? -h : h);
            }
        }
    }
}
