package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer / partner accounts. Password hashes live ONLY in the database -
 * they never travel to the frontend, and a frontend state sync can never
 * wipe them (replace() re-applies stored hashes by user id).
 */
public class UserDao extends BaseDao {

    public UserDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM users");
    }

    public void replace(List<JsonObject> users) throws SQLException {
        Map<String, String[]> hashes = hashes();
        exec("DELETE FROM users");
        String sql = "INSERT INTO users(id,username,email,phone,name,cnic,role,password_hash,salt,created_at,json) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (JsonObject u : users) {
                String id = Json.idOf(u);
                String[] h = hashes.get(id);
                ps.setString(1, id);
                ps.setString(2, Json.str(u, "username"));
                ps.setString(3, Json.str(u, "email"));
                ps.setString(4, Json.str(u, "phone"));
                ps.setString(5, Json.str(u, "name"));
                ps.setString(6, Json.str(u, "cnic"));
                ps.setString(7, Json.str(u, "role", "customer"));
                ps.setString(8, h != null ? h[0] : null);
                ps.setString(9, h != null ? h[1] : null);
                ps.setString(10, Json.str(u, "created"));
                ps.setString(11, u.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Map<String, String[]> hashes() throws SQLException {
        Map<String, String[]> out = new HashMap<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id, password_hash, salt FROM users WHERE password_hash IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3)});
        }
        return out;
    }

    /** Lookup by username or email (server-side login). */
    public JsonObject findByLogin(String identifier) throws SQLException {
        String q = "SELECT id, username, email, name, phone, role, password_hash, salt FROM users " +
                   "WHERE LOWER(username)=? OR LOWER(email)=? LIMIT 1";
        try (PreparedStatement ps = db.conn().prepareStatement(q)) {
            String low = identifier == null ? "" : identifier.toLowerCase();
            ps.setString(1, low);
            ps.setString(2, low);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                JsonObject o = Json.obj();
                o.addProperty("id", rs.getString(1));
                o.addProperty("username", rs.getString(2));
                o.addProperty("email", rs.getString(3));
                o.addProperty("name", rs.getString(4));
                o.addProperty("phone", rs.getString(5));
                o.addProperty("role", rs.getString(6));
                o.addProperty("passwordHash", rs.getString(7));
                o.addProperty("salt", rs.getString(8));
                return o;
            }
        }
    }

    /** Server-created account (register endpoint) - id must not exist yet. */
    public void insertServer(JsonObject u, String hash, String salt) throws SQLException {
        String sql = "INSERT INTO users(id,username,email,phone,name,cnic,role,password_hash,salt,created_at,json) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            ps.setString(1, Json.idOf(u));
            ps.setString(2, Json.str(u, "username"));
            ps.setString(3, Json.str(u, "email"));
            ps.setString(4, Json.str(u, "phone"));
            ps.setString(5, Json.str(u, "name"));
            ps.setString(6, Json.str(u, "cnic"));
            ps.setString(7, "customer");
            ps.setString(8, hash);
            ps.setString(9, salt);
            ps.setString(10, Json.str(u, "created"));
            ps.setString(11, u.toString());
            ps.executeUpdate();
        }
    }
}
