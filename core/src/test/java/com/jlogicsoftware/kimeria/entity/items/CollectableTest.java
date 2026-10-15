package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectableTest {
    @Test void eachItemUsesItsOwnFramesAndLoopsAtTheCoinCadence() {
        for (ItemType type : ItemType.values()) {
            List<String> origins = new ArrayList<>();
            List<TextureRegion> frames = new ArrayList<>();
            Assets assets = new Assets() {
                @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                    origins.add((int) x + "," + (int) y);
                    TextureRegion frame = new TextureRegion();
                    frames.add(frame);
                    return frame;
                }
            };
            Collectable item = new Collectable(assets, null, ignored -> {}, type.name(), 0, 0, 16, 16);
            assertEquals(List.of(type.spriteX + "," + type.spriteY, (type.spriteX + 16) + "," + type.spriteY), origins);
            List<TextureRegion> rendered = new ArrayList<>();
            Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
                (proxy, method, args) -> { if (method.getName().equals("draw")) rendered.add((TextureRegion) args[0]); return null; });
            item.render(batch);
            item.update(0.31f); item.render(batch);
            item.update(0.3f); item.render(batch);
            assertEquals(List.of(frames.get(0), frames.get(1), frames.get(0)), rendered);
        }
        assertEquals(ItemType.CHICKEN, ItemType.fromMapName("Chicken"));
    }

    @Test void pickupRunsItsEffectsOnlyOnceAndArtifactsDoNotAddCoins() {
        for (ItemType type : ItemType.values()) {
            List<String> events = new ArrayList<>();
            Assets assets = new Assets() {
                @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
            };
            GameContext game = (GameContext) Proxy.newProxyInstance(GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("playSounds")) return true;
                    events.add(method.getName());
                    return null;
                });
            Collectable item = new Collectable(assets, game, ignored -> events.add("ripple"), type.name(),
                0, 0, 16, 16, () -> events.add("pickup"));
            item.collideWithPlayer(); item.collideWithPlayer();
            assertEquals(type == ItemType.COIN ? List.of("pickup", "playSound", "addCoin", "ripple")
                : List.of("pickup", "playSound", "ripple"), events);
            assertTrue(item.isRemoved());
        }
    }
}
