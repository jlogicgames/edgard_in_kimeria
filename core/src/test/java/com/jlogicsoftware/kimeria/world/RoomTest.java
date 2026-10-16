package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.effects.BombExplosionEffect;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) {
            return new TextureRegion();
        }
    };
    private final AtomicInteger coins = new AtomicInteger();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final GameContext game = (GameContext) Proxy.newProxyInstance(GameContext.class.getClassLoader(),
        new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> new TrapWarnings();
            case "coinsCollected" -> coins.get();
            case "artifacts" -> artifacts;
            case "addCoin" -> { coins.incrementAndGet(); yield null; }
            default -> method.getReturnType() == boolean.class ? false : null;
        });

    private TiledMapData map(String name) {
        Gdx.files = new HeadlessFiles();
        return TiledMapData.load("tiles/" + name + ".tmx", assets);
    }
    private Player player() { return new Player(assets, game, 0, 0); }
    private Level forest() { map("forest"); return new Level(assets, game, player(), "forest", Rectangle::new); }

    @Test void unmarkedMapKeepsItsOriginalPlayerEntrance() {
        TiledMapData map = map("forest-1");
        RoomLayout layout = new RoomLayout(map);
        assertEquals(1, layout.rooms.size());
        var spawn = map.layer("SpawnPoints").stream().filter(o -> "Player".equals(o.type)).findFirst().orElseThrow();
        assertEquals(new Vector2(spawn.x, spawn.y), layout.rooms.get(0).entrance());
        assertNull(layout.rooms.get(0).exit());
    }
    @Test void forestHasThreeCompleteRoomsAndAnAuthoredBoss() {
        TiledMapData map = map("forest");
        RoomLayout layout = new RoomLayout(map);
        assertEquals(3, layout.rooms.size());
        for (var o : map.layer("SpawnPoints")) layout.owner(o);
        for (var o : map.layer("Collisions")) layout.owner(o);
        assertNotNull(layout.rooms.get(0).exit());
        assertNotNull(layout.rooms.get(1).exit());
        assertNull(layout.rooms.get(2).exit());
        var checkpoints = map.layer("SpawnPoints").stream().filter(o -> "Checkpoint".equals(o.type)).toList();
        assertTrue(checkpoints.isEmpty());
        var boss = map.layer("SpawnPoints").stream().filter(o -> "Boss".equals(o.type)).findFirst().orElseThrow();
        assertEquals(2, layout.owner(boss));
    }
    @Test void malformedRoomsFailInsteadOfSilentlyBecomingWholeLevel() {
        TiledMapData map = map("forest");
        var markers = map.objectGroups.get("Rooms");
        markers.removeIf(o -> "RoomStart".equals(o.type));
        TiledMapData missingStart = map;
        assertThrows(IllegalArgumentException.class, () -> new RoomLayout(missingStart));
        map = map("forest");
        map.layer("Rooms").stream().filter(o -> "Room".equals(o.type)).skip(1).findFirst().orElseThrow().x = 0;
        TiledMapData overlapping = map;
        assertThrows(IllegalArgumentException.class, () -> new RoomLayout(overlapping));
    }
    @Test void deathRestoresActiveObjectsAndDiscardsUnkeptPickupsAndEffects() {
        Level level = forest();
        Collectable coin = level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).findFirst().orElseThrow();
        coin.collideWithPlayer();
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        bomb.removeFromParent();
        level.resetCurrentRoom(player());
        assertFalse(level.objects.contains(coin));
        assertFalse(level.objects.contains(bomb));
        assertTrue(level.objects.stream().anyMatch(o -> o instanceof Bomb && !o.isRemoved()));
        assertTrue(level.keptPickups().isEmpty());
        level.keepPickups();
        assertTrue(level.keptPickups().isEmpty());
        int count = level.objects.size();
        for (int i = 0; i < 4; i++) level.resetCurrentRoom(player());
        assertEquals(count, level.objects.size());
        assertEquals(new Vector2(64, 272), level.player.position);
    }
    @Test void bombDamagesThePlayerOnlyAtDetonationAndRetryDiscardsItsPendingEffect() {
        Level level = forest();
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        Rectangle playerBounds = level.player.hitboxBounds();
        level.player.position.add(bomb.centerX() - playerBounds.x, bomb.centerY() - playerBounds.y);
        bomb.collideWithPlayer();
        bomb.update(0.5f);
        assertFalse(level.player.isGotHit());
        bomb.update(0.31f);
        assertTrue(level.player.isGotHit());
        level.resetCurrentRoom(player()); // Effect is still queued, not yet in objects.
        level.update(0f);
        assertFalse(level.objects.stream().anyMatch(BombExplosionEffect.class::isInstance));
        Bomb restored = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        restored.update(10f);
        assertFalse(restored.isRemoved());
        restored.collideWithPlayer();
        restored.update(0.5f);
        assertFalse(restored.isRemoved());
        restored.update(0.31f);
        assertTrue(restored.isRemoved());
        assertFalse(level.player.isGotHit(), "The respawned player is outside the blast");
        level.update(0f);
        assertTrue(level.objects.stream().anyMatch(BombExplosionEffect.class::isInstance));
        level.resetCurrentRoom(player()); // Also discard an effect already added to objects.
        assertFalse(level.objects.stream().anyMatch(BombExplosionEffect.class::isInstance));
    }
    @Test void nextRoomKeepsPassedStateAndOnlyResetsItsOwnPickups() {
        Level level = forest();
        Collectable coin = level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).findFirst().orElseThrow();
        coin.collideWithPlayer();
        Entity enemy = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.enemy.Enemy.class::isInstance)
            .map(Entity.class::cast).findFirst().orElseThrow();
        enemy.removeFromParent();
        level.enterNextRoom(player());
        assertEquals(1, level.keptPickups().size());
        assertTrue(coin.isCollected());
        assertTrue(enemy.isRemoved());
        assertFalse(level.objects.contains(enemy));
        assertEquals(new Vector2(1344, 272), level.player.position);
        var secondCoin = level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).findFirst().orElseThrow();
        secondCoin.collideWithPlayer();
        level.resetCurrentRoom(player());
        assertEquals(1, level.keptPickups().size());
        assertFalse(level.objects.stream().anyMatch(Checkpoint.class::isInstance));
        assertTrue(enemy.isRemoved());
        level.player.debugTriggerCheckpoint();
        assertEquals(Player.State.IDLE, level.player.getState(), "Debug checkpoint cannot bypass boss");
    }
    @Test void hotReloadInSecondRoomKeepsPositionAndCorrectRespawnEntrance() {
        Level level = forest();
        level.reloadAt(new Vector2(1500, 272));
        assertEquals(new Vector2(1500, 272), level.player.position);
        assertEquals(1280, level.currentRoomBounds().x);
        level.resetCurrentRoom(player());
        assertEquals(new Vector2(1344, 272), level.player.position);
        assertEquals(Player.State.IDLE, level.player.getState());
        assertEquals(1280, level.player.roomCameraTarget().x);
    }

    private Collectable artifact(Level level, ItemType type) {
        return level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast)
            .filter(item -> item.type() == type).findFirst().orElseThrow();
    }

    @Test void artifactPickupIsProvisionalAndDeathRestoresItWithoutChangingCoins() {
        Level level = forest();
        Collectable chicken = artifact(level, ItemType.CHICKEN);
        chicken.collideWithPlayer();
        chicken.collideWithPlayer();
        assertEquals(1, artifacts.items().size());
        assertEquals(ItemType.CHICKEN, artifacts.items().get(0).type());
        assertEquals(0, coins.get());
        assertTrue(chicken.isRemoved());
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest"));
        level.resetCurrentRoom(player());
        assertTrue(artifacts.items().isEmpty());
        assertFalse(artifact(level, ItemType.CHICKEN).isCollected());
    }

    @Test void roomExitsKeepArtifactsWithoutAutomaticCombatBonus() {
        Level level = forest();
        artifact(level, ItemType.CHICKEN).collideWithPlayer();
        level.enterNextRoom(player());
        artifact(level, ItemType.KEY).collideWithPlayer();
        assertEquals(2, artifacts.items().size());
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest"), "Key is still provisional");
        level.resetCurrentRoom(player());
        assertEquals(1, artifacts.items().size());
        assertEquals(ItemType.CHICKEN, artifacts.items().get(0).type());
        artifact(level, ItemType.KEY).collideWithPlayer();
        level.enterNextRoom(player());
        assertTrue(artifacts.hasFullSet("forest"));
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest"));
        level.resetCurrentRoom(player());
        assertFalse(level.objects.stream().anyMatch(o -> o instanceof Collectable c && c.type() == ItemType.KEY));
        assertEquals(2, artifacts.items().size());
    }

    @Test void bossRoomCanBeEnteredWithoutAnyArtifacts() {
        Level level = forest();
        level.enterNextRoom(player());
        level.player.debugTriggerCheckpoint();
        assertEquals(Player.State.IDLE, level.player.getState(), "Debug checkpoint cannot bypass boss");
        assertTrue(artifacts.items().isEmpty());
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest"));
    }

    @Test void retryRebuildsDestroyedWallsAndMovingTrapsWithoutStaleCollisionReferences() {
        map("forest");
        Level level = new Level(assets, game, player(), "room-test", Rectangle::new);
        var wall = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Wall.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Wall.class::cast).findFirst().orElseThrow();
        level.activateTrigger(level.triggers.get(0));
        assertTrue(wall.isRemoved());
        var platform = level.fallingPlatforms.get(0);
        platform.collideWithActor(); platform.update(1.25f); platform.update(0.1f);
        assertTrue(platform.getY() > 272);
        var lift = level.escalators.get(0);
        lift.update(1);
        level.queueSpawn(new com.jlogicsoftware.kimeria.effects.RippleEffect(0, 0, 1, 10, 1, 1, 1));
        level.resetCurrentRoom(player());
        assertFalse(level.collisionBlocks.contains(wall));
        var restored = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Wall.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Wall.class::cast).findFirst().orElseThrow();
        assertTrue(restored.isActive());
        assertTrue(level.collisionBlocks.contains(restored));
        assertEquals(272, level.fallingPlatforms.get(0).getY());
        assertNotSame(platform, level.fallingPlatforms.get(0));
        assertNotSame(lift, level.escalators.get(0));
        int count = level.objects.size();
        level.update(0);
        assertEquals(count, level.objects.size());
        assertNull(level.activeRipple());
        level.recordPickup(100);
        level.player.debugTriggerCheckpoint();
        assertTrue(level.keptPickups().contains(100));
    }

    @Test void fadeFreezesAtBlackAndSwitchesOnceEvenAfterLongFrame() {
        RoomFade fade = new RoomFade();
        AtomicInteger switches = new AtomicInteger();
        fade.start();
        fade.update(0.125f, switches::incrementAndGet);
        assertEquals(0.5f, fade.alpha());
        fade.update(4f, switches::incrementAndGet);
        assertTrue(fade.active());
        assertEquals(1f, fade.alpha());
        assertEquals(1, switches.get());
        fade.update(0.125f, switches::incrementAndGet);
        assertEquals(0.5f, fade.alpha());
        fade.update(0.125f, switches::incrementAndGet);
        assertFalse(fade.active());
        assertEquals(0f, fade.alpha());
        assertEquals(1, switches.get());
        fade.start(); fade.cancel(); fade.update(1, switches::incrementAndGet);
        assertEquals(1, switches.get());
    }
}
