package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.FogEffect;
import com.jlogicsoftware.kimeria.effects.Firefly;
import com.jlogicsoftware.kimeria.effects.RainDrop;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.effects.SkyBackground;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.WorldObject;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.enemy.YellowMob;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.items.Actionable;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.items.Wall;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Port of Dart's {@code Level}: loads a .tmx, spawns everything from its
 * "SpawnPoints"/"Collisions" object layers, and owns the per-frame update
 * and render passes for the whole scene.
 */
public class Level implements WorldObject {
    public final String name;
    public final TiledMapData map;
    public final Player player;

    public final List<CollisionBlock> collisionBlocks = new ArrayList<>();
    public final List<Escalator> escalators = new ArrayList<>();
    public final List<Trigger> triggers = new ArrayList<>();
    public final List<FallingPlatform> fallingPlatforms = new ArrayList<>();
    /** Everything else: enemies, collectables, bombs, checkpoints, torches, fireflies, rain, fog. */
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
        this.map = TiledMapData.load("tiles/" + levelName + ".tmx", assets);

        spawnObjects();
        addCollisions();

        if ("forest".equals(levelName)) {
            for (int i = 0; i < 24; i++) {
                objects.add(new Firefly(new Vector2(map.widthPx(), map.heightPx())));
            }
            objects.add(new FogEffect(visibleWorldRect));
        }
        if ("forest-1".equals(levelName)) {
            sky = new SkyBackground(assets);
            for (int i = 0; i < 48; i++) {
                objects.add(new RainDrop(new Vector2(map.widthPx(), map.heightPx()), visibleWorldRect));
            }
        }

        player.setLevel(this);
        player.spawned();
    }

    private void spawnObjects() {
        for (TiledObject o : map.layer("SpawnPoints")) {
            switch (o.type) {
                case "Player" -> {
                    player.position.set(o.x, o.y);
                    player.facingRight = true;
                }
                case "Collectable" -> objects.add(new Collectable(assets, game, this::queueSpawn, o.name, o.x, o.y, o.width, o.height));
                case "Bat" -> {
                    boolean vertical = o.propBool("isVertical", false);
                    Bat bat = new Bat(assets, game, o.x, o.y, o.width, o.height, vertical,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    bat.setPlayer(player);
                    objects.add(bat);
                }
                case "Checkpoint" -> objects.add(new Checkpoint(o.x, o.y, o.width, o.height));
                case "YellowMob" -> {
                    YellowMob mob = new YellowMob(assets, game, o.x, o.y, o.width, o.height,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    mob.setPlayer(player);
                    mob.setLevel(this);
                    objects.add(mob);
                }
                case "RedMob" -> {
                    RedMob mob = new RedMob(assets, game, o.x, o.y, o.width, o.height,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    mob.setPlayer(player);
                    mob.setLevel(this);
                    objects.add(mob);
                }
                case "Bomb" -> objects.add(new Bomb(assets, game, this::queueSpawn, o.x, o.y, o.width, o.height));
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
                        o.propBool("isVertical", false), o.propFloat("offNeg", 0), o.propFloat("offPos", 0), "");
                    escalators.add(escalator);
                }
                case "FallingPlatform" -> {
                    FallingPlatform platform = new FallingPlatform(assets, o.x, o.y, o.width, o.height, this::queueSpawn);
                    fallingPlatforms.add(platform);
                }
                default -> {
                }
            }
        }
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
        if (sky != null) sky.update(dt);
        for (Escalator e : escalators) e.update(dt);
        for (FallingPlatform p : fallingPlatforms) p.update(dt);
        for (WorldObject o : objects) o.update(dt);
        player.update(dt);

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
    }

    private static void drawHitbox(ShapeRenderer sr, Actor<?> actor) {
        Hitbox hb = actor.hitbox;
        if (hb.radius > 0) {
            sr.circle(actor.centerX(), actor.centerY(), hb.radius);
        } else if (hb.width > 0 && hb.height > 0) {
            float x = actor.getX() + actor.facingAwareOffsetX();
            float y = actor.getY() + hb.offsetY;
            sr.rect(x, y, hb.width, hb.height);
        }
    }
}
