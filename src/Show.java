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
 * Implements Bookable; maintains seat grid state and displays seat map.
 */
public class Show implements Bookable {

    // ------------------------------------------------------------------ //
    // Constants
    // ------------------------------------------------------------------ //
    public static final int ROWS    = 5;   // A to E
    public static final int COLUMNS = 8;   // 1 to 8

    public static final char AVAILABLE = 'O';
    public static final char BOOKED    = 'X';

    // ------------------------------------------------------------------ //
    // Fields
    // ------------------------------------------------------------------ //
    private final int    showId;
    private final String movieName;
    private final String showTime;

    /** 2-D grid: seats[row][col], row 0='A', col 0='1' */
    private final char[][] seats;

    // ------------------------------------------------------------------ //
    // Constructors
    // ------------------------------------------------------------------ //
    public Show(int showId, String movieName, String showTime) {
        this.showId    = showId;
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

    public Show(String movieName, String showTime) {
        this(0, movieName, showTime);
    }

    // ------------------------------------------------------------------ //
    // Getters & Setters
    // ------------------------------------------------------------------ //
    public int    getShowId()    { return showId;    }
    public String getMovieName() { return movieName; }
    public String getShowTime()  { return showTime;  }

    /**
     * Updates the status of an individual seat.
     *
     * @param rowChar uppercase row letter ('A'-'E')
     * @param colNum  1-based column number (1-8)
     * @param booked  true if booked ('X'), false if available ('O')
     */
    public void setSeatStatus(char rowChar, int colNum, boolean booked) {
        int r = Character.toUpperCase(rowChar) - 'A';
        int c = colNum - 1;
        if (r >= 0 && r < ROWS && c >= 0 && c < COLUMNS) {
            seats[r][c] = booked ? BOOKED : AVAILABLE;
        }
    }

    // ------------------------------------------------------------------ //
    // Seat-map display
    // ------------------------------------------------------------------ //

    /**
     * Prints the current seat map to standard output.
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
    public static int[] parseCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Seat code cannot be null.");
        }
        code = code.trim().toUpperCase();
        if (code.length() != 2) {
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
    public boolean isAvailable(int rowIdx, int colIdx) {
        return seats[rowIdx][colIdx] == AVAILABLE;
    }

    // ------------------------------------------------------------------ //
    // Bookable implementation
    // ------------------------------------------------------------------ //

    /**
     * All-or-nothing in-memory seat booking.
     *
     * Phase 1 - Validate: parse every code and confirm each seat is
     *            available; collect errors without touching the grid.
     * Phase 2 - Commit: mark seats as booked and return normalised codes.
     */
    @Override
    public String[] bookSeats(String[] seatCodes) {
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

        String[] normCodes = new String[seatCodes.length];
        for (int i = 0; i < seatCodes.length; i++) {
            seats[indices[i][0]][indices[i][1]] = BOOKED;
            normCodes[i] = seatCodes[i].trim().toUpperCase();
        }

        return normCodes;
    }

    /**
     * Frees the seats associated with a booking (sets them back to 'O').
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
