/**
 * Booking.java
 *
 * Record of a confirmed seat reservation.
 *
 * Key responsibilities:
 *   - Store booking ID (e.g. "CB1001"), associated show, reserved seat codes,
 *     amount charged, coupon applied, and cancellation status.
 *   - Provide calculateTotal() and overloaded calculateTotal(String coupon)
 *     that applies 10% discount for "CINE10".
 *   - Display booking confirmation and cancellation summaries.
 */
public class Booking {

    // ------------------------------------------------------------------ //
    // Valid coupon constants
    // ------------------------------------------------------------------ //
    public static final String VALID_COUPON    = "CINE10";
    public static final double COUPON_DISCOUNT = 0.10;   // 10 %

    // ------------------------------------------------------------------ //
    // Fields
    // ------------------------------------------------------------------ //
    private final String    bookingId;
    private final Show      show;
    private final String[]  seatCodes;
    private final double    amountCharged;   // after any discount
    private final String    couponUsed;      // "" if no coupon applied
    private       boolean   cancelled;

    // ------------------------------------------------------------------ //
    // Constructors
    // ------------------------------------------------------------------ //

    /**
     * Primary constructor used by DAO / repository when creating or loading bookings from the database.
     *
     * @param bookingId     database-generated booking ID (e.g., "CB1001")
     * @param show          the booked Show
     * @param seatCodes     array of seat codes
     * @param amountCharged the calculated amount paid
     * @param couponUsed    coupon code if applied, otherwise empty string
     * @param cancelled     true if booking was cancelled
     */
    public Booking(String bookingId, Show show, String[] seatCodes, double amountCharged, String couponUsed, boolean cancelled) {
        this.bookingId     = bookingId;
        this.show          = show;
        this.seatCodes     = seatCodes;
        this.amountCharged = amountCharged;
        this.couponUsed    = (couponUsed != null && isCouponValid(couponUsed)) ? couponUsed.toUpperCase() : (couponUsed == null ? "" : couponUsed.trim());
        this.cancelled     = cancelled;
    }

    /**
     * Convenience constructor without a coupon code.
     */
    public Booking(Show show, String[] seatCodes) {
        this(show, seatCodes, "");
    }

    /**
     * Convenience constructor with an optional coupon code.
     */
    public Booking(Show show, String[] seatCodes, String couponCode) {
        this.show          = show;
        this.seatCodes     = seatCodes;
        this.cancelled     = false;
        this.amountCharged = calculateTotal(couponCode);
        this.couponUsed    = isCouponValid(couponCode) ? couponCode.toUpperCase() : "";
        this.bookingId     = "CB_TEMP";
    }

    // ------------------------------------------------------------------ //
    // Price calculation  (method overloading)
    // ------------------------------------------------------------------ //

    /**
     * Calculates the full price with no discount.
     * Prices are read from SeatType – never hard-coded here.
     *
     * @return total amount in rupees
     */
    public double calculateTotal() {
        return computeTotal(this.seatCodes, "");
    }

    /**
     * Overloaded version: calculates total, applying a 10% discount if
     * the supplied coupon matches "CINE10" (case-insensitive).
     *
     * @param couponCode the coupon string; may be null, empty, or invalid
     * @return discounted (or full) amount in rupees
     */
    public double calculateTotal(String couponCode) {
        return computeTotal(this.seatCodes, couponCode);
    }

    /**
     * Static helper to compute total given seat codes and an optional coupon code.
     *
     * @param seatCodes  array of seat codes (e.g., {"C3", "C4"})
     * @param couponCode coupon code (e.g. "CINE10")
     * @return total amount in rupees
     */
    public static double computeTotal(String[] seatCodes, String couponCode) {
        double total = 0;
        if (seatCodes != null) {
            for (String code : seatCodes) {
                if (code != null && !code.trim().isEmpty()) {
                    char row = code.trim().toUpperCase().charAt(0);
                    total += SeatType.fromRow(row).getPrice();
                }
            }
        }
        if (isCouponValid(couponCode)) {
            return total * (1.0 - COUPON_DISCOUNT);
        }
        return total;
    }

    // ------------------------------------------------------------------ //
    // Coupon validation helper
    // ------------------------------------------------------------------ //
    public static boolean isCouponValid(String code) {
        return code != null && code.trim().equalsIgnoreCase(VALID_COUPON);
    }

    // ------------------------------------------------------------------ //
    // Getters
    // ------------------------------------------------------------------ //
    public String   getBookingId()     { return bookingId;     }
    public Show     getShow()          { return show;          }
    public String[] getSeatCodes()     { return seatCodes;     }
    public double   getAmountCharged() { return amountCharged; }
    public String   getCouponUsed()    { return couponUsed;    }
    public boolean  isCancelled()      { return cancelled;     }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    // ------------------------------------------------------------------ //
    // Display
    // ------------------------------------------------------------------ //

    /** Prints a formatted booking confirmation to standard output. */
    public void printSummary() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("         BOOKING CONFIRMATION");
        System.out.println("=".repeat(50));
        System.out.printf("  Booking ID   : %s%n", bookingId);
        System.out.printf("  Movie        : %s%n", show.getMovieName());
        System.out.printf("  Show Time    : %s%n", show.getShowTime());
        System.out.printf("  Seats        : %s%n", String.join(", ", seatCodes));
        if (!couponUsed.isEmpty()) {
            System.out.printf("  Coupon       : %s (10%% discount applied)%n", couponUsed);
        }
        System.out.printf("  Amount Paid  : Rs. %.0f%n", amountCharged);
        System.out.println("=".repeat(50));
        System.out.println("  Thank you for booking with CineBook!");
        System.out.println("=".repeat(50) + "\n");
    }

    /** Prints a formatted cancellation receipt. */
    public void printCancellationSummary() {
        double refund = amountCharged * 0.80;
        System.out.println("\n" + "=".repeat(50));
        System.out.println("         CANCELLATION RECEIPT");
        System.out.println("=".repeat(50));
        System.out.printf("  Booking ID   : %s%n", bookingId);
        System.out.printf("  Movie        : %s%n", show.getMovieName());
        System.out.printf("  Show Time    : %s%n", show.getShowTime());
        System.out.printf("  Seats freed  : %s%n", String.join(", ", seatCodes));
        System.out.printf("  Amount Paid  : Rs. %.0f%n", amountCharged);
        System.out.printf("  Refund (80%%) : Rs. %.0f%n", refund);
        System.out.println("=".repeat(50) + "\n");
    }
}
