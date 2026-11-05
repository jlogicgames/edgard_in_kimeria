package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.effects.Firefly;
import com.jlogicsoftware.kimeria.effects.RainDrop;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.effects.SkyBackground;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Entity;
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
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.items.Wall;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
import com.jlogicsoftware.kimeria.entity.objects.StaticPlatform;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.EnumSet;
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
    private static final float CARRYABLE_SUPPORT_OVERLAP = 0.1f;
    private static final float CARRYABLE_SEARCH_STEP = 1f;
    public final String name;
    public final TiledMapData map;
    public Player player;
    public final RangedStock rangedStock;
    private int roomStartAmmo;

    public com.badlogic.gdx.graphics.g2d.TextureRegion objectSprite(SpriteSheets.Sheet sheet) {
        return sheet.frame(assets);
    }

    public final List<CollisionBlock> collisionBlocks = new ArrayList<>();
    public final List<Escalator> escalators = new ArrayList<>();
    public final List<Trigger> triggers = new ArrayList<>();
    public final List<Ladder> ladders = new ArrayList<>();
    public final List<FallingPlatform> fallingPlatforms = new ArrayList<>();
    /** Map-authored lethal volumes that bound play space across room ownership. */
    private final List<Rectangle> deadlines = new ArrayList<>();
    private final List<StaticPlatform> staticPlatforms = new ArrayList<>();
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
    /**
     * A map-authored hint zone (object type {@code Hint} in the {@code Hints} layer). The hero's body
     * inside the zone shows {@code message} (a {@link Msg} name) and/or, when {@code controls} is
     * set, the contextual pickup/lever/ladder instructions. Zones are stateless: they apply again
     * on every retry and return. A level without zones never shows tutorial hints.
     */
    public record HintZone(String id, Rectangle bounds, Msg message, boolean controls) { }
    private final List<HintZone> hintZones = new ArrayList<>();
    public List<HintZone> hintZones() { return List.copyOf(hintZones); }

    private static boolean insideZone(HintZone zone, Rectangle body) {
        return zone.bounds.contains(body.x + body.width / 2, body.y + body.height / 2);
    }

    /** True while the hero stands in a zone that teaches the contextual controls. */
    public boolean controlHintsActive() {
        Rectangle body = player.hitboxBounds();
        for (HintZone zone : hintZones) if (zone.controls && insideZone(zone, body)) return true;
        return false;
    }

    /** The authored message zone holding the hero, or null. */
    public HintZone messageHint() {
        Rectangle body = player.hitboxBounds();
        for (HintZone zone : hintZones) if (zone.message != null && insideZone(zone, body)) return zone;
        return null;
    }

    private void loadHintZones() {
        Set<String> ids = new HashSet<>();
        for (TiledObject o : map.layer("Hints")) {
            if (!"Hint".equals(o.type))
                throw new IllegalArgumentException("Hints layer holds only Hint objects: " + describe(o));
            if (o.width <= 0 || o.height <= 0)
                throw new IllegalArgumentException("Hint needs positive bounds: " + describe(o));
            roomLayout.owner(o); // A zone belongs to exactly one room.
            Rectangle zone = new Rectangle(o.x, o.y, o.width, o.height);
            if (!currentRoomBoundsOf(zone))
                throw new IllegalArgumentException("Hint crosses a room boundary: " + describe(o));
            String message = o.prop("message", "");
            boolean controls = o.propBool("controls", false);
            if (message.isEmpty() && !controls)
                throw new IllegalArgumentException("Hint needs a message or controls: " + describe(o));
            Msg msg = message.isEmpty() ? null : Msg.valueOf(message); // A mistyped key fails loudly.
            hintZones.add(new HintZone(o.name, zone, msg, controls));
        }
    }

    private boolean currentRoomBoundsOf(Rectangle zone) {
        for (RoomLayout.Room room : roomLayout.rooms) {
            Rectangle b = room.bounds(); // Inclusive, unlike Rectangle.contains(Rectangle).
            if (zone.x >= b.x && zone.y >= b.y && zone.x + zone.width <= b.x + b.width
                && zone.y + zone.height <= b.y + b.height) return true;
        }
        return false;
    }

    private final List<WorldObject> ambience = new ArrayList<>();
    private sealed interface ObjectSnapshot permits CarryableState, TorchState, WallState,
        EscalatorState, FallingPlatformState, LadderState { }
    private record CarryableState(Carryable.Snapshot value) implements ObjectSnapshot { }
    private record TorchState(float x, float y, int intensity, boolean burning,
                              boolean removed) implements ObjectSnapshot { }
    private record WallState(boolean active, boolean removed, float y, boolean raised) implements ObjectSnapshot { }
    private record EscalatorState(Escalator.Snapshot value) implements ObjectSnapshot { }
    private record FallingPlatformState(FallingPlatform.Snapshot value) implements ObjectSnapshot { }
    private record LadderState(Ladder.Snapshot value) implements ObjectSnapshot { }

    /** One bounded secured entry per room, including immutable snapshots of mutable world owners. */
    private record Visit(Map<Integer, Enemy<?>> enemies, Set<Integer> defeated,
                         Map<Integer, Bomb.Snapshot> traps,
                         Map<Integer, ObjectSnapshot> objects) { }
    private final Map<Integer, Visit> visits = new HashMap<>();
    private final Map<Integer, Enemy<?>> roomEnemies = new HashMap<>();
    private final Map<Integer, Bomb> roomTraps = new HashMap<>();
    /** Current-room owner registry retains identity even after an owner leaves a live list. */
    private final Map<Integer, WorldObject> roomStateOwners = new HashMap<>();
    private boolean revisiting, fightCommitted, brewed, healerMet;
    private final Set<Integer> authoredCoins = new HashSet<>();
    /** Ids of mechanisms that some Trigger points at through its {@code target} object property. */
    private final Set<Integer> referencedMechanisms = new HashSet<>();
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
        else dx = Math.max(room.x, Math.min(room.x + room.width - view.width, view.x + dx))
            - Math.max(room.x, Math.min(room.x + room.width - view.width, view.x));
        if (room.height <= view.height) dy = 0;
        else dy = Math.max(room.y, Math.min(room.y + room.height - view.height, view.y + dy))
            - Math.max(room.y, Math.min(room.y + room.height - view.height, view.y));
        return new Vector2(dx, dy);
    }

    public Rectangle currentRoomBounds() { return roomLayout.rooms.get(currentRoom).bounds(); }
    public String currentRoomName() { return roomLayout.rooms.get(currentRoom).id(); }

    /** Finds the nearest clear, supported location for a room-owned carryable. */
    public Rectangle findSafeCarryablePlacement(float carryableSize) {
        Rectangle room = currentRoomBounds();
        Vector2 entrance = roomLayout.rooms.get(currentRoom).entrance();
        float standingY = entrance.y + player.size.y;
        Rectangle best = null;
        float bestVerticalDistance = Float.POSITIVE_INFINITY;
        float bestHorizontalDistance = Float.POSITIVE_INFINITY;
        for (var block : collisionBlocks) {
            if (!block.isActive() || block.isQuickSand()) continue;
            Rectangle candidate = supportedCandidate(entrance.x, standingY, room, carryableSize,
                block.getX(), block.getY(), block.getWidth());
            if (candidate != null) {
                float verticalDistance = Math.abs(candidate.y + carryableSize - standingY);
                float horizontalDistance = Math.abs(candidate.x - entrance.x);
                if (verticalDistance < bestVerticalDistance
                    || (verticalDistance == bestVerticalDistance && horizontalDistance < bestHorizontalDistance)) {
                    best = candidate;
                    bestVerticalDistance = verticalDistance;
                    bestHorizontalDistance = horizontalDistance;
                }
            }
        }
        return best;
    }

    private Rectangle supportedCandidate(float anchorX, float standingY, Rectangle room,
                                         float carryableSize, float supportX, float supportY,
                                         float supportWidth) {
        float minimumX = Math.max(room.x, supportX + CARRYABLE_SUPPORT_OVERLAP);
        float maximumX = Math.min(room.x + room.width - carryableSize,
            supportX + supportWidth - carryableSize - CARRYABLE_SUPPORT_OVERLAP);
        minimumX = Math.max(minimumX, anchorX - player.size.x);
        maximumX = Math.min(maximumX, anchorX + player.size.x);
        if (minimumX > maximumX) return null;
        float candidateY = supportY - carryableSize;
        if (Math.abs(supportY - standingY) > player.size.y) return null;
        float nearestX = Math.max(minimumX, Math.min(maximumX, anchorX));
        int steps = (int) Math.ceil((maximumX - minimumX) / CARRYABLE_SEARCH_STEP);
        for (int step = 0; step <= steps; step++) {
            float offset = step * CARRYABLE_SEARCH_STEP;
            Rectangle right = candidateAt(nearestX + offset, candidateY, carryableSize, room,
                minimumX, maximumX);
            if (right != null) return right;
            if (step > 0) {
                Rectangle left = candidateAt(nearestX - offset, candidateY, carryableSize, room,
                    minimumX, maximumX);
                if (left != null) return left;
            }
        }
        return null;
    }

    private Rectangle candidateAt(float x, float y, float carryableSize, Rectangle room,
                                  float minimumX, float maximumX) {
        if (x < minimumX || x > maximumX) return null;
        Rectangle candidate = new Rectangle(x, y, carryableSize, carryableSize);
        if (x < room.x || x > room.x + room.width - carryableSize
            || !room.contains(candidate) || blocksObject(candidate, true) || overlapsQuickSand(candidate)) return null;
        return candidate;
    }

    private boolean overlapsQuickSand(Rectangle bounds) {
        for (var block : collisionBlocks) {
            if (!block.isActive() || !block.isQuickSand()) continue;
            if (bounds.overlaps(new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight())))
                return true;
        }
        return false;
    }
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
        if (revisit) roomStartAmmo = rangedStock.count();
        else rangedStock.restore(roomStartAmmo);
        revisiting = revisit;
        game.artifacts().resetRoom();
        objects.clear(); collisionBlocks.clear(); escalators.clear(); triggers.clear(); ladders.clear();
        fallingPlatforms.clear(); staticPlatforms.clear();
        pendingSpawns.clear(); pendingPickups.clear();
        boss = null;
        roomEnemies.clear(); roomTraps.clear(); roomStateOwners.clear();
        victory = completionSent = false;
        clearCameraEffects();
        player = nextPlayer;
        spawnObjects();
        addCollisions();
        restoreRoomObjects();
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
        this(assets, game, player, levelName, visibleWorldRect, new RangedStock());
    }

    public Level(Assets assets, GameContext game, Player player, String levelName,
                 Supplier<Rectangle> visibleWorldRect, RangedStock rangedStock) {
        this.rangedStock = rangedStock;
        roomStartAmmo = rangedStock.count();
        this.assets = assets;
        this.game = game;
        this.player = player;
        this.name = levelName;
        this.entryCoins = game.coinsCollected();
        this.map = TiledMapData.load("tiles/" + levelName + ".tmx", assets);

        roomLayout = new RoomLayout(map);
        // Fail early on ambiguous ownership and cross-room trigger links.
        for (TiledObject o : map.layer("SpawnPoints")) {
            if ("Deadline".equals(o.type)) {
                if (o.width <= 0 || o.height <= 0)
                    throw new IllegalArgumentException("Deadline needs positive bounds: " + o.id);
                deadlines.add(new Rectangle(o.x, o.y, o.width, o.height));
                continue;
            }
            int owner = roomLayout.owner(o);
            if ("ThornRoot".equals(o.type) || "ThornTuft".equals(o.type)) validateThorn(o, owner);
            if ("Trigger".equals(o.type) && !o.prop("target", "").isEmpty()) {
                TiledObject target = findMechanism(o);
                referencedMechanisms.add(target.id);
                if (roomLayout.owner(target) != owner)
                    throw new IllegalArgumentException("Trigger crosses room boundary: " + describe(o));
            } else if ("Trigger".equals(o.type)) for (TiledObject target : map.layer("SpawnPoints")) {
                if (("Actionable".equals(target.type) || "Escalator".equals(target.type)
                    || "Ladder".equals(target.type))
                    && o.name.equals(target.name) && roomLayout.owner(target) != owner)
                    throw new IllegalArgumentException("Trigger crosses room boundary: " + o.name);
            }
        }
        if (deadlines.isEmpty()) throw new IllegalArgumentException("Level needs a Deadline SpawnPoint");
        loadHintZones();
        for (TiledObject o : map.layer("Collisions")) roomLayout.owner(o);
        for (TiledObject o : map.layer("SpawnPoints")) {
            if ("Collectable".equals(o.type) && ItemType.fromMapName(o.name) == ItemType.COIN)
                authoredCoins.add(o.id);
        }
        spawnObjects();
        addCollisions();

        for (int i = 0, fireflies = map.propInt("fireflies", 0); i < fireflies; i++) {
            ambience.add(new Firefly(new Vector2(map.widthPx(), map.heightPx())));
        }
        // The healer recipe's artifacts are level configuration read from the map, e.g. "BAT,MUSHROOM".
        // A mistyped name throws IllegalArgumentException from ItemType.fromMapName: failing loudly is intended.
        Set<ItemType> ingredients = EnumSet.noneOf(ItemType.class);
        for (String token : map.prop("ingredients", "").split(",")) {
            if (!token.isBlank()) ingredients.add(ItemType.fromMapName(token.trim()));
        }
        game.artifacts().requireForLevel(name, ingredients);
        if ("forest-1".equals(levelName)) {
            sky = new SkyBackground(assets);
            for (int i = 0; i < 48; i++) {
                ambience.add(new RainDrop(new Vector2(map.widthPx(), map.heightPx()), visibleWorldRect));
            }
        }

        placePlayer();
    }

    public boolean healerAhead() {
        return map.propBool("healerBeforeBoss", false) && !fightCommitted && map.layer("SpawnPoints").stream()
            .anyMatch(o -> "Boss".equals(o.type) && roomLayout.owner(o) == currentRoom + 1);
    }

    /** True when the player's body has entered a map-authored out-of-play volume. */
    public boolean touchesDeadline(Rectangle playerBounds) {
        return deadlines.stream().anyMatch(playerBounds::overlaps);
    }

    /** Records one genuine pre-boss dialogue entry and reports whether it is a return visit. */
    public boolean beginPreBossHealerMeeting() {
        if (!healerAhead()) throw new IllegalStateException("No healer before this room");
        boolean returning = healerMet;
        healerMet = true;
        return returning;
    }

    /** Securing here is idempotent, including the second call when the return is chosen. */
    public void prepareHealer() {
        if (!healerAhead()) throw new IllegalStateException("No healer before this room");
        keepPickups();
        secureVisit();
        roomStartAmmo = rangedStock.count();
    }

    public int forestCoinTotal() { return authoredCoins.size(); }
    public int forestCoinsSecured() {
        return (int) authoredCoins.stream().filter(keptPickups::contains).count();
    }
    public boolean canBrew() {
        return !brewed && healerAhead() && !authoredCoins.isEmpty()
            && forestCoinsSecured() == authoredCoins.size() && game.coinsCollected() - entryCoins >= authoredCoins.size()
            && !game.artifacts().requiredForLevel(name).isEmpty()
            && game.artifacts().requiredForLevel(name).stream()
                .allMatch(type -> game.artifacts().hasIngredient(name, type));
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
        player.releaseCarriedForRoomSecure();
        Set<Integer> defeated = new HashSet<>();
        Visit previous = visits.get(currentRoom);
        if (previous != null) defeated.addAll(previous.defeated());
        roomEnemies.forEach((id, enemy) -> { if (enemy.isDefeated()) defeated.add(id); });
        Map<Integer, Bomb.Snapshot> traps = new HashMap<>();
        roomTraps.forEach((id, bomb) -> traps.put(id, bomb.snapshot()));
        Map<Integer, ObjectSnapshot> objectSnapshots = new HashMap<>();
        roomStateOwners.forEach((id, owner) -> {
            ObjectSnapshot snapshot = snapshotRoomObject(owner);
            if (snapshot != null) objectSnapshots.put(id, snapshot);
        });
        visits.put(currentRoom, new Visit(Map.copyOf(roomEnemies), Set.copyOf(defeated),
            Map.copyOf(traps), Map.copyOf(objectSnapshots)));
    }

    private ObjectSnapshot snapshotRoomObject(WorldObject owner) {
        if (owner instanceof Carryable carryable) return new CarryableState(carryable.snapshot());
        if (owner instanceof Torch torch) return new TorchState(torch.getX(), torch.getY(),
            torch.intensity(), torch.isBurning(), torch.isRemoved());
        if (owner instanceof Wall wall) return new WallState(wall.isActive(), wall.isRemoved(), wall.getY(), wall.isRaised());
        if (owner instanceof Escalator escalator) return new EscalatorState(escalator.snapshot());
        if (owner instanceof FallingPlatform platform) return new FallingPlatformState(platform.snapshot());
        if (owner instanceof Ladder ladder) return new LadderState(ladder.snapshot());
        return null;
    }

    private void restoreRoomObjects() {
        Visit saved = visits.get(currentRoom);
        if (saved == null) return;
        saved.objects().forEach((id, snapshot) -> {
            WorldObject owner = roomStateOwners.get(id);
            if (owner == null) return;
            if (owner instanceof Carryable carryable && snapshot instanceof CarryableState state) {
                carryable.restore(state.value());
            } else if (owner instanceof Torch torch && snapshot instanceof TorchState state) {
                torch.position.set(state.x(), state.y());
                torch.restoreState(state.intensity(), state.burning());
                if (state.removed()) torch.removeFromParent();
            } else if (owner instanceof Wall wall && snapshot instanceof WallState state) {
                wall.restoreState(state.active(), state.removed(), state.y(), state.raised());
            } else if (owner instanceof Escalator escalator && snapshot instanceof EscalatorState state) {
                escalator.restore(state.value());
            } else if (owner instanceof FallingPlatform platform && snapshot instanceof FallingPlatformState state) {
                platform.restore(state.value());
            } else if (owner instanceof Ladder ladder && snapshot instanceof LadderState state) {
                ladder.restore(state.value());
            }
        });
        roomStateOwners.forEach((id, owner) -> {
            if (owner.isRemoved()) {
                objects.remove(owner);
                escalators.remove(owner);
                fallingPlatforms.remove(owner);
                collisionBlocks.remove(owner);
            }
        });
    }

    private void registerRoomStateOwner(int id, WorldObject owner) {
        if (roomStateOwners.put(id, owner) != null)
            throw new IllegalArgumentException("Duplicate mutable room-state object ID: " + id);
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
            if ("Deadline".equals(o.type)) continue;
            if (roomLayout.owner(o) != currentRoom) continue;
            if (restoreRetainedObject(o)) continue;
            switch (o.type) {
                case "Player" -> {
                    player.position.set(o.x, o.y);
                    player.facingRight = true;
                }
                case "Collectable" -> spawnCollectable(o, ItemType.fromMapName(o.name));
                case "FireballRefill" -> {
                    if (!keptPickups.contains(o.id)) objects.add(new com.jlogicsoftware.kimeria.entity.items.FireballRefill(this, game, o.id, o.x, o.y));
                }
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
                    Torch torch = new Torch(assets, o.x + o.width / 2f, o.y + o.height / 2f, o.width, o.height, intensity, "");
                    objects.add(torch);
                    registerRoomStateOwner(o.id, torch);
                }
                case "Stone", "CarryableTorch" -> {
                    Carryable carryable = new Carryable(this, o.id,
                        "Stone".equals(o.type) ? Carryable.Kind.STONE : Carryable.Kind.TORCH, o.x, o.y);
                    carryable.setRespawns(o.propBool("respawn", false));
                    objects.add(carryable);
                    registerRoomStateOwner(o.id, carryable);
                }
                case "Trigger" -> {
                    Trigger trigger = new Trigger(o.id, o.x, o.y, o.width, o.height, triggerKey(o));
                    trigger.throwActivated = o.propBool("throwActivated", false);
                    objects.add(trigger);
                    triggers.add(trigger);
                }
                case "Actionable" -> spawnActionable(o);
                case "Ladder" -> {
                    Ladder ladder = new Ladder(assets, o.x, o.y, o.width, o.height, mechanismKey(o),
                        o.propFloat("dropDistance", 0));
                    ladders.add(ladder);
                    objects.add(ladder);
                    // Resolve the ladder top before the floor platform underneath it, so a
                    // falling player lands on the nearest support instead of the lower floor.
                    collisionBlocks.add(0, ladder.topSupport());
                    if (o.propFloat("dropDistance", 0) > 0) registerRoomStateOwner(o.id, ladder);
                }
                case "Escalator" -> {
                    Escalator escalator = new Escalator(assets, o.x, o.y, o.width, o.height,
                        o.propBool("isVertical", false), o.propFloat("offNeg", 0), o.propFloat("offPos", 0), mechanismKey(o), o.propBool("running", true));
                    escalators.add(escalator);
                    registerRoomStateOwner(o.id, escalator);
                }
                case "FallingPlatform" -> {
                    FallingPlatform platform = new FallingPlatform(assets, o.x, o.y, o.width, o.height, this::queueSpawn, game.trapWarnings());
                    fallingPlatforms.add(platform);
                    registerRoomStateOwner(o.id, platform);
                }
                case "ThornRoot", "ThornTuft" -> objects.add(ThornTrap.fromMap(assets, game.trapWarnings(), o,
                    () -> player.canLeaveRoom() ? player.hitboxBounds() : null, () -> player.collidedWithActor(false)));
                default -> {
                }
            }
        }
        spawnLevers();
    }

    /** Proximity traps (D44) fail at load when they could not be jumped, float, or start triggered. */
    private void validateThorn(TiledObject o, int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        Rectangle entranceBody = new Rectangle(room.entrance().x + player.hitbox.offsetX, room.entrance().y + player.hitbox.offsetY,
            player.hitbox.width, player.hitbox.height);
        String problem = ThornTrap.problem(o, map.layer("Collisions"), map.layer("SpawnPoints"), room.bounds(), entranceBody);
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /** "Name [id] at (x, y)" -- enough to find the object in Tiled without guessing. */
    private static String describe(TiledObject o) {
        return "'" + o.name + "' [" + o.id + "] at (" + (int) o.x + ", " + (int) o.y + ")";
    }

    private static boolean isMechanismType(String type) {
        return "Actionable".equals(type) || "Escalator".equals(type) || "Ladder".equals(type);
    }

    /** Resolves a Trigger's {@code target} object property to a mechanism in SpawnPoints, or fails with a precise message. */
    private TiledObject findMechanism(TiledObject trigger) {
        String raw = trigger.prop("target", "");
        int targetId;
        try {
            targetId = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Trigger " + describe(trigger) + ": target '" + raw + "' is not an object reference");
        }
        for (TiledObject candidate : map.layer("SpawnPoints")) {
            if (candidate.id != targetId) continue;
            if (!isMechanismType(candidate.type))
                throw new IllegalArgumentException("Trigger " + describe(trigger) + ": target " + describe(candidate)
                    + " has type '" + candidate.type + "', expected Actionable, Escalator or Ladder");
            return candidate;
        }
        for (TiledObject candidate : map.layer("Collisions")) {
            if (candidate.id == targetId)
                throw new IllegalArgumentException("Trigger " + describe(trigger) + ": target " + describe(candidate)
                    + " is in the Collisions layer; mechanisms must be in SpawnPoints");
        }
        throw new IllegalArgumentException("Trigger " + describe(trigger) + ": target object " + targetId + " does not exist");
    }

    /** Link key a Trigger uses: the referenced object's id when {@code target} is set, else its legacy name. */
    private static String triggerKey(TiledObject o) {
        String target = o.prop("target", "").trim();
        return target.isEmpty() ? o.name : "#" + target;
    }

    /** Link key a mechanism answers to: "#id" when some Trigger references it, else its legacy name. */
    private String mechanismKey(TiledObject o) {
        return referencedMechanisms.contains(o.id) ? "#" + o.id : o.name;
    }

    private void spawnLevers() {
        for (TiledObject o : map.layer("SpawnPoints")) {
            if (!"Trigger".equals(o.type) || roomLayout.owner(o) != currentRoom) continue;
            Trigger trigger = triggers.stream().filter(t -> t.id == o.id).findFirst().orElseThrow();
            List<Actionable> linked = new ArrayList<>();
            for (WorldObject object : objects)
                if (object instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId()))
                    linked.add(actionable);
            // Escalators only supply the lever's visual state when nothing else shares the link
            // (a name shared by a wall and a lift keeps the wall as the lever's face).
            if (linked.isEmpty()) for (Escalator escalator : escalators)
                if (trigger.targetId.equals(escalator.getTargetId())) linked.add(escalator);
            if (linked.size() != 1) throw new IllegalArgumentException("Trigger " + describe(o)
                + " needs exactly one linked mechanism in SpawnPoints (Actionable, Escalator or Ladder), found "
                + linked.size() + (o.prop("target", "").isEmpty() ? " by name; set its 'target' property" : ""));
            Actionable target = linked.get(0);
            if (target instanceof Torch torch) objects.add(new Lever(assets, trigger, torch));
            else if (target instanceof Ladder ladder) objects.add(new Lever(assets, trigger, ladder::isLowered));
            else if (target instanceof Wall wall) objects.add(new Lever(assets, trigger, wall::isRaised));
            else if (target instanceof Escalator escalator) objects.add(new Lever(assets, trigger, escalator::isRunning));
            else throw new IllegalArgumentException("Trigger " + describe(o) + " has no lever visual for its linked mechanism");
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
        String targetId = mechanismKey(o);
        if ("Torch".equals(type)) {
            int intensity = (int) o.propFloat("Intensity", 0);
            Torch torch = new Torch(assets, o.x + o.width / 2f, o.y + o.height / 2f, o.width, o.height, intensity, targetId);
            objects.add(torch);
            registerRoomStateOwner(o.id, torch);
            if (intensity == 0) torch.toggleFire(false);
        } else if ("Wall".equals(type)) {
            boolean verticalMovement = "vertical".equals(o.prop("movement", ""));
            float moveDurationSeconds = o.propFloat("time", Wall.DEFAULT_MOVE_DURATION_SECONDS);
            Wall wall = new Wall(assets, o.x, o.y, o.width, o.height, targetId,
                verticalMovement, moveDurationSeconds);
            objects.add(wall);
            collisionBlocks.add(wall);
            registerRoomStateOwner(o.id, wall);
        }
    }

    private void addCollisions() {
        for (TiledObject o : map.layer("Collisions")) {
            if (roomLayout.owner(o) != currentRoom) continue;
            CollisionBlock block = switch (o.type) {
                case "Platform" -> {
                    staticPlatforms.add(new StaticPlatform(assets, o.x, o.y, o.width, o.height));
                    yield CollisionBlock.platform(o.x, o.y, o.width, o.height);
                }
                case "QuickSand" -> CollisionBlock.quickSand(o.x, o.y, o.width, o.height);
                case "Wall" -> CollisionBlock.wall(o.x, o.y, o.width, o.height);
                default -> CollisionBlock.plain(o.x, o.y, o.width, o.height);
            };
            collisionBlocks.add(block);
        }
        collisionBlocks.add(ceiling(currentRoomBounds()));
    }

    /** How thick the invisible ceiling is: far more than one frame of jump travel, so nothing tunnels through. */
    static final float CEILING_THICKNESS = 64f;

    /**
     * Every room is closed from above (D38, #215). The side walls are clamberable, so without a ceiling
     * the hero could wall-jump up one and over the top of the room, out of the level. The slab sits
     * entirely above the room (its underside is the room's top edge), so it cuts no jump, ladder,
     * platform or hint zone inside the room. Added here rather than authored in each map so that
     * every present and future room, in every level, has it.
     */
    static CollisionBlock ceiling(Rectangle room) {
        return CollisionBlock.plain(room.x, room.y - CEILING_THICKNESS, room.width, CEILING_THICKNESS);
    }

    /** Solid placement/flight check. One-way platforms only block placement, never horizontal flight. */
    public boolean blocksObject(Rectangle bounds, boolean placement) {
        if (!currentRoomBounds().contains(bounds)) return true;
        for (CollisionBlock block : collisionBlocks) {
            if (!block.isActive() || block.isQuickSand() || (!placement && block.isPlatform())) continue;
            if (bounds.overlaps(new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight()))) return true;
        }
        for (var platform : escalators) if (bounds.overlaps(new Rectangle(platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight()))) return true;
        for (var platform : fallingPlatforms) if (bounds.overlaps(new Rectangle(platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight()))) return true;
        return false;
    }

    /** Feedback belongs to a visible linked object, never to the invisible trigger rectangle. */
    public Entity mechanismFeedbackTarget(Trigger trigger) {
        if (trigger == null) return null;
        for (WorldObject object : objects) {
            if (object instanceof Lever lever && !lever.isRemoved() && lever.trigger == trigger) return lever;
        }
        Entity nearest = null;
        for (WorldObject object : objects) {
            if (object instanceof Entity entity && object instanceof Actionable actionable
                && !object.isRemoved() && trigger.targetId.equals(actionable.getTargetId())) {
                nearest = nearerFeedbackTarget(trigger, nearest, entity);
            }
        }
        for (Escalator escalator : escalators) {
            if (!escalator.isRemoved() && trigger.targetId.equals(escalator.getTargetId()))
                nearest = nearerFeedbackTarget(trigger, nearest, escalator);
        }
        return nearest;
    }

    /** The visible mechanisms a trigger drives, so the player can see what a lever will affect. */
    public List<Entity> linkedMechanisms(Trigger trigger) {
        List<Entity> linked = new ArrayList<>();
        if (trigger == null) return linked;
        for (WorldObject object : objects) {
            if (object instanceof Entity entity && object instanceof Actionable actionable
                && !object.isRemoved() && trigger.targetId.equals(actionable.getTargetId())) linked.add(entity);
        }
        for (Escalator escalator : escalators) {
            if (!escalator.isRemoved() && trigger.targetId.equals(escalator.getTargetId())) linked.add(escalator);
        }
        return linked;
    }

    private static Entity nearerFeedbackTarget(Trigger trigger, Entity current, Entity candidate) {
        if (current == null) return candidate;
        float distance = Vector2.dst2(trigger.centerX(), trigger.centerY(), candidate.centerX(), candidate.centerY());
        float currentDistance = Vector2.dst2(trigger.centerX(), trigger.centerY(), current.centerX(), current.centerY());
        if (distance < currentDistance || (distance == currentDistance &&
            (candidate.getX() < current.getX() || (candidate.getX() == current.getX() && candidate.getY() < current.getY()))))
            return candidate;
        return current;
    }

    /**
     * A thrown carryable touching a {@code throwActivated} trigger operates it once per throw (D39).
     * Returns true when it did, which ends the throw's lever check.
     */
    public boolean throwHitsLever(Carryable thrown) {
        Rectangle bounds = thrown.bounds();
        for (Trigger trigger : triggers) {
            if (!trigger.throwActivated || trigger.isRemoved()) continue;
            if (!bounds.overlaps(new Rectangle(trigger.getX(), trigger.getY(), trigger.getWidth(), trigger.getHeight()))) continue;
            activateTrigger(trigger);
            return true;
        }
        return false;
    }

    public void activateTrigger(Trigger trigger) {
        Set<Actionable> activated = new HashSet<>();
        for (WorldObject obj : objects) {
            if (obj instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId())
                && activated.add(actionable)) {
                actionable.performAction();
            }
        }
        for (CollisionBlock block : collisionBlocks) {
            if (block instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId())
                && activated.add(actionable)) {
                actionable.performAction();
            }
        }
        for (Escalator escalator : escalators) {
            if (trigger.targetId.equals(escalator.getTargetId()) && activated.add(escalator)) {
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
        for (WorldObject o : objects) if (o instanceof Carryable c) c.followHolder();
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
        for (StaticPlatform platform : staticPlatforms) platform.render(batch);
        for (Escalator e : escalators) e.render(batch);
        for (FallingPlatform p : fallingPlatforms) p.render(batch);
        for (WorldObject o : objects) o.render(batch);
        for (WorldObject o : ambience) o.render(batch);
        if (!player.isRemoved()) player.render(batch);
        player.renderInteractionTarget(batch);
    }

    /** F1 debug gizmos: collision blocks in green, Deadlines in orange, actor hitboxes in red. */
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

        sr.setColor(com.badlogic.gdx.graphics.Color.SKY);
        for (Ladder ladder : ladders) sr.rect(ladder.getX(), ladder.getY(), ladder.getWidth(), ladder.getHeight());

        sr.setColor(1f, 0.55f, 0.1f, 1f);
        for (Rectangle deadline : deadlines) sr.rect(deadline.x, deadline.y, deadline.width, deadline.height);

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
