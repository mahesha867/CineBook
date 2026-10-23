import java.sql.SQLException;
import java.util.ArrayList;
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
 *   - Coordinate Theatre, Show, Booking, and Movie Snacks interactions.
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

        // Calculate and preview ticket subtotal
        double ticketSubtotal = Booking.computeTicketSubtotal(seatCodes);
        System.out.printf("%n  Ticket Subtotal: Rs. %.0f%n%n", ticketSubtotal);

        // Step 6 & 7 – Optional movie snacks ordering
        List<SnackOrder> snackOrders = new ArrayList<>();
        System.out.print("  Would you like to order snacks? (Y/N): ");
        String snackChoice = scanner.nextLine().trim();

        if (snackChoice.equalsIgnoreCase("Y")) {
            handleSnackOrdering(snackOrders);
        }

        // Step 8 – Optional coupon
        System.out.printf("%n  Enter coupon code (or press Enter to skip): ");
        String coupon = scanner.nextLine().trim();

        // Step 9, 10, 11 – Attempt booking (all-or-nothing database transaction)
        try {
            Booking booking = theatre.makeBooking(selectedShow, seatCodes, coupon, snackOrders);
            booking.printSummary();
        } catch (IllegalArgumentException ex) {
            System.out.println("\n  [!] " + ex.getMessage());
            System.out.println("  Seat map is unchanged. No seats were booked.\n");
        } catch (IllegalStateException ex) {
            System.out.println("\n  [!] Database operation failed: " + ex.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ //
    // Snack Ordering flow
    // ------------------------------------------------------------------ //
    private static void handleSnackOrdering(List<SnackOrder> snackCart) {
        List<Snack> availableSnacks;
        try {
            availableSnacks = theatre.getAvailableSnacks();
        } catch (SQLException ex) {
            System.out.println("\n  [!] Unable to load snack menu from database: " + ex.getMessage());
            System.out.println("  Continuing with ticket-only booking.\n");
            return;
        }

        if (availableSnacks.isEmpty()) {
            System.out.println("\n  [!] No snacks are currently available.\n");
            return;
        }

        displaySnackMenu(availableSnacks);
        SnackService snackService = theatre.getSnackService();

        boolean adding = true;
        while (adding) {
            System.out.print("  Enter snack ID: ");
            String snackId = scanner.nextLine().trim().toUpperCase();

            Snack selectedSnack = findSnack(availableSnacks, snackId);
            if (selectedSnack == null) {
                System.out.printf("  [!] Invalid snack ID '%s'. Please select a valid ID from the menu.%n%n", snackId);
                continue;
            }

            int qty = readPositiveInt("  Enter quantity: ");
            snackService.addOrUpdateSnack(snackCart, selectedSnack, qty);
            System.out.println("\n  Snack added successfully!\n");

            System.out.print("  Would you like to add another snack? (Y/N): ");
            String more = scanner.nextLine().trim();
            if (!more.equalsIgnoreCase("Y")) {
                adding = false;
            }
        }

        // Review, modify, or remove items option
        if (!snackCart.isEmpty()) {
            manageSnackCart(snackCart, availableSnacks, snackService);
        }
    }

    private static void displaySnackMenu(List<Snack> snacks) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("               CINEBOOK SNACK MENU");
        System.out.println("=".repeat(50));
        System.out.printf("%-7s %-25s %s%n", "ID", "SNACK NAME", "PRICE");
        System.out.println("-".repeat(50));
        for (Snack s : snacks) {
            System.out.printf("%-7s %-25s Rs. %.0f%n", s.getSnackId(), s.getSnackName(), s.getPrice());
        }
        System.out.println("=".repeat(50) + "\n");
    }

    private static Snack findSnack(List<Snack> snacks, String snackId) {
        if (snackId == null) return null;
        for (Snack s : snacks) {
            if (s.getSnackId().equalsIgnoreCase(snackId.trim())) {
                return s;
            }
        }
        return null;
    }

    private static void manageSnackCart(List<SnackOrder> cart, List<Snack> availableSnacks, SnackService snackService) {
        System.out.print("\n  Would you like to review, modify, or remove any snacks? (Y/N): ");
        String answer = scanner.nextLine().trim();
        if (!answer.equalsIgnoreCase("Y")) {
            return;
        }

        boolean managing = true;
        while (managing && !cart.isEmpty()) {
            System.out.println("\n" + "-".repeat(40));
            System.out.println("        SNACK ORDER MANAGEMENT");
            System.out.println("-".repeat(40));
            System.out.println("  [1] Review Selected Snacks");
            System.out.println("  [2] Modify Snack Quantity");
            System.out.println("  [3] Remove a Snack");
            System.out.println("  [4] Add Another Snack");
            System.out.println("  [5] Done with Snacks");
            System.out.println("-".repeat(40));

            int choice = readInt("  Enter your choice (1-5): ");
            switch (choice) {
                case 1:
                    printCartSummary(cart, snackService);
                    break;
                case 2:
                    printCartSummary(cart, snackService);
                    System.out.print("  Enter snack ID to modify: ");
                    String modId = scanner.nextLine().trim().toUpperCase();
                    SnackOrder existing = snackService.findInCart(cart, modId);
                    if (existing == null) {
                        System.out.println("  [!] That snack is not currently in your cart.");
                    } else {
                        int newQty = readPositiveInt("  Enter new quantity: ");
                        snackService.modifyQuantity(cart, modId, newQty);
                        System.out.println("  [✓] Quantity updated successfully!");
                    }
                    break;
                case 3:
                    printCartSummary(cart, snackService);
                    System.out.print("  Enter snack ID to remove: ");
                    String remId = scanner.nextLine().trim().toUpperCase();
                    if (snackService.removeSnack(cart, remId)) {
                        System.out.println("  [✓] Snack removed from cart.");
                    } else {
                        System.out.println("  [!] That snack is not currently in your cart.");
                    }
                    break;
                case 4:
                    displaySnackMenu(availableSnacks);
                    System.out.print("  Enter snack ID: ");
                    String addId = scanner.nextLine().trim().toUpperCase();
                    Snack snack = findSnack(availableSnacks, addId);
                    if (snack == null) {
                        System.out.println("  [!] Invalid snack ID.");
                    } else {
                        int addQty = readPositiveInt("  Enter quantity: ");
                        snackService.addOrUpdateSnack(cart, snack, addQty);
                        System.out.println("  [✓] Snack added to cart!");
                    }
                    break;
                case 5:
                    managing = false;
                    break;
                default:
                    System.out.println("  [!] Invalid choice. Please choose 1 to 5.");
            }
        }

        if (cart.isEmpty()) {
            System.out.println("\n  All snacks were removed. Continuing with ticket-only booking.");
        }
    }

    private static void printCartSummary(List<SnackOrder> cart, SnackService snackService) {
        System.out.println("\n  Current Snack Cart:");
        if (cart.isEmpty()) {
            System.out.println("    (Cart is empty)");
            return;
        }
        for (SnackOrder order : cart) {
            System.out.printf("    - %s%n", order.getFormattedSummary());
        }
        System.out.printf("  Snack Subtotal: Rs. %.0f%n", snackService.calculateSnackSubtotal(cart));
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

    /**
     * Reads a strictly positive integer from the console with retry on bad input.
     */
    private static int readPositiveInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value > 0) {
                    return value;
                }
                System.out.println("  [!] Quantity must be greater than 0. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid number. Please enter a positive integer.");
            }
        }
    }
}
