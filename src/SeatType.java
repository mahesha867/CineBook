/**
 * SeatType.java
 *
 * Enum that defines the three seat categories available in the cinema.
 * Each constant carries its own price, so booking logic never hard-codes
 * a rupee amount; it always reads the price through getPrice().
 *
 * Row mapping (enforced in Show):
 *   A, B  -> REGULAR  – Rs. 150
 *   C, D  -> PREMIUM  – Rs. 250
 *   E     -> VIP      – Rs. 400
 */
public enum SeatType {

    REGULAR(150),
    PREMIUM(250),
    VIP(400);

    // ------------------------------------------------------------------ //
    private final int price;   // price in rupees

    // ------------------------------------------------------------------ //
    SeatType(int price) {
        this.price = price;
    }

    /** Returns the ticket price for this seat category (in Rs.). */
    public int getPrice() {
        return price;
    }

    /**
     * Derives the SeatType for a given row character.
     *
     * @param row uppercase row letter ('A' – 'E')
     * @return corresponding SeatType
     * @throws IllegalArgumentException if row is outside A-E
     */
    public static SeatType fromRow(char row) {
        switch (Character.toUpperCase(row)) {
            case 'A': case 'B': return REGULAR;
            case 'C': case 'D': return PREMIUM;
            case 'E':           return VIP;
            default:
                throw new IllegalArgumentException(
                        "Invalid row '" + row + "'. Valid rows: A-E.");
        }
    }
}
