package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.User;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;

public final class UserRepo {

    private final Database db;
    private static final String COLS =
            "id, username, email, phone, name, cnic, role, password_hash, data, created_at";

    public UserRepo(Database db) { this.db = db; }

    public List<User> listAll() {
        return db.with(c -> all(c));
    }

    public List<User> all(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM users ORDER BY created_at", null);
        List<User> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(User.fromRow(r, i));
        return out;
    }

    public User get(PgConnection c, String id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM users WHERE id = $1", new String[]{id});
        return r.rowCount() > 0 ? User.fromRow(r, 0) : null;
    }

    public User get(String id) {
        return db.with(c -> get(c, id));
    }

    /** Locks this account while its credentials are checked and rotated. */
    public User getForUpdate(PgConnection c, String id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM users WHERE id = $1 FOR UPDATE",
                new String[]{id});
        return r.rowCount() > 0 ? User.fromRow(r, 0) : null;
    }

    /** Seeding must look for an admin by role, not by the original username. */
    public User findAdmin(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM users WHERE role = 'admin'" +
                " ORDER BY created_at LIMIT 1", null);
        return r.rowCount() > 0 ? User.fromRow(r, 0) : null;
    }

    /** Usernames cannot also match another user's email (both work for sign-in). */
    public boolean loginNameTaken(PgConnection c, String id, String username) {
        QueryResult r = c.query("SELECT 1 FROM users WHERE id <> $1" +
                " AND (lower(username) = lower($2) OR lower(email) = lower($2)) LIMIT 1",
                new String[]{id, username});
        return r.rowCount() > 0;
    }

    public User updateAdminCredentials(PgConnection c, String id, String username, String hash) {
        QueryResult r = c.query("UPDATE users SET username = $1, password_hash = $2," +
                " data = (data - 'password' - 'passwordHash' - 'password_hash' - 'pass')" +
                " || jsonb_build_object('username', $1::text), updated_at = now()" +
                " WHERE id = $3 AND role = 'admin' RETURNING id",
                new String[]{username, hash, id});
        if (r.rowCount() == 0) throw ApiException.forbidden("Admin account not found");
        return get(c, id);
    }

    public User findByLogin(PgConnection c, String identifier) {
        return lookupByLogin(c, identifier, false);
    }

    /** Use inside a transaction so credential rotation cannot race a login. */
    public User findByLoginForUpdate(PgConnection c, String identifier) {
        return lookupByLogin(c, identifier, true);
    }

    private User lookupByLogin(PgConnection c, String identifier, boolean lock) {
        String idn = Json.clean(identifier).toLowerCase();
        QueryResult r = c.query("SELECT " + COLS +
                " FROM users WHERE lower(username) = $1 OR lower(email) = $1" +
                (lock ? " FOR UPDATE" : ""), new String[]{idn});
        return r.rowCount() > 0 ? User.fromRow(r, 0) : null;
    }

    public void insert(PgConnection c, User u, String hash) {
        c.query("INSERT INTO users(id, username, email, phone, name, cnic, role, password_hash, data)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)" +
                " ON CONFLICT (id) DO UPDATE SET" +
                " username=EXCLUDED.username, email=EXCLUDED.email, phone=EXCLUDED.phone," +
                " name=EXCLUDED.name, cnic=EXCLUDED.cnic, data=EXCLUDED.data," +
                " password_hash=CASE WHEN EXCLUDED.password_hash IS NOT NULL THEN EXCLUDED.password_hash ELSE users.password_hash END," +
                " updated_at=now()",
                new String[]{u.id, u.username, u.email, orEmpty(u.phone), orEmpty(u.name),
                        orEmpty(u.cnic), u.role, hash, withoutSecrets(u.data).toString()});
    }

    /** Upsert used by sync: never overwrites the stored password hash. */
    public void upsertFromSync(PgConnection c, JsonObject data) {
        String id = Json.getStr(data, "id", "");
        String username = Json.getStr(data, "username", "");
        String email = Json.getStr(data, "email", "");
        if (id.isEmpty() || username.isEmpty()) {
            throw ApiException.validation("Sync users[] entries need id and username");
        }
        User existing = get(c, id);
        // Generic state sync must never reset/rename an admin account. The
        // authenticated Settings endpoint is the only way to change it.
        if (existing != null && existing.isAdmin()) return;
        if ("admin".equals(Json.getStr(data, "role", "customer"))) {
            throw ApiException.forbidden("Admin login details can only be edited in Settings");
        }
        JsonObject clean = withoutSecrets(data);
        String hash = null;
        String pw = Json.getStr(data, "password", "");
        if (!pw.isEmpty()) {
            hash = BCrypt.hashpw(pw, BCrypt.gensalt(10));
        }
        if (email.isEmpty()) email = "unknown-" + id + "@apex.local";
        c.query("INSERT INTO users(id, username, email, phone, name, cnic, role, password_hash, data)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)" +
                " ON CONFLICT (id) DO UPDATE SET" +
                " username=EXCLUDED.username, email=EXCLUDED.email, phone=EXCLUDED.phone," +
                " name=EXCLUDED.name, cnic=EXCLUDED.cnic, data=EXCLUDED.data," +
                " password_hash=CASE WHEN EXCLUDED.password_hash IS NOT NULL THEN EXCLUDED.password_hash ELSE users.password_hash END," +
                " updated_at=now()",
                new String[]{id, username, email,
                        Json.getStr(data, "phone", ""), Json.getStr(data, "name", ""),
                        Json.getStr(data, "cnic", ""),
                        Json.getStr(data, "role", "customer").equals("admin") ? "admin" : "customer",
                        hash, clean.toString()});
    }

    private static JsonObject withoutSecrets(JsonObject data) {
        JsonObject clean = data == null ? new JsonObject() : data.deepCopy();
        for (String key : new String[]{"password", "passwordHash", "password_hash", "pass"}) {
            clean.remove(key);
        }
        return clean;
    }

    private static boolean isBcrypt(String hash) {
        return hash != null && hash.matches("^\\$2[abxy]\\$[0-3][0-9]\\$[./A-Za-z0-9]{53}$");
    }

    /**
     * One-time cleanup for old installations which may have kept a plaintext
     * password in password_hash or inside the legacy user JSON. It is safe to
     * repeat on every startup: a valid BCrypt hash is never hashed twice.
     */
    public int hardenExistingPasswords() {
        return db.tx(c -> {
            QueryResult rows = c.query("SELECT id, password_hash, data FROM users FOR UPDATE", null);
            int changed = 0;
            for (int i = 0; i < rows.rowCount(); i++) {
                String id = rows.col("id", i);
                String stored = rows.col("password_hash", i);
                JsonObject data;
                try {
                    data = JsonParser.parseString(rows.col("data", i)).getAsJsonObject();
                } catch (RuntimeException e) {
                    data = new JsonObject();
                }
                boolean hasLegacySecret = data.has("password") || data.has("passwordHash") ||
                        data.has("password_hash") || data.has("pass");
                String replacement = null;
                if (stored != null && !stored.isBlank() && !isBcrypt(stored)) {
                    replacement = BCrypt.hashpw(stored, BCrypt.gensalt(10));
                } else if (stored == null || stored.isBlank()) {
                    String legacy = Json.getStr(data, "password", "");
                    if (legacy.isEmpty()) legacy = Json.getStr(data, "pass", "");
                    if (legacy.isEmpty()) legacy = Json.getStr(data, "passwordHash", "");
                    if (legacy.isEmpty()) legacy = Json.getStr(data, "password_hash", "");
                    if (!legacy.isEmpty()) {
                        replacement = isBcrypt(legacy) ? legacy : BCrypt.hashpw(legacy, BCrypt.gensalt(10));
                    }
                }
                if (hasLegacySecret || replacement != null) {
                    c.query("UPDATE users SET password_hash = COALESCE($1, password_hash)," +
                            " data = $2::jsonb, updated_at = now() WHERE id = $3",
                            new String[]{replacement, withoutSecrets(data).toString(), id});
                    changed++;
                }
            }
            return changed;
        });
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM users", null));
        return Long.parseLong(r.first());
    }

    public void remove(PgConnection c, String id) {
        c.query("DELETE FROM users WHERE id = $1", new String[]{id});
        c.query("DELETE FROM sessions WHERE user_id = $1", new String[]{id});
    }

    public static String orEmpty(String s) { return s == null ? "" : s; }
}
