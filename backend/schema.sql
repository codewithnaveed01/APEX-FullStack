-- =====================================================================
-- APEX - Driven Beyond Ordinary : SQLite schema (reference copy)
-- The Java backend creates these tables automatically on first run
-- (SQLite local / MySQL on Railway-Render via DATABASE_URL).
-- =====================================================================

CREATE TABLE IF NOT EXISTS cars(
  id INTEGER PRIMARY KEY, name TEXT, brand TEXT, category TEXT, origin TEXT, image TEXT,
  year INTEGER, rate INTEGER, hourly_rate INTEGER, deposit INTEGER, seats INTEGER,
  engine TEXT, power TEXT, fuel TEXT, km TEXT, color TEXT, plate TEXT,
  car_condition TEXT, status TEXT, market_note TEXT, owner_id TEXT, json TEXT);

CREATE TABLE IF NOT EXISTS drivers(
  id INTEGER PRIMARY KEY, name TEXT, phone TEXT, city TEXT, experience INTEGER,
  license TEXT, active INTEGER, json TEXT);

CREATE TABLE IF NOT EXISTS users(
  id TEXT PRIMARY KEY, username TEXT, email TEXT, phone TEXT, name TEXT, cnic TEXT,
  role TEXT, password_hash TEXT, salt TEXT, created_at TEXT, json TEXT);

CREATE TABLE IF NOT EXISTS bookings(
  id TEXT PRIMARY KEY, user_id TEXT, customer_name TEXT, email TEXT, phone TEXT,
  destination TEXT, pickup_mode TEXT, payment TEXT, status TEXT, payment_status TEXT,
  rental INTEGER, deposit INTEGER, home_delivery INTEGER, total INTEGER, paid INTEGER,
  cancellation_fee INTEGER, start_dt TEXT, end_dt TEXT, pickup_at TEXT,
  actual_return TEXT, extra_hours INTEGER, extra_charges INTEGER, final_amount INTEGER,
  created_at TEXT, json TEXT);

CREATE TABLE IF NOT EXISTS booking_items(
  id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT, pos INTEGER, car_id INTEGER,
  start_date TEXT, end_date TEXT, start_dt TEXT, end_dt TEXT, city TEXT,
  service TEXT, assigned_driver INTEGER, json TEXT);

CREATE TABLE IF NOT EXISTS owner_applications(
  id TEXT PRIMARY KEY, user_id TEXT, brand TEXT, model TEXT, status TEXT,
  created_at TEXT, json TEXT);

CREATE TABLE IF NOT EXISTS notifications(
  id TEXT PRIMARY KEY, user_id TEXT, title TEXT, msg TEXT, time TEXT,
  read_flag INTEGER, json TEXT);

CREATE TABLE IF NOT EXISTS chats(
  user_id TEXT PRIMARY KEY, user_name TEXT, last_time TEXT, json TEXT);

CREATE TABLE IF NOT EXISTS banned_cnic(cnic TEXT PRIMARY KEY, created_at TEXT);

CREATE TABLE IF NOT EXISTS owner_wallets(owner_id TEXT PRIMARY KEY, balance INTEGER);

CREATE TABLE IF NOT EXISTS wallet(id INTEGER PRIMARY KEY CHECK(id=1), balance INTEGER);

CREATE TABLE IF NOT EXISTS wallet_transactions(
  id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, amount INTEGER, note TEXT,
  account TEXT, created_at TEXT);

CREATE TABLE IF NOT EXISTS sessions(
  token TEXT PRIMARY KEY, role TEXT, username TEXT, created_at TEXT, expires_at TEXT);

CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT);

-- Online payment receipts + TID verification workflow
CREATE TABLE IF NOT EXISTS payments(
  id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT, method TEXT, amount INTEGER,
  tid TEXT, receipt_doc INTEGER, status TEXT, submitted_at TEXT, reviewed_at TEXT, note TEXT);

-- Sensitive uploads (CNIC front/back, licence, receipts) - served only via auth API
CREATE TABLE IF NOT EXISTS documents(
  id INTEGER PRIMARY KEY AUTOINCREMENT, owner_type TEXT, owner_id TEXT, kind TEXT,
  path TEXT, content_type TEXT, status TEXT DEFAULT 'Pending', created_at TEXT);

-- Customer reviews with moderation
CREATE TABLE IF NOT EXISTS reviews(
  id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT, car_id INTEGER, user_id TEXT,
  rating INTEGER, body TEXT, status TEXT, created_at TEXT);

INSERT OR IGNORE INTO wallet(id, balance) VALUES (1, 0);
