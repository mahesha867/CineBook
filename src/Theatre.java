import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Theatre.java
 *
 * Central coordinator for all Shows and Bookings in CineBook.
 * Backed by TheatreDao and SnackDao for MySQL persistence and ACID transactions.
 *
 * Responsibilities:
 *   - Load and expose the list of available shows and live seat grids.
 *   - Expose available snack menu loaded from MySQL.
 *   - Coordinate booking creation (with or without snacks) and cancellation through TheatreDao.
 *   - Format and display active bookings and show schedules.
 */
public class Theatre {

    // ------------------------------------------------------------------ //
    private final String        name;
    private final TheatreDao    dao;
    private final SnackService  snackService;
    private final List<Show>    shows;
    private final List<Booking> bookings;

    // ------------------------------------------------------------------ //
    // Constructors
    // ------------------------------------------------------------------ //
    public Theatre(String name, TheatreDao dao) throws SQLException {
        this.name         = name;
        this.dao          = dao != null ? dao : new TheatreDao();
        this.snackService = new SnackService();
        this.shows        = new ArrayList<>();
        this.bookings     = new ArrayList<>();
        initFromDatabase();
    }

    public Theatre(String name) throws SQLException {
        this(name, new TheatreDao());
    }

    // ------------------------------------------------------------------ //
    // Database Initialization
    // ------------------------------------------------------------------ //
    private void initFromDatabase() throws SQLException {
        // Idempotently ensure shows, seats, and snacks exist in MySQL
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
    public String        getName()         { return name;         }
    public List<Show>    getShows()        { return shows;        }
    public List<Booking> getBookings()     { return bookings;     }
    public TheatreDao    getDao()          { return dao;          }
    public SnackService  getSnackService() { return snackService; }

    /**
     * Retrieves all available snacks from the database.
     */
    public List<Snack> getAvailableSnacks() throws SQLException {
        return dao.getSnackDao().getAllAvailableSnacks();
    }

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
     * Creates and records a booking in a MySQL transaction without snacks.
     */
    public Booking makeBooking(Show show, String[] seatCodes, String couponCode) {
        return makeBooking(show, seatCodes, couponCode, Collections.emptyList());
    }

    /**
     * Creates and records a booking with snacks in an atomic MySQL transaction.
     *
     * @param show        the chosen show
     * @param seatCodes   seats the user wants to book
     * @param couponCode  coupon entered by the user (may be empty or invalid)
     * @param snackOrders list of snack orders
     * @return the confirmed Booking object
     * @throws IllegalArgumentException on validation failure, seat conflict, or duplicate
     */
    public Booking makeBooking(Show show, String[] seatCodes, String couponCode, List<SnackOrder> snackOrders) {
        Booking booking = dao.makeBooking(show, seatCodes, couponCode, snackOrders);

        // Warn the user if the coupon entered was not recognised
        if (couponCode != null && !couponCode.trim().isEmpty() && booking.getCouponUsed().isEmpty()) {
            System.out.printf(
                    "%n  [!] Coupon '%s' is not valid - full price applied.%n%n",
                    couponCode.trim());
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
        System.out.println("\n" + "=".repeat(65));
        System.out.println("                   YOUR ACTIVE BOOKINGS");
        System.out.println("=".repeat(65));

        boolean any = false;
        for (Booking b : bookings) {
            if (!b.isCancelled()) {
                int snackCount = b.getSnackOrders() != null ? b.getSnackOrders().size() : 0;
                String snackNote = snackCount > 0 ? String.format("%d snack(s)", snackCount) : "No snacks";
                System.out.printf("  %-8s | %-16s | %-8s | %-11s | Rs. %.0f%n",
                        b.getBookingId(),
                        b.getShow().getMovieName(),
                        String.join(",", b.getSeatCodes()),
                        snackNote,
                        b.getAmountCharged());
                any = true;
            }
        }
        if (!any) {
            System.out.println("  No active bookings.");
        }
        System.out.println("=".repeat(65) + "\n");
    }
}
