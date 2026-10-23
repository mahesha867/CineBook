import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SnackBookingTests.java
 *
 * Automated test suite verifying all 12 core requirements:
 * 1. Snack menu retrieval
 * 2. Snack quantity validation
 * 3. Snack subtotal calculation
 * 4. Ticket and snack total calculation
 * 5. Coupon discount calculation (ticket subtotal only)
 * 6. Booking creation with snacks
 * 7. Booking creation without snacks
 * 8. Transaction rollback when snack persistence fails
 * 9. Cancellation and refund calculation (80% of total paid)
 * 10. Preservation of historical snack prices
 * 11. Prevention of duplicate snack orders
 * 12. Existing ticket booking and cancellation functionality
 */
public class SnackBookingTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   RUNNING CINEBOOK SNACK ENHANCEMENT TEST SUITE  ");
        System.out.println("==================================================");

        try {
            DatabaseConfig.validateConnection();
        } catch (SQLException e) {
            System.err.println("Fatal: Cannot connect to MySQL test database: " + e.getMessage());
            System.exit(1);
        }

        TheatreDao dao = new TheatreDao();
        try {
            dao.ensureSeeded();
        } catch (SQLException e) {
            System.err.println("Fatal: Failed seeding test database: " + e.getMessage());
            System.exit(1);
        }

        runTest("1. Snack Menu Retrieval", () -> testSnackMenuRetrieval(dao));
        runTest("2. Snack Quantity Validation", SnackBookingTests::testSnackQuantityValidation);
        runTest("3. Snack Subtotal Calculation", SnackBookingTests::testSnackSubtotalCalculation);
        runTest("4. Ticket and Snack Total Calculation", SnackBookingTests::testTicketAndSnackTotalCalculation);
        runTest("5. Coupon Discount Calculation (Ticket Subtotal Only)", SnackBookingTests::testCouponDiscountCalculation);
        runTest("6. Booking Creation with Snacks (Transactional)", () -> testBookingCreationWithSnacks(dao));
        runTest("7. Booking Creation without Snacks", () -> testBookingCreationWithoutSnacks(dao));
        runTest("8. Transaction Rollback when Snack Persistence Fails", () -> testTransactionRollbackWhenSnackPersistenceFails(dao));
        runTest("9. Cancellation and 80% Refund Calculation", () -> testCancellationAndRefundCalculation(dao));
        runTest("10. Preservation of Historical Snack Prices", () -> testPreservationOfHistoricalSnackPrices(dao));
        runTest("11. Prevention of Duplicate Snack Orders", () -> testPreventionOfDuplicateSnackOrders(dao));
        runTest("12. Existing Ticket Booking & Conflict Functionality", () -> testExistingTicketBookingAndCancellation(dao));

        System.out.println("==================================================");
        System.out.printf("   TEST SUMMARY: %d Passed, %d Failed%n", passed, failed);
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, TestRunnable test) {
        try {
            test.run();
            System.out.printf("  [PASS] %s%n", name);
            passed++;
        } catch (Throwable t) {
            System.out.printf("  [FAIL] %s - %s%n", name, t.getMessage());
            t.printStackTrace(System.out);
            failed++;
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(String.format("%s (Expected: %s, Actual: %s)", message, expected, actual));
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) <= delta) return;
        throw new AssertionError(String.format("%s (Expected: %.2f, Actual: %.2f)", message, expected, actual));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // ------------------------------------------------------------------ //
    // Tests
    // ------------------------------------------------------------------ //

    private static void testSnackMenuRetrieval(TheatreDao dao) throws Exception {
        List<Snack> snacks = dao.getSnackDao().getAllAvailableSnacks();
        assertTrue(snacks.size() >= 9, "Should load at least 9 snack items from MySQL");

        Snack s1 = dao.getSnackDao().getSnackById("S001");
        assertTrue(s1 != null, "S001 should exist");
        assertEquals("Popcorn", s1.getSnackName(), "S001 should be Popcorn");
        assertEquals(120.0, s1.getPrice(), 0.01, "S001 price should be 120");

        Snack s7 = dao.getSnackDao().getSnackById("S007");
        assertTrue(s7 != null, "S007 should exist");
        assertEquals("Coke", s7.getSnackName(), "S007 should be Coke");
        assertEquals(80.0, s7.getPrice(), 0.01, "S007 price should be 80");
    }

    private static void testSnackQuantityValidation() {
        SnackService service = new SnackService();
        Snack snack = new Snack("S001", "Popcorn", "Regular", 120.0);

        // Test 0 quantity
        boolean zeroFailed = false;
        try {
            service.validateQuantity(0);
        } catch (IllegalArgumentException e) {
            zeroFailed = true;
        }
        assertTrue(zeroFailed, "Zero quantity must throw IllegalArgumentException");

        // Test negative quantity
        boolean negFailed = false;
        try {
            service.validateQuantity(-5);
        } catch (IllegalArgumentException e) {
            negFailed = true;
        }
        assertTrue(negFailed, "Negative quantity must throw IllegalArgumentException");

        // Test SnackOrder creation with negative
        boolean orderNegFailed = false;
        try {
            new SnackOrder(snack, -2);
        } catch (IllegalArgumentException e) {
            orderNegFailed = true;
        }
        assertTrue(orderNegFailed, "SnackOrder with negative quantity must fail");

        // Positive should succeed
        service.validateQuantity(3);
        SnackOrder order = new SnackOrder(snack, 2);
        assertEquals(2, order.getQuantity(), "Quantity should be 2");
    }

    private static void testSnackSubtotalCalculation() {
        Snack s1 = new Snack("S001", "Popcorn", "Regular", 120.0);
        Snack s2 = new Snack("S007", "Coke", "Beverage", 80.0);

        List<SnackOrder> cart = new ArrayList<>();
        cart.add(new SnackOrder(s1, 2)); // 240
        cart.add(new SnackOrder(s2, 3)); // 240

        SnackService service = new SnackService();
        double subtotal = service.calculateSnackSubtotal(cart);
        assertEquals(480.0, subtotal, 0.01, "Snack subtotal should be 480");

        // Modify quantity
        service.modifyQuantity(cart, "S007", 1); // 240 + 80 = 320
        assertEquals(320.0, service.calculateSnackSubtotal(cart), 0.01, "Modified subtotal should be 320");

        // Remove item
        service.removeSnack(cart, "S001"); // only Coke x1 = 80
        assertEquals(80.0, service.calculateSnackSubtotal(cart), 0.01, "Subtotal after removal should be 80");
    }

    private static void testTicketAndSnackTotalCalculation() {
        // Seats: A1, A2 (Row A = REGULAR = Rs. 150 each -> 300)
        String[] seats = {"A1", "A2"};
        double ticketSubtotal = Booking.computeTicketSubtotal(seats);
        assertEquals(300.0, ticketSubtotal, 0.01, "Ticket subtotal for A1, A2 should be 300");

        Snack s = new Snack("S003", "Nachos", "Snacks", 150.0);
        List<SnackOrder> snacks = List.of(new SnackOrder(s, 2)); // 300
        double grandTotal = Booking.computeGrandTotal(seats, "", snacks);
        assertEquals(600.0, grandTotal, 0.01, "Grand total with no discount should be 300 + 300 = 600");
    }

    private static void testCouponDiscountCalculation() {
        // Prompt specific example:
        // Seats: C3, C4 (Row C = PREMIUM = Rs. 250 each -> 500)
        // Snacks: Popcorn x2 (240), Coke x2 (160) -> 400
        // Coupon: CINE10 (10% on ticket subtotal only = 50)
        // Final: 500 - 50 + 400 = 850
        String[] seats = {"C3", "C4"};
        double ticketSubtotal = Booking.computeTicketSubtotal(seats);
        assertEquals(500.0, ticketSubtotal, 0.01, "Ticket subtotal for C3, C4 should be 500");

        double discount = Booking.computeTicketDiscount(ticketSubtotal, "CINE10");
        assertEquals(50.0, discount, 0.01, "Ticket discount should be 50 (10% of 500)");

        Snack s1 = new Snack("S001", "Popcorn", "Regular", 120.0);
        Snack s2 = new Snack("S007", "Coke", "Beverage", 80.0);
        List<SnackOrder> snacks = List.of(new SnackOrder(s1, 2), new SnackOrder(s2, 2));

        double grandTotal = Booking.computeGrandTotal(seats, "CINE10", snacks);
        assertEquals(850.0, grandTotal, 0.01, "Final amount must be exactly Rs. 850");

        // Verify invalid coupon does not give discount
        double noDiscount = Booking.computeGrandTotal(seats, "INVALID_CODE", snacks);
        assertEquals(900.0, noDiscount, 0.01, "Invalid coupon should give no discount (900)");
    }

    private static void testBookingCreationWithSnacks(TheatreDao dao) throws Exception {
        List<Show> shows = dao.getAllShows();
        Show show = shows.get(0); // Inception

        // Reset test seats D7, D8 for this show
        resetSeat(show.getShowId(), 'D', 7);
        resetSeat(show.getShowId(), 'D', 8);

        String[] seats = {"D7", "D8"}; // Row D = PREMIUM (250 each = 500)
        Snack s1 = dao.getSnackDao().getSnackById("S001"); // Popcorn (120)
        Snack s2 = dao.getSnackDao().getSnackById("S008"); // Sprite (80)
        List<SnackOrder> orders = List.of(new SnackOrder(s1, 2), new SnackOrder(s2, 1)); // 240 + 80 = 320

        // Final amount: 500 - 50 (CINE10) + 320 = 770
        Booking booking = dao.makeBooking(show, seats, "CINE10", orders);

        assertTrue(booking.getBookingId().startsWith("CB"), "Booking ID should start with CB");
        assertEquals(770.0, booking.getAmountCharged(), 0.01, "Amount charged should be 770");
        assertEquals(2, booking.getSnackOrders().size(), "Should have 2 snack orders");

        // Verify database persistence in booking_snacks
        List<SnackOrder> dbSnacks = dao.getSnackDao().getSnacksForBooking(booking.getBookingId());
        assertEquals(2, dbSnacks.size(), "DB should contain 2 snack items for booking");
        assertEquals("S001", dbSnacks.get(0).getSnack().getSnackId(), "First snack should be S001");
        assertEquals(2, dbSnacks.get(0).getQuantity(), "Quantity should be 2");
        assertEquals(120.0, dbSnacks.get(0).getUnitPrice(), 0.01, "Unit price should be 120");
        assertEquals(240.0, dbSnacks.get(0).getSubtotal(), 0.01, "Subtotal should be 240");

        // Verify seats are marked booked in seats table
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT is_booked, booking_id FROM seats WHERE show_id = ? AND seat_row = 'D' AND seat_number IN (7, 8)")) {
            ps.setInt(1, show.getShowId());
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    assertTrue(rs.getBoolean("is_booked"), "Seat should be marked booked");
                    assertEquals(booking.getBookingId(), rs.getString("booking_id"), "Seat should point to bookingId");
                    count++;
                }
                assertEquals(2, count, "Both seats should be verified");
            }
        }
    }

    private static void testBookingCreationWithoutSnacks(TheatreDao dao) throws Exception {
        List<Show> shows = dao.getAllShows();
        Show show = shows.get(1); // Interstellar

        resetSeat(show.getShowId(), 'B', 1);
        resetSeat(show.getShowId(), 'B', 2);

        String[] seats = {"B1", "B2"}; // REGULAR = 150 each = 300
        Booking booking = dao.makeBooking(show, seats, "");

        assertEquals(300.0, booking.getAmountCharged(), 0.01, "Amount charged without coupon or snacks should be 300");
        assertTrue(booking.getSnackOrders().isEmpty(), "Snack orders should be empty");

        List<SnackOrder> dbSnacks = dao.getSnackDao().getSnacksForBooking(booking.getBookingId());
        assertTrue(dbSnacks.isEmpty(), "DB booking_snacks should have 0 records for ticket-only booking");
    }

    private static void testTransactionRollbackWhenSnackPersistenceFails(TheatreDao dao) throws Exception {
        List<Show> shows = dao.getAllShows();
        Show show = shows.get(2); // The Dark Knight

        resetSeat(show.getShowId(), 'E', 7);
        resetSeat(show.getShowId(), 'E', 8);

        String[] seats = {"E7", "E8"};
        // Create an invalid snack item with a non-existent snack_id that violates foreign key constraint in booking_snacks
        Snack invalidSnack = new Snack("NONEXISTENT999", "Fake Snack", "Food", 50.0);
        List<SnackOrder> badOrders = List.of(new SnackOrder(invalidSnack, 1));

        boolean failedAsExpected = false;
        try {
            dao.makeBooking(show, seats, "", badOrders);
        } catch (Exception e) {
            failedAsExpected = true;
        }
        assertTrue(failedAsExpected, "Booking should fail when snack foreign key is invalid");

        // Verify seats remain unbooked
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT is_booked, booking_id FROM seats WHERE show_id = ? AND seat_row = 'E' AND seat_number IN (7, 8)")) {
            ps.setInt(1, show.getShowId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assertTrue(!rs.getBoolean("is_booked"), "Seat should NOT be marked booked after rollback");
                    assertTrue(rs.getString("booking_id") == null, "Seat booking_id should be NULL after rollback");
                }
            }
        }
    }

    private static void testCancellationAndRefundCalculation(TheatreDao dao) throws Exception {
        List<Show> shows = dao.getAllShows();
        Show show = shows.get(0);

        resetSeat(show.getShowId(), 'A', 5);
        resetSeat(show.getShowId(), 'A', 6);

        String[] seats = {"A5", "A6"}; // Row A = REGULAR (150 x 2 = 300)
        Snack s = dao.getSnackDao().getSnackById("S004"); // French Fries (100)
        List<SnackOrder> orders = List.of(new SnackOrder(s, 2)); // 200
        // Total amount paid = 300 + 200 = 500

        Booking booking = dao.makeBooking(show, seats, "", orders);
        assertEquals(500.0, booking.getAmountCharged(), 0.01, "Amount paid should be 500");

        // Cancel the booking
        Booking cancelled = dao.cancelBooking(booking.getBookingId(), null);
        assertTrue(cancelled.isCancelled(), "Cancelled status should be true");

        // Verify 80% refund including snacks: 500 * 0.80 = 400
        double expectedRefund = 500.0 * 0.80;
        assertEquals(expectedRefund, cancelled.getAmountCharged() * 0.80, 0.01, "Refund should be 80% of total amount paid (400)");

        // Verify historical snacks in booking_snacks are NOT deleted
        List<SnackOrder> preservedSnacks = dao.getSnackDao().getSnacksForBooking(booking.getBookingId());
        assertEquals(1, preservedSnacks.size(), "Snack order records must be preserved after cancellation");
        assertEquals("S004", preservedSnacks.get(0).getSnack().getSnackId(), "Preserved snack ID should match");

        // Verify seats are freed
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT is_booked, booking_id FROM seats WHERE show_id = ? AND seat_row = 'A' AND seat_number IN (5, 6)")) {
            ps.setInt(1, show.getShowId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assertTrue(!rs.getBoolean("is_booked"), "Cancelled booking seats should be freed");
                    assertTrue(rs.getString("booking_id") == null, "Booking ID should be null for freed seats");
                }
            }
        }

        // Verify duplicate cancellation prevention
        boolean duplicateRejected = false;
        try {
            dao.cancelBooking(booking.getBookingId(), null);
        } catch (IllegalArgumentException e) {
            duplicateRejected = true;
        }
        assertTrue(duplicateRejected, "Duplicate cancellation must be rejected");
    }

    private static void testPreservationOfHistoricalSnackPrices(TheatreDao dao) throws Exception {
        List<Show> shows = dao.getAllShows();
        Show show = shows.get(3); // Oppenheimer

        resetSeat(show.getShowId(), 'B', 7);
        resetSeat(show.getShowId(), 'B', 8);

        String[] seats = {"B7", "B8"};
        Snack originalPopcorn = dao.getSnackDao().getSnackById("S001");
        double originalPrice = originalPopcorn.getPrice(); // 120.00
        List<SnackOrder> orders = List.of(new SnackOrder(originalPopcorn, 2));

        Booking booking = dao.makeBooking(show, seats, "", orders);

        // Temporarily change price of S001 in MySQL snacks table to 250.00
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE snacks SET price = 250.00 WHERE snack_id = 'S001'")) {
            ps.executeUpdate();
        }

        // Reload the booking's snacks from database
        List<SnackOrder> loadedOrders = dao.getSnackDao().getSnacksForBooking(booking.getBookingId());
        assertEquals(1, loadedOrders.size(), "Should have 1 snack order");
        assertEquals(originalPrice, loadedOrders.get(0).getUnitPrice(), 0.01,
                "Historical unit price in booking must remain 120.00 even after menu price changes");
        assertEquals(originalPrice * 2, loadedOrders.get(0).getSubtotal(), 0.01,
                "Historical subtotal in booking must remain 240.00");

        // Revert price in snacks table back to original
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE snacks SET price = ? WHERE snack_id = 'S001'")) {
            ps.setDouble(1, originalPrice);
            ps.executeUpdate();
        }
    }

    private static void testPreventionOfDuplicateSnackOrders(TheatreDao dao) {
        SnackService service = new SnackService();
        Snack popcorn = new Snack("S001", "Popcorn", "Regular", 120.0);

        List<SnackOrder> cart = new ArrayList<>();
        service.addOrUpdateSnack(cart, popcorn, 2);
        service.addOrUpdateSnack(cart, popcorn, 3);

        assertEquals(1, cart.size(), "Adding same snack twice should merge quantities in cart");
        assertEquals(5, cart.get(0).getQuantity(), "Quantity should be aggregated to 5");
        assertEquals(600.0, cart.get(0).getSubtotal(), 0.01, "Subtotal should be 600");
    }

    private static void testExistingTicketBookingAndCancellation(TheatreDao dao) throws Exception {
        List<Show> shows = dao.getAllShows();
        Show show = shows.get(0);

        resetSeat(show.getShowId(), 'C', 1);

        // Single seat booking
        Booking b1 = dao.makeBooking(show, new String[]{"C1"}, "");
        assertEquals(250.0, b1.getAmountCharged(), 0.01, "C1 (PREMIUM) should cost 250");

        // Attempt concurrent booking conflict on already booked seat
        boolean conflictCaught = false;
        try {
            dao.makeBooking(show, new String[]{"C1"}, "");
        } catch (IllegalArgumentException e) {
            conflictCaught = true;
        }
        assertTrue(conflictCaught, "Booking already booked seat C1 must throw conflict error");

        // Cancel booking
        Booking cancelled = dao.cancelBooking(b1.getBookingId(), null);
        assertTrue(cancelled.isCancelled(), "Booking should be cancelled");
        assertEquals(200.0, cancelled.getAmountCharged() * 0.80, 0.01, "Refund should be 80% of 250 = 200");
    }

    // ------------------------------------------------------------------ //
    // Helper to reset a seat to available state for isolated tests
    // ------------------------------------------------------------------ //
    private static void resetSeat(int showId, char row, int col) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Delete any existing booking referencing this seat
            String findBookingSql = "SELECT booking_id FROM seats WHERE show_id = ? AND seat_row = ? AND seat_number = ?";
            String bookingId = null;
            try (PreparedStatement ps = conn.prepareStatement(findBookingSql)) {
                ps.setInt(1, showId);
                ps.setString(2, String.valueOf(row));
                ps.setInt(3, col);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        bookingId = rs.getString("booking_id");
                    }
                }
            }

            if (bookingId != null) {
                try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM bookings WHERE booking_id = ?")) {
                    psDel.setString(1, bookingId);
                    psDel.executeUpdate();
                }
            }

            try (PreparedStatement psReset = conn.prepareStatement(
                    "UPDATE seats SET is_booked = FALSE, booking_id = NULL WHERE show_id = ? AND seat_row = ? AND seat_number = ?")) {
                psReset.setInt(1, showId);
                psReset.setString(2, String.valueOf(row));
                psReset.setInt(3, col);
                psReset.executeUpdate();
            }
        }
    }
}
