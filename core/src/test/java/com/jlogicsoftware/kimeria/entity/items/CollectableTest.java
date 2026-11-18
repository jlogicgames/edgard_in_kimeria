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
                    TextureRegion frame = new TextureRegion();
                    if (path.equals(type.sheet)) { origins.add((int) x + "," + (int) y); frames.add(frame); }  // not the shared cue's regions
                    return frame;
                }
            };
            Collectable item = new Collectable(assets, null, type.name(), 0, 0, 16, 16);
            assertEquals(List.of(type.spriteX + "," + type.spriteY, (type.spriteX + 16) + "," + type.spriteY), origins);
            List<TextureRegion> rendered = new ArrayList<>();
            Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("draw") && frames.contains((TextureRegion) args[0])) rendered.add((TextureRegion) args[0]);
                    return null;
                });
            item.render(batch);
            item.update(0.31f); item.render(batch);
            item.update(0.3f); item.render(batch);
            assertEquals(List.of(frames.get(0), frames.get(1), frames.get(0)), rendered);
        }
        assertEquals(ItemType.BAT, ItemType.fromMapName("Bat"));
    }

    /** #345: every collectable bobs 2 px UP (world Y grows down, so the drawn y gets smaller) in whole pixels; the box stays. */
    @Test void everyItemBobsUpInWholePixelsWhileItsBoxStays() {
        for (ItemType type : ItemType.values()) {
            List<TextureRegion> frames = new ArrayList<>();
            Assets assets = new Assets() {
                @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                    TextureRegion region = new TextureRegion();
                    if (path.equals(type.sheet)) frames.add(region);
                    return region;
                }
            };
            Collectable item = new Collectable(assets, null, type.name(), 100, 200, 16, 16);
            List<Float> drawnY = new ArrayList<>();
            Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("draw") && frames.contains((TextureRegion) args[0])) drawnY.add((Float) args[2]);
                    return null;
                });
            float step = 1 / 60f;
            List<Integer> lifts = new ArrayList<>();
            float time = 0f;
            for (int i = 0; i < 72; i++) { lifts.add(Collectable.bobLift(time)); item.render(batch); item.update(step); time += step; }  // 1.2 s: one whole bob
            for (int i = 0; i < drawnY.size(); i++)
                assertEquals(200f - lifts.get(i), drawnY.get(i), "render draws the item bobLift px above its box");
            float top = drawnY.stream().min(Float::compare).orElseThrow(), rest = drawnY.stream().max(Float::compare).orElseThrow();
            assertEquals(200f, rest, type + " rests where the map put it");
            assertEquals(200f - Collectable.BOB_HEIGHT, top, type + " rises BOB_HEIGHT px, upward");
            for (float y : drawnY) assertEquals(Math.round(y), y, 0f, "whole pixels only");
            assertEquals(200f, drawnY.get(0), "a bob starts at rest");
            assertEquals(200f - Collectable.BOB_HEIGHT, drawnY.get(36), "the top is half a period in");
            assertEquals(200f, item.position.y, "the pickup box does not move");
        }
    }

    /** #345: a warm halo is drawn additively BEFORE the item, centred on it, pulsing with the bob; the blend is restored. Not on the coin. */
    @Test void theHaloPulsesBehindTheItemAndTheBlendIsRestored() {
        List<String> calls = new ArrayList<>();
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                return new TextureRegion() { @Override public String toString() { return path.substring(path.lastIndexOf('/') + 1); } };
            }
        };
        Collectable item = new Collectable(assets, null, "Mushroom", 40, 200, 16, 16);  // sparkle offset 1.0 s: none drawn here
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setBlendFunction" -> calls.add("blend " + args[0] + "," + args[1]);
                    case "setColor" -> calls.add(args.length == 4 ? "alpha " + args[3] : "white");
                    case "draw" -> calls.add(args[0] + " " + args[1] + "," + args[2] + " " + args[3]);
                    default -> { }
                }
                return null;
            });
        item.render(batch);  // t = 0: at rest
        assertEquals(List.of("blend 770,1", "alpha 0.5", "pickup-glow.png 36.0,196.0 24.0", "white", "blend 770,771", "mushroom.png 40.0,200.0 16.0"),
            calls, "at rest: halo at its dimmest, centred on the item, then the item in normal blending");
        calls.clear();
        item.update(0.6f); item.render(batch);  // 0.6 s: the top of the bob
        assertEquals("alpha 0.9", calls.get(1), "the halo is brightest at the top of the bob");
        assertEquals("pickup-glow.png 36.0,194.0 24.0", calls.get(2), "the halo rises with the item");
        calls.clear();
        Collectable coin = new Collectable(assets, null, "Coin", 40, 200, 16, 16);
        coin.render(batch);
        assertEquals(List.of("coin.png 40.0,200.0 16.0"), calls, "the coin draws no halo and leaves the blend alone: its white rim washes out against one (D76)");
        assertTrue(Collectable.hasHalo(ItemType.BAT) && Collectable.hasHalo(ItemType.MUSHROOM) && !Collectable.hasHalo(ItemType.COIN));
    }

    /** #345: one three-frame sparkle per period, upper right of the item, started at a different time per item. */
    @Test void theSparkleRunsThreeFramesEveryPeriodStaggeredByX() {
        assertEquals(0, Collectable.sparkleFrame(0f));
        assertEquals(1, Collectable.sparkleFrame(0.15f));
        assertEquals(2, Collectable.sparkleFrame(0.25f));
        assertEquals(-1, Collectable.sparkleFrame(0.35f));
        assertEquals(-1, Collectable.sparkleFrame(2.3f));
        assertEquals(0, Collectable.sparkleFrame(2.45f), "it repeats every SPARKLE_PERIOD");
        List<String> drawn = new ArrayList<>();
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                return new TextureRegion() { @Override public String toString() { return path.endsWith("sparkle.png") ? "sparkle" + (int) x : "other"; } };
            }
        };
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
            (proxy, method, args) -> {
                if (method.getName().equals("draw") && args[0].toString().startsWith("sparkle")) drawn.add(args[0] + " " + args[1] + "," + args[2]);
                return null;
            });
        Collectable first = new Collectable(assets, null, "Coin", 0, 200, 16, 16);
        Collectable second = new Collectable(assets, null, "Coin", 16, 200, 16, 16);
        first.render(batch); second.render(batch);
        assertEquals(List.of("sparkle0 11.0,198.0"), drawn, "x 0 sparkles at once, 3 px right of centre and 2 px above; x 16 waits 0.4 s");
    }

    @Test void healerIngredientsAreBatAndMushroomOnTheirOwnTwoFrameSheets() {
        assertEquals(java.util.List.of(ItemType.COIN, ItemType.BAT, ItemType.MUSHROOM), java.util.List.of(ItemType.values()));
        assertEquals(ItemType.MUSHROOM, ItemType.fromMapName("Mushroom"));
        assertEquals("images/objects/coin.png", ItemType.COIN.sheet);
        assertEquals("images/objects/bat.png", ItemType.BAT.sheet);
        assertEquals("images/objects/mushroom.png", ItemType.MUSHROOM.sheet);
        assertEquals("bat", ItemType.BAT.iconName());
        assertEquals("mushroom", ItemType.MUSHROOM.iconName());
        for (String retired : new String[]{"Chicken", "Key"}) {
            assertThrows(IllegalArgumentException.class, () -> ItemType.fromMapName(retired),
                retired + " is no longer a collectable");
        }
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
            Collectable item = new Collectable(assets, game, type.name(),
                0, 0, 16, 16, () -> events.add("pickup"));
            item.collideWithPlayer(); item.collideWithPlayer();
            assertEquals(type == ItemType.COIN ? List.of("pickup", "playSound", "addCoin")
                : List.of("pickup", "playSound"), events);
            assertTrue(item.isRemoved());
        }
    }
}
