
package org.example;
import java.util.HashMap;
import java.util.concurrent.*;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.logging.Handler;

import org.json.JSONObject;
import java.util.Map;


public class ReminderManager {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> reminders = new ConcurrentHashMap<>();
    private final Main bot; // Посилання на основний клас для надсилання повідомлень
    private BotLogic botLogic; // Нове поле
    public ReminderManager(Main bot) {
        this.bot = bot;
    }

    public void setBotLogic(BotLogic botLogic) {
        this.botLogic = botLogic;
    }

    // Завантаження нагадувань з бази даних
    public void loadRemindersFromDatabase() {
        String query = "SELECT chatid, reminder_hour, reminder_minute FROM users WHERE reminder_hour IS NOT NULL AND reminder_minute IS NOT NULL";

        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                long chatId = rs.getLong("chatid");
                int hour = rs.getInt("reminder_hour");
                int minute = rs.getInt("reminder_minute");
                scheduleOrRescheduleReminder(chatId, hour, minute, null); // Плануємо нагадування
            }

        } catch (SQLException e) {
            System.out.println("Помилка при завантаженні нагадувань з бази даних: " + e.getMessage());
        }
    }

    public void scheduleOrRescheduleReminder(long chatId, int hour, int minute, String successMessage) {
        try {
            if (reminders.containsKey(chatId)) {
                reminders.get(chatId).cancel(false);
            }

            // Отримання часового поясу
            String userTimeZone = DataBase.getInstance().getUserTimeZone(chatId);
            ZoneId zoneId = ZoneId.of(userTimeZone);

            // Переведення часу в локальний для сервера
            LocalTime targetTime = LocalTime.of(hour, minute);
            ZonedDateTime userTime = ZonedDateTime.of(LocalDate.now(), targetTime, zoneId);
            ZonedDateTime serverTime = userTime.withZoneSameInstant(ZoneId.systemDefault());

            long initialDelay = Duration.between(LocalDateTime.now(), serverTime.toLocalDateTime()).getSeconds();
            if (initialDelay < 0) {
                initialDelay += TimeUnit.DAYS.toSeconds(1);
            }

            ScheduledFuture<?> scheduledTask = scheduler.scheduleAtFixedRate(
                    () -> sendDailyReminder(chatId),
                    initialDelay,
                    TimeUnit.DAYS.toSeconds(1),
                    TimeUnit.SECONDS
            );

            reminders.put(chatId, scheduledTask);

            if (successMessage != null) {
                botLogic.sendMessage(chatId, successMessage);
            }
        } catch (Exception e) {
            botLogic.sendMessage(chatId, "Помилка при налаштуванні нагадування: " + e.getMessage());
        }
    }


    // Оновлення часу нагадування для користувача
    public void updateReminderTime(long chatId, int hour, int minute) {
        String updateQuery = "UPDATE users SET reminder_hour = ?, reminder_minute = ? WHERE chatid = ?";

        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {

            pstmt.setInt(1, hour);
            pstmt.setInt(2, minute);
            pstmt.setLong(3, chatId);
            pstmt.executeUpdate();
            scheduleOrRescheduleReminder(chatId, hour, minute, "Час нагадування успішно оновлено.");
        } catch (SQLException e) {
            botLogic.sendMessage(chatId, "Помилка при оновленні часу нагадування: " + e.getMessage());
        }
    }

    // Обчислення початкової затримки
    private long calculateInitialDelay(LocalTime targetTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReminder = now.toLocalDate().atTime(targetTime);

        if (now.isAfter(nextReminder)) {
            nextReminder = nextReminder.plusDays(1);
        }
        return Duration.between(now, nextReminder).getSeconds();
    }

    private Map<Long, Boolean> userActivityMap = new HashMap<>(); // Зберігає, чи користувач відповів


    // Відправка нагадування
    private void sendDailyReminder(long chatId) {
        String reminderText = "🔔 *Нагадування* 🔔\n Привіт! 👋 \nЦе нагадування, щоб ти записав свої робочі години за сьогодні. ⏰";

        botLogic.sendMessage(chatId, reminderText);

        // Встановлюємо прапорець "активність відсутня"
        userActivityMap.put(chatId, false);

        // Плануємо повторне нагадування через 5 хвилин
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (!userActivityMap.getOrDefault(chatId, true)) { // Якщо користувач не відповів
                botLogic.sendMessage(chatId, "🔔 *Повторне нагадування* 🔔\n" +
                        "Ви ще не записали свої робочі години. Будь ласка, зробіть це зараз! 😊\n");
            }
        }, 6, TimeUnit.MINUTES); // 6 хвилин очікування
    }
    public void deleteReminder(long chatId) {
        if (reminders.containsKey(chatId)) {
            reminders.get(chatId).cancel(false);
            reminders.remove(chatId);
        }
        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET reminder_hour = NULL, reminder_minute = NULL WHERE chatid = ?")) {
            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }






}
