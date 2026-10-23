# 🎬 CineBook

A Java console application for booking movie tickets and movie snacks at a single-screen cinema, backed by a persistent MySQL relational database. Built following OOP and Data Access Object (DAO) principles, covering JDBC transactions, row-level locking (`SELECT ... FOR UPDATE`), collision-safe ID generation, enums, interfaces, and graceful error handling.

---

## Features

- 🎥 **4 available shows** — Inception, Interstellar, The Dark Knight, and Oppenheimer persisted in MySQL.
- 🪑 **Interactive seat map** — 5×8 grid (rows A–E, columns 1–8) with live `O` (available) and `X` (booked) status loaded from MySQL.
- 💰 **Three seat categories** — prices derived from the `SeatType` enum (never stored in the database).
- 🍿 **Movie Snacks & Beverages Booking** — order snacks alongside movie tickets; dynamic menu loaded from MySQL with support for multiple items, quantities, and cart review/modification/removal.
- 🔒 **ACID database transactions** — ticket booking, seat reservation, and snack orders are committed atomically in a single logical transaction; failures roll back completely.
- 🛡️ **Concurrency-safe seat reservation** — uses row-level locking (`SELECT ... FOR UPDATE`) sorted deterministically to prevent duplicate bookings and deadlocks.
- 🎟️ **Coupon support** — `CINE10` applies a 10% discount on the **ticket subtotal only** (snacks do not receive the ticket discount).
- ❌ **Transactional cancellation with 80% refund** — frees reserved seats, marks the booking cancelled, preserves historical snack order details, and calculates an 80% refund of the total amount paid (including snacks).
- 🔑 **Collision-safe booking IDs** — database-generated sequence preserving the `CB1001` display format without relying on in-memory counters.
- ⚡ **Idempotent seeding** — rerunning the application or schema script initializes missing shows, seats, and snack menu items without duplicates.
- 🛑 **Fail-fast connectivity** — clear diagnostic console errors if the database is unavailable or misconfigured, preventing silent fallback to memory-only state.

---

## Pricing Rules

### 1. Seat Pricing
Prices are determined solely by the `SeatType` Java enum:

| Rows | Category | Price   |
|------|----------|---------|
| A–B  | REGULAR  | Rs. 150 |
| C–D  | PREMIUM  | Rs. 250 |
| E    | VIP      | Rs. 400 |

### 2. Snack Menu
Snack items and prices are loaded dynamically from MySQL (`snacks` table):

| Item ID | Snack Name | Category | Price |
|---|---|---|---:|
| S001 | Popcorn | Regular | Rs. 120 |
| S002 | Cheese Popcorn | Premium | Rs. 180 |
| S003 | Nachos | Snacks | Rs. 150 |
| S004 | French Fries | Snacks | Rs. 100 |
| S005 | Veg Sandwich | Food | Rs. 130 |
| S006 | Chicken Sandwich | Food | Rs. 170 |
| S007 | Coke | Beverage | Rs. 80 |
| S008 | Sprite | Beverage | Rs. 80 |
| S009 | Mineral Water | Beverage | Rs. 40 |

### 3. Final Amount Calculation
$$\text{Final Amount} = \text{Ticket Subtotal} + \text{Snack Subtotal} - \text{Applicable Discount}$$

- **Coupon Discount**: `CINE10` provides a 10% discount applied strictly to the **ticket subtotal**. Snacks do not receive any coupon discount.
- **Cancellation Refund**: `Refund (80%) = 0.80 * Amount Paid` (where amount paid includes both tickets and snacks).

---

## Database Architecture

### Schema: `cinebook`

The database consists of 6 tables plus a sequence generator:

1. **`shows`**
   - `show_id` (INT AUTO_INCREMENT PRIMARY KEY)
   - `movie_name` (VARCHAR(100) NOT NULL)
   - `show_time` (VARCHAR(20) NOT NULL)
   - `UNIQUE KEY uk_show_movie_time (movie_name, show_time)`

2. **`booking_seq`**
   - `id` (INT AUTO_INCREMENT PRIMARY KEY, AUTO_INCREMENT = 1001)
   - Used to generate collision-safe IDs formatted as `CB1001`, `CB1002`, etc.

3. **`bookings`**
   - `booking_id` (VARCHAR(20) PRIMARY KEY)
   - `show_id` (INT NOT NULL, FK -> `shows.show_id`)
   - `amount_paid` (DECIMAL(10, 2) NOT NULL)
   - `coupon` (VARCHAR(50) DEFAULT NULL)
   - `is_cancelled` (BOOLEAN NOT NULL DEFAULT FALSE)
   - `created_at` (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)

4. **`seats`**
   - `seat_id` (INT AUTO_INCREMENT PRIMARY KEY)
   - `show_id` (INT NOT NULL, FK -> `shows.show_id`)
   - `seat_row` (CHAR(1) NOT NULL)
   - `seat_number` (INT NOT NULL)
   - `is_booked` (BOOLEAN NOT NULL DEFAULT FALSE)
   - `booking_id` (VARCHAR(20) DEFAULT NULL, FK -> `bookings.booking_id`)
   - `UNIQUE KEY uk_show_seat (show_id, seat_row, seat_number)`

5. **`booking_seats`**
   - `id` (INT AUTO_INCREMENT PRIMARY KEY)
   - `booking_id` (VARCHAR(20) NOT NULL, FK -> `bookings.booking_id`)
   - `seat_row` (CHAR(1) NOT NULL)
   - `seat_number` (INT NOT NULL)
   - `UNIQUE KEY uk_booking_seat (booking_id, seat_row, seat_number)`

6. **`snacks`**
   - `snack_id` (VARCHAR(10) PRIMARY KEY)
   - `snack_name` (VARCHAR(100) NOT NULL)
   - `category` (VARCHAR(50) NOT NULL)
   - `price` (DECIMAL(10, 2) NOT NULL)
   - `is_available` (BOOLEAN NOT NULL DEFAULT TRUE)

7. **`booking_snacks`**
   - `id` (INT AUTO_INCREMENT PRIMARY KEY)
   - `booking_id` (VARCHAR(20) NOT NULL, FK -> `bookings.booking_id`)
   - `snack_id` (VARCHAR(10) NOT NULL, FK -> `snacks.snack_id`)
   - `quantity` (INT NOT NULL, CHECK quantity > 0)
   - `unit_price` (DECIMAL(10, 2) NOT NULL) — preserves price at time of booking
   - `subtotal` (DECIMAL(10, 2) NOT NULL)
   - `UNIQUE KEY uk_booking_snack (booking_id, snack_id)`

---

## Project Structure

```
CineBook/
├── database/
│   └── schema.sql             # MySQL schema and idempotent seed script (shows, seats, snacks)
├── lib/
│   └── mysql-connector-j-8.3.0.jar # MySQL Connector/J JDBC driver
├── out/                       # Compiled .class files (generated)
├── src/
│   ├── DatabaseConfig.java    # JDBC connection manager & env variable configuration
│   ├── TheatreDao.java        # DAO layer: SQL queries, transactions, row locking, booking persistence
│   ├── SnackDao.java          # Snack DAO: menu retrieval, table seeding, booking snacks persistence
│   ├── SnackService.java      # Snack business logic: validation, cart operations, subtotal calculation
│   ├── SeatType.java          # Enum: REGULAR, PREMIUM, VIP with ticket pricing logic
│   ├── Bookable.java          # Interface: bookSeats(), cancelBooking()
│   ├── Show.java              # Entity: seat grid state, seat map rendering
│   ├── Snack.java             # Entity: snack menu item
│   ├── SnackOrder.java        # Value object: snack item, quantity, unit price, subtotal
│   ├── Booking.java           # Record: booking details, ticket & snack subtotals, summaries
│   ├── Theatre.java           # Domain service: orchestration, shows, active bookings, snacks
│   ├── CineApp.java           # Entry point: console menu loop, snack ordering flow, fail-fast checks
│   └── SnackBookingTests.java # Automated test suite covering 12 core requirements
└── README.md
```

---

## Configuration & Environment Variables

`DatabaseConfig` reads database credentials from environment variables. If not set, it defaults to standard local development values:

| Variable | Description | Default Value |
|---|---|---|
| `DB_URL` | JDBC Connection URL | `jdbc:mysql://localhost:3306/cinebook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USER` | MySQL Username | `root` |
| `DB_PASSWORD` | MySQL Password | `root` |

### Setting Environment Variables (Optional)

**PowerShell (Windows):**
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/cinebook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER="root"
$env:DB_PASSWORD="your_mysql_password"
```

**Bash (Linux / macOS):**
```bash
export DB_URL="jdbc:mysql://localhost:3306/cinebook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USER="root"
export DB_PASSWORD="your_mysql_password"
```

---

## Setup & Initialization

### 1. Requirements
- **JDK 17 or later** (Java 21 supported)
- **MySQL Server 8.0 or later** running locally or remotely

### 2. Initialize Database & Seed Data
Run `database/schema.sql` using the MySQL command-line client:

```powershell
# From the CineBook root directory:
Get-Content database\schema.sql | mysql -u root -p
```
*(Or in bash: `mysql -u root -p < database/schema.sql`)*

This script:
1. Creates the `cinebook` database if it doesn't exist.
2. Creates all tables (`shows`, `booking_seq`, `bookings`, `seats`, `booking_seats`, `snacks`, `booking_snacks`).
3. Seeds the 4 initial shows (`Inception`, `Interstellar`, `The Dark Knight`, `Oppenheimer`).
4. Seeds 40 seats (Rows A–E, Columns 1–8) for each show.
5. Seeds the 9 initial snack menu items (`S001`–`S009`).

*(Note: The Java application also includes idempotent initialization checks in `TheatreDao.ensureSeeded()` and `SnackDao.ensureSeeded()` to ensure missing records are automatically seeded if needed).*

---

## How to Compile and Run

### Windows (PowerShell)

```powershell
# 1. Compile all Java source files into out/:
javac -cp "lib/mysql-connector-j-8.3.0.jar;." -d out (Get-ChildItem -Path src -Filter *.java | ForEach-Object { $_.FullName })

# 2. Run the application:
java -cp "out;lib/mysql-connector-j-8.3.0.jar" CineApp

# 3. Run the automated test suite:
java -cp "out;lib/mysql-connector-j-8.3.0.jar" SnackBookingTests
```

### Linux / macOS (Bash)

```bash
# 1. Compile:
javac -cp "lib/mysql-connector-j-8.3.0.jar:." -d out src/*.java

# 2. Run the application:
java -cp "out:lib/mysql-connector-j-8.3.0.jar" CineApp

# 3. Run the automated test suite:
java -cp "out:lib/mysql-connector-j-8.3.0.jar" SnackBookingTests
```

---

## Sample Interactions

### 1. Booking Confirmation (C3, C4 with Snacks and CINE10)
```text
==================================================
              BOOKING CONFIRMATION
==================================================
Booking ID       : CB1001
Movie            : Inception
Show Time        : 10:30 AM
Seats            : C3, C4

---------------- TICKET DETAILS ------------------
Ticket Subtotal  : Rs. 500

---------------- SNACK DETAILS -------------------
Popcorn        x2  : Rs. 240
Coke           x2  : Rs. 160

Snack Subtotal   : Rs. 400

---------------- PAYMENT DETAILS -----------------
Coupon           : CINE10
Ticket Discount  : Rs. 50
Final Amount     : Rs. 850
==================================================
Thank you for booking with CineBook!
==================================================
```

### 2. Cancellation Receipt (80% Refund)
```text
==================================================
              CANCELLATION RECEIPT
==================================================
Booking ID       : CB1001
Movie            : Inception
Show Time        : 10:30 AM
Seats freed      : C3, C4
Snack items      : 2 item(s) preserved
Amount Paid      : Rs. 850
Refund (80%)     : Rs. 680
==================================================
```

---

## OOP & Architectural Concepts Demonstrated

| Concept | Implementation Details |
|---|---|
| **DAO Pattern** | `TheatreDao` and `SnackDao` encapsulate all JDBC queries, transactions, and seeding. |
| **Separation of Concerns** | `CineApp` handles UI I/O; `SnackService` handles snack validation and cart manipulation; `Theatre` manages domain coordination; DAOs handle persistence. |
| **ACID Transactions** | Multi-table atomic commits and rollbacks across `bookings`, `booking_seats`, and `booking_snacks` (`conn.setAutoCommit(false)`). |
| **Row-Level Locking** | `SELECT ... FOR UPDATE` with sorted seat keys prevents concurrent seat race conditions and deadlocks. |
| **Historical Price Preservation** | `booking_snacks` records unit price and subtotal at booking time so future menu price changes do not alter past bookings. |
| **Enum Encapsulation** | `SeatType` contains all ticket pricing rules and row mappings. |
| **Interface Polymorphism** | `Show implements Bookable`. |
| **Method Overloading** | Overloaded calculation and booking methods in `Booking`, `Theatre`, and `TheatreDao`. |
| **Exception Handling** | Input validation and SQL errors handled gracefully without crashing the console loop. |

---

## License

This project is for educational purposes.
