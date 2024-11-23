package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
public class Main {
    private final ReminderManager reminderManager;
    private final BotLogic botLogic;
    public Main() {
        this.reminderManager = new ReminderManager(this); // Передаємо Main в ReminderManager
        this.botLogic = new BotLogic(this);// Передаємо Main в BotLogic
        reminderManager.setBotLogic(botLogic);// Передаємо botLogic у ReminderManager
        // Завантаження нагадувань з бази під час запуску
        reminderManager.loadRemindersFromDatabase();
    }

    public ReminderManager getReminderManager() {
        return reminderManager;
    }





    public static void main(String[] args) {
        try {
            // Спочатку створюємо Main
            Main mainInstance = new Main();

            // Передаємо Main у BotLogic
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new BotLogic(mainInstance));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}