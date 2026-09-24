package com.apex.service;

import com.apex.config.AppConfig;
import com.apex.dao.SessionDao;
import com.apex.dao.UserDao;
import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Authentication for BOTH roles:
 *  - admin    : env-configured credentials (APEX_ADMIN_USER / APEX_ADMIN_PASS)
 *  - customer : server-side accounts with salted SHA-256 password hashes
 * Tokens carry the role; the router enforces ADMIN vs ANY access per route.
 */
public class AuthService {

    private final Database db;
    private final SessionDao sessions;
    private final UserDao users;
    private final AppConfig cfg;

    public AuthService(Database db, SessionDao sessions, UserDao users, AppConfig cfg) {
        this.db = db;
        this.sessions = sessions;
        this.users = users;
        this.cfg = cfg;
    }

    public JsonObject login(String username, String password) throws SQLException {
        return db.tx(c -> {
            sessions.purgeExpired();
            if (username == null || password == null) return null;
            if (cfg.getAdminUsername().equals(username) && cfg.getAdminPassword().equals(password)) {
                return issue(username, "admin");
            }
            JsonObject u = users.findByLogin(username);
            if (u == null || u.get("passwordHash") == null) return null;
            String hash = hash(password, u.get("salt").getAsString());
            if (!hash.equals(u.get("passwordHash").getAsString())) return null;
            JsonObject out = issue(u.get("username").getAsString(), "customer");
            out.add("user", publicUser(u));
            return out;
        });
    }

    /** Creates a customer account with a salted hash; returns token + user. */
    public JsonObject register(JsonObject b) throws SQLException {
        String username = Json.str(b, "username", "").trim();
        String email = Json.str(b, "email", "").trim().toLowerCase();
        String name = Json.str(b, "name", "").trim();
        String phone = Json.str(b, "phone", "").trim();
        String password = Json.str(b, "password", "");
        if (username.length() < 3) throw new IllegalArgumentException("Username must be at least 3 characters");
        if (!email.contains("@")) throw new IllegalArgumentException("Valid email is required");
        if (name.length() < 2) throw new IllegalArgumentException("Full name is required");
        if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");
        return db.tx(c -> {
            if (users.findByLogin(username) != null) throw new IllegalArgumentException("Username or email already registered");
            String salt = UUID.randomUUID().toString().substring(0, 12);
            JsonObject u = Json.obj();
            u.addProperty("id", "C" + System.currentTimeMillis());
            u.addProperty("username", username);
            u.addProperty("email", email);
            u.addProperty("name", name);
            u.addProperty("phone", phone);
            u.addProperty("role", "customer");
            u.addProperty("created", java.time.Instant.now().toString());
            users.insertServer(u, hash(password, salt), salt);
            JsonObject out = issue(username, "customer");
            out.add("user", publicUser(u));
            return out;
        });
    }

    public JsonObject validate(String token) throws SQLException {
        if (token == null || token.isBlank()) return null;
        return db.with(c -> sessions.find(token));
    }

    public void logout(String token) throws SQLException {
        db.tx(c -> { sessions.delete(token); return null; });
    }

    private JsonObject issue(String username, String role) throws SQLException {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().substring(0, 8);
        sessions.create(token, username, role);
        JsonObject out = Json.obj();
        out.addProperty("token", token);
        out.addProperty("role", role);
        out.addProperty("username", username);
        out.addProperty("validHours", SessionDao.HOURS_VALID);
        return out;
    }

    private JsonObject publicUser(JsonObject u) {
        JsonObject p = Json.obj();
        p.addProperty("id", Json.str(u, "id"));
        p.addProperty("username", Json.str(u, "username"));
        p.addProperty("email", Json.str(u, "email"));
        p.addProperty("name", Json.str(u, "name"));
        p.addProperty("phone", Json.str(u, "phone"));
        return p;
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
