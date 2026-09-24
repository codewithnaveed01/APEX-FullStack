package com.apex.db;

import com.apex.log.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Fixed-size pool of {@link PgConnection}s with transaction support.
 *
 *  - every request borrows one connection and returns it in finally
 *  - {@link #tx} wraps work in BEGIN/COMMIT with automatic ROLLBACK
 *  - dead connections are discarded and lazily replaced
 */
public final class Database implements AutoCloseable {

    public static final class Cfg {
        public String host = "localhost";
        public int port = 5432;
        public String user = "apex";
        public String password = "";
        public String database = "apex";
        public boolean ssl = false;

        public Cfg host(String h) { host = h; return this; }
        public Cfg port(int p) { port = p; return this; }
        public Cfg user(String u) { user = u; return this; }
        public Cfg password(String p) { password = p; return this; }
        public Cfg database(String d) { database = d; return this; }
        public Cfg ssl(boolean s) { ssl = s; return this; }

        public String describe() {
            return "postgresql://" + user + ":***@" + host + ":" + port + "/" + database + (ssl ? "?sslmode=require" : "");
        }
    }

    private final Cfg cfg;
    private final int size;
    private final LinkedBlockingQueue<PgConnection> idle = new LinkedBlockingQueue<>();
    private final Set<PgConnection> all = ConcurrentHashMap.newKeySet();
    private volatile boolean closed = false;

    private Database(Cfg cfg, int size) {
        this.cfg = cfg;
        this.size = Math.max(1, size);
    }

    /** Open the pool, retrying so the app can start before the DB is up (Railway). */
    public static Database open(Cfg cfg, int size, int retries, long retryMs) {
        Database db = new Database(cfg, size);
        RuntimeException last = null;
        for (int i = 0; i <= retries; i++) {
            try {
                db.with(c -> { c.simpleQuery("SELECT 1"); return null; });
                Logger.info("Connected to PostgreSQL at {} (pool size {})", cfg.describe(), size);
                return db;
            } catch (RuntimeException e) {
                last = e;
                if (i < retries) {
                    Logger.warn("Database not ready ({}). Retry {}/{} in {} ms",
                            e.getMessage(), i + 1, retries, retryMs);
                    try { Thread.sleep(retryMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw new IllegalStateException("Cannot connect to PostgreSQL: " + last.getMessage(), last);
    }

    public Cfg cfg() { return cfg; }

    public PgConnection borrow() {
        if (closed) throw new IllegalStateException("database closed");
        PgConnection c = idle.poll();
        if (c != null) {
            if (c.isHealthy() && c.ping()) return c;
            all.remove(c);
            try { c.close(); } catch (Exception ignored) { }
        }
        synchronized (all) {
            if (all.size() < size) {
                PgConnection nc = new PgConnection(cfg.host, cfg.port, cfg.user, cfg.password, cfg.database, cfg.ssl);
                all.add(nc);
                return nc;
            }
        }
        try {
            c = idle.poll(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PgConnection.PgException("57014", "Interrupted waiting for a connection");
        }
        if (c == null || !c.ping()) {
            throw new PgConnection.PgException("57014", "Connection pool timeout - database busy or unreachable");
        }
        return c;
    }

    public void release(PgConnection c) {
        if (c == null) return;
        if (!c.isHealthy()) {
            // Half-read response (or dead socket): discard instead of poisoning.
            all.remove(c);
            try { c.close(); } catch (Exception ignored) { }
            return;
        }
        idle.offer(c);
    }

    /** Run work inside a transaction; commit on success, rollback on any failure. */
    public <T> T tx(Function<PgConnection, T> work) {
        PgConnection c = borrow();
        try {
            c.simpleQuery("BEGIN");
            try {
                T r = work.apply(c);
                c.simpleQuery("COMMIT");
                return r;
            } catch (Throwable t) {
                try { c.simpleQuery("ROLLBACK"); } catch (RuntimeException rb) {
                    Logger.error("ROLLBACK failed after error: {}", rb.getMessage());
                }
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                if (t instanceof Error) throw (Error) t;
                throw new RuntimeException(t);
            }
        } finally {
            release(c);
        }
    }

    /** Run work on a single (auto-commit) connection. */
    public <T> T with(Function<PgConnection, T> work) {
        PgConnection c = borrow();
        try {
            return work.apply(c);
        } finally {
            release(c);
        }
    }

    public boolean ping() {
        try {
            return with(PgConnection::ping);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void close() {
        closed = true;
        for (PgConnection c : all) {
            try { c.close(); } catch (Exception ignored) { }
        }
        all.clear();
        idle.clear();
    }
}
