import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TheatreDao.java
 *
 * Data Access Object (DAO) responsible for all database interactions.
 * Keeps SQL queries, connection handling, and transaction management
 * strictly decoupled from console UI and domain presentation logic.
 *
 * Implements:
 *   - Idempotent schema seeding
 *   - Live loading of shows, seats, and bookings
 *   - Concurrency-safe, transactional bookings with SELECT ... FOR UPDATE
 *   - Transactional cancellations with 80% refund calculation
 *   - Safe resource cleanup using try-with-resources
 */
public class TheatreDao {

    // ------------------------------------------------------------------ //
    // Initial Seed Data (Idempotent)
    // ------------------------------------------------------------------ //
    private static final String[][] SEED_SHOWS = {
            {"Inception",       "10:30 AM"},
            {"Interstellar",    "02:00 PM"},
            {"The Dark Knight", "06:45 PM"},
            {"Oppenheimer",     "09:30 PM"}
    };

    /**
     * Ensures initial shows and their 40 seats each are seeded idempotently.
     * If shows or seats are missing, inserts them without duplicating existing rows.
     */
    public void ensureSeeded() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Ensure shows exist
                String insertShowSql = "INSERT INTO shows (movie_name, show_time) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE movie_name = VALUES(movie_name)";
                try (PreparedStatement ps = conn.prepareStatement(insertShowSql)) {
                    for (String[] show : SEED_SHOWS) {
                        ps.setString(1, show[0]);
                        ps.setString(2, show[1]);
                        ps.executeUpdate();
                    }
                }

                // Retrieve all show IDs to ensure their 40 seats exist
                String selectShowsSql = "SELECT show_id FROM shows";
                List<Integer> showIds = new ArrayList<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(selectShowsSql)) {
                    while (rs.next()) {
                        showIds.add(rs.getInt("show_id"));
                    }
                }

                // Ensure 40 seats (A-E, 1-8) for each show
                String insertSeatSql = "INSERT IGNORE INTO seats (show_id, seat_row, seat_number, is_booked) " +
                        "VALUES (?, ?, ?, FALSE)";
                try (PreparedStatement psSeat = conn.prepareStatement(insertSeatSql)) {
                    for (int showId : showIds) {
                        for (char r = 'A'; r <= 'E'; r++) {
                            for (int c = 1; c <= 8; c++) {
                                psSeat.setInt(1, showId);
                                psSeat.setString(2, String.valueOf(r));
                                psSeat.setInt(3, c);
                                psSeat.addBatch();
                            }
                        }
                    }
                    psSeat.executeBatch();
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

    // ------------------------------------------------------------------ //
    // Load Shows & Seat Maps
    // ------------------------------------------------------------------ //

    /**
     * Loads all shows from MySQL along with their live seat status.
     *
     * @return list of Show entities populated with database state
     * @throws SQLException if a database access error occurs
     */
    public List<Show> getAllShows() throws SQLException {
        List<Show> shows = new ArrayList<>();
        String showSql = "SELECT show_id, movie_name, show_time FROM shows ORDER BY show_id ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(showSql)) {

            while (rs.next()) {
                int id = rs.getInt("show_id");
                String movie = rs.getString("movie_name");
                String time = rs.getString("show_time");
                shows.add(new Show(id, movie, time));
            }
        }

        // Load seats for each show
        String seatSql = "SELECT seat_row, seat_number, is_booked FROM seats WHERE show_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(seatSql)) {

            for (Show show : shows) {
                ps.setInt(1, show.getShowId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        char rowChar = rs.getString("seat_row").charAt(0);
                        int colNum = rs.getInt("seat_number");
                        boolean isBooked = rs.getBoolean("is_booked");
                        show.setSeatStatus(rowChar, colNum, isBooked);
                    }
                }
            }
        }

        return shows;
    }

    // ------------------------------------------------------------------ //
    // Load Bookings
    // ------------------------------------------------------------------ //

    /**
     * Loads all bookings from MySQL and maps them to their respective Show entities.
     *
     * @param shows the list of currently loaded shows
     * @return list of Booking objects
     * @throws SQLException if a database error occurs
     */
    public List<Booking> getAllBookings(List<Show> shows) throws SQLException {
        Map<Integer, Show> showMap = new HashMap<>();
        for (Show s : shows) {
            showMap.put(s.getShowId(), s);
        }

        List<Booking> bookings = new ArrayList<>();
        String bookingSql = "SELECT booking_id, show_id, amount_paid, coupon, is_cancelled " +
                "FROM bookings ORDER BY created_at ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(bookingSql)) {

            while (rs.next()) {
                String bookingId = rs.getString("booking_id");
                int showId = rs.getInt("show_id");
                double amountPaid = rs.getDouble("amount_paid");
                String coupon = rs.getString("coupon");
                boolean isCancelled = rs.getBoolean("is_cancelled");

                Show show = showMap.get(showId);
                if (show == null) {
                    show = new Show(showId, "Unknown Movie", "Unknown Time");
                }

                // Load reserved seats for this booking
                List<String> seatCodes = getSeatsForBooking(conn, bookingId);
                bookings.add(new Booking(
                        bookingId,
                        show,
                        seatCodes.toArray(new String[0]),
                        amountPaid,
                        coupon != null ? coupon : "",
                        isCancelled
                ));
            }
        }

        return bookings;
    }

    private List<String> getSeatsForBooking(Connection conn, String bookingId) throws SQLException {
        List<String> seats = new ArrayList<>();
        String sql = "SELECT seat_row, seat_number FROM booking_seats WHERE booking_id = ? ORDER BY seat_row, seat_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(rs.getString("seat_row") + rs.getInt("seat_number"));
                }
            }
        }
        return seats;
    }

    // ------------------------------------------------------------------ //
    // Transactional Booking
    // ------------------------------------------------------------------ //

    /**
     * Makes a booking inside an ACID database transaction.
     *
     * 1. Validates input seat codes (syntax and duplicates).
     * 2. Locks the requested seats using SELECT ... FOR UPDATE (ordered to prevent deadlocks).
     * 3. Verifies none of the seats are already booked.
     * 4. Calculates total price (reading prices from SeatType enum).
     * 5. Generates collision-safe ID from booking_seq.
     * 6. Inserts into bookings and booking_seats.
     * 7. Updates seats to is_booked = TRUE and sets booking_id.
     * 8. Commits transaction or rolls back on any error.
     *
     * @param show       the chosen Show
     * @param seatCodes  seat code array (e.g. {"C3", "C4"})
     * @param couponCode optional coupon code
     * @return confirmed Booking entity
     * @throws IllegalArgumentException on validation, already-booked, or conflict errors
     */
    public Booking makeBooking(Show show, String[] seatCodes, String couponCode) {
        if (seatCodes == null || seatCodes.length == 0) {
            throw new IllegalArgumentException("No seat codes provided.");
        }

        // Phase 1: Validate syntax and check for duplicates
        StringBuilder syntaxErrors = new StringBuilder();
        Set<String> uniqueCodes = new HashSet<>();
        String[] normCodes = new String[seatCodes.length];

        for (int i = 0; i < seatCodes.length; i++) {
            String rawCode = seatCodes[i] != null ? seatCodes[i].trim().toUpperCase() : "";
            try {
                Show.parseCode(rawCode);
                if (!uniqueCodes.add(rawCode)) {
                    syntaxErrors.append(String.format("  - Duplicate seat code '%s' specified in booking request.%n", rawCode));
                }
                normCodes[i] = rawCode;
            } catch (IllegalArgumentException ex) {
                syntaxErrors.append("  - ").append(ex.getMessage()).append("\n");
            }
        }

        if (syntaxErrors.length() > 0) {
            throw new IllegalArgumentException("Booking failed. Please fix the following issues:\n" + syntaxErrors);
        }

        // Sort seat codes deterministically to prevent deadlocks when locking rows
        Arrays.sort(normCodes, Comparator.comparing(c -> c));

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Phase 2: Lock requested seats with SELECT ... FOR UPDATE
                StringBuilder query = new StringBuilder(
                        "SELECT seat_row, seat_number, is_booked FROM seats WHERE show_id = ? AND (");
                for (int i = 0; i < normCodes.length; i++) {
                    if (i > 0) query.append(" OR ");
                    query.append("(seat_row = ? AND seat_number = ?)");
                }
                query.append(") FOR UPDATE");

                Map<String, Boolean> lockedSeats = new HashMap<>();
                try (PreparedStatement psLock = conn.prepareStatement(query.toString())) {
                    psLock.setInt(1, show.getShowId());
                    int paramIdx = 2;
                    for (String code : normCodes) {
                        psLock.setString(paramIdx++, String.valueOf(code.charAt(0)));
                        psLock.setInt(paramIdx++, Character.getNumericValue(code.charAt(1)));
                    }

                    try (ResultSet rs = psLock.executeQuery()) {
                        while (rs.next()) {
                            String code = rs.getString("seat_row") + rs.getInt("seat_number");
                            lockedSeats.put(code, rs.getBoolean("is_booked"));
                        }
                    }
                }

                // Check availability
                StringBuilder conflictErrors = new StringBuilder();
                for (String code : normCodes) {
                    if (!lockedSeats.containsKey(code)) {
                        conflictErrors.append(String.format("  - Seat %s does not exist for this show.%n", code));
                    } else if (Boolean.TRUE.equals(lockedSeats.get(code))) {
                        conflictErrors.append(String.format("  - Seat %s is already booked.%n", code));
                    }
                }

                if (conflictErrors.length() > 0) {
                    conn.rollback();
                    throw new IllegalArgumentException("Booking failed. Please fix the following issues:\n" + conflictErrors);
                }

                // Phase 3: Calculate price
                double totalAmount = Booking.computeTotal(normCodes, couponCode);
                String appliedCoupon = Booking.isCouponValid(couponCode) ? couponCode.trim().toUpperCase() : null;

                // Phase 4: Generate collision-safe booking ID
                String bookingId;
                try (PreparedStatement psSeq = conn.prepareStatement(
                        "INSERT INTO booking_seq VALUES ()", Statement.RETURN_GENERATED_KEYS)) {
                    psSeq.executeUpdate();
                    try (ResultSet rsSeq = psSeq.getGeneratedKeys()) {
                        if (rsSeq.next()) {
                            long seqId = rsSeq.getLong(1);
                            bookingId = "CB" + seqId;
                        } else {
                            throw new SQLException("Failed to obtain generated key from booking_seq.");
                        }
                    }
                }

                // Phase 5: Insert booking
                String insertBookingSql = "INSERT INTO bookings (booking_id, show_id, amount_paid, coupon, is_cancelled) " +
                        "VALUES (?, ?, ?, ?, FALSE)";
                try (PreparedStatement psBooking = conn.prepareStatement(insertBookingSql)) {
                    psBooking.setString(1, bookingId);
                    psBooking.setInt(2, show.getShowId());
                    psBooking.setDouble(3, totalAmount);
                    if (appliedCoupon != null) {
                        psBooking.setString(4, appliedCoupon);
                    } else {
                        psBooking.setNull(4, java.sql.Types.VARCHAR);
                    }
                    psBooking.executeUpdate();
                }

                // Phase 6: Insert booking_seats
                String insertSeatsSql = "INSERT INTO booking_seats (booking_id, seat_row, seat_number) VALUES (?, ?, ?)";
                try (PreparedStatement psBookingSeats = conn.prepareStatement(insertSeatsSql)) {
                    for (String code : normCodes) {
                        psBookingSeats.setString(1, bookingId);
                        psBookingSeats.setString(2, String.valueOf(code.charAt(0)));
                        psBookingSeats.setInt(3, Character.getNumericValue(code.charAt(1)));
                        psBookingSeats.addBatch();
                    }
                    psBookingSeats.executeBatch();
                }

                // Phase 7: Update seats table
                String updateSeatsSql = "UPDATE seats SET is_booked = TRUE, booking_id = ? " +
                        "WHERE show_id = ? AND seat_row = ? AND seat_number = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSeatsSql)) {
                    for (String code : normCodes) {
                        psUpdate.setString(1, bookingId);
                        psUpdate.setInt(2, show.getShowId());
                        psUpdate.setString(3, String.valueOf(code.charAt(0)));
                        psUpdate.setInt(4, Character.getNumericValue(code.charAt(1)));
                        psUpdate.addBatch();
                    }
                    psUpdate.executeBatch();
                }

                // Commit transaction
                conn.commit();

                // Update in-memory show grid
                for (String code : normCodes) {
                    show.setSeatStatus(code.charAt(0), Character.getNumericValue(code.charAt(1)), true);
                }

                return new Booking(
                        bookingId,
                        show,
                        normCodes,
                        totalAmount,
                        appliedCoupon != null ? appliedCoupon : "",
                        false
                );
            } catch (SQLException e) {
                conn.rollback();
                if (e.getErrorCode() == 1205 || e.getErrorCode() == 1213) {
                    throw new IllegalArgumentException(
                            "Booking failed due to a concurrent booking conflict. Please try again.");
                }
                throw new IllegalStateException("Database error during booking: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database connection error: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ //
    // Transactional Cancellation
    // ------------------------------------------------------------------ //

    /**
     * Cancels a booking inside an ACID transaction.
     *
     * 1. Locks booking row with SELECT ... FOR UPDATE.
     * 2. Rejects non-existent or already-cancelled bookings.
     * 3. Marks booking as cancelled.
     * 4. Frees reserved seats in seats table.
     * 5. Commits transaction and returns updated Booking.
     *
     * @param bookingId booking ID to cancel
     * @param showMap   map of showId -> Show entity for memory update
     * @return cancelled Booking entity with stored amount paid for refund calculation
     */
    public Booking cancelBooking(String bookingId, Map<Integer, Show> showMap) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            throw new IllegalArgumentException("No booking ID entered.");
        }
        String normId = bookingId.trim().toUpperCase();

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock booking row
                String lockSql = "SELECT b.booking_id, b.show_id, b.amount_paid, b.coupon, b.is_cancelled, " +
                        "s.movie_name, s.show_time " +
                        "FROM bookings b " +
                        "JOIN shows s ON b.show_id = s.show_id " +
                        "WHERE b.booking_id = ? FOR UPDATE";

                int showId;
                double amountPaid;
                String coupon;
                boolean isCancelled;
                String movieName;
                String showTime;

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setString(1, normId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw new IllegalArgumentException("No booking found with ID: " + normId);
                        }
                        isCancelled = rs.getBoolean("is_cancelled");
                        if (isCancelled) {
                            conn.rollback();
                            throw new IllegalArgumentException("Booking " + normId + " has already been cancelled.");
                        }
                        showId = rs.getInt("show_id");
                        amountPaid = rs.getDouble("amount_paid");
                        coupon = rs.getString("coupon");
                        movieName = rs.getString("movie_name");
                        showTime = rs.getString("show_time");
                    }
                }

                // Fetch seats for this booking
                List<String> seats = getSeatsForBooking(conn, normId);

                // Mark booking cancelled
                String updateBookingSql = "UPDATE bookings SET is_cancelled = TRUE WHERE booking_id = ?";
                try (PreparedStatement psUpdateB = conn.prepareStatement(updateBookingSql)) {
                    psUpdateB.setString(1, normId);
                    psUpdateB.executeUpdate();
                }

                // Free seats in seats table
                String freeSeatsSql = "UPDATE seats SET is_booked = FALSE, booking_id = NULL " +
                        "WHERE show_id = ? AND booking_id = ?";
                try (PreparedStatement psFree = conn.prepareStatement(freeSeatsSql)) {
                    psFree.setInt(1, showId);
                    psFree.setString(2, normId);
                    psFree.executeUpdate();
                }

                conn.commit();

                // Update in-memory show grid
                Show show = (showMap != null) ? showMap.get(showId) : null;
                if (show == null) {
                    show = new Show(showId, movieName, showTime);
                }
                for (String code : seats) {
                    show.setSeatStatus(code.charAt(0), Character.getNumericValue(code.charAt(1)), false);
                }

                return new Booking(
                        normId,
                        show,
                        seats.toArray(new String[0]),
                        amountPaid,
                        coupon != null ? coupon : "",
                        true
                );
            } catch (SQLException e) {
                conn.rollback();
                throw new IllegalStateException("Database error during cancellation: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database connection error: " + e.getMessage(), e);
        }
    }
}
