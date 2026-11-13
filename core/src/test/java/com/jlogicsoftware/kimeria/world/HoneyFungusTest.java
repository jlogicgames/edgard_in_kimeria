package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.entity.objects.HoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.LethalHazard;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The honey fungus pair (D63, #295 phase 2): the harmless {@link HoneyFungus} and the lethal {@link FalseHoneyFungus}, driven
 * through real {@link Level} updates in the test map {@code fungus-test} (a honey fungus at x 288 and a false one at x 480 on an
 * empty floor) and in the shipped maps they are placed in. Times come from {@link TrapWarnings} through {@link ExpectedWarnings},
 * never from a literal. World Y grows downward: the floor line is y 320, a fungus footprint is y 304..320 and the cloud y 296..320.
 */
class HoneyFungusTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, FUNGUS_W = 24, FUNGUS_H = 16, CLOUD = 24;
    private static final float HONEY_X = 288, FALSE_X = 480, ZONE = 64, CLOUD_LEFT = FALSE_X, CLOUD_RIGHT = FALSE_X + CLOUD, CLOUD_TOP = FLOOR - CLOUD;
    /** The hero's body box as the game defines it: offset 18 in a 48 px frame, 11 wide, 22 tall, so the body's bottom is the frame's. */
    private static final float BODY_RIGHT_OFFSET = 29, BODY_W = 11, BODY_H = 22, STAND_Y = 272;
    private static final float HALF_PIXEL = .5f, TINY = .1f, GLOW_EPS = 1e-4f, MAX_GLOW_STEP = 0.01f;
    private static final int SETTLE_FRAMES = 3, SECONDS_FRAMES = 60, LONG_WAIT_FRAMES = 60 * 6;
    /** forest-0 (room 0 and room 2) and forest-2 (forest-canopy, room 1) as built: footprint x, zone sides. */
    private static final float LEVER_X = 150, LEVER_ZONE = 40, WALL_X = 1804, WALL_ZONE_LEFT = 24, WALL_ZONE_RIGHT = 48, CANOPY_X = 1672;
    /** The nearest other hazards in forest-canopy, from the map: the RedMob at 1472 patrols to 1472 + 16 + 48; the bat at 1760 hurts from 1752; the vine at 1896. */
    private static final float BRANCH_ZONE_RIGHT = 1776, CANOPY_MOB_PATROL_RIGHT = 1536, CANOPY_BAT_LEFT = 1752, CANOPY_VINE_X = 1896;
    private static final float CANOPY_START_RIGHT = 1625, CANOPY_PASSED_LEFT = 1705, NO_JUMP_GAP = -1000;
    private static final int CANOPY_ROOM = 1;
    private static final float CANOPY_ENTRANCE_X = 1344;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); setLonger(false); }
    @AfterEach void tearDown() { setLonger(false); harness.restoreGlobals(); }

    private void setLonger(boolean longer) {
        try {
            var field = TrapWarnings.class.getDeclaredField("longer");
            field.setAccessible(true);
            field.setBoolean(harness.game().trapWarnings(), longer);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private Player player() { return new Player(harness.assets(), harness.game(), 0, 0); }
    private Level level(String map) { return new Level(harness.assets(), harness.game(), player(), map, Rectangle::new); }
    private Level fresh() { return level("fungus-test"); }
    private Level room(String map, int index) { return harness.roomOf(map, index); }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    /** Stands the hero with its body's right edge at {@code right} on the floor, still. */
    private void place(Level level, float right) { level.player.position.set(right - BODY_RIGHT_OFFSET, STAND_Y); level.player.velocity().setZero(); harness.held.clear(); }
    private void retry(Level level) { level.resetCurrentRoom(player()); settle(level, 2); }

    private <T> T one(Level level, Class<T> type) {
        return level.objects.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
    private HoneyFungus honey(Level level) { return one(level, HoneyFungus.class); }
    private FalseHoneyFungus fake(Level level) { return one(level, FalseHoneyFungus.class); }
    private long count(Level level, Class<?> type) { return level.objects.stream().filter(type::isInstance).count(); }

    /** Frames from the trigger until the fungus leaves its swell, with the hero standing at {@code right}. */
    private int swellFrames(Level level, FungusTrap f, float right) {
        place(level, right);
        int frames = 0;
        while (f.state() != FungusTrap.State.SWELL && frames++ < SECONDS_FRAMES) frame(level);
        assertEquals(FungusTrap.State.SWELL, f.state(), "the zone started the swell");
        frames = 0;
        while (f.state() == FungusTrap.State.SWELL && frames++ < LONG_WAIT_FRAMES) frame(level);
        return frames;
    }

    // -- the objects and where they stand --

    @Test void theFixtureHoldsOneOfEachAndBothStandOnTheFloorLine() {
        Level level = fresh();
        assertEquals(1, count(level, HoneyFungus.class));
        assertEquals(1, count(level, FalseHoneyFungus.class));
        for (FungusTrap f : new FungusTrap[]{honey(level), fake(level)}) {
            assertEquals(FungusTrap.State.IDLE, f.state());
            assertEquals(FLOOR, f.getY() + f.getHeight(), 0f, "footprint bottom edge is the floor line (Y grows downward)");
            assertEquals(FUNGUS_H, f.getHeight(), 0f);
        }
        Rectangle cloud = fake(level).cloudBounds();
        assertEquals(new Rectangle(CLOUD_LEFT, CLOUD_TOP, CLOUD, CLOUD), cloud, "centred on the mushroom, drawn size");
        assertEquals(FLOOR, cloud.y + cloud.height, 0f, "the cloud stands on the floor line, not below it");
        assertTrue(cloud.y < fake(level).getY(), "and reaches higher on screen than the cap");
        assertFalse(LethalHazard.class.isAssignableFrom(HoneyFungus.class), "the harmless one has no hitbox at all");
        assertInstanceOf(LethalHazard.class, fake(level));
        assertEquals(new Rectangle(HONEY_X - ZONE, FLOOR - FUNGUS_H - 32, FUNGUS_W + 2 * ZONE, FUNGUS_H + 32), honey(level).triggerZone());
    }

    @Test void theZoneStartsTheSwellAtItsBoundaryAndNotOnePixelBefore() {
        for (float edge : new float[]{HONEY_X - ZONE, FALSE_X - ZONE}) {
            Level level = fresh();
            FungusTrap f = edge == HONEY_X - ZONE ? honey(level) : fake(level);
            place(level, edge - HALF_PIXEL);
            settle(level, SECONDS_FRAMES * 3);
            assertEquals(FungusTrap.State.IDLE, f.state(), "just outside the zone never triggers, edge " + edge);
            place(level, edge + HALF_PIXEL);
            frame(level);
            assertEquals(FungusTrap.State.SWELL, f.state(), "half a pixel inside triggers, edge " + edge);
        }
    }

    @Test void aHeroWhoCannotBeHurtDoesNotTrigger() {
        Level level = fresh();
        place(level, HONEY_X + FUNGUS_W / 2f);
        level.player.collidedWithActor(false); // dying: canLeaveRoom() is false
        settle(level, SETTLE_FRAMES);
        assertFalse(level.player.canLeaveRoom());
        assertEquals(FungusTrap.State.IDLE, honey(level).state());
    }

    // -- the telegraph --

    @Test void theSwellIsExactlyTheSharedWarningNormallyAndWithTheAssist() {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level a = fresh(), b = fresh();
            int honeyFrames = swellFrames(a, honey(a), HONEY_X - ZONE + 2);
            int falseFrames = swellFrames(b, fake(b), FALSE_X - ZONE + 2);
            float expected = ExpectedWarnings.standard(longer);
            assertEquals(expected, honey(a).warningTotal(), 1e-6f, "honey, longer=" + longer);
            assertEquals(expected, fake(b).warningTotal(), 1e-6f, "false, longer=" + longer);
            assertEquals(expected * SECONDS_FRAMES, honeyFrames, 2, "honey swell frames, longer=" + longer);
            assertEquals(expected * SECONDS_FRAMES, falseFrames, 2, "false swell frames, longer=" + longer);
            assertEquals(FungusTrap.State.BURST, honey(a).state());
            assertEquals(FungusTrap.State.BURST, fake(b).state());
        }
    }

    @Test void theSwellShowsTheTwoSwellFramesInOrderAndTheIdleFramesOnlyBeforeIt() {
        Level level = fresh();
        FalseHoneyFungus f = fake(level);
        assertEquals(FungusTrap.FRAME_IDLE, f.frameIndex());
        place(level, FALSE_X - ZONE + 2);
        frame(level);
        assertEquals(FungusTrap.FRAME_SWELL_FIRST, f.frameIndex());
        while (f.state() == FungusTrap.State.SWELL && f.stateTime() < f.warningTotal() / 2f) frame(level);
        assertEquals(FungusTrap.FRAME_SWELL_SECOND, f.frameIndex());
        while (f.state() == FungusTrap.State.SWELL) frame(level);
        assertEquals(FungusTrap.FRAME_SPENT, f.frameIndex(), "deflated once it has burst");
    }

    // -- the lethal cloud --

    @Test void nothingKillsDuringTheSwellThenTheCloudKillsAHeroStandingInItTheFrameItBursts() {
        Level level = fresh();
        FalseHoneyFungus f = fake(level);
        place(level, CLOUD_LEFT + CLOUD / 2f + BODY_W / 2f); // body centred under the cloud
        int frames = 0;
        while (f.state() != FungusTrap.State.BURST && frames++ < LONG_WAIT_FRAMES) {
            assertTrue(alive(level), "alive during the swell, frame " + frames);
            frame(level);
        }
        assertEquals(FungusTrap.State.BURST, f.state());
        assertFalse(alive(level), "the live cloud kills the body inside it in the frame it appears");
        assertEquals(ExpectedWarnings.standard(false) * SECONDS_FRAMES, frames, 2);
    }

    @Test void aHeroBesideTheCloudIsNeverHurtAndTheCloudIsLiveForExactlyItsSeconds() {
        Level level = fresh();
        FalseHoneyFungus f = fake(level);
        place(level, CLOUD_RIGHT + TINY + BODY_W); // body left edge 0.1 px right of the cloud
        int live = 0;
        for (int i = 0; i < LONG_WAIT_FRAMES; i++) {
            frame(level);
            if (f.cloudLive()) live++;
            assertTrue(alive(level), "beside the cloud, frame " + i);
        }
        assertEquals(FalseHoneyFungus.CLOUD_SECONDS * SECONDS_FRAMES, live, 2);
        Level left = fresh();
        place(left, CLOUD_LEFT - TINY); // body right edge 0.1 px left of the cloud, inside the zone
        settle(left, LONG_WAIT_FRAMES);
        assertTrue(alive(left));
        assertEquals(FungusTrap.State.SPENT, fake(left).state());
    }

    @Test void theLethalRectangleIsTheDrawnCloudAndNothingElse() {
        Level level = fresh();
        FalseHoneyFungus f = fake(level);
        Rectangle inside = new Rectangle(CLOUD_LEFT + HALF_PIXEL, CLOUD_TOP + HALF_PIXEL, BODY_W, BODY_H);
        assertFalse(f.lethalTo(inside), "not before the burst");
        place(level, FALSE_X - ZONE + 2);
        while (!f.cloudLive()) frame(level);
        assertTrue(f.lethalTo(inside));
        assertFalse(f.lethalTo(new Rectangle(CLOUD_LEFT, CLOUD_TOP - BODY_H, BODY_W, BODY_H)), "feet exactly on the cloud's top edge: a jump clears it");
        assertTrue(f.lethalTo(new Rectangle(CLOUD_LEFT, CLOUD_TOP - BODY_H + TINY, BODY_W, BODY_H)), "feet 0.1 px into the cloud");
        assertFalse(f.lethalTo(new Rectangle(CLOUD_LEFT, FLOOR, BODY_W, BODY_H)), "below the floor line is outside the cloud");
        assertFalse(f.lethalTo(new Rectangle(CLOUD_RIGHT, CLOUD_TOP, BODY_W, BODY_H)), "right of the cloud");
        assertFalse(f.lethalTo(new Rectangle(CLOUD_LEFT - BODY_W, CLOUD_TOP, BODY_W, BODY_H)), "left of the cloud");
        assertTrue(f.everLethalIn(inside));
    }

    @Test void whenTheCloudHasGoneItIsSpentForGoodAndNeverArmsAgain() {
        Level level = fresh();
        FalseHoneyFungus f = fake(level);
        place(level, FALSE_X - ZONE + 2);
        settle(level, LONG_WAIT_FRAMES);
        assertEquals(FungusTrap.State.SPENT, f.state());
        assertEquals(FungusTrap.FRAME_SPENT, f.frameIndex());
        place(level, CLOUD_LEFT + CLOUD / 2f + BODY_W / 2f); // leave and come back, straight into where the cloud was
        settle(level, LONG_WAIT_FRAMES);
        assertTrue(alive(level), "no ammunition, no re-arming (D26)");
        assertEquals(FungusTrap.State.SPENT, f.state());
        assertFalse(f.lethalTo(level.player.hitboxBounds()));
    }

    @Test void leavingTheZoneDuringTheSwellDoesNotCancelIt() {
        Level level = fresh();
        FalseHoneyFungus f = fake(level);
        place(level, FALSE_X - ZONE + 2);
        frame(level);
        place(level, 80);
        settle(level, LONG_WAIT_FRAMES);
        assertEquals(FungusTrap.State.SPENT, f.state(), "it ran its whole sequence");
        assertTrue(alive(level));
    }

    // -- the harmless burst --

    @Test void theHarmlessBurstRisesNeverHarmsAndEndsSpent() {
        Level level = fresh();
        HoneyFungus h = honey(level);
        place(level, HONEY_X + FUNGUS_W / 2f + BODY_W / 2f); // standing in the middle of it
        int peak = 0;
        float topOfSpores = Float.MAX_VALUE, startLine = h.getY();
        for (int i = 0; i < LONG_WAIT_FRAMES; i++) {
            frame(level);
            assertTrue(alive(level), "golden spores never hurt, frame " + i);
            peak = Math.max(peak, h.spores().count());
            for (int p = 0; p < h.spores().count(); p++) topOfSpores = Math.min(topOfSpores, h.spores().y(p));
        }
        assertEquals(HoneyFungus.SPORES_PER_BURST, peak, "the whole burst is live at once");
        assertTrue(topOfSpores < startLine, "world Y grows downward: the spores rise, so their Y gets smaller than the cap's top");
        assertEquals(0, h.spores().count(), "all gone");
        assertEquals(FungusTrap.State.SPENT, h.state());
        assertEquals(FungusTrap.FRAME_SPENT, h.frameIndex());
        assertTrue(HoneyFungus.SPORE_LIFE_MAX * SECONDS_FRAMES >= 0);
    }

    @Test void theBurstSporesAreDeterministicForThePlace() {
        Level a = fresh(), b = fresh();
        place(a, HONEY_X + FUNGUS_W / 2f); place(b, HONEY_X + FUNGUS_W / 2f);
        for (int i = 0; i < SECONDS_FRAMES * 2; i++) {
            frame(a); frame(b);
            assertEquals(honey(a).spores().count(), honey(b).spores().count());
            for (int p = 0; p < honey(a).spores().count(); p++) assertEquals(honey(a).spores().x(p), honey(b).spores().x(p), 0f);
        }
    }

    // -- idle, pause, retry --

    @Test void theIdleIsASmoothDeterministicGlowOnTheClockAlone() {
        float period = FungusTrap.GLOW_PERIOD_SECONDS;
        assertEquals(FungusTrap.GLOW_MIN, FungusTrap.glow(0f), GLOW_EPS, "dimmest at the start");
        assertEquals(1f, FungusTrap.glow(period / 2f), GLOW_EPS, "brightest half a period in");
        assertEquals(FungusTrap.glow(0.3f), FungusTrap.glow(0.3f + period), GLOW_EPS, "periodic");
        Level a = fresh(), b = fresh();
        place(a, 100); place(b, 400); // the hero's place changes nothing outside the zone
        float low = 2f, high = 0f, previous = honey(a).brightness();
        for (int i = 0; i < SECONDS_FRAMES * 10; i++) {
            frame(a); frame(b);
            float v = honey(a).brightness();
            assertEquals(FungusTrap.FRAME_IDLE, honey(a).frameIndex(), "one fixed frame: no blinking");
            assertEquals(v, honey(b).brightness(), 0f);
            assertEquals(v, fake(a).brightness(), 0f, "the pair glow identically: only the swell tells them apart");
            assertTrue(Math.abs(v - previous) <= MAX_GLOW_STEP, "smooth: step " + Math.abs(v - previous) + " at frame " + i);
            assertTrue(v >= FungusTrap.GLOW_MIN - GLOW_EPS && v <= 1f + GLOW_EPS);
            low = Math.min(low, v); high = Math.max(high, v); previous = v;
        }
        assertTrue(low < FungusTrap.GLOW_MIN + GLOW_EPS * 10 && high > 1f - GLOW_EPS * 10, "the glow really breathes: " + low + " .. " + high);
    }

    @Test void theGlowStopsOnceTheSwellStartsAndRetryRestartsItOnTheSameClock() {
        Level level = fresh();
        place(level, FALSE_X - ZONE + 2);
        frame(level);
        assertEquals(1f, fake(level).brightness(), 0f, "full brightness in the swell");
        settle(level, LONG_WAIT_FRAMES);
        assertEquals(1f, fake(level).brightness(), 0f, "and when spent");
        Level again = fresh();
        Level other = fresh();
        for (int i = 0; i < SECONDS_FRAMES; i++) { frame(again); frame(other); }
        assertEquals(honey(other).brightness(), honey(again).brightness(), 0f, "same clock time, same glow");
        assertEquals(FungusTrap.glow(honey(again).clock()), honey(again).brightness(), GLOW_EPS);
    }

    @Test void pausingFreezesTheSwellAndTheClockAndResumingContinuesFromTheSameState() {
        Level paused = fresh(), straight = fresh();
        place(paused, FALSE_X - ZONE + 2); place(straight, FALSE_X - ZONE + 2);
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        float stateTime = fake(paused).stateTime(), clock = fake(paused).clock();
        // KimeriaGame stops calling Level.update while paused, so a pause is a run of frames with no update.
        assertEquals(stateTime, fake(paused).stateTime(), 0f);
        assertEquals(clock, fake(paused).clock(), 0f);
        for (int i = 0; i < LONG_WAIT_FRAMES; i++) { frame(paused); frame(straight); }
        assertEquals(fake(straight).state(), fake(paused).state());
        assertEquals(fake(straight).clock(), fake(paused).clock(), 1e-4f);
    }

    @Test void retryRebuildsBothInTheirIdleStateWithTheCloudGone() {
        Level level = fresh();
        place(level, CLOUD_LEFT + CLOUD / 2f + BODY_W / 2f);
        settle(level, LONG_WAIT_FRAMES);
        assertFalse(alive(level), "died in the cloud");
        assertEquals(FungusTrap.State.SPENT, fake(level).state());
        retry(level);
        assertEquals(FungusTrap.State.IDLE, fake(level).state(), "retry re-arms the room (D3)");
        assertEquals(FungusTrap.State.IDLE, honey(level).state());
        assertFalse(fake(level).cloudLive());
        assertTrue(alive(level));
        assertEquals(0, honey(level).spores().count());
    }

    // -- authoring --

    @Test void aFalseFungusOverTheEntranceOrWithoutOpenFloorFailsAtLoad() {
        var entrance = assertThrows(IllegalArgumentException.class, () -> level("fungus-entrance"));
        assertTrue(entrance.getMessage().contains("FalseHoneyFungus") && entrance.getMessage().contains("entrance"), entrance.getMessage());
        var pit = assertThrows(IllegalArgumentException.class, () -> level("fungus-pit"));
        assertTrue(pit.getMessage().contains("open floor"), pit.getMessage());
    }

    // -- the shipped maps --

    @Test void forest0HasTheHarmlessOnesInTheLeverRoomAndTheWallRoomAndNoLethalOne() {
        Level lever = room("forest-0", 0);
        HoneyFungus first = honey(lever);
        assertEquals(1, count(lever, HoneyFungus.class));
        assertEquals(0, count(lever, FalseHoneyFungus.class));
        assertEquals(new Rectangle(LEVER_X, FLOOR - FUNGUS_H, FUNGUS_W, FUNGUS_H), new Rectangle(first.getX(), first.getY(), first.getWidth(), first.getHeight()));
        assertEquals(LEVER_X - LEVER_ZONE, first.triggerZone().x, 0f);
        Level wall = room("forest-0", 2);
        assertEquals("forest-0-wall", wall.currentRoomName());
        HoneyFungus second = honey(wall);
        assertEquals(WALL_X, second.getX(), 0f);
        assertEquals(FLOOR, second.getY() + second.getHeight(), 0f);
        assertEquals(WALL_X - WALL_ZONE_LEFT, second.triggerZone().x, 0f);
        assertEquals(WALL_ZONE_LEFT + FUNGUS_W + WALL_ZONE_RIGHT, second.triggerZone().width, 0f);
        assertTrue(second.triggerZone().x > BRANCH_ZONE_RIGHT, "its zone does not share the falling branch's, so the two never fire together");
    }

    @Test void theHarmlessBurstInTheFirstRoomIsSeenFromTheEntranceAndHurtsNothing() {
        Level level = room("forest-0", 0);
        HoneyFungus h = honey(level);
        boolean[] seenBurst = new boolean[1];
        harness.held.add(Input.Keys.D);
        for (int i = 0; i < SECONDS_FRAMES * 8 && !h.isSpent(); i++) {
            frame(level);
            seenBurst[0] |= h.state() == FungusTrap.State.BURST;
            assertTrue(alive(level));
        }
        harness.held.clear();
        assertTrue(seenBurst[0], "a hero walking right from the entrance sets it off");
    }

    @Test void forest2PlacesTheFalseOneInForestCanopyWithEveryDistanceKept() {
        Level level = room("forest-2", CANOPY_ROOM);
        assertEquals("forest-canopy", level.currentRoomName());
        FalseHoneyFungus f = fake(level);
        assertEquals(0, count(level, HoneyFungus.class));
        assertEquals(new Rectangle(CANOPY_X, FLOOR - FUNGUS_H, FUNGUS_W, FUNGUS_H), new Rectangle(f.getX(), f.getY(), f.getWidth(), f.getHeight()));
        Rectangle cloud = f.cloudBounds();
        assertEquals(FLOOR, cloud.y + cloud.height, 0f);
        assertTrue(cloud.x - CANOPY_MOB_PATROL_RIGHT >= FungusTrap.CLEARANCE, "the RedMob's patrol is 48 px or more from the cloud");
        assertTrue(CANOPY_BAT_LEFT - (cloud.x + cloud.width) >= FungusTrap.CLEARANCE, "the bat is 48 px or more from the cloud");
        assertTrue(CANOPY_VINE_X - (cloud.x + cloud.width) >= FungusTrap.CLEARANCE, "so is the D59 stretch, which starts at the vine");
        Rectangle entrance = new Rectangle(CANOPY_ENTRANCE_X + 18, 272 + 26, BODY_W, BODY_H);
        assertTrue(cloud.x - (entrance.x + entrance.width) >= FungusTrap.CLEARANCE);
        assertTrue(f.triggerZone().x > CANOPY_MOB_PATROL_RIGHT, "the zone does not reach the RedMob's patrol");
    }

    @Test void forest1ForestEntryAndTheBossRoomAreUntouched() {
        assertEquals(0, count(room("forest-1", 0), FungusTrap.class));
        assertEquals(0, count(room("forest-2", 0), FungusTrap.class), "forest-entry has no free floor (docs/ROOMS.md)");
        assertEquals(0, count(room("forest-2", 2), FungusTrap.class));
        assertEquals(1, count(room("forest-2", CANOPY_ROOM), FungusTrap.class));
    }

    /** Whether a scripted hero, starting inside the false fungus's zone, is alive after walking past it; {@code delay} frames of waiting first. */
    private boolean passes(boolean longer, int delay, float jumpGap) {
        setLonger(longer);
        Level level = room("forest-2", CANOPY_ROOM);
        place(level, CANOPY_START_RIGHT);
        settle(level, 1);
        for (int f = 0; f < LONG_WAIT_FRAMES; f++) {
            Rectangle body = level.player.hitboxBounds();
            boolean go = f >= delay;
            boolean jump = go && level.player.isOnGround() && body.x + body.width >= CANOPY_X - jumpGap && body.x + body.width < CANOPY_X - jumpGap + 6;
            harness.held.clear();
            if (go) harness.held.add(Input.Keys.D);
            if (jump) { harness.held.add(Input.Keys.J); harness.pressed.add(Input.Keys.J); }
            frame(level);
            if (!alive(level)) return false;
            if (level.player.hitboxBounds().x > CANOPY_PASSED_LEFT) return true;
        }
        return false;
    }

    @Test void theFalseFungusOfForestCanopyHasTwoBypassesWaitingItOutAndJumpingTheLiveCloud() {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = room("forest-2", CANOPY_ROOM);
            place(level, CANOPY_START_RIGHT);
            settle(level, LONG_WAIT_FRAMES); // stands in its zone, left of the cloud, while it swells and bursts
            assertTrue(alive(level), "waiting beside it is safe, longer=" + longer);
            assertEquals(FungusTrap.State.SPENT, fake(level).state());
            harness.held.add(Input.Keys.D);
            int frames = 0;
            while (alive(level) && level.player.hitboxBounds().x < CANOPY_PASSED_LEFT && frames++ < LONG_WAIT_FRAMES) frame(level);
            harness.held.clear();
            assertTrue(alive(level) && level.player.hitboxBounds().x >= CANOPY_PASSED_LEFT, "and once spent the way is free, longer=" + longer);
        }
        setLonger(false);
        int survivors = 0;
        StringBuilder windows = new StringBuilder();
        for (int delay = 0; delay <= SECONDS_FRAMES * 2; delay += 6) {
            for (float gap : new float[]{14, 22, 30}) {
                if (passes(false, delay, gap)) { survivors++; windows.append(delay).append('/').append((int) gap).append(' '); }
            }
        }
        System.out.println("false fungus jump route: surviving delay/gap pairs " + windows);
        assertTrue(survivors >= 3, "jumping the live cloud works from several take-offs: " + survivors);
        int walkersKilled = 0, walkersThrough = 0;
        for (int delay = 0; delay <= SECONDS_FRAMES * 2; delay += 6) {
            if (passes(false, delay, NO_JUMP_GAP)) walkersThrough++; else walkersKilled++;
        }
        System.out.println("false fungus walkers without a jump: " + walkersThrough + " through, " + walkersKilled + " killed");
        assertTrue(walkersKilled > 0, "a walker who reaches the cloud while it is live dies, so the lesson has teeth");
        assertTrue(walkersThrough > 0, "and one who waits for it to pass walks through");
    }
}
