import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Theatre.java
 *
 * Central coordinator for all Shows and Bookings in CineBook.
 * Backed by TheatreDao for MySQL persistence and ACID transactions.
 *
 * Responsibilities:
 *   - Load and expose the list of available shows and live seat grids.
 *   - Load and record bookings from MySQL.
 *   - Coordinate booking creation and cancellation through TheatreDao.
 *   - Format and display active bookings and show schedules.
 */
public class Theatre {

    // ------------------------------------------------------------------ //
    private final String         name;
    private final TheatreDao     dao;
    private final List<Show>     shows;
    private final List<Booking>  bookings;

    // ------------------------------------------------------------------ //
    // Constructors
    // ------------------------------------------------------------------ //
    public Theatre(String name, TheatreDao dao) throws SQLException {
        this.name     = name;
        this.dao      = dao != null ? dao : new TheatreDao();
        this.shows    = new ArrayList<>();
        this.bookings = new ArrayList<>();
        initFromDatabase();
    }

    public Theatre(String name) throws SQLException {
        this(name, new TheatreDao());
    }

    // ------------------------------------------------------------------ //
    // Database Initialization
    // ------------------------------------------------------------------ //
    private void initFromDatabase() throws SQLException {
        // Idempotently ensure shows and 40 seats each exist in MySQL
        dao.ensureSeeded();

        // Load shows with their live seat maps
        shows.clear();
        shows.addAll(dao.getAllShows());

        // Load existing bookings
        bookings.clear();
        bookings.addAll(dao.getAllBookings(shows));
    }

    // ------------------------------------------------------------------ //
    // Getters
    // ------------------------------------------------------------------ //
    public String        getName()     { return name;     }
    public List<Show>    getShows()    { return shows;    }
    public List<Booking> getBookings() { return bookings; }

    // ------------------------------------------------------------------ //
    // Show listing
    // ------------------------------------------------------------------ //

    /** Prints the numbered list of shows to standard output. */
    public void displayShows() {
        System.out.println("\n" + "=".repeat(50));
        System.out.printf("          %s – NOW SHOWING%n", name.toUpperCase());
        System.out.println("=".repeat(50));
        for (int i = 0; i < shows.size(); i++) {
            Show s = shows.get(i);
            System.out.printf("  [%d] %-25s  %s%n",
                    i + 1, s.getMovieName(), s.getShowTime());
        }
        System.out.println("=".repeat(50));
    }

    // ------------------------------------------------------------------ //
    // Booking
    // ------------------------------------------------------------------ //

    /**
     * Creates and records a booking in a MySQL transaction.
     *
     * @param show       the chosen show
     * @param seatCodes  seats the user wants to book
     * @param couponCode coupon entered by the user (may be empty or invalid)
     * @return the confirmed Booking object
     * @throws IllegalArgumentException on validation failure, seat conflict, or duplicate
     */
    public Booking makeBooking(Show show, String[] seatCodes, String couponCode) {
        Booking booking = dao.makeBooking(show, seatCodes, couponCode);

        // Warn the user if the coupon entered was not recognised
        if (!couponCode.isEmpty() && booking.getCouponUsed().isEmpty()) {
            System.out.printf(
                    "%n  [!] Coupon '%s' is not valid - full price applied.%n%n",
                    couponCode);
        }

        bookings.add(booking);
        return booking;
    }

    // ------------------------------------------------------------------ //
    // Cancellation
    // ------------------------------------------------------------------ //

    /**
     * Cancels a booking in a MySQL transaction and updates in-memory state.
     *
     * @param bookingId the ID entered by the user
     * @return the cancelled Booking object
     * @throws IllegalArgumentException if not found or already cancelled
     */
    public Booking cancelBooking(String bookingId) {
        Map<Integer, Show> showMap = new HashMap<>();
        for (Show s : shows) {
            showMap.put(s.getShowId(), s);
        }

        Booking cancelled = dao.cancelBooking(bookingId, showMap);

        // Update local booking in list if present
        for (Booking b : bookings) {
            if (b.getBookingId().equalsIgnoreCase(bookingId.trim())) {
                b.setCancelled(true);
                break;
            }
        }

        return cancelled;
    }

    // ------------------------------------------------------------------ //
    // Active bookings listing
    // ------------------------------------------------------------------ //

    /** Prints all active (non-cancelled) bookings. */
    public void displayActiveBookings() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("          YOUR ACTIVE BOOKINGS");
        System.out.println("=".repeat(50));

        boolean any = false;
        for (Booking b : bookings) {
            if (!b.isCancelled()) {
                System.out.printf("  %-8s | %-20s | %-10s | Rs. %.0f%n",
                        b.getBookingId(),
                        b.getShow().getMovieName(),
                        String.join(",", b.getSeatCodes()),
                        b.getAmountCharged());
                any = true;
            }
        }
        if (!any) {
            System.out.println("  No active bookings.");
        }
        System.out.println("=".repeat(50) + "\n");
    }
}
