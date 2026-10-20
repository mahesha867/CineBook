import java.util.ArrayList;
import java.util.List;

/**
 * Theatre.java
 *
 * Central repository for all Shows and Bookings.
 *
 * Responsibilities:
 *   - Seed and expose the list of available shows.
 *   - Record every confirmed booking in an ArrayList<Booking>.
 *   - Handle cancellation lookups and enforce the one-cancel rule.
 *   - Delegate actual seat-grid changes to Show (Bookable).
 */
public class Theatre {

    // ------------------------------------------------------------------ //
    private final String         name;
    private final List<Show>     shows;
    private final List<Booking>  bookings;

    // ------------------------------------------------------------------ //
    // Constructor
    // ------------------------------------------------------------------ //
    public Theatre(String name) {
        this.name     = name;
        this.shows    = new ArrayList<>();
        this.bookings = new ArrayList<>();
        seedShows();
    }

    // ------------------------------------------------------------------ //
    // Seed data
    // ------------------------------------------------------------------ //

    /** Populates the theatre with at least three shows at start-up. */
    private void seedShows() {
        shows.add(new Show("Inception",       "10:30 AM"));
        shows.add(new Show("Interstellar",    "02:00 PM"));
        shows.add(new Show("The Dark Knight", "06:45 PM"));
        shows.add(new Show("Oppenheimer",     "09:30 PM"));
    }

    // ------------------------------------------------------------------ //
    // Getters
    // ------------------------------------------------------------------ //
    public String       getName()     { return name;     }
    public List<Show>   getShows()    { return shows;    }
    public List<Booking> getBookings(){ return bookings; }

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
     * Creates and records a booking for the given show and seat codes.
     * Passes an optional coupon code to the Booking constructor.
     *
     * @param show       the chosen show
     * @param seatCodes  seats the user wants to book
     * @param couponCode coupon entered by the user (may be empty or invalid)
     * @return the new Booking object
     * @throws IllegalArgumentException propagated from Show.bookSeats()
     */
    public Booking makeBooking(Show show, String[] seatCodes, String couponCode) {
        // Delegate grid changes to the Show; receive normalised seat codes back.
        // Show.bookSeats() throws if anything is invalid (all-or-nothing).
        String[] normCodes = show.bookSeats(seatCodes);

        // Create exactly one Booking – the sole consumer of one counter ID.
        Booking booking = new Booking(show, normCodes, couponCode);

        // Warn the user if the coupon they entered was not recognised
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
     * Looks up a booking by ID and cancels it.
     *
     * Rules enforced:
     *   1. The booking ID must exist.
     *   2. The booking must not have been cancelled already.
     * If valid, delegates seat-freeing to Show.cancelBooking() and marks
     * the booking as cancelled.
     *
     * @param bookingId the ID entered by the user
     * @return the cancelled Booking so the caller can print a receipt
     * @throws IllegalArgumentException if not found or already cancelled
     */
    public Booking cancelBooking(String bookingId) {
        for (Booking b : bookings) {
            if (b.getBookingId().equalsIgnoreCase(bookingId.trim())) {
                if (b.isCancelled()) {
                    throw new IllegalArgumentException(
                            "Booking " + bookingId + " has already been cancelled.");
                }
                // Delegate seat-freeing to the Show
                b.getShow().cancelBooking(b);
                b.setCancelled(true);
                return b;
            }
        }
        throw new IllegalArgumentException(
                "No booking found with ID: " + bookingId.trim());
    }

    // ------------------------------------------------------------------ //
    // Active bookings listing (optional convenience)
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
