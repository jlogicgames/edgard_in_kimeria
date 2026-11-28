package com.jlogicsoftware.kimeria.tiled;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free TMX/TSX reader (see {@link MiniXml} for why it's
 * a hand-rolled parser rather than {@code javax.xml}/DOM).
 *
 * <p>Deliberately hand-rolled instead of using gdx's Tiled extension: this
 * keeps every coordinate exactly as authored in the .tmx file (top-left
 * origin, Y grows downward) with zero row-flipping, which is what the
 * original Flame ({@code flame_tiled}) and Bevy ({@code bevy_ecs_tiled})
 * versions both do. The whole game renders through a Y-down camera
 * (see {@link com.jlogicsoftware.kimeria.KimeriaGame}) specifically so that
 * these coordinates can be used verbatim.
 */
public class TiledMapData {
    public int widthTiles, heightTiles, tileWidth, tileHeight;
    /** [row][col] gid, row 0 = top row of the .tmx file, 0 = empty. */
    public int[][] backgroundGrid;
    public final Map<String, List<TiledObject>> objectGroups = new HashMap<>();
    /** Map-level custom properties (level configuration such as {@code fireflies}). */
    public final Map<String, String> properties = new HashMap<>();

    public int propInt(String key, int fallback) {
        String v = properties.get(key);
        if (v == null || v.isEmpty()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean propBool(String key, boolean fallback) {
        String v = properties.get(key);
        if (v == null || v.isEmpty()) return fallback;
        return Boolean.parseBoolean(v);
    }

    public String prop(String key, String fallback) {
        return properties.getOrDefault(key, fallback);
    }

    /** Scenery tile layers (parallax and factor-1 decor), in file order, which is their draw order. */
    public final List<SceneryLayer> sceneryLayers = new ArrayList<>();
    /** The foreground tile-object groups, one per room (object group names starting with {@value #FOREGROUND_PREFIX}). */
    public final List<ForegroundGroup> foregroundGroups = new ArrayList<>();
    public static final String FOREGROUND_PREFIX = "foreground";
    /** What precedes {@code -<room name>} in the name of a room's second, nearer foreground group (D143). */
    public static final String NEAR_FOREGROUND_PREFIX = FOREGROUND_PREFIX + "-near";
    /** Tiled keeps the flip flags in the top bits of a gid. */
    private static final long FLIP_H = 0x80000000L, FLIP_V = 0x40000000L, FLIP_D = 0x20000000L, FLIP_ROT = 0x10000000L;
    private static final int GID_MASK = 0x0FFFFFFF;
    /** How far a room's x and a foreground group's {@code roomx} may differ and still be the same room (both are whole pixels in a map). */
    private static final float SAME_ROOM_X = 0.5f;
    /** A collection tile's image: the whole file, or a sub-rectangle of a packed sheet (Tiled 1.9 tile {@code x}, {@code y}, {@code width}, {@code height}). */
    private static final class TileImage {
        String path = "";
        int x, y, width, height;
    }

    /** A loaded tileset: a sheet of equal tiles ({@code sheet} set) or a collection of images (one image per tile id). */
    private static final class Tileset {
        int firstGid, tileCount, columns, tileWidth, tileHeight;
        String sheet = "";
        final Map<Integer, TileImage> images = new HashMap<>(); // tile id -> its image
    }

    private final List<Tileset> tilesets = new ArrayList<>();
    private final Map<Integer, TextureRegion> resolved = new HashMap<>();
    private Assets assets;
    private TextureRegion[] tileRegions; // the first tileset, index by (gid - firstgid)
    private int firstGid;

    public float widthPx() {
        return widthTiles * tileWidth;
    }

    public float heightPx() {
        return heightTiles * tileHeight;
    }

    public List<TiledObject> layer(String name) {
        return objectGroups.getOrDefault(name, List.of());
    }

    public TextureRegion tile(int gid) {
        if (gid <= 0) return null;
        int index = gid - firstGid;
        if (tileRegions == null || index < 0 || index >= tileRegions.length) return null;
        return tileRegions[index];
    }

    /**
     * The region of any tileset's tile: a sheet cell, or the whole image of a collection tile. Resolved once and cached; meant for
     * load time (it may allocate), the per-frame code draws the regions the layers already hold. Flip bits are ignored.
     */
    public TextureRegion region(int gid) {
        gid &= GID_MASK;
        if (gid <= 0) return null;
        TextureRegion hit = resolved.get(gid);
        if (hit != null) return hit;
        for (int i = tilesets.size() - 1; i >= 0; i--) {
            Tileset ts = tilesets.get(i);
            if (gid < ts.firstGid) continue;
            int id = gid - ts.firstGid;
            TextureRegion region = null;
            if (!ts.sheet.isEmpty()) {
                if (id < ts.tileCount) region = assets.region(ts.sheet, (id % ts.columns) * ts.tileWidth, (id / ts.columns) * ts.tileHeight, ts.tileWidth, ts.tileHeight);
            } else {
                TileImage image = ts.images.get(id);
                if (image != null) region = assets.region(image.path, image.x, image.y, image.width, image.height);
            }
            if (region != null) resolved.put(gid, region);
            return region;
        }
        return null;
    }

    /** Where a tile's pixels lie: the image file and the tile's top-left in it, px. */
    public record TileSource(String image, int x, int y) { }

    /** The source of a tile of any tileset, or null for no such tile; load time only (it allocates). Flip bits are ignored. */
    public TileSource tileSource(int gid) {
        gid &= GID_MASK;
        if (gid <= 0) return null;
        for (int i = tilesets.size() - 1; i >= 0; i--) {
            Tileset ts = tilesets.get(i);
            if (gid < ts.firstGid) continue;
            int id = gid - ts.firstGid;
            if (!ts.sheet.isEmpty())
                return id < ts.tileCount ? new TileSource(ts.sheet, (id % ts.columns) * ts.tileWidth, (id / ts.columns) * ts.tileHeight) : null;
            TileImage image = ts.images.get(id);
            return image == null ? null : new TileSource(image.path, image.x, image.y);
        }
        return null;
    }

    public static TiledMapData load(String tmxPath, Assets assets) {
        try {
            TiledMapData data = new TiledMapData();
            data.assets = assets;
            FileHandle tmxFile = Gdx.files.internal(tmxPath);
            XmlEl mapEl = MiniXml.parse(tmxFile.readString("UTF-8"));
            data.widthTiles = intAttr(mapEl, "width", 0);
            data.heightTiles = intAttr(mapEl, "height", 0);
            data.tileWidth = intAttr(mapEl, "tilewidth", 16);
            data.tileHeight = intAttr(mapEl, "tileheight", 16);
            data.backgroundGrid = new int[data.heightTiles][data.widthTiles];

            XmlEl mapProps = mapEl.firstChild("properties");
            if (mapProps != null) {
                for (XmlEl propEl : mapProps.children("property")) {
                    data.properties.put(propEl.attr("name", ""), propEl.attr("value", ""));
                }
            }

            String dir = tmxFile.parent().path();
            boolean first = true;
            for (XmlEl ts : mapEl.children("tileset")) {
                String source = ts.attr("source", "");
                if (source.isEmpty()) continue;
                int firstGid = intAttr(ts, "firstgid", 1);
                Tileset loaded = loadTileset(Gdx.files.internal(normalize(dir + "/" + source)), firstGid, data);
                data.tilesets.add(loaded);
                if (first) { // the first tileset is the play layer's: the per-frame fast path of tile()
                    data.firstGid = firstGid;
                    data.tileRegions = new TextureRegion[loaded.tileCount];
                    for (int i = 0; i < loaded.tileCount; i++) data.tileRegions[i] = data.region(firstGid + i);
                    first = false;
                }
            }

            for (XmlEl layerEl : mapEl.children("layer")) {
                XmlEl dataEl = layerEl.firstChild("data");
                if (dataEl == null) continue;
                if (isScenery(layerEl)) {
                    data.sceneryLayers.add(parseScenery(layerEl, dataEl, data));
                    continue;
                }
                String csv = dataEl.text.trim();
                String[] cells = csv.split("\\s*,\\s*");
                int i = 0;
                for (int r = 0; r < data.heightTiles; r++) {
                    for (int c = 0; c < data.widthTiles; c++) {
                        if (i < cells.length && !cells[i].isEmpty()) {
                            data.backgroundGrid[r][c] = Integer.parseInt(cells[i].trim());
                        }
                        i++;
                    }
                }
            }

            for (XmlEl groupEl : mapEl.children("objectgroup")) {
                String groupName = groupEl.attr("name", "");
                if (groupName.startsWith(FOREGROUND_PREFIX)) {
                    data.foregroundGroups.add(parseForeground(groupEl, data));
                    continue;
                }
                // Tiled draws every object in a group shifted by the layer offset; mirror that.
                float offsetX = floatAttr(groupEl, "offsetx", 0);
                float offsetY = floatAttr(groupEl, "offsety", 0);
                List<TiledObject> objects = new ArrayList<>();
                for (XmlEl objEl : groupEl.children("object")) {
                    TiledObject obj = new TiledObject();
                    obj.id = intAttr(objEl, "id", 0);
                    obj.name = objEl.attr("name", "");
                    obj.type = objEl.hasAttr("type") ? objEl.attr("type", "") : objEl.attr("class", "");
                    obj.x = floatAttr(objEl, "x", 0) + offsetX;
                    obj.y = floatAttr(objEl, "y", 0) + offsetY;
                    obj.width = floatAttr(objEl, "width", 0);
                    obj.height = floatAttr(objEl, "height", 0);
                    XmlEl polylineEl = objEl.firstChild("polyline");
                    if (polylineEl != null) obj.polyline = points(polylineEl.attr("points", ""), obj.x, obj.y);
                    XmlEl propsEl = objEl.firstChild("properties");
                    if (propsEl != null) {
                        for (XmlEl propEl : propsEl.children("property")) {
                            obj.properties.put(propEl.attr("name", ""), propEl.attr("value", ""));
                        }
                    }
                    objects.add(obj);
                }
                data.objectGroups.put(groupName, objects);
            }

            data.validateForegrounds();
            return data;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TMX: " + tmxPath, e);
        }
    }

    /** Numbers per polyline point (x and y). */
    private static final int XY = 2;

    /** Tiled's "x,y x,y ..." points, relative to the object, as world-space x0, y0, x1, y1, ... */
    static float[] points(String text, float originX, float originY) {
        String[] pairs = text.trim().split("\\s+");
        float[] out = new float[pairs.length * XY];
        for (int i = 0; i < pairs.length; i++) {
            String[] xy = pairs[i].split(",");
            if (xy.length != XY) throw new IllegalArgumentException("polyline point must be x,y: " + pairs[i]);
            out[XY * i] = originX + Float.parseFloat(xy[0]);
            out[XY * i + 1] = originY + Float.parseFloat(xy[1]);
        }
        return out;
    }

    /**
     * Every foreground group must belong to a room: it is named {@code foreground-<room name>} (or {@code foreground-near-<room name>},
     * the room's nearer group) and its {@code roomx} is the x of a {@code Room} object of that name, so a moved or renamed room cannot
     * silently leave its foreground drawing against the wrong origin.
     */
    private void validateForegrounds() {
        List<TiledObject> rooms = objectGroups.get("Rooms");
        for (ForegroundGroup group : foregroundGroups) {
            boolean found = false;
            if (rooms != null) {
                for (TiledObject room : rooms) {
                    if (!"Room".equals(room.type) || !(group.name.equals(FOREGROUND_PREFIX + "-" + room.name)
                        || group.name.equals(NEAR_FOREGROUND_PREFIX + "-" + room.name))) continue;
                    if (Math.abs(room.x - group.roomX) > SAME_ROOM_X) {
                        throw new IllegalArgumentException("foreground group " + group.name + ": roomx " + group.roomX + " is not the x of room " + room.name + " (" + room.x + ")");
                    }
                    found = true;
                }
            }
            if (!found) throw new IllegalArgumentException("foreground group " + group.name + ": no Room object is named " + group.name.substring(Math.min(group.name.length(), FOREGROUND_PREFIX.length() + 1)));
        }
    }

    /** A scenery layer has a parallax attribute or the property {@code scenery=true}. */
    static boolean isScenery(XmlEl layerEl) {
        if (layerEl.hasAttr("parallaxx") || layerEl.hasAttr("parallaxy")) return true;
        return "true".equals(properties(layerEl).get("scenery"));
    }

    private static Map<String, String> properties(XmlEl el) {
        Map<String, String> out = new HashMap<>();
        XmlEl propsEl = el.firstChild("properties");
        if (propsEl != null) for (XmlEl propEl : propsEl.children("property")) out.put(propEl.attr("name", ""), propEl.attr("value", ""));
        return out;
    }

    private static SceneryLayer parseScenery(XmlEl layerEl, XmlEl dataEl, TiledMapData map) {
        SceneryLayer layer = new SceneryLayer();
        layer.name = layerEl.attr("name", "");
        layer.columns = intAttr(layerEl, "width", map.widthTiles);
        layer.rows = intAttr(layerEl, "height", map.heightTiles);
        layer.parallaxX = doubleAttr(layerEl, "parallaxx", 1);
        layer.parallaxY = doubleAttr(layerEl, "parallaxy", 1);
        layer.offsetX = floatAttr(layerEl, "offsetx", 0);
        layer.offsetY = floatAttr(layerEl, "offsety", 0);
        layer.opacity = floatAttr(layerEl, "opacity", 1);
        layer.properties.putAll(properties(layerEl));
        layer.driftX = layer.properties.containsKey("driftx") ? Float.parseFloat(layer.properties.get("driftx")) : 0;
        layer.wrapX = layer.properties.containsKey("wrapx") ? Float.parseFloat(layer.properties.get("wrapx")) : 0;
        layer.cells = new TextureRegion[layer.columns * layer.rows];
        layer.gids = new int[layer.cells.length];
        String[] cells = dataEl.text.trim().split("\\s*,\\s*");
        for (int i = 0; i < layer.cells.length && i < cells.length; i++) {
            if (cells[i].isEmpty()) continue;
            long raw = Long.parseLong(cells[i].trim());
            if ((raw & ~(long) GID_MASK) != 0) throw new IllegalArgumentException("scenery layer " + layer.name + ", cell " + i + ": flipped and rotated tiles are not supported");
            layer.gids[i] = (int) raw;
            layer.cells[i] = map.region((int) raw);
        }
        return layer;
    }

    private static ForegroundGroup parseForeground(XmlEl groupEl, TiledMapData map) {
        ForegroundGroup group = new ForegroundGroup();
        group.name = groupEl.attr("name", "");
        Map<String, String> props = properties(groupEl);
        if (!props.containsKey("roomx")) throw new IllegalArgumentException("foreground group " + group.name + ": the property roomx (its room's x in the map) is required");
        group.roomX = Float.parseFloat(props.get("roomx"));
        group.factorX = doubleAttr(groupEl, "parallaxx", 1);
        group.factorY = doubleAttr(groupEl, "parallaxy", 1);
        float offsetX = floatAttr(groupEl, "offsetx", 0), offsetY = floatAttr(groupEl, "offsety", 0);
        List<XmlEl> objects = groupEl.children("object");
        int n = objects.size();
        group.regions = new TextureRegion[n];
        group.names = new String[n];
        group.x = new float[n]; group.y = new float[n]; group.width = new float[n]; group.height = new float[n];
        group.flipX = new boolean[n]; group.flipY = new boolean[n];
        for (XmlEl objEl : objects) {
            if (!objEl.hasAttr("gid")) continue; // a foreground piece is a tile object
            long raw = Long.parseLong(objEl.attr("gid", "0"));
            if ((raw & (FLIP_D | FLIP_ROT)) != 0) throw new IllegalArgumentException("foreground object " + objEl.attr("id", "?") + ": diagonal and rotated flips are not supported");
            int i = group.count++;
            group.regions[i] = map.region((int) (raw & GID_MASK));
            if (group.regions[i] == null) throw new IllegalArgumentException("foreground object " + objEl.attr("id", "?") + ": unknown gid " + raw);
            group.names[i] = objEl.attr("name", "");
            group.width[i] = floatAttr(objEl, "width", group.regions[i].getRegionWidth());
            group.height[i] = floatAttr(objEl, "height", group.regions[i].getRegionHeight());
            group.x[i] = floatAttr(objEl, "x", 0) + offsetX;
            group.y[i] = floatAttr(objEl, "y", 0) + offsetY - group.height[i]; // Tiled stores a tile object's bottom edge
            group.flipX[i] = (raw & FLIP_H) != 0;
            group.flipY[i] = (raw & FLIP_V) != 0;
        }
        return group;
    }

    private static Tileset loadTileset(FileHandle tsxFile, int firstGid, TiledMapData data) {
        XmlEl tsEl = MiniXml.parse(tsxFile.readString("UTF-8"));
        Tileset ts = new Tileset();
        ts.firstGid = firstGid;
        ts.tileWidth = intAttr(tsEl, "tilewidth", data.tileWidth);
        ts.tileHeight = intAttr(tsEl, "tileheight", data.tileHeight);
        ts.columns = Math.max(1, intAttr(tsEl, "columns", 1));
        ts.tileCount = intAttr(tsEl, "tilecount", 0);
        String dir = tsxFile.parent().path();
        XmlEl imageEl = tsEl.firstChild("image");
        if (imageEl != null) {
            ts.sheet = normalize(dir + "/" + imageEl.attr("source", ""));
            return ts;
        }
        for (XmlEl tileEl : tsEl.children("tile")) { // a collection of images: one image per tile id
            XmlEl image = tileEl.firstChild("image");
            if (image == null) continue;
            TileImage tileImage = new TileImage();
            tileImage.path = normalize(dir + "/" + image.attr("source", ""));
            tileImage.x = intAttr(tileEl, "x", 0);
            tileImage.y = intAttr(tileEl, "y", 0);
            tileImage.width = intAttr(tileEl, "width", intAttr(image, "width", 0));   // a tile without a sub-rectangle is its whole image
            tileImage.height = intAttr(tileEl, "height", intAttr(image, "height", 0));
            ts.images.put(intAttr(tileEl, "id", 0), tileImage);
        }
        return ts;
    }

    private static String normalize(String path) {
        // Collapse "assets/tiles/../images/x.png" style paths coming from relative TSX sources.
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else if (!part.equals(".") && !part.isEmpty()) {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }

    private static int intAttr(XmlEl el, String name, int fallback) {
        String v = el.attr(name, "");
        return v.isEmpty() ? fallback : Integer.parseInt(v);
    }

    private static double doubleAttr(XmlEl el, String name, double fallback) {
        String v = el.attr(name, "");
        return v.isEmpty() ? fallback : Double.parseDouble(v);
    }

    private static float floatAttr(XmlEl el, String name, float fallback) {
        String v = el.attr(name, "");
        return v.isEmpty() ? fallback : Float.parseFloat(v);
    }
}
