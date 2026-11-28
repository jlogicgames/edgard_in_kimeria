package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.effects.CrownGlintFx;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.tiled.SceneryLayer;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The leaf patches the wind's glints may light (#593, D163): read from {@value #PATH}, which tools/wind_spots.py bakes from the
 * art's typed {@code leaves} boxes, and placed into a room. A patch is a set of leaf pixels, horizontal runs from its top-left in
 * its sheet's pixels: a decor crown's in its frame, the mid and grove layers' in their tileset image, inside one tile. Nothing here
 * reads pixels, so desktop, web and headless tests see the same patches. Load time only: everything allocates.
 */
public final class WindSpots {
    public static final String PATH = "wind/forest-spots.txt";
    private static final String SHEET = "sheet", COMMENT = "#", DECOR_PREFIX = "decor-", PNG = ".png";
    /** The words of a {@code sheet <name> <depth> <frame width> <height>} line. */
    private static final int SHEET_WORDS = 5, NAME_WORD = 1, DEPTH_WORD = 2, WIDTH_WORD = 3, HEIGHT_WORD = 4;
    private static final int XY = 2, RUN = KimeriaParticleSystem.GLINT_RUN;
    /** The scenery layer, and the depth's name in the file, per depth. */
    private static final String[] DEPTH_NAMES = {"crown", "mid", "grove"};

    /** One patch: its top-left in the sheet and its runs from there, (dx, dy, width) each. */
    public record Patch(float x, float y, float[] runs) { }

    /** One sheet's patches: its depth and the frame (or image) size. */
    public record Sheet(String name, int depth, int width, int height, List<Patch> patches) { }

    private final Map<String, Sheet> sheets = new HashMap<>();

    public static WindSpots load() { return parse(Gdx.files.internal(PATH).readString("UTF-8")); }

    static WindSpots parse(String text) {
        WindSpots result = new WindSpots();
        Sheet current = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(COMMENT)) continue;
            String[] words = line.split(" ");
            if (SHEET.equals(words[0])) {
                if (words.length != SHEET_WORDS) throw new IllegalArgumentException(PATH + ": bad line '" + line + "'");
                current = new Sheet(words[NAME_WORD], depthOf(words[DEPTH_WORD]), Integer.parseInt(words[WIDTH_WORD]),
                    Integer.parseInt(words[HEIGHT_WORD]), new ArrayList<>());
                result.sheets.put(current.name(), current);
            } else {
                if (current == null || words.length < XY + RUN || (words.length - XY) % RUN != 0)
                    throw new IllegalArgumentException(PATH + ": bad line '" + line + "'");
                float[] runs = new float[words.length - XY];
                for (int i = 0; i < runs.length; i++) runs[i] = Float.parseFloat(words[XY + i]);
                current.patches().add(new Patch(Float.parseFloat(words[0]), Float.parseFloat(words[1]), runs));
            }
        }
        return result;
    }

    private static int depthOf(String name) {
        for (int d = 0; d < DEPTH_NAMES.length; d++) if (DEPTH_NAMES[d].equals(name)) return d;
        throw new IllegalArgumentException(PATH + ": unknown depth '" + name + "'");
    }

    /** The sheet of that name, or null. */
    public Sheet sheet(String name) { return sheets.get(name); }
    public java.util.Collection<Sheet> sheets() { return sheets.values(); }

    /** The index of the scenery layer a parallax depth lights, or -1 for the crowns and for a map without that layer. */
    public static int layerIndex(TiledMapData map, int depth) {
        if (depth == CrownGlintFx.CROWN) return -1;
        for (int i = 0; i < map.sceneryLayers.size(); i++) if (map.sceneryLayers.get(i).name.equals(DEPTH_NAMES[depth])) return i;
        return -1;
    }

    /**
     * The patches of one room, per depth: the crowns' in world px (every {@code Decor} of the room whose kind has a crown sheet, its
     * draw position and mirroring as {@code Decor.render} places it), the mid and grove layers' in layer px, over the columns of the
     * layer the room's camera (a view {@code viewWidth} wide) can show.
     */
    public CrownGlintFx.Spots[] forRoom(TiledMapData map, RoomLayout layout, int room, float viewWidth) {
        CrownGlintFx.Spots[] result = new CrownGlintFx.Spots[CrownGlintFx.DEPTHS];
        for (int d = 0; d < result.length; d++) result[d] = new CrownGlintFx.Spots();
        addCrowns(map, layout, room, result[CrownGlintFx.CROWN]);
        Rectangle bounds = layout.rooms.get(room).bounds();
        float spare = bounds.width - viewWidth;
        float cameraMin = spare >= 0 ? bounds.x : bounds.x + spare / 2, cameraMax = spare >= 0 ? bounds.x + spare : cameraMin;
        for (int d = CrownGlintFx.MID; d < CrownGlintFx.DEPTHS; d++) {
            int index = layerIndex(map, d);
            if (index < 0) continue;
            SceneryLayer layer = map.sceneryLayers.get(index);
            float from = (float) (layer.parallaxX * cameraMin) - layer.offsetX - map.tileWidth;
            float to = (float) (layer.parallaxX * cameraMax) - layer.offsetX + viewWidth + map.tileWidth;
            addLayer(map, layer, d, from, to, result[d]);
        }
        return result;
    }

    private void addCrowns(TiledMapData map, RoomLayout layout, int room, CrownGlintFx.Spots out) {
        for (TiledObject o : map.layer("SpawnPoints")) {
            if (!"Decor".equals(o.type) || layout.owner(o) != room) continue;
            Sheet sheet = sheets.get(DECOR_PREFIX + o.prop("kind", "").toLowerCase(Locale.ROOT));
            if (sheet == null || sheet.depth() != CrownGlintFx.CROWN) continue;
            float drawX = o.x + (o.width - sheet.width()) / 2f, drawY = o.y + o.height - sheet.height();
            // This crown is drawn over the earlier ones, and a glint over all of them: it must not stand on another crown's shade.
            // The whole frame counts, since no pixel is read here.
            out.removeTouching(drawX, drawY, sheet.width(), sheet.height());
            boolean mirror = o.propBool("mirror", false);
            for (Patch patch : sheet.patches()) {
                float x = mirror ? sheet.width() - patch.x() - CrownGlintFx.Spots.width(patch.runs()) : patch.x();
                out.add(drawX + x, drawY + patch.y(), mirror ? mirrored(patch.runs()) : patch.runs());
            }
        }
    }

    /** The runs flipped left to right within the patch's width. */
    private static float[] mirrored(float[] runs) {
        float width = CrownGlintFx.Spots.width(runs);
        float[] result = runs.clone();
        for (int k = 0; k + RUN - 1 < result.length; k += RUN) result[k] = width - runs[k] - runs[k + RUN - 1];
        return result;
    }

    private void addLayer(TiledMapData map, SceneryLayer layer, int depth, float from, float to, CrownGlintFx.Spots out) {
        Map<Integer, List<Patch>> byGid = new HashMap<>(); // gid -> its patches, relative to the tile
        int first = Math.max(0, (int) Math.floor(from / map.tileWidth)), last = Math.min(layer.columns - 1, (int) Math.floor(to / map.tileWidth));
        for (int r = 0; r < layer.rows; r++) {
            for (int c = first; c <= last; c++) {
                int gid = layer.gids[r * layer.columns + c];
                if (gid == 0) continue;
                for (Patch patch : byGid.computeIfAbsent(gid, g -> tilePatches(map, g, depth))) {
                    float x = c * map.tileWidth + patch.x();
                    if (x >= from && x <= to) out.add(x, r * map.tileHeight + patch.y(), patch.runs());
                }
            }
        }
    }

    private List<Patch> tilePatches(TiledMapData map, int gid, int depth) {
        List<Patch> found = new ArrayList<>();
        TiledMapData.TileSource source = map.tileSource(gid);
        if (source == null) return found;
        String file = source.image().substring(source.image().lastIndexOf('/') + 1);
        Sheet sheet = file.endsWith(PNG) ? sheets.get(file.substring(0, file.length() - PNG.length())) : null;
        if (sheet == null || sheet.depth() != depth) return found;
        for (Patch patch : sheet.patches()) {
            float x = patch.x() - source.x(), y = patch.y() - source.y();
            if (x >= 0 && x < map.tileWidth && y >= 0 && y < map.tileHeight) found.add(new Patch(x, y, patch.runs()));
        }
        return found;
    }
}
