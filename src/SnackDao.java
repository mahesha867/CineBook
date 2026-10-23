import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SnackDao.java
 *
 * Data Access Object (DAO) responsible for snack menu persistence and booking snacks mapping.
 * Handles snack retrieval, database seeding, and transactional persistence.
 */
public class SnackDao {

    private static final String[][] SEED_SNACKS = {
            {"S001", "Popcorn",          "Regular",  "120.00"},
            {"S002", "Cheese Popcorn",   "Premium",  "180.00"},
            {"S003", "Nachos",           "Snacks",   "150.00"},
            {"S004", "French Fries",     "Snacks",   "100.00"},
            {"S005", "Veg Sandwich",     "Food",     "130.00"},
            {"S006", "Chicken Sandwich", "Food",     "170.00"},
            {"S007", "Coke",             "Beverage", "80.00"},
            {"S008", "Sprite",           "Beverage", "80.00"},
            {"S009", "Mineral Water",    "Beverage", "40.00"}
    };

    /**
     * Idempotently seeds the predefined snack menu in MySQL.
     * Updates details if already existing without duplicating rows.
     */
    public void ensureSeeded() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Ensure table exists
                String createSnacksTableSql = "CREATE TABLE IF NOT EXISTS snacks (" +
                        "snack_id VARCHAR(10) PRIMARY KEY, " +
                        "snack_name VARCHAR(100) NOT NULL, " +
                        "category VARCHAR(50) NOT NULL, " +
                        "price DECIMAL(10, 2) NOT NULL, " +
                        "is_available BOOLEAN NOT NULL DEFAULT TRUE" +
                        ") ENGINE=InnoDB";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createSnacksTableSql);
                }

                String createBookingSnacksTableSql = "CREATE TABLE IF NOT EXISTS booking_snacks (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "booking_id VARCHAR(20) NOT NULL, " +
                        "snack_id VARCHAR(10) NOT NULL, " +
                        "quantity INT NOT NULL, " +
                        "unit_price DECIMAL(10, 2) NOT NULL, " +
                        "subtotal DECIMAL(10, 2) NOT NULL, " +
                        "CONSTRAINT uk_booking_snack UNIQUE (booking_id, snack_id), " +
                        "CONSTRAINT fk_booking_snacks_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id) ON DELETE CASCADE, " +
                        "CONSTRAINT fk_booking_snacks_snack FOREIGN KEY (snack_id) REFERENCES snacks(snack_id), " +
                        "CONSTRAINT chk_quantity CHECK (quantity > 0)" +
                        ") ENGINE=InnoDB";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createBookingSnacksTableSql);
                }

                // Insert seed items
                String insertSql = "INSERT INTO snacks (snack_id, snack_name, category, price, is_available) " +
                        "VALUES (?, ?, ?, ?, TRUE) " +
                        "ON DUPLICATE KEY UPDATE snack_name = VALUES(snack_name), category = VALUES(category), " +
                        "price = VALUES(price), is_available = VALUES(is_available)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (String[] item : SEED_SNACKS) {
                        ps.setString(1, item[0]);
                        ps.setString(2, item[1]);
                        ps.setString(3, item[2]);
                        ps.setDouble(4, Double.parseDouble(item[3]));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Retrieves all available snacks from MySQL.
     *
     * @return list of available Snack items
     * @throws SQLException on database error
     */
    public List<Snack> getAllAvailableSnacks() throws SQLException {
        List<Snack> list = new ArrayList<>();
        String sql = "SELECT snack_id, snack_name, category, price, is_available " +
                "FROM snacks WHERE is_available = TRUE ORDER BY snack_id ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Snack(
                        rs.getString("snack_id"),
                        rs.getString("snack_name"),
                        rs.getString("category"),
                        rs.getDouble("price"),
                        rs.getBoolean("is_available")
                ));
            }
        }
        return list;
    }

    /**
     * Looks up a snack by its unique ID.
     *
     * @param snackId the snack ID (e.g., "S001")
     * @return the Snack entity or null if not found
     * @throws SQLException on database error
     */
    public Snack getSnackById(String snackId) throws SQLException {
        if (snackId == null || snackId.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT snack_id, snack_name, category, price, is_available FROM snacks WHERE snack_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, snackId.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Snack(
                            rs.getString("snack_id"),
                            rs.getString("snack_name"),
                            rs.getString("category"),
                            rs.getDouble("price"),
                            rs.getBoolean("is_available")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Persists snack orders associated with a booking inside an existing transactional connection.
     *
     * @param conn        active transactional JDBC connection
     * @param bookingId   the confirmed booking ID
     * @param snackOrders list of snack orders to persist
     * @throws SQLException if a database access error or constraint violation occurs
     */
    public void saveBookingSnacks(Connection conn, String bookingId, List<SnackOrder> snackOrders) throws SQLException {
        if (snackOrders == null || snackOrders.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO booking_snacks (booking_id, snack_id, quantity, unit_price, subtotal) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SnackOrder order : snackOrders) {
                ps.setString(1, bookingId);
                ps.setString(2, order.getSnack().getSnackId());
                ps.setInt(3, order.getQuantity());
                ps.setDouble(4, order.getUnitPrice());
                ps.setDouble(5, order.getSubtotal());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Retrieves all snack orders associated with a booking using an existing connection.
     * Preserves historical unit price and subtotal.
     *
     * @param conn      active JDBC connection
     * @param bookingId the booking ID
     * @return list of SnackOrder items for this booking
     * @throws SQLException on database error
     */
    public List<SnackOrder> getSnacksForBooking(Connection conn, String bookingId) throws SQLException {
        List<SnackOrder> orders = new ArrayList<>();
        String sql = "SELECT bs.snack_id, bs.quantity, bs.unit_price, bs.subtotal, " +
                "s.snack_name, s.category, s.is_available " +
                "FROM booking_snacks bs " +
                "LEFT JOIN snacks s ON bs.snack_id = s.snack_id " +
                "WHERE bs.booking_id = ? ORDER BY bs.id ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String snackId = rs.getString("snack_id");
                    String snackName = rs.getString("snack_name");
                    if (snackName == null) snackName = snackId;
                    String category = rs.getString("category");
                    if (category == null) category = "Snacks";
                    boolean isAvailable = rs.getBoolean("is_available");
                    double unitPrice = rs.getDouble("unit_price");
                    int quantity = rs.getInt("quantity");
                    double subtotal = rs.getDouble("subtotal");

                    Snack snack = new Snack(snackId, snackName, category, unitPrice, isAvailable);
                    orders.add(new SnackOrder(snack, quantity, unitPrice, subtotal));
                }
            }
        }
        return orders;
    }

    /**
     * Standalone retrieval of snack orders for a given booking.
     */
    public List<SnackOrder> getSnacksForBooking(String bookingId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return getSnacksForBooking(conn, bookingId);
        }
    }
}
