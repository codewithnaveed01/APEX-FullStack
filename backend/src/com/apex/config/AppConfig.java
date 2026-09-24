package com.apex.config;

import java.net.URI;
import java.util.Locale;

/**
 * All configuration comes from the environment so the same build runs
 * locally, in Docker and on Railway.
 *
 *  PORT            - HTTP port (Railway injects this)        [3030]
 *  DATABASE_URL    - postgres://user:pass@host:port/db?sslmode=require
 *                    (used in preference to PG* vars)
 *  PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE/PGSSLMODE
 *  APEX_HOME       - backend working directory               [./backend]
 *  APEX_STATIC     - frontend directory to serve             [sibling "frontend"]
 *  APEX_UPLOADS    - upload storage dir                      [APEX_HOME/uploads]
 *  APEX_ADMIN_USER - seeded admin username                   [admin]
 *  APEX_ADMIN_PASS - seeded admin password                   [admin1234]
 *  APEX_SESSION_HOURS - session TTL                          [24]
 *  APEX_DB_POOL    - connection pool size                    [5]
 */
public final class AppConfig {

    public final int port;
    public final String home;
    public final String staticDir;
    public final String uploadsDir;
    public final String adminUsername;
    public final String adminPassword;
    public final int sessionHours;
    public final int dbPoolSize;

    public final String dbHost;
    public final int dbPort;
    public final String dbUser;
    public final String dbPassword;
    public final String dbDatabase;
    public final boolean dbSsl;

    private AppConfig(Builder b) {
        port = b.port; home = b.home; staticDir = b.staticDir; uploadsDir = b.uploadsDir;
        adminUsername = b.adminUsername; adminPassword = b.adminPassword;
        sessionHours = b.sessionHours; dbPoolSize = b.dbPoolSize;
        dbHost = b.dbHost; dbPort = b.dbPort; dbUser = b.dbUser;
        dbPassword = b.dbPassword; dbDatabase = b.dbDatabase; dbSsl = b.dbSsl;
    }

    public static AppConfig load() {
        String url = env("DATABASE_URL");
        Builder b = new Builder();
        if (url != null && !url.isBlank()) {
            parseUrl(url, b);
        } else {
            b.dbHost = env("PGHOST") != null ? env("PGHOST") : "localhost";
            b.dbPort = envInt("PGPORT", 5432);
            b.dbUser = env("PGUSER") != null ? env("PGUSER") : "apex";
            b.dbPassword = env("PGPASSWORD") != null ? env("PGPASSWORD") : "";
            b.dbDatabase = env("PGDATABASE") != null ? env("PGDATABASE") : "apex";
            String mode = env("PGSSLMODE");
            b.dbSsl = mode != null && (mode.equals("require") || mode.equals("verify-ca") || mode.equals("verify-full"));
        }
        return b.build();
    }

    private static void parseUrl(String url, Builder b) {
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("postgres") && !scheme.equals("postgresql")) {
                throw new IllegalArgumentException("Unsupported scheme in DATABASE_URL: " + scheme);
            }
            if (u.getHost() != null) b.dbHost = u.getHost();
            if (u.getPort() > 0) b.dbPort = u.getPort();
            if (u.getUserInfo() != null) {
                String[] ui = u.getUserInfo().split(":", 2);
                if (ui.length == 2) {
                    b.dbUser = decode(ui[0]);
                    b.dbPassword = decode(ui[1]);
                } else {
                    b.dbUser = decode(ui[0]);
                }
            }
            if (u.getPath() != null && u.getPath().length() > 1) {
                b.dbDatabase = decode(u.getPath().substring(1));
            }
            for (String kv : (u.getQuery() == null ? "" : u.getQuery()).split("&")) {
                String[] p = kv.split("=", 2);
                if (p.length == 2 && p[0].equals("sslmode")) {
                    b.dbSsl = p[1].equals("require") || p[1].equals("verify-ca") || p[1].equals("verify-full");
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + e.getMessage(), e);
        }
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public String databaseUrl() {
        return "postgresql://" + dbUser + ":***@" + dbHost + ":" + dbPort + "/" + dbDatabase + (dbSsl ? "?sslmode=require" : "");
    }

    private static String env(String k) {
        String v = System.getenv(k);
        if (v != null && !v.isBlank()) return v;
        String p = System.getProperty(k);
        return (p != null && !p.isBlank()) ? p : null;
    }

    private static int envInt(String k, int dflt) {
        String v = env(k);
        if (v == null) return dflt;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return dflt; }
    }

    private static final class Builder {
        int port = envInt("PORT", 3030);
        String home = env("APEX_HOME") != null ? env("APEX_HOME") : System.getProperty("user.dir");
        String staticDir;
        String uploadsDir;
        String adminUsername = "admin";
        String adminPassword;
        int sessionHours = envInt("APEX_SESSION_HOURS", 24);
        int dbPoolSize = envInt("APEX_DB_POOL", 5);
        String dbHost = "localhost";
        int dbPort = 5432;
        String dbUser = "apex";
        String dbPassword = "";
        String dbDatabase = "apex";
        boolean dbSsl = false;

        Builder() {
            adminUsername = env("APEX_ADMIN_USER") != null ? env("APEX_ADMIN_USER") : "admin";
            adminPassword = env("APEX_ADMIN_PASS") != null ? env("APEX_ADMIN_PASS") : "admin1234";
            staticDir = env("APEX_STATIC") != null ? env("APEX_STATIC") : new java.io.File(home, "../frontend").getAbsolutePath();
            uploadsDir = env("APEX_UPLOADS") != null ? env("APEX_UPLOADS") : new java.io.File(home, "uploads").getAbsolutePath();
        }

        AppConfig build() {
            return new AppConfig(this);
        }
    }
}
