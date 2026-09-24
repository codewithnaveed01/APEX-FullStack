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

/** Customer reviews with admin moderation (Pending / Approved / Hidden). */
public class ReviewDao extends BaseDao {

    public ReviewDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        List<JsonObject> out = new ArrayList<>();
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT id,booking_id,car_id,user_id,rating,body,status,created_at FROM reviews ORDER BY id DESC")) {
            while (rs.next()) out.add(row(rs));
        }
        return out;
    }

    /** Approved reviews for one car (public). */
    public List<JsonObject> approvedFor(int carId) throws SQLException {
        List<JsonObject> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,booking_id,car_id,user_id,rating,body,status,created_at FROM reviews WHERE car_id=? AND status='Approved' ORDER BY id DESC")) {
            ps.setInt(1, carId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        }
        return out;
    }

    public long insert(String bookingId, int carId, String userId, int rating, String body) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO reviews(booking_id,car_id,user_id,rating,body,status,created_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, bookingId);
            ps.setInt(2, carId);
            ps.setString(3, userId);
            ps.setInt(4, rating);
            ps.setString(5, body);
            ps.setString(6, "Pending");
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        }
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery(db.isMysql() ? "SELECT LAST_INSERT_ID()" : "SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public JsonObject find(long id) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,booking_id,car_id,user_id,rating,body,status,created_at FROM reviews WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs) : null;
            }
        }
    }

    public void updateStatus(long id, String status) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("UPDATE reviews SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public boolean existsForBooking(String bookingId) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("SELECT 1 FROM reviews WHERE booking_id=?")) {
            ps.setString(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private JsonObject row(ResultSet rs) throws SQLException {
        JsonObject o = Json.obj();
        o.addProperty("id", rs.getLong(1));
        o.addProperty("bookingId", rs.getString(2));
        o.addProperty("carId", rs.getInt(3));
        o.addProperty("userId", rs.getString(4));
        o.addProperty("rating", rs.getInt(5));
        o.addProperty("body", rs.getString(6));
        o.addProperty("status", rs.getString(7));
        o.addProperty("created", rs.getString(8));
        return o;
    }
}
