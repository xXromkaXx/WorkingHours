package org.example;

import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class Logig extends TelegramLongPollingBot {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> reminderTasks = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(Logig.class);

    // Метод для завантаження нагадувань з бази даних при запуску бота
    public void loadRemindersFromDatabase() {
        String query = "SELECT chatid, reminder_hour, reminder_minute FROM users WHERE reminder_hour IS NOT NULL AND reminder_minute IS NOT NULL";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                long chatId = rs.getLong("chatid");
                int hour = rs.getInt("reminder_hour");
                int minute = rs.getInt("reminder_minute");
                scheduleDailyReminder(chatId, hour, minute);  // Плануємо нагадування для кожного користувача
            }

        } catch (SQLException e) {
            System.out.println("Помилка при завантаженні нагадувань з бази даних: " + e.getMessage());
        }
    }


    // Метод для налаштування щоденного нагадування для конкретного користувача
    private void scheduleDailyReminder(long chatId, int hour, int minute) {
        String timezone = getUserTimezone(chatId);
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            timezone = "Europe/Warsaw"; // Значення за замовчуванням
        }

        long initialDelay = calculateInitialDelay(LocalTime.of(hour, minute), timezone);

        long period = TimeUnit.DAYS.toMillis(1);

        if (reminderTasks.containsKey(chatId)) {
            reminderTasks.get(chatId).cancel(false);
            reminderTasks.remove(chatId);
        }

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> sendDailyReminder(chatId),
                initialDelay, period, TimeUnit.MILLISECONDS);

        reminderTasks.put(chatId, task);
    }



    private long calculateInitialDelay(LocalTime targetTime, String timezone) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
        ZonedDateTime nextReminder = now.withHour(targetTime.getHour()).withMinute(targetTime.getMinute()).withSecond(0);

        if (now.isAfter(nextReminder)) {
            nextReminder = nextReminder.plusDays(1);
        }

        return Duration.between(now, nextReminder).toMillis();
    }



    // Метод для відправки нагадування
    private void sendDailyReminder(long chatId) {
        sendMessage(chatId, "Це ваше нагадування на сьогодні!");  // Повідомлення, яке надсилається користувачеві
    }

    // Метод для оновлення часу нагадування користувача у базі даних та перепланування нагадування
    public void updateReminderTime(long chatId, int hour, int minute) {
        String updateQuery = "UPDATE users SET reminder_hour = ?, reminder_minute = ? WHERE chatid = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {

            pstmt.setInt(1, hour);
            pstmt.setInt(2, minute);
            pstmt.setLong(3, chatId);
            pstmt.executeUpdate();

            sendMessage(chatId,"Час нагадування успішно оновлено.");

            // Переплановуємо нагадування для користувача
            scheduleDailyReminder(chatId, hour, minute);

        } catch (SQLException e) {
            sendMessage(chatId,"Помилка при оновленні часу нагадування: ");
        }
    }





    public enum SubState {
        NONE,
        ASK_MONTH,

        WAIT_FOR_HOURS,

        WAIT_FOR_HOURS_R

    }


    private enum State {
        START, reg, SavingName, AddWork, SavingWork, MAIN,ENTER_HOURS,SELECT_WORK_TO_VIEW,VIEW_WORK_HOURS,EDIT_WORK,MainMenuBackForLIST,MainMenuBackForAddWork,editingHours
    ,reminderSetup,reminderHours,reminderMinutes,SET_TIMEZONE
    }

    private String selectedWork;

    private State currentState = State.START;
    private SubState currentSubState = SubState.NONE;


    private String userName;
    private Integer selectedMonth = null;
    private Integer selectedDay = null;
private Integer rHours=null;


    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }




    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            // Обробка callback для вибору місяця
            String[] data = update.getCallbackQuery().getData().split(":");
            if (data[0].equals("select_month")) {
                int month = Integer.parseInt(data[1]);
                String workName = data[2];
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                handleMonthSelection(chatId, month, workName);
            }
           else if (data[0].equals("edit_day")) {
                int month = Integer.parseInt(data[1]);
                int day = Integer.parseInt(data[2]);
                String workName = data[3];
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                handleDaySelection(chatId, month, day, workName);
            }
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            messageText = messageText.substring(0, 1).toUpperCase() + messageText.substring(1).toLowerCase();
            if (messageText.length() > 1) {
                messageText = capitalizeFirstLetter(messageText);
            }

            long chatId = update.getMessage().getChatId();

            // Обробка команди /start
            if (messageText.equals("/start")) {
                if (!UserExists(chatId)) {
                    currentState = State.reg;
                } else {
                    currentState = State.MAIN;
                }
            }else if (messageText.equals("/settimezone")) {
                currentState = State.SET_TIMEZONE;
                sendTimezoneKeyboard(chatId);
            }



            // Викликаємо обробку станів
            handleState(update, chatId);
        }
    }



    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        message.setText(text);
        message.setParseMode("Markdown");
        try {
            execute(message);

       } catch (TelegramApiException e) {
    logger.error("Помилка під час виконання команди Telegram API: {}", e.getMessage(), e);
}

    }


    private void sendMessageWithKeyboard(Long chatId, String text, ReplyKeyboardMarkup keyboardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(keyboardMarkup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    public Boolean formatString(String input) {


        // Перевірка на наявність лише букв і цифр
        if (!input.matches("[a-zA-Zа-яА-ЯёЁіІїЇєЄґҐ0-9]+")) {
            return false;
        }
        return true;
    }

    private void handleState(Update update, long chatId) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
messageText=messageText.substring(0, 1).toUpperCase() + messageText.substring(1).toLowerCase();


            switch (currentState) {

                case reg:
                    sendMessage(chatId, "Вкажіть, як до вас звертатися:");

                    currentState = State.SavingName;  // Переходимо до наступного стану
                    break;
                case SavingName:
                    if (formatString(messageText)) {
                        userName = messageText;  // Зберігаємо ім'я користувача
                        addUser(chatId, userName);
                        sendMessage(chatId, "Привіт, " + userName + "!");
                        currentState = State.AddWork;
                    }
                    else
                    {
                        sendMessage(chatId,"Ім'я містить недопустимі символи!");
                        currentState=State.reg;
                        handleState(update,chatId);
                    }
                    break;
                case AddWork:
                    // Перевіряємо, чи користувач має хоча б одну роботу
                    if (getUserJobs(chatId).size() > 0) {
                        // Користувач має принаймні одну роботу, тому відображаємо додаткову клавіатуру

                        sendMessageWithKeyboard(chatId, "Вкажіть назву нової роботи або скористайтеся кнопками нижче:", createMainMenuBackKeyboard());

                        // Переходимо до спеціального стану, де є клавіатура "Головне меню" і "Назад"

                        currentState = State.SavingWork;

                    } else  if (getUserJobs(chatId).size() == 0){
                        // Користувач не має жодної роботи, тому просимо ввести назву нової роботи
                        sendMessage(chatId, "Вкажіть назву роботи:");

                        // Переходимо до стану збереження роботи
                        currentState = State.SavingWork;
                    }
                    break;



                case SavingWork:
                    if (getUserJobs(chatId).size() >= 1){
                        if (messageText.equals("Головне меню") || messageText.equals("Назад")) {
                        currentState = State.MAIN;
                        menuMain(chatId, "Оберіть дію");  // Показуємо головне меню
                    }
                        }
                    if (formatString(messageText)) {
                        // Спочатку перевіряємо, чи існує робота для користувача
                        if (workExists(chatId, messageText)) {
                            sendMessage(chatId,"Робота з такою назвую у вас вже є !!!");
                            currentState=State.AddWork;
                            handleState(update,chatId);
                            return;
                        }
                        else  if (messageText.equals("Головне меню") || messageText.equals("Назад")) {
                            currentState = State.MAIN;
                            menuMain(chatId, "Оберіть дію");  // Показуємо головне меню

                        }
                        else {
                            addWork(chatId, messageText);
                            menuMain(chatId, "Роботу додано, виберіть наступну дію:");
                            currentState = State.MAIN;

                        }
                    }
                    else
                    {
                        sendMessage(chatId,"Назва містить недопустимі символи!");
                        currentState=State.AddWork;
                        handleState(update,chatId);
                    }

                    break;




                case reminderSetup:


                    if (messageText.equals("Змінити час")) {

                        currentState = State.reminderHours;

                        currentSubState = SubState.WAIT_FOR_HOURS_R;
                        sendMessageWithKeyboard(chatId,"Введіть годину для надсилання нагадування (0-23):",createMainMenuDOWNLOADKeyboard()) ;
                         return;
                    } if (messageText.equals("Видалити нагадування")) {
                        deleteReminder(chatId);
                        currentState = State.MAIN;
                        menuMain(chatId, "Нагадування видалено. Оберіть дію:");

                   return;
                    } if (messageText.equals("Назад")) {
                        currentState = State.MAIN;
                        menuMain(chatId, "Оберіть дію:");
                        return;
                    }
                        sendMessage(chatId, "Будь ласка, оберіть одну з дій:");
                        break;



                case reminderHours:

                    if (messageText.equals("Головне меню")) {
                        currentState = State.MAIN;
                        menuMain(chatId, "Оберіть дію");  // Показуємо головне меню
                        return;
                    } else if (messageText.equals("Скасувати")) {
                        currentState = State.reminderSetup;

                        sendMessageWithKeyboard(chatId, "Виберіть дію для нагадування:", createReminderKeyboard());

                        return;
                    } else if (getJobNamesForUser(chatId).contains(messageText)) {
                        selectedWork = messageText;
                        currentState = State.EDIT_WORK;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для нової обраної роботи
                        return;
                    }

                    if (currentSubState == SubState.WAIT_FOR_HOURS_R) {
                        if (!messageText.matches("\\d+")) {
                            sendMessage(chatId, "Будь ласка, введіть числове значення для години (0-23).");
                            break;
                        }
                        int hours = Integer.parseInt(messageText);
                        if (hours >= 0 && hours <= 23) {
                            rHours = hours;
                            sendMessage(chatId, "Введіть хвилини для надсилання нагадування (0-59):");
                            currentState = State.reminderMinutes;
                            currentSubState = SubState.NONE;
                        } else {
                            sendMessage(chatId, "Будь ласка, введіть коректну годину (0-23).");
                        }
                    }
                    break;

                case reminderMinutes:
                    if (messageText.equals("Головне меню")) {
                        currentState = State.MAIN;
                        menuMain(chatId, "Оберіть дію");  // Показуємо головне меню
                        return;
                    } else if (messageText.equals("Скасувати")) {
                        currentState = State.reminderSetup;

                        sendMessageWithKeyboard(chatId, "Виберіть дію для нагадування:", createReminderKeyboard());

                        return;
                    }
                    if (!messageText.matches("\\d+")) {
                        sendMessage(chatId, "Будь ласка, введіть числове значення для хвилин (0-59).");
                        break;
                    }
                    int minutes = Integer.parseInt(messageText);
                    if (minutes >= 0 && minutes <= 59) {
                        updateReminderTime(chatId, rHours, minutes);
                        sendMessage(chatId, "Нагадування встановлено на " + rHours + ":" + minutes);
                        currentState = State.MAIN;
                        menuMain(chatId, "Оберіть дію:");
                    } else {
                        sendMessage(chatId, "Будь ласка, введіть коректні хвилини (0-59).");
                    }
                    break;










                case MAIN:
                    if (getJobNamesForUser(chatId).contains(messageText)) {
                        selectedWork = messageText;
                        currentState = State.EDIT_WORK;
                        currentSubState = SubState.NONE;
                        showSettingUpWorkMenu(chatId);
                        return;
                    } if (messageText.equals("Додати роботу")) {
                        currentState = State.AddWork;
                        handleState(update, chatId);
                        return;  // ВАЖЛИВО! Зупиняє виконання handleState(), щоб не пішло далі!

                    } if (messageText.equals("Нагадування")) {

                        currentState = State.reminderSetup;

                        sendMessageWithKeyboard(chatId, "Виберіть дію для нагадування:", createReminderKeyboard());
                        return;  // ВАЖЛИВО! Зупиняє виконання handleState(), щоб не пішло далі!

                    }
                    menuMain(chatId, "\"Виберіть дію:\"\n- Назва роботи – корегування\n- Додати роботу\n");
            return;










                case EDIT_WORK:
                    switch (messageText) {
                        case "Додати години":
                            currentState = State.ENTER_HOURS;
                            sendMessageWithKeyboard(chatId, "Введіть кількість годин:", createMainMenuBackKeyboard());
                            break;

                        case "Розрахувати кількість год/м":
                            promptMonthSelection(chatId, selectedWork);
                            return;

                        case "Видалити роботу":
                            deleteJob(chatId, selectedWork);
                            currentState = State.MAIN;
                            menuMain(chatId, "Роботу \"" + selectedWork + "\" видалено.");

                            break;

                        case "Редагувати години":

                            editWorkHours(chatId, selectedWork);
                            break;

                        case "Назад":
                            currentState = State.MAIN;
                            break;

                        default:
                            sendMessage(chatId, "Оберіть дію зі списку.");
                            showSettingUpWorkMenu(chatId);
                            break;
                    }
                    break;

                default:
                    sendMessage(chatId, "Щось пішло не так. Спробуйте ще раз.");
                    currentState = State.MAIN;
                    break;
                case ENTER_HOURS:
                    if (messageText.equals("Назад")) {
                        currentState = State.EDIT_WORK;
                        showSettingUpWorkMenu(chatId);  // Повертаємо користувача до меню редагування роботи
                        return;
                    }
                    try {
                        int hours = Integer.parseInt(messageText); // Вводимо кількість годин
                        addWorkHours(chatId, selectedWork, hours);
                        sendMessage(chatId, "Години успішно додано для роботи: " + selectedWork);
                        currentState = State.MAIN;
                        handleState(update,chatId);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Введіть коректну кількість годин.");
                    }
                    break;
                case SELECT_WORK_TO_VIEW:

                        currentState = State.VIEW_WORK_HOURS;
                        handleState(update, chatId);
                   break;

                case VIEW_WORK_HOURS:
                    List<String> hoursData = getWorkHoursData(chatId, selectedWork);
                    if (hoursData.isEmpty()) {
                        sendMessage(chatId, "Немає записів для роботи: " + selectedWork);

                    } else {
                        sendMessage(chatId, "Список годин для роботи " + selectedWork + ":\n" + String.join("\n", hoursData));

                    }
                    sendMessageWithKeyboard (chatId, "Скористайтеся кнопками нижче:", createMainMenuBackKeyboard());
                    currentState=State.MainMenuBackForLIST;

                    break;

                case MainMenuBackForLIST:
                    if (messageText.equals("Головне меню")) {
                        currentState = State.MAIN;
                        menuMain(chatId, "Оберіть дію");  // Показуємо головне меню
                    } else if (messageText.equals("Назад")) {
                        // Повертаємося до меню коригування обраної роботи
                        currentState = State.EDIT_WORK;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для обраної роботи
                    } else if (getJobNamesForUser(chatId).contains(messageText)) {
                        selectedWork = messageText;
                        currentState = State.EDIT_WORK;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для нової обраної роботи
                    }
                    break;




                case editingHours:
                    if ("Головне меню".equals(messageText)) {
                        // Повернення до головного меню

                        currentState = State.MAIN;
                        currentSubState = SubState.NONE;

                        handleState(update, chatId);
                        return;
                    } else if ("Скасувати".equals(messageText)) {
                        currentState = State.EDIT_WORK;
                        currentSubState = SubState.NONE;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для обраної роботи
                        return;
                    } else if (getJobNamesForUser(chatId).contains(messageText)) {
                        selectedWork = messageText;
                        currentState = State.EDIT_WORK;
                        currentSubState = SubState.NONE;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для нової обраної роботи
                        return;
                    }
// Логіка для кожного підстану
                    switch (currentSubState) {




                        case WAIT_FOR_HOURS:
                            if (messageText.equals("Назад")) {
                                currentState = State.EDIT_WORK;
                                showSettingUpWorkMenu(chatId);  // Повертаємо користувача до меню редагування роботи
                                return;
                            }
                            try {
                                int hours = Integer.parseInt(messageText);
                                editingHoursWork(chatId, selectedWork, selectedMonth, selectedDay, hours);

                                // Повертаємось до основного меню після завершення редагування
                                currentState = State.MAIN;
                                currentSubState = SubState.NONE;
                                selectedMonth = null;
                                selectedDay = null;
                                handleState(update, chatId);
                            } catch (NumberFormatException e) {
                                sendMessageWithKeyboard(chatId, "Введіть коректне значення для годин.", createMainMenuBackKeyboard());
                            }
                            break;

                        default:
                            currentSubState = SubState.ASK_MONTH;
                            break;
                    }
                    break;
                case SET_TIMEZONE: String selectedTimezone = messageText.trim();

                    // Якщо вибрано "Інший..."
                    if (selectedTimezone.equals("🏳 Інший... (ввести вручну)")) {
                        sendMessage(chatId, "✍ Введіть назву вашого часового поясу (наприклад: `Europe/Paris`):");
                        return;
                    }

                    // Витягуємо лише назву часового поясу (без прапорця)
                    selectedTimezone = selectedTimezone.replaceAll("^[^a-zA-Z]+", "").trim();

                    // Перевіряємо, чи пояс валідний
                    if (!ZoneId.getAvailableZoneIds().contains(selectedTimezone)) {
                        sendMessage(chatId, "❌ Некоректний часовий пояс! Використовуйте формат `Europe/Kyiv`, `America/New_York` тощо.");
                        return;
                    }

                    updateUserTimezone(chatId, selectedTimezone);
                    sendMessage(chatId, "✅ Ваш часовий пояс оновлено на: `" + selectedTimezone + "`");

                    currentState = State.MAIN; // Повертаємо в головне меню:
break;


            }
        }


    }





    public List<String> getUserJobs(Long chatId) {
        List<String> jobs = new ArrayList<>();
        String query = "SELECT work_name FROM work_types WHERE chatid = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(resultSet.getString("work_name"));
                }
            }
       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}


        return jobs;
    }

    // Метод для перевірки, чи існує користувач в базі даних
    public boolean UserExists(Long chatId) {
        String sql = "SELECT COUNT(*) FROM users WHERE chatid = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt(1) > 0; // Якщо кількість більша за 0, користувач існує
            }

       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}


        return false;
    }


    public static void addUser(Long userId, String name) {
        String sql = "INSERT INTO users (chatid, username) VALUES (?, ?) ON CONFLICT (chatid) DO NOTHING";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
            System.out.println("Користувача додано успішно.");
        } catch (SQLException e) {
            System.out.println("Помилка при додаванні користувача: " + e.getMessage());
        }
    }



    public void addWork(Long chatId, String workName) {

        LocalDate currentDate = LocalDate.now();

        int currentMonth = currentDate.getMonthValue();


        // SQL-запити для вставки
        String insertWorkTypeSql = """
            INSERT INTO work_types (chatid, work_name)
            VALUES (?, ?)
            ON CONFLICT (chatid, work_name) DO NOTHING
            """;

            String insertWorkHoursSql = """
            INSERT INTO work_hours (chatid, work_id, month, work_data)
            SELECT ?, wt.work_id, ?, '{}'
            FROM work_types wt
            WHERE wt.chatid = ? AND wt.work_name = ?
            AND NOT EXISTS (
                SELECT 1 FROM work_hours wh
                WHERE wh.chatid = ? AND wh.work_id = wt.work_id
            )
            """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Виконуємо вставку в таблицю work_types
            try (PreparedStatement pstmtWorkType = conn.prepareStatement(insertWorkTypeSql)) {
                pstmtWorkType.setLong(1, chatId);
                pstmtWorkType.setString(2, workName);
                pstmtWorkType.executeUpdate();
            }

            // Виконуємо вставку в таблицю work_hours
            try (PreparedStatement pstmtWorkHours = conn.prepareStatement(insertWorkHoursSql)) {
                pstmtWorkHours.setLong(1, chatId);
                pstmtWorkHours.setLong(2, currentMonth );
                pstmtWorkHours.setLong(3, chatId);
                pstmtWorkHours.setString(4, workName);
                pstmtWorkHours.setLong(5, chatId);
                pstmtWorkHours.executeUpdate();
            }


       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}



    }

    private boolean workExists(Long chatId, String workName) {
        String sql = """
                SELECT j.work_id 
                FROM work_types j
                JOIN work_hours wl ON j.work_id = wl.work_id
                WHERE wl.chatid = ? AND j.work_name = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);

            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Повертає true, якщо запис існує
       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}

        return false;
    }








    private void addWorkHours(Long chatId, String workName, int hours) {
        String selectSql = """
        SELECT work_data FROM work_hours 
        WHERE chatid = ? 
        AND work_id = (SELECT work_id FROM work_types WHERE work_name = ?) 
        AND month = ?
    """;

        String insertSql = """
        INSERT INTO work_hours (chatid, work_id, month, work_data)
        VALUES (?, (SELECT work_id FROM work_types WHERE work_name = ?), ?, ?::jsonb)
    """;

        String updateSql = """
        UPDATE work_hours 
        SET work_data = work_data || ?::jsonb
        WHERE chatid = ? 
        AND work_id = (SELECT work_id FROM work_types WHERE work_name = ?) 
        AND month = ?
    """;

        // Отримуємо поточний день та місяць
        LocalDate currentDate = LocalDate.now();
        int dayOfMonth = currentDate.getDayOfMonth();
        int currentMonth = currentDate.getMonthValue();

        // Створюємо JSON для поточного дня
        String dayDataJson = "{\"" + dayOfMonth + "\": " + hours + "}";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            // Перевіряємо, чи існує запис для цього chatId, work_id, та місяця
            selectStmt.setLong(1, chatId);
            selectStmt.setString(2, workName);
            selectStmt.setInt(3, currentMonth);

            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                // Якщо запис існує, оновлюємо його, додаючи новий день у JSON
                updateStmt.setString(1, dayDataJson);
                updateStmt.setLong(2, chatId);
                updateStmt.setString(3, workName);
                updateStmt.setInt(4, currentMonth);
                updateStmt.executeUpdate();
            } else {
                // Якщо запису немає, створюємо новий запис з поточним місяцем і днями
                insertStmt.setLong(1, chatId);
                insertStmt.setString(2, workName);
                insertStmt.setInt(3, currentMonth);
                insertStmt.setString(4, dayDataJson);
                insertStmt.executeUpdate();
            }

       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}

    }


    private void editingHoursWork(Long chatId, String workName, int month, int day, int hours) {
        String selectSql = """
        SELECT work_data FROM work_hours 
        WHERE chatid = ? 
        AND work_id = (SELECT work_id FROM work_types WHERE work_name = ?) 
        AND month = ?
    """;

        String insertSql = """
        INSERT INTO work_hours (chatid, work_id, month, work_data)
        VALUES (?, (SELECT work_id FROM work_types WHERE work_name = ?), ?, ?::jsonb)
    """;

        String updateSql = """
        UPDATE work_hours 
        SET work_data = work_data || ?::jsonb
        WHERE chatid = ? 
        AND work_id = (SELECT work_id FROM work_types WHERE work_name = ?) 
        AND month = ?
    """;




        // Створюємо JSON для поточного дня
        String dayDataJson = "{\"" + day + "\": " + hours + "}";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            // Перевіряємо, чи існує запис для цього chatId, work_id, та місяця
            selectStmt.setLong(1, chatId);
            selectStmt.setString(2, workName);
            selectStmt.setInt(3, month);

            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                // Отримуємо наявний JSON для поточного місяця
                String workData = rs.getString("work_data");

                // Перевіряємо, чи містить JSON запис для вказаного дня
                if (workData.contains("\"" + day + "\":")) {
                    // Якщо день вже є в JSON, оновлюємо запис
                    updateStmt.setString(1, dayDataJson);
                    updateStmt.setLong(2, chatId);
                    updateStmt.setString(3, workName);
                    updateStmt.setInt(4, month);
                    updateStmt.executeUpdate();

                    sendMessage(chatId, "Години для обраного дня успішно оновлено.");
                } else {
                    // Якщо дня немає в JSON, повідомляємо користувача
                    sendMessage(chatId, "Запису для обраного дня немає. Додайте спочатку години для цього дня.");
                }
            } else {
                // Якщо запису для місяця взагалі немає, повідомляємо користувача
                sendMessage(chatId, "Запису для обраного місяця немає. Додайте спочатку години для цього місяця.");
            }

        } catch (SQLException e) {
            logger.error("Помилка SQL: {}", e.getMessage(), e);
            sendMessage(chatId, "Сталася помилка під час оновлення робочих годин.");
        }
    }





    // Метод для отримання назв робіт з бази даних
    public List<String> getJobNamesForUser(Long chatId) {
        List<String> jobNames = new ArrayList<>();
        String sql = """
        SELECT DISTINCT work_types.work_name
        FROM work_types
        JOIN work_hours ON work_types.work_id = work_hours.work_id
        WHERE work_hours.chatid = ?
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                jobNames.add(resultSet.getString("work_name"));
            }

       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}


        return jobNames;
    }





    private void menuMain(Long chatId, String text) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        for (String job : getJobNamesForUser(chatId)) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(job));
            keyboardRows.add(row);
        }

        KeyboardRow addWorkRow = new KeyboardRow();
        addWorkRow.add(new KeyboardButton("Додати роботу"));
        addWorkRow.add(new KeyboardButton("Нагадування"));
        keyboardRows.add(addWorkRow);

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup createMainMenuBackKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        KeyboardRow mainRow = new KeyboardRow();
        mainRow.add(new KeyboardButton("Головне меню"));

        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("Назад"));

        keyboardMarkup.setKeyboard(List.of(mainRow, backRow));

        return keyboardMarkup;
    }




    // Метод для відображення меню коригування роботи
    private void showSettingUpWorkMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Виберіть дію для роботи: " );

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow  AddHoursRow = new KeyboardRow();
        AddHoursRow.add(new KeyboardButton("Додати години"));

        KeyboardRow ListHours = new KeyboardRow();
        ListHours.add(new KeyboardButton("Розрахувати кількість год/м"));


        KeyboardRow EditHoursANDDeleteJob =new KeyboardRow();
        EditHoursANDDeleteJob.add(new KeyboardButton("Редагувати години"));
        EditHoursANDDeleteJob.add(new KeyboardButton("Видалити роботу"));

        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("Назад"));

        keyboardRows.add(AddHoursRow);
        keyboardRows.add(ListHours);
        keyboardRows.add(EditHoursANDDeleteJob);
        keyboardRows.add(backRow);

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private List<String> getWorkHoursData(long chatId, String workName) {
        List<String> hoursData = new ArrayList<>();
        String sql = """
            SELECT work_data
            FROM work_hours
            JOIN work_types ON work_hours.work_id = work_types.work_id
            WHERE work_hours.chatid = ? AND work_types.work_name = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String workDataJson = rs.getString("work_data");
                hoursData = parseWorkData(workDataJson);  // Розпарсимо JSON-дані
            }
       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}

        return hoursData;
    }
    private List<String> parseWorkData(String workDataJson) {
        List<String> hoursData = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(workDataJson);
            for (String day : jsonObject.keySet()) {
                int hours = jsonObject.getInt(day);
                hoursData.add("📅 *День:* " + Integer.parseInt(day) + " | ⏳ *Години:* " + hours);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return hoursData;
    }


    public boolean deleteJob(Long chatId, String workName) {
        String selectWorkIdSQL = "SELECT work_id FROM work_types WHERE chatid = ? AND work_name = ?";
        String deleteFromWorkHoursSQL = "DELETE FROM work_hours WHERE work_id = ?";
        String deleteFromWorkTypesSQL = "DELETE FROM work_types WHERE work_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Крок 1: Отримуємо work_id для вказаної роботи та chatId
            int workId;
            try (PreparedStatement selectStmt = conn.prepareStatement(selectWorkIdSQL)) {
                selectStmt.setLong(1, chatId);
                selectStmt.setString(2, workName);
                ResultSet rs = selectStmt.executeQuery();

                if (!rs.next()) {
                    return false; // Робота не знайдена для цього chatId та workName
                }
                workId = rs.getInt("work_id");
            }

            // Крок 2: Видаляємо записи в таблиці work_hours з отриманим work_id
            try (PreparedStatement deleteHoursStmt = conn.prepareStatement(deleteFromWorkHoursSQL)) {
                deleteHoursStmt.setInt(1, workId);
                deleteHoursStmt.executeUpdate();
            }

            // Крок 3: Видаляємо запис у таблиці work_types з отриманим work_id
            try (PreparedStatement deleteWorkStmt = conn.prepareStatement(deleteFromWorkTypesSQL)) {
                deleteWorkStmt.setInt(1, workId);
                deleteWorkStmt.executeUpdate();
            }

            return true; // Успішно видалено

        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Видалення не вдалося через помилку
        }
    }




    //нагадування
    public void deleteReminder(long chatId) {
        String deleteQuery = "UPDATE users SET reminder_hour = NULL, reminder_minute = NULL WHERE chatid = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {

            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
            sendMessage(chatId, "Нагадування видалено.");

            // Перевіряємо, чи є активне нагадування для цього користувача, і видаляємо його
            if (reminderTasks.containsKey(chatId)) {
                reminderTasks.get(chatId).cancel(false);
                reminderTasks.remove(chatId);

            }

        } catch (SQLException e) {
            logger.error("Помилка SQL: {}", e.getMessage(), e);
            sendMessage(chatId, "Помилка при видаленні нагадування.");

        }
    }

    private ReplyKeyboardMarkup createReminderKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(false);

        KeyboardRow mainRow = new KeyboardRow();
        mainRow.add(new KeyboardButton("Змінити час"));
        mainRow.add(new KeyboardButton("Видалити нагадування"));


        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("Назад"));

        keyboardMarkup.setKeyboard(List.of(mainRow, backRow));

        return keyboardMarkup;
    }


    // Додаємо метод для створення клавіатури з місяцями
    private InlineKeyboardMarkup createMonthSelectionKeyboard(String workName) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        String[] months = {"Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
                "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"};

        for (int i = 0; i < months.length; i += 3) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int j = i; j < i + 3 && j < months.length; j++) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(months[j]);
                button.setCallbackData("select_month:" + (j + 1) + ":" + workName);
                row.add(button);
            }
            rowsInline.add(row);
        }

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        return inlineKeyboardMarkup;
    }

    // Метод для виклику вибору місяця
    private void promptMonthSelection(long chatId, String workName) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Оберіть місяць для перегляду годин для роботи: " + workName);
        message.setReplyMarkup(createMonthSelectionKeyboard(workName));
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Обробка вибраного місяця
    private void handleMonthSelection(long chatId, int month, String workName) {
        List<String> hoursData = getWorkHoursDataForMonth(chatId, workName, month);
        int totalHours = calculateTotalHours(hoursData);

        StringBuilder message = new StringBuilder();
        message.append("📅 *Місяць:* ").append(getMonthName(month)).append("\n");
        message.append("⏳ *Загальна кількість годин:* ").append(totalHours).append("\n\n");

        for (String dayData : hoursData) {
            message.append(dayData).append("\n");
        }

        sendMessage(chatId, message.toString());
    }

    // Отримання даних по годинах за конкретний місяць
    private List<String> getWorkHoursDataForMonth(long chatId, String workName, int month) {
        List<String> hoursData = new ArrayList<>();
        String sql = """
            SELECT work_data
            FROM work_hours
            JOIN work_types ON work_hours.work_id = work_types.work_id
            WHERE work_hours.chatid = ? AND work_types.work_name = ? AND work_hours.month = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);
            pstmt.setInt(3, month);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String workDataJson = rs.getString("work_data");
                JSONObject jsonObject = new JSONObject(workDataJson);
                for (String day : jsonObject.keySet()) {
                    int hours = jsonObject.getInt(day);
                    hoursData.add("📅 День: " + day + " | ⏳ Години: " + hours);
                }
            }
       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}

        return hoursData;
    }

    // Підрахунок загальної кількості годин
    private int calculateTotalHours(List<String> hoursData) {
        return hoursData.stream()
                .mapToInt(data -> Integer.parseInt(data.replaceAll(".*Години: (\\d+)", "$1")))
                .sum();
    }

    // Метод для отримання назви місяця
    private String getMonthName(int month) {
        String[] months = {"Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
                "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"};
        return months[month - 1];
    }







    //редагування годин
    private void editWorkHours(long chatId, String workName) {
        int currentMonth = LocalDate.now().getMonthValue();
        List<String> daysWithHours = getWorkHoursForEditing(chatId, workName, currentMonth);

        if (daysWithHours.isEmpty()) {
            sendMessage(chatId, "Немає записів на цей місяць. Виберіть день, щоб додати години.");
        }

        sendInlineDaysKeyboard(chatId, workName, currentMonth, daysWithHours);
    }

    private void sendInlineDaysKeyboard(long chatId, String workName, int month, List<String> daysWithHours) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (String dayData : daysWithHours) {
            String[] parts = dayData.split(":");
            String dayDO = parts[0].replaceAll("\\D+", ""); // Видаляємо все, окрім чисел
            String hours = parts.length > 1 ? parts[1] : "0";
            // Перевіряємо, чи в числі більше або дорівнює 100
           String day= splitTime(dayDO);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("День " + day + " (" +hours + " год)");

            button.setCallbackData("edit_day:" + month + ":" + day + ":" + workName); // Передаємо тільки число дня

            rows.add(List.of(button));
        }

        markup.setKeyboard(rows);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Виберіть день для редагування:");
        message.setReplyMarkup(markup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    // Обробка вибору дня
    private void handleDaySelection(long chatId, int month, int day, String workName) {

        int existingHours = getHoursForDay(chatId, workName, month, day);
        sendMessage(chatId, "На день " + day + " вже внесено " + existingHours + " годин. Введіть нове значення або натисніть 'Скасувати'.");
        currentState = State.editingHours;
        currentSubState=SubState.WAIT_FOR_HOURS;
        selectedMonth = month;
        selectedDay = day;
        selectedWork = workName;
    }

    private int getHoursForDay(long chatId, String workName, int month, int day) {
        String sql = "SELECT work_data FROM work_hours JOIN work_types ON work_hours.work_id = work_types.work_id WHERE work_hours.chatid = ? AND work_types.work_name = ? AND work_hours.month = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);
            pstmt.setInt(3, month);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                JSONObject jsonObject = new JSONObject(rs.getString("work_data"));
                return jsonObject.optInt(String.valueOf(day), 0);
            }
       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}

        return 0;
    }




    private List<String> getWorkHoursForEditing(long chatId, String workName, int month) {
        List<String> hoursData = new ArrayList<>();
        String sql = """
        SELECT work_data
        FROM work_hours
        JOIN work_types ON work_hours.work_id = work_types.work_id
        WHERE work_hours.chatid = ? AND work_types.work_name = ? AND work_hours.month = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);
            pstmt.setInt(3, month);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String workDataJson = rs.getString("work_data");

                // Парсимо JSON об'єкт
                JSONObject jsonObject = new JSONObject(workDataJson);
                for (String key : jsonObject.keySet()) {

                        // Перетворюємо ключ у число (день)
                        int day = Integer.parseInt(key);
                        // Отримуємо значення (кількість годин)
                        int hours = jsonObject.getInt(key);

                        // Додаємо коректний вивід
                        hoursData.add(" " + day + " " + hours);

                        System.out.println(" " + day + " " + hours);

                }
            }
       } catch (SQLException e) {
    logger.error("Помилка SQL: {}", e.getMessage(), e);
}

        return hoursData;
    }

        public static String splitTime(String day2) {
            int day = Integer.parseInt(day2);
        String result = "";

            // Перевірка, чи число має 4 цифри
            if (day >= 1000) {
                int days = day / 100; // Перші дві цифри — це дні

                result = days+"" ;
            }
            // Якщо число має 3 цифри
            else if (day >= 100) {
                int days = day / 100; // Перша цифра — це дні

                result = days+"" ;
            }


            return result;
        }






    private ReplyKeyboardMarkup createMainMenuDOWNLOADKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        KeyboardRow mainRow = new KeyboardRow();
        mainRow.add(new KeyboardButton("Головне меню"));

        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("Скасувати"));

        keyboardMarkup.setKeyboard(List.of(mainRow, backRow));

        return keyboardMarkup;
    }






    //тайм зона
    public void updateUserTimezone(long chatId, String timezone) {
        String query = "UPDATE users SET timezone = ? WHERE chatid = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, timezone);
            pstmt.setLong(2, chatId);
            pstmt.executeUpdate();
            sendMessage(chatId, "Ваш часовий пояс оновлено на " + timezone);
        } catch (SQLException e) {
            sendMessage(chatId, "Помилка при оновленні часового поясу.");
        }
    }

    private void sendTimezoneKeyboard(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🌍 Виберіть свій часовий пояс:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        // Рядки кнопок
        keyboardRows.add(createRow("🇺🇦 Europe/Kyiv", "🇵🇱 Europe/Warsaw"));
        keyboardRows.add(createRow("🇷🇺 Europe/Moscow", "🇹🇷 Europe/Istanbul"));
        keyboardRows.add(createRow("🇺🇸 America/New_York", "🇩🇪 Europe/Berlin"));
        keyboardRows.add(createRow("🏳 Інший... (ввести вручну)"));

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Функція для швидкого створення рядка кнопок
    private KeyboardRow createRow(String... buttons) {
        KeyboardRow row = new KeyboardRow();
        for (String button : buttons) {
            row.add(new KeyboardButton(button));
        }
        return row;
    }


    private String getUserTimezone(long chatId) {
        String query = "SELECT timezone FROM users WHERE chatid = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("timezone");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Europe/Warsaw"; // Значення за замовчуванням
    }





    @Override
    public String getBotUsername() {
        return System.getenv("BOT_USERNAME"); // Читаємо з середовища
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN"); // Читаємо з середовища
    }
}