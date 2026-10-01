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

    private TextureRegion[] tileRegions; // index by (gid - firstgid)
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

    public static TiledMapData load(String tmxPath, Assets assets) {
        try {
            TiledMapData data = new TiledMapData();
            FileHandle tmxFile = Gdx.files.internal(tmxPath);
            XmlEl mapEl = MiniXml.parse(tmxFile.readString("UTF-8"));
            data.widthTiles = intAttr(mapEl, "width", 0);
            data.heightTiles = intAttr(mapEl, "height", 0);
            data.tileWidth = intAttr(mapEl, "tilewidth", 16);
            data.tileHeight = intAttr(mapEl, "tileheight", 16);
            data.backgroundGrid = new int[data.heightTiles][data.widthTiles];

            String tsxSource = null;
            for (XmlEl ts : mapEl.children("tileset")) {
                data.firstGid = intAttr(ts, "firstgid", 1);
                tsxSource = ts.attr("source", "");
            }
            if (tsxSource != null && !tsxSource.isEmpty()) {
                String dir = tmxFile.parent().path();
                FileHandle tsxFile = Gdx.files.internal(dir + "/" + tsxSource);
                loadTileset(tsxFile, data, assets);
            }

            for (XmlEl layerEl : mapEl.children("layer")) {
                XmlEl dataEl = layerEl.firstChild("data");
                if (dataEl == null) continue;
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

            return data;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TMX: " + tmxPath, e);
        }
    }

    private static void loadTileset(FileHandle tsxFile, TiledMapData data, Assets assets) {
        XmlEl tsEl = MiniXml.parse(tsxFile.readString("UTF-8"));
        int tileWidth = intAttr(tsEl, "tilewidth", data.tileWidth);
        int tileHeight = intAttr(tsEl, "tileheight", data.tileHeight);
        int columns = intAttr(tsEl, "columns", 1);
        int tileCount = intAttr(tsEl, "tilecount", 0);
        XmlEl imageEl = tsEl.firstChild("image");
        String imageSource = imageEl.attr("source", "");
        String dir = tsxFile.parent().path();
        String imagePath = normalize(dir + "/" + imageSource);

        data.tileRegions = new TextureRegion[tileCount];
        for (int i = 0; i < tileCount; i++) {
            int col = i % columns;
            int row = i / columns;
            data.tileRegions[i] = assets.region(imagePath, col * tileWidth, row * tileHeight, tileWidth, tileHeight);
        }
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

    private static float floatAttr(XmlEl el, String name, float fallback) {
        String v = el.attr(name, "");
        return v.isEmpty() ? fallback : Float.parseFloat(v);
    }
}
