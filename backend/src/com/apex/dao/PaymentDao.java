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
 * Online payment submissions with receipt + TID verification workflow:
 * Pending -> Verified / Rejected / Reupload requested.
 */
public class PaymentDao extends BaseDao {

    public PaymentDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        List<JsonObject> out = new ArrayList<>();
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id,booking_id,method,amount,tid,receipt_doc,status,submitted_at,reviewed_at,note FROM payments ORDER BY id DESC")) {
            while (rs.next()) out.add(row(rs));
        }
        return out;
    }

    public JsonObject find(long id) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,booking_id,method,amount,tid,receipt_doc,status,submitted_at,reviewed_at,note FROM payments WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs) : null;
            }
        }
    }

    public long insert(String bookingId, String method, long amount, String tid, Integer receiptDoc, String status) throws SQLException {
        String sql = "INSERT INTO payments(booking_id,method,amount,tid,receipt_doc,status,submitted_at,note) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            ps.setString(1, bookingId);
            ps.setString(2, method);
            ps.setLong(3, amount);
            ps.setString(4, tid);
            if (receiptDoc == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, receiptDoc);
            ps.setString(6, status == null ? "Pending Verification" : status);
            ps.setString(7, Instant.now().toString());
            ps.setString(8, "");
            ps.executeUpdate();
        }
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery(db.isMysql() ? "SELECT LAST_INSERT_ID()" : "SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public void review(long id, String status, String note) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "UPDATE payments SET status=?, note=?, reviewed_at=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setString(2, note);
            ps.setString(3, Instant.now().toString());
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    private JsonObject row(ResultSet rs) throws SQLException {
        JsonObject o = Json.obj();
        o.addProperty("id", rs.getLong(1));
        o.addProperty("bookingId", rs.getString(2));
        o.addProperty("method", rs.getString(3));
        o.addProperty("amount", rs.getLong(4));
        o.addProperty("tid", rs.getString(5));
        int doc = rs.getInt(6);
        o.addProperty("receiptDoc", rs.wasNull() ? null : doc);
        o.addProperty("status", rs.getString(7));
        o.addProperty("submittedAt", rs.getString(8));
        o.addProperty("reviewedAt", rs.getString(9));
        o.addProperty("note", rs.getString(10));
        return o;
    }
}
