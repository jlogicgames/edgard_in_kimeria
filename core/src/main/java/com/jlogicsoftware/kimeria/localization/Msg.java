package com.jlogicsoftware.kimeria.localization;

import static com.jlogicsoftware.kimeria.localization.Language.ENGLISH;

/** Port of Rust's {@code localization::Msg} UI message catalog. */
public enum Msg {
    TITLE, PLAY, CONTINUE, NEW_GAME, NEW_GAME_CONFIRMATION, CONFIRM, CANCEL, ABOUT, OPTIONS, EXIT, BACK, RESUME, RESTART_LEVEL, EXIT_TO_MENU,
    PAUSE_MENU, LANGUAGE_LABEL, DISPLAY_LABEL, FULLSCREEN, WINDOWED, MENU_HINT, ABOUT_BODY, ABOUT_BODY_LEFT_HANDED, ABOUT_GAMEPAD, ABOUT_GAMEPAD_LEFT_HANDED, LOADING, INTRO_STORY, ENDING_STORY,
    ATTACK, INTERACT, SHOOT, LEFT_HANDED_LABEL, ASSIST, LONGER_TRAP_WARNINGS, ON, OFF, BOSS,
    HEALER, EDGARD, HEALER_GREETING, EDGARD_REPLY, HEALER_INGREDIENTS, HEALER_PAYMENT,
    HEALER_BOTH, HEALER_READY, HEALER_OFFER, HEALER_PAID, HEALER_LOCK, HEALER_RETURN, HEALER_BREW,
    HEALER_VICTORY, EDGARD_DARKNESS, HEALER_GUIDE, HEALER_FIGHT, HEALER_HINT, CHICKEN, KEY, COINS, PAID, ABOUT_RULES, RULES, CONTROLS;

    public String t(Language lang) {
        boolean en = lang == ENGLISH;
        return switch (this) {
            case RULES -> en ? "Game rules" : "Правила гри";
            case CONTROLS -> en ? "Controls" : "Керування";
            case HEALER -> en ? "Healer" : "Знахар";
            case EDGARD -> en ? "Edgard" : "Едгард";
            case HEALER_GREETING -> en
                ? "Edgard, the forest guardian bars your path. I can brew an elixir to strengthen your sword against him."
                : "Едгарде, лісовий вартовий перекрив шлях. Я можу зварити еліксир, що посилить твій меч проти нього.";
            case EDGARD_REPLY -> en ? "What do you need for the elixir?" : "Що тобі потрібно для еліксиру?";
            case HEALER_INGREDIENTS -> en
                ? "The recipe needs Chicken and Key. You are missing ingredients. Check the list; payment also needs every coin from this forest."
                : "Для рецепта потрібні курка та ключ. Тобі бракує складників. Перевір список: плата - усі монети цього лісу.";
            case HEALER_BOTH -> en
                ? "You are missing ingredients and payment. Bring Chicken, Key and every coin from this forest. The list shows what is still missing."
                : "Тобі бракує складників і плати. Принеси курку, ключ та всі монети цього лісу. У списку видно, чого ще немає.";
            case HEALER_PAYMENT -> en
                ? "You have the ingredients, but not enough payment. Bring every coin from this forest; coins from earlier lands do not count."
                : "Складники є, та плати не вистачає. Принеси всі монети цього лісу. Монети з попередніх земель не враховуються.";
            case HEALER_READY -> en
                ? "I see you have everything: Chicken, Key and all forest coins. I'll brew your elixir now. Drink it, and your sword will strike this boss ten times harder."
                : "Бачу, в тебе все є: курка, ключ і всі монети лісу. Зараз зварю тобі еліксир. Випий його: удари мечем по цьому босу стануть удесятеро сильнішими.";
            case HEALER_OFFER -> en
                ? "I can send you to the forest entrance to collect what you missed. Or face him without the elixir. Once the fight begins, there is no way back."
                : "Я можу повернути тебе на початок лісу по пропущене. Або бийся без еліксиру. Коли почнеш бій, повернутися вже не можна.";
            case HEALER_PAID -> en
                ? "Chicken, Key and all forest coins are paid. You drink the elixir. Your sword now deals tenfold damage to this boss alone."
                : "Курку, ключ і всі монети лісу сплачено. Ти випиваєш еліксир. Тепер меч завдає вдесятеро більше шкоди лише цьому босу.";
            case HEALER_LOCK -> en
                ? "Defeat him to open the way onward. The elixir lasts through this battle and its retries, then ends with his defeat."
                : "Здолай його, щоб відкрити шлях далі. Еліксир діє в цьому бою та повторних спробах, доки боса не переможено.";
            case HEALER_RETURN -> en ? "Return to the forest entrance" : "Повернутися на початок лісу";
            case HEALER_BREW -> en ? "Pay, brew and drink" : "Сплатити, зварити й випити";
            case HEALER_FIGHT -> en ? "Enter the boss fight" : "Розпочати бій із босом";
            case HEALER_VICTORY -> en
                ? "The forest guardian is defeated, Edgard. You have opened the way ahead."
                : "Лісового вартового переможено, Едгарде. Ти відкрив шлях далі.";
            case EDGARD_DARKNESS -> en
                ? "But darkness still hangs over the kingdom."
                : "Але темрява досі нависає над королівством.";
            case HEALER_GUIDE -> en
                ? "Your journey continues. I will be your guide. Go on: we will meet again."
                : "Твоя подорож триває. Я буду твоїм провідником. Рушай: ми ще зустрінемося.";
            case HEALER_HINT -> en ? "Next >" : "Далі >";
            case CHICKEN -> en ? "Chicken" : "Курка";
            case KEY -> en ? "Key" : "Ключ";
            case COINS -> en ? "Forest coins" : "Монети лісу";
            case PAID -> en ? "Paid - elixir drunk" : "Сплачено - еліксир випито";
            case ABOUT_RULES -> en
                ? "One hit kills; attempts are unlimited. Retry from this room; new pickups roll back, secured ones stay kept. Tap jump for each jump. Assist extends trap warnings."
                : "Один удар смертельний; спроби необмежені. Повтор із цієї кімнати: нові знахідки скидаються, закріплені зберігаються. Для кожного стрибка натискай знову. Допомога подовжує попередження пасток.";
            case BOSS -> en ? "Boss" : "Бос";
            case TITLE -> en ? "Edgard in Kimeria" : "Едгард у Кімерії";
            case PLAY -> en ? "Play" : "Грати";
            case CONTINUE -> en ? "Continue" : "Продовжити гру";
            case NEW_GAME -> en ? "New Game" : "Нова гра";
            case NEW_GAME_CONFIRMATION -> en
                ? "Start a new game? Your saved progress will be replaced."
                : "Почати нову гру? Збережений прогрес буде замінено.";
            case CONFIRM -> en ? "Confirm" : "Підтвердити";
            case CANCEL -> en ? "Cancel" : "Скасувати";
            case ABOUT -> en ? "About" : "Про гру";
            case OPTIONS -> en ? "Options" : "Налаштування";
            case EXIT -> en ? "Exit" : "Вихід";
            case BACK -> en ? "Back" : "Назад";
            case RESUME -> en ? "Resume" : "Продовжити";
            case RESTART_LEVEL -> en ? "Restart Level" : "Почати рівень знову";
            case EXIT_TO_MENU -> en ? "Exit to Menu" : "Вийти в меню";
            case PAUSE_MENU -> en ? "Pause Menu" : "Меню паузи";
            case LANGUAGE_LABEL -> en ? "Language" : "Мова";
            case DISPLAY_LABEL -> en ? "Display" : "Екран";
            case FULLSCREEN -> en ? "Fullscreen" : "Повноекранний";
            case WINDOWED -> en ? "Windowed" : "У вікні";
            case MENU_HINT -> en
                ? "Arrows/Tab to move - Enter/Space/A to confirm - Esc/B to go back"
                : "Стрілки/Tab - рух - Enter/Пробіл/A - підтвердити - Esc/B - назад";
            case ATTACK -> en ? "Attack" : "Атака";
            case INTERACT -> en ? "Interact" : "Взаємодія";
            case SHOOT -> en ? "Shoot" : "Постріл";
            case LEFT_HANDED_LABEL -> en ? "Left-handed" : "Для шульг";
            case ASSIST -> en ? "Assist" : "Допомога";
            case LONGER_TRAP_WARNINGS -> en ? "Longer trap warnings" : "Довші попередження пасток";
            case ON -> en ? "On" : "Увімк.";
            case OFF -> en ? "Off" : "Вимк.";
            case INTRO_STORY -> en
                ? "I am the healer. Listen to the story of Kimeria.\n\nIn the days of King Rufardus the Wise, the witch L'Kardina came to our kingdom. She had been banished from a neighboring land. When Kimeria tried to drive her away as well, her fury became a terrible curse.\n\nThe sun ceased to shine. Darkness swallowed the kingdom, and eternal night settled over its sleeping lands.\n\nWord of Kimeria's misfortune reached a wandering warrior named Edgard. He set out on a long journey to overthrow the witch and save the kingdom.\n\nOn his way, he met me, an old healer. Beyond our meeting lay unfamiliar paths and cunning traps. I gave him my counsel, and he ventured onward.\n\nHere begins Edgard's journey through the sleeping kingdom."
                : "Я знахар. Послухай історію Кімерії.\n\nЗа часів короля Руфардуса Мудрого до нашого королівства прийшла відьма Л'Кардіна. Її вигнали із сусідньої країни. Коли й у Кімерії спробували її прогнати, вона розлютилася й наклала на край страшне прокляття.\n\nСонце перестало світити. Темрява поглинула королівство, і вічна ніч огорнула його сонні землі.\n\nЗвістка про лихо Кімерії дійшла до мандрівного воїна на ім'я Едгард. Він вирушив у далеку дорогу, щоб повалити владу відьми й урятувати королівство.\n\nДорогою він зустрів мене, старого знахаря. Попереду чекали незнайомі стежки й підступні пастки. Я дав йому пораду, і він рушив далі.\n\nТут починається подорож Едгарда сонним королівством.";
            case ENDING_STORY -> en
                ? "Edgard has defeated every enemy.\n\nThe kingdom is free of monsters.\n\nLight has returned to the world.\n\nTHE END"
                : "Едгард здолав усіх ворогів.\n\nКоролівство очищено від монстрів.\n\nСвітло повернулося у світ.\n\nКІНЕЦЬ";
            case LOADING -> en ? "Loading..." : "Завантаження...";
            case ABOUT_BODY -> en
                ? "Use A/D for movement.\nJ to jump. L to shoot.\nK to attack, or to interact inside a trigger zone.\nEscape to pause.\nJump on an enemy from above to stomp it. Ordinary enemies kill on side/bottom contact. The boss body blocks safely; his axe kills."
                : "Використовуйте A/D для руху.\nJ - стрибок. L - постріл.\nK - атака, а в зоні тригера - взаємодія.\nEscape - пауза.\nСтрибніть на ворога зверху, щоб розчавити його. Дотик до звичайних ворогів збоку чи знизу смертельний. Тіло боса безпечно блокує рух; його сокира вбиває.";
            case ABOUT_BODY_LEFT_HANDED -> en
                ? "Use the Arrow Keys for movement.\nZ to jump. C to shoot.\nX to attack, or to interact inside a trigger zone.\nEscape to pause.\nJump on an enemy from above to stomp it. Ordinary enemies kill on side/bottom contact. The boss body blocks safely; his axe kills."
                : "Використовуйте стрілки для руху.\nZ - стрибок. C - постріл.\nX - атака, а в зоні тригера - взаємодія.\nEscape - пауза.\nСтрибніть на ворога зверху, щоб розчавити його. Дотик до звичайних ворогів збоку чи знизу смертельний. Тіло боса безпечно блокує рух; його сокира вбиває.";
            case ABOUT_GAMEPAD -> en
                ? "Gamepad: left stick/D-pad to move, A to jump, X/B to attack or interact, Y to shoot, Start to pause."
                : "Геймпад: лівий стік/D-pad - рух, A - стрибок, X/B - атака або взаємодія, Y - постріл, Start - пауза.";
            case ABOUT_GAMEPAD_LEFT_HANDED -> en
                ? "Gamepad: right stick to move, D-pad down to jump, left/right to attack or interact, up to shoot, Start to pause."
                : "Геймпад: правий стік - рух, D-pad вниз - стрибок, вліво/вправо - атака або взаємодія, вгору - постріл, Start - пауза.";
        };
    }
}
