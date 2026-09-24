package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.Schema;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Key/value settings store - holds the company & payment config document. */
public class SettingsDao extends BaseDao {

    public SettingsDao(Database db) { super(db); }

    public String get(String key) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(Schema.settingsSelect(db.isMysql()))) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void set(String key, String value) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(Schema.settingsUpsert(db.isMysql()))) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
