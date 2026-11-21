package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.Assets;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D117: which skip hint the story queues for each mode, and the touch > pad > keyboard order. */
class IntroSkipHintTest {
    private static class MetricsFont extends BitmapFont {
        MetricsFont(int size) {
            super(new BitmapFontData(new HeadlessFiles().internal("fonts/generated/QuestSquare-" + size + ".fnt"), false), new TextureRegion(), true);
        }
        @Override protected void load(BitmapFontData data) { }
    }

    @Test void theModeOrderIsTouchThenPadThenKeyboard() {
        assertEquals(IntroScreen.SkipHint.NONE, IntroScreen.skipHintFor(true, true));
        assertEquals(IntroScreen.SkipHint.NONE, IntroScreen.skipHintFor(true, false));
        assertEquals(IntroScreen.SkipHint.GAMEPAD, IntroScreen.skipHintFor(false, true));
        assertEquals(IntroScreen.SkipHint.KEYBOARD, IntroScreen.skipHintFor(false, false));
    }

    @Test void renderQueuesOnlyTheHintOfTheChosenMode() {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            List<BitmapFontCache> queued = new ArrayList<>();
            Overlay overlay = new Overlay(new Assets() {
                @Override public BitmapFont font(int size) { return new MetricsFont(size); }
            }, 640, 360, null) {
                @Override public void queueBlock(BitmapFontCache block, float alpha) { if (alpha > 0f) queued.add(block); }
            };
            String story = "a\n\nb";
            IntroPages pages = IntroPages.split(story, p -> 100f);
            BitmapFontCache[] blocks = new BitmapFontCache[pages.paragraphCount()];
            for (int i = 0; i < blocks.length; i++) blocks[i] = overlay.bodyBlock("x", 320f, 40f, 512f);
            BitmapFontCache keyboard = overlay.hintBlock("Esc: skip", 628f, IntroScreen.SKIP_HINT_TOP);
            BitmapFontCache pad = overlay.hintBlock("Start: skip", 628f, IntroScreen.SKIP_HINT_TOP);
            IntroScreen intro = new IntroScreen(story, pages, blocks, false, 640f, null);
            intro.setSkipHints(keyboard, pad);
            intro.update(IntroScreen.FADE_IN_SECONDS);

            intro.skipHint(IntroScreen.SkipHint.NONE);
            intro.render(null, overlay);
            assertFalse(queued.contains(keyboard) || queued.contains(pad), "touch: no text");
            queued.clear();
            intro.skipHint(IntroScreen.SkipHint.GAMEPAD);
            intro.render(null, overlay);
            assertTrue(queued.contains(pad)); assertFalse(queued.contains(keyboard));
            queued.clear();
            intro.skipHint(IntroScreen.SkipHint.KEYBOARD);
            intro.render(null, overlay);
            assertTrue(queued.contains(keyboard)); assertFalse(queued.contains(pad));
        } finally {
            Gdx.files = previous;
        }
    }
}
