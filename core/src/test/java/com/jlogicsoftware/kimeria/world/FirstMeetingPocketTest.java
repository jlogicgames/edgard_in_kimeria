package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.YellowMob;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.entity.objects.LethalHazard;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.objects.ThornVine;
import com.jlogicsoftware.kimeria.entity.objects.Bramble;
import com.jlogicsoftware.kimeria.entity.objects.DeadfallTrap;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D105 (#499): a new element's first meeting has a safe pocket. Each test stands the hero still, for
 * {@link #POCKET_SECONDS}, at the pocket its room card names (hero centre x, on the required route just before that beat)
 * and checks, every frame: nothing harms him; the element is inside the game camera's view (the target the player hands
 * the camera, {@code Player.roomCameraTarget}, at the logical resolution); and over the wait the element is seen acting
 * (a named state or phase, or {@link #MOVES} for one that patrols). {@code RoomCardsTest} checks that each card's
 * `First meeting` names one of these methods, with the room, the element and the pocket x in it; the 96 px clearance is
 * measured on the map there. Two kinds of element that act only on contact pass by a stop: an enemy that waits idle until the hero
 * is in its reach, stopped behind a solid log inside its range (the RedMob, {@code forest1MobTheRedMob}; the YellowMob behind its
 * cover, {@code forestEntryTheYellowMob}), and a touch trap whose trigger lies outside its lethal band, tripped and stood on (the
 * deadfall's vine, {@code forest1DeadfallTheDeadfall}; the false honey fungus's zone, {@code forestCanopyTheFalseFungus}, D105). A trap
 * whose trigger is the lethal spot has no pocket. A skill never acts (D109): its pocket is {@code skillPocket}, the whole first
 * obstacle in view while nothing lethal in the room stirs.
 */
class FirstMeetingPocketTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** Longer than every clock in these rooms (the slowest, the hive under the ladder, runs 4.5 s), twice over. */
    private static final float POCKET_SECONDS = 12f;
    /** The signal of an element whose position changed during the wait (a bat on its patrol). */
    static final String MOVES = "MOVES";

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    /** What the element is doing: its state, its clock's phase, and whether it has moved from where it started. */
    private static void signals(Entity e, float startX, float startY, Set<String> out) {
        if (e instanceof Actor<?> a) out.add(String.valueOf(a.getState()));
        if (e instanceof Bramble b) out.add(String.valueOf(b.currentPhase()));
        if (e instanceof HangingHive b) out.add(String.valueOf(b.currentPhase()));
        if (e instanceof Puffball b) out.add(String.valueOf(b.currentPhase())); // the puffball neither moves nor acts: its phase is the signal
        if (e instanceof DeadfallTrap b) out.add(b.phase().name());
        if (e instanceof EmberFissure b) out.add(String.valueOf(b.currentPhase()));
        if (e instanceof FungusTrap b) out.add(b.state().name());
        if (e instanceof ThornTrap b) out.add(b.phase().name());
        if (e instanceof ExplosiveTrap b) out.add(b.phase().name());
        if (e instanceof FallingTrap b) out.add(b.state().name());
        if (e instanceof ThornVine v && v.angle() != 0f) out.add(v.angle() < 0 ? "SWING_LEFT" : "SWING_RIGHT");
        if (e.getX() != startX || e.getY() != startY) out.add(MOVES);
    }

    /**
     * Stands the hero with his centre at {@code pocketX} in {@code room} and checks the pocket around the first (lowest x)
     * {@code type} there, the {@code element} of the room card, which must show {@code acting} during the wait.
     */
    private Set<String> pocket(String map, String room, String element, Class<? extends Entity> type, float pocketX, String acting) {
        return pocket(map, room, element, type, pocketX, Float.NaN, acting);
    }

    /**
     * As {@link #pocket(String, String, String, Class, float, String)}, with the hero standing on a support whose top is at
     * {@code standY} (world Y, growing downward) instead of the room's floor: a pocket raised out of a firing line (the snail, D106).
     */
    private Set<String> pocket(String map, String room, String element, Class<? extends Entity> type, float pocketX, float standY, String acting) {
        Rectangle view = new Rectangle();
        Level level = h.room(map, room, () -> view);
        Rectangle bounds = level.currentRoomBounds();
        Entity target = null;
        for (var o : level.objects)
            if (type.isInstance(o) && ((Entity) o).getX() >= bounds.x && ((Entity) o).getX() < bounds.x + bounds.width
                && (target == null || ((Entity) o).getX() < target.getX())) target = (Entity) o;
        for (var o : level.escalators) // escalators are kept apart from the other objects
            if (type.isInstance(o) && o.getX() >= bounds.x && o.getX() < bounds.x + bounds.width && (target == null || o.getX() < target.getX())) target = o;
        assertNotNull(target, room + " holds no " + type.getSimpleName() + " (" + element + ")");
        level.player.position.x = pocketX - level.player.getWidth() / 2;
        if (!Float.isNaN(standY)) {
            level.player.position.y = standY - level.player.getHeight();
            level.player.velocity.setZero();
        }
        assertEquals(pocketX, level.player.centerX(), 0.01f);
        float startX = target.getX(), startY = target.getY();
        Vector2 resolution = h.game().logicalResolution();
        Set<String> seen = new TreeSet<>();
        Rectangle box = new Rectangle();
        for (int f = 0; f < POCKET_SECONDS / STEP; f++) {
            Vector2 camera = level.player.roomCameraTarget();
            view.set(camera.x, camera.y, resolution.x, resolution.y);
            level.update(STEP);
            assertFalse(level.player.isGotHit(), room + ": the hero in the pocket at x " + pocketX + " was hit at " + f * STEP + " s");
            assertTrue(view.overlaps(box.set(target.getX(), target.getY(), target.getWidth(), target.getHeight())), room + ": the " + element + " left the camera's view at " + f * STEP + " s");
            signals(target, startX, startY, seen);
            if (!Float.isNaN(standY)) assertEquals(standY, level.player.position.y + level.player.getHeight(), 0.5f, room + ": the hero left the raised pocket");
        }
        assertTrue(seen.contains(acting), room + ": the " + element + " never showed " + acting + " from the pocket, only " + seen);
        return seen;
    }

    /** Anything in the room that can kill the hero: an enemy, a lethal hazard, or a trap with a trigger (D109). */
    private static boolean lethal(Object o) {
        return o instanceof Enemy<?> || o instanceof LethalHazard || o instanceof DeadfallTrap || o instanceof FungusTrap
            || o instanceof FallingTrap || o instanceof ExplosiveTrap || o instanceof ThornTrap;
    }

    /** A lethal object's position and everything it shows ({@link #signals}): the sum of what could stir. */
    private static String stir(Entity e) {
        Set<String> out = new TreeSet<>();
        signals(e, e.getX(), e.getY(), out);
        return e.getX() + "," + e.getY() + out;
    }

    /**
     * The pocket of a skill (D109): it never acts, so the hero stands at {@code pocketX} for {@link #POCKET_SECONDS} and, every
     * frame, is never hit, has the whole first obstacle (the room's climbable {@code Wall} block at {@code geometryX}) inside the
     * camera's view, and sees nothing lethal stir: no enemy, hazard or trap of the room (enemies, lethal hazards, deadfalls, fungus, falling,
     * exploding and thorn traps, puffballs) changes position, state or phase. The view check is trivial in a room narrower than the view (the whole room is in view from anywhere); it bites in a wide
     * room, as {@link #aSkillPocketTooFarFromTheWallFails} shows.
     */
    private void skillPocket(String map, String room, String element, float pocketX, float geometryX) {
        Rectangle view = new Rectangle();
        Level level = h.room(map, room, () -> view);
        Rectangle bounds = level.currentRoomBounds();
        CollisionBlock wall = null;
        for (CollisionBlock b : level.collisionBlocks)
            if (b.isWallClimbable() && b.getX() == geometryX && b.getX() >= bounds.x && b.getX() < bounds.x + bounds.width) wall = b;
        assertNotNull(wall, room + " holds no climbable wall at x " + geometryX + " (" + element + ")");
        level.player.position.x = pocketX - level.player.getWidth() / 2;
        assertEquals(pocketX, level.player.centerX(), 0.01f);
        java.util.List<Entity> watched = new java.util.ArrayList<>();
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (var o : level.objects) if (lethal(o)) { watched.add((Entity) o); rest.add(stir((Entity) o)); }
        for (var o : level.escalators) if (lethal(o)) { watched.add(o); rest.add(stir(o)); }
        Vector2 resolution = h.game().logicalResolution();
        Rectangle box = new Rectangle();
        for (int f = 0; f < POCKET_SECONDS / STEP; f++) {
            Vector2 camera = level.player.roomCameraTarget();
            view.set(camera.x, camera.y, resolution.x, resolution.y);
            level.update(STEP);
            assertFalse(level.player.isGotHit(), room + ": the hero in the pocket at x " + pocketX + " was hit at " + f * STEP + " s");
            assertTrue(view.contains(box.set(wall.getX(), wall.getY(), wall.getWidth(), wall.getHeight())), room + ": the whole " + element + " wall is not in the camera's view at " + f * STEP + " s");
            for (int i = 0; i < watched.size(); i++)
                assertEquals(rest.get(i), stir(watched.get(i)), room + ": " + watched.get(i).getClass().getSimpleName() + " stirred at " + f * STEP + " s while the " + element + " pocket stood");
        }
    }

    /** The two ways a skill pocket fails: the wall out of the view in a wide room, and a lethal object that stirs. */
    @Test void aSkillPocketTooFarFromTheWallFails() {
        assertThrows(AssertionError.class, () -> skillPocket("forest-2", "forest-walls", "wall-jump", 880, 1264), "the room's right wall (x 1264) is beyond the view from x 880");
        assertThrows(AssertionError.class, () -> skillPocket("forest-2", "forest-walls", "wall-jump", 1110, 992), "the RedMob wakes for a hero at x 1110");
    }

    /** A hero's body width, the margin a trip-and-stand pocket needs (D109). */
    private static final float BODY = 16;

    /**
     * How far the trap's trigger reaches past its lethal band, and how far the pocket's body edge stands from the band, both measured on
     * the map's object: {@code {reach, clear}}; the band is the cloud (fungus), the swept log (deadfall) or the blast (stump, root).
     */
    private float[] tripAndStandMeasure(Level level, Entity trap, float pocketX) {
        float bodyLeft = pocketX - 5.5f, bodyRight = pocketX + 5.5f, triggerLeft, triggerRight, bandLeft, bandRight;
        if (trap instanceof FalseHoneyFungus f) {
            Rectangle zone = f.triggerZone(), band = f.cloudBounds();
            triggerLeft = zone.x; triggerRight = zone.x + zone.width; bandLeft = band.x; bandRight = band.x + band.width;
        } else if (trap instanceof DeadfallTrap d) {
            Rectangle vine = d.vineBox(), log = new Rectangle();
            triggerLeft = vine.x; triggerRight = vine.x + vine.width; bandLeft = Float.MAX_VALUE; bandRight = -Float.MAX_VALUE;
            for (float deg = DeadfallTrap.SNAG_DEGREES; deg <= 0; deg += 2) { d.logBoxAt(deg, log); bandLeft = Math.min(bandLeft, log.x); bandRight = Math.max(bandRight, log.x + log.width); }
        } else if (trap instanceof ExplosiveTrap x) {
            Rectangle trigger = x.triggerBox(), band = x.blastBox();
            triggerLeft = trigger.x; triggerRight = trigger.x + trigger.width; bandLeft = band.x; bandRight = band.x + band.width;
        } else if (trap instanceof ThornTrap th) {
            Rectangle zone = th.triggerZone(), band = th.lethalBounds();
            triggerLeft = zone.x; triggerRight = zone.x + zone.width; bandLeft = band.x; bandRight = band.x + band.width;
        } else if (trap instanceof Bomb b) { // the Bomb's blast is a 64 px square centred on it (Bomb.BLAST_SIZE, private)
            Rectangle trigger = b.hitboxBounds();
            triggerLeft = trigger.x; triggerRight = trigger.x + trigger.width;
            bandLeft = b.getX() + b.getWidth() / 2f - 32; bandRight = bandLeft + 64;
        } else throw new AssertionError(trap.getClass().getSimpleName() + " has no measured trip-and-stand condition");
        float reach = Math.max(bandLeft - triggerLeft, triggerRight - bandRight);
        float clear = Math.max(bandLeft - bodyRight, bodyLeft - bandRight);
        return new float[]{reach, clear};
    }

    /** The first {@code type} of {@code room} (map order), for the measure. */
    private Entity firstOf(Level level, Class<? extends Entity> type) {
        for (var o : level.objects) if (type.isInstance(o)) return (Entity) o;
        throw new AssertionError("no " + type.getSimpleName());
    }

    /**
     * The condition of a trip-and-stand pocket (D109), asserted from the map for the pocket's own trap and x: the trigger reaches at least
     * {@link #BODY} px past the lethal band, and the pocket's body stands at least that clear of the band.
     */
    private void tripAndStand(String map, String room, Class<? extends Entity> type, float pocketX) {
        Level level = h.room(map, room, Rectangle::new);
        float[] m = tripAndStandMeasure(level, firstOf(level, type), pocketX);
        assertTrue(m[0] >= BODY, room + ": the " + type.getSimpleName() + " trigger reaches only " + m[0] + " px past its lethal band");
        assertTrue(m[1] >= BODY, room + ": the pocket at x " + pocketX + " stands only " + m[1] + " px from the lethal band");
    }

    /** The thorn root and tuft meet the condition: their trigger zone reaches 64 px or more past the kill band, and a pocket at the zone's edge stands clear of it (D109). */
    @Test void aThornRootOrTuftMeetsTheTripAndStandCondition() {
        for (String[] room : new String[][]{{"forest-1", "forest-1-bramble"}, {"forest-1", "forest-1-spider"}}) {
            Level level = h.room(room[0], room[1], Rectangle::new);
            ThornTrap thorn = (ThornTrap) firstOf(level, ThornTrap.class);
            float edge = thorn.triggerZone().x + 8;
            float[] m = tripAndStandMeasure(level, thorn, edge);
            assertTrue(m[0] >= BODY, room[1] + ": the thorn's trigger reaches " + m[0] + " px past its kill band");
            assertTrue(m[1] >= BODY, room[1] + ": a pocket at the zone's edge (x " + edge + ") stands " + m[1] + " px from the kill band");
        }
    }

    /** The Bomb's trigger box (10 x 13) lies inside its 64 px blast, so it fails the condition too: no pocket (D109). */
    @Test void aBombFailsTheTripAndStandCondition() {
        Level level = h.room("forest-2", "forest-entry", Rectangle::new);
        Bomb bomb = (Bomb) firstOf(level, Bomb.class);
        float[] m = tripAndStandMeasure(level, bomb, bomb.getX() - 40);
        assertTrue(m[0] < BODY, "the Bomb's trigger reaches " + m[0] + " px past its blast");
    }

    /** The stump and the root, whose trigger box lies inside their blast, fail the condition: no pocket (D109). */
    @Test void anExplodingStumpOrRootFailsTheTripAndStandCondition() {
        Level level = h.room("forest-1", "forest-1-mob", Rectangle::new);
        ExplosiveTrap stump = (ExplosiveTrap) firstOf(level, ExplosiveTrap.class);
        float[] m = tripAndStandMeasure(level, stump, stump.getX() - 40);
        assertTrue(m[0] < BODY, "the stump's trigger reaches " + m[0] + " px past its blast");
        Level canopy = h.room("forest-2", "forest-canopy", Rectangle::new);
        ExplosiveTrap root = (ExplosiveTrap) firstOf(canopy, ExplosiveTrap.class);
        assertTrue(tripAndStandMeasure(canopy, root, root.getX() - 40)[0] < BODY, "the root's trigger lies inside its blast");
    }

    /** The snail sees him on the one-way step (inside its sight, above its firing line): it notices and watches, never spits (D106). */
    @Test void forest1SpiderTheSnail() {
        Set<String> seen = pocket("forest-1", "forest-1-spider", "snail", Snail.class, 7456, 288, "NOTICE");
        for (Snail.State never : new Snail.State[]{Snail.State.WINDUP, Snail.State.SPIT, Snail.State.RELOAD})
            assertFalse(seen.contains(never.name()), "the snail never spits at the hero on the step: it showed " + seen);
        assertTrue(seen.contains(Snail.State.WATCH.name()), "it watches him after the notice: " + seen);
    }
    /** Behind the log (x 3728-3760) inside the RedMob's range: it notices him, runs and is stopped by the log, its whip short of him (D105). */
    @Test void forest1MobTheRedMob() { pocket("forest-1", "forest-1-mob", "redmob", RedMob.class, 3716, "RUN"); }
    /**
     * On the trip vine (x 5112-5128): he trips it and stands, 54 px short of the swing band (x 5182-5282), and sees the log slip and
     * swing past. Only for a touch trap whose trigger lies outside its lethal band, measured by {@link #tripAndStand} (D88, D105, D109); the
     * stump, the root and the Bomb, whose trigger lies inside their blast, fail it and have no such pocket.
     */
    @Test void forest1DeadfallTheDeadfall() { tripAndStand("forest-1", "forest-1-deadfall", DeadfallTrap.class, 5120); pocket("forest-1", "forest-1-deadfall", "deadfall", DeadfallTrap.class, 5120, "SWING"); }
    /** Between the falling platforms (x 96-224) and the first escalator's patrol (x 256-384): the platform is seen running (D105, D107). */
    @Test void forest1EscalatorsTheEscalator() { pocket("forest-1", "forest-1-escalators", "running-platform", Escalator.class, 240, MOVES); }
    /** Between the entrance and the cloud (x 766-786): the cloud is out at the entry and sinks, then swells (D110, #500); the RedMob (span x 896-1008) is far off and never wakes. */
    @Test void forest1TrapsThePuffball() { pocket("forest-1", "forest-1-traps", "puffball", Puffball.class, 744, "CLOUD"); }
    /** The tunnel's mouth under the roof: the beetle hears him, notices, chases and waits at its range end, never committing; its blast stops short of the tunnel (D105, #500). */
    @Test void forest1GuardTheBeetle() { pocket("forest-1", "forest-1-guard", "beetle", Beetle.class, 2550, "NOTICE"); }
    @Test void forest1SpiderTheSpider() { pocket("forest-1", "forest-1-spider", "spider", Spider.class, 7030, "NOTICE"); }
    @Test void forest1BrambleTheBramble() { pocket("forest-1", "forest-1-bramble", "bramble", Bramble.class, 6170, "GROW"); }
    @Test void forest1RouteTheBat() { pocket("forest-1", "forest-1-route", "bat-enemy", Bat.class, 1760, MOVES); }
    @Test void forest1HiveTheFloorHive() { pocket("forest-1", "forest-1-hive", "hanging-hive", HangingHive.class, 6420, "RELEASE"); }
    /** Behind the cover (x 112-144) inside the YellowMob's range: it wakes, runs at him and is stopped by the cover (D105). */
    @Test void forestEntryTheYellowMob() { pocket("forest-2", "forest-entry", "yellowmob", YellowMob.class, 100, "RUN"); }
    /** In the trigger zone (from x 1608), 26 px short of the cloud: it swells and bursts in front of him and is spent (D105). */
    @Test void forestCanopyTheFalseFungus() { tripAndStand("forest-2", "forest-canopy", FalseHoneyFungus.class, 1640); pocket("forest-2", "forest-canopy", "false-honey-fungus", FalseHoneyFungus.class, 1640, "BURST"); }
    /** 20 px short of the tip's sweep: the vine is seen swinging at him and back, never reaching (D46). */
    @Test void forestCanopyTheThornVine() { pocket("forest-2", "forest-canopy", "thorn-vine", ThornVine.class, 1848, "SWING_LEFT"); }
    /** A skill's pocket (D109): on the floor 26 px short of the first wall, the whole wall in view and nothing lethal stirring. */
    @Test void forestWallsTheWallJump() { skillPocket("forest-2", "forest-walls", "wall-jump", 880, 912); }
    @Test void forestFissureTheFirstFissure() { pocket("forest-2", "forest-fissure", "ember-fissure", EmberFissure.class, 3200, "JET"); }
}
