package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.Firefly;
import com.jlogicsoftware.kimeria.effects.RainDrop;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.effects.SkyBackground;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.WorldObject;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.enemy.YellowMob;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.items.Actionable;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.items.Wall;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;

/**
 * Port of Dart's {@code Level}: loads a .tmx, spawns everything from its
 * "SpawnPoints"/"Collisions" object layers, and owns the per-frame update
 * and render passes for the whole scene.
 */
public class Level implements WorldObject {
    public final String name;
    public final TiledMapData map;
    public Player player;

    public final List<CollisionBlock> collisionBlocks = new ArrayList<>();
    public final List<Escalator> escalators = new ArrayList<>();
    public final List<Trigger> triggers = new ArrayList<>();
    public final List<FallingPlatform> fallingPlatforms = new ArrayList<>();
    /** Everything else: enemies, collectables, bombs, checkpoints, torches, fireflies, rain. */
    public final List<WorldObject> objects = new ArrayList<>();
    /**
     * Objects spawned mid-frame (a coin's pickup ripple, a bomb's explosion,
     * ...) land here instead of straight into {@link #objects}: they're
     * typically spawned from inside {@code Player}'s own iteration over
     * {@code objects} (checking overlaps), and adding to a list while
     * iterating it throws {@code ConcurrentModificationException}. Drained
     * into {@code objects} once per frame, after that iteration is over.
     */
    private final List<WorldObject> pendingSpawns = new ArrayList<>();

    private final Assets assets;
    private final GameContext game;
    /** Screen-fixed drifting clouds behind the world; null on levels without a sky. */
    private SkyBackground sky;
    public final RoomLayout roomLayout;
    private int currentRoom;
    private final List<WorldObject> ambience = new ArrayList<>();
    /** One bounded entry per visited room: traversal objects are deliberately not retained. */
    private record Visit(Map<Integer, Enemy<?>> enemies, Set<Integer> defeated,
                         Map<Integer, Bomb.Snapshot> traps) { }
    private final Map<Integer, Visit> visits = new HashMap<>();
    private final Map<Integer, Enemy<?>> roomEnemies = new HashMap<>();
    private final Map<Integer, Bomb> roomTraps = new HashMap<>();
    private boolean revisiting, fightCommitted, brewed;
    private final Set<Integer> authoredCoins = new HashSet<>();
    private final int entryCoins;
    private final Set<Integer> pendingPickups = new HashSet<>();
    private final Set<Integer> keptPickups = new HashSet<>();

    private Boss boss;
    private int retryBossEnergy = -1;

    public Boss boss() { return boss; }

    public void updatePresentation(float realDt) {
        if (boss != null) boss.updatePresentation(realDt);
    }
    private boolean victory, completionSent;
    private float shakeRemaining;

    /** True only after the death presentation and the completed automatic exit. */
    public boolean victoryExitComplete() { return victory && completionSent; }

    /** Checkpoints, including the debug shortcut, cannot bypass an authored boss. */
    public boolean hasBossEncounter() {
        return map.layer("SpawnPoints").stream().anyMatch(o -> "Boss".equals(o.type));
    }

    public void playerDied() {
        clearCameraEffects();
        if (boss != null) {
            retryBossEnergy = boss.energy();
            boss.abandonAttack();
        }
    }

    public void bossDefeated() {
        clearCameraEffects();
        game.artifacts().endBossElixir(name);
        victory = true;
        player.beginVictoryWalk();
        game.setNormalTime();
    }

    public void shakeAfterBossAttack() { if (!victory) shakeRemaining = .18f; }
    public void shakeAfterEmpoweredHit() { shakeRemaining = .18f; }
    public void clearCameraEffects() { shakeRemaining = 0; }

    /** Visual displacement only; narrow rooms keep their existing centred framing. */
    public Vector2 cameraShakeOffset(Rectangle view) {
        Rectangle room = currentRoomBounds();
        float age = .18f - shakeRemaining;
        float amplitude = 1.25f * Math.max(0, shakeRemaining / .18f);
        float dx = (float) Math.sin(age * 110) * amplitude;
        float dy = (float) Math.sin(age * 137) * amplitude;
        if (room.width <= view.width) dx = 0;
        else dx = Math.max(room.x, Math.min(room.x + room.width - view.width, view.x + dx)) - view.x;
        if (room.height <= view.height) dy = 0;
        else dy = Math.max(room.y, Math.min(room.y + room.height - view.height, view.y + dy)) - view.y;
        return new Vector2(dx, dy);
    }

    public Rectangle currentRoomBounds() { return roomLayout.rooms.get(currentRoom).bounds(); }
    public boolean atRoomExit() {
        Rectangle exit = roomLayout.rooms.get(currentRoom).exit();
        return !victory && exit != null && player.canLeaveRoom() && exit.overlaps(player.hitboxBounds());
    }
    public void recordPickup(int id) { pendingPickups.add(id); }
    public void keepPickups() {
        keptPickups.addAll(pendingPickups);
        pendingPickups.clear();
        game.artifacts().keepRoom();
    }
    public Set<Integer> keptPickups() { return Set.copyOf(keptPickups); }

    public boolean hasNextRoom() { return currentRoom + 1 < roomLayout.rooms.size(); }

    public void enterNextRoom(Player nextPlayer) {
        if (!hasNextRoom()) throw new IllegalStateException("No next room");
        keepPickups();
        secureVisit();
        currentRoom++;
        activateRoom(nextPlayer, true);
    }

    /** Dev-only map reload: keep a valid position and activate its room without a transition. */
    public void reloadAt(Vector2 savedPosition) {
        for (int i = 0; i < roomLayout.rooms.size(); i++) {
            if (roomLayout.rooms.get(i).bounds().contains(savedPosition.x + player.size.x / 2,
                                                        savedPosition.y + player.size.y / 2)) {
                currentRoom = i;
                resetCurrentRoom(player);
                player.position.set(savedPosition);
                game.panCameraTo(player.roomCameraTarget(), 0.35f);
                return;
            }
        }
        // If the edited map removed this position, keep the first room's safe entrance.
    }

    public void resetCurrentRoom(Player nextPlayer) {
        activateRoom(nextPlayer, false);
    }

    private void activateRoom(Player nextPlayer, boolean revisit) {
        revisiting = revisit;
        game.artifacts().resetRoom();
        objects.clear(); collisionBlocks.clear(); escalators.clear(); triggers.clear(); fallingPlatforms.clear();
        pendingSpawns.clear(); pendingPickups.clear();
        boss = null;
        roomEnemies.clear(); roomTraps.clear();
        victory = completionSent = false;
        clearCameraEffects();
        player = nextPlayer;
        spawnObjects();
        addCollisions();
        placePlayer();
        if (boss != null && retryBossEnergy >= 0) boss.showRestoration(retryBossEnergy);
        retryBossEnergy = -1;
    }

    private void placePlayer() {
        player.position.set(roomLayout.rooms.get(currentRoom).entrance());
        player.facingRight = true;
        player.setLevel(this);
        player.spawned();
    }

    /** Queues {@code obj} to join {@link #objects} after the current update pass finishes. */
    public void queueSpawn(WorldObject obj) {
        pendingSpawns.add(obj);
    }

    public Level(Assets assets, GameContext game, Player player, String levelName,
                 Supplier<Rectangle> visibleWorldRect) {
        this.assets = assets;
        this.game = game;
        this.player = player;
        this.name = levelName;
        this.entryCoins = game.coinsCollected();
        this.map = TiledMapData.load("tiles/" + levelName + ".tmx", assets);

        roomLayout = new RoomLayout(map);
        // Fail early on ambiguous ownership and cross-room trigger links.
        for (TiledObject o : map.layer("SpawnPoints")) {
            int owner = roomLayout.owner(o);
            if ("Trigger".equals(o.type)) for (TiledObject target : map.layer("SpawnPoints")) {
                if (("Actionable".equals(target.type) || "Escalator".equals(target.type))
                    && o.name.equals(target.name) && roomLayout.owner(target) != owner)
                    throw new IllegalArgumentException("Trigger crosses room boundary: " + o.name);
            }
        }
        for (TiledObject o : map.layer("Collisions")) roomLayout.owner(o);
        for (TiledObject o : map.layer("SpawnPoints")) {
            if ("Collectable".equals(o.type) && ItemType.fromMapName(o.name) == ItemType.COIN)
                authoredCoins.add(o.id);
        }
        spawnObjects();
        addCollisions();

        if ("forest".equals(levelName)) {
            for (int i = 0; i < 24; i++) {
                ambience.add(new Firefly(new Vector2(map.widthPx(), map.heightPx())));
            }
        }
        if ("forest-1".equals(levelName)) {
            sky = new SkyBackground(assets);
            for (int i = 0; i < 48; i++) {
                ambience.add(new RainDrop(new Vector2(map.widthPx(), map.heightPx()), visibleWorldRect));
            }
        }

        placePlayer();
    }

    public boolean healerAhead() {
        return "forest".equals(name) && !fightCommitted && map.layer("SpawnPoints").stream()
            .anyMatch(o -> "Boss".equals(o.type) && roomLayout.owner(o) == currentRoom + 1);
    }

    /** Securing here is idempotent, including the second call when the return is chosen. */
    public void prepareHealer() {
        if (!healerAhead()) throw new IllegalStateException("No healer before this room");
        keepPickups();
        secureVisit();
    }

    public int forestCoinTotal() { return authoredCoins.size(); }
    public int forestCoinsSecured() {
        return (int) authoredCoins.stream().filter(keptPickups::contains).count();
    }
    public boolean canBrew() {
        return !brewed && healerAhead() && !authoredCoins.isEmpty()
            && forestCoinsSecured() == authoredCoins.size() && game.coinsCollected() - entryCoins >= authoredCoins.size()
            && game.artifacts().hasIngredient(name, ItemType.CHICKEN)
            && game.artifacts().hasIngredient(name, ItemType.KEY);
    }
    public boolean brewElixir() {
        if (!canBrew()) return false;
        if (!game.artifacts().consumeIngredients(name)) return false;
        game.spendCoins(authoredCoins.size());
        brewed = true;
        return true;
    }
    public void commitBossEncounter() {
        if (!healerAhead()) throw new IllegalStateException("No uncommitted boss encounter");
        prepareHealer();
        fightCommitted = true;
    }
    public void returnForCollection(Player nextPlayer) {
        if (!healerAhead()) throw new IllegalStateException("Collection return unavailable");
        prepareHealer();
        currentRoom = 0;
        activateRoom(nextPlayer, true);
    }

    private void secureVisit() {
        Set<Integer> defeated = new HashSet<>();
        Visit previous = visits.get(currentRoom);
        if (previous != null) defeated.addAll(previous.defeated());
        roomEnemies.forEach((id, enemy) -> { if (enemy.isDefeated()) defeated.add(id); });
        Map<Integer, Bomb.Snapshot> traps = new HashMap<>();
        roomTraps.forEach((id, bomb) -> traps.put(id, bomb.snapshot()));
        visits.put(currentRoom, new Visit(Map.copyOf(roomEnemies), Set.copyOf(defeated), Map.copyOf(traps)));
    }

    private boolean restoreRetainedObject(TiledObject o) {
        Visit saved = visits.get(currentRoom);
        if (saved == null) return false;
        if (saved.defeated().contains(o.id)) return true;
        Enemy<?> enemy = saved.enemies().get(o.id);
        if (revisiting && enemy != null) {
            enemy.revisit(player);
            roomEnemies.put(o.id, enemy);
            objects.add(enemy);
            return true;
        }
        return false;
    }

    private void spawnObjects() {
        for (TiledObject o : map.layer("SpawnPoints")) {
            if (roomLayout.owner(o) != currentRoom) continue;
            if (restoreRetainedObject(o)) continue;
            switch (o.type) {
                case "Player" -> {
                    player.position.set(o.x, o.y);
                    player.facingRight = true;
                }
                case "Collectable" -> spawnCollectable(o, ItemType.fromMapName(o.name));
                case "Chicken" -> spawnCollectable(o, ItemType.CHICKEN);
                case "Key" -> spawnCollectable(o, ItemType.KEY);
                case "Bat" -> {
                    boolean vertical = o.propBool("isVertical", false);
                    Bat bat = new Bat(assets, game, o.x, o.y, o.width, o.height, vertical,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    bat.setPlayer(player);
                    objects.add(bat);
                    roomEnemies.put(o.id, bat);
                }
                case "Checkpoint" -> objects.add(new Checkpoint(o.x, o.y, o.width, o.height));
                case "YellowMob" -> {
                    YellowMob mob = new YellowMob(assets, game, o.x, o.y, o.width, o.height,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    mob.setPlayer(player);
                    mob.setLevel(this);
                    objects.add(mob);
                    roomEnemies.put(o.id, mob);
                }
                case "Boss" -> {
                    boss = new Boss(assets, game, this, o.x, o.y);
                    boss.setPlayer(player);
                    objects.add(boss);
                }
                case "RedMob" -> {
                    RedMob mob = new RedMob(assets, game, o.x, o.y, o.width, o.height,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    mob.setPlayer(player);
                    mob.setLevel(this);
                    objects.add(mob);
                    roomEnemies.put(o.id, mob);
                }
                case "Bomb" -> {
                    Bomb bomb = new Bomb(assets, game, this::queueSpawn, blast -> {
                        if (blast.overlaps(player.hitboxBounds())) player.collidedWithActor(false);
                    }, o.x, o.y, o.width, o.height);
                    Visit saved = visits.get(currentRoom);
                    if (saved != null && saved.traps().containsKey(o.id)) bomb.restore(saved.traps().get(o.id));
                    roomTraps.put(o.id, bomb);
                    if (!bomb.isRemoved()) objects.add(bomb);
                }
                case "Torch" -> {
                    int intensity = (int) o.propFloat("Intensity", 80);
                    objects.add(new Torch(o.x + o.width / 2f, o.y + o.height / 2f, o.width, o.height, intensity, ""));
                }
                case "Trigger" -> {
                    Trigger trigger = new Trigger(o.x, o.y, o.width, o.height, o.name);
                    objects.add(trigger);
                    triggers.add(trigger);
                }
                case "Actionable" -> spawnActionable(o);
                case "Escalator" -> {
                    Escalator escalator = new Escalator(assets, o.x, o.y, o.width, o.height,
                        o.propBool("isVertical", false), o.propFloat("offNeg", 0), o.propFloat("offPos", 0), o.name);
                    escalators.add(escalator);
                }
                case "FallingPlatform" -> {
                    FallingPlatform platform = new FallingPlatform(assets, o.x, o.y, o.width, o.height, this::queueSpawn, game.trapWarnings());
                    fallingPlatforms.add(platform);
                }
                default -> {
                }
            }
        }
    }

    private void spawnCollectable(TiledObject o, ItemType type) {
        if (keptPickups.contains(o.id) || (type != ItemType.COIN && game.artifacts().contains(name, o.id))) return;
        objects.add(new Collectable(assets, game, this::queueSpawn, type.name(), o.x, o.y, o.width, o.height, () -> {
            recordPickup(o.id);
            if (type != ItemType.COIN) game.artifacts().collect(name, o.id, type);
        }));
    }

    private void spawnActionable(TiledObject o) {
        String type = o.prop("type", "");
        String targetId = o.name;
        if ("Torch".equals(type)) {
            int intensity = (int) o.propFloat("Intensity", 0);
            Torch torch = new Torch(o.x + o.width / 2f, o.y + o.height / 2f, o.width, o.height, intensity, targetId);
            objects.add(torch);
            if (intensity == 0) torch.toggleFire(false);
        } else if ("Wall".equals(type)) {
            Wall wall = new Wall(o.x, o.y, o.width, o.height, targetId);
            objects.add(wall);
            collisionBlocks.add(wall);
        }
    }

    private void addCollisions() {
        for (TiledObject o : map.layer("Collisions")) {
            if (roomLayout.owner(o) != currentRoom) continue;
            CollisionBlock block = switch (o.type) {
                case "Platform" -> CollisionBlock.platform(o.x, o.y, o.width, o.height);
                case "QuickSand" -> CollisionBlock.quickSand(o.x, o.y, o.width, o.height);
                case "Wall" -> CollisionBlock.wall(o.x, o.y, o.width, o.height);
                default -> CollisionBlock.plain(o.x, o.y, o.width, o.height);
            };
            collisionBlocks.add(block);
        }
    }

    public void activateTrigger(Trigger trigger) {
        for (WorldObject obj : objects) {
            if (obj instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId())) {
                actionable.performAction();
            }
        }
        for (CollisionBlock block : collisionBlocks) {
            if (block instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId())) {
                actionable.performAction();
            }
        }
        for (Escalator escalator : escalators) {
            if (trigger.targetId.equals(escalator.getTargetId())) {
                escalator.performAction();
            }
        }
    }

    /**
     * The most recently spawned coin ripple still running, or null. Like Dart and Rust, only
     * one is drawn at a time, so a second coin collected mid-ripple takes over from the first.
     */
    public RippleEffect activeRipple() {
        for (int i = objects.size() - 1; i >= 0; i--) {
            if (objects.get(i) instanceof RippleEffect ripple && !ripple.isRemoved()) return ripple;
        }
        return null;
    }

    @Override
    public void update(float dt) {
        shakeRemaining = Math.max(0, shakeRemaining - dt);
        if (victory) {
            if (player.victoryPausePressed()) { game.togglePause(); return; }
            dt = Math.min(dt, .05f);
            updateWorld(dt);
            finishUpdate();
            if (boss.deathFinished() && !completionSent) {
                player.advanceVictoryWalk(dt);
                // A room narrower than the screen is centred: walk beyond the VIEW edge too.
                float right = currentRoomBounds().x + Math.max(currentRoomBounds().width,
                    (currentRoomBounds().width + game.logicalResolution().x) / 2f);
                if (player.getX() > right + player.getWidth()) {
                    completionSent = true;
                    keepPickups();
                    game.loadNextLevel();
                }
            }
            return;
        }
        updateWorld(dt);
        player.update(dt);
        if (boss != null) boss.resolveCombat();

        finishUpdate();
    }

    /** The world keeps moving during victory; only the hero uses a protected scripted walk. */
    private void updateWorld(float dt) {
        if (sky != null) sky.update(dt);
        for (WorldObject o : ambience) o.update(dt);
        for (Escalator e : escalators) e.update(dt);
        for (FallingPlatform p : fallingPlatforms) p.update(dt);
        for (WorldObject o : objects) o.update(dt);
    }

    private void finishUpdate() {
        escalators.removeIf(WorldObject::isRemoved);
        fallingPlatforms.removeIf(WorldObject::isRemoved);
        collisionBlocks.removeIf(CollisionBlock::isRemoved);
        objects.removeIf(WorldObject::isRemoved);

        if (!pendingSpawns.isEmpty()) {
            objects.addAll(pendingSpawns);
            pendingSpawns.clear();
        }
    }

    /**
     * Draws only the tiles overlapping {@code visibleWorldRect} (with a
     * 1-tile margin), instead of every tile in the map regardless of the
     * camera. For forest-1 (80x23 = 1840 tiles) that's the difference
     * between ~1840 draw calls a frame and the ~40x23 actually on screen.
     */
    public void renderBackground(Batch batch, Rectangle visibleWorldRect) {
        int colStart = Math.max(0, (int) Math.floor(visibleWorldRect.x / map.tileWidth) - 1);
        int colEnd = Math.min(map.widthTiles - 1, (int) Math.ceil((visibleWorldRect.x + visibleWorldRect.width) / map.tileWidth) + 1);
        int rowStart = Math.max(0, (int) Math.floor(visibleWorldRect.y / map.tileHeight) - 1);
        int rowEnd = Math.min(map.heightTiles - 1, (int) Math.ceil((visibleWorldRect.y + visibleWorldRect.height) / map.tileHeight) + 1);

        for (int r = rowStart; r <= rowEnd; r++) {
            for (int c = colStart; c <= colEnd; c++) {
                int gid = map.backgroundGrid[r][c];
                var region = map.tile(gid);
                if (region != null) {
                    batch.draw(region, c * map.tileWidth, r * map.tileHeight, map.tileWidth, map.tileHeight);
                }
            }
        }
    }

    public void render(Batch batch, Rectangle visibleWorldRect) {
        if (sky != null) sky.render(batch, visibleWorldRect);
        renderBackground(batch, visibleWorldRect);
        for (Escalator e : escalators) e.render(batch);
        for (FallingPlatform p : fallingPlatforms) p.render(batch);
        for (WorldObject o : objects) o.render(batch);
        for (WorldObject o : ambience) o.render(batch);
        if (!player.isRemoved()) player.render(batch);
    }

    /** Port of Rust's F1 debug gizmos: collision blocks in green, actor hitboxes in red. */
    public void renderDebug(ShapeRenderer sr) {
        sr.setColor(0.2f, 0.9f, 0.4f, 1f);
        for (CollisionBlock block : collisionBlocks) {
            if (block.isActive()) {
                sr.rect(block.getX(), block.getY(), block.getWidth(), block.getHeight());
            }
        }
        for (Escalator e : escalators) {
            sr.rect(e.getX(), e.getY(), e.getWidth(), e.getHeight());
        }

        sr.setColor(0.9f, 0.3f, 0.3f, 1f);
        drawHitbox(sr, player);
        for (WorldObject o : objects) {
            if (o instanceof Actor<?> actor) {
                drawHitbox(sr, actor);
            }
        }

        // What kills on touch, where it differs from the body box.
        sr.setColor(1f, 0.55f, 0.1f, 1f);
        for (WorldObject o : objects) {
            if (o instanceof Enemy<?> enemy && enemy.hasSeparateHurtbox()) {
                Rectangle r = enemy.hurtBounds();
                sr.rect(r.x, r.y, r.width, r.height);
            }
        }

        // Melee attack colliders: yellow while winding up, magenta on the frames that hurt.
        for (WorldObject o : objects) {
            if (o instanceof Enemy<?> enemy && enemy.attackBounds() != null) {
                Rectangle r = enemy.attackBounds();
                if (enemy.isAttackActive()) sr.setColor(1f, 0.2f, 1f, 1f);
                else sr.setColor(1f, 0.9f, 0.2f, 1f);
                sr.rect(r.x, r.y, r.width, r.height);
            }
        }
    }

    private static void drawHitbox(ShapeRenderer sr, Actor<?> actor) {
        Hitbox hb = actor.hitbox;
        Rectangle b = actor.hitboxBounds();
        if (hb.radius > 0) {
            sr.circle(b.x + b.width / 2f, b.y + b.height / 2f, hb.radius);
        } else if (hb.width > 0 && hb.height > 0) {
            sr.rect(b.x, b.y, b.width, b.height);
        }
    }
}
