import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DatabaseConfig.java
 *
 * Manages database configuration and JDBC connections for CineBook.
 *
 * Reads connection settings from environment variables:
 *   - DB_URL      : JDBC connection URL (default: jdbc:mysql://localhost:3306/cinebook?...)
 *   - DB_USER     : MySQL username      (default: root)
 *   - DB_PASSWORD : MySQL password      (default: root)
 */
public class DatabaseConfig {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/cinebook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "root";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[!] MySQL JDBC Driver (com.mysql.cj.jdbc.Driver) not found on classpath.");
            System.err.println("    Please ensure mysql-connector-j.jar is in the classpath (e.g. in lib/).");
        }
    }

    /** Returns the JDBC URL from environment or default. */
    public static String getUrl() {
        String url = System.getenv("DB_URL");
        return (url != null && !url.trim().isEmpty()) ? url.trim() : DEFAULT_URL;
    }

    /** Returns the database username from environment or default. */
    public static String getUser() {
        String user = System.getenv("DB_USER");
        return (user != null && !user.trim().isEmpty()) ? user.trim() : DEFAULT_USER;
    }

    /** Returns the database password from environment or default. */
    public static String getPassword() {
        String pass = System.getenv("DB_PASSWORD");
        return (pass != null) ? pass : DEFAULT_PASS;
    }

    /**
     * Establishes and returns a new JDBC Connection.
     *
     * @return active java.sql.Connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getUrl(), getUser(), getPassword());
    }

    /**
     * Tests the database connection.
     *
     * @throws SQLException if connection cannot be established or validated
     */
    public static void validateConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            if (!conn.isValid(3)) {
                throw new SQLException("Connection test timed out or returned invalid.");
            }
        }
    }
}
