# 🎬 CineBook

A Java console application for booking movie tickets at a single-screen cinema. Built as an OOP fundamentals exercise covering enums, interfaces, 2D arrays, method overloading, and ArrayList usage.

---

## Features

- 🎥 **4 available shows** — choose from Inception, Interstellar, The Dark Knight, and Oppenheimer
- 🪑 **Interactive seat map** — 5×8 grid (rows A–E, columns 1–8) with live `O`/`X` status
- 💰 **Three seat categories** — prices read from a `SeatType` enum (never hard-coded)
- 🔒 **All-or-nothing booking** — every seat in a request is validated before any seat is reserved
- 🎟️ **Coupon support** — `CINE10` applies a 10% discount via overloaded `calculateTotal()`
- ❌ **Cancellation with 80% refund** — frees the seats and prevents duplicate cancellations
- 🛡️ **Graceful error handling** — bad menu choices, invalid codes, and already-booked seats all produce clear messages without crashing

---

## Seat Pricing

| Rows | Category | Price      |
|------|----------|------------|
| A–B  | REGULAR  | Rs. 150    |
| C–D  | PREMIUM  | Rs. 250    |
| E    | VIP      | Rs. 400    |

---

## Project Structure

```
CineBook/
├── src/
│   ├── SeatType.java     # Enum: REGULAR, PREMIUM, VIP with prices
│   ├── Bookable.java     # Interface: bookSeats(), cancelBooking()
│   ├── Show.java         # Entity: 2D seat grid, seat-map display, all-or-nothing booking
│   ├── Booking.java      # Record: booking ID, amount, coupon, cancellation state
│   ├── Theatre.java      # Aggregator: show list, ArrayList<Booking>, orchestration
│   └── CineApp.java      # Entry point: main menu, console I/O
├── out/                  # Compiled .class files (generated)
└── README.md
```

---

## Class Responsibilities

### `SeatType` (enum)
Defines the three seat categories. Each constant carries its own `price` field and a `getPrice()` getter. The static `fromRow(char)` factory maps a row letter (A–E) to the correct category. **All booking price calculations read from this enum.**

### `Bookable` (interface)
Declares two operations that any bookable entity must implement:
- `bookSeats(String[])` — validate and commit seats; returns normalised codes
- `cancelBooking(Booking)` — free seats associated with a booking

### `Show` (implements Bookable)
Stores the movie name, show time, and a `char[5][8]` seat grid (`'O'` = available, `'X'` = booked). Implements the two-phase all-or-nothing booking algorithm and provides a formatted `displaySeatMap()` rendered with nested loops.

### `Booking`
Auto-generates a unique ID via a static counter (`CB1001`, `CB1002`, …). Stores the show, seat codes, amount charged, coupon applied, and cancellation status. Overloads `calculateTotal()`:
- `calculateTotal()` — full price, no discount
- `calculateTotal(String coupon)` — 10% off when coupon is `CINE10`; invalid coupons are silently ignored

### `Theatre`
Seeds four shows at start-up. Owns an `ArrayList<Booking>`. `makeBooking()` delegates grid changes to the `Show` and then creates exactly one `Booking` (so failed attempts never waste an ID). `cancelBooking()` enforces the one-cancel rule and delegates seat-freeing to the `Show`.

### `CineApp`
Contains `main()`, the main-menu loop, and all console I/O. A `readInt()` helper catches `InputMismatchException` so non-numeric input never crashes the app.

---

## All-or-Nothing Booking — How It Works

`Show.bookSeats()` executes in two strict phases:

```
Phase 1 – Validate (read-only, no grid changes)
  ├─ Parse each seat code (must be exactly 2 chars, row A-E, col 1-8)
  └─ Check each seat is 'O' (available)
  → If ANY check fails, throw with a full error list.

Phase 2 – Commit (only if Phase 1 had zero errors)
  └─ Mark every seat 'X'
  → Return normalised codes; Theatre creates one Booking.
```

**Example:** entering `C3,Z9` — `C3` is valid but `Z9` has an invalid row. Phase 1 catches `Z9` and throws before a single seat is marked. The grid is left unchanged and the user sees a precise error.

---

## Sample Interaction

```
*** BOOKING — C3,C4 with coupon CINE10 ***

  Booking ID   : CB1001
  Movie        : Inception
  Show Time    : 10:30 AM
  Seats        : C3, C4
  Coupon       : CINE10 (10% discount applied)
  Amount Paid  : Rs. 450       ← 2×250=500, −10%=450

*** INVALID SEAT — Z9 ***

  [!] Booking failed. Please fix the following issues:
    - Row 'Z' is invalid. Valid rows are A to E.
  Seat map is unchanged. No seats were booked.

*** CANCELLATION — CB1001 ***

  Booking ID   : CB1001
  Seats freed  : C3, C4
  Amount Paid  : Rs. 450
  Refund (80%) : Rs. 360       ← 450 × 0.80 = 360

*** DUPLICATE CANCEL — CB1001 ***

  [!] Booking CB1001 has already been cancelled.
```

---

## How to Compile and Run

**Requirements:** JDK 8 or later

```powershell
# Compile (from the CineBook root)
javac -d out src\SeatType.java src\Bookable.java src\Booking.java src\Show.java src\Theatre.java src\CineApp.java

# Run
java -cp out CineApp
```

---

## OOP Concepts Demonstrated

| Concept | Where used |
|---|---|
| Enum with fields & methods | `SeatType` |
| Interface | `Bookable` |
| Inheritance / polymorphism | `Show implements Bookable` |
| Method overloading | `Booking.calculateTotal()` |
| 2D array | `char[5][8]` seat grid in `Show` |
| Static counter | Booking ID generation in `Booking` |
| ArrayList | Booking storage in `Theatre` |
| Exception handling | `CineApp.readInt()`, validation in `Show` |
| Nested loops | Seat-map rendering in `Show.displaySeatMap()` |
| Formatted output | `printf` throughout |

---

## License

This project is for educational purposes.
