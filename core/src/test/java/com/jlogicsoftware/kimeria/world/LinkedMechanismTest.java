package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Triggers linked to mechanisms through the Tiled {@code target} object property, not through names. */
class LinkedMechanismTest {
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) {
            return new TextureRegion();
        }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(GameContext.class.getClassLoader(),
        new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> new TrapWarnings();
            case "artifacts" -> new ArtifactInventory();
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Level level() {
        Gdx.files = new HeadlessFiles();
        return new Level(assets, game, new Player(assets, game, 0, 0), "link-test", Rectangle::new);
    }

    @Test void leverTogglesTheEscalatorItPointsAtAndNoOther() {
        Level level = level();
        var first = level.escalators.stream().filter(e -> e.getX() == 300).findFirst().orElseThrow();
        var second = level.escalators.stream().filter(e -> e.getX() == 400).findFirst().orElseThrow();
        var trigger = level.triggers.stream().filter(t -> t.id == 11).findFirst().orElseThrow();
        assertTrue(first.isRunning());
        level.activateTrigger(trigger);
        assertFalse(first.isRunning());
        assertFalse(second.isRunning(), "running=false authored on the map starts the platform stopped");
        assertTrue(second.currentMoveDirection().isZero(), "A stopped platform must not carry its rider");
        assertTrue(first.currentMoveDirection().isZero());
        level.activateTrigger(trigger);
        assertFalse(first.currentMoveDirection().isZero(), "A running platform carries its rider");
        level.activateTrigger(trigger);
        level.activateTrigger(level.triggers.stream().filter(t -> t.id == 13).findFirst().orElseThrow());
        assertTrue(second.isRunning());
        assertFalse(first.isRunning(), "A lever only drives the mechanism its target property references");
        assertEquals(1, level.linkedMechanisms(trigger).size());
        assertSame(first, level.linkedMechanisms(trigger).get(0));
        assertEquals(2, level.objects.stream().filter(Lever.class::isInstance).count());
    }

    @Test void brokenReferencesNameTheOffendingObject() {
        Gdx.files = new HeadlessFiles();
        var e = assertThrows(IllegalArgumentException.class, () -> new Level(assets, game,
            new Player(assets, game, 0, 0), "link-broken-test", Rectangle::new));
        assertTrue(e.getMessage().contains("[11]") && e.getMessage().contains("999"), e.getMessage());
    }
}
