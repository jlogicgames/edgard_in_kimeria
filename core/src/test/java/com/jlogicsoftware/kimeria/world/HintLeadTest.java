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
import com.jlogicsoftware.kimeria.ui.HintTransition;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Continuous-walk simulation of the tutorial hint timing (#209): the real {@link HintTransition} is driven
 * exactly as the HUD drives it, while the hero holds Right. A lesson message must be fully readable for a
 * while before the hero can reach its object, and must still be on screen shortly after he passes it.
 */
class HintLeadTest {
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

    private Level room(int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "forest-0", Rectangle::new);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    /** Seconds the message was fully visible before the hero first reached an object, and after. */
    private record Lead(float fullBefore, float longestGapAfter, float firstFull, float reachedAt) { }

    private Lead walk(Level level, String message, float secondsAfterReach) {
        return walk(level, message, secondsAfterReach, () -> level.player.canInteract());
    }

    private Lead walk(Level level, String message, float secondsAfterReach, java.util.function.BooleanSupplier reached) {
        return walk(level, message, secondsAfterReach, reached, () -> { held.clear(); held.add(Input.Keys.D); });
    }

    private Lead walk(Level level, String message, float secondsAfterReach, java.util.function.BooleanSupplier reached, Runnable drive) {
        var hint = new HintTransition();
        var anchor = new Vector2();
        float t = 0, reachedAt = -1, firstFull = -1, lastFullBeforeReach = -1, gap = 0, longestGap = 0;
        int guard = 0;
        while (reachedAt < 0 || t < reachedAt + secondsAfterReach) {
            drive.run();
            frame(level);
            t += STEP;
            String text = level.player.zoneHint();
            if (!text.isEmpty()) level.player.zoneHintPosition(anchor);
            hint.update(text, anchor, HintTransition.clampFrame(STEP), true);
            boolean full = hint.text().equals(message) && hint.alpha() >= .95f;
            if (full && firstFull < 0) firstFull = t;
            if (reachedAt < 0) {
                if (full) lastFullBeforeReach = t;
                if (reached.getAsBoolean()) reachedAt = t;
            } else if (hint.alpha() > .5f) gap = 0;
            else { gap += STEP; longestGap = Math.max(longestGap, gap); }
            assertTrue(++guard < 60 * 40, "never reached at " + level.player.position);
        }
        held.clear();
        float before = firstFull < 0 || reachedAt < firstFull ? 0 : reachedAt - firstFull;
        System.out.printf(java.util.Locale.ROOT, "hint lead '%s': full %.2f s before reach (first full %.2f s, reach %.2f s), longest blank stretch %.2f s in the following %.1f s%n",
            message, before, firstFull, reachedAt, longestGap, secondsAfterReach);
        return new Lead(before, longestGap, firstFull, reachedAt);
    }

    @Test void leverRoomMessageIsReadableBeforeAContinuousWalkReachesTheLever() {
        Lead lead = walk(room(0), Msg.HINT_LEVER_TORCH.t(Language.ENGLISH), 1.5f);
        assertTrue(lead.fullBefore >= .9f, "Lever message fully visible for at least 1 s before the lever is in reach: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and some instruction stays on screen while the hero operates and leaves the lever: " + lead.longestGapAfter);
    }

    /** The ladder room walk: Right held, a jump about 30 px before the thorn root (D48). */
    private Runnable ladderDriver(Level level) {
        return () -> {
            Rectangle b = level.player.hitboxBounds();
            float r = b.x + b.width;
            held.clear(); held.add(Input.Keys.D);
            if (level.player.isOnGround() && r >= 896 - 32 && r < 896 - 8) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        };
    }

    @Test void ladderRoomMessageIsReadableBeforeAContinuousWalkReachesTheLever() {
        Level level = room(1);
        Lead lead = walk(level, Msg.HINT_LADDER_LEVER.t(Language.ENGLISH), 1.5f, () -> level.player.canInteract(), ladderDriver(level));
        assertTrue(lead.fullBefore >= .9f, "Lever message fully visible for at least 1 s before the lever is in reach: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and some instruction stays on screen while the hero operates and leaves the lever: " + lead.longestGapAfter);
    }

    @Test void wallRoomLeverMessageIsReadableBeforeAContinuousWalkReachesTheLever() {
        Lead lead = walk(room(2), Msg.HINT_WALL_LEVER.t(Language.ENGLISH), 1.5f);
        assertTrue(lead.fullBefore >= .9f, "Lever message fully visible for at least 1 s before the lever is in reach: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and some instruction stays on screen while the hero operates and leaves the lever: " + lead.longestGapAfter);
    }

    /** Walking on without stopping (wall already raised): the pass message is up before the body meets the wall. */
    @Test void wallRoomPassMessageIsReadableBeforeAContinuousWalkReachesTheWall() {
        Level level = room(2);
        level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Wall.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Wall.class::cast).findFirst().orElseThrow().performAction();
        Lead lead = walk(level, Msg.HINT_WALL_PASS.t(Language.ENGLISH), 1.0f, () -> {
            Rectangle b = level.player.hitboxBounds();
            return b.x + b.width >= 2096 - 2;
        });
        assertTrue(lead.fullBefore >= .9f, "Wall message fully visible for at least 1 s before the body reaches the wall: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and it stays up while the hero passes under the wall: " + lead.longestGapAfter);
    }

    /** The stone message is readable before a continuous walk from the entrance reaches the stone (#199). */
    @Test void stoneRoomMessageIsReadableBeforeAContinuousWalkReachesTheStone() {
        Level level = room(4);
        Lead lead = walk(level, Msg.HINT_STONE_THROW.t(Language.ENGLISH), 1.5f, () -> level.player.selectedCarryable() != null);
        assertTrue(lead.fullBefore >= .9f, "Stone message fully visible for at least 1 s before the stone is in reach: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and some instruction stays on screen while the hero picks it up: " + lead.longestGapAfter);
    }

    @Test void platformRoomLeverMessageIsReadableBeforeAContinuousWalkReachesTheLever() {
        Lead lead = walk(room(3), Msg.HINT_PLATFORM_LEVER.t(Language.ENGLISH), 1.5f);
        assertTrue(lead.fullBefore >= .9f, "Lever message fully visible for at least 1 s before the lever is in reach: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and some instruction stays on screen while the hero operates and leaves the lever: " + lead.longestGapAfter);
    }

    /** Walking on without stopping: the ride message is up before the body reaches the platform's near edge. */
    @Test void platformRoomRideMessageIsReadableBeforeAContinuousWalkReachesThePlatform() {
        Level level = room(3);
        Lead lead = walk(level, Msg.HINT_PLATFORM_RIDE.t(Language.ENGLISH), 1.0f, () -> {
            Rectangle b = level.player.hitboxBounds();
            return b.x + b.width >= 2880 - 2;
        });
        assertTrue(lead.fullBefore >= .9f, "Platform message fully visible for at least 1 s before the body reaches the platform: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and it stays up while the hero jumps on: " + lead.longestGapAfter);
    }

    /** The traps room walk of the lesson: Right held, a jump at the pit lip and another at the platform's far end (#200). */
    private Runnable trapsDriver(Level level) {
        return () -> {
            Rectangle b = level.player.hitboxBounds();
            float r = b.x + b.width;
            boolean jump = level.player.isOnGround() && ((r >= 4030 && r < 4040) || (r >= 4126 && r < 4146));
            held.clear(); held.add(Input.Keys.D);
            if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        };
    }

    /** The falling-platform message is up before a continuous walk reaches the lip of the pit (#200). */
    @Test void trapsRoomPlatformMessageIsReadableBeforeAContinuousWalkReachesThePit() {
        Level level = room(5);
        Lead lead = walk(level, Msg.HINT_TRAP_PLATFORM.t(Language.ENGLISH), 0.5f, () -> {
            Rectangle b = level.player.hitboxBounds();
            return b.x + b.width >= 4032;
        }, trapsDriver(level));
        assertTrue(lead.fullBefore >= .9f, "Platform message fully visible for at least 1 s before the pit: " + lead.fullBefore);
    }

    /** The bomb message is up before the same walk, after crossing the platform, touches the bomb (#200). */
    @Test void trapsRoomBombMessageIsReadableBeforeAContinuousWalkReachesTheBomb() {
        Level level = room(5);
        Lead lead = walk(level, Msg.HINT_TRAP_STUMP.t(Language.ENGLISH), 1.5f, () -> {
            Rectangle b = level.player.hitboxBounds();
            return b.x + b.width >= 4352 + 3;
        }, trapsDriver(level));
        assertTrue(lead.fullBefore >= .9f, "Bomb message fully visible for at least 1 s before the bomb is touched: " + lead.fullBefore);
        assertTrue(lead.longestGapAfter <= .7f, "and it stays up while the hero runs clear: " + lead.longestGapAfter);
    }

    // ---- D48: one new trap in each of the ladder, wall, platform and stone rooms. The hint of each trap is up (fully
    // visible for about a second) before a continuous walk from the entrance reaches the trap's trigger. ----

    private <T> T first(Level level, Class<T> type) {
        return level.objects.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    @Test void ladderRoomThornRootMessageIsReadableBeforeAContinuousWalkReachesTheRootsZone() {
        Level level = room(1);
        var root = first(level, com.jlogicsoftware.kimeria.entity.objects.ThornTrap.class);
        Lead lead = walk(level, Msg.HINT_LADDER_ROOT.t(Language.ENGLISH), 0.3f, () -> level.player.hitboxBounds().overlaps(root.triggerZone()));
        assertTrue(lead.fullBefore >= .9f, "Root message fully visible for at least 0.9 s before the zone: " + lead.fullBefore);
    }

    @Test void wallRoomFallingBranchMessageIsReadableBeforeAContinuousWalkReachesTheBranchZone() {
        Level level = room(2);
        var branch = first(level, com.jlogicsoftware.kimeria.entity.objects.FallingBranch.class);
        Lead lead = walk(level, Msg.HINT_WALL_BRANCH.t(Language.ENGLISH), 0.3f, () -> level.player.hitboxBounds().overlaps(branch.zone()));
        assertTrue(lead.fullBefore >= .9f, "Branch message fully visible for at least 0.9 s before the zone: " + lead.fullBefore);
    }

    @Test void platformRoomFallingStoneMessageIsReadableBeforeAContinuousWalkReachesTheStoneZone() {
        Level level = room(3);
        var stone = first(level, com.jlogicsoftware.kimeria.entity.objects.FallingStone.class);
        Lead lead = walk(level, Msg.HINT_PLATFORM_STONE.t(Language.ENGLISH), 0.3f, () -> level.player.hitboxBounds().overlaps(stone.zone()));
        assertTrue(lead.fullBefore >= .9f, "Stone message fully visible for at least 0.9 s before the zone: " + lead.fullBefore);
    }

    @Test void stoneRoomStumpMessageIsReadableBeforeAContinuousWalkTouchesTheStump() {
        Level level = room(4);
        var stump = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap.class::cast).findFirst().orElseThrow();
        Lead lead = walk(level, Msg.HINT_STONE_STUMP.t(Language.ENGLISH), 0.3f, () -> level.player.hitboxBounds().overlaps(stump.triggerBox()));
        assertTrue(lead.fullBefore >= .9f, "Stump message fully visible for at least 0.9 s before the stump is touched: " + lead.fullBefore);
    }

    /** The lever messages keep their lead now that the trap hint follows them closely (they end just before the lever is in reach). */
    @Test void leverMessagesOfTheTrapRoomsKeepTheirLeadWithTheTrapZonesNextToThem() {
        for (var entry : new Object[][]{{1, Msg.HINT_LADDER_LEVER}, {2, Msg.HINT_WALL_LEVER}, {3, Msg.HINT_PLATFORM_LEVER}}) {
            Level level = room((Integer) entry[0]);
            Lead lead = walk(level, ((Msg) entry[1]).t(Language.ENGLISH), 0.5f, () -> level.player.canInteract(), (Integer) entry[0] == 1 ? ladderDriver(level) : () -> { held.clear(); held.add(Input.Keys.D); });
            assertTrue(lead.fullBefore >= .9f, entry[1] + ": " + lead.fullBefore);
        }
    }

    /** #235 review: the trap's own text is still the one on the zone while the hero crosses the trap's whole column, so the next text never swaps in mid-crossing. */
    @Test void eachTrapHintStaysTheZoneTextAcrossTheTrapsWholeColumn() {
        Object[][] cases = {{1, Msg.HINT_LADDER_ROOT, 832f, 912f}, {2, Msg.HINT_WALL_BRANCH, 1728f, 1776f},
            {3, Msg.HINT_PLATFORM_STONE, 2512f, 2560f}, {4, Msg.HINT_STONE_STUMP, 3488f, 3504f}};
        for (Object[] c : cases) {
            Level level = room((Integer) c[0]);
            for (float x = (Float) c[2]; x <= (Float) c[3]; x += 4) {
                level.player.position.set(x - 24, 272);
                frame(level);
                assertEquals(((Msg) c[1]).t(Language.ENGLISH), level.player.zoneHint(), "room " + c[0] + " centre x=" + x);
            }
        }
    }
}
