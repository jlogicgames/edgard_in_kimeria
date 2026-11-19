package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The teaching room of the hanging hive, {@code forest-1-hive} (#365, D90, D72): room 7 of forest-1, after the guard room.
 * A hive over open floor (with a stone near the entrance), then a hive over the ladder up to the exit ledge. Scripted heroes
 * with the ordinary controls measure the mastered route (D19), sweep every start offset (D26) and the optional knock-downs.
 * World Y grows downward.
 */
class Forest1HiveTest {
    private static final float STEP = 1f / 60f;
    private static final String HIVE_ROOM = "forest-1-hive";
    private static final float LEDGE_TOP = 208f;
    /** The climber's cue: start up the ladder right after a curtain, while the cycle is still this young. */
    private static final float CLIMB_CUE_SECONDS = 1.2f;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() throws Exception { h.setUp(); setLonger(false); }

    @AfterEach void restoreGlobals() throws Exception { setLonger(false); h.restoreGlobals(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    /** mob, deadfall, traps, lever, bramble, route, guard, then the hive room (#375, #363, #418, #364, #365). */
    private Level room() {
        Level level = h.room("forest-1", HIVE_ROOM);
        assertEquals("forest-1-hive", level.currentRoomName());
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    private Rectangle body(Level level) { return level.player.hitboxBounds(); }

    private static List<HangingHive> hives(Level level) {
        return level.objects.stream().filter(HangingHive.class::isInstance).map(HangingHive.class::cast)
            .sorted(Comparator.comparingDouble(HangingHive::getX)).toList();
    }

    private static boolean dangerous(HangingHive h) { return h.bandActive() || h.currentPhase() != HangingHive.Phase.CALM; }

    // -- the room --

    @Test void theRoomHoldsTwoHivesALadderAStoneAndNothingElseThatActs() {
        Level level = room();
        List<HangingHive> hives = hives(level);
        assertEquals(2, hives.size());
        HangingHive floor = hives.get(0), ladder = hives.get(1);
        assertEquals(320f, floor.floorY(), 0.01f);
        assertEquals(62f, floor.floorY() - floor.mouthY(), 0.01f, "the floor hive's band");
        assertEquals(320f, ladder.floorY(), 0.01f, "a ladder is not solid: the band runs down it to the floor");
        assertEquals(198f, ladder.floorY() - ladder.mouthY(), 0.01f);
        Ladder l = level.objects.stream().filter(Ladder.class::isInstance).map(Ladder.class::cast).findFirst().orElseThrow();
        Rectangle band = ladder.columnInto(new Rectangle());
        assertTrue(band.x >= l.getX() && band.x + band.width <= l.getX() + l.getWidth(), "the band runs down the ladder");
        assertEquals(1, level.objects.stream().filter(Carryable.class::isInstance).count(), "one stone");
        Rectangle room = level.currentRoomBounds();
        float entrance = room.x + 32;
        assertTrue(floor.centerX() - entrance < 320, "the floor hive is in view from the entrance");
        assertTrue(ladder.centerX() - entrance < 640, "the ladder hive is in the same screen");
    }

    // -- the route --

    /** Ordinary play: walk, wait outside each band while it is in the air or about to be, climb right after a curtain. Seconds, or -1 on a death. */
    private float route(Level level, float delay) {
        held.clear();
        settle(level, Math.round(delay * 60));
        List<HangingHive> hives = hives(level);
        HangingHive floor = hives.get(0), ladder = hives.get(1);
        float floorBand = floor.centerX() - HangingHive.BAND_WIDTH / 2f, ladderBand = ladder.centerX() - HangingHive.BAND_WIDTH / 2f;
        int frames = 0;
        boolean climbing = false;
        while (!level.atRoomExit()) {
            held.clear();
            Rectangle b = body(level);
            float right = b.x + b.width;
            if (!climbing && right <= floorBand - 2f) {
                if (!(right >= floorBand - 12f && dangerous(floor))) held.add(Input.Keys.D);
            } else if (!climbing && right < ladderBand - 6f) {
                held.add(Input.Keys.D);
            } else if (!climbing) {
                if (!dangerous(ladder) && ladder.cycleTime() <= CLIMB_CUE_SECONDS) { climbing = true; held.add(Input.Keys.D); }
            } else {
                held.add(Input.Keys.W);
                if (!level.player.onLadder()) held.add(Input.Keys.D);
                if (level.player.position.y + 48 <= LEDGE_TOP + 1 && !level.player.onLadder()) held.remove(Input.Keys.W);
            }
            frame(level);
            if (!alive(level)) return -1;
            assertTrue(++frames < 60 * 40, "stuck at " + level.player.position);
        }
        held.clear();
        return frames * STEP;
    }

    @Test void theMasteredRouteIsMeasuredAndEveryStartOffsetReachesTheExitWithAndWithoutTheAssist() throws Exception {
        float first = route(room(), 0f);
        assertTrue(first > 0, "the mastered route from the entrance");
        float slowest = 0, fastest = Float.MAX_VALUE;
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < 9f; delay += 0.1f) {
                float t = route(room(), delay);
                assertTrue(t > 0, "longer=" + longer + " delay " + delay + ": died");
                slowest = Math.max(slowest, t); fastest = Math.min(fastest, t);
            }
        }
        System.out.println("forest-1-hive route: from the entrance " + first + " s; over start offsets 0-9 s " + fastest + " to " + slowest + " s");
        assertTrue(slowest < 20f, "D19: the mastered route stays about 20 s at worst (" + slowest + ")");
    }

    @Test void aHeroWhoIgnoresTheHivesDiesSomewhereAndTheRetryCostsAtMostTheRoom() {
        int deaths = 0, atLadder = 0;
        float latest = 0, earliest = Float.MAX_VALUE;
        for (int k = 0; k < 45; k++) {
            float delay = k * 0.1f;
            Level level = room();
            held.clear();
            settle(level, Math.round(delay * 60));
            int frames = 0;
            while (alive(level) && !level.atRoomExit() && frames < 60 * 30) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                frame(level); frames++;
            }
            if (!alive(level)) {
                deaths++;
                float at = delay + frames * STEP;   // replayed from the room's entrance
                latest = Math.max(latest, at); earliest = Math.min(earliest, at);
                if (body(level).x + body(level).width > hives(level).get(1).centerX() - HangingHive.BAND_WIDTH) atLadder++;
            }
        }
        held.clear();
        System.out.println("forest-1-hive careless: " + deaths + " deaths in 45 start offsets, " + atLadder + " under the ladder hive, "
            + earliest + " to " + latest + " s after entering");
        assertTrue(deaths > 0, "the hives are lethal");
        assertTrue(latest < 20f, "a death replays at most the room");
    }

    // -- the optional answers --

    @Test void theStoneByTheEntranceKnocksTheFloorHiveDownWithAJumpThrowAndThePassIsFreeAfterwards() {
        int hits = 0;
        float near = Float.MAX_VALUE, far = 0;
        for (float from = 6330; from <= 6440; from += 4) {
            Level level = room();
            HangingHive floor = hives(level).get(0);
            Carryable stone = level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
            level.player.position.set(stone.getX() - 18, 272);
            level.player.velocity().setZero();
            level.player.facingRight = true;
            settle(level, 10);
            held.clear(); held.add(Input.Keys.W); frame(level);
            held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level); held.clear(); frame(level);
            assertSame(stone, level.player.carriedObject(), "picked up the stone");
            int f = 0;
            while (body(level).x + body(level).width < from && f++ < 600) { held.clear(); held.add(Input.Keys.D); frame(level); }
            held.clear(); settle(level, 20);
            held.add(Input.Keys.J); pressed.add(Input.Keys.J); frame(level); held.clear(); settle(level, 10);
            held.add(Input.Keys.L); pressed.add(Input.Keys.L); frame(level); held.clear();
            settle(level, 60);
            if (!floor.isKnocked() || !alive(level)) continue;
            hits++; near = Math.min(near, from); far = Math.max(far, from);
            settle(level, 120);
            for (int i = 0; i < 60 * 8; i++) { frame(level); assertFalse(floor.bandActive(), "no stinger after the knock-down"); }
        }
        System.out.println("forest-1-hive stone: " + hits + " of 28 jump-throws knock the floor hive down (body right edge " + near + " to " + far + ")");
        assertTrue(hits >= 3, "the stone answer works in the real room (" + hits + ")");
    }

    @Test void aFireballAtTheTopOfAJumpKnocksTheFloorHiveDownForOneCharge() {
        int hits = 0;
        for (int lead = 6; lead <= 30; lead += 2) {
            Level level = room();
            HangingHive floor = hives(level).get(0);
            level.player.position.set(6360 - 29, 272);
            level.player.velocity().setZero();
            level.player.facingRight = true;
            settle(level, 20);
            int ammo = level.rangedStock.count();
            if (ammo == 0) level.rangedStock.refill();
            ammo = level.rangedStock.count();
            held.add(Input.Keys.J); pressed.add(Input.Keys.J); frame(level); held.clear(); settle(level, lead);
            held.add(Input.Keys.L); pressed.add(Input.Keys.L); frame(level); held.clear();
            assertEquals(ammo - 1, level.rangedStock.count(), "one charge is paid");
            settle(level, 60);
            if (floor.isKnocked()) {
                hits++;
                assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance), "the fireball is spent");
            }
        }
        System.out.println("forest-1-hive fireball: " + hits + " of 13 jump-fires knock the floor hive down");
        assertTrue(hits >= 1, "a jump-fire reaches the floor hive");
    }
}
