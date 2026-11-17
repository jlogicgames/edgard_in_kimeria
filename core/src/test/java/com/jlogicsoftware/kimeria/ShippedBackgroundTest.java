package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D92 (#430): every shipped level sets its map property {@code background} explicitly, and the value parses. Test maps
 * may rely on {@link Level#DEFAULT_BACKGROUND}; a shipped map may not, even when the Python contrast gate is skipped.
 */
class ShippedBackgroundTest {
    @Test void everyShippedMapSetsAValidBackground() {
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        };
        com.badlogic.gdx.Files previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        try {
            for (String level : KimeriaGame.levelNames()) {
                TiledMapData map = TiledMapData.load("tiles/" + level + ".tmx", assets);
                assertTrue(map.properties.containsKey("background"), level + " must set the map property background (D92)");
                Level.parseBackground(map.properties.get("background"), new float[3]); // throws on a malformed colour
            }
        } finally {
            Gdx.files = previous;
        }
    }
}
