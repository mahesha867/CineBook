/**
 * Show.java
 *
 * Represents a single movie show.
 * Each Show maintains its own 5-row x 8-column seat grid (char[][]) so
 * the seat state of one show never interferes with another.
 *
 * Grid encoding:
 *   'O' – seat is available
 *   'X' – seat is booked
 *
 * Implements Bookable; all booking and cancellation logic lives here.
 */
public class Show implements Bookable {

    // ------------------------------------------------------------------ //
    // Constants
    // ------------------------------------------------------------------ //
    public static final int ROWS    = 5;   // A to E
    public static final int COLUMNS = 8;   // 1 to 8

    private static final char AVAILABLE = 'O';
    private static final char BOOKED    = 'X';

    // ------------------------------------------------------------------ //
    // Fields
    // ------------------------------------------------------------------ //
    private final String movieName;
    private final String showTime;

    /** 2-D grid: seats[row][col], row 0='A', col 0='1' */
    private final char[][] seats;

    // ------------------------------------------------------------------ //
    // Constructor
    // ------------------------------------------------------------------ //
    public Show(String movieName, String showTime) {
        this.movieName = movieName;
        this.showTime  = showTime;
        this.seats     = new char[ROWS][COLUMNS];

        // Initialise every seat as available
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                seats[r][c] = AVAILABLE;
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Getters
    // ------------------------------------------------------------------ //
    public String getMovieName() { return movieName; }
    public String getShowTime()  { return showTime;  }

    // ------------------------------------------------------------------ //
    // Seat-map display
    // ------------------------------------------------------------------ //

    /**
     * Prints the current seat map to standard output.
     * Example output:
     *   =============================================
     *           SEAT MAP – Inception (10:30 AM)
     *   =============================================
     *        1   2   3   4   5   6   7   8
     *   A  [ O ] [ O ] [ O ] ...
     *   ...
     *   =============================================
     *   Legend:  O = Available   X = Booked
     *   Pricing: REGULAR(A-B)=Rs.150  PREMIUM(C-D)=Rs.250  VIP(E)=Rs.400
     */
    public void displaySeatMap() {
        System.out.println("=".repeat(57));
        System.out.printf("        SEAT MAP - %s (%s)%n", movieName, showTime);
        System.out.println("=".repeat(57));

        // Column header
        System.out.print("     ");
        for (int c = 1; c <= COLUMNS; c++) {
            System.out.printf("  %d ", c);
        }
        System.out.println();

        // Rows
        for (int r = 0; r < ROWS; r++) {
            char rowLabel = (char) ('A' + r);
            System.out.printf("  %c  ", rowLabel);
            for (int c = 0; c < COLUMNS; c++) {
                System.out.printf("[ %c ]", seats[r][c]);
            }
            // Seat type label on the right
            SeatType type = SeatType.fromRow(rowLabel);
            System.out.printf("  <- %s (Rs.%d)%n", type, type.getPrice());
        }

        System.out.println("=".repeat(57));
        System.out.println("  Legend  :  O = Available    X = Booked");
        System.out.println("=".repeat(57));
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    /**
     * Parses a seat code like "C3" into a {row, col} index pair.
     *
     * @param code seat code (case-insensitive)
     * @return int[] {rowIndex, colIndex}  (0-based)
     * @throws IllegalArgumentException if the code format is invalid
     */
    private int[] parseCode(String code) {
        code = code.trim().toUpperCase();
        if (code.length() < 2 || code.length() > 2) {
            throw new IllegalArgumentException(
                    "'" + code + "' is not a valid seat code. Use format like A1, E8.");
        }
        char rowChar = code.charAt(0);
        char colChar = code.charAt(1);

        if (rowChar < 'A' || rowChar > 'E') {
            throw new IllegalArgumentException(
                    "Row '" + rowChar + "' is invalid. Valid rows are A to E.");
        }
        if (colChar < '1' || colChar > '8') {
            throw new IllegalArgumentException(
                    "Column '" + colChar + "' is invalid. Valid columns are 1 to 8.");
        }

        return new int[]{ rowChar - 'A', colChar - '1' };
    }

    /**
     * Checks whether a seat is currently available without modifying state.
     *
     * @param rowIdx 0-based row index
     * @param colIdx 0-based column index
     * @return true if the seat is available
     */
    private boolean isAvailable(int rowIdx, int colIdx) {
        return seats[rowIdx][colIdx] == AVAILABLE;
    }

    // ------------------------------------------------------------------ //
    // Bookable implementation
    // ------------------------------------------------------------------ //

    /**
     * All-or-nothing seat booking.
     *
     * Phase 1 - Validate: parse every code and confirm each seat is
     *            available; collect errors without touching the grid.
     * Phase 2 - Commit: only if Phase 1 produced zero errors, mark every
     *            seat as booked and return the normalised seat codes.
     * The caller (Theatre.makeBooking) is responsible for creating the
     * single Booking object so only one ID is ever consumed per transaction.
     *
     * @param seatCodes array of seat code strings entered by the user
     * @return normalised (uppercase, trimmed) seat codes that were reserved
     * @throws IllegalArgumentException (with a descriptive message) if any
     *         code is invalid or any seat is already booked
     */
    @Override
    public String[] bookSeats(String[] seatCodes) {
        // ---- Phase 1: Validate ----------------------------------------
        int[][] indices = new int[seatCodes.length][2];
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < seatCodes.length; i++) {
            try {
                int[] idx = parseCode(seatCodes[i]);
                indices[i] = idx;
                if (!isAvailable(idx[0], idx[1])) {
                    errors.append(String.format(
                            "  - Seat %s is already booked.%n",
                            seatCodes[i].trim().toUpperCase()));
                }
            } catch (IllegalArgumentException ex) {
                errors.append("  - ").append(ex.getMessage()).append("\n");
            }
        }

        if (errors.length() > 0) {
            throw new IllegalArgumentException(
                    "Booking failed. Please fix the following issues:\n" + errors);
        }

        // ---- Phase 2: Commit ------------------------------------------
        String[] normCodes = new String[seatCodes.length];
        for (int i = 0; i < seatCodes.length; i++) {
            seats[indices[i][0]][indices[i][1]] = BOOKED;
            normCodes[i] = seatCodes[i].trim().toUpperCase();
        }

        return normCodes;   // caller builds the Booking
    }

    /**
     * Frees the seats associated with a booking (sets them back to 'O').
     * Called after a cancellation has been validated by Theatre.
     *
     * @param booking the booking whose seats should be freed
     */
    @Override
    public void cancelBooking(Booking booking) {
        for (String code : booking.getSeatCodes()) {
            int[] idx = parseCode(code);
            seats[idx[0]][idx[1]] = AVAILABLE;
        }
    }

    // ------------------------------------------------------------------ //
    // toString
    // ------------------------------------------------------------------ //
    @Override
    public String toString() {
        return String.format("%s  [%s]", movieName, showTime);
    }
}
