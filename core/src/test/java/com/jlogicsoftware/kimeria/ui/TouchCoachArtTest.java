package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D139 (#496): the touch tutorial's art exists at its registered size, reads as itself and stands out from every background the touch controls sit on.
 * The hand is cream with a navy outline (as the knob), the chevron and the ring are amber with a navy outline, the ring is hollow so the button shows
 * through, and the pressed hand differs from the idle one.
 */
class TouchCoachArtTest {
    private static final int NAVY = 0x14122b, CREAM = 0xfdf3d0, AMBER = 0xffc83a;
    private static final int DARK_BAR = 0x10101c, BACKGROUND = 0x2c3f66, GROUND = 0x7a4e2c;
    private static final double EDGE_CONTRAST = 3.0;
    private static final int HAND = 32, CHEVRON = 16, RING = 64, MIN_HAND_PIXELS = 250, MIN_CHEVRON_PIXELS = 40;

    @Test void everySpriteHasItsRegisteredSizeAndASourceGrid() throws Exception {
        String sizes = Files.readString(root().resolve("tools/sprite_layouts.py"));
        for (Object[] sprite : new Object[][] {{"hand", HAND}, {"hand-down", HAND}, {"chevron", CHEVRON}, {"ring", RING}}) {
            String name = (String) sprite[0];
            int size = (int) sprite[1];
            assertTrue(sizes.contains("'" + name + "': (" + size + ", " + size + ")"), name + " is registered in TOUCH_CONTROL_SIZES");
            BufferedImage png = load(name);
            assertEquals(size, png.getWidth(), name);
            assertEquals(size, png.getHeight(), name);
            assertTrue(Files.exists(root().resolve("art/hud/touch/" + name + ".grid")), name + " has its source grid");
        }
    }

    @Test void theHandIsCreamWithANavyOutlineAndTheFingertipSitsWhereTheCodeDrawsIt() throws Exception {
        for (String name : new String[] {"hand", "hand-down"}) {
            BufferedImage png = load(name);
            assertTrue(count(png, CREAM) >= MIN_HAND_PIXELS, name + " is mostly cream");
            assertTrue(count(png, NAVY) > 0, name + " has its outline");
            int tipY = name.equals("hand") ? (int) TouchControls.HAND_TIP_Y : (int) TouchControls.HAND_TIP_DOWN_Y;
            int tipX = (int) TouchControls.HAND_TIP_X;
            assertTrue(opaque(png, tipX, tipY + 1), name + ": the art is there under the fingertip hotspot");
            for (int y = 0; y < tipY - 1; y++) for (int x = 0; x < HAND; x++)
                assertTrue(!opaque(png, x, y) || (png.getRGB(x, y) & 0xffffff) == AMBER, name + ": only the press rays rise above the fingertip, at " + x + "," + y);
        }
        BufferedImage idle = load("hand"), down = load("hand-down");
        int differing = 0;
        for (int y = 0; y < HAND; y++) for (int x = 0; x < HAND; x++) if (idle.getRGB(x, y) != down.getRGB(x, y)) differing++;
        assertTrue(differing >= 20, "the pressed hand is drawn differently, not only tinted: " + differing + " pixels");
        assertTrue(count(down, AMBER) > count(idle, AMBER), "the pressed hand carries the amber press rays");
    }

    @Test void theChevronAndTheRingAreAmberWithANavyOutline() throws Exception {
        BufferedImage chevron = load("chevron");
        assertTrue(count(chevron, AMBER) >= MIN_CHEVRON_PIXELS, "the chevron is mostly amber");
        assertTrue(count(chevron, NAVY) > 0);
        BufferedImage ring = load("ring");
        assertTrue(count(ring, AMBER) > 0 && count(ring, NAVY) > 0);
        assertFalse(opaque(ring, RING / 2, RING / 2), "the ring is hollow, so the button's pictogram shows through");
        assertTrue(opaque(ring, RING / 2, 2) || opaque(ring, RING / 2, 3), "the ring reaches the art's edge so it clears the button");
    }

    /** Over the dark bar, the night background and the ground, either the fill or the outline contrasts at 3:1. */
    @Test void everySpriteReadsOnTheDarkBarTheBackgroundAndTheGround() throws Exception {
        int[][] inks = {{CREAM, NAVY}, {CREAM, NAVY}, {AMBER, NAVY}, {AMBER, NAVY}};
        String[] names = {"hand", "hand-down", "chevron", "ring"};
        for (int i = 0; i < names.length; i++) for (int background : new int[] {DARK_BAR, BACKGROUND, GROUND}) {
            double best = Math.max(contrast(luminance(inks[i][0]), luminance(background)), contrast(luminance(inks[i][1]), luminance(background)));
            assertTrue(best >= EDGE_CONTRAST, names[i] + " on " + Integer.toHexString(background) + " = " + best);
        }
    }

    /** Left-handed in a side bar the stick sits at W - max(bar / 2, 52): the amber chevrons ahead of the right-drag must still be wholly on screen. */
    @Test void theChevronsStayOnScreenLeftHandedInEveryBarWidthAndAreUntouchedWhereTheyFit() {
        float width = 640f, half = TouchControls.CHEVRON_SIZE / 2;
        for (float bar = 0f; bar <= 300f; bar += 4f) {
            float stick = width - TouchControls.sideStickX(bar);
            float near = TouchControls.chevronX(stick, width, 52f), far = TouchControls.chevronX(stick, width, 64f);
            assertTrue(far + half <= width + 1e-3f, "bar " + bar + ": the far chevron ends at " + (far + half) + " of " + width);
            assertTrue(near - half >= stick - 1e-3f - 44f, "bar " + bar + ": the near chevron stays beside the stick");
            assertEquals(12f, far - near, 1e-3f, "bar " + bar + ": spacing kept");
        }
        assertEquals(100f + 52f, TouchControls.chevronX(100f, 640f, 52f), 0f, "right-handed (or wide bar): unchanged");
        assertEquals(100f + 64f, TouchControls.chevronX(100f, 640f, 64f), 0f);
    }

    private static int count(BufferedImage png, int rgb) {
        int n = 0;
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) if (opaque(png, x, y) && (png.getRGB(x, y) & 0xffffff) == rgb) n++;
        return n;
    }

    private static boolean opaque(BufferedImage png, int x, int y) { return (png.getRGB(x, y) >>> 24) != 0; }

    private static Path root() {
        Path dir = new File("").getAbsoluteFile().toPath();
        while (dir != null && !Files.exists(dir.resolve("tools/sprite_layouts.py"))) dir = dir.getParent();
        assertNotNull(dir, "the repository root");
        return dir;
    }

    private static BufferedImage load(String name) throws Exception {
        try (var input = TouchCoachArtTest.class.getClassLoader().getResourceAsStream("images/HUD/touch/" + name + ".png")) {
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
