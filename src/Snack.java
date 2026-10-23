import java.util.Objects;

/**
 * Snack.java
 *
 * Represents a snack or beverage menu item available for ordering in CineBook.
 * Snack items are persisted in MySQL and retrieved dynamically.
 */
public class Snack {

    private final String  snackId;
    private final String  snackName;
    private final String  category;
    private final double  price;
    private final boolean isAvailable;

    public Snack(String snackId, String snackName, String category, double price, boolean isAvailable) {
        this.snackId     = snackId != null ? snackId.trim().toUpperCase() : "";
        this.snackName   = snackName != null ? snackName.trim() : "";
        this.category    = category != null ? category.trim() : "";
        this.price       = price;
        this.isAvailable = isAvailable;
    }

    public Snack(String snackId, String snackName, String category, double price) {
        this(snackId, snackName, category, price, true);
    }

    public String  getSnackId()     { return snackId;     }
    public String  getSnackName()   { return snackName;   }
    public String  getCategory()    { return category;    }
    public double  getPrice()       { return price;       }
    public boolean isAvailable()    { return isAvailable; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Snack)) return false;
        Snack snack = (Snack) o;
        return snackId.equalsIgnoreCase(snack.snackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snackId.toUpperCase());
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s) [Rs. %.0f]", snackId, snackName, category, price);
    }
}
