package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.testsupport.HeroBody;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wolf's develop (#525, D158): the stomp rule, the landing read, the jump bite, the jump back and the held lunge, the assist,
 * the leaps on a route and their load checks, through real Level updates.
 *
 * <p>wolf-long.tmx: floor y 320, the wolf spawns at x 640 facing left with a range of x 480..896. wolf-route.tmx: three floor legs,
 * x 400..640 and (over a 32 px gap) x 672..880 at y 320, then a 16 px step up to x 880..1104 at y 304; the wolf starts on the
 * left. World Y grows downward. An airborne hero is placed by hand with a velocity, as a jump would leave him.
 */
class WolfDevelopTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, UPPER = 304;
    private static final int SECOND = 60;
    /** The hero's frame height, and his body's width and run speed. */
    private static final float HERO_FRAME = 48, HERO_WIDTH = HeroBody.W, RUN = Player.MOVE_SPEED;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private static Wolf wolf(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Wolf w) return w;
        return null;
    }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }
    private void press(Level level, int key) { harness.held.add(key); harness.pressed.add(key); frame(level); harness.held.remove(key); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(harness.game().trapWarnings(), longer);
    }

    private int until(Level level, java.util.function.BooleanSupplier condition, int limit) {
        for (int f = 0; f < limit; f++) {
            if (condition.getAsBoolean()) return f;
            frame(level);
        }
        fail("condition never held in " + limit + " frames");
        return -1;
    }

    private Level open() { return harness.roomOf("wolf-long", 0); }
    private Level routed() { return harness.roomOf("wolf-route", 0); }

    /** Stands the hero (body centre at {@code x}) on the floor line {@code floor}, still, facing right. */
    private void stand(Level level, float x, float floor) {
        level.player.position.set(x - HeroBody.CENTRE, floor - HERO_FRAME);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        level.player.setOnGround(true);
        harness.held.clear();
    }

    /** The hero in the air: body centre at {@code x}, feet {@code up} px above the floor, moving ({@code vx}, {@code vy}), steering so. */
    private void leapHero(Level level, float x, float up, float vx, float vy) {
        level.player.position.set(x - HeroBody.CENTRE, FLOOR - up - HERO_FRAME);
        level.player.velocity().set(vx, vy);
        level.player.setOnGround(false);
        steer(vx);
    }

    private void steer(float vx) {
        harness.held.remove(Input.Keys.A);
        harness.held.remove(Input.Keys.D);
        if (vx < 0) harness.held.add(Input.Keys.A);
        if (vx > 0) harness.held.add(Input.Keys.D);
    }

    private static float heroCentre(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }

    /** The wolf of wolf-long running left at the hero, who stands ahead of it at x 590. */
    private Wolf chasing(Level level) {
        Wolf w = wolf(level);
        stand(level, 590, FLOOR);
        until(level, () -> w.state() == Wolf.State.CHASE && w.speed() > 100, SECOND * 2);
        return w;
    }

    // ---- the stomp rule --------------------------------------------------------------------------------------------------

    /** Drops the hero on to the wolf from 4 px above its body; true when it died. The hero bounces either way. */
    private boolean stomp(Level level, Wolf w) {
        Rectangle body = w.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - HeroBody.CENTRE, body.y - 1 - HERO_FRAME);
        level.player.velocity().set(0, 200);
        level.player.setOnGround(false);
        harness.held.clear();
        frame(level);
        assertTrue(level.player.velocity().y < 0, "he bounces off: " + w.state());
        assertTrue(alive(level));
        return w.isDefeated();
    }

    @Test void aStompOnATrottingSniffingNoticingChasingOrSkiddingWolfBouncesTheHeroOffWithNoKill() {
        for (Wolf.State state : new Wolf.State[]{Wolf.State.TROT, Wolf.State.SNIFF, Wolf.State.NOTICE, Wolf.State.CHASE, Wolf.State.SKID}) {
            Level level = open();
            Wolf w = wolf(level);
            switch (state) {
                case TROT -> { }
                case SNIFF -> until(level, () -> w.state() == Wolf.State.SNIFF, SECOND * 20);
                case NOTICE -> { stand(level, 590, FLOOR); frame(level); }
                case CHASE -> chasing(level);
                default -> {
                    chasing(level);
                    stand(level, w.centreX() + 150, FLOOR);
                    until(level, () -> w.state() == Wolf.State.SKID, SECOND);
                }
            }
            assertEquals(state, w.state());
            assertFalse(w.stompKills(), state.toString());
            assertFalse(stomp(level, w), "no kill in " + state);
        }
    }

    @Test void aStompKillsItInItsWindUpLungeBiteAndRecovery() {
        for (Wolf.State state : new Wolf.State[]{Wolf.State.WINDUP, Wolf.State.LUNGE, Wolf.State.BITE, Wolf.State.RECOVER}) {
            Level level = open();
            Wolf w = wolf(level);
            stand(level, w.jawFrontX() - 60, FLOOR);
            until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
            if (state != Wolf.State.WINDUP) {
                stand(level, 100, FLOOR);
                until(level, () -> w.state() == state, SECOND * 2);
            }
            assertTrue(w.stompKills(), state.toString());
            assertTrue(stomp(level, w), "killed in " + state);
        }
    }

    /** wolf-perch.tmx: a 32 px one-way ledge at x 544..736, y 288, inside the wolf's range. */
    private Wolf bayedUnder(Level level, float x) {
        Wolf w = wolf(level);
        stand(level, x, 288);
        until(level, () -> w.state() == Wolf.State.BAY, SECOND * 6);
        return w;
    }

    @Test void aBayedWolfStaysBayedWhileItStepsUnderThePerchedHeroAndAStompKillsIt() {
        Level level = harness.roomOf("wolf-perch", 0);
        Wolf w = bayedUnder(level, 640);
        stand(level, 680, 288);   // he walks along the ledge: it follows
        until(level, () -> w.state() == Wolf.State.CHASE, SECOND);
        assertTrue(w.stompKills(), "bayed still, in its small steps");
        assertTrue(stomp(level, w));
    }

    @Test void aBayedWolfStartsNothingAtAHeroWhoJumpsOnItsPerch() {
        Level level = harness.roomOf("wolf-perch", 0);
        Wolf w = bayedUnder(level, 640);
        press(level, Input.Keys.J);
        for (int f = 0; f < SECOND / 2; f++) {
            frame(level);
            assertEquals(Wolf.State.BAY, w.state());
        }
    }

    @Test void itNeverBaysUnderAHeroInTheAir() {
        Level level = open();
        Wolf w = chasing(level);
        for (int f = 0; f < SECOND * 2; f++) {
            leapHero(level, 520, 80, 0, 0);   // hung in the air, past every move's reach of this one jump
            frame(level);
            assertNotEquals(Wolf.State.BAY, w.state());
        }
    }

    // ---- the landing read ------------------------------------------------------------------------------------------------

    @Test void itReadsAJumpFromItsChaseForAQuarterSecondThenRunsShortOfTheLandingAndWaits() {
        Level level = open();
        Wolf w = chasing(level);
        leapHero(level, w.centreX() - 140, 40, 60, -150);
        frame(level);
        assertEquals(Wolf.State.READ, w.state());
        assertTrue(harness.sounds.contains("wolf_read"), "seen and heard");
        float locked = w.readTargetX();
        settle(level, 3);
        assertTrue(level.wolfFx().hard().count() > 0, "the marks where he will land");
        int tell = 3;
        while (w.state() == Wolf.State.READ) { frame(level); tell++; assertTrue(tell < SECOND); }
        assertEquals(TrapWarnings.WOLF_READ_SECONDS, tell * STEP, STEP * 1.5f);
        assertEquals(Wolf.State.CHASE, w.state());
        assertEquals(locked, w.readTargetX(), .001f, "the spot is locked at the read");
    }

    /** The jump leaves from where the hero is; he steers back mid-air: the wolf still goes to the spot it locked. */
    @Test void theReadSpotIsWhereHeWouldLandAtThatSpeedAndSteeringAwayDoesNotMoveIt() {
        Level level = open();
        Wolf w = chasing(level);
        float from = w.centreX() - 140;
        leapHero(level, from, 40, RUN, -100);
        frame(level);
        float locked = w.readTargetX();
        float g = level.player.gravityPerSecond();
        float t = (100 + (float) Math.sqrt(100 * 100 + 2 * g * 40)) / g;
        float spot = from + RUN * t;
        float muzzle = locked + (spot > w.centreX() ? 47 : -1);
        assertEquals(spot + (spot > w.centreX() ? -1 : 1) * (HERO_WIDTH / 2 + Wolf.READ_STAND_OFF), muzzle, 3f, "36 px short of the landing");
        for (int f = 0; f < 20; f++) { steer(-RUN); frame(level); assertEquals(locked, w.readTargetX(), .001f); }
    }

    @Test void aHeroWhoLandsInTheLungeBoxAfterAReadMeetsAFullWindUp() {
        Level level = open();
        Wolf w = chasing(level);
        leapHero(level, w.centreX() - 140, 50, 0, -100);
        frame(level);
        harness.held.clear();
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        assertEquals(TrapWarnings.BITE_WARNING_SECONDS, w.warningTotal(), .001f);
        assertTrue(alive(level), "nothing struck him before the normal wind-up");
    }

    // ---- the jump bite ---------------------------------------------------------------------------------------------------

    /** The chasing wolf and a hero in its pounce box, ahead of it, feet 30 px up, rising, moving {@code vx} px/s. */
    private Wolf pouncing(Level level, float vx) {
        Wolf w = chasing(level);
        leapHero(level, w.centreX() - 36, 30, vx, -150);
        frame(level);
        assertEquals(Wolf.State.POUNCE_COIL, w.state());
        return w;
    }

    @Test void itBitesAHeroInThePounceBoxAfterAQuarterSecondTellWithATwoTenthsLeap() {
        Level level = open();
        Wolf w = pouncing(level, 0);
        assertTrue(harness.sounds.contains("wolf_pounce"));
        int tell = 0;
        while (w.state() == Wolf.State.POUNCE_COIL) { frame(level); tell++; assertTrue(alive(level), "the tell is harmless"); }
        assertEquals(TrapWarnings.WOLF_POUNCE_SECONDS, tell * STEP, STEP * 1.5f);
        assertTrue(level.wolfFx().hard().count() > 0, "the glints along its arc");
        int flight = 0;
        while (w.state() == Wolf.State.POUNCE) { frame(level); flight++; assertTrue(flight < SECOND); }
        assertTrue(flight * STEP <= Wolf.POUNCE_SECONDS + STEP);
        settle(level, 2);
        assertFalse(alive(level), "a hero who does not steer is bitten");
        assertTrue(harness.sounds.contains("wolf_bite"), "hit or miss (D93)");
    }

    @Test void aHeroWhoTurnsBackInTheAirEscapesTheJumpBiteThenItRecoversAndAStompKillsIt() {
        for (float vx : new float[]{-100, 100}) {
            Level level = open();
            Wolf w = pouncing(level, vx);
            steer(-vx);
            until(level, () -> w.state() == Wolf.State.RECOVER, SECOND * 2);
            assertTrue(alive(level), "turning back at " + vx + " px/s clears the locked arc");
            assertTrue(w.stompKills(), "the free counter");
        }
    }

    @Test void aHeroWhoStopsInTheAirEscapesTheJumpBite() {
        for (float vx : new float[]{-100, 100}) {
            Level level = open();
            Wolf w = pouncing(level, vx);
            steer(0);
            until(level, () -> w.state() == Wolf.State.RECOVER, SECOND * 2);
            assertTrue(alive(level), "stopping from " + vx + " px/s clears the locked arc");
        }
    }

    @Test void aStompOnTheJumpBiteBouncesHimUnharmedAndNothingElseStartsUntilHeStands() {
        Level level = open();
        Wolf w = pouncing(level, 0);
        assertFalse(w.stompKills());
        assertFalse(stomp(level, w), "a bounce");
        for (int f = 0; f < SECOND * 2 && w.state() != Wolf.State.RECOVER; f++) {
            frame(level);
            assertNotEquals(Wolf.State.READ, w.state(), "the bounce was this jump's answer");
            assertTrue(alive(level), "the leap it had begun does not reach him");
        }
        assertEquals(Wolf.State.RECOVER, w.state());
        assertTrue(alive(level));
    }

    /** A chasing wolf moved to {@code wolfX}, the hero in the air at {@code heroX}; two seconds on, with him gone. */
    private void leapAtHim(Level level, Wolf w, float wolfX, float heroX, float up, float vx, float vy) {
        w.position.x = wolfX;
        w.facingRight = false;
        leapHero(level, heroX, up, vx, vy);
        frame(level);
        for (int f = 0; f < SECOND * 2 && (w.state() == Wolf.State.POUNCE_COIL || w.state() == Wolf.State.POUNCE || f < 40); f++) {
            if (f == 20) stand(level, 100, FLOOR);   // out of the way before it lands
            frame(level);
        }
    }

    /** wolf-block.tmx: a block at x 560..592, 32 px high, stands to the left of the wolf's range (x 592..896). */
    @Test void aJumpBiteNeverLandsItOnABlockBesideIt() {
        int answered = 0;
        for (float heroX = 550; heroX <= 640; heroX += 3)
            for (float vx : new float[]{-100, 0}) {
                Level level = harness.roomOf("wolf-block", 0);
                Wolf w = wolf(level);
                stand(level, 700, FLOOR);
                until(level, () -> w.state() == Wolf.State.CHASE && w.speed() > 50, SECOND * 2);
                leapAtHim(level, w, 592, heroX, 60, vx, -200);
                settle(level, SECOND);
                assertEquals(FLOOR, w.floorY(), .5f, "never on top of the block (hero at " + heroX + ", " + vx + ")");
                assertTrue(w.hitboxBounds().x >= 592 - .5f, "never past it");
                if (heroX > 620) answered++;
            }
        assertTrue(answered > 0);
    }

    /** wolf-route.tmx: from the left end of its middle leg, a hero over the 32 px gap is not pounced across it. */
    @Test void aJumpBiteNeverCarriesItAcrossAGapOfItsRoute() {
        for (float heroX = 640; heroX <= 700; heroX += 4)
            for (float vx : new float[]{-100, 0}) {
                Level level = routed();
                Wolf w = wolf(level);
                until(level, () -> w.leg() == 1 && w.state() == Wolf.State.TROT, SECOND * 20);
                stand(level, 790, FLOOR);
                w.position.x = 700;
                w.facingRight = true;
                until(level, () -> w.state() == Wolf.State.CHASE, SECOND * 2);
                leapAtHim(level, w, 668, heroX, 50, vx, -150);
                assertTrue(w.state() == Wolf.State.LEAP || w.state() == Wolf.State.LEAP_COIL || w.hitboxBounds().x >= 672 - .5f,
                    "it stays on its side of the gap (hero at " + heroX + ", " + vx + "): " + w.state() + " " + w.position.x);
            }
    }

    @Test void oneJumpIsAnsweredOnce() {
        Level level = open();
        Wolf w = chasing(level);
        leapHero(level, w.centreX() - 140, 40, 0, -100);
        frame(level);
        assertEquals(Wolf.State.READ, w.state());
        for (int f = 0; f < SECOND; f++) {
            leapHero(level, w.centreX() - 30, 40, 0, 0);   // still the same jump: he never stood
            frame(level);
            assertNotEquals(Wolf.State.POUNCE_COIL, w.state());
        }
    }

    @Test void theAssistTurnsTheReadAndTheJumpBiteOff() throws Exception {
        setLonger(true);
        Level level = open();
        Wolf w = chasing(level);
        for (float x : new float[]{w.centreX() - 140, w.centreX() - 36}) {
            leapHero(level, x, 40, 0, -100);
            frame(level);
            assertEquals(Wolf.State.CHASE, w.state());
        }
    }

    // ---- the jump back and the held lunge --------------------------------------------------------------------------------

    /** A wolf winding up at a hero {@code gap} px from its muzzle, facing it. */
    private Wolf windingUp(Level level, float gap) {
        Wolf w = wolf(level);
        stand(level, w.jawFrontX() - gap - HERO_WIDTH / 2, FLOOR);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        return w;
    }

    @Test void aSwingAtItsWindUpMakesItCoilAndHopFortyPixelsBackThenRecover() {
        Level level = open();
        Wolf w = windingUp(level, 60);
        float x = w.position.x;
        press(level, Input.Keys.K);
        until(level, () -> w.state() == Wolf.State.FLINCH, 2);
        assertTrue(harness.sounds.contains("wolf_flinch"));
        int coil = until(level, () -> w.state() == Wolf.State.HOP, SECOND);
        assertEquals(TrapWarnings.WOLF_FLINCH_SECONDS, coil * STEP, STEP * 1.5f);
        until(level, () -> w.state() == Wolf.State.RECOVER, SECOND);
        assertEquals(x + Wolf.HOP_REACH, w.position.x, .01f, "away from him");
        int recovery = until(level, () -> w.state() != Wolf.State.RECOVER, SECOND * 2);
        assertEquals(Wolf.HOP_RECOVER_SECONDS, recovery * STEP, STEP * 1.5f);
        assertTrue(alive(level));
        assertFalse(w.isDefeated(), "the blade never reached it");
    }

    /** From every gap up to the edge of its box: once the first swing is over there is time to step in and swing again. */
    @Test void theNextSwingInItsRecoveryKillsIt() {
        for (float gap : new float[]{30, 45, 60, 71}) {
            Level level = open();
            Wolf w = windingUp(level, gap);
            press(level, Input.Keys.K);
            until(level, () -> w.state() == Wolf.State.RECOVER, SECOND);
            until(level, () -> !level.player.isSwinging(), SECOND);
            float front = w.jawFrontX();
            harness.held.add(Input.Keys.D);
            until(level, () -> level.player.hitboxBounds().x + HERO_WIDTH >= front - 8, SECOND * 2);
            harness.held.clear();
            assertEquals(Wolf.State.RECOVER, w.state(), "there is time to step in from " + gap);
            settle(level, 6);   // a tenth of a second to see it and strike
            press(level, Input.Keys.K);
            settle(level, 3);
            assertTrue(w.isDefeated(), "gap " + gap);
            assertTrue(alive(level));
        }
    }

    @Test void theSecondSwingIsWaitedOutThenItWindsUpInFullAndTheThirdGetsTheHop() {
        Level level = open();
        Wolf w = windingUp(level, 60);
        press(level, Input.Keys.K);
        until(level, () -> w.state() == Wolf.State.RECOVER, SECOND);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        until(level, () -> !level.player.isSwinging(), SECOND);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        stand(level, w.jawFrontX() - 60, FLOOR);
        press(level, Input.Keys.K);
        until(level, () -> w.state() == Wolf.State.HOLD, 2);
        assertFalse(w.stompKills());
        int held = until(level, () -> w.state() != Wolf.State.HOLD, SECOND * 2);
        assertFalse(level.player.isSwinging(), "it waited the blade out (" + held + " frames)");
        assertEquals(Wolf.State.WINDUP, w.state());
        assertTrue(w.warningRemaining() > TrapWarnings.BITE_WARNING_SECONDS - STEP * 2, "a full new wind-up");
        assertEquals(2, w.swingsMet());
        press(level, Input.Keys.K);
        until(level, () -> w.state() == Wolf.State.FLINCH, 2);
        assertEquals(3, w.swingsMet());
    }

    /** wolf-wall.tmx: a solid at x 800..816 stands behind a wolf that faces left at x 750: there is no room to hop back. */
    @Test void aBlockedHopIsNotStartedSoTheBladeMeetsTheLunge() {
        Level level = harness.roomOf("wolf-wall", 0);
        Wolf w = wolf(level);
        w.position.x = 750;
        stand(level, w.jawFrontX() - 65, FLOOR);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        settle(level, 20);
        press(level, Input.Keys.K);
        for (int f = 0; f < SECOND; f++) { frame(level); assertNotEquals(Wolf.State.FLINCH, w.state()); }
        assertEquals(0, w.swingsMet());
        assertTrue(w.isDefeated(), "the blade was live when the jaws came");
    }

    @Test void aSwingFacingAwayIsNotMet() {
        Level level = open();
        Wolf w = windingUp(level, 60);
        level.player.facingRight = false;
        press(level, Input.Keys.K);
        settle(level, 3);
        assertEquals(Wolf.State.WINDUP, w.state());
    }

    @Test void theAssistTurnsTheJumpBackOff() throws Exception {
        setLonger(true);
        Level level = open();
        Wolf w = windingUp(level, 60);
        press(level, Input.Keys.K);
        settle(level, 3);
        assertEquals(Wolf.State.WINDUP, w.state());
    }

    /** The same inputs give the same states, frame for frame (D7): never a roll. */
    @Test void theSameMovesGiveTheSameOutcomeOnEveryRetry() {
        List<String> first = new ArrayList<>(), second = new ArrayList<>();
        for (List<String> trace : List.of(first, second)) {
            Level level = open();
            Wolf w = chasing(level);
            leapHero(level, w.centreX() - 36, 30, 60, -150);
            for (int f = 0; f < SECOND * 4; f++) {
                if (f % 90 == 45) { harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); }
                frame(level);
                harness.held.remove(Input.Keys.K);
                trace.add(w.state() + " " + w.position.x + " " + w.position.y + " " + alive(level));
            }
        }
        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(s -> s.startsWith("POUNCE")), "the scenario meets a move");
    }

    // ---- the route -------------------------------------------------------------------------------------------------------

    @Test void onItsRouteItTrotsAndLeapsTheGapAndTheStepBothWaysAfterACoil() {
        Level level = routed();
        Wolf w = wolf(level);
        assertEquals(0, w.leg());
        assertEquals(FLOOR, w.floorY(), .5f);
        List<Integer> legs = new ArrayList<>();
        int coil = 0, flight = 0;
        for (int f = 0; f < SECOND * 40; f++) {
            frame(level);
            if (w.state() == Wolf.State.LEAP_COIL) coil++;
            if (w.state() == Wolf.State.LEAP) { flight++; assertFalse(w.stompKills()); }
            if (legs.isEmpty() || legs.get(legs.size() - 1) != w.leg()) legs.add(w.leg());
            if (w.state() == Wolf.State.TROT) assertEquals(w.leg() == 2 ? UPPER : FLOOR, w.floorY(), .5f, "on its leg's floor");
        }
        assertEquals(List.of(0, 1, 2, 1, 0), legs.subList(0, 5));
        assertFalse(harness.sounds.contains("wolf_leap"), "out of the camera's view the claws are silent");
        assertEquals(TrapWarnings.WOLF_LEAP_SECONDS / Wolf.LEAP_SECONDS, (float) coil / flight, .1f, "a coil before every flight");
    }

    @Test void theClawsOfItsCoilAreHeardInView() {
        Level level = harness.roomOf("wolf-route", 0, () -> new Rectangle(0, 0, 1280, 368));
        Wolf w = wolf(level);
        until(level, () -> w.state() == Wolf.State.LEAP_COIL, SECOND * 20);
        assertTrue(harness.sounds.contains("wolf_leap"), harness.sounds.toString());
    }

    @Test void itChasesAHeroAcrossTheGapByLeapingAndNeverStrikesAcrossIt() {
        Level level = routed();
        Wolf w = wolf(level);
        until(level, () -> w.facingRight && w.state() == Wolf.State.TROT && w.position.x > 500, SECOND * 30);
        stand(level, 760, FLOOR);   // over the gap, on the middle leg
        boolean leapt = false;
        for (int f = 0; f < SECOND * 4 && !leapt; f++) {
            frame(level);
            if (w.leg() == 0) assertNotEquals(Wolf.State.WINDUP, w.state(), "no strike across a gap");
            leapt = w.leg() == 1;
        }
        assertTrue(leapt, "it came over: " + w.state());
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
    }

    @Test void aOneTileStepIsNoRefugeOnARoute() {
        Level level = routed();
        Wolf w = wolf(level);
        // The step's face is as high as its eyes: it hears a hero above it once it trots within 64 px of him.
        until(level, () -> w.leg() == 1 && w.facingRight && w.state() == Wolf.State.TROT && w.position.x > 820, SECOND * 40);
        stand(level, 940, UPPER);
        until(level, () -> w.leg() == 2, SECOND * 4);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
    }

    @Test void aStompOnItsLeapBouncesTheHeroOffAndEveryShotKillsItThere() {
        Level level = routed();
        Wolf w = wolf(level);
        until(level, () -> w.state() == Wolf.State.LEAP, SECOND * 20);
        settle(level, 11);   // past the top of its arc, coming down
        assertEquals(Wolf.State.LEAP, w.state());
        assertFalse(stomp(level, w));
        w.collidedWithActor(true);
        assertTrue(w.isDefeated());
    }

    @Test void aRoutedWolfLeftBehindComesBackToItsStart() {
        Level level = routed();
        Wolf w = wolf(level);
        float x = w.position.x;
        until(level, () -> w.leg() == 1, SECOND * 30);
        w.revisit(level.player);
        assertEquals(0, w.leg());
        assertEquals(x, w.position.x, .001f);
        assertEquals(FLOOR, w.floorY(), .5f);
        assertTrue(w.facingRight);
    }

    // ---- the route's load checks -----------------------------------------------------------------------------------------

    private static String trace(float... points) {
        try { Wolf.Route.trace(points); return null; } catch (IllegalArgumentException e) { return e.getMessage(); }
    }

    @Test void aRouteIsLevelFloorLegsJoinedByStepsAndGapsWithinItsReach() {
        assertNull(trace(0, 320, 100, 320));
        assertNull(trace(0, 320, 100, 320, 148, 320, 300, 320), "a 48 px gap");
        assertNull(trace(0, 320, 100, 320, 100, 304, 300, 304), "a 16 px step");
        assertNull(trace(300, 304, 100, 304, 100, 320, 0, 320), "drawn from the right");
        assertTrue(trace(0, 320, 100, 320, 149, 320, 300, 320).contains("gap wider"));
        assertTrue(trace(0, 320, 100, 320, 100, 303, 300, 303).contains("step higher"));
        assertTrue(trace(0, 320, 100, 320, 100, 320, 300, 320).contains("merge"));
        assertTrue(trace(0, 320, 100, 321).contains("not level"));
        assertTrue(trace(0, 320, 20, 320).contains("shorter than its body"));
        assertTrue(trace(0, 320, 100, 320, 80, 304, 300, 304).contains("turns back"));
        assertTrue(trace(0, 320, 100, 320, 120, 320).contains("even number"));
    }

    @Test void theRouteChecksRejectAShortRouteASolidInALeapAndAStandingPlaceInSight() {
        Rectangle room = new Rectangle(0, 0, 1280, 368);
        List<Rectangle> floors = List.of(new Rectangle(0, 320, 640, 48), new Rectangle(672, 320, 608, 48));
        float[] good = {400, 320, 640, 320, 672, 320, 880, 320};
        assertNull(Wolf.routeProblem(good, floors, List.of(), room, Map.of()));
        assertTrue(Wolf.routeProblem(new float[]{400, 320, 500, 320}, floors, List.of(), room, Map.of()).contains("at least 160"));
        // A pillar standing in the gap from below the floor: it hangs into no leg's headroom, only into the leap.
        String pillar = Wolf.routeProblem(good, floors, List.of(new Rectangle(648, 296, 16, 72)), room, Map.of());
        assertTrue(pillar != null && pillar.contains("in the way of its leap"), pillar);
        assertTrue(Wolf.routeProblem(good, floors, List.of(), room, Map.of("exit", new Rectangle(200, 288, 16, 32))).contains("exit"));
        assertTrue(Wolf.routeProblem(new float[]{400, 320, 660, 320, 672, 320, 880, 320}, floors, List.of(), room, Map.of()).contains("without gaps"));
    }
}
