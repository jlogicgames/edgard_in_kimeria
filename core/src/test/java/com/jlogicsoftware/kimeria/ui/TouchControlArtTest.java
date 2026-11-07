package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D55 (#226): the touch controls must stay readable on the dark portrait strip / side bars and over sky and ground.
 * Measures the shipped PNGs and the label colour with WCAG relative luminance, so a darker redraw fails here.
 */
class TouchControlArtTest {
    private static final int DARK_BAR = 0x10101c, SKY = 0x87ceeb, GROUND = 0x7a4e2c;
    private static final double BAR_CONTRAST = 7.0, LABEL_CONTRAST = 7.0, PRESSED_DIFFERENCE = 1.4, EDGE_CONTRAST = 3.0;
    private static final int RING_OFFSET = 2, ICON_SIDE_X = 12;

    @Test void buttonStickAndKnobFillsAreLightAndStandOutFromTheDarkBars() throws Exception {
        for (String name : new String[] {"disc", "jump", "pause", "knob"}) {
            assertTrue(contrast(luminance(centre(name)), luminance(DARK_BAR)) >= BAR_CONTRAST, name + " fill vs dark bar");
        }
        assertTrue(contrast(luminance(centre("knob")), luminance(centre("base"))) >= EDGE_CONTRAST, "knob vs stick base");
    }

    /** The outermost opaque pixel is the dark outline, the next the white ring: one of them contrasts with each background. */
    @Test void everyControlHasADarkOutlineAndALightRingSoItReadsOnBarSkyAndGround() throws Exception {
        for (String name : new String[] {"disc", "disc-down", "jump", "pause", "base", "knob"}) {
            BufferedImage png = load(name);
            int outline = png.getRGB(png.getWidth() / 2, firstOpaque(png)), ring = png.getRGB(png.getWidth() / 2, firstOpaque(png) + RING_OFFSET);
            for (int background : new int[] {DARK_BAR, SKY, GROUND}) {
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

    @Test void buttonLabelsAreReadableOnIdleAndPressedButtons() throws Exception {
        double label = luminance(com.badlogic.gdx.graphics.Color.rgb888(TouchControls.LABEL_COLOR));
        assertTrue(contrast(luminance(centre("disc")), label) >= LABEL_CONTRAST, "label on idle button");
        assertTrue(contrast(luminance(centre("disc-down")), label) >= LABEL_CONTRAST, "label on pressed button");
    }

    private static int firstOpaque(BufferedImage png) {
        for (int y = 0; y < png.getHeight(); y++) if ((png.getRGB(png.getWidth() / 2, y) >>> 24) != 0) return y;
        throw new AssertionError("empty image");
    }

    /** A fill pixel: the middle, or beside the arrow / pause bars for the buttons that carry an icon. */
    private static int centre(String name) throws Exception {
        BufferedImage png = load(name);
        boolean icon = name.startsWith("jump") || name.startsWith("pause");
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
