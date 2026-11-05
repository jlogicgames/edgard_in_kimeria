package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.HintGeometry;
import com.jlogicsoftware.kimeria.ui.HintLayout;
import com.jlogicsoftware.kimeria.ui.HintTransition;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-0 lever lesson room (#186): ordinary keyboard controls drive the hero through the real
 * map. Times are author (mastered) measurements, not novice playtests. Recipe: docs/ROOMS.md.
 */
class LeverLessonTest {
    private static final float STEP = 1f / 60f;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "language" -> Language.ENGLISH;
            case "isGameStarted" -> true;
            case "artifacts" -> artifacts;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? STEP : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private Level freshLevel() {
        return new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
    }

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private Torch torch(Level level) {
        return level.objects.stream().filter(Torch.class::isInstance).map(Torch.class::cast).findFirst().orElseThrow();
    }

    private Lever lever(Level level) {
        return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow();
    }

    /** Holds Right until the hero can operate the lever; returns elapsed frames. */
    private int walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertTrue(++frames < 60 * 20, "lever never came into reach at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Up + Action, as the hint teaches: Action is a fresh press while Up is held. */
    private int operate(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    /** Walks Right to the room exit; returns elapsed frames. */
    private int walkToExit(Level level) {
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            assertFalse(level.player.isGotHit(), "died at " + level.player.position);
            assertTrue(++frames < 60 * 30, "exit unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    @Test void theRoomOffersOneLeverAndOneDarkTorchAndNothingElse() {
        Level level = freshLevel();
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.objects.stream().filter(Torch.class::isInstance).count());
        assertEquals(1, level.triggers.size());
        assertFalse(torch(level).isBurning(), "The effect starts off, so pressing visibly changes it");
        assertFalse(lever(level).isUp());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>));
        Rectangle torchBounds = new Rectangle(torch(level).getX(), torch(level).getY(), 16, 16);
        assertTrue(Math.abs(torchBounds.x - lever(level).getX()) < 160, "Torch is in the lever's view");
    }

    @Test void walkingToTheLeverAndPressingUpActionTogglesTheTorchBothWays() {
        Level level = freshLevel();
        walkToLever(level);
        Torch torch = torch(level);
        Lever lever = lever(level);
        assertFalse(torch.isBurning());
        operate(level);
        assertTrue(torch.isBurning()); assertTrue(lever.isUp());
        // The effect is observable from the lever's position: the torch is on the same screen.
        assertTrue(Math.abs(torch.centerX() - level.player.centerX()) < 320);
        operate(level);
        assertFalse(torch.isBurning()); assertFalse(lever.isUp());
        operate(level);
        assertTrue(torch.isBurning(), "The lever toggles; it never fires once and locks");
    }

    @Test void exitIsReachableWithTheLeverOffOnAndAfterRetryAndRestart() {
        Level off = freshLevel();
        walkToExit(off);

        Level on = freshLevel();
        walkToLever(on); operate(on);
        assertTrue(torch(on).isBurning());
        walkToExit(on);

        // Death / room retry: the room is not yet secured, so it returns to its authored (dark) state.
        Level retry = freshLevel();
        walkToLever(retry); operate(retry);
        assertTrue(torch(retry).isBurning());
        retry.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertFalse(torch(retry).isBurning(), "Retry restores the authored state");
        assertFalse(lever(retry).isUp());
        walkToExit(retry);
        retry.resetCurrentRoom(new Player(assets, game, 0, 0));
        walkToLever(retry); operate(retry);
        walkToExit(retry);

        // Restart Level rebuilds the whole level from the map.
        Level restarted = freshLevel();
        assertFalse(torch(restarted).isBurning());
        walkToExit(restarted);
    }

    @Test void lessonHintsCoverEveryStandingPositionInBothTorchStatesAndSurviveRetry() {
        Level level = freshLevel();
        Set<String> messages = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (boolean lit : new boolean[]{false, true}) {
                for (float x = 24; x < 544; x += 8) {
                    torch(level).toggleFire(lit);
                    level.player.position.set(x, 272);
                    for (int i = 0; i < 3; i++) frame(level);
                    String hint = level.player.contextHint(), zone = level.player.zoneHint();
                    String where = "x=" + x + " lit=" + lit + ": " + hint + " / " + zone;
                    // #211: the zone message is never replaced by the live lever label; both show together.
                    assertEquals(level.messageHint().message().t(Language.ENGLISH), zone, where);
                    messages.add(zone);
                    if (level.player.canInteract()) {
                        assertTrue(hint.contains(Msg.LEVER.t(Language.ENGLISH)), where);
                        assertTrue(hint.contains((lit ? Msg.TURN_OFF : Msg.TURN_ON).t(Language.ENGLISH)), where);
                    } else assertEquals("", hint, where);
                }
            }
            level.resetCurrentRoom(new Player(assets, game, 0, 0));
        }
        assertEquals(Set.of(Msg.HINT_TUTORIAL_WALK.t(Language.ENGLISH), Msg.HINT_LEVER_TORCH.t(Language.ENGLISH),
            Msg.HINT_LEVER_EXIT.t(Language.ENGLISH)), messages);
        for (String text : messages) assertFalse(text.toLowerCase().contains("dark"), "State-dependent wording: " + text);
    }

    /**
     * #211: the HUD's two channels, driven here exactly as the HUD drives them. The lever label appears at
     * once and the zone message is never hidden, replaced or restarted while it shows.
     */
    @Test void theZoneMessageStaysFullyVisibleWhileTheLeverLabelShowsAndTheLeverLabelIsImmediate() {
        Level level = freshLevel();
        HintTransition zone = new HintTransition(), context = new HintTransition();
        Vector2 zoneAnchor = new Vector2(), contextAnchor = new Vector2();
        String message = Msg.HINT_LEVER_TORCH.t(Language.ENGLISH);
        HintGeometry geometry = new HintGeometry();
        float zoneFullSince = -1, firstZoneTop = -1, t = 0;
        int guard = 0, interacting = 0;
        while (interacting < 90) {
            if (interacting == 30 && !torch(level).isBurning()) operate(level); // toggling must not disturb the message
            else { held.clear(); if (!level.player.canInteract()) held.add(Input.Keys.D); frame(level); }
            t += STEP;
            String z = level.player.zoneHint(), c = level.player.contextHint();
            if (!z.isEmpty()) level.player.zoneHintPosition(zoneAnchor);
            if (!c.isEmpty()) level.player.contextHintPosition(contextAnchor);
            zone.update(z, zoneAnchor, STEP, true);
            context.update(c, contextAnchor, STEP);
            if (zoneFullSince < 0 && zone.text().equals(message) && zone.alpha() >= .95f) zoneFullSince = t;
            if (level.player.canInteract()) {
                interacting++;
                assertTrue(zoneFullSince >= 0, "the message is readable before the lever is in reach");
                if (interacting == 1) assertTrue(context.alpha() > 0 && context.text().contains(Msg.LEVER.t(Language.ENGLISH)),
                    "the lever label is visible on the very frame the lever comes into reach: " + context.alpha());
                // Placement as the HUD derives it: from presence, not opacity, so the zone message never moves
                // and the label's reservation is the same through the Turn on -> Turn off crossfade.
                boolean zonePresent = !z.isEmpty() || !zone.text().isEmpty();
                assertTrue(zonePresent, "message present at frame " + interacting);
                if (!context.text().isEmpty()) {
                    for (float cameraY : new float[]{0, 8}) {
                        float zoneTop = geometry.zoneTop(zoneAnchor, zone.text(), cameraY);
                        if (cameraY == 0) { if (firstZoneTop < 0) firstZoneTop = zoneTop; assertEquals(firstZoneTop, zoneTop, .001, "message moved"); }
                        assertTrue(geometry.gap(contextAnchor, context.text(), zoneAnchor, zone.text(), cameraY) >= HintLayout.GAP - .001,
                            "message and lever label overlap at frame " + interacting + " cameraY=" + cameraY);
                    }
                }
                assertEquals(message, zone.text(), "message replaced at frame " + interacting);
                assertTrue(zone.alpha() >= .95f, "message dimmed to " + zone.alpha() + " at frame " + interacting);
                assertEquals(z, message);
                // Turn on -> Turn off swaps the label text, which crossfades through 0 (unchanged contextual behaviour).
                if (interacting >= 2 && interacting < 30) assertTrue(context.alpha() > 0, "no flicker before the toggle");
            }
            assertTrue(++guard < 60 * 40, "stuck at " + level.player.position);
        }
        assertTrue(context.alpha() >= .95f, "lever label settled to full opacity");
    }

    /** The standing hero keeps the lever label clear of the message and of their own head, wherever they stand. */
    @Test void theLeverLabelClearsTheMessageAndTheHeroAtEveryStandingPosition() {
        Level level = freshLevel();
        HintGeometry geometry = new HintGeometry();
        Vector2 zoneAnchor = new Vector2(), contextAnchor = new Vector2();
        int both = 0;
        for (float x = 24; x < 544; x += 4) for (float y : new float[]{272, 272 - 58}) { // floor, top of a jump
            level.player.position.set(x, y);
            for (int i = 0; i < 3; i++) frame(level);
            String zone = level.player.zoneHint(), context = level.player.contextHint();
            if (zone.isEmpty() || context.isEmpty()) continue;
            both++;
            level.player.zoneHintPosition(zoneAnchor); level.player.contextHintPosition(contextAnchor);
            for (float cameraY : new float[]{0, 8})
                assertTrue(geometry.gap(contextAnchor, context, zoneAnchor, zone, cameraY) >= HintLayout.GAP - .001,
                    "x=" + x + " y=" + y + " cameraY=" + cameraY);
        }
        assertTrue(both > 5, "label and message do share positions in this room: " + both);
    }

    @Test void leavingTheLeversReachEndsOnlyTheLeverLabel() {
        Level level = freshLevel();
        walkToLever(level);
        assertFalse(level.player.contextHint().isEmpty());
        assertFalse(level.player.zoneHint().isEmpty());
        walkToExit(level);
        assertEquals("", level.player.contextHint(), "Out of reach the lever label ends; the zone message continues");
        assertFalse(level.player.zoneHint().isEmpty());
    }

    @Test void bothChannelsEndWhenTheHeroCanNoLongerAct() {
        Level level = freshLevel();
        walkToLever(level);
        assertFalse(level.player.contextHint().isEmpty());
        assertFalse(level.player.zoneHint().isEmpty());
        level.player.beginVictoryWalk(); // death, checkpoint and victory share canFight() == false
        assertEquals("", level.player.contextHint());
        assertEquals("", level.player.zoneHint());
    }

    @Test void theLeverHintNamesTheActionAndFlipsWithTheState() {
        Level level = freshLevel();
        walkToLever(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_ON.t(Language.ENGLISH)), level.player.contextHint());
        assertFalse(level.player.zoneHint().isEmpty(), "The lever label never hides the zone message (#211)");
        assertTrue(level.player.contextHint().contains("W + K") || level.player.contextHint().contains("Up"), level.player.contextHint());
        operate(level);
        assertTrue(level.player.contextHint().contains(Msg.TURN_OFF.t(Language.ENGLISH)), level.player.contextHint());
    }

    @Test void masteredRouteTimeIsMeasured() {
        Level level = freshLevel();
        int frames = walkToLever(level);
        frames += operate(level);
        frames += walkToExit(level);
        float seconds = frames * STEP;
        System.out.printf(java.util.Locale.ROOT, "forest-0-lever mastered route (walk, operate lever, exit): %.2f s%n", seconds);
        assertTrue(seconds < 30, "Author check, not a novice playtest (#129-#131): " + seconds);
    }
}
