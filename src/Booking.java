import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Booking.java
 *
 * Record of a confirmed ticket reservation and optional movie snacks order.
 *
 * Key responsibilities:
 *   - Store booking ID (e.g. "CB1001"), associated show, reserved seat codes,
 *     associated snack orders, amount charged, coupon applied, and cancellation status.
 *   - Provide calculateTotal() and overloaded calculation methods that apply
 *     the 10% coupon discount to the ticket subtotal only.
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
    private final String            bookingId;
    private final Show              show;
    private final String[]          seatCodes;
    private final double            amountCharged;   // Final amount paid
    private final String            couponUsed;      // "" if no coupon applied
    private       boolean           cancelled;
    private final List<SnackOrder>  snackOrders;

    // ------------------------------------------------------------------ //
    // Constructors
    // ------------------------------------------------------------------ //

    /**
     * Full constructor used by DAO when creating or loading bookings from MySQL.
     *
     * @param bookingId     database-generated booking ID (e.g., "CB1001")
     * @param show          the booked Show
     * @param seatCodes     array of seat codes
     * @param amountCharged the final calculated amount paid
     * @param couponUsed    coupon code if applied, otherwise empty string
     * @param cancelled     true if booking was cancelled
     * @param snackOrders   list of snack orders tied to this booking
     */
    public Booking(String bookingId, Show show, String[] seatCodes, double amountCharged,
                   String couponUsed, boolean cancelled, List<SnackOrder> snackOrders) {
        this.bookingId     = bookingId;
        this.show          = show;
        this.seatCodes     = seatCodes;
        this.amountCharged = amountCharged;
        this.couponUsed    = (couponUsed != null && isCouponValid(couponUsed)) ? couponUsed.toUpperCase() : (couponUsed == null ? "" : couponUsed.trim());
        this.cancelled     = cancelled;
        this.snackOrders   = (snackOrders != null) ? new ArrayList<>(snackOrders) : new ArrayList<>();
    }

    /**
     * Constructor for backwards compatibility without snacks.
     */
    public Booking(String bookingId, Show show, String[] seatCodes, double amountCharged,
                   String couponUsed, boolean cancelled) {
        this(bookingId, show, seatCodes, amountCharged, couponUsed, cancelled, Collections.emptyList());
    }

    /**
     * Convenience constructor without a coupon code or snacks.
     */
    public Booking(Show show, String[] seatCodes) {
        this(show, seatCodes, "", Collections.emptyList());
    }

    /**
     * Convenience constructor with an optional coupon code and no snacks.
     */
    public Booking(Show show, String[] seatCodes, String couponCode) {
        this(show, seatCodes, couponCode, Collections.emptyList());
    }

    /**
     * Convenience constructor with seats, coupon code, and snack orders.
     */
    public Booking(Show show, String[] seatCodes, String couponCode, List<SnackOrder> snackOrders) {
        this.show          = show;
        this.seatCodes     = seatCodes;
        this.cancelled     = false;
        this.snackOrders   = (snackOrders != null) ? new ArrayList<>(snackOrders) : new ArrayList<>();
        this.amountCharged = computeGrandTotal(seatCodes, couponCode, this.snackOrders);
        this.couponUsed    = isCouponValid(couponCode) ? couponCode.toUpperCase() : "";
        this.bookingId     = "CB_TEMP";
    }

    // ------------------------------------------------------------------ //
    // Price calculation helpers (Method Overloading)
    // ------------------------------------------------------------------ //

    /**
     * Computes the un-discounted ticket subtotal based on SeatType enum prices.
     */
    public static double computeTicketSubtotal(String[] seatCodes) {
        double total = 0.0;
        if (seatCodes != null) {
            for (String code : seatCodes) {
                if (code != null && !code.trim().isEmpty()) {
                    char row = code.trim().toUpperCase().charAt(0);
                    total += SeatType.fromRow(row).getPrice();
                }
            }
        }
        return total;
    }

    /**
     * Computes the discount amount applied to the ticket subtotal.
     * Discount applies ONLY to ticket subtotal, never snacks.
     */
    public static double computeTicketDiscount(double ticketSubtotal, String couponCode) {
        if (isCouponValid(couponCode)) {
            return ticketSubtotal * COUPON_DISCOUNT;
        }
        return 0.0;
    }

    /**
     * Computes the total snack subtotal.
     */
    public static double computeSnackSubtotal(List<SnackOrder> snackOrders) {
        if (snackOrders == null || snackOrders.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (SnackOrder order : snackOrders) {
            if (order != null) {
                total += order.getSubtotal();
            }
        }
        return total;
    }

    /**
     * Formula: Final Amount = Ticket Subtotal + Snack Subtotal - Applicable Discount.
     */
    public static double computeGrandTotal(String[] seatCodes, String couponCode, List<SnackOrder> snackOrders) {
        double ticketSubtotal = computeTicketSubtotal(seatCodes);
        double discount = computeTicketDiscount(ticketSubtotal, couponCode);
        double snackSubtotal = computeSnackSubtotal(snackOrders);
        return (ticketSubtotal - discount) + snackSubtotal;
    }

    /**
     * Existing method: calculates ticket total after coupon discount.
     */
    public static double computeTotal(String[] seatCodes, String couponCode) {
        double ticketSubtotal = computeTicketSubtotal(seatCodes);
        double discount = computeTicketDiscount(ticketSubtotal, couponCode);
        return ticketSubtotal - discount;
    }

    /**
     * Calculates the full ticket price with no discount and no snacks.
     */
    public double calculateTotal() {
        return computeTotal(this.seatCodes, "");
    }

    /**
     * Calculates ticket price applying coupon discount if valid.
     */
    public double calculateTotal(String couponCode) {
        return computeTotal(this.seatCodes, couponCode);
    }

    /**
     * Overloaded method: calculates grand total with coupon and snack orders.
     */
    public double calculateTotal(String couponCode, List<SnackOrder> snackOrders) {
        return computeGrandTotal(this.seatCodes, couponCode, snackOrders);
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
    public String           getBookingId()       { return bookingId;     }
    public Show             getShow()            { return show;          }
    public String[]         getSeatCodes()       { return seatCodes;     }
    public double           getAmountCharged()   { return amountCharged; }
    public String           getCouponUsed()      { return couponUsed;    }
    public boolean          isCancelled()        { return cancelled;     }
    public List<SnackOrder> getSnackOrders()     { return Collections.unmodifiableList(snackOrders); }

    public double getTicketSubtotal() {
        return computeTicketSubtotal(this.seatCodes);
    }

    public double getTicketDiscount() {
        return computeTicketDiscount(getTicketSubtotal(), this.couponUsed);
    }

    public double getSnackSubtotal() {
        return computeSnackSubtotal(this.snackOrders);
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    // ------------------------------------------------------------------ //
    // Display
    // ------------------------------------------------------------------ //

    /** Prints a formatted booking confirmation to standard output. */
    public void printSummary() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("              BOOKING CONFIRMATION");
        System.out.println("=".repeat(50));
        System.out.printf("Booking ID       : %s%n", bookingId);
        System.out.printf("Movie            : %s%n", show != null ? show.getMovieName() : "N/A");
        System.out.printf("Show Time        : %s%n", show != null ? show.getShowTime() : "N/A");
        System.out.printf("Seats            : %s%n", seatCodes != null ? String.join(", ", seatCodes) : "None");

        System.out.println("\n---------------- TICKET DETAILS ------------------");
        System.out.printf("Ticket Subtotal  : Rs. %.0f%n", getTicketSubtotal());

        if (snackOrders != null && !snackOrders.isEmpty()) {
            System.out.println("\n---------------- SNACK DETAILS -------------------");
            for (SnackOrder order : snackOrders) {
                System.out.println(order.getFormattedSummary());
            }
            System.out.println();
            System.out.printf("Snack Subtotal   : Rs. %.0f%n", getSnackSubtotal());
        }

        System.out.println("\n---------------- PAYMENT DETAILS -----------------");
        if (!couponUsed.isEmpty()) {
            System.out.printf("Coupon           : %s%n", couponUsed);
            System.out.printf("Ticket Discount  : Rs. %.0f%n", getTicketDiscount());
        }
        System.out.printf("Final Amount     : Rs. %.0f%n", amountCharged);
        System.out.println("=".repeat(50));
        System.out.println("Thank you for booking with CineBook!");
        System.out.println("=".repeat(50) + "\n");
    }

    /** Prints a formatted cancellation receipt. */
    public void printCancellationSummary() {
        double refund = amountCharged * 0.80;
        System.out.println("\n" + "=".repeat(50));
        System.out.println("              CANCELLATION RECEIPT");
        System.out.println("=".repeat(50));
        System.out.printf("Booking ID       : %s%n", bookingId);
        System.out.printf("Movie            : %s%n", show != null ? show.getMovieName() : "N/A");
        System.out.printf("Show Time        : %s%n", show != null ? show.getShowTime() : "N/A");
        System.out.printf("Seats freed      : %s%n", seatCodes != null ? String.join(", ", seatCodes) : "None");
        if (snackOrders != null && !snackOrders.isEmpty()) {
            System.out.printf("Snack items      : %d item(s) preserved%n", snackOrders.size());
        }
        System.out.printf("Amount Paid      : Rs. %.0f%n", amountCharged);
        System.out.printf("Refund (80%%)     : Rs. %.0f%n", refund);
        System.out.println("=".repeat(50) + "\n");
    }
}
