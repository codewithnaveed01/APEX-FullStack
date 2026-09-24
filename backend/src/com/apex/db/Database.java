package com.apex.db;

import com.apex.config.AppConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connection owner for BOTH engines:
 *  - SQLite  (local laptop, zero config)   -> apex.db file
 *  - MySQL   (Railway / Render / any host) -> DATABASE_URL or MYSQL_* env vars
 * Railway example:  DATABASE_URL=mysql://user:pass@host:port/railwaydb
 */
public class Database implements AutoCloseable {

    public interface TxWork<T> {
        T run(Connection c) throws SQLException;
    }

    private final Connection conn;
    private final boolean mysql;

    public Database(AppConfig cfg) throws SQLException {
        String url = cfg.getJdbcUrl();
        if (url != null) {
            this.mysql = true;
            this.conn = DriverManager.getConnection(url);
        } else {
            this.mysql = false;
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + cfg.getDbFile());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
        }
        createSchema();
    }

    public Connection conn() {
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
        boolean old = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            T out = work.run(conn);
            conn.commit();
            return out;
        } catch (SQLException | RuntimeException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            throw e instanceof SQLException ? (SQLException) e : new SQLException(e);
        } finally {
            try { conn.setAutoCommit(old); } catch (SQLException ignored) { }
        }
    }

    public synchronized <T> T with(TxWork<T> work) throws SQLException {
        return work.run(conn);
    }

    private void createSchema() throws SQLException {
        String[] ddl = mysql ? Schema.MYSQL : Schema.SQLITE;
        try (Statement st = conn.createStatement()) {
            for (String sql : ddl) st.execute(sql);
            if (!mysql) {
                // v2 upgrade for existing local files
                try { st.execute("ALTER TABLE wallet_transactions ADD COLUMN account TEXT"); } catch (SQLException ignored) { }
                try { st.execute("ALTER TABLE cars ADD COLUMN hourly_rate INTEGER"); } catch (SQLException ignored) { }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
