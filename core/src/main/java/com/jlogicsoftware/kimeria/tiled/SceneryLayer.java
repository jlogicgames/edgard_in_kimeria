package com.jlogicsoftware.kimeria.tiled;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.Map;

/**
 * A tile layer that is scenery, not the play layer: a parallax layer (sky, stars, moon, clouds, far, grove, mid) or a factor-1 decor
 * layer carried past the room (play-above, play-below). It never feeds collision or {@link TiledMapData#backgroundGrid}.
 *
 * <p>A layer is scenery when it has a {@code parallaxx} or {@code parallaxy} attribute or the property {@code scenery=true}
 * (Tiled writes the attributes only for a factor other than 1, so the factor-1 layers carry the property). Cells are resolved to
 * texture regions once at load; drawing indexes them, never looks a gid up.
 */
public final class SceneryLayer {
    public String name = "";
    public int columns, rows;
    /** Row-major regions, null for an empty cell. */
    public TextureRegion[] cells;
    /** Horizontal and vertical parallax factors (Tiled {@code parallaxx}, {@code parallaxy}; default 1), as doubles so 0.15 is the mock's 0.15, not the float 0.15000000596. */
    public double parallaxX = 1, parallaxY = 1;
    /** Tiled layer offsets in pixels ({@code offsetx}, {@code offsety}). */
    public float offsetX, offsetY;
    public float opacity = 1;
    /** Cloud drift in px/s and the wrap length in px (layer properties {@code driftx}, {@code wrapx}); 0 for a still layer. */
    public float driftX, wrapX;
    public final Map<String, String> properties = new HashMap<>();

    public TextureRegion cell(int column, int row) { return cells[row * columns + column]; }
}
