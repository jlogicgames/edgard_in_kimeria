package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.Assets;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PortraitAssetsTest {
    @Test void portraitsKeepThePaletteTransparencyAndSharedYDownAssetLifecycle() {
        var files = Gdx.files;
        var app = Gdx.app;
        var graphics = Gdx.graphics;
        var gl = Gdx.gl;
        var gl20 = Gdx.gl20;
        AtomicInteger deleted = new AtomicInteger();
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (proxy, method, args) ->
                switch (method.getName()) {
                case "getType" -> Application.ApplicationType.HeadlessDesktop;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
            new Class<?>[]{Graphics.class}, (proxy, method, args) ->
                method.getReturnType() == boolean.class ? false : null);
        // Decode the real PNGs and build real texture regions; only GPU upload/deletion is stubbed.
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
            new Class<?>[]{GL20.class}, (proxy, method, args) -> {
                if (method.getName().equals("glDeleteTexture")) deleted.incrementAndGet();
                return method.getReturnType() == int.class ? 1 : method.getReturnType() == boolean.class ? false : null;
            });
        Assets assets = new Assets();
        try {
            for (String name : new String[]{"edgard", "healer"}) {
                String path = "images/portraits/" + name + ".png";
                Pixmap pixels = new Pixmap(Gdx.files.internal(path));
                try {
                    int transparent = 0, visible = 0;
                    var colors = new java.util.HashSet<Integer>();
                    for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
                        int rgba = pixels.getPixel(x, y);
                        colors.add(rgba);
                        int alpha = rgba & 255;
                        if (alpha == 0) transparent++;
                        if (alpha >= 250) visible++;
                    }
                    assertTrue(colors.size() <= 256, name + " 256-color remaster palette including alpha");
                    assertTrue(transparent > 0, name + " transparent background");
                    assertTrue(visible > 0, name + " visible artwork");
                    var region = assets.region(path);
                    assertEquals(pixels.getWidth(), region.getRegionWidth());
                    assertEquals(pixels.getHeight(), region.getRegionHeight());
                    assertEquals(0f, region.getU()); assertEquals(1f, region.getU2());
                    assertEquals(1f, region.getV()); assertEquals(0f, region.getV2());
                    assertSame(assets.texture(path), region.getTexture());
                    assertSame(region.getTexture(), assets.region(path).getTexture());
                    assertEquals(Texture.TextureFilter.Nearest, region.getTexture().getMinFilter());
                    assertEquals(Texture.TextureFilter.Nearest, region.getTexture().getMagFilter());
                } finally { pixels.dispose(); }
            }
            new HealerDialogueView(assets);
            new HealerDialogueView(assets);
            for (String name : new String[]{"edgard", "healer"}) {
                Texture texture = assets.texture("images/portraits/" + name + ".png");
                assertEquals(Texture.TextureFilter.Linear, texture.getMinFilter());
                assertEquals(Texture.TextureFilter.Linear, texture.getMagFilter());
            }
            assertEquals(0, deleted.get(), "views do not own textures");
            assets.dispose();
            assertEquals(2, deleted.get(), "one disposal per cached portrait");
            assets.dispose();
            assertEquals(2, deleted.get(), "cache cleared after disposal");
        } finally {
            assets.dispose();
            Gdx.files = files; Gdx.app = app; Gdx.graphics = graphics;
            Gdx.gl = gl; Gdx.gl20 = gl20;
        }
    }
}
