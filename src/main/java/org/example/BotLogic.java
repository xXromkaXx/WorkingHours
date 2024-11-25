package org.example;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import org.apache.commons.lang3.EnumUtils;
import org.checkerframework.checker.units.qual.C;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.Collections;

import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;

import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.logging.Handler;

public class BotLogic extends TelegramLongPollingBot {

    private final Main main;

    public BotLogic(Main main) {
        this.main = main; // Зберігаємо посилання на Main
    }

    private enum State {
        START,MAIN,WORKSETTINGS,ENTER_HOURS,AddWork,SavingWork,SELECT_WORK_TO_VIEW,VIEW_WORK_HOURS,MainMenuBackForLIST,editingHours,reminderSetup
        ,reminderHours,reminderMinutes,settimezone }





    public enum SubState {
        NONE,
        ASK_MONTH,
        WAIT_FOR_MONTH,
        WAIT_FOR_DAY,
        WAIT_FOR_HOURS,
        ASK_HOURS,
        WAIT_FOR_HOURS_R,
        WAIT_FOR_MINUTES_R;
    }

    private String selectedWork;
    private State currentState = State.START;
    private SubState currentSubState = SubState.NONE;


    private Integer selectedMonth = null;
    private Integer selectedDay = null;
    private Integer rHours=null;
    private Integer rMinutes=null;

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            messageText = messageText.substring(0, 1).toUpperCase() + messageText.substring(1).toLowerCase();

            long chatId = update.getMessage().getChatId();


            if ("/start".equals(messageText)) {
                DataBase database = DataBase.getInstance(); // Отримуємо Singleton

                if (!database.UserExists(chatId)) {
                    // Якщо користувач новий
                    sendMessage(chatId, "Вітаю! Як вас звати?");
                    currentState = State.START; // Встановлюємо стан для запиту імені
                } else {
                    // Якщо користувач вже існує
                    String userName = database.getUserName(chatId); // Отримуємо ім'я користувача
                    sendMessage(chatId, "Вітаю знову, " + userName + "! Раді вас бачити!");
                    currentState = State.MAIN; // Переходимо до головного меню
                    handleState(update, chatId);
                }
            } else if (messageText.startsWith("/settimezone")) {
                currentState = State.settimezone; // Встановлюємо стан для зміни часового поясу
                sendMessage(chatId, "Введіть часовий пояс у форматі, наприклад: Europe/Warsaw");
            } else {
                // Обробка стану
                handleState(update, chatId);
            }
        }


    }
    // Метод для отримання часового поясу користувача з бази
    private String getUserTimeZone(long chatId) {
        // Приклад отримання часового поясу з бази
        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT timezone FROM users WHERE chatid = ?")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("timezone");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Значення за замовчуванням (UTC), якщо користувач не має часового поясу
        return "UTC";
    }
    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        message.setText(text);
        message.setParseMode("Markdown");
        try {
            execute(message);

        } catch (TelegramApiException e) {
            e.printStackTrace();

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

    public static boolean isValidZoneId(String text) {
        // Отримуємо список усіх доступних часовых поясів
        Set<String> availableZones = ZoneId.getAvailableZoneIds();

        // Перевіряємо без врахування регістру
        for (String availableZone : availableZones) {
            if (availableZone.equalsIgnoreCase(text)) {
                return true; // Знайдено валідний часовий пояс
            }
        }
        return false;
    }
    private String getCorrectZoneId(String zoneId) {
        Set<String> availableZones = ZoneId.getAvailableZoneIds();

        for (String availableZone : availableZones) {
            if (availableZone.equalsIgnoreCase(zoneId)) {
                return availableZone; // Повертаємо точний формат часового поясу
            }
        }
        return zoneId; // Це має ніколи не відбуватись, якщо викликати цей метод після перевірки
    }

    private void handleState(Update update, long chatId) {
        if (update.hasMessage() && update.getMessage().getText().startsWith("/settimezone")) {
            // Не змінюємо стан, якщо це команда /settimezone
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            messageText = messageText.substring(0, 1).toUpperCase() + messageText.substring(1).toLowerCase();
            DataBase database = DataBase.getInstance(); // Отримуємо Singleton

            switch (currentState) {
                case START:
                if (messageText.matches("[a-zA-Zа-яА-ЯёЁіІїЇєЄґҐ]+")) { // Перевірка на допустимість символів
                    database.saveUser(chatId, messageText); // Зберігаємо ім'я користувача в базі
                    sendMessage(chatId, "Радий познайомитися, " + messageText + "!");
                    currentState = State.MAIN;
                    menuMain(chatId, "Оберіть дію:");
                } else {
                    sendMessage(chatId, "Будь ласка, введіть коректне ім'я.");
                }
                break;
                case settimezone:
                    String userInput = messageText.trim();

                    if (isValidZoneId(userInput)) {
                        // Зберігаємо часовий пояс у правильному регістрі
                        String correctedZoneId = getCorrectZoneId(userInput);
                        database.updateUserTimeZone(chatId, correctedZoneId);
                        sendMessage(chatId, "Часовий пояс успішно змінено на " + correctedZoneId);

                        // Повернення до основного стану
                        currentState = State.MAIN;
                    } else {
                        sendMessage(chatId, "Некоректний часовий пояс. Спробуйте ще раз. Наприклад: Europe/Warsaw");
                    }
                    break;

                case AddWork:
                    currentState = State.SavingWork;

                    break;
                case SavingWork:
                    if (database.getUserJobs(chatId).size() >= 1){
                        if (messageText.equals("Головне меню") || messageText.equals("Назад")) {
                            currentState = State.MAIN;
                            menuMain(chatId, "Оберіть дію");  // Показуємо головне меню
                        }
                    }
                    if (formatString(messageText)) {

                        if (database.workExists(chatId, messageText)) {
                            sendMessage(chatId,"Робота з такою назвую у вас вже є !!! Вкажіть іншу назву");

                            return;
                        }
                        else  if (messageText.equals("Головне меню") || messageText.equals("Назад")) {
                            currentState = State.MAIN;

                        }
                        else {
                            database.addWork(chatId, messageText);
                            sendMessage(chatId, "Роботу додано!");
                            currentState = State.MAIN;
                            handleState(update,chatId);
                        }
                    }
                    else
                    {
                        sendMessage(chatId,"Назва містить недопустимі символи!");
                        currentState=State.AddWork;
                        handleState(update,chatId);
                    }

                    break;

                case MAIN :

                    if (database.getJobNamesForUser(chatId).contains(messageText)) {
                        sendMessage(chatId, "Ви корегуєте " + messageText);
                        selectedWork=messageText;
                        currentState = State.WORKSETTINGS; // Переходьте до стану редагування
                        showSettingUpWorkMenu(chatId);
                    } else if ("Додати роботу".equals(messageText)) {
                        currentState = State.SavingWork;
                        sendMessageWithKeyboard(chatId, database.getUserJobs(chatId).size() > 0
                                        ? "Вкажіть назву нової роботи або скористайтеся кнопками нижче:"
                                        : "Вкажіть назву роботи:",
                                database.getUserJobs(chatId).size() > 0 ? createMainMenuBackKeyboard() : null);


                    } else if (messageText.equals("Нагадування")) {
                        currentState = State.reminderSetup;
                        handleState(update, chatId);

                    }else {
                        menuMain(chatId, "Ваші можливості:\n" +
                                "- Назва роботи: керування роботою\n" +
                                "- ➕ Додати роботу\n" +
                                "- ⏰ Нагадування");
                    }
                    break;

                case WORKSETTINGS:
                {
                    switch (messageText) {

                        case "Додати години":
                            currentState = State.ENTER_HOURS;
                            sendMessageWithKeyboard (chatId, "Введіть кількість годин або скористайтеся кнопками нижче:", createMainMenuBackKeyboard());

                            break;

                        case "Розрахувати кількість год/м":
                            currentState = State.SELECT_WORK_TO_VIEW;
                            handleState(update,chatId);
                            break;


                        case "Видалити роботу":
                            database.deleteJob(chatId, selectedWork);
                            sendMessage(chatId, "Роботу \"" + selectedWork + "\" видалено.");
                            currentState = State.MAIN;
                            handleState(update, chatId);
                            break;

                        case "Редагувати години":
                            currentState=State.editingHours;
                            currentSubState = SubState.ASK_MONTH;
                            handleState(update,chatId);
                            break;


                        case "Назад":

                            currentState = State.MAIN;
                            handleState(update, chatId);
                            break;

                        default:
                            sendMessage(chatId, "Оберіть дію зі списку.");
                            showSettingUpWorkMenu(chatId);
                            break;
                    }
                    break;

                }
                case ENTER_HOURS:
                    try {

                        int hours = Integer.parseInt(messageText); // Вводимо кількість годин
                        database.addWorkHours(chatId, selectedWork, hours);
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
                    List<String> hoursData = database.getWorkHoursData(chatId, selectedWork);
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
                        currentState = State.WORKSETTINGS;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для обраної роботи
                    } else if (database.getJobNamesForUser(chatId).contains(messageText)) {
                        selectedWork = messageText;
                        currentState = State.WORKSETTINGS;
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
                    }
                  else   if ("Назад".equals(messageText)) {
                        currentState = State.WORKSETTINGS;
                        currentSubState = SubState.NONE;
                        showSettingUpWorkMenu(chatId);
                        return;
                    }
                    else if (database.getJobNamesForUser(chatId).contains(messageText)) {
                        selectedWork = messageText;
                        currentState = State.WORKSETTINGS;
                        currentSubState = SubState.NONE;
                        showSettingUpWorkMenu(chatId);  // Показуємо меню коригування для нової обраної роботи
                        return;
                    }
// Логіка для кожного підстану
                    switch (currentSubState) {
                        case ASK_MONTH:
                            sendMessageWithKeyboard(chatId, "Введіть місяць, який хочете змінити.", createMainMenuBackKeyboard());
                            currentSubState = SubState.WAIT_FOR_MONTH;
                            break;

                        case WAIT_FOR_MONTH:
                            try {
                                selectedMonth = Integer.parseInt(messageText);
                                sendMessageWithKeyboard(chatId, "Введіть день, який хочете змінити.", createMainMenuBackKeyboard());
                                currentSubState = SubState.WAIT_FOR_DAY;
                            } catch (NumberFormatException e) {
                                sendMessageWithKeyboard(chatId, "Введіть коректне значення для місяця.", createMainMenuBackKeyboard());
                            }
                            break;

                        case WAIT_FOR_DAY:
                            try {
                                selectedDay = Integer.parseInt(messageText);
                                sendMessageWithKeyboard(chatId, "Введіть кількість годин.", createMainMenuBackKeyboard());
                                currentSubState = SubState.WAIT_FOR_HOURS;
                            } catch (NumberFormatException e) {
                                sendMessageWithKeyboard(chatId, "Введіть коректне значення для дня.", createMainMenuBackKeyboard());
                            }
                            break;

                        case WAIT_FOR_HOURS:
                            try {
                                int hours = Integer.parseInt(messageText);
                                sendMessage(chatId,database.editingHoursWork(chatId, selectedWork, selectedMonth, selectedDay, hours));

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



                //нагадування
                case reminderSetup:

                    currentSubState = SubState.ASK_HOURS;
                    currentState = State.reminderHours;
                    sendMessage(chatId,"Налаштуйте нагадування:\n" +
                            "- Створити нове нагадування\n" +
                            "- Видалити нагадування\n" +
                            "\n" +
                            "\uD83D\uDCE2 Увімкніть сповіщення для цього чату, щоб отримувати нагадування вчасно!");
handleState(update,chatId);
                    break;

                case reminderHours:

                    switch (currentSubState) {
                        case ASK_HOURS:
                            if ("Видалити нагадування".equals(messageText)) {
                                ReminderManager reminderManager = main.getReminderManager();
                                reminderManager.deleteReminder(chatId); // Додайте метод видалення
                                sendMessage(chatId, "Нагадування успішно видалено.");
                                currentState = State.MAIN;
                                currentSubState = SubState.NONE;
                                return;
                            }
                            sendMessageWithKeyboard(chatId, "Введіть годину для надсилання нагадування (0-23):", createReminderKeyboard());
                            currentSubState = SubState.WAIT_FOR_HOURS_R;

                            break;

                        case WAIT_FOR_HOURS_R:
                            if ("Назад".equals(messageText)) {
                                currentState = State.MAIN;
                                currentSubState = SubState.NONE;
                                menuMain(chatId, "Оберіть дію");
                                return;
                            } else  if ("Видалити нагадування".equals(messageText)) {
                                ReminderManager reminderManager = main.getReminderManager();
                                reminderManager.deleteReminder(chatId); // Додайте метод видалення
                                sendMessage(chatId, "Нагадування успішно видалено.");
                                currentState = State.MAIN;
                                currentSubState = SubState.NONE;
                                return;
                            }
                            else {
                                try {
                                    int hours = Integer.parseInt(messageText);
                                    if (hours >= 0 && hours <= 23) {
                                        rHours = hours;
                                        sendMessage(chatId, "Введіть хвилини для надсилання нагадування (0-59):");
                                        currentState = State.reminderMinutes;
                                        currentSubState = SubState.WAIT_FOR_MINUTES_R;
                                    } else {
                                        sendMessage(chatId, "Будь ласка, введіть коректну годину (0-23).");
                                    }
                                } catch (NumberFormatException e) {
                                    sendMessage(chatId, "Будь ласка, введіть числове значення для години.");
                                }
                            }
                            break;

                        default:
                            currentSubState = SubState.ASK_HOURS;
                            break;
                    }
                    break;
                case reminderMinutes:
                    switch (currentSubState) {
                        case NONE: // Перехід до запиту хвилин
                            sendMessage(chatId, "Введіть хвилини для надсилання нагадування (0-59):");
                            currentSubState = SubState.WAIT_FOR_MINUTES_R;
                            handleState(update, chatId);
                            break;

                        case WAIT_FOR_MINUTES_R:
                            if ("Назад".equals(messageText)) {
                                currentState = State.reminderHours; // Повертаємось до попереднього стану
                                currentSubState = SubState.ASK_HOURS;
                               handleState(update,chatId);
                                return;
                            } else {
                                try {
                                    int minutes = Integer.parseInt(messageText);
                                    if (minutes >= 0 && minutes <= 59) {
                                        rMinutes = minutes;

                                        // Виклик збереження часу нагадування в базі
                                        ReminderManager reminderManager = main.getReminderManager();
                                        reminderManager.updateReminderTime(chatId, rHours, rMinutes);



                                        sendMessage(chatId, "Нагадування успішно налаштоване!");
                                        currentState = State.MAIN;
                                        currentSubState = SubState.NONE;
                                        menuMain(chatId, "Оберіть дію");
                                    } else {
                                        sendMessage(chatId, "Будь ласка, введіть коректне значення хвилин (0-59).");
                                    }
                                } catch (NumberFormatException e) {
                                    sendMessage(chatId, "Будь ласка, введіть числове значення для хвилин.");
                                }
                            }
                            break;

                        default:
                            currentSubState = SubState.NONE;
                            break;
                    }
                    break;

                default:
                    sendMessage(chatId, "Невідома дія. Спробуйте ще раз.");
                    currentState = State.MAIN; // За замовчуванням повертаємо в головне меню
            }
        }
    }


    private void menuMain(Long chatId, String text) {
        DataBase database = DataBase.getInstance(); // Отримуємо Singleton

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        for (String job : database.getJobNamesForUser(chatId)) {
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

//нагадування

    private ReplyKeyboardMarkup createReminderKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);  // Вимкнено, щоб протестувати
        keyboardMarkup.setSelective(false);

        KeyboardRow mainRow = new KeyboardRow();
        mainRow.add(new KeyboardButton("Видалити нагадування"));

        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("Назад"));

        keyboardMarkup.setKeyboard(List.of(mainRow, backRow));

        return keyboardMarkup;
    }



    @Override
    public String getBotUsername() {
        return "RecordOfWorkingDays_bot";
    }
    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }
}