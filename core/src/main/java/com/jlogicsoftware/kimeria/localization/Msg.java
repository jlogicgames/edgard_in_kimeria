package com.jlogicsoftware.kimeria.localization;

import static com.jlogicsoftware.kimeria.localization.Language.ENGLISH;

/** Port of Rust's {@code localization::Msg} UI message catalog. */
public enum Msg {
    TITLE, PLAY, ABOUT, OPTIONS, EXIT, BACK, RESUME, EXIT_TO_MENU, PLAY_AGAIN,
    PAUSE_MENU, GAME_OVER, LANGUAGE_LABEL, DISPLAY_LABEL, FULLSCREEN, WINDOWED, MENU_HINT, ABOUT_BODY, ABOUT_BODY_LEFT_HANDED, LOADING,
    ATTACK, INTERACT, SHOOT, LEFT_HANDED_LABEL, ON, OFF;

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
            case MENU_HINT -> en
                ? "Arrows/Tab to move - Enter/Space/A to confirm - Esc/B to go back"
                : "Стрілки/Tab — рух - Enter/Пробіл/A — підтвердити - Esc/B — назад";
            case ATTACK -> en ? "Attack" : "Атака";
            case INTERACT -> en ? "Interact" : "Взаємодія";
            case SHOOT -> en ? "Shoot" : "Постріл";
            case LEFT_HANDED_LABEL -> en ? "Left-handed" : "Для шульг";
            case ON -> en ? "On" : "Увімк.";
            case OFF -> en ? "Off" : "Вимк.";
            case LOADING -> en ? "Loading..." : "Завантаження...";
            case ABOUT_BODY -> en
                ? "Edgard in Kimeria\n\nUse WASD or Arrow Keys for movement.\nJ to jump. L to shoot.\nK to attack, or to interact inside a trigger zone.\nEscape to pause.\nCollect as many coins as you can and avoid enemies!"
                : "Едгард у Кімерії\n\nВикористовуйте WASD або стрілки для руху.\nJ — стрибок. L — постріл.\nK — атака, а в зоні тригера — взаємодія.\nEscape — пауза.\nЗберіть якомога більше монет і уникайте ворогів!";
            case ABOUT_BODY_LEFT_HANDED -> en
                ? "Edgard in Kimeria\n\nUse the Arrow Keys for movement.\nZ to jump. C to shoot.\nX to attack, or to interact inside a trigger zone.\nEscape to pause.\nCollect as many coins as you can and avoid enemies!"
                : "Едгард у Кімерії\n\nВикористовуйте стрілки для руху.\nZ — стрибок. C — постріл.\nX — атака, а в зоні тригера — взаємодія.\nEscape — пауза.\nЗберіть якомога більше монет і уникайте ворогів!";
        };
    }
}
