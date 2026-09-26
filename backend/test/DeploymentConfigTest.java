package com.apex.config;

/** Run with DATABASE_URL/PGHOST/RAILWAY_ENVIRONMENT unset (see README). */
public final class DeploymentConfigTest {
    private DeploymentConfigTest() { }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (System.getenv("DATABASE_URL") != null || System.getenv("PGHOST") != null ||
                System.getenv("RAILWAY_ENVIRONMENT") != null || System.getenv("RAILWAY_SERVICE_ID") != null) {
            throw new IllegalStateException("Unset DATABASE_URL/PGHOST/RAILWAY_* to run this config test");
        }
        try {
            System.setProperty("DATABASE_URL",
                    "postgres://name%40corp:p%2540ss+word%3A09@db.local:6543/cars%2Bdb?sslmode=require");
            AppConfig parsed = AppConfig.load();
            check("name@corp".equals(parsed.dbUser), "Encoded username was not decoded");
            check("p%40ss+word:09".equals(parsed.dbPassword), "Password was decoded incorrectly");
            check("db.local".equals(parsed.dbHost) && parsed.dbPort == 6543, "Host/port mismatch");
            check("cars+db".equals(parsed.dbDatabase) && parsed.dbSsl, "DB/SSL mismatch");

            System.setProperty("DATABASE_URL", "postgres://name:SECRET-DO-NOT-LOG@/cars");
            try {
                AppConfig.load();
                throw new AssertionError("Invalid DATABASE_URL was accepted");
            } catch (IllegalStateException e) {
                check(!e.toString().contains("SECRET-DO-NOT-LOG") && e.getCause() == null,
                        "Invalid URL printed the password");
            }

            System.clearProperty("DATABASE_URL");
            System.setProperty("RAILWAY_ENVIRONMENT", "production");
            try {
                AppConfig.load();
                throw new AssertionError("Railway started without DATABASE_URL or PGHOST");
            } catch (IllegalStateException e) {
                check(e.getMessage().contains("DATABASE_URL") && e.getMessage().contains("Postgres"),
                        "Missing Railway DB reference error is not actionable");
            }
            System.out.println("PASS: Railway missing-DB guard, URL decoding, safe invalid-URL error");
        } finally {
            System.clearProperty("DATABASE_URL");
            System.clearProperty("RAILWAY_ENVIRONMENT");
        }
    }
}
