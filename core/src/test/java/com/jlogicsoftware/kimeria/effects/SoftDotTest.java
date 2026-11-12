package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** The dot regions are cached (#285 step 1): the same instance per call, a fresh one after {@code dispose()}. */
class SoftDotTest {
    @Test void regionsAreCachedAndDropWithTheirTextureOnDispose() {
        Application previousApp = Gdx.app;
        Graphics previousGraphics = Gdx.graphics;
        GL20 previousGl = Gdx.gl, previousGl20 = Gdx.gl20;
        GdxNativesLoader.load();
        // Texture upload is stubbed, as in ShadersTest.
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (proxy, method, args) ->
                method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
            new Class<?>[]{Graphics.class}, (proxy, method, args) ->
                method.getReturnType() == boolean.class ? false : null);
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
            new Class<?>[]{GL20.class}, (proxy, method, args) ->
                method.getReturnType() == int.class ? 1 : method.getReturnType() == boolean.class ? false : null);
        try {
            TextureRegion dot = SoftDot.region();
            TextureRegion glow = SoftDot.glowRegion();
            assertSame(dot, SoftDot.region());
            assertSame(glow, SoftDot.glowRegion());
            assertNotSame(dot, glow);
            SoftDot.dispose();
            assertNotSame(dot, SoftDot.region());
            assertNotSame(glow, SoftDot.glowRegion());
        } finally {
            SoftDot.dispose();
            Gdx.app = previousApp;
            Gdx.graphics = previousGraphics;
            Gdx.gl = previousGl;
            Gdx.gl20 = previousGl20;
        }
    }
}
