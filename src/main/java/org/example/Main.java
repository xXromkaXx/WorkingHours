package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;



public class Main {
    public static void main(String[] args) {

        try {
            Logig bot = new Logig();
            bot.loadRemindersFromDatabase();
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new Logig());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}

