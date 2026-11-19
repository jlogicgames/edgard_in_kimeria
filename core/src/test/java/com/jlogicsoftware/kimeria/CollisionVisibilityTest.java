package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A solid {@code Collisions} block has no render path of its own: what the player sees of it is the Background tile layer. So a block over
 * empty tiles is an invisible wall (the fallen log of #500 shipped that way once). Every solid block of every shipped level map must have a
 * non-empty Background tile under every cell it covers.
 *
 * <p>Excluded, each on its merits: {@code Platform} blocks (one-way: the branches and steps are drawn as sprites by their own classes,
 * {@code StaticPlatform}, and a floating step has no tile under it by design) and {@code QuickSand} (a liquid drawn by its own class, not by
 * tiles; forest-1 has none).
 */
class CollisionVisibilityTest {
    @Test void everySolidCollisionBlockOfEveryShippedMapHasABackgroundTileUnderEveryCell() {
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        };
        com.badlogic.gdx.Files previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        try {
            List<String> invisible = new ArrayList<>();
            for (String level : KimeriaGame.levelNames()) {
            TiledMapData map = TiledMapData.load("tiles/" + level + ".tmx", assets);
            for (TiledObject c : map.layer("Collisions")) {
                if ("Platform".equals(c.type) || "QuickSand".equals(c.type)) continue;
                for (int row = (int) (c.y / 16); row <= (int) ((c.y + c.height - 1) / 16); row++)
                    for (int col = (int) (c.x / 16); col <= (int) ((c.x + c.width - 1) / 16); col++)
                        if (row >= 0 && row < map.heightTiles && col >= 0 && col < map.widthTiles && map.backgroundGrid[row][col] == 0)
                            invisible.add(level + " block " + c.id + " '" + c.name + "' at (" + (int) c.x + ", " + (int) c.y + "): no tile at col " + col + " row " + row);
            }
            }
            assertTrue(invisible.isEmpty(), "invisible solid blocks (paint the Background layer under them):\n  " + String.join("\n  ", invisible));
        } finally {
            Gdx.files = previous;
        }
    }
}
