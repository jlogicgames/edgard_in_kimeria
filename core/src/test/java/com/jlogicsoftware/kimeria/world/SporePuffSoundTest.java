package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.entity.objects.HoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spore sounds (#445, #558, D93): the puffball's release plays {@code puffball_burst} once per cloud (only while it is in the camera
 * view); both honey fungi burst with {@code spore_puff}. The harmless and the false fungus make exactly the same sound (D63: the ear must not
 * tell which one kills), and the puffball's own sound is not theirs.
 */
class SporePuffSoundTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final String PUFF = "spore_puff", BURST = "puffball_burst";
    /** The hero's body box as the game defines it (HoneyFungusTest): offset 18 in a 48 px frame, 11 wide, so the right edge is 29 in. */
    private static final float BODY_RIGHT_OFFSET = 29, STAND_Y = 272, VIEW_W = 640, VIEW_H = 360, PERIOD = 4f, CLOUD = 1f, NEAR = 0.1f, HERO_RIGHT_OF_ZONE = 8;
    private static final int SECONDS = 60;
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private void run(Level level, float seconds) { for (int i = 0, n = Math.round(seconds * SECONDS); i < n; i++) { level.update(STEP); h.pressed.clear(); } }
    private static Puffball puffball(Level level) { return level.objects.stream().filter(Puffball.class::isInstance).map(Puffball.class::cast).findFirst().orElseThrow(); }
    private static FungusTrap fungus(Level level, Class<? extends FungusTrap> type) { return level.objects.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow(); }
    private long bursts() { return h.sounds.stream().filter(BURST::equals).count(); }

    @Test void thePuffballBurstsOncePerReleaseWhileInView() {
        Rectangle view = new Rectangle();
        Level level = h.roomOf("rhythm-test", 0, () -> view);
        Puffball p = puffball(level);
        view.set(p.centerX() - VIEW_W / 2f, p.getY() - VIEW_H / 2f, VIEW_W, VIEW_H);
        assertTrue(level.puffballInView(p));
        run(level, PERIOD - CLOUD - NEAR);
        assertEquals(0, bursts(), "the swell is silent here: the sound is the release");
        run(level, 2 * NEAR);
        assertEquals(1, bursts(), "one cue as the cloud starts (at 3.0 s)");
        run(level, CLOUD - 2 * NEAR + (PERIOD - CLOUD) - NEAR);
        assertEquals(1, bursts(), "none while the cloud lingers or the next swell runs");
        run(level, 2 * NEAR);
        assertEquals(2, bursts(), "and one for the next release");
        assertEquals(List.of(BURST, BURST), h.sounds);
    }

    @Test void thePuffballNeverMakesTheHoneyFungiSound() {
        Rectangle view = new Rectangle();
        Level level = h.roomOf("rhythm-test", 0, () -> view);
        Puffball p = puffball(level);
        view.set(p.centerX() - VIEW_W / 2f, p.getY() - VIEW_H / 2f, VIEW_W, VIEW_H);
        run(level, 2 * PERIOD);
        assertFalse(h.sounds.isEmpty());
        assertFalse(h.sounds.contains(PUFF), "the puffball has its own sound; spore_puff is the honey fungi's pair");
    }

    @Test void aPuffballOffScreenStaysSilent() {
        Rectangle view = new Rectangle();
        Level level = h.roomOf("rhythm-test", 0, () -> view);
        Puffball p = puffball(level);
        view.set(p.getX() - 3 * VIEW_W, p.getY(), VIEW_W, VIEW_H);
        assertFalse(level.puffballInView(p));
        run(level, 2 * PERIOD + 1f);
        assertEquals(0, bursts(), "a clocked trap off screen must not sound every period");
        view.set(0, 0, 0, 0);
        assertFalse(level.puffballInView(p), "no view: silent");
    }

    @Test void aSoundOffSettingMakesNoPuff() {
        h.playSounds = false;
        Rectangle view = new Rectangle();
        Level level = h.roomOf("rhythm-test", 0, () -> view);
        Puffball p = puffball(level);
        view.set(p.centerX() - VIEW_W / 2f, p.getY() - VIEW_H / 2f, VIEW_W, VIEW_H);
        run(level, PERIOD + 0.5f);
        assertTrue(h.sounds.isEmpty());
    }

    /** The puffball in this room's phase starts its cloud: a room opened inside a cloud, and a rebuild after death, are not releases. */
    @Test void aRoomOpenedInsideACloudAndARebuildAreNotReleases() {
        Rectangle view = new Rectangle();
        Level level = h.roomOf("rhythm-test", 0, () -> view);
        Puffball p = puffball(level);
        view.set(p.centerX() - VIEW_W / 2f, p.getY() - VIEW_H / 2f, VIEW_W, VIEW_H);
        run(level, PERIOD - CLOUD + 2 * NEAR); // the cloud is out
        assertEquals(1, bursts());
        h.sounds.clear();
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        Puffball fresh = puffball(level);
        view.set(fresh.centerX() - VIEW_W / 2f, fresh.getY() - VIEW_H / 2f, VIEW_W, VIEW_H);
        run(level, 2 * NEAR);
        assertEquals(0, bursts(), "a rebuild restarts at the authored phase, calm: no cue");
        // A puffball whose authored phase puts the room's opening inside the cloud makes no cue either.
        Puffball inCloud = new Puffball(h.assets(), p.getX(), p.getY(), p.getWidth(), p.getHeight(), h.game().trapWarnings(), PERIOD, PERIOD - CLOUD + NEAR, CLOUD);
        inCloud.onRelease(() -> h.sounds.add(BURST));
        assertEquals(Puffball.Phase.CLOUD, inCloud.currentPhase());
        inCloud.update(STEP);
        assertEquals(0, bursts(), "opening inside a cloud is not a release");
    }

    /** Triggers the fungus by standing in its zone, waits out the swell and the burst, returns the sounds heard. */
    private List<String> burstSounds(Class<? extends FungusTrap> type) {
        h.sounds.clear();
        Level level = h.roomOf("fungus-test", 0);
        FungusTrap f = fungus(level, type);
        level.player.position.set(f.getX() + f.getWidth() + HERO_RIGHT_OF_ZONE - BODY_RIGHT_OFFSET, STAND_Y);
        level.player.velocity().setZero();
        for (int i = 0; i < 6 * SECONDS && f.state() != FungusTrap.State.SPENT; i++) { level.update(STEP); h.pressed.clear(); }
        assertEquals(FungusTrap.State.SPENT, f.state(), "it swelled, burst and is spent");
        return h.sounds.stream().filter(PUFF::equals).toList(); // the false one also kills the idle hero: its hit sounds are not the burst's
    }

    @Test void bothFungiBurstWithTheSameOneSound() {
        List<String> harmless = burstSounds(HoneyFungus.class);
        List<String> lethal = burstSounds(FalseHoneyFungus.class);
        assertEquals(List.of(PUFF), harmless, "one cue per burst");
        assertEquals(harmless, lethal, "D63: the sound does not tell which one kills");
    }
}
