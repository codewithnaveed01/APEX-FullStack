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
     * MySQL via Railway/Render style DATABASE_URL (mysql://user:pass@host:port/db)
     * or explicit MYSQL_HOST/MYSQL_PORT/MYSQL_DB/MYSQL_USER/MYSQL_PASSWORD.
     * Returns null -> SQLite local file.
     */
    private static String resolveJdbcUrl() {
        String u = System.getenv("DATABASE_URL");
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
            return "jdbc:mysql://" + hostPort + "/" + db
                    + "?user=" + user + "&password=" + pass
                    + "&useSSL=true&serverTimezone=UTC&connectionTimeZone=SERVER";
        }
        String host = System.getenv("MYSQL_HOST");
        if (host != null && !host.isBlank()) {
            return "jdbc:mysql://" + host + ":" + env("MYSQL_PORT", "3306") + "/" + env("MYSQL_DB", "apex")
                    + "?user=" + env("MYSQL_USER", "root") + "&password=" + env("MYSQL_PASSWORD", "")
                    + "&useSSL=true&serverTimezone=UTC&connectionTimeZone=SERVER";
        }
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
