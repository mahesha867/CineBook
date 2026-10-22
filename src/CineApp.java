import java.sql.SQLException;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

/**
 * CineApp.java
 *
 * Entry point and console UI for the CineBook application.
 *
 * Responsibilities:
 *   - Validate database connectivity on launch; fail fast with clear diagnostics.
 *   - Drive the main menu loop.
 *   - Read and validate console user input.
 *   - Coordinate Theatre, Show, and Booking interactions.
 *   - Handle exceptions gracefully – never crash on bad user input.
 */
public class CineApp {

    // ------------------------------------------------------------------ //
    private static final Scanner scanner = new Scanner(System.in);
    private static Theatre theatre;

    // ------------------------------------------------------------------ //
    // Entry point
    // ------------------------------------------------------------------ //
    public static void main(String[] args) {
        // Step 1: Validate Database Connection before starting UI
        try {
            DatabaseConfig.validateConnection();
            theatre = new Theatre("CineBook Cinemas");
        } catch (SQLException ex) {
            System.err.println("\n" + "!".repeat(60));
            System.err.println("  [!] FATAL DATABASE ERROR: Unable to connect to MySQL.");
            System.err.println("!".repeat(60));
            System.err.printf("  Target URL : %s%n", DatabaseConfig.getUrl());
            System.err.printf("  User       : %s%n", DatabaseConfig.getUser());
            System.err.printf("  Reason     : %s%n%n", ex.getMessage());
            System.err.println("  Troubleshooting:");
            System.err.println("    1. Ensure your MySQL server service is active.");
            System.err.println("    2. Ensure the 'cinebook' database and schema exist:");
            System.err.println("         mysql -u root -p < database/schema.sql");
            System.err.println("    3. If necessary, set environment variables:");
            System.err.println("         $env:DB_URL      = \"jdbc:mysql://localhost:3306/cinebook...\"");
            System.err.println("         $env:DB_USER     = \"root\"");
            System.err.println("         $env:DB_PASSWORD = \"yourpassword\"");
            System.err.println("!".repeat(60) + "\n");
            System.exit(1);
            return;
        }

        printBanner();
        boolean running = true;

        while (running) {
            printMainMenu();
            int choice = readInt("  Enter your choice: ");

            switch (choice) {
                case 1:
                    handleBooking();
                    break;
                case 2:
                    handleCancellation();
                    break;
                case 3:
                    theatre.displayActiveBookings();
                    break;
                case 4:
                    System.out.println("\n  Thank you for using CineBook. Goodbye!\n");
                    running = false;
                    break;
                default:
                    System.out.println(
                            "\n  [!] Invalid option. Please choose 1, 2, 3, or 4.\n");
            }
        }

        scanner.close();
    }

    // ------------------------------------------------------------------ //
    // Banner
    // ------------------------------------------------------------------ //
    private static void printBanner() {
        System.out.println();
        System.out.println("*".repeat(50));
        System.out.println("*                                                *");
        System.out.println("*          C I N E B O O K   v1.0               *");
        System.out.println("*      Movie Ticket Booking – Console App        *");
        System.out.println("*                                                *");
        System.out.println("*".repeat(50));
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    // Main menu
    // ------------------------------------------------------------------ //
    private static void printMainMenu() {
        System.out.println("--------------------------------------------");
        System.out.println("               MAIN MENU");
        System.out.println("--------------------------------------------");
        System.out.println("  [1] Book Tickets");
        System.out.println("  [2] Cancel Booking");
        System.out.println("  [3] View My Bookings");
        System.out.println("  [4] Exit");
        System.out.println("--------------------------------------------");
    }

    // ------------------------------------------------------------------ //
    // Booking flow
    // ------------------------------------------------------------------ //
    private static void handleBooking() {
        // Step 1 – Pick a show
        theatre.displayShows();
        List<Show> shows = theatre.getShows();

        int showChoice = readInt(
                "  Select a show (1-" + shows.size() + "): ");

        if (showChoice < 1 || showChoice > shows.size()) {
            System.out.printf(
                    "%n  [!] Invalid show number. Please enter 1 to %d.%n%n",
                    shows.size());
            return;
        }

        Show selectedShow = shows.get(showChoice - 1);
        System.out.printf("%n  You selected: %s%n", selectedShow);

        // Step 2 – Display seat map
        selectedShow.displaySeatMap();

        // Step 3 – Enter seat codes
        System.out.println("  Enter seat codes (comma-separated, e.g. A1,B3,C5):");
        System.out.print("  Seats: ");
        String seatInput = scanner.nextLine().trim();

        if (seatInput.isEmpty()) {
            System.out.println("\n  [!] No seat codes entered. Returning to menu.\n");
            return;
        }

        String[] seatCodes = seatInput.split(",");
        for (int i = 0; i < seatCodes.length; i++) {
            seatCodes[i] = seatCodes[i].trim();
        }

        // Step 4 – Optional coupon
        System.out.print("  Enter coupon code (or press Enter to skip): ");
        String coupon = scanner.nextLine().trim();

        // Step 5 – Attempt booking (all-or-nothing database transaction)
        try {
            Booking booking = theatre.makeBooking(selectedShow, seatCodes, coupon);
            booking.printSummary();
        } catch (IllegalArgumentException ex) {
            System.out.println("\n  [!] " + ex.getMessage());
            System.out.println("  Seat map is unchanged. No seats were booked.\n");
        } catch (IllegalStateException ex) {
            System.out.println("\n  [!] Database operation failed: " + ex.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ //
    // Cancellation flow
    // ------------------------------------------------------------------ //
    private static void handleCancellation() {
        theatre.displayActiveBookings();

        System.out.print("  Enter Booking ID to cancel (e.g. CB1001): ");
        String id = scanner.nextLine().trim();

        if (id.isEmpty()) {
            System.out.println("\n  [!] No booking ID entered. Returning to menu.\n");
            return;
        }

        try {
            Booking cancelled = theatre.cancelBooking(id);
            cancelled.printCancellationSummary();
        } catch (IllegalArgumentException ex) {
            System.out.println("\n  [!] " + ex.getMessage() + "\n");
        } catch (IllegalStateException ex) {
            System.out.println("\n  [!] Database operation failed: " + ex.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ //
    // Input helpers
    // ------------------------------------------------------------------ //

    /**
     * Reads a single integer from the console.
     * If the user enters something that is not an integer, returns -1.
     *
     * @param prompt the text to display before waiting for input
     * @return the integer entered, or -1 on invalid input
     */
    private static int readInt(String prompt) {
        System.out.print(prompt);
        try {
            int value = scanner.nextInt();
            scanner.nextLine();  // consume trailing newline
            return value;
        } catch (InputMismatchException e) {
            scanner.nextLine();  // clear bad token
            return -1;
        }
    }
}
