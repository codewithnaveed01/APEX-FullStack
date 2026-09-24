package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Persistence for in-app notifications. */
public class NotificationDao extends BaseDao {

    public NotificationDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM notifications ORDER BY time DESC");
    }

    /** Server-generated notification (frontend shape: userId/title/msg/time/read/link). */
    public void insert(JsonObject n) throws SQLException {
        String sql = db.isMysql()
                ? "INSERT IGNORE INTO notifications(id,user_id,title,msg,time,read_flag,json) VALUES (?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO notifications(id,user_id,title,msg,time,read_flag,json) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            ps.setString(1, Json.idOf(n));
            ps.setString(2, Json.str(n, "userId"));
            ps.setString(3, Json.str(n, "title"));
            ps.setString(4, Json.str(n, "msg"));
            ps.setString(5, Json.str(n, "time"));
            ps.setInt(6, Json.boolVal(n, "read", false) ? 1 : 0);
            ps.setString(7, n.toString());
            ps.executeUpdate();
        }
    }

    public int unreadCount(String userId) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_flag=0")) {
            ps.setString(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void replace(List<JsonObject> notifs) throws SQLException {
        exec("DELETE FROM notifications");
        String sql = "INSERT INTO notifications(id,user_id,title,msg,time,read_flag,json) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (JsonObject n : notifs) {
                ps.setString(1, Json.idOf(n));
                ps.setString(2, Json.str(n, "userId"));
                ps.setString(3, Json.str(n, "title"));
                ps.setString(4, Json.str(n, "msg"));
                ps.setString(5, Json.str(n, "time"));
                ps.setInt(6, Json.boolVal(n, "read", false) ? 1 : 0);
                ps.setString(7, n.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
