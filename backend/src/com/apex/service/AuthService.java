package com.apex.service;

import com.apex.dao.SessionRepo;
import com.apex.dao.UserRepo;
import com.apex.db.Database;
import com.apex.model.User;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.google.gson.JsonObject;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Authentication: BCrypt password checks + opaque server-side sessions.
 * The admin account is seeded exactly once at startup (SeedService).
 */
public final class AuthService {

    private final Database db;
    private final UserRepo users;
    private final SessionRepo sessions;
    private final int sessionHours;
    private final SecureRandom random = new SecureRandom();

    public AuthService(Database db, UserRepo users, SessionRepo sessions, int sessionHours) {
        this.db = db;
        this.users = users;
        this.sessions = sessions;
        this.sessionHours = sessionHours;
    }

    /** Returns the session JSON or null when credentials are wrong. */
    public JsonObject login(String identifier, String password) {
        String idn = Json.clean(identifier);
        String pw = password == null ? "" : password;
        if (idn.isEmpty() || pw.isEmpty()) return null;
        return db.with(c -> {
            User u = users.findByLogin(c, idn);
            if (u == null) return null;
            if (u.passwordHash == null || u.passwordHash.isEmpty() || !BCrypt.checkpw(pw, u.passwordHash)) {
                return null;
            }
            return issue(c, u);
        });
    }

    public JsonObject register(JsonObject body) {
        String username = Validation.required(Json.getStr(body, "username", ""), "username", 3, 30);
        if (!username.matches("^[A-Za-z0-9_.-]+$")) {
            throw ApiException.validation("Username may only contain letters, numbers, . _ -");
        }
        String email = Validation.required(Json.getStr(body, "email", ""), "email", 5, 80);
        if (!Validation.email(email)) throw ApiException.validation("Invalid email address");
        String name = Validation.required(Json.getStr(body, "name", ""), "name", 2, 60);
        String phone = Validation.phone(Json.getStr(body, "phone", ""));
        String password = Json.getStr(body, "password", "");
        if (password.length() < 6) throw ApiException.validation("Password must be at least 6 characters");
        String cnic = Json.getStr(body, "cnic", "");
        if (!cnic.isEmpty()) Validation.cnic(cnic);

        final String fEmail = email.toLowerCase();
        final String fUsername = username;
        final String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));

        return db.tx(c -> {
            QueryResultDup guard = dupCheck(c, fUsername, fEmail);
            if (guard.usernameTaken) throw ApiException.conflict("Username is already taken");
            if (guard.emailTaken) throw ApiException.conflict("An account with this email already exists");
            String id = "U" + Long.toString(System.currentTimeMillis() / 1000, 36).toUpperCase()
                    + Integer.toString(random.nextInt(46656), 36).toUpperCase();
            JsonObject data = new JsonObject();
            data.addProperty("id", id);
            data.addProperty("username", fUsername);
            data.addProperty("email", fEmail);
            data.addProperty("name", name);
            data.addProperty("phone", phone);
            data.addProperty("cnic", cnic);
            data.addProperty("role", "customer");
            User u = new User(id, fUsername, fEmail, phone, name, cnic, "customer", hash,
                    nowIso(), data);
            users.insert(c, u, hash);
            return issue(c, u);
        });
    }

    private QueryResultDup dupCheck(com.apex.db.PgConnection c, String username, String email) {
        com.apex.db.QueryResult u = c.query("SELECT 1 FROM users WHERE lower(username) = $1",
                new String[]{username.toLowerCase()});
        com.apex.db.QueryResult e = c.query("SELECT 1 FROM users WHERE lower(email) = $1",
                new String[]{email.toLowerCase()});
        return new QueryResultDup(u.rowCount() > 0, e.rowCount() > 0);
    }

    private static final class QueryResultDup {
        final boolean usernameTaken, emailTaken;
        QueryResultDup(boolean u, boolean e) { usernameTaken = u; emailTaken = e; }
    }

    /** Issues a session on the CALLER's connection (never borrows a second one). */
    public JsonObject issue(com.apex.db.PgConnection c, User u) {
        byte[] tok = new byte[32];
        random.nextBytes(tok);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tok);
        Instant expires = Instant.now().plus(sessionHours, ChronoUnit.HOURS);
        sessions.create(c, token, u.id, u.username, u.role, expires);
        JsonObject o = new JsonObject();
        o.addProperty("token", token);
        o.addProperty("role", u.role);
        o.addProperty("username", u.username);
        o.addProperty("userId", u.id);
        o.add("user", u.publicJson());
        o.addProperty("expiresAt", expires.toString());
        return o;
    }

    /** Validates a bearer token; null when missing/expired/unknown. */
    public JsonObject validate(String token) {
        if (token == null || token.isBlank()) return null;
        return sessions.find(token.trim());
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        db.with(c -> {
            sessions.delete(c, token.trim());
            return null;
        });
    }

    public static String nowIso() {
        return Instant.now().toString();
    }
}
