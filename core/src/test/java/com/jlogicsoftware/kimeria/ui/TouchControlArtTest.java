package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D55 (#226): the touch controls must stay readable on the dark portrait strip / side bars and over the level background and ground.
 * Measures the shipped PNGs and the label colour with WCAG relative luminance, so a darker redraw fails here.
 */
class TouchControlArtTest {
    private static final int DARK_BAR = 0x10101c, BACKGROUND = 0x2c3f66, GROUND = 0x7a4e2c; // BACKGROUND: the forest maps' night (D92)
    private static final double BAR_CONTRAST = 7.0, PRESSED_DIFFERENCE = 1.4, EDGE_CONTRAST = 3.0;
    private static final int RING_OFFSET = 2, ICON_SIDE_X = 12;

    @Test void buttonStickAndKnobFillsAreLightAndStandOutFromTheDarkBars() throws Exception {
        for (String name : new String[] {"disc", "jump", "pause", "knob"}) {
            assertTrue(contrast(luminance(centre(name)), luminance(DARK_BAR)) >= BAR_CONTRAST, name + " fill vs dark bar");
        }
        assertTrue(contrast(luminance(centre("knob")), luminance(centre("base"))) >= EDGE_CONTRAST, "knob vs stick base");
    }

    /** The outermost opaque pixel is the dark outline, the next the white ring: one of them contrasts with each background. */
    @Test void everyControlHasADarkOutlineAndALightRingSoItReadsOnBarBackgroundAndGround() throws Exception {
        for (String name : new String[] {"disc", "disc-down", "jump", "pause", "base", "knob"}) {
            BufferedImage png = load(name);
            int outline = png.getRGB(png.getWidth() / 2, firstOpaque(png)), ring = png.getRGB(png.getWidth() / 2, firstOpaque(png) + RING_OFFSET);
            for (int background : new int[] {DARK_BAR, BACKGROUND, GROUND}) {
                double best = Math.max(contrast(luminance(outline), luminance(background)), contrast(luminance(ring), luminance(background)));
                assertTrue(best >= EDGE_CONTRAST, name + " edge vs " + Integer.toHexString(background) + " = " + best);
            }
        }
    }

    @Test void artIsOpaqueAndPressedStateIsClearlyDifferent() throws Exception {
        for (String name : new String[] {"disc", "disc-down", "jump", "jump-down", "pause", "pause-down", "knob", "knob-down", "base"}) {
            assertEquals(255, centre(name) >>> 24, name + " is opaque in the middle");
        }
        for (String name : new String[] {"disc", "jump", "pause", "knob"}) {
            double idle = luminance(centre(name)), down = luminance(centre(name + "-down"));
            assertNotEquals(centre(name), centre(name + "-down"), name);
            assertTrue(Math.abs(idle - down) > 0.05 || contrast(idle, down) >= PRESSED_DIFFERENCE, name + " pressed differs");
        }
        assertTrue(TouchControls.CONTROL_ALPHA >= 0.9f, "idle controls must be near-opaque");
    }

    /**
     * The story skip button (#336, D77) sits on the black story screen only. Its outermost outline is white (the other
     * discs' navy outline measures 0.20 in OKLab against black, under the 0.28 active floor of D76), so the silhouette,
     * the fill and the pressed state all stand out from black.
     */
    @Test void theSkipButtonReadsOnTheBlackStoryScreenAndTheIconIsNotThePauseBars() throws Exception {
        int black = 0x000000;
        for (String name : new String[] {"skip", "skip-down"}) {
            BufferedImage png = load(name);
            assertTrue(contrast(luminance(png.getRGB(png.getWidth() / 2, firstOpaque(png))), luminance(black)) >= BAR_CONTRAST, name + " outline vs black");
            assertTrue(contrast(luminance(centre(name)), luminance(black)) >= BAR_CONTRAST, name + " fill vs black");
            assertEquals(255, centre(name) >>> 24, name + " is opaque");
        }
        assertNotEquals(centre("skip"), centre("skip-down"));
        BufferedImage skip = load("skip"), pause = load("pause");
        assertNotEquals(pause.getRGB(pause.getWidth() / 2, pause.getHeight() / 2), skip.getRGB(skip.getWidth() / 2, skip.getHeight() / 2),
            "the gap between the pause bars is filled by the play triangle");
    }

    private static int firstOpaque(BufferedImage png) {
        for (int y = 0; y < png.getHeight(); y++) if ((png.getRGB(png.getWidth() / 2, y) >>> 24) != 0) return y;
        throw new AssertionError("empty image");
    }

    /** A fill pixel: the middle, or beside the arrow / pause bars for the buttons that carry an icon. */
    private static int centre(String name) throws Exception {
        BufferedImage png = load(name);
        boolean icon = name.startsWith("jump") || name.startsWith("pause") || name.startsWith("skip");
        return png.getRGB(icon ? ICON_SIDE_X : png.getWidth() / 2, png.getHeight() / 2);
    }

    private static BufferedImage load(String name) throws Exception {
        try (var input = TouchControlArtTest.class.getClassLoader().getResourceAsStream("images/HUD/touch/" + name + ".png")) {
            assertNotNull(input, name);
            return javax.imageio.ImageIO.read(input);
        }
    }

    private static double luminance(int rgb) {
        return 0.2126 * channel(rgb >> 16 & 0xff) + 0.7152 * channel(rgb >> 8 & 0xff) + 0.0722 * channel(rgb & 0xff);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double a, double b) {
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
