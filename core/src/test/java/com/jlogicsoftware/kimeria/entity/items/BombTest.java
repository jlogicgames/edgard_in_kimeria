package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BombExplosionEffect;
import com.jlogicsoftware.kimeria.entity.WorldObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BombTest {
    @TempDir Path directory;
    private final TrapWarnings warnings = new TrapWarnings();
    private final List<WorldObject> effects = new ArrayList<>();
    private final List<Rectangle> blasts = new ArrayList<>();
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) {
            return new TextureRegion();
        }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) ->
            method.getName().equals("trapWarnings") ? warnings : false);

    private Bomb bomb() {
        return new Bomb(assets, game, effects::add, blasts::add, 100, 100, 16, 16);
    }
    private void toggleAssist() {
        warnings.toggle(new HeadlessPreferences(new FileHandle(directory.resolve("prefs.xml").toFile())));
    }

    @Test void contactWarnsWithoutDamageAndRepeatedContactCannotPostponeTheBlast() {
        Bomb bomb = bomb();
        bomb.update(10f); // Idle bombs never detonate by themselves.
        assertTrue(blasts.isEmpty());
        bomb.collideWithPlayer();
        bomb.update(0.5f);
        bomb.collideWithPlayer();
        assertTrue(effects.isEmpty());
        assertTrue(blasts.isEmpty());
        assertFalse(bomb.isRemoved());
        bomb.update(0.31f);
        assertTrue(bomb.isRemoved());
        assertEquals(1, blasts.size());
        assertEquals(1, effects.size());
        bomb.collideWithPlayer();
        bomb.update(10f);
        assertEquals(1, blasts.size());
        assertEquals(1, effects.size());
    }

    @Test void assistIsSampledOnceAtActivationAndDepartureDoesNotCancelTheCountdown() {
        toggleAssist();
        Bomb bomb = bomb();
        bomb.collideWithPlayer();
        toggleAssist(); // Changing the setting cannot shorten an already active warning.
        bomb.update(0.81f);
        assertTrue(blasts.isEmpty());
        bomb.update(0.4f); // No repeated contact: the bomb still detonates after 1.2 s.
        assertEquals(1, blasts.size());
        Bomb normal = bomb();
        normal.collideWithPlayer();
        toggleAssist(); // Nor lengthen an already active normal warning.
        normal.update(0.81f);
        assertEquals(2, blasts.size());
    }

    @Test void activationFollowsTheSpriteAndBlastStaysCenteredWhileThePlumeRisesFromTheBombBase() {
        Bomb bomb = bomb();
        assertEquals(new Rectangle(103, 102, 10, 13), bomb.hitboxBounds());
        bomb.collideWithPlayer();
        bomb.update(0.81f);
        Rectangle blast = blasts.get(0);
        assertEquals(new Rectangle(76, 76, 64, 64), blast);
        assertTrue(blast.overlaps(new Rectangle(77, 77, 1, 1)), "Square corners are lethal too");
        assertFalse(blast.overlaps(new Rectangle(140, 100, 1, 1)), "The outside edge is safe");
        BombExplosionEffect effect = assertInstanceOf(BombExplosionEffect.class, effects.get(0));
        assertEquals(blast.x, effect.getX());
        Rectangle body = bomb.hitboxBounds();
        assertEquals(body.y + body.height, effect.getY() + effect.getHeight() * 0.85f, 0.001f);
        assertTrue(effect.getY() < blast.y, "The visual quad leaves more room above the bomb");
        assertEquals(blast.width, effect.getWidth());
        assertEquals(blast.height, effect.getHeight());
    }

    @Test void warningAlternatesVisibleTintAndRestoresBatchColor() {
        Bomb bomb = bomb();
        Color color = new Color(0.8f, 0.7f, 0.6f, 0.9f);
        float original = color.toFloatBits();
        List<Color> drawnColors = new ArrayList<>();
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getPackedColor": return color.toFloatBits();
                    case "getColor": return color;
                    case "setColor": color.set((float) args[0], (float) args[1], (float) args[2], (float) args[3]); break;
                    case "setPackedColor": Color.abgr8888ToColor(color, (float) args[0]); break;
                    case "draw": drawnColors.add(new Color(color)); break;
                    default: break;
                }
                return null;
            });
        bomb.collideWithPlayer();
        bomb.render(batch);
        assertEquals(original, color.toFloatBits());
        bomb.update(0.16f);
        bomb.render(batch);
        assertEquals(original, color.toFloatBits());
        assertTrue(drawnColors.get(0).g < drawnColors.get(1).g);
        assertEquals(drawnColors.get(0).a, drawnColors.get(1).a, 0.01f);
        assertTrue(drawnColors.get(0).a > 0);
    }
}
