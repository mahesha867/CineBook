# 🎬 CineBook

A Java console application for booking movie tickets at a single-screen cinema, backed by a persistent MySQL relational database. Built following OOP and Data Access Object (DAO) principles, covering JDBC transactions, row-level locking (`SELECT ... FOR UPDATE`), collision-safe ID generation, enums, interfaces, and graceful error handling.

---

## Features

- 🎥 **4 available shows** — Inception, Interstellar, The Dark Knight, and Oppenheimer persisted in MySQL.
- 🪑 **Interactive seat map** — 5×8 grid (rows A–E, columns 1–8) with live `O` (available) and `X` (booked) status loaded from MySQL.
- 💰 **Three seat categories** — prices derived from the `SeatType` enum (never stored in the database).
- 🔒 **ACID database transactions** — all requested seat codes in a booking are validated and committed atomically; failures roll back completely.
- 🛡️ **Concurrency-safe seat reservation** — uses row-level locking (`SELECT ... FOR UPDATE`) sorted deterministically to prevent duplicate bookings and deadlocks.
- 🎟️ **Coupon support** — `CINE10` applies a 10% discount via overloaded `calculateTotal()` / `computeTotal()`.
- ❌ **Transactional cancellation with 80% refund** — frees reserved seats, marks the booking cancelled, and rejects duplicate cancellations.
- 🔑 **Collision-safe booking IDs** — database-generated sequence preserving the `CB1001` display format without relying on in-memory counters.
- ⚡ **Idempotent seeding** — rerunning the application or schema script initializes missing shows and seats without duplicates.
- 🛑 **Fail-fast connectivity** — clear diagnostic console errors if the database is unavailable or misconfigured, preventing silent fallback to memory-only state.

---

## Seat Pricing

Prices are determined solely by the `SeatType` Java enum:

| Rows | Category | Price   |
|------|----------|---------|
| A–B  | REGULAR  | Rs. 150 |
| C–D  | PREMIUM  | Rs. 250 |
| E    | VIP      | Rs. 400 |

---

## Database Architecture

### Schema: `cinebook`

The database consists of 4 core tables plus a sequence generator:

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

---

## Project Structure

```
CineBook/
├── database/
│   └── schema.sql             # MySQL schema and idempotent seed script
├── lib/
│   └── mysql-connector-j-8.3.0.jar # MySQL Connector/J JDBC driver
├── out/                       # Compiled .class files (generated)
├── src/
│   ├── DatabaseConfig.java    # JDBC connection manager & env variable configuration
│   ├── TheatreDao.java        # DAO layer: SQL queries, transactions, row locking
│   ├── SeatType.java          # Enum: REGULAR, PREMIUM, VIP with pricing logic
│   ├── Bookable.java          # Interface: bookSeats(), cancelBooking()
│   ├── Show.java              # Entity: seat grid state, seat map rendering
│   ├── Booking.java           # Record: booking details, discount calculation, summaries
│   ├── Theatre.java           # Domain service: orchestration and active bookings
│   └── CineApp.java           # Entry point: console menu loop & fail-fast checks
├── schema.sql                 # Convenience root copy of database/schema.sql
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
- **JDK 8 or later**
- **MySQL Server 8.0 or later** running locally or remotely

### 2. Initialize Database & Seed Data
Run `database/schema.sql` using the MySQL command-line client:

```powershell
# From the CineBook root directory:
mysql -u root -p < database/schema.sql
```
*(Or in PowerShell: `Get-Content database\schema.sql | mysql -u root -p`)*

This script:
1. Creates the `cinebook` database if it doesn't exist.
2. Creates all tables and foreign key constraints.
3. Seeds the 4 initial shows (`Inception`, `Interstellar`, `The Dark Knight`, `Oppenheimer`).
4. Seeds 40 seats (Rows A–E, Columns 1–8) for each show.

*(Note: The Java application also includes an idempotent initialization check in `TheatreDao.ensureSeeded()` to ensure missing records are automatically seeded if needed).*

---

## How to Compile and Run

### Windows (PowerShell)

```powershell
# 1. Compile all Java source files into out/:
javac -cp "lib/mysql-connector-j-8.3.0.jar;." -d out (Get-ChildItem -Path src -Filter *.java | ForEach-Object { $_.FullName })

# 2. Run the application:
java -cp "out;lib/mysql-connector-j-8.3.0.jar" CineApp
```

### Linux / macOS (Bash)

```bash
# 1. Compile:
javac -cp "lib/mysql-connector-j-8.3.0.jar:." -d out src/*.java

# 2. Run:
java -cp "out:lib/mysql-connector-j-8.3.0.jar" CineApp
```

---

## Concurrency & Transaction Safety

1. **Two-Phase Commit in MySQL**:
   - Validation and seat code syntax checks run first.
   - Requested seats are sorted alphabetically and locked using `SELECT ... FOR UPDATE`. This eliminates deadlocks and ensures that concurrent booking attempts on overlapping seats queue safely.
   - If any seat is already booked or non-existent, the transaction rolls back (`conn.rollback()`) and throws a descriptive error.
   - If all seats are available, the booking and seats are inserted and updated within the same transaction before `conn.commit()`.
2. **Cancellation Protection**:
   - `cancelBooking` locks the booking row with `SELECT ... FOR UPDATE`.
   - Prevents duplicate cancellations and race conditions during refund processing.
   - Resets the seats back to `is_booked = FALSE` and `booking_id = NULL`.

---

## Sample Interactions

### 1. Booking Confirmation (C3, C4 with CINE10)
```text
==================================================
         BOOKING CONFIRMATION
==================================================
  Booking ID   : CB1001
  Movie        : Inception
  Show Time    : 10:30 AM
  Seats        : C3, C4
  Coupon       : CINE10 (10% discount applied)
  Amount Paid  : Rs. 450
==================================================
  Thank you for booking with CineBook!
==================================================
```

### 2. Already Booked Conflict
```text
  Enter seat codes (comma-separated, e.g. A1,B3,C5):
  Seats: C4, C5
  Enter coupon code (or press Enter to skip): 

  [!] Booking failed. Please fix the following issues:
  - Seat C4 is already booked.

  Seat map is unchanged. No seats were booked.
```

### 3. Cancellation Receipt (80% Refund)
```text
==================================================
         CANCELLATION RECEIPT
==================================================
  Booking ID   : CB1001
  Movie        : Inception
  Show Time    : 10:30 AM
  Seats freed  : C3, C4
  Amount Paid  : Rs. 450
  Refund (80%) : Rs. 360
==================================================
```

### 4. Database Unavailable Error (Fail-Fast)
```text
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  [!] FATAL DATABASE ERROR: Unable to connect to MySQL.
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  Target URL : jdbc:mysql://localhost:3306/cinebook...
  User       : root
  Reason     : Communications link failure
  ...
```

---

## OOP & Architectural Concepts Demonstrated

| Concept | Implementation Details |
|---|---|
| **DAO Pattern** | `TheatreDao` encapsulates all JDBC queries and transactions. |
| **Separation of Concerns** | `CineApp` handles UI I/O; `Theatre` manages domain coordination; `TheatreDao` handles persistence. |
| **ACID Transactions** | Multi-table atomic commits and rollbacks (`conn.setAutoCommit(false)`). |
| **Row-Level Locking** | `SELECT ... FOR UPDATE` prevents concurrent seat race conditions. |
| **Enum Encapsulation** | `SeatType` contains all pricing rules and row mappings. |
| **Interface Polymorphism** | `Show implements Bookable`. |
| **Method Overloading** | Overloaded total calculation in `Booking`. |
| **Exception Handling** | Custom and checked exceptions translated to clear user console messages. |

---

## License

This project is for educational purposes.
