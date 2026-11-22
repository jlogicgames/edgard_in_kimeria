package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest snail (#329, D106), driven headlessly through real Level updates with ordinary keyboard controls.
 *
 * <p>Fixture snail-test.tmx: room 640 wide, floor y 320, hero enters at x 32 (body x 50..61); the snail spawns at x 320 facing
 * left (body x 321..340), offNeg 3 and offPos 4, so its {@code position.x} stays in 272..384; a resting stone at x 96. Facing
 * left at its spawn its mouth is at x 321 and its firing line runs left to x 145, a band at y 311..315 (the gob's core centred
 * 7 px above the floor); its sight runs from x 321 to x 145, from y 272 to the floor. snail-wall.tmx adds a block at x 180..196,
 * y 288..320, which cuts both. The hero's body is 11x22, its right edge at {@code place}'s argument.
 */
class SnailTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, LEFT_END = 272, RIGHT_END = 384, MOUTH = 321;
    /** Hero body right edges: ahead in its line; behind it; far behind, out of its sight either way. */
    private static final float AHEAD = 250, BEHIND = 420, FAR_BEHIND = 600;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level level() { return harness.roomOf("snail-test", 0); }

    private static Snail snail(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Snail s) return s;
        return null;
    }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }

    /** Stands the hero (body right edge at {@code right}) on the floor, still, facing {@code right ? right : left}. */
    private void place(Level level, float right, boolean facingRight) {
        level.player.position.set(right - 29, FLOOR - 48);
        level.player.velocity().setZero();
        level.player.facingRight = facingRight;
        harness.held.clear();
    }

    private void place(Level level, float right) { place(level, right, true); }

    /** Holds the hero with its feet {@code lift} px above the floor, still: a jump's height, frame after frame. */
    private void hold(Level level, float right, float lift) {
        level.player.position.set(right - 29, FLOOR - 48 - lift);
        level.player.velocity().setZero();
    }

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

    /** The hero stands ahead in its line; it notices and winds up. */
    private Snail windingUp(Level level) {
        place(level, AHEAD);
        Snail s = snail(level);
        until(level, () -> s.state() == Snail.State.WINDUP, 60 * 2);
        return s;
    }

    /** Wound up and spat: the gob is in the air. */
    private Snail spat(Level level) {
        Snail s = windingUp(level);
        until(level, s::gobActive, 120);
        return s;
    }

    @Test void itCrawlsSlowlyPausesAtEachEndAndIsSilentWhenCalm() {
        Level level = level();
        Snail s = snail(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        boolean paused = false;
        float before = s.position.x;
        frame(level);
        assertEquals(Snail.PATROL_SPEED * STEP, before - s.position.x, .01f, "it crawls at 12 px/s");
        for (int f = 0; f < 60 * 30; f++) {
            frame(level);
            min = Math.min(min, s.position.x); max = Math.max(max, s.position.x);
            paused |= s.state() == Snail.State.PAUSE;
            assertTrue(s.state() == Snail.State.CRAWL || s.state() == Snail.State.PAUSE, s.state().toString());
        }
        assertEquals(LEFT_END, min, .01f, "reaches its left end");
        assertEquals(RIGHT_END, max, .01f, "reaches its right end");
        assertTrue(paused, "it stops at an end before it turns");
        assertTrue(harness.sounds.isEmpty(), "a calm snail is silent");
    }

    @Test void itPausesForPauseSecondsAtAnEndThenTurns() {
        Level level = level();
        Snail s = snail(level);
        until(level, () -> s.state() == Snail.State.PAUSE, 60 * 6);
        assertFalse(s.facingRight);
        int frames = until(level, () -> s.state() == Snail.State.CRAWL, 120);
        assertEquals(Snail.PAUSE_SECONDS, frames * STEP, STEP * 1.5f);
        assertTrue(s.facingRight, "it turns round");
    }

    @Test void itSeesOnlyAheadAndNoticesInBodyLanguage() {
        Level level = level();
        Snail s = snail(level);
        place(level, BEHIND);   // body 409..420: behind the snail facing left
        settle(level, 60);
        assertTrue(s.state() == Snail.State.CRAWL || s.state() == Snail.State.PAUSE, "a hero behind it is not seen");
        assertTrue(harness.sounds.isEmpty());

        Level ahead = level();
        Snail a = snail(ahead);
        place(ahead, AHEAD);
        frame(ahead);
        assertEquals(Snail.State.NOTICE, a.state());
        assertEquals(List.of("snail_notice"), harness.sounds, "its own call, no shared alert sound");
        float x = a.position.x;
        int frames = until(ahead, () -> a.state() != Snail.State.NOTICE, 60);
        assertEquals(Snail.NOTICE_TOTAL, frames * STEP, STEP * 1.5f, "the notice lasts 0.5 s");
        assertEquals(x, a.position.x, .01f, "it does not move while it notices");
    }

    @Test void touchIsHarmlessAndItNeverChases() {
        Level level = level();
        Snail s = snail(level);
        Rectangle body = s.hitboxBounds();
        place(level, body.x + 18);   // the hero's body overlaps the shell half of the calm snail, out of its line and sight
        settle(level, 60 * 3);
        assertTrue(alive(level), "touching a snail never kills: only the gob does");

        Level watched = level();
        Snail w = snail(watched);
        place(watched, 200);
        until(watched, () -> w.state() == Snail.State.WINDUP || w.state() == Snail.State.WATCH, 60);
        float x = w.position.x;
        settle(watched, 30);
        assertEquals(x, w.position.x, .01f, "aware, it holds still: it never chases");
    }

    @Test void itWindsUpForTheSharedWarningThenSpitsAGobThatKillsAHeroInItsLine() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = level();
            Snail s = windingUp(level);
            assertEquals(ExpectedWarnings.of(TrapWarnings.DEFAULT_WARNING_SECONDS, longer), s.warningTotal(), 1e-6f);
            assertTrue(harness.sounds.contains(longer ? "snail_windup_long" : "snail_windup"), harness.sounds.toString());
            assertFalse(s.facingRight, "it faces the hero");
            int toSpit = until(level, s::gobActive, 120);
            assertEquals(s.warningTotal(), toSpit * STEP, STEP * 1.5f, "it spits when the warning ends");
            assertTrue(harness.sounds.contains("snail_spit"));
            assertEquals(MOUTH, s.gobX(), 2.5f, "born at the mouth");
            assertEquals(FLOOR - Snail.GOB_HEIGHT, s.gobY(), .01f, "at shin height");
            float x = s.gobX();
            frame(level);
            assertEquals(Snail.GOB_SPEED * STEP, x - s.gobX(), .01f, "straight and level at 120 px/s");
            until(level, () -> !alive(level), 60);
            assertTrue(harness.sounds.contains("snail_splat"), "it splats on the hero");
            assertFalse(s.gobActive());
        }
    }

    @Test void aHeroAboveTheLineIsMissedAndTheGobDropsAndSplatsPastItsRange() {
        Level level = level();
        Snail s = spat(level);
        harness.sounds.clear();
        float lastX = s.gobX();
        while (s.gobActive()) {
            hold(level, AHEAD, 12);   // feet 12 px up: above the band (its top is 9 px up)
            lastX = s.gobX();
            frame(level);
        }
        assertTrue(alive(level), "a jump clears it");
        assertTrue(lastX < MOUTH - Snail.GOB_RANGE, "it flew its range, then dropped: " + lastX);
        assertTrue(lastX > MOUTH - Snail.GOB_RANGE - 30, "and splatted on the floor just past it: " + lastX);
        assertEquals(List.of("snail_splat"), harness.sounds, "seen and heard on a miss (D93)");
        assertTrue(level.snailFx().hard().count() > 0, "droplets and a smear");
    }

    @Test void theBladeCutsTheGobAndTheHeroLives() {
        Level level = level();
        Snail s = spat(level);
        place(level, AHEAD, true);
        harness.sounds.clear();
        until(level, () -> s.gobX() < AHEAD + 30, 60);
        press(level, Input.Keys.K);
        settle(level, 60);
        assertTrue(alive(level), "the sword cut it");
        assertFalse(s.gobActive());
        assertTrue(harness.sounds.contains("snail_splat"));
        assertFalse(s.isDefeated(), "the snail is far out of the sword's reach");
    }

    @Test void aBreakOnTheSameFrameAsTheGobsHitWins() {
        Level level = level();
        Snail s = spat(level);
        place(level, AHEAD);
        until(level, () -> s.gobX() - Snail.GOB_CORE / 2 <= AHEAD, 60);   // the gob reached the hero this frame: its hit is pending
        assertTrue(alive(level), "the hit lands on the snail's next frame, not at once");
        s.breakApart(false);   // the blade on the same frame
        settle(level, 10);
        assertTrue(alive(level), "the blade wins the tie");
    }

    /** #503: a fireball cast on the frame the gob reaches the hero burns it on its cast frame, before the hit lands. */
    @Test void aFireballCastOnTheFrameTheGobReachesTheHeroWinsTheTie() {
        Level probe = level();
        Snail p = spat(probe);
        place(probe, AHEAD);
        int due = until(probe, () -> p.gobX() - Snail.GOB_CORE / 2 <= AHEAD, 60);   // frames until the gob has reached the hero
        Level level = level();
        Snail s = spat(level);
        place(level, AHEAD);
        for (int f = 1; f < due; f++) frame(level);
        press(level, Input.Keys.L);   // cast on the very frame the gob arrives: its hit is pending, due on the next frame
        assertFalse(s.gobActive(), "the ball's first step burns the gob on its cast frame");
        settle(level, 10);
        assertTrue(alive(level), "the fireball wins the tie");
    }

    private void carryTheStone(Level level) {
        place(level, 108);
        harness.held.clear(); frame(level); harness.held.add(Input.Keys.W); frame(level); // Up alone interacts (D126)
        harness.held.clear(); frame(level);
        assertNotNull(level.player.carriedObject(), "picked up the stone");
    }

    /**
     * #505: on a shared floor the gob flies 7 px above the floor line ({@link Snail#GOB_HEIGHT}) and a stone leaves the hero's hands
     * above that band (box y 294..306 against the band's 311..315), so on the frame the gob arrives no stone is in its way, whatever the
     * frame order: this pins the geometry (true with or without the first-step rule). The tie is real from a ledge, below.
     */
    @Test void onASharedFloorAStoneThrownWhenTheGobArrivesIsAboveItsBand() {
        Level probe = level();
        carryTheStone(probe);
        Snail p = spat(probe);
        place(probe, AHEAD);
        int due = until(probe, () -> p.gobX() - Snail.GOB_CORE / 2 <= AHEAD, 60);
        Level level = level();
        carryTheStone(level);
        Snail s = spat(level);
        place(level, AHEAD);
        for (int f = 1; f < due; f++) frame(level);
        assertTrue(s.gobActive(), "the gob is in the air, its hit due on this frame");
        var stone = level.player.carriedObject();
        press(level, Input.Keys.L);
        assertNull(level.player.carriedObject(), "the stone was thrown");
        assertTrue(stone.bounds().y + stone.bounds().height < s.gobY() - Snail.GOB_CORE / 2, "above the gob's band");
        assertTrue(s.gobActive(), "so the gob is not broken on this frame");
        settle(level, 5);
        assertFalse(alive(level), "and its hit lands");
    }

    /** Holds the snail {@code lift} px above the floor each frame (it settles back to the floor otherwise) until it spits. */
    private void spitFromALedge(Level level, Snail s, float lift) {
        float y = s.position.y;
        for (int f = 0; f < 60 * 3 && !s.gobActive(); f++) { s.position.y = y - lift; frame(level); }
        assertTrue(s.gobActive(), "it spat from the ledge");
        assertEquals(FLOOR - lift - Snail.GOB_HEIGHT, s.gobY(), .01f, "the gob flies at the ledge's height");
    }

    /** The frame, counted from the spit, on which the gob reaches a hero who does nothing, with the snail {@code lift} px up. */
    private int gobArrival(float lift) {
        Level level = level();
        Snail s = snail(level);
        place(level, AHEAD);
        spitFromALedge(level, s, lift);
        int due = until(level, () -> s.gobX() - Snail.GOB_CORE / 2 <= AHEAD, 60);
        frame(level); frame(level);
        assertFalse(alive(level), "without a throw the hit lands (lift " + lift + ")");
        return due;
    }

    /**
     * #505: from a ledge 6 to 16 px up the gob flies at the stone's height, so a stone thrown on the frame the gob arrives breaks it
     * on its release frame and the hero lives. Without {@code Level.stepNewThrows} the stone first moves a frame late and the hero dies.
     */
    @Test void aStoneThrownOnTheFrameTheGobArrivesFromALedgeWinsTheTie() {
        for (float lift : new float[] {6, 8, 12, 16}) {
            int due = gobArrival(lift);
            Level level = level();
            carryTheStone(level);
            Snail s = snail(level);
            place(level, AHEAD);
            spitFromALedge(level, s, lift);
            for (int f = 1; f < due; f++) frame(level);
            assertTrue(s.gobActive(), "the gob is in the air, its hit due on this frame (lift " + lift + ")");
            harness.sounds.clear();
            press(level, Input.Keys.L);
            assertFalse(s.gobActive(), "the stone's first step breaks the gob on its release frame (lift " + lift + ")");
            assertTrue(harness.sounds.contains("snail_splat"));
            settle(level, 10);
            assertTrue(alive(level), "the stone wins the tie (lift " + lift + ")");
        }
    }

    /** #505: the first step is one step, 220 px/s for one frame, taken on the release frame, and the next frame is another one. */
    @Test void aThrownStoneNeverMovesTwiceInAFrame() {
        Level level = level();
        carryTheStone(level);
        var stone = level.player.carriedObject();
        place(level, 190, true);
        frame(level);   // the stone follows the hero's hands
        float held = stone.getX();
        press(level, Input.Keys.L);
        assertEquals(220 * STEP, stone.getX() - held, .01f, "one step on the release frame, not two and not none");
        float x0 = stone.getX();
        frame(level);
        assertEquals(220 * STEP, stone.getX() - x0, .01f, "and one step on the next frame");
    }

    @Test void aFireballBurnsTheGobAwayAndFliesOnToKillTheSnail() {
        Level level = level();
        Snail s = spat(level);
        place(level, AHEAD - 40, true);   // 40 px back: the fireball meets the gob in the air
        harness.sounds.clear();
        press(level, Input.Keys.L);
        until(level, () -> !s.gobActive(), 60);
        assertTrue(harness.sounds.contains("snail_splat"), "fire takes the gob");
        until(level, s::isDefeated, 120);
        assertTrue(alive(level));
        assertTrue(s.isRemains(), "the fireball flew on, unspent, and killed the snail: its body stays (D101)");
        assertEquals(0, level.bloodParticles().count(), "no blood from fire (D99)");
    }

    /**
     * A stone thrown from x 190 rises to 10 px above the hero's hands and comes down through the gob's band 86 to 110 px ahead,
     * 0.4 to 0.5 s after the throw (measured); thrown 0.2 s before the spit, it meets the gob there.
     */
    @Test void aThrownStoneBreaksTheGobApartAndFliesOn() {
        Level level = level();
        place(level, 108);
        harness.held.clear(); frame(level); harness.held.add(Input.Keys.W); frame(level); // Up alone interacts (D126)
        harness.held.clear(); frame(level);
        var stone = level.player.carriedObject();
        assertNotNull(stone, "picked up the stone");
        Snail s = snail(level);
        place(level, 190, true);
        until(level, () -> s.state() == Snail.State.WINDUP && s.warningRemaining() <= .2f, 60 * 3);
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        harness.sounds.clear();
        until(level, s::gobActive, 30);
        until(level, () -> !s.gobActive(), 60);
        assertTrue(alive(level), "the stone broke it before it reached the hero");
        assertTrue(harness.sounds.contains("snail_splat"));
        assertTrue(stone.snapshot().velocityX() != 0, "a gob stops nothing: the stone flies on");
    }

    @Test void theReloadIsHarmlessAndLastsItsTimeThenANewFullWindUp() {
        Level level = level();
        Snail s = spat(level);
        int sinceSpit = 0;
        while (s.gobActive()) { hold(level, AHEAD, 12); frame(level); sinceSpit++; }
        place(level, AHEAD);
        int more = until(level, () -> s.state() == Snail.State.WINDUP, 60 * 4);
        assertEquals(Snail.RELOAD_SECONDS, (sinceSpit + more) * STEP, STEP * 2, "the next wind-up starts when the reload ends");
        assertTrue(alive(level), "harmless while it reloads");
        int toSpit = until(level, s::gobActive, 120);
        assertEquals(TrapWarnings.DEFAULT_WARNING_SECONDS, toSpit * STEP, STEP * 1.5f, "a full new wind-up");
    }

    @Test void killedWhileWindingUpItFiresNothing() {
        Level level = level();
        Snail s = windingUp(level);
        harness.sounds.clear();
        s.collidedWithActor(true);
        settle(level, 120);
        assertFalse(s.gobActive());
        assertTrue(alive(level));
        assertTrue(harness.sounds.contains("snail_splat"), "the swelling gob pops on its lips, seen and heard");
        assertTrue(s.isRemains());
    }

    @Test void aGobInFlightFliesOnWhenItsSnailIsKilled() {
        Level level = level();
        Snail s = spat(level);
        s.collidedWithActor(true);
        frame(level);
        assertTrue(s.gobActive(), "the gob flies on");
        until(level, () -> !alive(level), 60);
    }

    @Test void aGobInFlightOutlivesItsCrumbledBody() {
        Level level = level();
        Snail s = spat(level);
        s.collidedWithActor(true);
        s.crumble();
        assertFalse(s.isRemoved(), "kept until its gob ends");
        assertFalse(s.isRemains(), "but the body is gone");
        until(level, () -> !alive(level), 60);
        settle(level, 2);
        assertTrue(s.isRemoved());
    }

    @Test void awareItTurnsToAHeroBehindItAndLosesOneOutOfSight() {
        Level level = level();
        Snail s = snail(level);
        place(level, AHEAD);
        until(level, () -> s.state() == Snail.State.WINDUP, 60);
        until(level, s::gobActive, 120);
        place(level, BEHIND);   // behind it now, during the reload
        int frames = until(level, () -> s.facingRight, 60);
        assertEquals(Snail.TURN_SECONDS, (frames + 1) * STEP, STEP * 1.5f, "it turns after 0.4 s");
        place(level, FAR_BEHIND);
        level.player.position.x = 61 - 29;   // far to its left, behind it now and out of its sight
        int lost = until(level, () -> s.state() == Snail.State.CRAWL, 180);
        assertEquals(Snail.LOSE_SECONDS, lost * STEP, STEP * 1.5f, "it loses him after 1.5 s");
    }

    @Test void aSolidCutsItsSightAndStopsItsGob() {
        Level hidden = harness.roomOf("snail-wall", 0);
        Snail h = snail(hidden);
        place(hidden, 170);   // behind the block at 180..196
        settle(hidden, 60);
        assertTrue(h.state() == Snail.State.CRAWL || h.state() == Snail.State.PAUSE, "cover breaks its sight");

        Level level = harness.roomOf("snail-wall", 0);
        Snail s = snail(level);
        place(level, AHEAD);
        until(level, s::gobActive, 60 * 2);
        float lastX = s.gobX();
        while (s.gobActive()) { hold(level, AHEAD, 12); lastX = s.gobX(); frame(level); }
        assertEquals(196 + Snail.GOB_CORE / 2, lastX, 3, "it splats on the block's face (its core's left edge on x 196)");
    }

    @Test void aStompKillsItWithTheNormalBounce() {
        Level level = level();
        Snail s = snail(level);
        Rectangle body = s.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - 23.5f, body.y - 48 - 20);
        level.player.velocity().setZero();
        boolean bounced = false;
        for (int f = 0; f < 60 && !s.isDefeated(); f++) { frame(level); bounced |= level.player.velocity.y < 0; }
        assertTrue(s.isDefeated() && bounced, "stomped, with the bounce");
        assertTrue(s.isRemains());
        assertTrue(alive(level));
    }

    @Test void theSwordKillsItWithTheLightSprayAndLeavesTheShell() {
        Level level = level();
        Snail s = snail(level);
        place(level, BEHIND);
        settle(level, 30);   // the hero finishes appearing
        Rectangle body = s.hitboxBounds();
        place(level, body.x + body.width + 13, false);   // just behind it, facing its back: unseen, and the sword reaches it
        press(level, Input.Keys.K);
        until(level, s::isDefeated, 30);
        assertTrue(alive(level));
        assertTrue(level.bloodParticles().count() > 0, "the sword's light spray (D65)");
        assertTrue(harness.sounds.contains("bounce"), "the shared kill sound");
        settle(level, 60);
        assertTrue(s.isRemains(), "the empty shell stays (D101)");
    }

    @Test void aDeathResetsItCalmAtItsSpawnWithNoGob() {
        Level level = level();
        Snail s = spat(level);
        s.revisit(level.player);
        assertEquals(Snail.State.CRAWL, s.state());
        assertFalse(s.gobActive());
        assertEquals(320, s.position.x, .01f);
        assertFalse(s.facingRight);
    }

    /** A spider with no hero to hear (it never acts), placed first in the level's objects at frame x {@code x}, on the floor. */
    private Spider dummySpider(Level level, float x) {
        Spider spider = new Spider(harness.assets(), harness.game(), x, FLOOR - 16, 32, 16, 0, 0);
        spider.setLevel(level);
        level.objects.add(0, spider);
        return spider;
    }

    @Test void theGobPassesThroughASpider() {
        Level level = level();
        Spider spider = dummySpider(level, 270);   // its body x 272..291, across the line
        Snail s = spat(level);
        until(level, () -> s.gobX() < 268, 60);   // the gob's core is past the spider's whole body
        assertTrue(s.gobActive(), "the gob passes through the spider");
        assertFalse(spider.isDefeated(), "and does not hurt it");
    }

    /** D106: the swing deals with an enemy and a gob in its box on the same frame, whichever comes first in the level's objects. */
    @Test void oneSwingKillsAnEnemyAndCutsAGobOnTheSameFrame() {
        Level level = level();
        Spider spider = dummySpider(level, 248);   // its body x 250..269, in the sword's box (the hero's front at 250, 17 px on)
        Snail s = spat(level);
        place(level, AHEAD, true);
        until(level, () -> s.gobX() <= 262, 60);   // the gob is in the sword's box, not yet on the hero
        press(level, Input.Keys.K);
        until(level, spider::isDefeated, 10);
        assertFalse(s.gobActive(), "the gob was cut on the frame the spider was hit");
        settle(level, 30);
        assertTrue(alive(level));
    }

    /**
     * D106: a fireball that reaches an enemy and a gob in the same step burns the gob (tested before the enemies) and is spent
     * on the enemy. A spider is held with its body's near edge on the gob's drawn edge, so both are reached in one step.
     */
    @Test void aFireballSpentOnAnEnemyStillBurnsTheGobItReachedInTheSameStep() {
        Level level = level();
        Spider spider = dummySpider(level, 400);
        Snail s = spat(level);
        place(level, AHEAD - 30, true);
        settle(level, 2);
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L);
        for (int f = 0; f < 60 && !spider.isDefeated(); f++) {
            // the gob moves 2 px before the fireball each frame: the spider's body edge (x + 2) on the gob's drawn edge after it
            spider.position.set(s.gobX() - Snail.GOB_BREAK_WIDTH / 2 - Snail.GOB_SPEED * STEP - 2, FLOOR - 16);
            spider.velocity().setZero();
            frame(level);
            harness.held.clear();
        }
        assertTrue(spider.isDefeated(), "the fireball was spent on the spider");
        assertFalse(s.gobActive(), "and burnt the gob it reached in the same step");
        assertTrue(harness.sounds.contains("snail_splat"));
        settle(level, 30);
        assertTrue(alive(level));
    }

    @Test void aFallingGobThatMeetsAWallSplatsOnTheWall() {
        Level level = harness.roomOf("snail-drop-wall", 0);   // a block at x 120..136, just past the line's end (x 145)
        Snail s = snail(level);
        place(level, AHEAD);
        until(level, s::gobActive, 60 * 2);
        while (s.gobActive()) { hold(level, AHEAD, 12); frame(level); }
        assertEquals(136 + Snail.GOB_CORE / 2, s.gobX(), 3, "it ends on the block's face");
        assertTrue(s.gobY() < FLOOR - Snail.GOB_CORE, "in the air, on the wall, not on the floor: " + s.gobY());
    }

    @Test void aGobThatFallsOutOfTheRoomStillSplatsSeenAndHeard() {
        Level level = harness.roomOf("snail-pit", 0);   // a pit x 64..200 just past the line's end: no load check made this one
        Snail s = new Snail(harness.assets(), harness.game(), 320, FLOOR - 16, 24, 16, 0, 0);
        s.setPlayer(level.player);
        s.setLevel(level);
        level.objects.add(s);
        place(level, AHEAD);
        until(level, s::gobActive, 60 * 2);
        harness.sounds.clear();
        while (s.gobActive()) { hold(level, AHEAD, 12); frame(level); }
        assertEquals(368, s.gobY(), Snail.GOB_CORE, "it ends at the room's bottom edge (leaving the room counts as blocked)");
        assertEquals(List.of("snail_splat"), harness.sounds, "seen and heard, never silent (D93)");
    }

    @Test void loadChecksKeepItsShotsOffStandingPlacesAndItsFloorWhole() {
        IllegalArgumentException entrance = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("snail-entrance", 0));
        assertTrue(entrance.getMessage().contains("entrance"), entrance.getMessage());
        IllegalArgumentException gap = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("snail-gap", 0));
        assertTrue(gap.getMessage().contains("without gaps"), gap.getMessage());
        assertNotNull(level(), "the fixture itself passes");
        assertNotNull(harness.roomOf("snail-wall", 0));
    }
}
