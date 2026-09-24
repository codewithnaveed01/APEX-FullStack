package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Opaque session tokens - logout and expiry are real, not client-side. */
public final class SessionRepo {

    private final Database db;

    public SessionRepo(Database db) { this.db = db; }

    public void create(PgConnection c, String token, String userId, String username, String role, Instant expiresAt) {
        c.query("INSERT INTO sessions(token, user_id, username, role, expires_at)" +
                " VALUES ($1,$2,$3,$4,$5) ON CONFLICT (token) DO NOTHING",
                new String[]{token, userId, username, role, fmt(expiresAt)});
    }

    /** Returns {token, userId, username, role, expiresAt} or null. */
    public JsonObject find(String token) {
        return db.with(c -> find(c, token));
    }

    public JsonObject find(PgConnection c, String token) {
        QueryResult r = c.query("SELECT token, user_id, username, role, expires_at FROM sessions" +
                " WHERE token = $1 AND expires_at > now()", new String[]{token});
        if (r.rowCount() == 0) return null;
        String[] row = r.rows.get(0);
        JsonObject o = new JsonObject();
        o.addProperty("token", row[0]);
        o.addProperty("userId", row[1]);
        o.addProperty("username", row[2]);
        o.addProperty("role", row[3]);
        o.addProperty("expiresAt", row[4]);
        return o;
    }

    public void delete(PgConnection c, String token) {
        c.query("DELETE FROM sessions WHERE token = $1", new String[]{token});
    }

    public void purgeExpired(PgConnection c) {
        c.query("DELETE FROM sessions WHERE expires_at <= now()", null);
    }

    private static String fmt(Instant i) {
        return DateTimeFormatter.ISO_INSTANT.format(i);
    }
}
