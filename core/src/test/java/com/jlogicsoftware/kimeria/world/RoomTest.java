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
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.effects.PickupSparksFx;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.effects.BombExplosionEffect;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
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
            case "spendCoins" -> { coins.addAndGet(-(int) args[0]); yield null; }
            case "addCoin" -> { coins.incrementAndGet(); yield null; }
            default -> method.getReturnType() == boolean.class ? false : null;
        });

    private TiledMapData map(String name) {
        Gdx.files = new HeadlessFiles();
        return TiledMapData.load("tiles/" + name + ".tmx", assets);
    }
    private Player player() { return new Player(assets, game, 0, 0); }
    private Level forest() { map("forest-2"); return new Level(assets, game, player(), "forest-2", Rectangle::new); }
    /**
     * Test resource {@code tiles/practice-test.tmx}: forest-2's D24 practice stone, carryable torch and lever-lit torch
     * (ids 116, 117, 119, same positions) in a first room, then a hall and a boss room so the healer return works. The
     * shipped map dropped them in #376.
     */
    /** Test resource {@code tiles/bomb-rooms-test.tmx}: the Bomb (id 54) and a coin in a first room, then a hall and a boss room; the Bomb left the forest for the Fortress (D128). */
    private Level bombRooms() { map("bomb-rooms-test"); return new Level(assets, game, player(), "bomb-rooms-test", Rectangle::new); }
    private Level practice() { map("practice-test"); return new Level(assets, game, player(), "practice-test", Rectangle::new); }

    private com.jlogicsoftware.kimeria.entity.items.FireballRefill refill(Level level) {
        return level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.FireballRefill.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.FireballRefill.class::cast).findFirst().orElseThrow();
    }

    @Test void fireballRefillsRollbackOnDeathAndStayConsumedOnSecuredCollectionReturns() {
        Level level = forest();
        level.rangedStock.restore(1);
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
        var first = refill(level);
        assertEquals(1696, first.getX());
        assertEquals(128, first.getY(), "SpawnPoints layer offset applies to the refill too");
        first.collideWithPlayer(); first.collideWithPlayer();
        assertEquals(2, level.rangedStock.count(), "One authored refill grants exactly one charge");
        assertTrue(artifacts.items().isEmpty(), "Ammunition is not a recipe ingredient");
        level.resetCurrentRoom(player());
        assertEquals(1, level.rangedStock.count());
        assertFalse(refill(level).isRemoved());
        assertFalse(level.keptPickups().contains(121));
        refill(level).collideWithPlayer();
        level.prepareHealer();
        assertTrue(level.keptPickups().contains(121));
        for (int visit = 0; visit < 3; visit++) {
            level.returnForCollection(player());
            level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
            assertTrue(level.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.items.FireballRefill.class::isInstance));
            assertEquals(2, level.rangedStock.count(), "Secured refill cannot be farmed");
        }
        level.enterNextRoom(player());
        level.rangedStock.spend();
        level.resetCurrentRoom(player());
        assertEquals(2, level.rangedStock.count(), "Boss death restores its entry stock");
        var stock = level.rangedStock;
        stock.restartLevel();
        Level restarted = new Level(assets, game, player(), "forest-2", Rectangle::new, stock);
        restarted.enterNextRoom(player()); restarted.enterNextRoom(player()); restarted.enterNextRoom(player()); restarted.enterNextRoom(player());
        assertFalse(refill(restarted).isRemoved(), "Restart rebuilds the authored opportunity");
        assertEquals(3, stock.count(), "Restart restores level-entry stock, not collected charges");
    }

    @Test void cameraShakeNeverPullsAnInspectionViewBackIntoTheRoom() {
        Level level = forest();
        for (float y : new float[]{-400, 408}) {
            Rectangle view = new Rectangle(0, y, 640, 360);
            assertEquals(new Vector2(), level.cameraShakeOffset(view));
            level.shakeAfterBossAttack();
            Vector2 shake = level.cameraShakeOffset(view);
            assertEquals(0, shake.y, .001, "Only shake is bounded; the inspection offset is retained");
            assertTrue(Math.abs(shake.x) <= 1.25f);
            level.clearCameraEffects();
        }
    }

    /** No shipped map is unmarked since #187 (D67, #104) gave forest-1 several rooms, so the fallback is exercised with the Rooms layer stripped in memory. */
    @Test void aMapWithoutRoomMarkersIsOneRoomAtItsPlayerSpawn() {
        TiledMapData map = map("forest-1");
        map.objectGroups.get("Rooms").clear();
        RoomLayout layout = new RoomLayout(map);
        assertEquals(1, layout.rooms.size());
        var spawn = map.layer("SpawnPoints").stream().filter(o -> "Player".equals(o.type)).findFirst().orElseThrow();
        assertEquals(new Vector2(spawn.x, spawn.y), layout.rooms.get(0).entrance());
        assertNull(layout.rooms.get(0).exit());
    }
    /**
     * forest-1 (D67, #104, #349, #375, #418, #363, #484): the first-RedMob room comes first in play order (it lies after the tree room on the map; the
     * Rooms list, not x, sets the order) and holds the Player spawn as its entrance; the deadfall room (D88), the trap course, the lever lesson room (D86),
     * the spider's first room, the bramble room, the spider's climbing room, the route room, the guard room, the hive room and the tree room follow, and the
     * tree room is last and holds the Checkpoint at its top.
     */
    @Test void forestOneRunsFromTheMobRoomToTheTreeRoomWithTheCheckpointInPlayOrder() {
        TiledMapData map = map("forest-1");
        RoomLayout layout = new RoomLayout(map);
        List<String> order = List.of("forest-1-mob", "forest-1-deadfall", "forest-1-escalators", "forest-1-traps", "forest-1-lever", "forest-1-spider", "forest-1-bramble",
            "forest-1-climb", "forest-1-route", "forest-1-guard", "forest-1-hive", "forest-1-tree");
        assertEquals(order.size(), layout.rooms.size());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(order.get(i), layout.rooms.get(i).id(), "room " + i);
            if (i < order.size() - 1) assertNotNull(layout.rooms.get(i).exit(), order.get(i) + " leads on to " + order.get(i + 1));
        }
        var spawn = map.layer("SpawnPoints").stream().filter(o -> "Player".equals(o.type)).findFirst().orElseThrow();
        assertEquals(new Vector2(spawn.x, spawn.y), layout.rooms.get(0).entrance());
        int last = order.size() - 1;
        assertNull(layout.rooms.get(last).exit());
        var checkpoint = map.layer("SpawnPoints").stream().filter(o -> "Checkpoint".equals(o.type)).toList();
        assertEquals(1, checkpoint.size());
        assertEquals(last, layout.owner(checkpoint.get(0)));
    }
    @Test void forestHasSixCompleteRoomsAndAnAuthoredBoss() {
        TiledMapData map = map("forest-2");
        RoomLayout layout = new RoomLayout(map);
        assertEquals(6, layout.rooms.size());
        for (var o : map.layer("SpawnPoints")) if (!"Deadline".equals(o.type)) layout.owner(o);
        for (var o : map.layer("Collisions")) layout.owner(o);
        assertNotNull(layout.rooms.get(0).exit());
        assertNotNull(layout.rooms.get(1).exit());
        assertNotNull(layout.rooms.get(2).exit());
        assertNotNull(layout.rooms.get(3).exit());
        assertNotNull(layout.rooms.get(4).exit());
        assertNull(layout.rooms.get(5).exit());
        assertEquals(java.util.List.of("forest-entry", "forest-walls", "forest-fissure", "forest-glade", "forest-canopy", "forest-boss"),
            layout.rooms.stream().map(RoomLayout.Room::id).toList(), "#376 split forest-entry at x 736; #366 added forest-fissure; #331 added forest-glade");
        var checkpoints = map.layer("SpawnPoints").stream().filter(o -> "Checkpoint".equals(o.type)).toList();
        assertTrue(checkpoints.isEmpty());
        var boss = map.layer("SpawnPoints").stream().filter(o -> "Boss".equals(o.type)).findFirst().orElseThrow();
        assertEquals(5, layout.owner(boss));
    }
    @Test void malformedRoomsFailInsteadOfSilentlyBecomingWholeLevel() {
        TiledMapData map = map("forest-2");
        var markers = map.objectGroups.get("Rooms");
        markers.removeIf(o -> "RoomStart".equals(o.type));
        TiledMapData missingStart = map;
        assertThrows(IllegalArgumentException.class, () -> new RoomLayout(missingStart));
        map = map("forest-2");
        map.layer("Rooms").stream().filter(o -> "Room".equals(o.type)).skip(1).findFirst().orElseThrow().x = 0;
        TiledMapData overlapping = map;
        assertThrows(IllegalArgumentException.class, () -> new RoomLayout(overlapping));
    }
    @Test void deathRestoresActiveObjectsAndDiscardsUnkeptPickupsAndEffects() {
        Level level = bombRooms();
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
        Level level = bombRooms();
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        Rectangle playerBounds = level.player.hitboxBounds();
        level.player.position.add(bomb.centerX() - playerBounds.x, bomb.centerY() - playerBounds.y);
        bomb.collideWithPlayer();
        bomb.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 0.6f);
        assertFalse(level.player.isGotHit());
        bomb.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 0.41f);
        assertTrue(level.player.isGotHit());
        level.resetCurrentRoom(player()); // Effect is still queued, not yet in objects.
        level.update(0f);
        assertFalse(level.objects.stream().anyMatch(BombExplosionEffect.class::isInstance));
        Bomb restored = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        restored.update(10f);
        assertFalse(restored.isRemoved());
        restored.collideWithPlayer();
        restored.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 0.6f);
        assertFalse(restored.isRemoved());
        restored.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 0.41f);
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
        assertEquals(new Vector2(752, 272), level.player.position, "forest-walls entrance (#376)");
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        assertEquals(new Vector2(3680, 272), level.player.position, "forest-glade entrance (#331)");
        level.enterNextRoom(player());
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

    /** The F3 debug hotkey's ripple is the one user of {@code RippleEffect} now: the level still finds it while it lives and drops it after its life. */
    @Test void aQueuedRippleIsFoundWhileItLivesAndDroppedAfterItsLife() {
        Level level = forest();
        assertNull(level.activeRipple());
        var ripple = new com.jlogicsoftware.kimeria.effects.RippleEffect(100, 100, .75f, 120, 12, 60, 30);
        level.queueSpawn(ripple);
        level.update(0f);
        assertSame(ripple, level.activeRipple());
        level.update(1f);
        assertTrue(ripple.isRemoved());
        assertNull(level.activeRipple());
    }

    /** #402: one spark burst per pickup, the same for a coin and for a Bat; none for a second touch, none replayed after a death, cleared with the room. */
    @Test void aPickupThrowsOneSparkBurstAndDeathReplaysNothing() {
        Level level = forest();
        Collectable coin = level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).findFirst().orElseThrow();
        assertEquals(0, level.pickupSparks().sparks().count());
        coin.collideWithPlayer();
        assertEquals(1, level.pickupSparks().ringCount(), "and one ring");
        level.update(0f);
        assertNull(level.activeRipple(), "no screen ripple on a pickup: the lens hid the burst");
        assertFalse(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.effects.RippleEffect.class::isInstance));
        assertEquals(PickupSparksFx.SPARKS, level.pickupSparks().sparks().count());
        assertEquals(coin.centerX(), level.pickupSparks().sparks().x(0), 0f, "from the item centre");
        assertEquals(coin.centerY(), level.pickupSparks().sparks().y(0), 0f);
        coin.collideWithPlayer();
        assertEquals(PickupSparksFx.SPARKS, level.pickupSparks().sparks().count(), "already collected: nothing more");
        level.resetCurrentRoom(player());
        assertEquals(0, level.pickupSparks().sparks().count(), "cleared with the room");
        level.update(1f);
        assertEquals(0, level.pickupSparks().sparks().count(), "the coin that returns after the death replays nothing");
        level.enterNextRoom(player());
        Collectable bat = artifact(level, ItemType.BAT);
        bat.collideWithPlayer();
        assertEquals(PickupSparksFx.SPARKS, level.pickupSparks().sparks().count(), "the same burst for an ingredient");
    }

    @Test void artifactPickupIsProvisionalAndDeathRestoresItWithoutChangingCoins() {
        Level level = forest();
        level.enterNextRoom(player()); // #417: the Bat lies on the second wall, in forest-walls
        Collectable bat = artifact(level, ItemType.BAT);
        bat.collideWithPlayer();
        bat.collideWithPlayer();
        assertEquals(1, artifacts.items().size());
        assertEquals(ItemType.BAT, artifacts.items().get(0).type());
        assertEquals(0, coins.get());
        assertTrue(bat.isRemoved());
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest-2"));
        level.resetCurrentRoom(player());
        assertTrue(artifacts.items().isEmpty());
        assertFalse(artifact(level, ItemType.BAT).isCollected());
    }

    @Test void roomExitsKeepArtifactsWithoutAutomaticCombatBonus() {
        Level level = forest();
        level.enterNextRoom(player()); // #417: the Bat is in forest-walls
        artifact(level, ItemType.BAT).collideWithPlayer();
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        level.enterNextRoom(player());
        artifact(level, ItemType.MUSHROOM).collideWithPlayer();
        assertEquals(2, artifacts.items().size());
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest-2"), "Mushroom is still provisional");
        level.resetCurrentRoom(player());
        assertEquals(1, artifacts.items().size());
        assertEquals(ItemType.BAT, artifacts.items().get(0).type());
        artifact(level, ItemType.MUSHROOM).collideWithPlayer();
        level.enterNextRoom(player());
        assertTrue(artifacts.hasFullSet("forest-2"));
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest-2"));
        level.resetCurrentRoom(player());
        assertFalse(level.objects.stream().anyMatch(o -> o instanceof Collectable c && c.type() == ItemType.MUSHROOM));
        assertEquals(2, artifacts.items().size());
    }

    @Test void bossRoomCanBeEnteredWithoutAnyArtifacts() {
        Level level = forest();
        for (int room = 0; room < 5; room++) level.enterNextRoom(player());
        assertNotNull(level.boss());
        level.player.debugTriggerCheckpoint();
        assertEquals(Player.State.IDLE, level.player.getState(), "Debug checkpoint cannot bypass boss");
        assertTrue(artifacts.items().isEmpty());
        assertEquals(1f, artifacts.bossWeaponDamageMultiplier("forest-2"));
    }

    @Test void retryRebuildsDestroyedWallsAndMovingTrapsWithoutStaleCollisionReferences() {
        map("forest-2");
        Level level = new Level(assets, game, player(), "room-test", Rectangle::new);
        var wall = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Wall.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Wall.class::cast).findFirst().orElseThrow();
        level.activateTrigger(level.triggers.stream().filter(t -> t.targetId.equals("door")).findFirst().orElseThrow());
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
    private void collect(Level level, ItemType... types) {
        var wanted = java.util.Set.of(types);
        new java.util.ArrayList<>(level.objects).stream()
            .filter(Collectable.class::isInstance).map(Collectable.class::cast)
            .filter(c -> wanted.contains(c.type())).forEach(Collectable::collideWithPlayer);
    }

    private com.jlogicsoftware.kimeria.entity.WorldObject roomOwner(Level level, int id) throws Exception {
        var owners = Level.class.getDeclaredField("roomStateOwners");
        owners.setAccessible(true);
        return ((java.util.Map<Integer, com.jlogicsoftware.kimeria.entity.WorldObject>) owners.get(level)).get(id);
    }

    @Test void carryableHandoffUsesSupportedRoomPlacementAndClearsBothHolderReferences() throws Exception {
        Level level = practice();
        var tool = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast)
            .filter(carryable -> carryable.id == 116).findFirst().orElseThrow();
        Player oldPlayer = level.player;
        var carriedField = Player.class.getDeclaredField("carried");
        carriedField.setAccessible(true);
        var authoredCollisionBlocks = new java.util.ArrayList<>(level.collisionBlocks);
        level.collisionBlocks.clear();
        level.collisionBlocks.add(com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.platform(48, 320, 12, 16));
        assertNull(level.findSafeCarryablePlacement(12), "A support too narrow for the full tool footprint is not a safe fallback");
        level.collisionBlocks.clear();
        level.collisionBlocks.add(com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.platform(400, 320, 200, 16));
        assertNull(level.findSafeCarryablePlacement(12), "A distant wide floor is outside the reachable entrance search");
        level.collisionBlocks.clear();
        level.collisionBlocks.addAll(authoredCollisionBlocks);
        var blockedAnchor = com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.wall(64, 308, 12, 12);
        var higherSupport = com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.platform(64, 240, 96, 16);
        level.collisionBlocks.add(blockedAnchor);
        level.collisionBlocks.add(higherSupport);
        carriedField.set(oldPlayer, tool);
        oldPlayer.position.set(250, 272); // The held hand is over the authored quicksand pit.
        tool.pickUp(oldPlayer);
        oldPlayer.releaseCarriedForRoomSecure();
        assertNull(oldPlayer.carriedObject());
        assertFalse(tool.isHeld());
        assertSupportedAndRetrievable(level, tool);

        oldPlayer.position.set(1260, 272); // The held hand would cross the room boundary here.
        carriedField.set(oldPlayer, tool);
        tool.pickUp(oldPlayer);
        oldPlayer.releaseCarriedForRoomSecure();
        assertNull(oldPlayer.carriedObject());
        assertFalse(tool.isHeld());
        assertSupportedAndRetrievable(level, tool);

        carriedField.set(oldPlayer, tool);
        tool.pickUp(oldPlayer);

        level.enterNextRoom(player());

        assertNull(oldPlayer.carriedObject());
        assertFalse(tool.isHeld());
        assertEquals(0, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast)
            .filter(carryable -> carryable.id == 116).count());

        level.returnForCollection(player());
        var restored = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast)
            .filter(carryable -> carryable.id == 116).findFirst().orElseThrow();
        assertFalse(restored.isHeld());
        assertSupportedAndRetrievable(level, restored);
        float restingY = restored.getY();
        for (int step = 0; step < 180; step++) restored.update(1f / 60f);
        assertFalse(restored.isRemoved());
        assertEquals(restingY, restored.getY(), .01f, "Room updates must keep the released tool supported");
        assertEquals(new Vector2(76, 308), new Vector2(restored.getX(), restored.getY()),
            "A blocked entrance anchor should use adjacent space on the same reachable floor");
        assertFalse(new Rectangle(64, 308, 12, 12).overlaps(restored.bounds()));
        assertTrue(restored.getY() > higherSupport.getY(), "A nearby high ledge is not a preferred handoff surface");
    }

    @Test void thrownCarryableKeepsItsLatestVelocityAndFreezesWhileItsRoomIsInactive() throws Exception {
        Level level = practice();
        var tools = java.util.List.of(
            (com.jlogicsoftware.kimeria.entity.items.Carryable) roomOwner(level, 116),
            (com.jlogicsoftware.kimeria.entity.items.Carryable) roomOwner(level, 117));
        tools.get(0).release(80, 308, true, true);
        tools.get(1).release(96, 308, true, true);
        var secured = tools.stream().map(com.jlogicsoftware.kimeria.entity.items.Carryable::snapshot).toList();
        level.enterNextRoom(player());
        for (int step = 0; step < 60; step++) level.update(1f / 60f);
        assertEquals(secured, tools.stream().map(com.jlogicsoftware.kimeria.entity.items.Carryable::snapshot).toList(),
            "Inactive stone and carryable torch owners do not advance their thrown motion");

        level.prepareHealer();
        level.returnForCollection(player());
        for (int index = 0; index < tools.size(); index++) {
            var resumed = (com.jlogicsoftware.kimeria.entity.items.Carryable) roomOwner(level, 116 + index);
            assertEquals(secured.get(index), resumed.snapshot(), "Revisit restores position, velocity, thrown, and removed fields");
            resumed.update(.05f);
            assertTrue(resumed.getX() > secured.get(index).x(), "A restored thrown tool resumes its saved motion");
        }
    }

    @Test void fixedTorchAndItsLeverRestoreTheOwnedFireStateAndCanToggleAgain() throws Exception {
        Level level = practice();
        var dimTarget = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        dimTarget.id = 2011; dimTarget.type = "Actionable"; dimTarget.name = "shared-light";
        dimTarget.x = 160; dimTarget.y = 240; dimTarget.width = 12; dimTarget.height = 12;
        dimTarget.properties.put("type", "Torch"); dimTarget.properties.put("Intensity", "0");
        var brightTarget = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        brightTarget.id = 2012; brightTarget.type = "Actionable"; brightTarget.name = "shared-light";
        brightTarget.x = 192; brightTarget.y = 240; brightTarget.width = 12; brightTarget.height = 12;
        brightTarget.properties.put("type", "Torch"); brightTarget.properties.put("Intensity", "80");
        level.map.layer("SpawnPoints").add(dimTarget);
        level.map.layer("SpawnPoints").add(brightTarget);
        level.resetCurrentRoom(player());
        var torch = (Torch) roomOwner(level, 119);
        var firstShared = (Torch) roomOwner(level, 2011);
        var secondShared = (Torch) roomOwner(level, 2012);
        firstShared.performAction();
        assertEquals(200, firstShared.intensity());
        assertEquals(80, secondShared.intensity(), "Owners with one target name keep separate authored-ID snapshots");
        var trigger = level.triggers.stream().filter(t -> t.targetId.equals("practice-light")).findFirst().orElseThrow();
        assertFalse(torch.isBurning());
        level.activateTrigger(trigger);
        assertTrue(torch.isBurning());
        assertEquals(200, torch.intensity());

        level.enterNextRoom(player());
        level.prepareHealer();
        level.returnForCollection(player());
        var restoredTorch = (Torch) roomOwner(level, 119);
        assertTrue(restoredTorch.isBurning());
        assertEquals(200, restoredTorch.intensity());
        assertEquals(200, ((Torch) roomOwner(level, 2011)).intensity());
        assertEquals(80, ((Torch) roomOwner(level, 2012)).intensity());
        var restoredTrigger = level.triggers.stream().filter(t -> t.targetId.equals("practice-light")).findFirst().orElseThrow();
        level.activateTrigger(restoredTrigger);
        assertFalse(restoredTorch.isBurning(), "The lever derives its state from the restored torch and toggles it off");
        assertEquals(0, restoredTorch.intensity());

        level.resetCurrentRoom(player());
        restoredTorch = (Torch) roomOwner(level, 119);
        assertTrue(restoredTorch.isBurning(), "Death rollback restores the last secured lit state");
        restoredTrigger = level.triggers.stream().filter(t -> t.targetId.equals("practice-light")).findFirst().orElseThrow();
        level.activateTrigger(restoredTrigger);

        level.enterNextRoom(player());
        level.prepareHealer();
        level.returnForCollection(player());
        restoredTorch = (Torch) roomOwner(level, 119);
        assertFalse(restoredTorch.isBurning());
        assertEquals(0, restoredTorch.intensity());

        Level fresh = practice();
        assertFalse(((Torch) roomOwner(fresh, 119)).isBurning(), "A fresh Level starts from authored torch state");
    }

    private void assertSupportedAndRetrievable(Level level, com.jlogicsoftware.kimeria.entity.items.Carryable carryable) {
        assertTrue(level.currentRoomBounds().contains(carryable.bounds()));
        assertFalse(level.blocksObject(carryable.bounds(), true));
        assertTrue(level.collisionBlocks.stream().filter(block -> block.isActive() && !block.isQuickSand())
            .anyMatch(block -> Math.abs(carryable.getY() + carryable.getHeight() - block.getY()) <= .1f
                && carryable.getX() >= block.getX()
                && carryable.getX() + carryable.getWidth() <= block.getX() + block.getWidth()),
            "The full carryable footprint needs stable collision support");
    }

    @Test void forestThreeRegistersBatAndMushroomAsItsOptionalIngredients() {
        Level level = forest();
        level.enterNextRoom(player()); // #417: the Bat is in forest-walls
        assertEquals(java.util.Set.of(ItemType.BAT, ItemType.MUSHROOM), artifacts.requiredForLevel("forest-2"));
        assertNotNull(artifact(level, ItemType.BAT)); // The Mushroom lives in a later room; the route replays collect it.
    }

    @Test void canBrewFollowsTheLevelsIngredientsRegistry() {
        coins.set(25);
        Level level = forest();
        collect(level, ItemType.COIN);
        level.enterNextRoom(player());
        collect(level, ItemType.BAT); // #417: forest-walls
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        level.enterNextRoom(player());
        collect(level, ItemType.COIN);
        level.prepareHealer();
        assertFalse(level.canBrew(), "forest-2 requires Bat and Mushroom");
        artifacts.requireForLevel("forest-2", java.util.Set.of(ItemType.BAT));
        assertTrue(level.canBrew(), "A level whose ingredients are only Bat is gated by that");
        artifacts.requireForLevel("forest-2", java.util.Set.of());
        assertFalse(level.canBrew(), "No required ingredients never qualifies");
    }

    @Test void healerNeedsEveryUniqueForestCoinAndPaysOnceWithoutSpendingEarlierCoins() {
        coins.set(19); // Currency from another level.
        Level level = forest();
        collect(level, ItemType.COIN);
        level.enterNextRoom(player());
        collect(level, ItemType.BAT); // #417: forest-walls
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        level.enterNextRoom(player());
        collect(level, ItemType.COIN, ItemType.MUSHROOM);
        assertEquals(6, level.forestCoinTotal());
        assertFalse(level.canBrew(), "Current-room pickups must first be secured");
        level.prepareHealer();
        assertEquals(6, level.forestCoinsSecured());
        assertEquals(25, coins.get());
        coins.set(19);
        assertFalse(level.canBrew(), "Even inconsistent inventory cannot spend prior-level currency");
        assertFalse(level.brewElixir());
        coins.set(25);
        assertTrue(level.canBrew());
        assertTrue(level.brewElixir());
        assertEquals(19, coins.get());
        assertTrue(artifacts.items().isEmpty());
        assertTrue(artifacts.hasFullSet("forest-2"), "Payment must not erase the collection result");
        assertEquals(10, artifacts.bossWeaponDamageMultiplier("forest-2"));
        assertEquals(1, artifacts.bossWeaponDamageMultiplier("forest-1"));
        assertFalse(level.brewElixir());
        assertEquals(19, coins.get());
        level.commitBossEncounter();
        assertThrows(IllegalStateException.class, () -> level.returnForCollection(player()));
        level.enterNextRoom(player());
        level.resetCurrentRoom(player());
        assertEquals(10, artifacts.bossWeaponDamageMultiplier("forest-2"));
        assertFalse(level.healerAhead());
        level.bossDefeated();
        assertEquals(1, artifacts.bossWeaponDamageMultiplier("forest-2"));
    }

    @Test void priorCurrencyCannotReplaceMissingForestCoinsOrIngredients() {
        for (ItemType missing : new ItemType[]{ItemType.BAT, ItemType.MUSHROOM, ItemType.COIN}) {
            artifacts.clear(); coins.set(999);
            Level level = forest();
            ItemType[] wanted = java.util.Arrays.stream(ItemType.values())
                .filter(t -> t != missing).toArray(ItemType[]::new);
            collect(level, wanted);
            level.enterNextRoom(player());
            collect(level, wanted); // #417: the Bat is in forest-walls
            level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
            level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
            level.enterNextRoom(player());
            collect(level, wanted);
            level.prepareHealer();
            assertFalse(level.canBrew(), "Missing " + missing);
            assertFalse(level.brewElixir());
            assertEquals(1, artifacts.bossWeaponDamageMultiplier("forest-2"));
        }
    }

    @Test void collectionTripsKeepKillsTrapsAndUniqueCoinsAndRollbackOnlyNewPickups() throws Exception {
        Level level = forest();
        var enemies = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.enemy.Enemy.class::isInstance)
            .map(o -> (com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>) o).toList();
        var killed = enemies.get(0);
        killed.collidedWithActor(true); // Secure a real defeat before its death animation ends.
        var surviving = enemies.get(1);
        surviving.position.x += 8;
        float survivorX = surviving.getX();
        collect(level, ItemType.COIN); // Deliberately miss Bat. (The forest no longer holds a Bomb, D128: its secured state is tested on bomb-rooms-test.)
        level.enterNextRoom(player()); // forest-walls: the Bat on the second wall, missed on purpose (#417).
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        level.enterNextRoom(player());
        collect(level, ItemType.COIN); // Deliberately miss Mushroom.
        level.prepareHealer();
        assertFalse(level.canBrew());
        assertEquals(6, level.forestCoinsSecured());
        for (int trip = 0; trip < 3; trip++) {
            level.returnForCollection(player());
            assertEquals(new Vector2(64, 272), level.player.position);
            assertEquals(6, coins.get());
            assertFalse(level.objects.contains(killed));
            if (trip == 0) {
                assertTrue(level.objects.contains(surviving));
                assertEquals(survivorX, surviving.getX());
            }
            assertFalse(level.objects.stream().anyMatch(o -> o instanceof Collectable c && c.type() == ItemType.COIN));
            level.resetCurrentRoom(player()); // Old coins and kills are secured; nothing here is provisional.
            assertEquals(2, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.enemy.Enemy.class::isInstance).count());
            level.enterNextRoom(player()); // forest-walls holds the Bat (#417)
            collect(level, ItemType.BAT);
            assertEquals(1, artifacts.items().size());
            level.resetCurrentRoom(player()); // The new Bat is provisional.
            assertTrue(artifacts.items().isEmpty());
            assertNotNull(artifact(level, ItemType.BAT));
            level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
            level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
            level.enterNextRoom(player());
            level.prepareHealer();
            assertEquals(6, level.forestCoinsSecured());
            var visitsField = Level.class.getDeclaredField("visits");
            visitsField.setAccessible(true);
            assertEquals(5, ((java.util.Map<?, ?>) visitsField.get(level)).size(),
                "Secured snapshots remain bounded to the five visited rooms across repeated returns");
        }
        level.returnForCollection(player());
        level.enterNextRoom(player());
        collect(level, ItemType.BAT);
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        level.enterNextRoom(player());
        collect(level, ItemType.MUSHROOM);
        level.prepareHealer();
        assertTrue(level.canBrew());
    }

    @Test void collectionReturnsKeepOneLiveInstancePerAuthoredCarryableId() {
        Level level = practice();
        for (int trip = 0; trip < 3; trip++) {
            level.enterNextRoom(player());
            level.prepareHealer();
            level.returnForCollection(player());
            assertEquals(1, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance)
                .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).filter(c -> c.id == 116).count(),
                "Each activated room has one live instance per authored carryable ID");
        }
    }

    @Test void healerRecognitionBelongsToLiveLevelAcrossReturnAndRoomDeath() {
        Level level = forest();
        assertThrows(IllegalStateException.class, level::beginPreBossHealerMeeting);
        level.enterNextRoom(player());
        assertThrows(IllegalStateException.class, level::beginPreBossHealerMeeting, "forest-walls is not before the healer");
        level.enterNextRoom(player()); // forest-walls -> forest-fissure (#366)
        level.enterNextRoom(player()); // forest-fissure -> forest-glade (#331)
        level.enterNextRoom(player());
        assertTrue(level.healerAhead());
        level.prepareHealer(); // Preparation alone does not consume the first meeting.
        assertFalse(level.beginPreBossHealerMeeting());

        level.resetCurrentRoom(player());
        level.prepareHealer();
        assertTrue(level.beginPreBossHealerMeeting(), "A room death keeps recognition for the live level");
        level.returnForCollection(player());
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
        level.prepareHealer();
        assertTrue(level.beginPreBossHealerMeeting(), "Collection return keeps recognition");

        Level restarted = forest();
        restarted.enterNextRoom(player()); restarted.enterNextRoom(player()); restarted.enterNextRoom(player()); restarted.enterNextRoom(player());
        restarted.prepareHealer();
        assertFalse(restarted.beginPreBossHealerMeeting(), "A fresh Level starts a new encounter");
    }

    /** The spent-trap half of the collection trips the forest no longer has (D128: its Bomb became a leg-hold, which keeps no state): a detonated Bomb stays spent on every return. */
    @Test void aDetonatedBombStaysSpentAcrossCollectionTrips() {
        Level level = bombRooms();
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        bomb.collideWithPlayer(); bomb.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 1.01f);
        assertTrue(bomb.isRemoved());
        level.enterNextRoom(player()); level.prepareHealer();
        for (int trip = 0; trip < 3; trip++) {
            level.returnForCollection(player());
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof Bomb && !o.isRemoved()), "trip " + trip + ": a spent Bomb is not rebuilt");
            level.resetCurrentRoom(player());
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof Bomb && !o.isRemoved()), "trip " + trip + ": nor by a retry");
            level.enterNextRoom(player()); level.prepareHealer();
        }
    }

    @Test void warnedTrapResumesItsSavedCountdownOnReturnAndRestoresItAfterRoomDeath() {
        Level level = bombRooms();
        Bomb trap = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        trap.collideWithPlayer(); trap.update(TrapWarnings.DEFAULT_WARNING_SECONDS * .4f);
        level.enterNextRoom(player()); level.prepareHealer(); level.returnForCollection(player());
        Bomb revisited = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        assertEquals(trap.snapshot(), revisited.snapshot());
        revisited.update(TrapWarnings.DEFAULT_WARNING_SECONDS * .2f);
        level.resetCurrentRoom(player());
        Bomb retry = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        assertEquals(trap.snapshot(), retry.snapshot());
        retry.update(TrapWarnings.DEFAULT_WARNING_SECONDS * .61f);
        assertTrue(retry.isRemoved());
    }

    @Test void burnedBatApproachAndSwitchChainsAreRestoredOnEveryCollectionTrip() throws Exception {
        Level level = forest();
        // Exercise a linked chain beside the shipped Bat route, without exempting any state.
        var platform = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        platform.id = 2000; platform.type = "FallingPlatform";
        platform.x = 416; platform.y = 256; platform.width = 32; platform.height = 16;
        level.map.layer("SpawnPoints").add(platform);
        var trigger = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        trigger.id = 2001; trigger.type = "Trigger"; trigger.name = "approach";
        trigger.x = 96; trigger.y = 288; trigger.width = 32; trigger.height = 32;
        level.map.layer("SpawnPoints").add(trigger);
        var wall = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        wall.id = 2002; wall.type = "Actionable"; wall.name = "approach";
        wall.x = 416; wall.y = 272; wall.width = 32; wall.height = 48;
        wall.properties.put("type", "Wall");
        level.map.layer("SpawnPoints").add(wall);
        var lift = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        lift.id = 2003; lift.type = "Escalator"; lift.name = "approach";
        lift.x = 440; lift.y = 272; lift.width = 32; lift.height = 16;
        lift.properties.put("offPos", "2");
        level.map.layer("SpawnPoints").add(lift);
        level.resetCurrentRoom(player());
        var initialFalling = level.fallingPlatforms.get(0);
        assertEquals(256, initialFalling.getY());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof Collectable c && c.type() == ItemType.BAT), "#417: the Bat left the entry room");
        var initialDoor = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Wall.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Wall.class::cast).findFirst().orElseThrow();
        var initialLift = level.escalators.get(0);
        assertTrue(initialDoor.isActive());
        assertEquals(com.jlogicsoftware.kimeria.entity.objects.Escalator.State.RUN, initialLift.getState());
        initialLift.update(1.5f);
        initialLift.update(.01f);
        assertEquals(-1, initialLift.snapshot().direction());
        assertTrue(initialLift.snapshot().facingRight());
        float savedLiftX = initialLift.getX();
        level.activateTrigger(level.triggers.stream().filter(t -> t.targetId.equals("approach")).findFirst().orElseThrow());
        assertTrue(initialDoor.isRemoved());
        assertEquals(com.jlogicsoftware.kimeria.entity.objects.Escalator.State.IDLE, initialLift.getState());
        var expectedLiftSnapshot = initialLift.snapshot();
        initialFalling.collideWithActor(); initialFalling.update(2); initialFalling.update(2);
        assertTrue(initialFalling.isRemoved());
        collect(level, ItemType.COIN);
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); collect(level, ItemType.COIN);
        level.prepareHealer(); level.returnForCollection(player());
        assertTrue(roomOwner(level, 2002).isRemoved());
        assertFalse(level.collisionBlocks.contains(roomOwner(level, 2002)), "The removed wall must not block collision");
        assertTrue(roomOwner(level, 2000).isRemoved(), "A spent one-shot platform remains spent after collection return");
        assertFalse(level.fallingPlatforms.contains(roomOwner(level, 2000)), "Removed platform is pruned from live lists");
        var restoredLift = level.escalators.get(0);
        assertEquals(com.jlogicsoftware.kimeria.entity.objects.Escalator.State.IDLE, restoredLift.getState());
        assertEquals(savedLiftX, restoredLift.getX());
        assertEquals(expectedLiftSnapshot, restoredLift.snapshot(), "Direction, activity, facing, and animation time survive return");

        var expectedLiftState = com.jlogicsoftware.kimeria.entity.objects.Escalator.State.IDLE;
        for (int trip = 0; trip < 3; trip++) {
            var approachTrigger = level.triggers.stream().filter(t -> t.targetId.equals("approach")).findFirst().orElseThrow();
            assertEquals("approach", restoredLift.getTargetId());
            var beforeToggle = restoredLift.getState();
            level.activateTrigger(approachTrigger);
            assertNotEquals(beforeToggle, restoredLift.getState(), "The restored trigger must still toggle its linked lift");
            expectedLiftState = restoredLift.getState();
            restoredLift.update(.2f);
            savedLiftX = restoredLift.getX();
            expectedLiftSnapshot = restoredLift.snapshot();
            level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
            collect(level, ItemType.COIN);
            level.prepareHealer();
            level.returnForCollection(player());
            restoredLift = level.escalators.get(0);
            assertTrue(roomOwner(level, 2002).isRemoved());
            assertTrue(roomOwner(level, 2000).isRemoved());
            assertFalse(level.fallingPlatforms.contains(roomOwner(level, 2000)));
            assertEquals(expectedLiftState, restoredLift.getState());
            assertEquals(savedLiftX, restoredLift.getX());
            assertEquals(expectedLiftSnapshot, restoredLift.snapshot(), "The latest bounded room snapshot wins after each trip");
            assertEquals(6, level.forestCoinsSecured());
        }
        assertEquals(6, coins.get());
        assertTrue(artifacts.items().isEmpty());
    }

    @Test void collapsedOneShotPlatformRemainsCollapsedOnCollectionReturnAndDeath() throws Exception {
        Level level = forest();
        var platformObject = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        platformObject.id = 2010; platformObject.type = "FallingPlatform";
        platformObject.x = 416; platformObject.y = 256; platformObject.width = 32; platformObject.height = 16;
        level.map.layer("SpawnPoints").add(platformObject);
        level.resetCurrentRoom(player());
        var first = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2010);
        first.collideWithActor(); first.update(8f); first.update(2f);
        assertTrue(first.isRemoved());

        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
        level.prepareHealer();
        level.returnForCollection(player());
        var afterReturn = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2010);
        assertNotSame(first, afterReturn);
        assertTrue(afterReturn.isRemoved());
        assertTrue(level.fallingPlatforms.isEmpty());
        afterReturn.collideWithActor(); // A removed platform cannot be rearmed by contact.
        assertTrue(afterReturn.isRemoved());

        level.resetCurrentRoom(player());
        var afterDeath = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2010);
        assertNotSame(afterReturn, afterDeath);
        assertTrue(afterDeath.isRemoved(), "Death restores the latest secured collapsed snapshot");
        assertFalse(level.fallingPlatforms.contains(afterDeath));
    }

    @Test void fallingPlatformWarningSnapshotResumesFreshAndFreezesWhileInactive() throws Exception {
        Level level = forest();
        var platformObject = new com.jlogicsoftware.kimeria.tiled.TiledObject();
        platformObject.id = 2011; platformObject.type = "FallingPlatform";
        platformObject.x = 416; platformObject.y = 256; platformObject.width = 32; platformObject.height = 16;
        level.map.layer("SpawnPoints").add(platformObject);
        level.resetCurrentRoom(player());
        var first = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2011);
        first.collideWithActor();
        first.update(.2f);
        var securedWarning = first.snapshot();
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
        for (int frame = 0; frame < 90; frame++) level.update(1f / 60f);
        assertEquals(securedWarning, first.snapshot(), "Inactive room objects do not advance");

        level.prepareHealer();
        level.returnForCollection(player());
        var warningResume = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2011);
        assertNotSame(first, warningResume);
        assertEquals(securedWarning, warningResume.snapshot());
        level.update(.01f); // Flush the freshly reconstructed transient light into the live object list.
        assertTrue(level.objects.stream().anyMatch(o -> o instanceof Torch torch && !torch.isRemoved()
                && Math.abs(torch.getX() - 416) < .01f),
            "A restored warning gets a fresh transient light");

        warningResume.update(securedWarning.warningSeconds());
        warningResume.update(.4f);
        var midFall = warningResume.snapshot();
        assertTrue(midFall.fallSeconds() > 0f);
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); // Secures the mid-fall state.
        level.returnForCollection(player());
        var midFallResume = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2011);
        assertEquals(midFall, midFallResume.snapshot(), "Mid-fall progress resumes without old torch state");
        assertFalse(level.objects.stream().anyMatch(o -> o instanceof Torch torch && !torch.isRemoved()
                && Math.abs(torch.getX() - 416) < .01f),
            "A mid-fall restore does not recreate an obsolete warning light");
        level.resetCurrentRoom(player());
        var afterDeath = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2011);
        assertEquals(midFall, afterDeath.snapshot(), "Death rolls back to the latest secured mid-fall baseline");

        for (int frame = 0; frame < 180 && !afterDeath.isRemoved(); frame++) afterDeath.update(1f / 60f);
        assertTrue(afterDeath.isRemoved());
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player());
        level.returnForCollection(player());
        var removedResume = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 2011);
        assertTrue(removedResume.isRemoved());
        assertFalse(level.fallingPlatforms.contains(removedResume), "Removed owner is pruned after restore");
    }

    @Test void anEmptyAuthoredCoinSetNeverAutomaticallyQualifiesForTheElixir() {
        var previous = Gdx.files;
        HeadlessFiles files = new HeadlessFiles();
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, args) -> {
                if (method.getName().equals("internal") && args[0].equals("tiles/forest-2.tmx"))
                    return files.internal("tiles/healer-empty.tmx");
                return method.invoke(files, args);
            });
        try {
            coins.set(999);
            Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
            collect(level, ItemType.BAT, ItemType.MUSHROOM);
            level.prepareHealer();
            assertTrue(artifacts.hasFullSet("forest-2"));
            assertEquals(0, level.forestCoinTotal());
            assertFalse(level.canBrew());
            assertFalse(level.brewElixir());
            assertEquals(999, coins.get());
        } finally { Gdx.files = previous; }
    }

    @Test void fogIsAbsentFromGameplayButRetainedInTheMenu() throws Exception {
        Level level = forest();
        var ambience = Level.class.getDeclaredField("ambience"); ambience.setAccessible(true);
        assertFalse(((java.util.List<?>) ambience.get(level)).stream()
            .anyMatch(com.jlogicsoftware.kimeria.effects.FogEffect.class::isInstance));
        for (int room = 0; room < 4; room++) level.enterNextRoom(player());
        assertFalse(((java.util.List<?>) ambience.get(level)).stream()
            .anyMatch(com.jlogicsoftware.kimeria.effects.FogEffect.class::isInstance));
        var fog = com.jlogicsoftware.kimeria.ui.MenuBackdrop.class.getDeclaredField("fog"); fog.setAccessible(true);
        assertInstanceOf(com.jlogicsoftware.kimeria.effects.FogEffect.class,
            fog.get(new com.jlogicsoftware.kimeria.ui.MenuBackdrop()));
    }

}
