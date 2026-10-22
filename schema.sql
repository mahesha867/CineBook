-- CineBook Cinema Ticket Booking System
-- Database Schema and Seed Script

CREATE DATABASE IF NOT EXISTS cinebook;
USE cinebook;

-- -------------------------------------------------------------
-- 1. Shows Table
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shows (
    show_id INT AUTO_INCREMENT PRIMARY KEY,
    movie_name VARCHAR(100) NOT NULL,
    show_time VARCHAR(20) NOT NULL,
    CONSTRAINT uk_show_movie_time UNIQUE (movie_name, show_time)
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- Sequence table for database-generated collision-safe booking IDs
-- Starts at 1001 to maintain CB1001, CB1002 format
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking_seq (
    id INT AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB AUTO_INCREMENT = 1001;

-- -------------------------------------------------------------
-- 2. Bookings Table
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    booking_id VARCHAR(20) PRIMARY KEY,
    show_id INT NOT NULL,
    amount_paid DECIMAL(10, 2) NOT NULL,
    coupon VARCHAR(50) DEFAULT NULL,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 3. Seats Table
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seats (
    seat_id INT AUTO_INCREMENT PRIMARY KEY,
    show_id INT NOT NULL,
    seat_row CHAR(1) NOT NULL,
    seat_number INT NOT NULL,
    is_booked BOOLEAN NOT NULL DEFAULT FALSE,
    booking_id VARCHAR(20) DEFAULT NULL,
    CONSTRAINT uk_show_seat UNIQUE (show_id, seat_row, seat_number),
    CONSTRAINT fk_seats_show FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE,
    CONSTRAINT fk_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 4. Booking Seats Table
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking_seats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id VARCHAR(20) NOT NULL,
    seat_row CHAR(1) NOT NULL,
    seat_number INT NOT NULL,
    CONSTRAINT uk_booking_seat UNIQUE (booking_id, seat_row, seat_number),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- Seed Data (Idempotent)
-- -------------------------------------------------------------

-- 4 Initial Shows
INSERT INTO shows (show_id, movie_name, show_time) VALUES
(1, 'Inception', '10:30 AM'),
(2, 'Interstellar', '02:00 PM'),
(3, 'The Dark Knight', '06:45 PM'),
(4, 'Oppenheimer', '09:30 PM')
ON DUPLICATE KEY UPDATE movie_name = VALUES(movie_name), show_time = VALUES(show_time);

-- Seed 40 seats (Rows A to E, Numbers 1 to 8) for each show
-- Show 1: Inception
INSERT IGNORE INTO seats (show_id, seat_row, seat_number, is_booked) VALUES
(1, 'A', 1, FALSE), (1, 'A', 2, FALSE), (1, 'A', 3, FALSE), (1, 'A', 4, FALSE), (1, 'A', 5, FALSE), (1, 'A', 6, FALSE), (1, 'A', 7, FALSE), (1, 'A', 8, FALSE),
(1, 'B', 1, FALSE), (1, 'B', 2, FALSE), (1, 'B', 3, FALSE), (1, 'B', 4, FALSE), (1, 'B', 5, FALSE), (1, 'B', 6, FALSE), (1, 'B', 7, FALSE), (1, 'B', 8, FALSE),
(1, 'C', 1, FALSE), (1, 'C', 2, FALSE), (1, 'C', 3, FALSE), (1, 'C', 4, FALSE), (1, 'C', 5, FALSE), (1, 'C', 6, FALSE), (1, 'C', 7, FALSE), (1, 'C', 8, FALSE),
(1, 'D', 1, FALSE), (1, 'D', 2, FALSE), (1, 'D', 3, FALSE), (1, 'D', 4, FALSE), (1, 'D', 5, FALSE), (1, 'D', 6, FALSE), (1, 'D', 7, FALSE), (1, 'D', 8, FALSE),
(1, 'E', 1, FALSE), (1, 'E', 2, FALSE), (1, 'E', 3, FALSE), (1, 'E', 4, FALSE), (1, 'E', 5, FALSE), (1, 'E', 6, FALSE), (1, 'E', 7, FALSE), (1, 'E', 8, FALSE);

-- Show 2: Interstellar
INSERT IGNORE INTO seats (show_id, seat_row, seat_number, is_booked) VALUES
(2, 'A', 1, FALSE), (2, 'A', 2, FALSE), (2, 'A', 3, FALSE), (2, 'A', 4, FALSE), (2, 'A', 5, FALSE), (2, 'A', 6, FALSE), (2, 'A', 7, FALSE), (2, 'A', 8, FALSE),
(2, 'B', 1, FALSE), (2, 'B', 2, FALSE), (2, 'B', 3, FALSE), (2, 'B', 4, FALSE), (2, 'B', 5, FALSE), (2, 'B', 6, FALSE), (2, 'B', 7, FALSE), (2, 'B', 8, FALSE),
(2, 'C', 1, FALSE), (2, 'C', 2, FALSE), (2, 'C', 3, FALSE), (2, 'C', 4, FALSE), (2, 'C', 5, FALSE), (2, 'C', 6, FALSE), (2, 'C', 7, FALSE), (2, 'C', 8, FALSE),
(2, 'D', 1, FALSE), (2, 'D', 2, FALSE), (2, 'D', 3, FALSE), (2, 'D', 4, FALSE), (2, 'D', 5, FALSE), (2, 'D', 6, FALSE), (2, 'D', 7, FALSE), (2, 'D', 8, FALSE),
(2, 'E', 1, FALSE), (2, 'E', 2, FALSE), (2, 'E', 3, FALSE), (2, 'E', 4, FALSE), (2, 'E', 5, FALSE), (2, 'E', 6, FALSE), (2, 'E', 7, FALSE), (2, 'E', 8, FALSE);

-- Show 3: The Dark Knight
INSERT IGNORE INTO seats (show_id, seat_row, seat_number, is_booked) VALUES
(3, 'A', 1, FALSE), (3, 'A', 2, FALSE), (3, 'A', 3, FALSE), (3, 'A', 4, FALSE), (3, 'A', 5, FALSE), (3, 'A', 6, FALSE), (3, 'A', 7, FALSE), (3, 'A', 8, FALSE),
(3, 'B', 1, FALSE), (3, 'B', 2, FALSE), (3, 'B', 3, FALSE), (3, 'B', 4, FALSE), (3, 'B', 5, FALSE), (3, 'B', 6, FALSE), (3, 'B', 7, FALSE), (3, 'B', 8, FALSE),
(3, 'C', 1, FALSE), (3, 'C', 2, FALSE), (3, 'C', 3, FALSE), (3, 'C', 4, FALSE), (3, 'C', 5, FALSE), (3, 'C', 6, FALSE), (3, 'C', 7, FALSE), (3, 'C', 8, FALSE),
(3, 'D', 1, FALSE), (3, 'D', 2, FALSE), (3, 'D', 3, FALSE), (3, 'D', 4, FALSE), (3, 'D', 5, FALSE), (3, 'D', 6, FALSE), (3, 'D', 7, FALSE), (3, 'D', 8, FALSE),
(3, 'E', 1, FALSE), (3, 'E', 2, FALSE), (3, 'E', 3, FALSE), (3, 'E', 4, FALSE), (3, 'E', 5, FALSE), (3, 'E', 6, FALSE), (3, 'E', 7, FALSE), (3, 'E', 8, FALSE);

-- Show 4: Oppenheimer
INSERT IGNORE INTO seats (show_id, seat_row, seat_number, is_booked) VALUES
(4, 'A', 1, FALSE), (4, 'A', 2, FALSE), (4, 'A', 3, FALSE), (4, 'A', 4, FALSE), (4, 'A', 5, FALSE), (4, 'A', 6, FALSE), (4, 'A', 7, FALSE), (4, 'A', 8, FALSE),
(4, 'B', 1, FALSE), (4, 'B', 2, FALSE), (4, 'B', 3, FALSE), (4, 'B', 4, FALSE), (4, 'B', 5, FALSE), (4, 'B', 6, FALSE), (4, 'B', 7, FALSE), (4, 'B', 8, FALSE),
(4, 'C', 1, FALSE), (4, 'C', 2, FALSE), (4, 'C', 3, FALSE), (4, 'C', 4, FALSE), (4, 'C', 5, FALSE), (4, 'C', 6, FALSE), (4, 'C', 7, FALSE), (4, 'C', 8, FALSE),
(4, 'D', 1, FALSE), (4, 'D', 2, FALSE), (4, 'D', 3, FALSE), (4, 'D', 4, FALSE), (4, 'D', 5, FALSE), (4, 'D', 6, FALSE), (4, 'D', 7, FALSE), (4, 'D', 8, FALSE),
(4, 'E', 1, FALSE), (4, 'E', 2, FALSE), (4, 'E', 3, FALSE), (4, 'E', 4, FALSE), (4, 'E', 5, FALSE), (4, 'E', 6, FALSE), (4, 'E', 7, FALSE), (4, 'E', 8, FALSE);
