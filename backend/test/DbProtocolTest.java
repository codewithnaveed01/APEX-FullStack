package com.apex.db;

import com.apex.config.AppConfig;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Deployment regression test against a disposable PostgreSQL database.
 * Run after build.sh (see README). Covers SCRAM auth, migrations/table visibility,
 * idempotent restarts, and recovering a connection after a server-side SQL error.
 */
public final class DbProtocolTest {
    private DbProtocolTest() { }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AppConfig cfg = AppConfig.load();
        try (Database db = Database.open(new Database.Cfg()
                .host(cfg.dbHost).port(cfg.dbPort).user(cfg.dbUser)
                .password(cfg.dbPassword).database(cfg.dbDatabase).ssl(cfg.dbSsl),
                2, 0, 0)) {
            Migrate.run(db, Paths.get(cfg.home, "migrations"));
            Migrate.run(db, Paths.get(cfg.home, "migrations")); // a redeploy must be safe
            check("1".equals(db.with(c -> c.query(
                    "SELECT count(*) FROM schema_migrations WHERE name = $1",
                    new String[]{"001_init.sql"})).first()), "001_init.sql was not applied exactly once");

            Set<String> expected = Set.of("users", "sessions", "cars", "drivers", "bookings",
                    "booking_items", "owner_applications", "notifications", "chat_threads",
                    "chat_messages", "banned_cnic", "wallet", "owner_wallets",
                    "wallet_transactions", "documents", "payments", "reviews", "app_settings");
            QueryResult tables = db.with(c -> c.query(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'",
                    new String[0]));
            Set<String> actual = new HashSet<>();
            for (String[] row : tables.rows) actual.add(row[0]);
            check(actual.containsAll(expected), "Missing tables: " + difference(expected, actual));

            // PostgreSQL sends ErrorResponse followed by ReadyForQuery. The
            // same connection must work after the error (and after a rollback).
            db.with(c -> {
                expectMissingTable(c);
                check(c.isHealthy(), "Connection not reusable after SQL error");
                check("ok".equals(c.query("SELECT $1::text", new String[]{"ok"}).first()),
                        "SELECT after an ErrorResponse failed");
                return null;
            });
            try {
                db.tx(c -> c.query("SELECT * FROM apex_missing_protocol_test_table", new String[0]));
                throw new AssertionError("Invalid SQL in transaction unexpectedly succeeded");
            } catch (PgConnection.PgException e) {
                check("42P01".equals(e.pgCode), "Unexpected transaction error " + e.pgCode);
            }
            check(db.ping(), "Database is not reachable after transaction rollback");

            // Wrong credentials must return an auth error promptly, not a 30s
            // read timeout caused by mishandling PostgreSQL's SASL framing.
            try (PgConnection ignored = new PgConnection(cfg.dbHost, cfg.dbPort, cfg.dbUser,
                    cfg.dbPassword + "_wrong", cfg.dbDatabase, cfg.dbSsl)) {
                throw new AssertionError("Wrong password unexpectedly succeeded");
            } catch (PgConnection.PgException e) {
                check(!e.getMessage().contains("Read timed out"), "Auth error became a read timeout");
                check("28P01".equals(e.pgCode), "Expected invalid_password (28P01), got " + e.pgCode);
            }
            try (Database ignored = Database.open(new Database.Cfg()
                    .host(cfg.dbHost).port(cfg.dbPort).user(cfg.dbUser)
                    .password(cfg.dbPassword + "_wrong").database(cfg.dbDatabase).ssl(cfg.dbSsl),
                    1, 10, 1000)) {
                throw new AssertionError("Pool started with a bad password");
            } catch (IllegalStateException e) {
                check(e.getMessage().contains("PostgreSQL rejected DATABASE_URL"),
                        "Bad credentials should fail fast with an actionable message");
            }
            System.out.println("PASS: SCRAM auth (including bad password), 18 tables, migration, SQL error recovery");
        }
    }

    private static void expectMissingTable(PgConnection c) {
        try {
            c.query("SELECT * FROM apex_missing_protocol_test_table", new String[0]);
            throw new AssertionError("Invalid SQL unexpectedly succeeded");
        } catch (PgConnection.PgException e) {
            check("42P01".equals(e.pgCode), "Expected undefined_table (42P01), got " + e.pgCode);
        }
    }

    private static Set<String> difference(Set<String> wanted, Set<String> actual) {
        Set<String> missing = new HashSet<>(wanted);
        missing.removeAll(actual);
        return missing;
    }
}
