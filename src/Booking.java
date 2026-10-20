/**
 * Booking.java
 *
 * Immutable-ish record of a confirmed seat reservation.
 *
 * Key responsibilities:
 *   - Auto-generate a unique booking ID using a static counter.
 *   - Store the show, reserved seat codes, amount charged, and
 *     whether the booking has been cancelled.
 *   - Expose calculateTotal() (no coupon) and an overloaded
 *     calculateTotal(String coupon) that applies a 10% discount
 *     for the code "CINE10".
 *
 * The amount field is set at construction time via calculateTotal()
 * so that cancellation refunds can always be derived from it later.
 *
 * NOTE: Booking objects are only created by Theatre.makeBooking() after
 * the Show has successfully validated and committed the seat changes.
 * This avoids wasting booking IDs on failed attempts.
 */
public class Booking {

    // ------------------------------------------------------------------ //
    // Valid coupon
    // ------------------------------------------------------------------ //
    private static final String VALID_COUPON   = "CINE10";
    private static final double COUPON_DISCOUNT = 0.10;   // 10 %

    // ------------------------------------------------------------------ //
    // Static counter for ID generation
    // ------------------------------------------------------------------ //
    private static int counter = 1000;

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
    // Constructors (overloading for coupon support)
    // ------------------------------------------------------------------ //

    /**
     * Creates a booking without a coupon code.
     * Amount is computed via calculateTotal().
     */
    public Booking(Show show, String[] seatCodes) {
        this(show, seatCodes, "");
    }

    /**
     * Creates a booking with an optional coupon code.
     * Amount is computed via calculateTotal(couponCode).
     *
     * @param couponCode the coupon string entered by the user (may be empty or invalid)
     */
    public Booking(Show show, String[] seatCodes, String couponCode) {
        this.bookingId     = "CB" + (++counter);
        this.show          = show;
        this.seatCodes     = seatCodes;
        this.cancelled     = false;

        double total = calculateTotal(couponCode);
        this.amountCharged = total;

        // Store the coupon only if it was actually valid/applied
        this.couponUsed = isCouponValid(couponCode) ? couponCode.toUpperCase() : "";
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
        double total = 0;
        for (String code : seatCodes) {
            char row = code.charAt(0);
            total += SeatType.fromRow(row).getPrice();
        }
        return total;
    }

    /**
     * Overloaded version: calculates total, applying a 10% discount if
     * the supplied coupon matches "CINE10" (case-insensitive).
     * An unrecognised coupon is silently ignored (no crash, no discount).
     *
     * @param couponCode the coupon string; may be null, empty, or invalid
     * @return discounted (or full) amount in rupees
     */
    public double calculateTotal(String couponCode) {
        double base = calculateTotal();
        if (isCouponValid(couponCode)) {
            return base * (1.0 - COUPON_DISCOUNT);
        }
        return base;
    }

    // ------------------------------------------------------------------ //
    // Coupon validation helper
    // ------------------------------------------------------------------ //
    private boolean isCouponValid(String code) {
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
