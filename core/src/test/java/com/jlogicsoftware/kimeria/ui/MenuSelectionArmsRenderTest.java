package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #415: {@code Overlay.buttons} draws the Kimeria arms (frame LIT) exactly twice a frame, one each side of the selected
 * button and nowhere else, and they follow the selection. Recorded through a Batch that keeps every region draw.
 */
class MenuSelectionArmsRenderTest {
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    /** One distinct region per arms frame, so a draw tells which frame it was. */
    private final TextureRegion[] armsFrames = new TextureRegion[KimeriaArms.FRAME_COUNT];
    private final List<float[]> armsDraws = new ArrayList<>();
    private final List<Integer> armsDrawFrames = new ArrayList<>();
    private Overlay overlay;
    private Batch batch;

    @BeforeEach void setUp() {
        harness.setUp();
        for (int i = 0; i < armsFrames.length; i++) armsFrames[i] = new TextureRegion();
        Assets assets = new Assets() {
            @Override public BitmapFont font(int size) { return new MetricsFont(size); }
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                assertEquals(KimeriaArms.ART, path);
                return armsFrames[(int) x / KimeriaArms.WIDTH];
            }
        };
        overlay = new Overlay(assets, 640, 360, new KimeriaArms(assets));
        Color color = new Color(Color.WHITE);
        Matrix4 matrix = new Matrix4();
        batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "draw" -> {
                    if (args.length == 5 && args[0] instanceof TextureRegion region) {
                        for (int frame = 0; frame < armsFrames.length; frame++) if (region == armsFrames[frame]) {
                            armsDraws.add(new float[]{(float) args[1], (float) args[2], (float) args[3], (float) args[4]});
                            armsDrawFrames.add(frame);
                        }
                    }
                    return null;
                }
                case "setColor" -> { if (args.length == 1) color.set((Color) args[0]); else color.set((float) args[0], (float) args[1], (float) args[2], (float) args[3]); return null; }
                case "getColor" -> { return color; }
                case "getPackedColor" -> { return color.toFloatBits(); }
                case "setPackedColor" -> { Color.abgr8888ToColor(color, (float) args[0]); return null; }
                case "getProjectionMatrix", "getTransformMatrix" -> { return matrix; }
                default -> {
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    if (r == float.class) return 0f;
                    return null;
                }
            }
        });
    }

    @AfterEach void tearDown() { Shaders.dispose(); harness.restoreGlobals(); }

    private void frame(Menu menu, float startY) {
        armsDraws.clear(); armsDrawFrames.clear();
        overlay.beginFrame();
        overlay.buttons(batch, menu, 320f, startY, null, false, false, false, () -> { }, () -> { });
        overlay.flushText(batch);
    }

    @Test void theArmsFlankOnlyTheSelectedButtonAndFollowTheSelection() {
        Menu menu = new Menu(null, null, List.of(new MenuItem("Continue", () -> { }), new MenuItem("New Game", () -> { }),
            new MenuItem("About", () -> { }), new MenuItem("Options", () -> { }), new MenuItem("Exit", () -> { })), null);
        float startY = 79f, width = overlay.buttonsWidth(menu), x = 320f - width / 2f;
        for (int selected = 0; selected < 5; selected++) {
            assertEquals(selected, menu.selected);
            frame(menu, startY);
            float by = startY + selected * (Overlay.BUTTON_H + Overlay.BUTTON_GAP);
            assertEquals(2, armsDraws.size(), "exactly two arms, row " + selected);
            assertEquals(List.of(KimeriaArms.LIT, KimeriaArms.LIT), armsDrawFrames, "the lit arms");
            float[] left = armsDraws.get(0), right = armsDraws.get(1);
            assertArrayEquals(new float[]{Overlay.selectionArmsLeftX(x), Overlay.selectionArmsTop(by), 24f, 36f}, left, 0f, "left, row " + selected);
            assertArrayEquals(new float[]{Overlay.selectionArmsRightX(x, width), Overlay.selectionArmsTop(by), 24f, 36f}, right, 0f, "right, row " + selected);
            assertTrue(left[0] + left[2] < x - Overlay.SELECTED_FRAME && right[0] > x + width + Overlay.SELECTED_FRAME, "outside the frame");
            assertTrue(left[1] >= by && left[1] + left[3] <= by + Overlay.BUTTON_H, "inside the selected row");
            menu.moveSelection(1);
        }
        assertEquals(0, menu.selected, "wrapped back to the first row");
    }

    @Test void noSelectionDrawsNoArms() {
        Menu menu = new Menu(null, null, List.of(new MenuItem(() -> "Hidden", () -> { }, () -> false)), null);
        frame(menu, 79f);
        assertTrue(armsDraws.isEmpty());
    }
}
