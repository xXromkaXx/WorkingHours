
package org.example;
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

public class DataBase {
    private static DataBase instance; // Singleton-інстанс
    private static final String URL = System.getenv("DB_URL");
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASSWORD");


    // Приватний конструктор (захищає від прямого створення об'єктів)
    private DataBase() {
    }

    // Метод для отримання Singleton-інстансу
    public static DataBase getInstance() {
        if (instance == null) {
            instance = new DataBase();
        }
        return instance;
    }

    // Метод для отримання підключення до бази даних
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public String getUserName(Long chatId) {
        String sql = "SELECT username FROM users WHERE chatid = ?";
        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet result = pstmt.executeQuery();

            if (result.next()) {
                return result.getString("username"); // Отримуємо значення стовпця "username"
            } else {
                return null; // Якщо запис не знайдено
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null; // Повертаємо null у випадку помилки
        }
    }


    public void saveUser(Long chatId, String userName) {
        String query = "INSERT INTO users (chatid, username,reminder_hour,reminder_minute,timezone) VALUES (?, ?,18,0,'Europe/Kyiv') ON CONFLICT (chatid) DO UPDATE SET username = ?";
        try (Connection conn = DataBase.getInstance().getConnection();

             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, userName);
            pstmt.setString(3, userName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean UserExists(Long chatId) {
        String sql = "SELECT COUNT(*) FROM users WHERE chatid = ?";
        try (Connection conn = DataBase.getInstance().getConnection(); // Використання Singleton
             PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt(1) > 0; // Якщо кількість більша за 0, користувач існує
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
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

        try (Connection conn = DataBase.getInstance().getConnection(); // Використання Singleton
             PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                jobNames.add(resultSet.getString("work_name"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return jobNames;
    }


    public List<String> getUserJobs(Long chatId) {
        List<String> jobs = new ArrayList<>();
        String query = "SELECT work_name FROM work_types WHERE chatid = ?";

        try (Connection connection =  DataBase.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(resultSet.getString("work_name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return jobs;
    }

    public void addWorkHours(Long chatId, String workName, int hours) {
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

        try (Connection conn = DataBase.getInstance().getConnection();
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
            e.printStackTrace();
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

        try (Connection conn = DataBase.getInstance().getConnection()) {
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
            e.printStackTrace();
        }


    }

    public boolean workExists(Long chatId, String workName) {
        String sql = """
                SELECT j.work_id 
                FROM work_types j
                JOIN work_hours wl ON j.work_id = wl.work_id
                WHERE wl.chatid = ? AND j.work_name = ?
                """;

        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);

            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Повертає true, якщо запис існує
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }



    public boolean deleteJob(Long chatId, String workName) {
        String selectWorkIdSQL = "SELECT work_id FROM work_types WHERE chatid = ? AND work_name = ?";
        String deleteFromWorkHoursSQL = "DELETE FROM work_hours WHERE work_id = ?";
        String deleteFromWorkTypesSQL = "DELETE FROM work_types WHERE work_id = ?";

        try (Connection conn = DataBase.getInstance().getConnection()) {
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



    public String  editingHoursWork(Long chatId, String workName, int month, int day, int hours) {
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

        int dayOfMonth = day;
        int currentMonth = month;

        // Створюємо JSON для поточного дня
        String dayDataJson = "{\"" + dayOfMonth + "\": " + hours + "}";

        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            // Перевіряємо, чи існує запис для цього chatId, work_id, та місяця
            selectStmt.setLong(1, chatId);
            selectStmt.setString(2, workName);
            selectStmt.setInt(3, currentMonth);

            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                // Отримуємо наявний JSON для поточного місяця
                String workData = rs.getString("work_data");

                // Перевіряємо, чи містить JSON запис для вказаного дня
                if (workData.contains("\"" + dayOfMonth + "\":")) {
                    // Якщо день вже є в JSON, оновлюємо запис
                    updateStmt.setString(1, dayDataJson);
                    updateStmt.setLong(2, chatId);
                    updateStmt.setString(3, workName);
                    updateStmt.setInt(4, currentMonth);
                    updateStmt.executeUpdate();

                    return  "Години для обраного дня успішно оновлено.";
                } else {
                    // Якщо дня немає в JSON, повідомляємо користувача

                    return  "Запису для обраного дня немає. Додайте спочатку години для цього дня.";
                }
            } else {
                // Якщо запису для місяця взагалі немає, повідомляємо користувача
                return  "Запису для обраного місяця немає. Додайте спочатку години для цього місяця.";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return  "Сталася помилка під час оновлення робочих годин.";
        }
    }


    public List<String> getWorkHoursData(long chatId, String workName) {
        List<String> hoursData = new ArrayList<>();
        String sql = """
            SELECT work_data
            FROM work_hours
            JOIN work_types ON work_hours.work_id = work_types.work_id
            WHERE work_hours.chatid = ? AND work_types.work_name = ?
            """;

        try (Connection conn = DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, workName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String workDataJson = rs.getString("work_data");
                hoursData = parseWorkData(workDataJson);  // Розпарсимо JSON-дані
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return hoursData;
    }
    private List<String> parseWorkData(String workDataJson) {
        List<String> hoursData = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(workDataJson);
            for (String day : jsonObject.keySet()) {
                int hours = jsonObject.getInt(day);
                hoursData.add("День: " + day + ", Години: " + hours);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return hoursData;
    }



    public void updateUserTimeZone(long chatId, String timeZone) {
        String sql = "UPDATE users SET timezone = ? WHERE chatid = ?";
        try (Connection conn =DataBase.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timeZone);
            pstmt.setLong(2, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getUserTimeZone(long chatId) {
        String sql = "SELECT timezone FROM users WHERE chatid = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("timezone");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "UTC"; // Значення за замовчуванням
    }





}