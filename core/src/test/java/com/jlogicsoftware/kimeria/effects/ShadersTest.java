package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShadersTest {
    @Test void proceduralQuadUsesFullYDownCoordinatesDespiteLibgdxOnePixelInset() {
        Application previousApp = Gdx.app;
        Graphics previousGraphics = Gdx.graphics;
        GL20 previousGl = Gdx.gl, previousGl20 = Gdx.gl20;
        GdxNativesLoader.load();
        // Texture upload is stubbed; the real TextureRegion applies its 1x1 UV inset.
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
            TextureRegion quad = Shaders.whiteQuad();
            assertEquals(0f, quad.getU());
            assertEquals(1f, quad.getU2());
            assertEquals(1f, quad.getV());
            assertEquals(0f, quad.getV2());
            TextureRegion another = Shaders.whiteQuad();
            assertEquals(quad.getU(), another.getU());
            assertEquals(quad.getV2(), another.getV2());
        } finally {
            Shaders.dispose();
            Gdx.app = previousApp;
            Gdx.graphics = previousGraphics;
            Gdx.gl = previousGl;
            Gdx.gl20 = previousGl20;
        }
    }
}
