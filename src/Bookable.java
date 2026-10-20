/**
 * Bookable.java
 *
 * Interface that declares the core booking operations any bookable
 * entity (e.g., a Show) must support.
 *
 * bookSeats()      – validate and mark seats; returns normalised codes.
 * cancelBooking()  – free the seats tied to a given booking.
 */
public interface Bookable {

    /**
     * Validates and commits the seat reservation on the grid.
     * Returns the normalised (trimmed, uppercased) seat codes so the
     * caller can create exactly one Booking object with the right ID.
     *
     * @param seatCodes array of seat codes, e.g. {"C3","C4"}
     * @return normalised seat codes that were successfully reserved
     * @throws IllegalArgumentException if any seat code is invalid or already booked
     */
    String[] bookSeats(String[] seatCodes);

    /**
     * Cancels a previously made booking and frees the associated seats.
     *
     * @param booking the Booking to cancel
     */
    void cancelBooking(Booking booking);
}
