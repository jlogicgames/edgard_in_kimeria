package com.jlogicsoftware.kimeria.localization;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.ui.BossHud;
import com.jlogicsoftware.kimeria.ui.ControlsView;
import com.jlogicsoftware.kimeria.ui.FontSizes;
import com.jlogicsoftware.kimeria.ui.HealerDialogueView;
import com.jlogicsoftware.kimeria.ui.HintLayout;
import com.jlogicsoftware.kimeria.ui.ControlsView;
import com.jlogicsoftware.kimeria.ui.Hud;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.MenuItem;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.ui.TouchLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real shipped font metrics, without GPU textures. Rendering itself is checked separately. */
class LocalizationLayoutTest {
    private static class MetricsFont extends BitmapFont {
        MetricsFont(int size) {
            super(new BitmapFontData(new HeadlessFiles().internal("fonts/generated/QuestSquare-" + size + ".fnt"), false),
                new TextureRegion(), true);
        }
        @Override protected void load(BitmapFontData data) { /* Metrics require no texture UVs. */ }
    }

    private static float height(BitmapFont font, String text, float width) {
        return new GlyphLayout(font, text, Color.WHITE, width, Align.center, true).height;
    }

    /** Wrapped line count (not a pixel threshold: the two-line limit must not depend on the font size). */
    private static int lines(BitmapFont font, String text, float width) {
        return new GlyphLayout(font, text, Color.WHITE, width, Align.center, true).runs.size;
    }

    /** D118: the skip hint's rect (Y down, as on screen) with the real font, in every language and both modes. */
    @Test void skipHintSitsInTheCornerClearOfThePageAndTheCue() {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            Overlay overlay = new Overlay(new Assets() {
                @Override public BitmapFont font(int size) { return new MetricsFont(size); }
            }, 640, 360, null);
            float right = 640f - com.jlogicsoftware.kimeria.ui.IntroScreen.SKIP_HINT_MARGIN, top = com.jlogicsoftware.kimeria.ui.IntroScreen.SKIP_HINT_TOP;
            float cueRight = 320f + com.jlogicsoftware.kimeria.ui.IntroScreen.CUE_SIZE / 2f;
            assertTrue(com.jlogicsoftware.kimeria.ui.IntroScreen.CUE_TOP > com.jlogicsoftware.kimeria.ui.IntroBook.COVER_BOTTOM, "the page-turn cue sits below the book's bottom edge (D133)");
            for (Language language : Language.values()) for (Msg message : new Msg[]{Msg.INTRO_SKIP_KEYBOARD, Msg.INTRO_SKIP_GAMEPAD}) {
                var layout = overlay.hintBlock(message.t(language), right, top).getLayouts().first();
                float left = right + layout.runs.first().x, bottom = top + layout.height;
                String where = language.code() + " " + message + " rect x " + left + ".." + (left + layout.width) + ", y " + top + ".." + bottom;
                assertTrue(left > cueRight, "right of the page cue: " + where);
                assertTrue(bottom <= 360f, "inside the screen: " + where);
                assertTrue(top > com.jlogicsoftware.kimeria.ui.IntroPages.PAGE_BOTTOM, "below the page area: " + where);
                assertTrue(top > com.jlogicsoftware.kimeria.ui.IntroBook.COVER_BOTTOM, "below the book's bottom edge, on the black (D133): " + where);
            }
        } finally {
            Gdx.files = previous;
        }
    }

    /** BossHud draws the label at the frame's left edge; the elixir icon sits on the same row, ELIXIR_OFFSET (176 px) in. */
    @Test void bossHudLabelEndsBeforeTheElixirIconInEveryLanguage() {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            for (boolean large : new boolean[]{false, true}) {
                BitmapFont label = new MetricsFont(FontSizes.label(large));
                for (Language language : Language.values()) {
                    String text = Msg.BOSS.t(language);
                    float width = new GlyphLayout(label, text).width;
                    assertTrue(width <= BossHud.ELIXIR_OFFSET, (large ? "large " : "") + language.code() + " boss label \"" + text + "\" is " + width + " px; the elixir icon starts at " + BossHud.ELIXIR_OFFSET + " px");
                }
            }
        } finally {
            Gdx.files = previous;
        }
    }

    /** Both text tiers (D137): the nominal sizes and the large ones a small picture uses must fit every box in five languages. */
    @Test void localizedTextFitsActualMenusDialogueHelpAndTouchBounds() {
        textFitsAtTier(false);
        textFitsAtTier(true);
    }

    private void textFitsAtTier(boolean large) {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            BitmapFont body = new MetricsFont(FontSizes.body(large)), dialogue = new MetricsFont(FontSizes.dialogue(large)), button = new MetricsFont(FontSizes.BUTTON),
                hint = new MetricsFont(FontSizes.label(large)), rotate = new MetricsFont(FontSizes.ROTATE_HINT), title = new MetricsFont(FontSizes.TITLE),
                zone = new MetricsFont(FontSizes.ZONE), context = new MetricsFont(FontSizes.label(large));
            Assets assets = new Assets() {
                @Override public BitmapFont font(int size) { return new MetricsFont(size); }
            };
            assets.setLargeText(large);
            Overlay overlay = new Overlay(assets, 640, 360, null);
            for (Language language : Language.values()) {
                String prefix = (large ? "large " : "") + language.code() + " ";
                for (Msg message : new Msg[]{Msg.TITLE, Msg.NEW_GAME, Msg.OPTIONS, Msg.ASSIST, Msg.ABOUT, Msg.PAUSE_MENU, Msg.LOADING})
                    assertTrue(new GlyphLayout(title, message.t(language)).width <= 608, prefix + message);
                for (Msg message : new Msg[]{Msg.PLAY, Msg.CONTINUE, Msg.NEW_GAME, Msg.CONFIRM, Msg.CANCEL,
                    Msg.ABOUT, Msg.OPTIONS, Msg.ASSIST, Msg.EXIT, Msg.BACK, Msg.RESUME, Msg.RESTART_LEVEL, Msg.EXIT_TO_MENU, Msg.RULES, Msg.CONTROLS})
                    assertTrue(new GlyphLayout(button, message.t(language)).width <= 544, prefix + message);
                assertTrue(height(body, Msg.NEW_GAME_CONFIRMATION.t(language), 290) <= 80, prefix + "confirmation");
                assertTrue(height(body, Msg.ABOUT_RULES.t(language), 580) <= 140, prefix + "rules");
                assertTrue(height(dialogue, Msg.HEALER.t(language), 100) <= 20, prefix + "speaker name");
                assertTrue(new GlyphLayout(body, language.nativeName()).width <= 260, prefix + "language name");
                assertTrue(height(hint, Msg.MENU_HINT.t(language), 608) <= 26, prefix + "menu hint");
                assertTrue(lines(context, Msg.FIREBALL_STOCK_FULL.t(language), Hud.CONTEXT_WIDTH) <= 2, prefix + "full ammunition hint");
                // Map-authored tutorial hints (#249: 39 px, 608 px wide) must stay within two lines.
                for (Msg message : new Msg[]{Msg.HINT_TUTORIAL_WALK, Msg.HINT_TUTORIAL_WALK_TOUCH, Msg.HINT_LEVER_TORCH, Msg.HINT_LEVER_EXIT,
                    Msg.HINT_LADDER_LEVER, Msg.HINT_LADDER_CLIMB, Msg.HINT_LADDER_EXIT,
                    Msg.HINT_WALL_LEVER, Msg.HINT_WALL_PASS, Msg.HINT_PLATFORM_LEVER, Msg.HINT_PLATFORM_RIDE,
                    Msg.HINT_STONE_THROW, Msg.HINT_STONE_EXIT, Msg.HINT_STONE_ACROSS,
                    Msg.HINT_TRAP_PLATFORM, Msg.HINT_TRAP_STUMP,
                    Msg.HINT_LADDER_ROOT, Msg.HINT_WALL_BRANCH, Msg.HINT_PLATFORM_STONE, Msg.HINT_STONE_STUMP})
                    assertTrue(lines(zone, message.t(language), Hud.ZONE_WIDTH) <= 2, prefix + message);
                // Spoken healer/player lines are measured against the actual two-thirds regions
                // and frame-derived height in HealerDialogueTest, including every typed dialogue key.
                for (Msg message : new Msg[]{Msg.HEALER_RETURN, Msg.HEALER_BREW, Msg.HEALER_FIGHT})
                    assertTrue(new GlyphLayout(dialogue, message.t(language)).width <= 512, prefix + message);
                // D138 (#492): the touch buttons carry pictograms, so the only touch text is the portrait strip's rotate hint:
                // one line in the strip's 360 units minus the side margins, at the hint's own size.
                assertTrue(lines(rotate, Msg.ROTATE_HINT.t(language), TouchLayout.SPACE_WIDTH - 48f) <= 1, prefix + "rotate hint");
                // About pages (D135): the blurb and the rules each sit in the 610 px panel between the title and two buttons.
                for (String page : List.of(
                    Msg.ABOUT_GAME_STORY.t(language) + "\n\n" + Msg.ABOUT_GAME_PLAY.t(language) + "\n\n" + Msg.ABOUT_GAME_MORE.t(language),
                    Msg.ABOUT_RULES.t(language) + "\n\n" + Msg.ABOUT_RULES_CARRY.t(language)))
                    assertTrue(44 + height(body, page, 580) + 20 + 88 + 40 <= 332, prefix + "about page " + page.substring(0, 12));
                assertTrue(new GlyphLayout(button, Msg.ABOUT_GAME.t(language)).width <= 544, prefix + "page button");
                // Controls screen (D135), body font as drawn: hand headers in one 162 px line, the touch preview's button
                // labels in 96 px and its stick label in 80 px; rows and cells are measured in ControlsViewTest.
                for (Msg message : new Msg[]{Msg.RIGHT_HAND, Msg.LEFT_HAND})
                    assertTrue(lines(body, message.t(language), large ? ControlsView.HEADER_W_LARGE : ControlsView.HEADER_W) <= 1, prefix + message);
                for (Msg message : new Msg[]{Msg.ATTACK, Msg.FIREBALL, Msg.JUMP})
                    assertTrue(lines(body, message.t(language), ControlsView.BUTTON_LABEL_W) <= 1, prefix + message);
                assertTrue(lines(body, Msg.STICK.t(language), ControlsView.STICK_LABEL_W) <= 1, prefix + "stick label");
                // The Controls table itself is measured in ControlsViewTest (rows, columns, touch line).
                for (String label : List.of(Msg.LONGER_TRAP_WARNINGS.t(language) + ": " + Msg.OFF.t(language),
                    Msg.SCREEN_SHAKE.t(language) + ": " + Msg.OFF.t(language),
                    Msg.INSTANT_TEXT.t(language) + ": " + Msg.OFF.t(language),
                    Msg.LEFT_HANDED_LABEL.t(language) + ": " + Msg.ON.t(language),
                    Msg.LANGUAGE_LABEL.t(language) + ": " + language.nativeName(), Msg.RESTART_LEVEL.t(language))) {
                    Menu menu = new Menu(null, null, List.of(new MenuItem(label, () -> {})), null);
                    assertTrue(new GlyphLayout(button, label).width <= overlay.buttonsWidth(menu) - 48, prefix + label);
                    assertTrue(overlay.buttonsWidth(menu) <= Overlay.MAX_BUTTONS_W, prefix + label);
                }
                // The Accessibility menu is a fixed 440 px column (D62): the longest On/Off label must fit it.
                for (String label : List.of(Msg.INSTANT_TEXT.t(language) + ": " + Msg.OFF.t(language),
                    Msg.INSTANT_TEXT.t(language) + ": " + Msg.ON.t(language))) {
                    float width = new GlyphLayout(button, label).width;
                    assertTrue(width <= 440 - 48, prefix + label);
                }
            }
        } finally { Gdx.files = previous; }
    }

    /**
     * D70, D78: the 18 px ingredient checklist (the middle column between the portrait windows, from y 16, an icon beside each row, #540) ends above the
     * first choice, and every choice label with its two markers fits the middle column, in every language and state.
     */
    @Test void ingredientChecklistAndChoiceButtonsFitAtTheDialogueSize() {
        ingredientChecklistAndChoicesFit(false);
        ingredientChecklistAndChoicesFit(true);
    }

    private void ingredientChecklistAndChoicesFit(boolean large) {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            BitmapFont dialogue = new MetricsFont(FontSizes.dialogue(large));
            var view = new HealerDialogueView(new Assets() {
                @Override public TextureRegion region(String path, com.badlogic.gdx.graphics.Texture.TextureFilter filter) { return new TextureRegion(); }
            });
            float column = HealerDialogueView.MIDDLE_W;
            float top = HealerDialogueView.CHECKLIST_Y, choices = HealerDialogueView.CHOICE_Y;
            float widestRow = 0;
            float markers = 2 * (HealerDialogueView.MARKER_INSET + new GlyphLayout(dialogue, ">").width + 4);
            for (Language language : Language.values()) for (boolean paid : new boolean[]{false, true}) for (boolean done : new boolean[]{false, true}) {
                if (paid) {
                    GlyphLayout layout = new GlyphLayout(dialogue, view.checklistText(language, true, done, !done, done, done ? 3 : 0, 3), Color.WHITE, HealerDialogueView.PAID_TEXT_W, Align.center, true);
                    assertTrue(layout.width <= HealerDialogueView.PAID_TEXT_W + 1, language.code() + " paid message width");
                    assertTrue(top + layout.height <= choices - 4, language.code() + " paid message stays above the choices: " + layout.height);
                    continue;
                }
                // #540 (D132): each row is an icon, a gap and one line of text; the longest must fit beside its icon, and three rows stay above the choices.
                for (int row = 0; row < HealerDialogueView.ROWS; row++) {
                    String text = HealerDialogueView.rowText(row, language, done, !done, done, done ? 3 : 0, 3);
                    GlyphLayout layout = new GlyphLayout(dialogue, text, Color.WHITE, column, Align.left, false);
                    widestRow = Math.max(widestRow, layout.width);
                    float beside = column - HealerDialogueView.ROW_ICON - HealerDialogueView.ROW_GAP;
                    assertTrue(layout.width <= beside, language.code() + " row " + row + " fits beside its icon: " + layout.width + " of " + beside);
                    assertTrue(layout.height <= HealerDialogueView.ROW_ICON, language.code() + " one line per row");
                }
                float rowsBottom = top + (HealerDialogueView.ROWS - 1) * HealerDialogueView.ROW_STEP + HealerDialogueView.ROW_ICON;
                assertTrue(rowsBottom <= choices - 4, "three rows stay above the choices: " + rowsBottom);
            }
            System.out.printf("LAYOUT widest checklist row over five languages: %.1f px of %.1f beside the icon%n", widestRow, column - HealerDialogueView.ROW_ICON - HealerDialogueView.ROW_GAP);
            for (Language language : Language.values()) for (Msg message : new Msg[]{Msg.HEALER_RETURN, Msg.HEALER_BREW, Msg.HEALER_FIGHT})
                assertTrue(new GlyphLayout(dialogue, message.t(language)).width <= column - markers,
                    language.code() + " " + message + " in the " + column + " px choice button");
        } finally { Gdx.files = previous; }
    }

    /**
     * #211: the zone message (anchored 30% down the 368 px room, two lines at most, #249: 39 px font, 608 px wide) and the widest
     * contextual stack of every language, for keyboard, gamepad (both handedness) and touch wording, on screen
     * together. Hero standing (head at world y 272) or at a jump apex (58 px higher), label anchor 12 px below
     * the head, camera 0 and 8 px down. The placed label must clear the message by the gap as rectangles.
     */
    @Test void zoneMessageAndTheWidestContextualLabelNeverOverlapInAnyLanguageOrDevice() {
        labelsNeverOverlap(false);
        labelsNeverOverlap(true); // the label above the hero is 20 px on a small picture (D137)
    }

    private void labelsNeverOverlap(boolean large) {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            BitmapFont zone = new MetricsFont(FontSizes.ZONE), contextFont = new MetricsFont(FontSizes.label(large));
            float cap = contextFont.getCapHeight(), zoneCap = zone.getCapHeight(), zoneWorldY = 368 * .3f;
            for (Language language : Language.values()) {
                String up = Msg.UP.t(language), down = Msg.DOWN.t(language);
                for (String[] device : new String[][]{
                    {"W", "S", "K", "L"}, {"Up", "Down", "X", "C"}, // keyboard, default / left-handed
                    {Msg.LEFT_STICK.t(language) + " " + up, Msg.LEFT_STICK.t(language) + " " + down, "X/B", "Y"},
                    {Msg.RIGHT_STICK.t(language) + " " + up, Msg.RIGHT_STICK.t(language) + " " + down, Msg.DPAD_ACTION.t(language), Msg.DPAD_UP.t(language)},
                    {"", "", "", ""}}) { // touch (D138): no binding, the label says only what happens
                    String[] binding = new String[4];
                    for (int i = 0; i < binding.length; i++) binding[i] = device[i].isEmpty() ? "" : device[i] + ": ";
                    String lever = binding[0] + Msg.TURN_OFF.t(language) + " - " + Msg.LEVER.t(language);
                    String pickup = binding[0] + Msg.PICK_UP.t(language) + " - " + Msg.STONE.t(language);
                    String place = binding[1] + Msg.PUT_DOWN.t(language) + " - " + Msg.PLACE_BLOCKED.t(language);
                    String ranged = binding[3] + Msg.THROW.t(language);
                    String ladder = binding[0] + Msg.CLIMB.t(language) + "\n" + binding[1] + Msg.CLIMB_DOWN.t(language);
                    for (String context : new String[]{lever, pickup, ladder, Msg.FIREBALL_STOCK_FULL.t(language),
                        lever + "\n" + place + "\n" + ranged}) {
                        for (Msg message : new Msg[]{Msg.HINT_TUTORIAL_WALK, Msg.HINT_TUTORIAL_WALK_TOUCH, Msg.HINT_LEVER_TORCH, Msg.HINT_LEVER_EXIT,
                            Msg.HINT_LADDER_LEVER, Msg.HINT_LADDER_CLIMB, Msg.HINT_LADDER_EXIT,
                            Msg.HINT_WALL_LEVER, Msg.HINT_WALL_PASS, Msg.HINT_PLATFORM_LEVER, Msg.HINT_PLATFORM_RIDE,
                            Msg.HINT_STONE_THROW, Msg.HINT_STONE_EXIT, Msg.HINT_STONE_ACROSS,
                            Msg.HINT_TRAP_PLATFORM, Msg.HINT_TRAP_STUMP,
                    Msg.HINT_LADDER_ROOT, Msg.HINT_WALL_BRANCH, Msg.HINT_PLATFORM_STONE, Msg.HINT_STONE_STUMP}) {
                            float contextHeight = height(contextFont, context, Hud.CONTEXT_WIDTH), zoneHeight = height(zone, message.t(language), Hud.ZONE_WIDTH);
                            for (float cameraY : new float[]{0, 8}) for (float contextWorldY : new float[]{284, 284 - 58}) {
                                String where = language.code() + " " + message + " / " + context.replace('\n', '|')
                                    + " cameraY=" + cameraY + " anchor=" + contextWorldY;
                                float zoneAnchor = 360 - (zoneWorldY - cameraY), contextAnchor = 360 - (contextWorldY - cameraY);
                                float zoneTop = HintLayout.top(zoneAnchor, zoneHeight, zoneCap, 360);
                                float contextTop = HintLayout.contextTop(contextAnchor, contextHeight, true, zoneAnchor, zoneHeight, cap, zoneCap, 360);
                                // Rectangles (zone 608 px, label 300 px wide): the message's bottom is above the label's top.
                                assertTrue(zoneTop - zoneHeight - contextTop >= HintLayout.GAP - .001, where);
                                assertTrue(zoneTop <= 360 - HintLayout.TOP_MARGIN, where);
                                assertTrue(contextTop - contextHeight >= 0, "label on screen: " + where);
                            }
                        }
                    }
                }
            }
        } finally { Gdx.files = previous; }
    }
}
