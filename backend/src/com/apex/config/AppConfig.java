package com.apex.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runtime configuration. Everything can be overridden through environment
 * variables so the same build runs on a laptop and on a VPS / container.
 *
 *  PORT          - HTTP port (default 8080)
 *  APEX_HOME     - backend working directory (db + seed file live here)
 *  APEX_DB       - explicit SQLite file path
 *  APEX_STATIC   - explicit frontend directory to serve
 *  APEX_ADMIN_USER / APEX_ADMIN_PASS - operations credentials
 */
public class AppConfig {

    private final int port;
    private final Path home;
    private final Path dbFile;
    private final Path staticDir;
    private final Path seedFile;
    private final Path uploadsDir;
    private final String adminUsername;
    private final String adminPassword;
    private final String jdbcUrl;

    public AppConfig() {
        this.port = intEnv("PORT", 3030);
        this.home = Paths.get(env("APEX_HOME", System.getProperty("apex.home", "."))).toAbsolutePath().normalize();
        this.dbFile = Paths.get(env("APEX_DB", home.resolve("apex.db").toString())).toAbsolutePath();
        this.staticDir = Paths.get(env("APEX_STATIC", home.resolveSibling("frontend").toString())).toAbsolutePath().normalize();
        this.seedFile = home.resolve("seed.json");
        this.uploadsDir = Paths.get(env("APEX_UPLOADS", home.resolve("uploads").toString())).toAbsolutePath();
        this.adminUsername = env("APEX_ADMIN_USER", "admin");
        this.adminPassword = env("APEX_ADMIN_PASS", "admin1234");
        this.jdbcUrl = resolveJdbcUrl();
    }

    /**
     * MySQL connection URL, checked in this order:
     *  1. MYSQL_URL     - Railway MySQL plugin sets this automatically
     *  2. DATABASE_URL  - Render / Heroku style (must start with mysql://)
     *  3. MYSQL_HOST / MYSQLHOST (+PORT/DB/USER/PASSWORD, both spellings)
     * Returns null -> SQLite local file.
     */
    private static String resolveJdbcUrl() {
        String u = firstNonBlank(System.getenv("MYSQL_URL"), System.getenv("DATABASE_URL"));
        if (u != null && u.startsWith("mysql://")) {
            String rest = u.substring("mysql://".length());
            String creds = "", hostdb = rest;
            if (rest.contains("@")) {
                creds = rest.substring(0, rest.indexOf('@'));
                hostdb = rest.substring(rest.indexOf('@') + 1);
            }
            String user = creds.contains(":") ? creds.substring(0, creds.indexOf(':')) : creds;
            String pass = creds.contains(":") ? creds.substring(creds.indexOf(':') + 1) : "";
            String hostPort = hostdb.contains("/") ? hostdb.substring(0, hostdb.indexOf('/')) : hostdb;
            String db = hostdb.contains("/") ? hostdb.substring(hostdb.indexOf('/') + 1) : "apex";
            if (db.contains("?")) db = db.substring(0, db.indexOf('?'));
            return "jdbc:mysql://" + hostPort + "/" + db + jdbcParams(user, pass);
        }
        String host = firstNonBlank(System.getenv("MYSQL_HOST"), System.getenv("MYSQLHOST"));
        if (host != null) {
            String user = firstNonBlank(System.getenv("MYSQL_USER"), System.getenv("MYSQLUSER"), "root");
            String pass = firstNonBlank(System.getenv("MYSQL_PASSWORD"), System.getenv("MYSQLPASSWORD"), "");
            return "jdbc:mysql://" + host + ":" + firstNonBlank(System.getenv("MYSQL_PORT"), System.getenv("MYSQLPORT"), "3306")
                    + "/" + firstNonBlank(System.getenv("MYSQL_DB"), System.getenv("MYSQLDATABASE"), System.getenv("MYSQL_DATABASE"), "apex")
                    + jdbcParams(user, pass);
        }
        return null;
    }

    /** True when any MySQL env var is present (so we never silently fall back to SQLite). */
    public static boolean mysqlConfigured() {
        String u = firstNonBlank(System.getenv("MYSQL_URL"), System.getenv("DATABASE_URL"));
        return (u != null && u.startsWith("mysql://"))
                || firstNonBlank(System.getenv("MYSQL_HOST"), System.getenv("MYSQLHOST")) != null;
    }

    /**
     * Connection flags that work against Railway/Render managed MySQL:
     *  - useSSL defaults to false (managed MySQL is reached over the private
     *    network; forcing SSL breaks hosts that do not serve it). MYSQL_SSL=true opts in.
     *  - allowPublicKeyRetrieval=true  -> caching_sha2_password over plain connections
     *  - characterEncoding=UTF-8       -> Urdu / non-ASCII names survive the round trip
     */
    private static String jdbcParams(String user, String pass) {
        String ssl = env("MYSQL_SSL", "false");
        return "?user=" + user + "&password=" + pass
                + "&useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8"
                + "&connectionTimeZone=SERVER";
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? dflt : v;
    }

    private static int intEnv(String key, int dflt) {
        try {
            return Integer.parseInt(env(key, String.valueOf(dflt)).trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public int getPort() { return port; }
    public Path getHome() { return home; }
    public Path getDbFile() { return dbFile; }
    public Path getStaticDir() { return staticDir; }
    public Path getSeedFile() { return seedFile; }
    public Path getUploadsDir() { return uploadsDir; }
    public String getAdminUsername() { return adminUsername; }
    public String getAdminPassword() { return adminPassword; }
    public String getJdbcUrl() { return jdbcUrl; }
}
