package org.example;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = System.getenv("DATABASE_URL"); // Отримуємо URL з змінної середовища
    private static final String USER = System.getenv("DB_USER"); // Отримуємо юзера
    private static final String PASSWORD = System.getenv("DB_PASSWORD"); // Отримуємо пароль

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}

