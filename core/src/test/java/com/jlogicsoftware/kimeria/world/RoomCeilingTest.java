package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.HintGeometry;
import com.jlogicsoftware.kimeria.ui.HintLayout;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #215: the side walls of a room are clamberable and wall-jumpable, so a room without a ceiling let the
 * hero climb out over the top and fall out of the level. Every room of every level must keep the hero
 * inside, whichever side wall he climbs and however long he keeps jumping. Rule: D38.
 */
class RoomCeilingTest {
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
    private GL20 previousGl, previousGl20;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        previousGl = Gdx.gl; previousGl20 = Gdx.gl20;
        GdxNativesLoader.load();
        // forest-1 builds a sky texture: decode the real PNG, stub only the GPU upload.
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (proxy, method, args) -> {
                Class<?> r = method.getReturnType();
                if (r == int.class) return 1;
                if (r == boolean.class) return false;
                if (r == float.class) return 0f;
                if (r == long.class) return 0L;
                return null;
            });
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getDeltaTime")) return STEP;
                Class<?> r = method.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == float.class) return 0f;
                return null;
            });
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getType" -> Application.ApplicationType.HeadlessDesktop;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
        Gdx.gl = previousGl; Gdx.gl20 = previousGl20;
    }

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private Level roomOf(String name, int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), name, Rectangle::new);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    /** Stands the hero on the floor against one side wall, holds into it and jumps as often as the game allows. */
    private float highestPointReached(Level level, boolean leftWall, int frames) {
        return highestPointReached(level, leftWall, frames, 0);
    }

    /** {@code lift} stands the hero that many px higher, e.g. on a ledge instead of the floor. */
    private float highestPointReached(Level level, boolean leftWall, int frames, float lift) {
        Rectangle room = level.currentRoomBounds();
        Player p = level.player;
        Rectangle b = p.hitboxBounds();
        float dx = leftWall ? room.x + 16 + 1 - b.x : room.x + room.width - 16 - 1 - (b.x + b.width);
        p.position.x += dx;
        p.position.y -= lift;
        float top = Float.MAX_VALUE;
        for (int i = 0; i < frames; i++) {
            held.clear();
            held.add(leftWall ? Input.Keys.A : Input.Keys.D);
            if (i % 2 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(level);
            Rectangle hb = p.hitboxBounds();
            top = Math.min(top, hb.y);
            assertTrue(hb.x >= room.x - 1 && hb.x + hb.width <= room.x + room.width + 1,
                level.currentRoomName() + (leftWall ? " left" : " right") + ": left the room sideways at frame " + i + " " + hb);
            assertTrue(hb.y >= room.y - 1, level.currentRoomName() + (leftWall ? " left" : " right")
                + ": climbed out over the top at frame " + i + " " + hb + " room " + room);
        }
        held.clear();
        return top;
    }

    private void assertCannotLeave(String map, int rooms) {
        for (int room = 0; room < rooms; room++) {
            for (boolean left : new boolean[]{true, false}) {
                highestPointReached(roomOf(map, room), left, 60 * 20);
            }
        }
    }

    @Test void forest0RoomsKeepTheHeroInsideWhateverWallHeClimbs() { assertCannotLeave("forest-0", 6); }
    @Test void forest1KeepsTheHeroInside() { assertCannotLeave("forest-1", 1); }
    /** The boss room (index 2) is enclosed by plain, non-climbable blocks and has no side walls to stand against. */
    @Test void forest3KeepsTheHeroInside() { assertCannotLeave("forest-3", 2); }

    @Test void everyRoomIsClosedFromAboveByASlabEndingAtItsTopEdge() {
        for (String map : new String[]{"forest-0", "forest-1", "forest-3"}) {
            int rooms = map.equals("forest-0") ? 6 : map.equals("forest-3") ? 3 : 1;
            for (int index = 0; index < rooms; index++) {
                Level level = roomOf(map, index);
                Rectangle room = level.currentRoomBounds();
                String where = map + " room " + index;
                var slab = level.collisionBlocks.stream().filter(b -> b.getY() + b.getHeight() == room.y).findFirst().orElseThrow();
                assertEquals(room.x, slab.getX(), 0.01f, where);
                assertEquals(room.width, slab.getWidth(), 0.01f, where);
                assertTrue(slab.getHeight() >= 32, where + ": thick enough that no frame of jump travel tunnels through");
                assertFalse(slab.isPlatform() || slab.isWall() || slab.isQuickSand(), where);
            }
        }
    }

    /** The ladder room's exit ledge (y 208, against the right wall) is a 112 px head start up that wall. */
    @Test void theLadderRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-0", 1), false, 60 * 20, 112);
    }

    /** Same for the platform room's exit ledge, which sits against the right wall. */
    @Test void thePlatformRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-0", 3), false, 60 * 20, 112);
    }

    /** D48: the platform room's falling stone starts flush with the room top, inside the room and under the D38 slab. */
    @Test void theFallingStoneOfThePlatformRoomStartsInsideTheRoomUnderTheSlab() {
        Level level = roomOf("forest-0", 3);
        Rectangle room = level.currentRoomBounds();
        var stone = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::cast).findFirst().orElseThrow();
        assertEquals(room.y, stone.bodyBounds().y, 0.01f, "flush with the room top");
        assertTrue(level.collisionBlocks.stream().anyMatch(b -> b.getY() + b.getHeight() == room.y
            && b.getX() <= stone.bodyBounds().x && b.getX() + b.getWidth() >= stone.bodyBounds().x + stone.bodyBounds().width), "the slab lies over it");
        assertTrue(stone.bodyBounds().x >= room.x && stone.bodyBounds().x + stone.bodyBounds().width <= room.x + room.width);
    }
}
