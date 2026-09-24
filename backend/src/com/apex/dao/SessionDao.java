package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Bearer-token sessions for the operations (admin) API. */
public class SessionDao extends BaseDao {

    public static final long HOURS_VALID = 12;

    public SessionDao(Database db) { super(db); }

    public void create(String token, String username, String role) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO sessions(token, role, username, created_at, expires_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, token);
            ps.setString(2, role);
            ps.setString(3, username);
            ps.setString(4, Instant.now().toString());
            ps.setString(5, Instant.now().plus(HOURS_VALID, ChronoUnit.HOURS).toString());
            ps.executeUpdate();
        }
    }

    /** Returns the session row when the token exists and is not expired. */
    public JsonObject find(String token) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT token, role, username, expires_at FROM sessions WHERE token=? AND expires_at > ?")) {
            ps.setString(1, token);
            ps.setString(2, Instant.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                JsonObject o = Json.obj();
                o.addProperty("token", rs.getString(1));
                o.addProperty("role", rs.getString(2));
                o.addProperty("username", rs.getString(3));
                o.addProperty("expires", rs.getString(4));
                return o;
            }
        }
    }

    public void delete(String token) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("DELETE FROM sessions WHERE token=?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    public void purgeExpired() throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
            ps.setString(1, Instant.now().toString());
            ps.executeUpdate();
        }
    }
}
