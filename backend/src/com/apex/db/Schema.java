package com.apex.db;

/** DDL for both supported engines: SQLite (local) and MySQL (Railway/Render). */
public final class Schema {

    private Schema() { }

    public static final String[] SQLITE = {
        "CREATE TABLE IF NOT EXISTS cars(id INTEGER PRIMARY KEY, name TEXT, brand TEXT, category TEXT, origin TEXT, image TEXT, year INTEGER, rate INTEGER, hourly_rate INTEGER, deposit INTEGER, seats INTEGER, engine TEXT, power TEXT, fuel TEXT, km TEXT, color TEXT, plate TEXT, car_condition TEXT, status TEXT, market_note TEXT, owner_id TEXT, json TEXT)",
        "CREATE TABLE IF NOT EXISTS drivers(id INTEGER PRIMARY KEY, name TEXT, phone TEXT, city TEXT, experience INTEGER, license TEXT, active INTEGER, json TEXT)",
        "CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, username TEXT, email TEXT, phone TEXT, name TEXT, cnic TEXT, role TEXT, password_hash TEXT, salt TEXT, created_at TEXT, json TEXT)",
        "CREATE TABLE IF NOT EXISTS bookings(id TEXT PRIMARY KEY, user_id TEXT, customer_name TEXT, email TEXT, phone TEXT, destination TEXT, pickup_mode TEXT, payment TEXT, status TEXT, payment_status TEXT, rental INTEGER, deposit INTEGER, home_delivery INTEGER, total INTEGER, paid INTEGER, cancellation_fee INTEGER, start_dt TEXT, end_dt TEXT, pickup_at TEXT, actual_return TEXT, extra_hours INTEGER, extra_charges INTEGER, final_amount INTEGER, created_at TEXT, json TEXT)",
        "CREATE TABLE IF NOT EXISTS booking_items(id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT, pos INTEGER, car_id INTEGER, start_date TEXT, end_date TEXT, start_dt TEXT, end_dt TEXT, city TEXT, service TEXT, assigned_driver INTEGER, json TEXT)",
        "CREATE TABLE IF NOT EXISTS owner_applications(id TEXT PRIMARY KEY, user_id TEXT, brand TEXT, model TEXT, status TEXT, created_at TEXT, json TEXT)",
        "CREATE TABLE IF NOT EXISTS notifications(id TEXT PRIMARY KEY, user_id TEXT, title TEXT, msg TEXT, time TEXT, read_flag INTEGER, json TEXT)",
        "CREATE TABLE IF NOT EXISTS chats(user_id TEXT PRIMARY KEY, user_name TEXT, last_time TEXT, json TEXT)",
        "CREATE TABLE IF NOT EXISTS banned_cnic(cnic TEXT PRIMARY KEY, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS owner_wallets(owner_id TEXT PRIMARY KEY, balance INTEGER)",
        "CREATE TABLE IF NOT EXISTS wallet(id INTEGER PRIMARY KEY CHECK(id=1), balance INTEGER)",
        "CREATE TABLE IF NOT EXISTS wallet_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, amount INTEGER, note TEXT, account TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY, role TEXT, username TEXT, created_at TEXT, expires_at TEXT)",
        "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT, method TEXT, amount INTEGER, tid TEXT, receipt_doc INTEGER, status TEXT, submitted_at TEXT, reviewed_at TEXT, note TEXT)",
        "CREATE TABLE IF NOT EXISTS documents(id INTEGER PRIMARY KEY AUTOINCREMENT, owner_type TEXT, owner_id TEXT, kind TEXT, path TEXT, content_type TEXT, content BLOB, status TEXT DEFAULT 'Pending', created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS reviews(id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT, car_id INTEGER, user_id TEXT, rating INTEGER, body TEXT, status TEXT, created_at TEXT)",
        "INSERT OR IGNORE INTO wallet(id, balance) VALUES (1, 0)"
    };

    public static final String[] MYSQL = {
        "CREATE TABLE IF NOT EXISTS cars(id INT PRIMARY KEY, name TEXT, brand TEXT, category TEXT, origin TEXT, image TEXT, year INT, rate INT, hourly_rate INT, deposit INT, seats INT, engine TEXT, power TEXT, fuel TEXT, km TEXT, color TEXT, plate TEXT, car_condition TEXT, status TEXT, market_note TEXT, owner_id TEXT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS drivers(id INT PRIMARY KEY, name TEXT, phone TEXT, city TEXT, experience INT, license TEXT, active INT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS users(id VARCHAR(64) PRIMARY KEY, username TEXT, email TEXT, phone TEXT, name TEXT, cnic TEXT, role TEXT, password_hash TEXT, salt TEXT, created_at TEXT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS bookings(id VARCHAR(64) PRIMARY KEY, user_id TEXT, customer_name TEXT, email TEXT, phone TEXT, destination TEXT, pickup_mode TEXT, payment TEXT, status TEXT, payment_status TEXT, rental INT, deposit INT, home_delivery INT, total INT, paid INT, cancellation_fee INT, start_dt TEXT, end_dt TEXT, pickup_at TEXT, actual_return TEXT, extra_hours INT, extra_charges INT, final_amount INT, created_at TEXT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS booking_items(id INT PRIMARY KEY AUTO_INCREMENT, booking_id VARCHAR(64), pos INT, car_id INT, start_date TEXT, end_date TEXT, start_dt TEXT, end_dt TEXT, city TEXT, service TEXT, assigned_driver INT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS owner_applications(id VARCHAR(64) PRIMARY KEY, user_id TEXT, brand TEXT, model TEXT, status TEXT, created_at TEXT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS notifications(id VARCHAR(64) PRIMARY KEY, user_id TEXT, title TEXT, msg MEDIUMTEXT, time TEXT, read_flag INT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS chats(user_id VARCHAR(64) PRIMARY KEY, user_name TEXT, last_time TEXT, json MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS banned_cnic(cnic VARCHAR(32) PRIMARY KEY, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS owner_wallets(owner_id VARCHAR(64) PRIMARY KEY, balance BIGINT)",
        "CREATE TABLE IF NOT EXISTS wallet(id INT PRIMARY KEY, balance BIGINT)",
        "CREATE TABLE IF NOT EXISTS wallet_transactions(id INT PRIMARY KEY AUTO_INCREMENT, type TEXT, amount BIGINT, note TEXT, account TEXT, created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS sessions(token VARCHAR(128) PRIMARY KEY, role TEXT, username TEXT, created_at TEXT, expires_at TEXT)",
        "CREATE TABLE IF NOT EXISTS settings(key_ VARCHAR(64) PRIMARY KEY, value MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS payments(id INT PRIMARY KEY AUTO_INCREMENT, booking_id VARCHAR(64), method TEXT, amount BIGINT, tid TEXT, receipt_doc INT, status TEXT, submitted_at TEXT, reviewed_at TEXT, note MEDIUMTEXT)",
        "CREATE TABLE IF NOT EXISTS documents(id INT PRIMARY KEY AUTO_INCREMENT, owner_type TEXT, owner_id TEXT, kind TEXT, path TEXT, content_type TEXT, content MEDIUMBLOB, status VARCHAR(20) DEFAULT 'Pending', created_at TEXT)",
        "CREATE TABLE IF NOT EXISTS reviews(id INT PRIMARY KEY AUTO_INCREMENT, booking_id VARCHAR(64), car_id INT, user_id TEXT, rating INT, body TEXT, status TEXT, created_at TEXT)",
        "INSERT IGNORE INTO wallet(id, balance) VALUES (1, 0)"
    };

    /** settings uses key/key_ depending on engine (KEY is reserved in MySQL). */
    public static String settingsSelect(boolean mysql) {
        return mysql ? "SELECT value FROM settings WHERE key_=?" : "SELECT value FROM settings WHERE key=?";
    }

    public static String settingsUpsert(boolean mysql) {
        return mysql
            ? "INSERT INTO settings(key_, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value=VALUES(value)"
            : "INSERT INTO settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value";
    }

    public static String banInsert(boolean mysql) {
        return mysql
            ? "INSERT IGNORE INTO banned_cnic(cnic, created_at) VALUES (?, ?)"
            : "INSERT OR IGNORE INTO banned_cnic(cnic, created_at) VALUES (?, ?)";
    }
}
