import java.util.Objects;

/**
 * SnackOrder.java
 *
 * Represents a snack item and its ordered quantity within a CineBook booking.
 * Encapsulates unit price at the time of booking to ensure historical integrity.
 */
public class SnackOrder {

    private final Snack  snack;
    private       int    quantity;
    private final double unitPrice;
    private       double subtotal;

    /**
     * Primary constructor for new snack orders during booking.
     * Captures current snack price as unitPrice.
     *
     * @param snack    the selected Snack item
     * @param quantity the number of items ordered (> 0)
     */
    public SnackOrder(Snack snack, int quantity) {
        if (snack == null) {
            throw new IllegalArgumentException("Snack cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0.");
        }
        this.snack     = snack;
        this.quantity  = quantity;
        this.unitPrice = snack.getPrice();
        this.subtotal  = this.unitPrice * quantity;
    }

    /**
     * Constructor used by DAO when loading historical bookings from MySQL.
     * Preserves historical unit price and subtotal.
     */
    public SnackOrder(Snack snack, int quantity, double unitPrice, double subtotal) {
        if (snack == null) {
            throw new IllegalArgumentException("Snack cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0.");
        }
        this.snack     = snack;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
        this.subtotal  = subtotal;
    }

    public Snack  getSnack()     { return snack;     }
    public int    getQuantity()  { return quantity;  }
    public double getUnitPrice() { return unitPrice; }
    public double getSubtotal()  { return subtotal;  }

    /**
     * Updates the quantity and recalculates the subtotal.
     *
     * @param newQuantity positive integer
     */
    public void setQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0.");
        }
        this.quantity = newQuantity;
        this.subtotal = this.unitPrice * newQuantity;
    }

    /**
     * Formats this order line for booking confirmation summaries.
     * Example: "Popcorn       x2 : Rs. 240"
     */
    public String getFormattedSummary() {
        return String.format("%-14s x%-2d : Rs. %.0f", snack.getSnackName(), quantity, subtotal);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnackOrder)) return false;
        SnackOrder that = (SnackOrder) o;
        return Objects.equals(snack.getSnackId(), that.snack.getSnackId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(snack.getSnackId());
    }

    @Override
    public String toString() {
        return getFormattedSummary();
    }
}
