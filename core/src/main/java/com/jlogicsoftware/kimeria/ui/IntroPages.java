package com.jlogicsoftware.kimeria.ui;

/**
 * The opening and ending story cut into static pages (#336). Paragraphs ({@code \n\n}) are packed greedily, top to
 * bottom, onto a page while they fit {@link #MAX_HEIGHT}; the split is computed once at load with the body font, so no
 * page overflows in any language. A single paragraph taller than a page cannot be split without changing the text, so
 * it is an error, never a silent overflow.
 */
public final class IntroPages {
    /** Measures one paragraph wrapped at the page width, in the body font (the game passes {@code Overlay.bodyHeight}). */
    public interface Measure { float height(String paragraph); }

    /** Distance from the screen top to the first line, and the room a page may use (the page-turn cue sits below it). */
    public static final float TEXT_TOP = 40f, MAX_HEIGHT = 264f;
    /** The lowest a page's text may reach, from the screen top, whatever its first line's top. */
    public static final float PAGE_BOTTOM = TEXT_TOP + MAX_HEIGHT;
    /** One blank body line between paragraphs. */
    public static final float PARAGRAPH_GAP = FontSizes.BODY;

    private final String[] paragraphs;
    private final float[] tops, heights;
    private final int[] firsts; // firsts[page] .. firsts[page + 1] - 1; one extra entry closes the last page

    private IntroPages(String[] paragraphs, float[] tops, float[] heights, int[] firsts) {
        this.paragraphs = paragraphs; this.tops = tops; this.heights = heights; this.firsts = firsts;
    }

    public static IntroPages split(String text, Measure measure) { return split(text, measure, TEXT_TOP); }

    /**
     * As {@link #split(String, Measure)}, with the first line at {@code top} instead of {@link #TEXT_TOP}: the ending puts the
     * Kimeria arms above its text (#416, D95). The bottom of the page stays {@link #PAGE_BOTTOM}, so a page holds
     * {@code PAGE_BOTTOM - top}.
     */
    public static IntroPages split(String text, Measure measure, float top) {
        float room = PAGE_BOTTOM - top;
        String[] parts = text.split("\n\n");
        float[] heights = new float[parts.length], tops = new float[parts.length];
        int[] firsts = new int[parts.length + 1];
        int pages = 0;
        float used = 0f;
        for (int i = 0; i < parts.length; i++) {
            heights[i] = measure.height(parts[i]);
            if (heights[i] > room) throw new IllegalStateException("Paragraph " + i + " is " + heights[i]
                + " px high, a page holds " + room + ": " + parts[i]);
            boolean fits = i > 0 && used + PARAGRAPH_GAP + heights[i] <= room;
            if (fits) {
                tops[i] = top + used + PARAGRAPH_GAP;
                used += PARAGRAPH_GAP + heights[i];
            } else {
                firsts[pages++] = i;
                tops[i] = top;
                used = heights[i];
            }
        }
        firsts[pages] = parts.length;
        return new IntroPages(parts, tops, heights, java.util.Arrays.copyOf(firsts, pages + 1));
    }

    public int pageCount() { return firsts.length - 1; }
    public int paragraphCount() { return paragraphs.length; }
    public int first(int page) { return firsts[page]; }
    /** One past the last paragraph of {@code page}. */
    public int end(int page) { return firsts[page + 1]; }
    public String paragraph(int index) { return paragraphs[index]; }
    /** Distance from the screen top to the paragraph's first line. */
    public float top(int index) { return tops[index]; }
    public float height(int index) { return heights[index]; }
    /** Bottom edge (from the screen top) of the last paragraph on {@code page}. */
    public float bottom(int page) { int last = end(page) - 1; return tops[last] + heights[last]; }
}
