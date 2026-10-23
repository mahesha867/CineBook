import java.util.List;

/**
 * SnackService.java
 *
 * Domain service providing business logic for snack selection, validation,
 * quantity management, and subtotal calculation.
 */
public class SnackService {

    /**
     * Validates that an ordered quantity is strictly positive.
     *
     * @param quantity the quantity to check
     * @throws IllegalArgumentException if quantity <= 0
     */
    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive integer greater than zero.");
        }
    }

    /**
     * Calculates the total price for a collection of snack orders.
     *
     * @param orders list of SnackOrder objects
     * @return sum of all snack order subtotals
     */
    public double calculateSnackSubtotal(List<SnackOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (SnackOrder order : orders) {
            if (order != null) {
                total += order.getSubtotal();
            }
        }
        return total;
    }

    /**
     * Adds a snack to the cart. If the snack already exists in the cart,
     * aggregates the quantity rather than creating duplicate order lines.
     *
     * @param cart     the active cart list
     * @param snack    the selected Snack
     * @param quantity the quantity to add (> 0)
     */
    public void addOrUpdateSnack(List<SnackOrder> cart, Snack snack, int quantity) {
        if (snack == null) {
            throw new IllegalArgumentException("Selected snack cannot be null.");
        }
        validateQuantity(quantity);

        for (SnackOrder order : cart) {
            if (order.getSnack().getSnackId().equalsIgnoreCase(snack.getSnackId())) {
                order.setQuantity(order.getQuantity() + quantity);
                return;
            }
        }
        cart.add(new SnackOrder(snack, quantity));
    }

    /**
     * Modifies the quantity of an existing snack in the cart.
     *
     * @param cart        the active cart list
     * @param snackId     the ID of the snack to modify
     * @param newQuantity positive integer
     * @return true if updated, false if snack was not found in cart
     */
    public boolean modifyQuantity(List<SnackOrder> cart, String snackId, int newQuantity) {
        if (snackId == null || cart == null) {
            return false;
        }
        validateQuantity(newQuantity);

        for (SnackOrder order : cart) {
            if (order.getSnack().getSnackId().equalsIgnoreCase(snackId.trim())) {
                order.setQuantity(newQuantity);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a snack from the cart by its snack ID.
     *
     * @param cart    the active cart list
     * @param snackId the ID of the snack to remove
     * @return true if an item was removed, false otherwise
     */
    public boolean removeSnack(List<SnackOrder> cart, String snackId) {
        if (snackId == null || cart == null) {
            return false;
        }
        return cart.removeIf(order -> order.getSnack().getSnackId().equalsIgnoreCase(snackId.trim()));
    }

    /**
     * Finds a snack order in the cart by its snack ID.
     *
     * @param cart    the active cart list
     * @param snackId the ID of the snack
     * @return the SnackOrder or null if not found
     */
    public SnackOrder findInCart(List<SnackOrder> cart, String snackId) {
        if (snackId == null || cart == null) {
            return null;
        }
        for (SnackOrder order : cart) {
            if (order.getSnack().getSnackId().equalsIgnoreCase(snackId.trim())) {
                return order;
            }
        }
        return null;
    }
}
