package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.effects.RemainsFx;
import com.jlogicsoftware.kimeria.effects.ParticleTextures;
import com.jlogicsoftware.kimeria.effects.SnailFx;
import com.jlogicsoftware.kimeria.effects.WolfFx;
import com.jlogicsoftware.kimeria.effects.SpiderFx;
import com.jlogicsoftware.kimeria.effects.EnvironmentFx;
import com.jlogicsoftware.kimeria.effects.FireballFx;
import com.jlogicsoftware.kimeria.effects.PickupSparksFx;
import com.jlogicsoftware.kimeria.effects.PortalFx;
import com.jlogicsoftware.kimeria.effects.RoomRippleFx;
import com.jlogicsoftware.kimeria.effects.KimeriaBloodSystem;
import com.jlogicsoftware.kimeria.effects.ScreenShake;
import com.jlogicsoftware.kimeria.effects.Firefly;
import com.jlogicsoftware.kimeria.effects.LeafFx;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.ui.FairyGuide;
import com.jlogicsoftware.kimeria.ui.TouchCoach;
import com.jlogicsoftware.kimeria.physics.CollisionUtils;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.WorldObject;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.FlyingBeetle;
import com.jlogicsoftware.kimeria.entity.enemy.MosquitoSwarm;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.enemy.Remains;
import com.jlogicsoftware.kimeria.entity.enemy.YellowMob;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.items.Actionable;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.objects.DeadfallTrap;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.items.Wall;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.objects.FallingBranch;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.objects.Bramble;
import com.jlogicsoftware.kimeria.entity.objects.LethalHazard;
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.LanceRoot;
import com.jlogicsoftware.kimeria.entity.objects.LegHold;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.entity.objects.HoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.ThornVine;
import com.jlogicsoftware.kimeria.entity.objects.Decor;
import com.jlogicsoftware.kimeria.entity.objects.HangingVine;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
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
    /** The scenery layers behind the play layer and the room's foreground in front of the world (#236); empty for a map without them. */
    private final ParallaxRenderer parallax;
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
    /** The static one-way platforms of the current room as drawn (their colliders are in {@link #collisionBlocks}). */
    public List<StaticPlatform> staticPlatforms() { return java.util.Collections.unmodifiableList(staticPlatforms); }
    /** Everything else: enemies, collectables, bombs, checkpoints, torches, fireflies. */
    public final List<WorldObject> objects = new ArrayList<>();
    /** Cosmetic scenery (#239): drawn right after the background, behind platforms, objects and the hero; no collision. */
    public final List<Decor> decor = new ArrayList<>();
    /** Scenery vines (#239 step 5): drawn with {@link #decor}, swaying when a falling stone lands within reach. */
    public final List<HangingVine> vines = new ArrayList<>();
    private final Rectangle heroBodyScratch = new Rectangle(), actorBodyScratch = new Rectangle(), hintBodyScratch = new Rectangle(), lethalBodyScratch = new Rectangle(),
        exitBodyScratch = new Rectangle(), trapHeroScratch = new Rectangle();
    /** The targets one lever pull has already performed, so a target listed twice acts once (reused, never allocated per pull). */
    private static final int ACTIVATED_CAPACITY = 8;
    private final Array<Actionable> activatedScratch = new Array<>(false, ACTIVATED_CAPACITY);
    /** A hive's frame and band column, tested against the camera view for its sounds (D90, `hiveInView`); reused, never allocated per frame. */
    private final Rectangle hiveViewScratch = new Rectangle();
    /** A fissure's frame and column, tested against the camera view for its sounds (D91); reused, never allocated per frame. */
    private final Rectangle fissureViewScratch = new Rectangle();
    /** Each ember fissure as checked at load, by object id: a spawn builds from it instead of parsing and checking again (D91). */
    private final Map<Integer, EmberFissure> checkedFissures = new HashMap<>();
    /** The deadfalls' sounds and bark chips (D88, D75): one listener for every deadfall, built once. */
    private final DeadfallTrap.Listener deadfallListener = new DeadfallTrap.Listener() {
        @Override public void tripped(DeadfallTrap trap) { if (game.playSounds()) game.playSound(DEADFALL_CREAK_SOUND); }
        @Override public void caught(DeadfallTrap trap) {
            fx.barkChips(trap.snagX(), trap.snagY());
            if (game.playSounds()) game.playSound(DEADFALL_KNOCK_SOUND);
        }
    };
    /** The hero's body for the honey fungi's zone test (D63): one box refilled on every ask, so no frame allocates. */
    private final Rectangle fungusHeroScratch = new Rectangle();
    /**
     * Objects spawned mid-frame (a coin's pickup ripple, a bomb's explosion,
     * ...) land here instead of straight into {@link #objects}: they're
     * typically spawned from inside {@code Player}'s own iteration over
     * {@code objects} (checking overlaps), and adding to a list while
     * iterating it throws {@code ConcurrentModificationException}. Drained
     * into {@code objects} once per frame, after that iteration is over.
     */
    private final List<WorldObject> pendingSpawns = new ArrayList<>();
    /**
     * Capacity of {@link #blood}: the boss cannot be hit again for HURT + WARNING (1.35 s), longer than a drop lives (.9 s),
     * so at most one burst of 48 drops is alive; twice that is the headroom (KimeriaBloodSystemCapacityTest measures the peak).
     */
    public static final int BLOOD_CAPACITY = 96;
    /** The map's {@code background} when it sets none (D92): only test maps fall back here; every shipped map sets it (ShippedBackgroundTest, check_contrast.py). */
    public static final String DEFAULT_BACKGROUND = "#4a92b8";
    /** Forest firefly light: the menu backdrop's lime (#472). Not a reserved hue (aqua, warm white, fire orange). */
    private static final Color FIREFLY_LIGHT = Color.valueOf("b8f23a");
    private static final int RGB = 3, HEX_RGB = 6, HEX_ARGB = 8, OPAQUE = 0xff, BYTE = 0xff, BITS_PER_CHANNEL = 8, HEX_RADIX = 16;
    private static final long BLOOD_SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final long FX_SEED_MIX = 0xC2B2AE3D27D4EB4FL;
    /** All blood of the level: no object is queued per hit. Cleared with the room. */
    private final KimeriaBloodSystem blood = new KimeriaBloodSystem(BLOOD_CAPACITY, TimeUtils.nanoTime() ^ BLOOD_SEED_MIX);
    private static final long FIREBALL_FX_SEED_MIX = 0x94D049BB133111EBL;
    /** The fireballs' trails and hits (#397): one for the level, like the blood. Cleared with the room. */
    private final FireballFx fireballFx = new FireballFx(TimeUtils.nanoTime() ^ FIREBALL_FX_SEED_MIX);
    private static final long PICKUP_SPARKS_SEED_MIX = 0xD6E8FEB86659FD93L;
    /** The spark burst of every pickup (#402): one for the level. Cleared with the room, so a pickup that returns after a death replays nothing. */
    private final PickupSparksFx pickupSparks = new PickupSparksFx(TimeUtils.nanoTime() ^ PICKUP_SPARKS_SEED_MIX);
    /** The portal on the current room's exit (#552) and the ripple of a room change (#553): cosmetic, owned here like the other effects. */
    private final PortalFx portal = new PortalFx();
    private final RoomRippleFx roomRipple = new RoomRippleFx();
    /**
     * A room change that {@link #beginLeaving} started is under way, and how long it has run; only that sets {@link #heroLeft}, so the fade-in of the
     * next room, a boss fight's fade and the debug fade, which never begin a leave, cannot hide the hero.
     */
    private boolean leaving;
    private float leaveClock;
    /** True from the middle of the leaving pause until the next room starts: the hero has gone into the portal (drawing only). */
    private boolean heroLeft;
    private static final long BEETLE_FX_SEED_MIX = 0xBF58476D1CE4E5B9L;
    /** The beetles' wind-up specks, bursts and deaths (#327, D100): one for the level, like the blood. Cleared with the room. */
    private final BeetleFx beetleFx = new BeetleFx(TimeUtils.nanoTime() ^ BEETLE_FX_SEED_MIX);
    private static final long SPIDER_FX_SEED_MIX = 0x94D049BB133111EBL, REMAINS_FX_SEED_MIX = 0x2545F4914F6CDD1DL;
    /** Objects that move or hang, which a spider's thread must not pass through (D103 load check). */
    private static final Set<String> SPIDER_BLOCKER_TYPES = Set.of("Actionable", "Escalator", "FallingPlatform", "FallingBranch",
        "HangingVine", "ThornVine", "HangingHive", "Deadfall");
    /** The spiders' silk glints, leap line, bites, drops and deaths (#328, D102, D103): one for the level. Cleared with the room. */
    private final SpiderFx spiderFx = new SpiderFx(TimeUtils.nanoTime() ^ SPIDER_FX_SEED_MIX);
    private static final long SNAIL_FX_SEED_MIX = 0xD1B54A32D192ED03L;
    /** The snails' wind-up specks, gob drips and splats and deaths (#329, D106): one for the level. Cleared with the room. */
    private final SnailFx snailFx = new SnailFx(TimeUtils.nanoTime() ^ SNAIL_FX_SEED_MIX);
    private static final long WOLF_FX_SEED_MIX = 0xFF51AFD7ED558CCDL;
    /** The wolves' wind-up breath, skid and bite dust, jaw glints and deaths (#331, D115): one for the level. Cleared with the room. */
    private final WolfFx wolfFx = new WolfFx(TimeUtils.nanoTime() ^ WOLF_FX_SEED_MIX);
    /** Every monster body's crumble and burn (D101, #478): one for the level. Cleared with the room. */
    private final RemainsFx remainsFx = new RemainsFx(TimeUtils.nanoTime() ^ REMAINS_FX_SEED_MIX);

    private final Assets assets;
    private final GameContext game;
    /** The camera's visible world rect; the bramble's cues are heard only while it overlaps it (D89). */
    private final Supplier<Rectangle> visibleWorldRect;
    /**
     * The flat colour behind the world (D92): the map property {@code background}, a Tiled colour ({@code #RRGGBB} or
     * {@code #AARRGGBB}, opaque). Read once at load; {@link #backgroundRgb()} hands out this array, never a copy.
     */
    private final float[] background = new float[RGB];
    public final RoomLayout roomLayout;
    private int currentRoom;
    /**
     * A map-authored hint zone (object type {@code Hint} in the {@code Hints} layer). The hero's body
     * inside the zone shows {@code message} (a {@link Msg} name) and/or, when {@code controls} is
     * set, the contextual pickup/lever/ladder instructions. Zones are stateless: they apply again
     * on every retry and return. A level without zones never shows tutorial hints.
     *
     * <p>{@code touchMessage} (a {@link Msg} name, optional) replaces {@code message} while the player is guided by touch
     * ({@code TouchControls.touchGuided}, D139); {@code coach} (optional) names the touch tutorial step this zone arms
     * ({@link TouchCoach.Step}, D139). Both default to null.
     */
    public record HintZone(String id, Rectangle bounds, Msg message, boolean controls, Msg touchMessage, TouchCoach.Step coach) { }
    private final List<HintZone> hintZones = new ArrayList<>();
    public List<HintZone> hintZones() { return List.copyOf(hintZones); }

    private static boolean insideZone(HintZone zone, Rectangle body) {
        return zone.bounds.contains(body.x + body.width / 2, body.y + body.height / 2);
    }

    /** True while the hero stands in a zone that teaches the contextual controls. */
    public boolean controlHintsActive() {
        Rectangle body = player.hitboxBoundsInto(hintBodyScratch);
        for (int i = 0; i < hintZones.size(); i++) if (hintZones.get(i).controls && insideZone(hintZones.get(i), body)) return true;
        return false;
    }

    /** The authored message zone holding the hero, or null. */
    public HintZone messageHint() {
        Rectangle body = player.hitboxBoundsInto(hintBodyScratch);
        for (int i = 0; i < hintZones.size(); i++) {
            HintZone zone = hintZones.get(i);
            if (zone.message != null && insideZone(zone, body)) return zone;
        }
        return null;
    }

    /** Sets the bit of every touch coach step whose zone holds the hero (D139); allocation free, so the touch controls can ask every frame. */
    public void coachZones(TouchCoach.Signals out) {
        Rectangle body = player.hitboxBoundsInto(hintBodyScratch);
        for (int i = 0; i < hintZones.size(); i++) {
            HintZone zone = hintZones.get(i);
            if (zone.coach != null && insideZone(zone, body)) out.zone(zone.coach);
        }
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
            String coach = o.prop("coach", ""), touchMessage = o.prop("touchMessage", "");
            if (message.isEmpty() && !controls && coach.isEmpty())
                throw new IllegalArgumentException("Hint needs a message, controls or a coach step: " + describe(o));
            if (!touchMessage.isEmpty() && message.isEmpty())
                throw new IllegalArgumentException("Hint touchMessage needs a message to replace: " + describe(o));
            Msg msg = message.isEmpty() ? null : Msg.valueOf(message); // A mistyped key fails loudly.
            Msg touchMsg = touchMessage.isEmpty() ? null : Msg.valueOf(touchMessage);
            hintZones.add(new HintZone(o.name, zone, msg, controls, touchMsg, coach.isEmpty() ? null : TouchCoach.Step.parse(coach)));
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
        EscalatorState, FallingPlatformState, LadderState, ExplosiveState { }
    private record CarryableState(Carryable.Snapshot value) implements ObjectSnapshot { }
    private record TorchState(float x, float y, int intensity, boolean burning,
                              boolean removed) implements ObjectSnapshot { }
    private record WallState(boolean active, boolean removed, float y, boolean raised) implements ObjectSnapshot { }
    private record EscalatorState(Escalator.Snapshot value) implements ObjectSnapshot { }
    private record FallingPlatformState(FallingPlatform.Snapshot value) implements ObjectSnapshot { }
    private record LadderState(Ladder.Snapshot value) implements ObjectSnapshot { }
    private record ExplosiveState(ExplosiveTrap.Snapshot value) implements ObjectSnapshot { }

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
    /** Seconds the looping wall rumble still has to run (0 when it is not playing). */
    private float rumbleSeconds;
    private boolean rumbling;
    private static final String WALL_RUMBLE_SOUND = "wall_rumble", GRIT_FALL_SOUND = "grit_fall", STONE_IMPACT_SOUND = "stone_impact", BRANCH_IMPACT_SOUND = "branch_impact";
    private static final String DEADFALL_CREAK_SOUND = "deadfall_creak", DEADFALL_KNOCK_SOUND = "deadfall_knock";
    /** The bramble's warning rustle and its grow lash (#364, D89). */
    static final String BRAMBLE_RUSTLE_SOUND = "bramble_rustle", BRAMBLE_LASH_SOUND = "bramble_lash";
    /** The spore puff (#445): both honey fungi's burst (D63: one sound for the pair). */
    private static final String SPORE_PUFF_SOUND = "spore_puff";
    /** The puffball's release (#558): its own, harsher sound; it is no look-alike of the honey fungi. */
    static final String PUFFBALL_BURST_SOUND = "puffball_burst";
    /** The thorn root's and tuft's spring (#446): one sound for the family. */
    private static final String THORN_SPRING_SOUND = "thorn_spring";
    /** The hanging hive's sounds (#365): the warning buzz, the stinger patter of a release, the knock-down. */
    private static final String HIVE_BUZZ_SOUND = "hive_buzz", STINGER_RAIN_SOUND = "stinger_rain", HIVE_FALL_SOUND = "hive_fall";
    /** The ember fissure's sounds (#366): the warning rumble and the jet's roar. */
    static final String FISSURE_RUMBLE_SOUND = "fissure_rumble", EMBER_JET_SOUND = "ember_jet";
    /** The lance root's sounds (#562, D129): the warning creak and the thrust. */
    static final String ROOT_CREAK_SOUND = "root_creak", ROOT_THRUST_SOUND = "root_thrust";
    /** The leg-hold's sounds (#561, D128): the warning rattle and the snap. */
    static final String LEGHOLD_RATTLE_SOUND = "leghold_rattle", LEGHOLD_SNAP_SOUND = "leghold_snap";
    /** The fireball going out on terrain or at the end of its life (#397); the burn-out plays it quieter. */
    static final String FIREBALL_FIZZLE_SOUND = "fireball_fizzle";
    private static final float BURN_OUT_VOLUME = .6f;
    private final EnvironmentFx fx;
    private final LeafFx leafFx = new LeafFx();
    public LeafFx leafFx() { return leafFx; }
    /** D126: nothing the hero picks up or operates stands within this gap of a ladder zone (px), so Up never means two things. */
    public static final float LADDER_CLEARANCE = 32f;

    /**
     * Up picks up and uses, and also climbs (D126), so no hand-operated object (a lever that is not thrown at, a stone
     * or a carried torch) may stand within {@link #LADDER_CLEARANCE} of a ladder's zone, the union of its raised and
     * lowered spans. Mirrors {@code check_ladder_clearance} in {@code tools/check_tiled_links.py}.
     */
    private void validateLadderClearance() {
        for (TiledObject ladder : map.layer("SpawnPoints")) {
            if (!"Ladder".equals(ladder.type)) continue;
            float bottom = ladder.y + ladder.height + ladder.propFloat("dropDistance", 0);
            for (TiledObject o : map.layer("SpawnPoints")) {
                boolean hand = "Stone".equals(o.type) || "CarryableTorch".equals(o.type)
                    || "Trigger".equals(o.type) && !o.propBool("throwActivated", false);
                if (!hand) continue;
                float gapX = Math.max(Math.max(ladder.x - (o.x + o.width), o.x - (ladder.x + ladder.width)), 0);
                float gapY = Math.max(Math.max(ladder.y - (o.y + o.height), o.y - bottom), 0);
                if (Math.hypot(gapX, gapY) < LADDER_CLEARANCE)
                    throw new IllegalArgumentException(o.type + " " + describe(o) + " stands within " + (int) LADDER_CLEARANCE
                        + " px of ladder " + describe(ladder) + " (D126)");
            }
        }
    }

    /** Objects whose column or cloud must stay clear of falling leaves (#473): the traps. Package-private: {@code LeafRoomsTest} checks it covers every trap of the catalogue (docs/TRAPS.md). */
    static final java.util.Set<String> LEAF_AVOIDED = java.util.Set.of("FallingBranch", "FallingStone", "Deadfall",
        "HangingHive", "ThornVine", "Bramble", "EmberFissure", "LanceRoot", "LegHold", "ThornRoot", "ThornTuft", "ExplosiveStump", "ExplosiveRoot",
        "FalseHoneyFungus", "HoneyFungus", "FallingPlatform", "Puffball", "Bomb");
    private static final float LEAF_AVOID_MARGIN_PX = 24;
    private void startLeaves() {
        RoomLayout.Room room = roomLayout.rooms.get(currentRoom);
        if (room.leaves() <= 0) { leafFx.start(room.bounds(), 0); return; }
        com.badlogic.gdx.utils.FloatArray avoid = new com.badlogic.gdx.utils.FloatArray();
        for (java.util.List<TiledObject> group : map.objectGroups.values()) for (TiledObject o : group) {
            if (!LEAF_AVOIDED.contains(o.type) || roomLayout.owner(o) != currentRoom) continue;
            avoid.add(o.x - LEAF_AVOID_MARGIN_PX); avoid.add(o.x + o.width + LEAF_AVOID_MARGIN_PX);
        }
        leafFx.start(room.bounds(), room.leaves(), avoid.toArray());
    }
    private final FairyGuide fairy;

    /** The environment effects of this level (screen shake, jump dust, ceiling grit). */
    public EnvironmentFx fx() { return fx; }

    /** The fairy helper (D82): shows the interaction target the hero's Up will affect. */
    public FairyGuide fairy() { return fairy; }

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
        placeVictoryPortal();
        game.setNormalTime();
    }

    public void shakeAfterBossAttack() { if (!victory) fx.shake().start(ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.BOSS_SECONDS); }
    public void shakeAfterEmpoweredHit() { fx.shake().start(ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.BOSS_SECONDS); }
    /**
     * The jolt of a blast the hero survived, at any distance (#448, D156). Call it after the blast's kill test: a lethal
     * one starts none, because {@link #playerDied} has cleared the camera and the death beat is its feedback.
     */
    public void shakeAfterBlast(float amplitudePx, float seconds) { if (!player.isGotHit()) fx.shake().start(amplitudePx, seconds); }
    public void clearCameraEffects() { fx.clear(); stopRumble(); }

    private void stopRumble() {
        if (!rumbling) return;
        rumbling = false;
        rumbleSeconds = 0;
        game.stopSound(WALL_RUMBLE_SOUND);
    }

    /**
     * Visual displacement only; narrow rooms keep their existing centred framing.
     *
     * <p>The returned vector is a field of this level and is overwritten by the next call: read {@code x} and
     * {@code y} at once and do not store it (copy it with {@code new Vector2(offset)} outside per-frame code if
     * it must be kept). It is not safe to mutate.
     */
    public Vector2 cameraShakeOffset(Rectangle view) { return fx.shake().offset(currentRoomBounds(), view); }

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
        return !victory && exit != null && player.canLeaveRoom() && exit.overlaps(player.hitboxBoundsInto(exitBodyScratch));
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
        roomRipple.enter(player.centerX(), player.centerY());
    }

    /** Dev-only map reload: keep a valid position and activate its room without a transition. */
    public void reloadAt(Vector2 savedPosition) {
        for (int i = 0; i < roomLayout.rooms.size(); i++) {
            if (roomLayout.rooms.get(i).bounds().contains(savedPosition.x + player.size.x / 2,
                                                        savedPosition.y + player.size.y / 2)) {
                currentRoom = i;
                resetCurrentRoom(player);
                player.position.set(savedPosition);
                player.resetCamera();
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
        objects.clear(); decor.clear(); vines.clear(); collisionBlocks.clear(); escalators.clear(); triggers.clear(); ladders.clear();
        fallingPlatforms.clear(); staticPlatforms.clear();
        pendingSpawns.clear(); pendingPickups.clear(); blood.clear(); fireballFx.clear(); pickupSparks.clear(); beetleFx.clear(); spiderFx.clear(); snailFx.clear(); wolfFx.clear(); remainsFx.clear();
        startLeaves();
        boss = null;
        roomEnemies.clear(); roomTraps.clear(); roomStateOwners.clear();
        victory = completionSent = false;
        clearCameraEffects();
        fairy.reset(); // the marks belong to the room that was left (D87)
        player = nextPlayer;
        heroLeft = leaving = false;
        leaveClock = 0;
        roomRipple.clear();
        spawnObjects();
        addCollisions();
        restoreRoomObjects();
        placePlayer();
        placePortal();
        if (boss != null && retryBossEnergy >= 0) boss.showRestoration(retryBossEnergy);
        retryBossEnergy = -1;
    }

    private static final float VICTORY_PORTAL_WIDTH = 24, VICTORY_PORTAL_HEIGHT = 48, VICTORY_PORTAL_MARGIN = 16;
    /** How far above the hero's feet a block's top may sit and still be the floor under him: rounding only. */
    private static final float VICTORY_FLOOR_SLACK = .5f;

    private void placePortal() {
        Rectangle exit = roomLayout.rooms.get(currentRoom).exit();
        if (exit != null) { portal.place(exit.x, exit.y, exit.width, exit.height); return; }
        portal.clear();
        // The last room of a level without a boss ends it on its Checkpoint: the amber portal marks it (#605). A boss level shows it when the boss is dead.
        for (int i = 0; i < objects.size(); i++)
            if (objects.get(i) instanceof Checkpoint cp) { portal.placeLevelEnd(cp.position.x, cp.position.y, cp.size.x, cp.size.y); return; }
    }

    /** The boss is dead: the amber portal opens at the right end of the room, where the victory walk leaves, on the floor there (#605). */
    private void placeVictoryPortal() {
        Rectangle room = currentRoomBounds();
        float x = room.x + room.width - VICTORY_PORTAL_WIDTH - VICTORY_PORTAL_MARGIN;
        float floor = floorBelow(x, VICTORY_PORTAL_WIDTH, player.position.y + player.hitbox.offsetY + player.hitbox.height);
        player.setVictoryFloor(floor);
        portal.placeLevelEnd(x, floor - VICTORY_PORTAL_HEIGHT, VICTORY_PORTAL_WIDTH, VICTORY_PORTAL_HEIGHT);
    }

    /** The top of the highest solid block under the span {@code x} to {@code x + width} at or below {@code from} (larger Y is lower); {@code from} if there is none. */
    private float floorBelow(float x, float width, float from) {
        float floor = Float.MAX_VALUE;
        for (int i = 0; i < collisionBlocks.size(); i++) {
            CollisionBlock block = collisionBlocks.get(i);
            if (!block.isActive() || block.isPlatform() || block.isQuickSand()) continue;
            if (block.getX() >= x + width || block.getX() + block.getWidth() <= x) continue;
            if (block.getY() >= from - VICTORY_FLOOR_SLACK) floor = Math.min(floor, block.getY());
        }
        return floor == Float.MAX_VALUE ? from : floor;
    }

    /** The portal of the current room, for tests: violet on a room exit, amber where the level ends (a boss level shows it only after the kill). */
    public PortalFx portal() { return portal; }
    public RoomRippleFx roomRipple() { return roomRipple; }
    public boolean heroLeft() { return heroLeft; }

    /** The hero reaches the exit: the ripple plays where he stands and he goes into the portal when the pause has half run (see {@link #updateRoomChange}). */
    public void beginLeaving() {
        roomRipple.leave(player.centerX(), player.centerY());
        leaving = true;
        heroLeft = false;
        leaveClock = 0;
    }

    /** The room change was cancelled (the healer meeting): the hero is back and the ripple ends. */
    public void cancelLeaving() { heroLeft = leaving = false; leaveClock = 0; roomRipple.clear(); }

    /** Advances only the effects of a room change while the rest of the world is held: the portal and the ripple, and, in a leave, the moment the hero is gone. */
    public void updateRoomChange(float dt) {
        if (leaving) {
            leaveClock += dt;
            if (leaveClock >= RoomRippleFx.LEAVE_HOLD_SECONDS / 2f) heroLeft = true;
        }
        portal.update(dt);
        roomRipple.update(dt);
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

    /** Sprays blood from a landed hit (see {@link KimeriaBloodSystem#spray}); it joins the very next update, like a queued object. */
    public void blood(float x, float y, float direction, float floorY, boolean empowered) {
        blood.spray(x, y, direction, floorY, empowered);
    }

    /** Sprays the light blood of a monster killed by the sword (#319); fireballs and other killers never call this. */
    public void mobBlood(float x, float y, float direction, float floorY) {
        blood.sprayLight(x, y, direction, floorY);
    }

    /** The level's blood, for tests. */
    public KimeriaBloodSystem bloodParticles() { return blood; }

    /** The fireballs' trail and hit particles (#397); the fireball writes into them, tests read them. */
    public FireballFx fireballFx() { return fireballFx; }
    /** The spark burst and ring of every pickup (#402); {@code Collectable}'s callback fires it, tests read it. */
    public PickupSparksFx pickupSparks() { return pickupSparks; }
    /** The beetles' specks, bursts and deaths (#327); {@code Beetle} writes into them, tests read them. */
    public BeetleFx beetleFx() { return beetleFx; }
    /** The spiders' silk, leap, bite and death effects (#328); {@code Spider} writes into them, tests read them. */
    public SpiderFx spiderFx() { return spiderFx; }
    /** The snails' specks, gob drips and splats and deaths (#329, D106); {@code Snail} writes into them, tests read them. */
    public SnailFx snailFx() { return snailFx; }
    /** The wolves' breath, dust, jaw glints and deaths (#331, D115); {@code Wolf} writes into them, tests read them. */
    public WolfFx wolfFx() { return wolfFx; }
    /** Every monster body's crumble and burn (D101, #478); the bodies write into them, tests read them. */
    public RemainsFx remainsFx() { return remainsFx; }

    /** The fireball's fizzle (#397): a hit on terrain at full volume, a burn-out at the end of its life quieter. */
    public void fireballFizzle(boolean burnOut) {
        if (game.playSounds()) game.playSound(FIREBALL_FIZZLE_SOUND, burnOut ? BURN_OUT_VOLUME : 1);
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
        fx = new EnvironmentFx(() -> !game.screenShakeDisabled(), TimeUtils.nanoTime() ^ FX_SEED_MIX);
        fairy = new FairyGuide(assets);
        ParticleTextures.prepare(); // the shared particle textures, once at load, never on the first frame that draws one (#483)
        fairy.setView(visibleWorldRect);
        this.visibleWorldRect = visibleWorldRect;
        this.player = player;
        this.name = levelName;
        this.entryCoins = game.coinsCollected();
        this.map = TiledMapData.load("tiles/" + levelName + ".tmx", assets);
        this.parallax = new ParallaxRenderer(map);

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
            if (FallingTrapLayout.isFallingTrap(o.type)) FallingTrapLayout.validate(o, roomLayout, owner, map, game.logicalResolution().y); // D45: every room, at load.
            if ("ThornRoot".equals(o.type) || "ThornTuft".equals(o.type)) validateThorn(o, owner);
            if (ExplosiveTrap.isExplosive(o.type)) validateExplosive(o, owner); // D47: every room, at load.
            if ("HoneyFungus".equals(o.type) || "FalseHoneyFungus".equals(o.type)) validateFungus(o, owner); // D63: every room, at load.
            if ("FallingPlatform".equals(o.type) && "branch".equals(o.prop("skin", ""))) validateBranch(o, owner); // D80: every room, at load.
            if ("Deadfall".equals(o.type)) validateDeadfall(o, owner); // D88: every room, at load.
            if ("Bramble".equals(o.type)) validateBramble(o, owner); // D89: every room, at load.
            if ("HangingHive".equals(o.type)) hiveFor(o, owner); // D90: every room, at load.
            if ("EmberFissure".equals(o.type) || "LanceRoot".equals(o.type)) checkedFissures.put(o.id, fissureFor(o, owner)); // D91, D129: every room, at load.
            if ("LegHold".equals(o.type)) legHoldFor(o, owner); // D128: every room, at load.
            if ("Beetle".equals(o.type)) validateBeetle(o, owner); // D100: every room, at load.
            if ("Spider".equals(o.type)) validateSpider(o, owner); // D102: every room, at load.
            if ("Snail".equals(o.type)) validateSnail(o, owner); // D106: every room, at load.
            if ("Wolf".equals(o.type)) validateWolf(o, owner); // D115: every room, at load.
            if ("FlyingBeetle".equals(o.type)) validateFlyingBeetle(o, owner); // D145: every room, at load.
            if ("MosquitoSwarm".equals(o.type)) validateSwarm(o, owner); // D151: every room, at load.
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
        validateLadderClearance(); // D126: every room, at load.
        loadHintZones();
        for (TiledObject o : map.layer("Collisions")) roomLayout.owner(o);
        for (TiledObject o : map.layer("SpawnPoints")) {
            if ("Collectable".equals(o.type) && ItemType.fromMapName(o.name) == ItemType.COIN)
                authoredCoins.add(o.id);
        }
        spawnObjects();
        addCollisions();

        startLeaves();
        for (int i = 0, fireflies = map.propInt("fireflies", 0); i < fireflies; i++) {
            ambience.add(Firefly.smallGlow(new Vector2(map.widthPx(), map.heightPx()), FIREFLY_LIGHT));
        }
        // The healer recipe's artifacts are level configuration read from the map, e.g. "BAT,MUSHROOM".
        // A mistyped name throws IllegalArgumentException from ItemType.fromMapName: failing loudly is intended.
        Set<ItemType> ingredients = EnumSet.noneOf(ItemType.class);
        for (String token : map.prop("ingredients", "").split(",")) {
            if (!token.isBlank()) ingredients.add(ItemType.fromMapName(token.trim()));
        }
        game.artifacts().requireForLevel(name, ingredients);
        parseBackground(map.prop("background", DEFAULT_BACKGROUND), background);

        placePlayer();
        placePortal();
    }

    /** The flat colour behind the world as RGB in 0..1 (D92); the level's own array, read every frame, never modified. */
    public float[] backgroundRgb() { return background; }

    /**
     * Parses a Tiled colour property into {@code rgb}: {@code #RRGGBB}, or {@code #AARRGGBB} with alpha ff. Anything else
     * throws IllegalArgumentException: a mistyped colour must fail the load, never fall back to a default.
     */
    public static void parseBackground(String value, float[] rgb) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != HEX_RGB && hex.length() != HEX_ARGB) throw new IllegalArgumentException("background: expected #RRGGBB or #AARRGGBB, got '" + value + "'");
        long argb = 0;
        for (int i = 0; i < hex.length(); i++) { // digit by digit: Long.parseLong would accept a sign ("#-4a92b")
            int digit = Character.digit(hex.charAt(i), HEX_RADIX);
            if (digit < 0) throw new IllegalArgumentException("background: not a hex colour: '" + value + "'");
            argb = argb * HEX_RADIX + digit;
        }
        if (hex.length() == HEX_ARGB && (argb >>> (RGB * BITS_PER_CHANNEL)) != OPAQUE)
            throw new IllegalArgumentException("background: must be opaque (alpha ff), got '" + value + "'");
        for (int i = 0; i < RGB; i++) rgb[i] = ((argb >>> ((RGB - 1 - i) * BITS_PER_CHANNEL)) & BYTE) / (float) BYTE;
    }

    public boolean healerAhead() {
        return map.propBool("healerBeforeBoss", false) && !fightCommitted && map.layer("SpawnPoints").stream()
            .anyMatch(o -> "Boss".equals(o.type) && roomLayout.owner(o) == currentRoom + 1);
    }

    /** True when the player's body has entered a map-authored out-of-play volume. */
    public boolean touchesDeadline(Rectangle playerBounds) {
        for (int i = 0, n = deadlines.size(); i < n; i++) if (playerBounds.overlaps(deadlines.get(i))) return true;
        return false;
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

    /** Package-private for the tests that secure a room the way a forward exit does. */
    void secureVisit() {
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
        if (owner instanceof ExplosiveTrap trap) return new ExplosiveState(trap.snapshot());
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
            } else if (owner instanceof ExplosiveTrap trap && snapshot instanceof ExplosiveState state) {
                trap.restore(state.value());
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
                    bat.setLevel(this);
                    objects.add(bat);
                    roomEnemies.put(o.id, bat);
                }
                case "Checkpoint" -> objects.add(new Checkpoint(o.x, o.y, o.width, o.height));
                case "Beetle" -> {
                    Beetle beetle = new Beetle(assets, game, o.x, o.y, o.width, o.height,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    beetle.setPlayer(player);
                    beetle.setLevel(this);
                    objects.add(beetle);
                    roomEnemies.put(o.id, beetle);
                }
                case "FlyingBeetle" -> {
                    FlyingBeetle beetle = new FlyingBeetle(assets, game, o.x, o.y, o.width, o.height,
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    beetle.setPlayer(player);
                    beetle.setLevel(this);
                    beetle.flyOver(monsterFloors());
                    objects.add(beetle);
                    roomEnemies.put(o.id, beetle);
                }
                case "MosquitoSwarm" -> {
                    MosquitoSwarm swarm = new MosquitoSwarm(assets, game, o.x, o.y, o.propBool("isVertical", false),
                        o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    swarm.setPlayer(player);
                    swarm.setLevel(this);
                    objects.add(swarm);
                    roomEnemies.put(o.id, swarm);
                }
                case "Spider" -> {
                    Spider spider = o.polyline != null ? new Spider(assets, game, Spider.Route.trace(o.polyline, spiderSolids()))
                        : new Spider(assets, game, o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    spider.setPlayer(player);
                    spider.setLevel(this);
                    objects.add(spider);
                    roomEnemies.put(o.id, spider);
                }
                case "Snail" -> {
                    Snail snail = new Snail(assets, game, o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    snail.setPlayer(player);
                    snail.setLevel(this);
                    objects.add(snail);
                    roomEnemies.put(o.id, snail);
                }
                case "Wolf" -> {
                    Wolf wolf = new Wolf(assets, game, o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0));
                    wolf.setPlayer(player);
                    wolf.setLevel(this);
                    objects.add(wolf);
                    roomEnemies.put(o.id, wolf);
                }
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
                        if (blast.overlaps(player.hitboxBoundsInto(trapHeroScratch))) player.collidedWithActor(false);
                        shakeAfterBlast(ScreenShake.BLAST_AMPLITUDE_PX, ScreenShake.BLAST_SECONDS);
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
                    FallingPlatform platform = new FallingPlatform(assets, o.x, o.y, o.width, o.height, this::queueSpawn, game.trapWarnings(),
                        FallingPlatform.Skin.fromMap(o.prop("skin", "")), o.propBool("mirror", false));
                    fallingPlatforms.add(platform);
                    registerRoomStateOwner(o.id, platform);
                }
                case "FallingBranch", "FallingStone" -> spawnFallingTrap(o);
                case "ThornRoot", "ThornTuft" -> {
                    ThornTrap thorn = ThornTrap.fromMap(assets, game.trapWarnings(), o,
                        () -> player.canLeaveRoom() ? player.hitboxBoundsInto(trapHeroScratch) : null, () -> player.collidedWithActor(false));
                    thorn.onSpring(() -> thornSprung(thorn));
                    objects.add(thorn);
                }
                case "ExplosiveStump", "ExplosiveRoot" -> {
                    ExplosiveTrap trap = ExplosiveTrap.fromMap(assets, game.trapWarnings(), o,
                        () -> player.canLeaveRoom() ? player.hitboxBoundsInto(trapHeroScratch) : null, () -> player.collidedWithActor(false),
                        this::explosiveBlast);
                    objects.add(trap);
                    registerRoomStateOwner(o.id, trap);
                }
                case "Deadfall" -> objects.add(DeadfallTrap.fromMap(assets, game.trapWarnings(), o,
                    () -> player.canLeaveRoom() ? player.hitboxBoundsInto(trapHeroScratch) : null, () -> player.collidedWithActor(false),
                    deadfallListener));
                case "Puffball" -> {
                    Puffball puffball = Puffball.fromMap(assets, o, game.trapWarnings(), describe(o));
                    requireSafeSetting(o, puffball, puffball.cloudBounds());
                    puffball.onRelease(() -> puffballSound(puffball));
                    objects.add(puffball);
                }
                case "Bramble" -> {
                    Bramble bramble = Bramble.fromMap(assets, o, game.trapWarnings(), describe(o)); // placement checked at load (D89)
                    bramble.onCues(() -> brambleSound(bramble, BRAMBLE_RUSTLE_SOUND), () -> brambleSound(bramble, BRAMBLE_LASH_SOUND));
                    objects.add(bramble);
                }
                case "HangingHive" -> spawnHive(o);
                case "EmberFissure", "LanceRoot" -> spawnFissure(o);
                case "LegHold" -> {
                    LegHold legHold = legHoldFor(o, currentRoom);
                    legHold.onWarning(() -> legHoldSound(legHold, LEGHOLD_RATTLE_SOUND));
                    legHold.onSnap(() -> legHoldSound(legHold, LEGHOLD_SNAP_SOUND));
                    objects.add(legHold);
                }
                case "HoneyFungus" -> {
                    HoneyFungus fungus = HoneyFungus.fromMap(assets, game.trapWarnings(), o, this::fungusHero);
                    fungus.onBurst(this::sporePuff);
                    objects.add(fungus);
                }
                case "FalseHoneyFungus" -> {
                    FalseHoneyFungus fungus = FalseHoneyFungus.fromMap(assets, game.trapWarnings(), o, this::fungusHero);
                    requireSafeSetting(o, fungus, fungus.cloudBounds());
                    fungus.onBurst(this::sporePuff);
                    objects.add(fungus);
                }
                case "Decor" -> decor.add(Decor.fromMap(assets, o, describe(o)));
                case "HangingVine" -> vines.add(HangingVine.fromMap(assets, o, describe(o)));
                case "ThornVine" -> {
                    ThornVine vine = ThornVine.fromMap(assets, o, describe(o));
                    float reach = vine.length() + ThornVine.LETHAL_HALF_WIDTH;
                    requireSafeSetting(o, vine, new Rectangle(vine.anchorX() - reach, vine.anchorY(), 2 * reach, reach));
                    objects.add(vine);
                }
                default -> {
                }
            }
        }
        spawnLevers();
    }

    /** D89: the bramble's root is against a solid face over its band, and the tip end has floor and room to stand beyond it. */
    private void validateBramble(TiledObject o, int owner) {
        Bramble bramble = Bramble.fromMap(assets, o, game.trapWarnings(), describe(o));
        List<Rectangle> solids = new ArrayList<>(), floors = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions")) {
            if ("QuickSand".equals(c.type)) continue;
            Rectangle r = new Rectangle(c.x, c.y, c.width, c.height);
            floors.add(r);
            if (!"Platform".equals(c.type)) solids.add(r);
        }
        String problem = Bramble.placementProblem(new Rectangle(o.x, o.y, o.width, o.height), bramble.rootRight(), solids, floors);
        if (problem != null) throw new IllegalArgumentException("Bramble " + describe(o) + ": " + problem);
        requireSafeSetting(o, bramble, bramble.fullBounds(), owner);
    }

    /** The puffball's clocked release is heard only while the puffball is inside the camera view (#445): off screen it must not puff every period. */
    private void puffballSound(Puffball puffball) {
        if (game.playSounds() && puffballInView(puffball)) game.playSound(PUFFBALL_BURST_SOUND);
    }

    /** True when {@code box} is within the camera's visible world rect: a cue that is heard only while it is on screen (D89, D90). */
    public boolean inCameraView(Rectangle box) {
        Rectangle view = visibleWorldRect.get();
        return view != null && view.width > 0 && view.height > 0 && view.overlaps(box);
    }

    boolean puffballInView(Puffball puffball) {
        Rectangle view = visibleWorldRect.get();
        return view != null && view.width > 0 && view.height > 0 && puffball.inView(view);
    }

    /** Both honey fungi burst with this one sound (D63: it must not tell which one kills); the hero's own trigger puts them in view. */
    private void sporePuff() {
        if (game.playSounds()) game.playSound(SPORE_PUFF_SOUND);
    }

    /** The thorns turn lethal (#446): one sound for the root and the tuft; the root also throws a few specks from its mound. */
    public void thornSprung(ThornTrap thorn) {
        if (game.playSounds()) game.playSound(THORN_SPRING_SOUND);
        if (thorn.kind() == ThornTrap.Kind.ROOT)
            fx.thornSpecks(thorn.position.x + thorn.size.x / 2f, thorn.position.y + thorn.size.y);
    }

    /** A stump or a root goes off (D47): its sound, then the jolt for a hero it did not kill (#448). */
    private void explosiveBlast() {
        if (game.playSounds()) game.playSound("explosion");
        shakeAfterBlast(ScreenShake.BLAST_AMPLITUDE_PX, ScreenShake.BLAST_SECONDS);
    }

    /** A bramble cue is heard only while the bramble is inside the camera view: a clocked trap off screen must not tick every period. */
    private void brambleSound(Bramble bramble, String sound) {
        if (game.playSounds() && brambleInView(bramble)) game.playSound(sound);
    }

    /** True while any part of the bramble's box is inside the camera's visible world rect (D89). */
    boolean brambleInView(Bramble bramble) {
        Rectangle view = visibleWorldRect.get();
        return view != null && view.width > 0 && view.height > 0 && bramble.boxOverlaps(view);
    }

    /**
     * A rhythmic trap's danger area must lie inside its room and must never cover a place the hero has to stand:
     * the entrance (every retry restarts at the authored phase), the room exit or a checkpoint.
     */
    private void requireSafeSetting(TiledObject o, LethalHazard hazard, Rectangle area) {
        requireSafeSetting(o, hazard, area, currentRoom);
    }

    private void requireSafeSetting(TiledObject o, LethalHazard hazard, Rectangle area, int owner) {
        if (!currentRoomBoundsOf(area))
            throw new IllegalArgumentException(o.type + " " + describe(o) + ": its danger area leaves the room");
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        var box = player.hitbox;
        if (hazard.everLethalIn(new Rectangle(room.entrance().x + box.offsetX, room.entrance().y + box.offsetY, box.width, box.height)))
            throw new IllegalArgumentException(o.type + " " + describe(o) + ": its danger area covers the room entrance");
        if (room.exit() != null && hazard.everLethalIn(room.exit()))
            throw new IllegalArgumentException(o.type + " " + describe(o) + ": its danger area covers the room exit");
        for (TiledObject other : map.layer("SpawnPoints"))
            if ("Checkpoint".equals(other.type) && roomLayout.owner(other) == owner
                && hazard.everLethalIn(new Rectangle(other.x, other.y, other.width, other.height)))
                throw new IllegalArgumentException(o.type + " " + describe(o) + ": its danger area covers a checkpoint");
    }

    /**
     * FallingBranch / FallingStone (D45, #232). Properties: {@code zoneX, zoneY, zoneWidth, zoneHeight} (the
     * trigger zone, absolute world px, required, inside this room); stone only: {@code skin} (default forest) and {@code lipTop} (#360, optional).
     * The object's own rectangle is the hanging position and the lethal footprint; it falls to the first solid
     * (Collisions layer, not QuickSand) below it. Traps are rebuilt with every room activation, so retry,
     * Restart Level and room re-entry all re-arm them.
     */
    private void spawnFallingTrap(TiledObject o) {
        FallingTrapLayout.Spec spec = FallingTrapLayout.validate(o, roomLayout, currentRoom, map, game.logicalResolution().y);
        java.util.function.Supplier<Rectangle> hero = () -> player.hitboxBoundsInto(trapHeroScratch);
        java.util.function.BooleanSupplier grounded = () -> player.isOnGround();
        Runnable kill = () -> player.collidedWithActor(false);
        float x = spec.body().x + spec.body().width / 2f;
        float floor = spec.landing(), width = spec.body().width;
        FallingTrap trap;
        if (spec.stone()) {
            trap = new FallingStone(assets, spec.skin(), spec.body(), spec.zone(), spec.landing(), spec.lipTop(), game.trapWarnings(), hero, grounded, kill);
            trap.onImpact(() -> {
                groundShake(x, HangingVine.STONE_STRENGTH);
                fx.stoneImpact(x, floor, width);
                fx.shake().start(ScreenShake.STONE_AMPLITUDE_PX, ScreenShake.STONE_SECONDS);
                if (game.playSounds()) game.playSound(STONE_IMPACT_SOUND);
            });
        } else {
            trap = new FallingBranch(assets, spec.body(), spec.zone(), spec.landing(), game.trapWarnings(), hero, grounded, kill);
            // D155: lighter than the stone, with no shake and no swaying vines.
            trap.onImpact(() -> {
                fx.branchImpact(x, floor, width);
                if (game.playSounds()) game.playSound(BRANCH_IMPACT_SOUND);
            });
        }
        objects.add(trap);
    }

    /**
     * The hanging hive (#365, D90), built and checked for the room {@code owner}: its stingers stop on the first solid under
     * its mouth in that room; a moving or switchable solid in the band fails, the band stays inside the room and never covers
     * the entrance, the exit or a checkpoint (D46's rule). Called for every room at load and again when the room spawns.
     */
    private HangingHive hiveFor(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        float floor = HangingHive.floorBelow(o, map.layer("Collisions"), room);
        HangingHive hive = HangingHive.fromMap(assets, o, game.trapWarnings(), floor, describe(o));
        String moving = HangingHive.movingSolidProblem(o, map.layer("SpawnPoints"), floor);
        if (moving != null) throw new IllegalArgumentException("HangingHive " + describe(o) + ": " + moving);
        Rectangle column = hive.columnInto(new Rectangle());
        if (column.x < room.x || column.x + column.width > room.x + room.width || column.y < room.y || column.y + column.height > room.y + room.height)
            throw new IllegalArgumentException("HangingHive " + describe(o) + ": its danger area leaves the room");
        requireSafeSetting(o, hive, column, owner);
        return hive;
    }

    private void spawnHive(TiledObject o) {
        HangingHive hive = hiveFor(o, currentRoom);
        hive.onWarning(() -> hiveSound(hive, HIVE_BUZZ_SOUND));
        hive.onRelease(() -> hiveSound(hive, STINGER_RAIN_SOUND));
        hive.onKnockDown(() -> hiveSound(hive, HIVE_FALL_SOUND));
        objects.add(hive);
    }

    /** A hive's sound is heard only while the hive or its band is inside the camera view: a clocked trap off screen must not buzz. */
    private void hiveSound(HangingHive hive, String sound) {
        if (game.playSounds() && hiveInView(hive)) game.playSound(sound);
    }

    /** True while the hive's frame or the column its band can cover overlaps the camera's visible world rect (D90). */
    boolean hiveInView(HangingHive hive) {
        Rectangle view = visibleWorldRect.get();
        return view != null && view.width > 0 && view.height > 0
            && hive.extentInto(hiveViewScratch).overlaps(view);
    }

    /**
     * The ember fissure (#366, D91), built and checked for the room {@code owner}: it stands on a solid floor, a solid block
     * above may cut its column (one-way platforms do not), no moving or switchable solid stands in the column, and the column
     * stays inside the room and never covers the entrance, the exit or a checkpoint (D46's rule). Called for every room at
     * load; the result is kept in {@code checkedFissures}, so a spawn does not check again.
     */
    private EmberFissure fissureFor(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        List<TiledObject> collisions = new ArrayList<>();
        List<Rectangle> floors = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions")) {
            if (c.x + c.width <= room.x || c.x >= room.x + room.width) continue;
            collisions.add(c);
            if (!"QuickSand".equals(c.type) && !"Platform".equals(c.type)) floors.add(new Rectangle(c.x, c.y, c.width, c.height));
        }
        EmberFissure fissure = "LanceRoot".equals(o.type) ? LanceRoot.fromMap(assets, o, game.trapWarnings(), collisions, describe(o))
            : EmberFissure.fromMap(assets, o, game.trapWarnings(), collisions, describe(o));
        String problem = EmberFissure.floorProblem(o, floors);
        if (problem == null) problem = EmberFissure.movingSolidProblem(o, map.layer("SpawnPoints"), fissure.columnHeight());
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + ": " + problem);
        Rectangle column = fissure.columnInto(new Rectangle());
        if (column.x < room.x || column.x + column.width > room.x + room.width || column.y < room.y || column.y + column.height > room.y + room.height)
            throw new IllegalArgumentException(o.type + " " + describe(o) + ": its danger area leaves the room");
        requireSafeSetting(o, fissure, column, owner);
        return fissure;
    }

    private void spawnFissure(TiledObject o) {
        EmberFissure checked = checkedFissures.get(o.id);
        if (checked == null) { checked = fissureFor(o, currentRoom); checkedFissures.put(o.id, checked); } // added to the map after load (tests)
        boolean root = "LanceRoot".equals(o.type);
        EmberFissure fissure = root ? new LanceRoot(assets, o.x, o.y, game.trapWarnings(), checked.period(), checked.phase(), checked.jetSeconds(), checked.columnHeight())
            : new EmberFissure(assets, o.x, o.y, game.trapWarnings(), checked.period(), checked.phase(), checked.jetSeconds(), checked.columnHeight());
        fissure.onWarning(() -> fissureSound(fissure, root ? ROOT_CREAK_SOUND : FISSURE_RUMBLE_SOUND));
        fissure.onJet(() -> fissureSound(fissure, root ? ROOT_THRUST_SOUND : EMBER_JET_SOUND));
        objects.add(fissure);
    }

    /**
     * The leg-hold (#561, D128), built and checked for the room {@code owner}: the floor and headroom of {@link LegHold#problem},
     * its snapped arch inside the room and never over the entrance, the exit or a checkpoint. Called for every room at load and
     * again at each spawn (it has no state to keep: the hero supplier is the live one).
     */
    private LegHold legHoldFor(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        List<TiledObject> collisions = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions")) if (c.x + c.width > room.x && c.x < room.x + room.width) collisions.add(c);
        LegHold legHold = LegHold.fromMap(assets, game.trapWarnings(), o, () -> player.canLeaveRoom() ? player.hitboxBoundsInto(trapHeroScratch) : null, describe(o));
        String problem = LegHold.problem(o, collisions);
        if (problem != null) throw new IllegalArgumentException("LegHold " + describe(o) + ": " + problem);
        requireSafeSetting(o, legHold, legHold.snapBoxInto(new Rectangle()), owner);
        return legHold;
    }

    /** The leg-hold's sounds are heard only while its frame or its specks' reach is inside the camera view (D93). */
    private void legHoldSound(LegHold legHold, String sound) {
        Rectangle view = visibleWorldRect.get();
        if (game.playSounds() && view != null && view.width > 0 && view.height > 0 && legHold.extentInto(fissureViewScratch).overlaps(view)) game.playSound(sound);
    }

    /** A fissure's sound is heard only while its frame or column is inside the camera view: a clocked trap off screen is silent. */
    private void fissureSound(EmberFissure fissure, String sound) {
        if (game.playSounds() && fissureInView(fissure)) game.playSound(sound);
    }

    /** True while the fissure's frame or the column its jet can cover overlaps the camera's visible world rect (D91). */
    boolean fissureInView(EmberFissure fissure) {
        Rectangle view = visibleWorldRect.get();
        return view != null && view.width > 0 && view.height > 0 && fissure.extentInto(fissureViewScratch).overlaps(view);
    }

    /** A falling stone landed at {@code x}: every scenery vine within reach sways, unless the Screen shake option is off. */
    private void groundShake(float x, float strength) {
        if (!fx.shake().enabled()) return;
        for (int i = 0; i < vines.size(); i++) vines.get(i).shake(x, strength);
    }

    /** Proximity traps (D44) fail at load when they could not be jumped, float, or start triggered. */
    private void validateThorn(TiledObject o, int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        Rectangle entranceBody = new Rectangle(room.entrance().x + player.hitbox.offsetX, room.entrance().y + player.hitbox.offsetY,
            player.hitbox.width, player.hitbox.height);
        String problem = ThornTrap.problem(o, map.layer("Collisions"), map.layer("SpawnPoints"), room.bounds(), entranceBody);
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /** The hero's body box for the honey fungi, or null while the hero cannot be hurt (the thorn traps' rule). */
    private Rectangle fungusHero() {
        return player.canLeaveRoom() ? player.hitboxBoundsInto(fungusHeroScratch) : null;
    }

    /** Honey fungi (D63) fail at load without open floor and a bypass, or with the zone over the entrance, or a lethal cloud near a standing place. */
    private void validateFungus(TiledObject o, int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        Map<String, Rectangle> standing = new java.util.LinkedHashMap<>();
        standing.put("entrance", new Rectangle(room.entrance().x + player.hitbox.offsetX, room.entrance().y + player.hitbox.offsetY,
            player.hitbox.width, player.hitbox.height));
        if (room.exit() != null) standing.put("exit", room.exit());
        for (TiledObject other : map.layer("SpawnPoints"))
            if ("Checkpoint".equals(other.type) && roomLayout.owner(other) == owner)
                standing.put("checkpoint [" + other.id + "]", new Rectangle(other.x, other.y, other.width, other.height));
        String problem = FungusTrap.problem(o, "FalseHoneyFungus".equals(o.type), map.layer("Collisions"), map.layer("SpawnPoints"), room.bounds(), standing);
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * Explosive traps (D47) fail at load when they have no bypass (open floor and headroom around them) or when their
     * blast square leaves the room or covers the entrance, the room exit or a checkpoint.
     */
    private void validateExplosive(TiledObject o, int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        Map<String, Rectangle> standing = new java.util.LinkedHashMap<>();
        standing.put("entrance", new Rectangle(room.entrance().x + player.hitbox.offsetX, room.entrance().y + player.hitbox.offsetY,
            player.hitbox.width, player.hitbox.height));
        if (room.exit() != null) standing.put("exit", room.exit());
        for (TiledObject other : map.layer("SpawnPoints"))
            if ("Checkpoint".equals(other.type) && roomLayout.owner(other) == owner)
                standing.put("checkpoint [" + other.id + "]", new Rectangle(other.x, other.y, other.width, other.height));
        String problem = ExplosiveTrap.problem(o, map.layer("Collisions"), map.layer("SpawnPoints"), room.bounds(), standing);
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * The trip-line deadfall (D88) fails at load without its floor run and free swing, with its vine under the entrance, or
     * with its log passing a standing place at body height.
     */
    private void validateDeadfall(TiledObject o, int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        Map<String, Rectangle> standing = new java.util.LinkedHashMap<>();
        standing.put("entrance", new Rectangle(room.entrance().x + player.hitbox.offsetX, room.entrance().y + player.hitbox.offsetY,
            player.hitbox.width, player.hitbox.height));
        if (room.exit() != null) standing.put("exit", room.exit());
        for (TiledObject other : map.layer("SpawnPoints"))
            if ("Checkpoint".equals(other.type) && roomLayout.owner(other) == owner)
                standing.put("checkpoint [" + other.id + "]", new Rectangle(other.x, other.y, other.width, other.height));
        String problem = DeadfallTrap.problem(o, map.layer("Collisions"), map.layer("SpawnPoints"), room.bounds(), standing);
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /** The beetle (D100) fails at load when its range and blast leave the room, its floor has a gap, or a burst could cover a standing place. */
    private void validateBeetle(TiledObject o, int owner) {
        String problem = Beetle.problem(o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0), monsterFloors(),
            roomLayout.rooms.get(owner).bounds(), standingPlaces(owner));
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * The flying beetle (D145, D146) fails at load when its route leaves the room or the camera's view, meets a platform or a
     * solid too tall to fly over, a hanging object or anything a moving one passes through, runs lower or higher than its
     * allowed height over a surface, or lets a burst or a dive reach a standing place.
     */
    private void validateFlyingBeetle(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        List<Rectangle> blockers = monsterFloors();
        blockers.addAll(spiderBlockers());
        List<Rectangle> movers = movingAreas();
        blockers.addAll(movers);
        String problem = FlyingBeetle.problem(o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0),
            monsterFloors(), movers, blockers, room, FallingTrapLayout.floorCameraTop(room, game.logicalResolution().y),
            standingPlaces(owner));
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * The mosquito swarm (D151) fails at load when its line, with the scatter's reach, leaves the room or the camera's view,
     * meets anything solid, one-way, hanging or moving or the fall of a branch or stone, flies over a standing place or open air,
     * or leaves no free way past.
     */
    private void validateSwarm(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        List<Rectangle> blockers = monsterFloors();
        blockers.addAll(spiderBlockers());
        blockers.addAll(movingAreas());
        blockers.addAll(fallColumns(owner));
        String problem = MosquitoSwarm.problem(o.x, o.y, o.width, o.height, o.propBool("isVertical", false), o.propFloat("offNeg", 0),
            o.propFloat("offPos", 0), monsterFloors(), blockers, room,
            FallingTrapLayout.floorCameraTop(room, game.logicalResolution().y), standingPlaces(owner));
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * The spider (D102) fails at load when its range leaves the room, its floor has a gap, or its reach could cover a standing
     * place. A spider on a traced route (a Tiled polyline, D103) also fails when a piece is not a floor, a wall or a thread, when it
     * leaves the camera's view, or when a drop could not be stepped out of or could fall on a standing place.
     */
    private void validateSpider(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        String problem = o.polyline != null
            ? Spider.routeProblem(o.polyline, monsterFloors(), spiderSolids(), spiderBlockers(), spiderNoDrop(), room,
                FallingTrapLayout.floorCameraTop(room, game.logicalResolution().y), standingPlaces(owner))
            : Spider.problem(o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0), monsterFloors(),
                room, standingPlaces(owner));
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * The snail (D106) fails at load when its range leaves the room or has a gap under it, or when a firing line, from any point of
     * its range facing either way, does not end on a solid face or a floor inside the room, rises above the camera's view, or
     * crosses a standing place.
     */
    private void validateSnail(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        List<Rectangle> solids = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions"))
            if (!"Platform".equals(c.type) && !"QuickSand".equals(c.type)) solids.add(new Rectangle(c.x, c.y, c.width, c.height));
        String problem = Snail.problem(o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0), monsterFloors(),
            solids, room, FallingTrapLayout.floorCameraTop(room, game.logicalResolution().y), standingPlaces(owner));
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /**
     * The wolf (D115) fails at load when its range is shorter than {@link Wolf#MIN_RANGE}, leaves the room or has a gap under it,
     * when a standing place (the entrance body, the room exit, a checkpoint) is within its sight or reach, or when a solid hangs into
     * the {@link Wolf#HEADROOM} px above its floor over its reach.
     */
    private void validateWolf(TiledObject o, int owner) {
        Rectangle room = roomLayout.rooms.get(owner).bounds();
        List<Rectangle> solids = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions"))
            if (!"Platform".equals(c.type) && !"QuickSand".equals(c.type)) solids.add(new Rectangle(c.x, c.y, c.width, c.height));
        String problem = Wolf.problem(o.x, o.y, o.width, o.height, o.propFloat("offNeg", 0), o.propFloat("offPos", 0), monsterFloors(),
            solids, room, standingPlaces(owner));
        if (problem != null) throw new IllegalArgumentException(o.type + " " + describe(o) + " " + problem);
    }

    /** The static solids a spider's walls run along and its threads must miss: untyped and {@code Wall} collision boxes (D103). */
    private List<Rectangle> spiderSolids() {
        List<Rectangle> solids = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions"))
            if (c.type.isEmpty() || "Wall".equals(c.type)) solids.add(new Rectangle(c.x, c.y, c.width, c.height));
        return solids;
    }

    /**
     * The whole area each solid or supporting object that moves passes through: escalators along their range, falling platforms
     * down their fall, vertical walls to their raised stop and ladders down their drop (D145).
     */
    private List<Rectangle> movingAreas() {
        List<Rectangle> areas = new ArrayList<>();
        for (TiledObject s : map.layer("SpawnPoints")) {
            switch (s.type) {
                case "Escalator" -> areas.add(Escalator.travel(s.x, s.y, s.width, s.height, s.propBool("isVertical", false),
                    s.propFloat("offNeg", 0), s.propFloat("offPos", 0)));
                case "FallingPlatform" -> areas.add(new Rectangle(s.x, s.y, s.width, s.height + FallingPlatform.FALL_DISTANCE));
                case "Actionable" -> {
                    if ("Wall".equals(s.prop("type", "")) && "vertical".equals(s.prop("movement", "")))
                        areas.add(new Rectangle(s.x, s.y - s.height, s.width, s.height + s.height));
                }
                case "Ladder" -> areas.add(new Rectangle(s.x, s.y, s.width, s.height + s.propFloat("dropDistance", 0)));
                default -> { }
            }
        }
        return areas;
    }

    /** The columns the falling branches and stones of room {@code owner} drop through, from where each hangs to where it lands. */
    private List<Rectangle> fallColumns(int owner) {
        List<Rectangle> columns = new ArrayList<>();
        for (TiledObject s : map.layer("SpawnPoints")) {
            if (!FallingTrapLayout.isFallingTrap(s.type) || roomLayout.owner(s) != owner) continue;
            FallingTrapLayout.Spec spec = FallingTrapLayout.validate(s, roomLayout, owner, map, game.logicalResolution().y);
            columns.add(new Rectangle(spec.body().x, spec.body().y, spec.body().width, spec.landing() - spec.body().y));
        }
        return columns;
    }

    /** What a spider's thread must not pass through besides solids: one-way platforms and objects that move or hang (D103). */
    private List<Rectangle> spiderBlockers() {
        List<Rectangle> blockers = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions"))
            if ("Platform".equals(c.type)) blockers.add(new Rectangle(c.x, c.y, c.width, c.height));
        for (TiledObject s : map.layer("SpawnPoints"))
            if (SPIDER_BLOCKER_TYPES.contains(s.type)) blockers.add(new Rectangle(s.x, s.y, s.width, s.height));
        return blockers;
    }

    /** Where a drop must not land: climbables (the hero on them cannot step aside) and quicksand (D103). */
    private List<Rectangle> spiderNoDrop() {
        List<Rectangle> places = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions"))
            if ("QuickSand".equals(c.type)) places.add(new Rectangle(c.x, c.y, c.width, c.height));
        for (TiledObject s : map.layer("SpawnPoints"))
            if ("Ladder".equals(s.type) || "HangingVine".equals(s.type)) places.add(new Rectangle(s.x, s.y, s.width, s.height));
        return places;
    }

    /** Where the hero must be able to stand in a room: its entrance body, its exit and its checkpoints (load-time checks). */
    private Map<String, Rectangle> standingPlaces(int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        Map<String, Rectangle> standing = new java.util.LinkedHashMap<>();
        standing.put("entrance", new Rectangle(room.entrance().x + player.hitbox.offsetX, room.entrance().y + player.hitbox.offsetY,
            player.hitbox.width, player.hitbox.height));
        if (room.exit() != null) standing.put("exit", room.exit());
        for (TiledObject other : map.layer("SpawnPoints"))
            if ("Checkpoint".equals(other.type) && roomLayout.owner(other) == owner)
                standing.put("checkpoint [" + other.id + "]", new Rectangle(other.x, other.y, other.width, other.height));
        return standing;
    }

    /** The floors a crawling monster may stand on: every collision box but quicksand (load-time checks). */
    private List<Rectangle> monsterFloors() {
        List<Rectangle> floors = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions"))
            if (!"QuickSand".equals(c.type)) floors.add(new Rectangle(c.x, c.y, c.width, c.height));
        return floors;
    }

    /** Rotten tree branches (D80) fail at load above a sound branch (D69) or when they are the only way up (D26). */
    private void validateBranch(TiledObject o, int owner) {
        RoomLayout.Room room = roomLayout.rooms.get(owner);
        List<Rectangle> goals = new ArrayList<>();
        if (room.exit() != null) goals.add(room.exit());
        for (TiledObject other : map.layer("SpawnPoints"))
            if ("Checkpoint".equals(other.type) && roomLayout.owner(other) == owner)
                goals.add(new Rectangle(other.x, other.y, other.width, other.height));
        String problem = FallingPlatform.branchProblem(o, map.layer("Collisions"), room.bounds(), room.entrance().x,
            room.entrance().y, goals);
        if (problem != null) throw new IllegalArgumentException("FallingPlatform " + describe(o) + " " + problem);
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
        objects.add(new Collectable(assets, game, type.name(), o.x, o.y, o.width, o.height, () -> {
            recordPickup(o.id);
            pickupSparks.burst(o.x + o.width / 2f, o.y + o.height / 2f);
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
                    staticPlatforms.add(new StaticPlatform(assets, o.x, o.y, o.width, o.height,
                        StaticPlatform.Skin.fromMap(o.prop("skin", "")), o.propBool("mirror", false)));
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
     * Every room is closed from above (D38, #215). Walls cannot be grabbed since D121, so the slab no longer
     * stops a climb; it still closes the room from above, so no jump, thrown object or push can leave the
     * level through the top. The slab sits entirely above the room (its underside is the room's top edge), so it cuts no jump, ladder,
     * platform or hint zone inside the room. Added here rather than authored in each map so that
     * every present and future room, in every level, has it.
     */
    static CollisionBlock ceiling(Rectangle room) {
        return CollisionBlock.plain(room.x, room.y - CEILING_THICKNESS, room.width, CEILING_THICKNESS);
    }

    /** {@code Rectangle.overlaps} against an object's box, without building the rectangle. */
    private static boolean overlapsBox(Rectangle bounds, Entity box) {
        return CollisionUtils.aabb(bounds.x, bounds.y, bounds.width, bounds.height, box.getX(), box.getY(), box.getWidth(), box.getHeight());
    }

    /** Solid placement/flight check. One-way platforms only block placement, never horizontal flight. */
    public boolean blocksObject(Rectangle bounds, boolean placement) {
        if (!currentRoomBounds().contains(bounds)) return true;
        for (int i = 0; i < collisionBlocks.size(); i++) {
            CollisionBlock block = collisionBlocks.get(i);
            if (!block.isActive() || block.isQuickSand() || (!placement && block.isPlatform())) continue;
            if (overlapsBox(bounds, block)) return true;
        }
        for (int i = 0; i < escalators.size(); i++) if (overlapsBox(bounds, escalators.get(i))) return true;
        for (int i = 0; i < fallingPlatforms.size(); i++) if (overlapsBox(bounds, fallingPlatforms.get(i))) return true;
        return false;
    }

    /**
     * The top of the highest solid, one-way platform or moving platform at {@code x} whose top lies between {@code fromY} and
     * {@code toY} (larger is lower), or {@code NaN} when there is none: what a flying beetle (D145) dives on and falls to. No
     * allocation.
     */
    public float surfaceUnder(float x, float fromY, float toY) {
        float surface = Float.NaN;
        for (int i = 0; i < collisionBlocks.size(); i++) {
            CollisionBlock block = collisionBlocks.get(i);
            if (block.isActive() && !block.isQuickSand()) surface = higherTop(block, x, fromY, toY, surface);
        }
        for (int i = 0; i < escalators.size(); i++) surface = higherTop(escalators.get(i), x, fromY, toY, surface);
        for (int i = 0; i < fallingPlatforms.size(); i++) surface = higherTop(fallingPlatforms.get(i), x, fromY, toY, surface);
        return surface;
    }

    private static float higherTop(Entity box, float x, float fromY, float toY, float best) {
        float top = box.getY();
        if (x < box.getX() || x > box.getX() + box.getWidth() || top < fromY || top > toY) return best;
        return Float.isNaN(best) || top < best ? top : best;
    }

    /**
     * True when floor a spider could have crawled on covers {@code bounds}: an authored collision box (what the load checks of a
     * spider count as floor, {@code monsterFloors}), not quicksand, a ladder's top step or an object that moves (#497).
     */
    public boolean spiderFloorAt(Rectangle bounds) {
        for (int i = 0; i < collisionBlocks.size(); i++) {
            CollisionBlock block = collisionBlocks.get(i);
            if (!block.isActive() || block.isQuickSand() || !overlapsBox(bounds, block)) continue;
            if (!isLadderTop(block)) return true;
        }
        return false;
    }

    private boolean isLadderTop(CollisionBlock block) {
        for (int i = 0; i < ladders.size(); i++) if (ladders.get(i).topSupport() == block) return true;
        return false;
    }

    /** Feedback belongs to a visible linked object, never to the invisible trigger rectangle. */
    public Entity mechanismFeedbackTarget(Trigger trigger) {
        if (trigger == null) return null;
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof Lever lever && !lever.isRemoved() && lever.trigger == trigger) return lever;
        }
        Entity nearest = null;
        for (int i = 0; i < objects.size(); i++) {
            WorldObject object = objects.get(i);
            if (object instanceof Entity entity && object instanceof Actionable actionable
                && !object.isRemoved() && trigger.targetId.equals(actionable.getTargetId())) {
                nearest = nearerFeedbackTarget(trigger, nearest, entity);
            }
        }
        for (int i = 0; i < escalators.size(); i++) {
            Escalator escalator = escalators.get(i);
            if (!escalator.isRemoved() && trigger.targetId.equals(escalator.getTargetId()))
                nearest = nearerFeedbackTarget(trigger, nearest, escalator);
        }
        return nearest;
    }

    /**
     * Adds the visible mechanisms {@code trigger} drives to {@code out}, each once (identity), so the player can see what a
     * lever will affect (D82: the fairy guide's passive glow). Index loops, no allocation: it runs every frame a lever is selected.
     */
    public void linkedMechanismsInto(Trigger trigger, Array<Entity> out) {
        if (trigger == null) return;
        for (int i = 0; i < objects.size(); i++) {
            WorldObject object = objects.get(i);
            if (object instanceof Entity entity && object instanceof Actionable actionable && !object.isRemoved()
                && trigger.targetId.equals(actionable.getTargetId()) && !out.contains(entity, true)) out.add(entity);
        }
        for (int i = 0; i < escalators.size(); i++) {
            Escalator escalator = escalators.get(i);
            if (!escalator.isRemoved() && trigger.targetId.equals(escalator.getTargetId()) && !out.contains(escalator, true))
                out.add(escalator);
        }
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
        for (int i = 0; i < triggers.size(); i++) {
            Trigger trigger = triggers.get(i);
            if (!trigger.throwActivated || trigger.isRemoved()) continue;
            if (!overlapsBox(bounds, trigger)) continue;
            activateTrigger(trigger);
            return true;
        }
        return false;
    }

    /** True the first time {@code actionable} is seen in this pull (identity, like the HashSet it replaces). */
    private static boolean markActivated(Array<Actionable> activated, Actionable actionable) {
        if (activated.contains(actionable, true)) return false;
        activated.add(actionable);
        return true;
    }

    public void activateTrigger(Trigger trigger) {
        Array<Actionable> activated = activatedScratch;
        activated.clear();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId())
                && markActivated(activated, actionable)) {
                perform(actionable);
            }
        }
        for (int i = 0; i < collisionBlocks.size(); i++) {
            if (collisionBlocks.get(i) instanceof Actionable actionable && trigger.targetId.equals(actionable.getTargetId())
                && markActivated(activated, actionable)) {
                perform(actionable);
            }
        }
        for (int i = 0; i < escalators.size(); i++) {
            Escalator escalator = escalators.get(i);
            if (trigger.targetId.equals(escalator.getTargetId()) && markActivated(activated, escalator)) {
                escalator.performAction();
            }
        }
        activated.clear();
        linkedMechanismsInto(trigger, fairy.beginChanged()); // D87: the fairy keeps a cue on what this pull changed
    }

    /**
     * A vertically moving wall that really starts to travel shakes the screen for its move time, drops ceiling grit and
     * plays the rumble and the falling grit (#239). The shake obeys the Screen shake option; grit and sounds do not.
     */
    private void perform(Actionable actionable) {
        actionable.performAction();
        if (actionable instanceof Wall wall && wall.movesVertically() && wall.isActive()) {
            fx.shake().start(ScreenShake.LEVER_AMPLITUDE_PX, wall.moveDurationSeconds());
            fx.ceilingGrit(wall.getX() + wall.getWidth() / 2f, wall.getWidth(), currentRoomBounds().y);
            if (game.playSounds()) {
                // The rumble loops for exactly the wall's move time (stopped in update, or by clearCameraEffects).
                game.loopSound(WALL_RUMBLE_SOUND);
                rumbling = true;
                rumbleSeconds = wall.moveDurationSeconds();
                game.playSound(GRIT_FALL_SOUND);
            }
        }
    }

    /**
     * The most recently spawned coin ripple still running, or null. Like Dart and Rust, only
     * one is drawn at a time, so a second coin collected mid-ripple takes over from the first.
     */
    public RippleEffect activeRipple() {
        RippleEffect change = roomRipple.distortion();
        if (change != null) return change;
        for (int i = objects.size() - 1; i >= 0; i--) {
            if (objects.get(i) instanceof RippleEffect ripple && !ripple.isRemoved()) return ripple;
        }
        return null;
    }

    @Override
    public void update(float dt) {
        fx.update(dt);
        if (rumbling && (rumbleSeconds -= dt) <= 0) stopRumble();
        if (victory) {
            if (player.victoryPausePressed()) { game.togglePause(); return; }
            dt = Math.min(dt, .05f);
            updateWorld(dt);
            finishUpdate(dt);
            if (!completionSent) player.settleVictory(dt);
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
        stepNewFireballs(dt);
        stepNewThrows(dt);
        killOnLethalHazards();
        for (int i = 0; i < objects.size(); i++) if (objects.get(i) instanceof Carryable c) c.followHolder();
        if (boss != null) boss.resolveCombat();

        finishUpdate(dt);
    }

    /**
     * A fireball cast this frame flies its first step now, on the frame it was cast (#503), as the sword resolves on its own
     * frame: a monster's bite pending from this frame (D102) then meets the ball before the bite lands, so the hit wins the tie.
     * It joins the object list at the end of the frame and first updates, as its second step, on the next.
     */
    private void stepNewFireballs(float dt) {
        for (int i = 0; i < pendingSpawns.size(); i++) if (pendingSpawns.get(i) instanceof Fireball ball) ball.update(dt);
    }

    /**
     * A stone or torch thrown this frame flies its first step now (#505), as a fireball does: a bite or a gob pending from this
     * frame (D102, D106) then meets it before it lands, so the hit wins the tie. It is already in the object list and updated
     * this frame as held, so only its release frame is stepped here; the next frame's update is its second step.
     */
    private void stepNewThrows(float dt) {
        for (int i = 0; i < objects.size(); i++) if (objects.get(i) instanceof Carryable c) c.stepIfJustThrown(dt);
    }

    /** Rhythmic traps (D46) kill by the hero's body box, exactly like the bomb blast: Player.die holds the only guards. */
    private void killOnLethalHazards() {
        Rectangle body = player.hitboxBoundsInto(lethalBodyScratch);
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof LethalHazard hazard && hazard.lethalTo(body)) {
                player.collidedWithActor(false);
                return;
            }
        }
    }

    /** The world keeps moving during victory; only the hero uses a protected scripted walk. */
    private void updateWorld(float dt) {
        for (int i = 0; i < ambience.size(); i++) ambience.get(i).update(dt);
        // Before the objects: a hit this frame sprays after this point, so its drops first move next frame, as queued objects did.
        blood.update(dt);
        fireballFx.update(dt);
        pickupSparks.update(dt);
        portal.update(dt);
        roomRipple.update(dt);
        beetleFx.update(dt);
        spiderFx.update(dt);
        snailFx.update(dt);
        wolfFx.update(dt);
        remainsFx.update(dt);
        leafFx.update(dt);
        for (int i = 0; i < escalators.size(); i++) escalators.get(i).update(dt);
        for (int i = 0; i < fallingPlatforms.size(); i++) fallingPlatforms.get(i).update(dt);
        updateDecor(dt);
        for (int i = 0; i < vines.size(); i++) vines.get(i).update(dt);
        for (int i = 0; i < objects.size(); i++) objects.get(i).update(dt);
    }

    /** The hero brushing past scenery (#239): reads the hero's box and heading, changes nothing in gameplay. */
    private void updateDecor(float dt) {
        boolean hero = !player.isRemoved();
        if (hero) player.hitboxBoundsInto(heroBodyScratch);
        for (int i = 0; i < decor.size(); i++) {
            Decor d = decor.get(i);
            if (hero) d.notice(heroBodyScratch, player.velocity.x);
            // D119: a bush sways for every actor moving through it (monsters, the boss), not only the hero.
            if (d.brushes() && !d.playing()) for (int o = 0; o < objects.size(); o++)
                if (objects.get(o) instanceof Enemy<?> e && !e.isDefeated() && !e.isRemoved()) d.notice(e.hitboxBoundsInto(actorBodyScratch), e.velocity.x);
            d.update(dt);
        }
    }

    /**
     * Ends every path that advances the world. The fairy guide is updated here, once, after the hero has moved, so she
     * moves exactly when the world does and her selection is the one the label and the hero's next action see (D82).
     * The one early return, the victory pause, opens the pause menu before the world steps: she holds with it on purpose.
     */
    private void finishUpdate(float dt) {
        escalators.removeIf(WorldObject::isRemoved);
        fallingPlatforms.removeIf(WorldObject::isRemoved);
        collisionBlocks.removeIf(CollisionBlock::isRemoved);
        objects.removeIf(WorldObject::isRemoved);

        if (!pendingSpawns.isEmpty()) {
            for (int i = 0; i < pendingSpawns.size(); i++) if (!pendingSpawns.get(i).isRemoved()) objects.add(pendingSpawns.get(i)); // a ball spent on its cast frame (#503) never joins
            pendingSpawns.clear();
        }
        fairy.update(dt, player);
        parallax.update(dt); // the clouds drift with the world's time, not the camera's
    }

    /**
     * Draws only the tiles overlapping {@code visibleWorldRect} (with a
     * 1-tile margin), instead of every tile in the map regardless of the
     * camera. For forest-1 (180x23 = 4140 tiles) that's the difference
     * between ~4140 draw calls a frame and the ~40x23 actually on screen.
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
        if (!parallax.isEmpty()) parallax.renderBack(batch, visibleWorldRect, ParallaxRenderer.originY(currentRoomBounds(), visibleWorldRect.height));
        // D122: a trunk is drawn behind the play tiles, so a tree whose branch hangs under a ledge passes behind that ledge.
        for (int i = 0; i < decor.size(); i++) if (decor.get(i).behindTiles()) decor.get(i).render(batch);
        renderBackground(batch, visibleWorldRect);
        for (int i = 0; i < decor.size(); i++) if (!decor.get(i).behindTiles()) decor.get(i).render(batch);
        for (int i = 0; i < vines.size(); i++) vines.get(i).render(batch);
        fairy.renderBehind(batch); // the target glows: light behind the objects, the objects stay crisp (D82)
        portal.render(batch); // behind every object and the hero: it marks the exit and never covers anything (#552)
        for (int i = 0; i < staticPlatforms.size(); i++) staticPlatforms.get(i).render(batch);
        for (int i = 0; i < escalators.size(); i++) escalators.get(i).render(batch);
        for (int i = 0; i < fallingPlatforms.size(); i++) fallingPlatforms.get(i).render(batch);
        for (int i = 0; i < objects.size(); i++) objects.get(i).render(batch);
        blood.render(batch);
        fireballFx.render(batch);
        beetleFx.render(batch);
        spiderFx.render(batch);
        snailFx.render(batch);
        wolfFx.render(batch);
        remainsFx.render(batch);
        fx.render(batch);
        leafFx.render(batch);
        // Fireflies are small lights: added onto the scene like the menu's, then the normal blend is restored.
        // Rooms without fireflies skip the switch, which would otherwise flush the batch twice a frame.
        if (!ambience.isEmpty()) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (int i = 0; i < ambience.size(); i++) ambience.get(i).render(batch);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (!player.isRemoved() && !heroLeft && !roomRipple.heroUnseen()) player.render(batch);
        roomRipple.render(batch);
        pickupSparks.render(batch); // over the hero: the item is at his hand when he takes it, and behind him the burst stays hidden until it leaves his outline
        fairy.renderFront(batch);
        if (!parallax.isEmpty()) parallax.renderFront(batch, visibleWorldRect, currentRoomBounds(), ParallaxRenderer.originY(currentRoomBounds(), visibleWorldRect.height));
    }

    private final Rectangle debugBodyScratch = new Rectangle();

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
            if (o instanceof Remains body && body.isRemains()) {
                // A body (D101) is reached by its own box, not by the one it stood in.
                Rectangle r = body.remainsInto(debugBodyScratch);
                sr.rect(r.x, r.y, r.width, r.height);
            } else if (o instanceof Actor<?> actor) {
                drawHitbox(sr, actor);
            }
        }

        // What kills on touch, where it differs from the body box.
        sr.setColor(1f, 0.55f, 0.1f, 1f);
        for (WorldObject o : objects) {
            if (o instanceof Enemy<?> enemy && !enemy.isDefeated() && enemy.hasSeparateHurtbox()) {
                Rectangle r = enemy.hurtBounds();
                sr.rect(r.x, r.y, r.width, r.height);
            }
        }

        // Melee attack colliders: yellow while winding up, magenta on the frames that hurt.
        for (WorldObject o : objects) {
            if (o instanceof Enemy<?> enemy && !enemy.isDefeated() && enemy.attackBounds() != null) {
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
