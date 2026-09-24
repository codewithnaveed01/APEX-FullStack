package com.apex.db;

import com.apex.config.AppConfig;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connection owner for BOTH engines:
 *  - SQLite  (local laptop, zero config)   -> apex.db file
 *  - MySQL   (Railway / Render / any host) -> MYSQL_URL / DATABASE_URL / MYSQL* env vars
 *
 * Managed hosts (Railway) may start the database AFTER the app, and may close
 * idle connections, so the MySQL connection is retried at startup and
 * transparently re-established whenever it dies. When MySQL env vars are set
 * we NEVER silently fall back to SQLite - that would hide data loss.
 */
public class Database implements AutoCloseable {

    public interface TxWork<T> {
        T run(Connection c) throws SQLException;
    }

    private static final int MYSQL_RETRY_ATTEMPTS = 30;
    private static final long MYSQL_RETRY_WAIT_MS = 2000;

    private final String jdbcUrl;   // null -> sqlite
    private final AppConfig cfg;
    private final boolean mysql;
    private Connection conn;

    public Database(AppConfig cfg) throws SQLException {
        this.cfg = cfg;
        this.jdbcUrl = cfg.getJdbcUrl();
        if (jdbcUrl != null) {
            this.mysql = true;
            this.conn = connectMysqlWithRetry();
        } else {
            if (AppConfig.mysqlConfigured()) {
                // env vars present but unparseable - refuse to start rather than
                // silently writing to an ephemeral SQLite file in the container.
                throw new SQLException("MySQL env vars are set but could not be parsed into a JDBC URL. "
                        + "Check MYSQL_URL / DATABASE_URL / MYSQL_HOST values.");
            }
            this.mysql = false;
            try {
                Files.createDirectories(cfg.getHome());
                Files.createDirectories(cfg.getUploadsDir());
            } catch (Exception ignored) { }
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + cfg.getDbFile());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
        }
        createSchema();
    }

    private Connection connectMysqlWithRetry() throws SQLException {
        SQLException last = null;
        for (int i = 1; i <= MYSQL_RETRY_ATTEMPTS; i++) {
            try {
                return DriverManager.getConnection(jdbcUrl);
            } catch (SQLException e) {
                last = e;
                System.out.println("[APEX] MySQL not reachable yet (attempt " + i + "/" + MYSQL_RETRY_ATTEMPTS
                        + "): " + e.getMessage());
                try { Thread.sleep(MYSQL_RETRY_WAIT_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new SQLException("Could not connect to MySQL after " + MYSQL_RETRY_ATTEMPTS
                + " attempts. URL (password hidden): " + jdbcUrl.replaceAll("password=[^&]*", "password=***"), last);
    }

    /**
     * Live connection, reconnecting automatically when the server closed an
     * idle connection (common on Railway/Render managed MySQL).
     * Synchronized: the whole app serializes DB work on this object's monitor.
     */
    public synchronized Connection conn() throws SQLException {
        if (mysql) {
            boolean dead;
            try {
                dead = conn == null || conn.isClosed() || !conn.isValid(2);
            } catch (SQLException e) {
                dead = true;
            }
            if (dead) {
                System.out.println("[APEX] MySQL connection lost - reconnecting");
                conn = DriverManager.getConnection(jdbcUrl);
            }
        }
        return conn;
    }

    public boolean isMysql() {
        return mysql;
    }

    public String engine() {
        return mysql ? "mysql" : "sqlite";
    }

    /** Runs work inside a transaction with rollback on failure. */
    public synchronized <T> T tx(TxWork<T> work) throws SQLException {
        boolean old = conn().getAutoCommit();
        try {
            conn().setAutoCommit(false);
            T out = work.run(conn());
            conn().commit();
            return out;
        } catch (SQLException | RuntimeException e) {
            try { conn().rollback(); } catch (SQLException ignored) { }
            throw e instanceof SQLException ? (SQLException) e : new SQLException(e);
        } finally {
            try { conn().setAutoCommit(old); } catch (SQLException ignored) { }
        }
    }

    public synchronized <T> T with(TxWork<T> work) throws SQLException {
        return work.run(conn());
    }

    private void createSchema() throws SQLException {
        String[] ddl = mysql ? Schema.MYSQL : Schema.SQLITE;
        try (Statement st = conn().createStatement()) {
            for (String sql : ddl) st.execute(sql);
            if (!mysql) {
                // v2 upgrade for existing local files
                try { st.execute("ALTER TABLE wallet_transactions ADD COLUMN account TEXT"); } catch (SQLException ignored) { }
                try { st.execute("ALTER TABLE cars ADD COLUMN hourly_rate INTEGER"); } catch (SQLException ignored) { }
            }
            // v3 upgrade: documents store file bytes in the DB (deploy disk is ephemeral)
            try { st.execute("ALTER TABLE documents ADD COLUMN content " + (mysql ? "MEDIUMBLOB" : "BLOB")); } catch (SQLException ignored) { }
            // v4 upgrade (existing MySQL DBs from EARLIER deploys): widen text columns.
            // Earlier deploys created them as TEXT (64KB limit). Car JSONs with
            // base64 photos exceed 64KB -> every full-state sync failed with
            // "Data too long for column json" and the admin's save was LOST.
            if (mysql) {
                String[] widen = {
                    "ALTER TABLE cars MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE drivers MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE users MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE bookings MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE booking_items MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE owner_applications MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE notifications MODIFY COLUMN msg MEDIUMTEXT",
                    "ALTER TABLE chats MODIFY COLUMN json MEDIUMTEXT",
                    "ALTER TABLE wallet_transactions MODIFY COLUMN note MEDIUMTEXT",
                    "ALTER TABLE settings MODIFY COLUMN value MEDIUMTEXT",
                    "ALTER TABLE reviews MODIFY COLUMN body MEDIUMTEXT"
                };
                for (String sql : widen) { try { st.execute(sql); } catch (SQLException ignored) { } }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
