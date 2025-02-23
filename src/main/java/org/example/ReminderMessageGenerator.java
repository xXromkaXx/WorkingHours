package org.example;

import java.util.List;
import java.util.Random;

public class ReminderMessageGenerator {
    private static final List<String> MESSAGES = List.of(
            "Привіт, %s! 🌟 Час виконати заплановану роботу! Не забувай фіксувати свої години. 🚀",
            "%s, ще один день – ще один крок до мети! 📅 Відзнач свої робочі години! ⏳",
            "Гей, %s! 👋 Пора зробити справи! Запиши свої години та рухайся вперед! 💪",
            "Нагадування для тебе, %s! 🎯 Не забудь відмітити, скільки годин відпрацював сьогодні!",
            "Ти на крок ближче до успіху, %s! 🚀 Відзнач свої години та продовжуй рухатися вперед!"
    );

    private static final List<String> MESSAGES_NO_NAME = List.of(
            "🌟 Час виконати заплановану роботу! Не забувай фіксувати свої години. 🚀",
            "Ще один день – ще один крок до мети! 📅 Відзнач свої робочі години! ⏳",
            "Гей! 👋 Пора зробити справи! Запиши свої години та рухайся вперед! 💪",
            "Нагадування для тебе! 🎯 Не забудь відмітити, скільки годин відпрацював сьогодні!",
            "Ти на крок ближче до успіху! 🚀 Відзнач свої години та продовжуй рухатися вперед!"
    );

    private static final Random RANDOM = new Random();

    public static String getRandomMessage(String userName) {
        if (isValidUserName(userName)) {
            return String.format(MESSAGES.get(RANDOM.nextInt(MESSAGES.size())), userName);
        } else {
            return MESSAGES_NO_NAME.get(RANDOM.nextInt(MESSAGES_NO_NAME.size()));
        }
    }

    private static boolean isValidUserName(String userName) {
        return userName != null && userName.matches("[a-zA-Zа-яА-ЯёЁіІїЇєЄґҐ]+");
    }


}
