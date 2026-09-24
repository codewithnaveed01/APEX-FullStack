package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.User;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonObject;

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

    public User findByLogin(PgConnection c, String identifier) {
        String idn = Json.clean(identifier).toLowerCase();
        QueryResult r = c.query("SELECT " + COLS + " FROM users WHERE lower(username) = $1 OR lower(email) = $1",
                new String[]{idn});
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
                        orEmpty(u.cnic), u.role, hash, u.data.toString()});
    }

    /** Upsert used by sync: never overwrites the stored password hash. */
    public void upsertFromSync(PgConnection c, JsonObject data, String plainPassword) {
        String id = Json.getStr(data, "id", "");
        String username = Json.getStr(data, "username", "");
        String email = Json.getStr(data, "email", "");
        if (id.isEmpty() || username.isEmpty()) {
            throw ApiException.validation("Sync users[] entries need id and username");
        }
        JsonObject clean = data.deepCopy();
        clean.remove("password");
        String hash = null;
        String pw = Json.getStr(data, "password", "");
        if (!pw.isEmpty()) {
            hash = org.mindrot.jbcrypt.BCrypt.hashpw(pw, org.mindrot.jbcrypt.BCrypt.gensalt(10));
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
