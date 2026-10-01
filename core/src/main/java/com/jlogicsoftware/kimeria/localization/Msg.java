package com.jlogicsoftware.kimeria.localization;

import static com.jlogicsoftware.kimeria.localization.Language.ENGLISH;

/** Port of Rust's {@code localization::Msg} UI message catalog. */
public enum Msg {
    TITLE, PLAY, ABOUT, OPTIONS, EXIT, BACK, RESUME, EXIT_TO_MENU, PLAY_AGAIN,
    PAUSE_MENU, GAME_OVER, LANGUAGE_LABEL, DISPLAY_LABEL, FULLSCREEN, WINDOWED, CONTROLS_HELP, MENU_HINT, ABOUT_BODY, LOADING;

    public String t(Language lang) {
        boolean en = lang == ENGLISH;
        return switch (this) {
            case TITLE -> en ? "Edgard in Kimeria" : "Едгард у Кімерії";
            case PLAY -> en ? "Play" : "Грати";
            case ABOUT -> en ? "About" : "Про гру";
            case OPTIONS -> en ? "Options" : "Налаштування";
            case EXIT -> en ? "Exit" : "Вихід";
            case BACK -> en ? "Back" : "Назад";
            case RESUME -> en ? "Resume" : "Продовжити";
            case EXIT_TO_MENU -> en ? "Exit to Menu" : "Вийти в меню";
            case PLAY_AGAIN -> en ? "Play Again" : "Грати знову";
            case PAUSE_MENU -> en ? "Pause Menu" : "Меню паузи";
            case GAME_OVER -> en ? "Game Over" : "Гру закінчено";
            case LANGUAGE_LABEL -> en ? "Language" : "Мова";
            case DISPLAY_LABEL -> en ? "Display" : "Екран";
            case FULLSCREEN -> en ? "Fullscreen" : "Повноекранний";
            case WINDOWED -> en ? "Windowed" : "У вікні";
            case CONTROLS_HELP -> en
                ? "Use WASD or Arrow Keys for movement.\nJ to jump. K to attack. L to interact.\nCollect as many coins as you can and avoid enemies!"
                : "Використовуйте WASD або стрілки для руху.\nJ — стрибок. K — атака. L — взаємодія.\nЗберіть якомога більше монет і уникайте ворогів!";
            case MENU_HINT -> en
                ? "Arrows/Tab to move - Enter/Space/A to confirm - Esc/B to go back"
                : "Стрілки/Tab — рух - Enter/Пробіл/A — підтвердити - Esc/B — назад";
            case LOADING -> en ? "Loading..." : "Завантаження...";
            case ABOUT_BODY -> en
                ? "Edgard in Kimeria\n\nUse WASD or Arrow Keys for movement.\nJ to jump. K to attack. L to interact.\nEscape to pause.\nCollect as many coins as you can and avoid enemies!"
                : "Едгард у Кімерії\n\nВикористовуйте WASD або стрілки для руху.\nJ — стрибок. K — атака. L — взаємодія.\nEscape — пауза.\nЗберіть якомога більше монет і уникайте ворогів!";
        };
    }
}
