package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Uploaded sensitive documents (CNIC front/back, licence, payment receipts).
 * Files live in backend/uploads and are ONLY served through the
 * authenticated /api/documents/{id} endpoint - never statically.
 */
public class DocumentDao extends BaseDao {

    public DocumentDao(Database db) { super(db); }

    public long insert(String ownerType, String ownerId, String kind, String path, String contentType, byte[] content) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO documents(owner_type,owner_id,kind,path,content_type,content,status,created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, ownerType);
            ps.setString(2, ownerId);
            ps.setString(3, kind);
            ps.setString(4, path);
            ps.setString(5, contentType);
            ps.setBytes(6, content);
            ps.setString(7, "Pending");
            ps.setString(8, Instant.now().toString());
            ps.executeUpdate();
        }
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery(db.isMysql() ? "SELECT LAST_INSERT_ID()" : "SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public JsonObject find(long id) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,owner_type,owner_id,kind,path,content_type,content,status,created_at FROM documents WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                JsonObject o = Json.obj();
                o.addProperty("id", rs.getLong(1));
                o.addProperty("ownerType", rs.getString(2));
                o.addProperty("ownerId", rs.getString(3));
                o.addProperty("kind", rs.getString(4));
                o.addProperty("path", rs.getString(5));
                o.addProperty("contentType", rs.getString(6));
                byte[] content = rs.getBytes(7);
                if (content != null) {
                    o.addProperty("data", java.util.Base64.getEncoder().encodeToString(content));
                }
                o.addProperty("status", rs.getString(8));
                o.addProperty("created", rs.getString(9));
                return o;
            }
        }
    }

    public void updateStatus(long id, String status) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("UPDATE documents SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public List<JsonObject> all() throws SQLException {
        List<JsonObject> out = new ArrayList<>();
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id,owner_type,owner_id,kind,path,content_type,status,created_at FROM documents ORDER BY id DESC")) {
            while (rs.next()) {
                JsonObject o = Json.obj();
                o.addProperty("id", rs.getLong(1));
                o.addProperty("ownerType", rs.getString(2));
                o.addProperty("ownerId", rs.getString(3));
                o.addProperty("kind", rs.getString(4));
                o.addProperty("path", rs.getString(5));
                o.addProperty("contentType", rs.getString(6));
                o.addProperty("status", rs.getString(7));
                o.addProperty("created", rs.getString(8));
                out.add(o);
            }
        }
        return out;
    }
}
