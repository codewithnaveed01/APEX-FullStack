package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Persistence for support chat threads. */
public class ChatDao extends BaseDao {

    public ChatDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM chats ORDER BY last_time DESC");
    }

    /** Insert-or-replace ONE chat thread. */
    public void upsertThread(JsonObject chat) throws SQLException {
        String uid = Json.str(chat, "userId", "");
        try (PreparedStatement dp = db.conn().prepareStatement("DELETE FROM chats WHERE user_id=?")) {
            dp.setString(1, uid);
            dp.executeUpdate();
        }
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO chats(user_id,user_name,last_time,json) VALUES (?,?,?,?)")) {
            ps.setString(1, uid);
            ps.setString(2, Json.str(chat, "userName"));
            ps.setString(3, Json.str(chat, "lastTime"));
            ps.setString(4, chat.toString());
            ps.executeUpdate();
        }
    }

    public JsonObject findByUser(String userId) throws SQLException {
        for (JsonObject c : all()) if (userId.equals(Json.str(c, "userId"))) return c;
        return null;
    }

    public void replace(List<JsonObject> chats) throws SQLException {
        exec("DELETE FROM chats");
        String sql = "INSERT INTO chats(user_id,user_name,last_time,json) VALUES (?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (JsonObject c : chats) {
                ps.setString(1, Json.str(c, "userId"));
                ps.setString(2, Json.str(c, "userName"));
                ps.setString(3, Json.str(c, "lastTime"));
                ps.setString(4, c.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
