package com.apex.dao;

import com.apex.db.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Banned CNIC list - checked at checkout and manual booking. */
public class BanDao extends BaseDao {

    public BanDao(Database db) { super(db); }

    public List<String> all() throws SQLException {
        return selectStrings("SELECT cnic FROM banned_cnic ORDER BY created_at DESC");
    }

    public void add(String cnic) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(com.apex.db.Schema.banInsert(db.isMysql()))) {
            ps.setString(1, cnic);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    public void remove(String cnic) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("DELETE FROM banned_cnic WHERE cnic=?")) {
            ps.setString(1, cnic);
            ps.executeUpdate();
        }
    }

    public void replace(List<String> cnics) throws SQLException {
        exec("DELETE FROM banned_cnic");
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO banned_cnic(cnic, created_at) VALUES (?, ?)")) {
            for (String c : cnics) {
                ps.setString(1, c);
                ps.setString(2, Instant.now().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
