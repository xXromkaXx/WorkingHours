package org.example;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.URI;
import java.net.URISyntaxException;

public class DatabaseConnection {
    private static final String RAW_URL = System.getenv("DATABASE_URL");
    private static String DB_URL;
    private static String USER;
    private static String PASSWORD;

    static {
        try {
            // Парсимо URL та приводимо його до JDBC формату
            URI dbUri = new URI(RAW_URL);
            String[] userInfo = dbUri.getUserInfo().split(":");

            USER = userInfo[0];
            PASSWORD = userInfo[1];

            DB_URL = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();
        } catch (URISyntaxException | NullPointerException e) {
            System.err.println("Помилка парсингу DATABASE_URL: " + e.getMessage());
        }
    }
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASSWORD);
    }
}

