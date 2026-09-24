package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Persistence for partner ("list your car") onboarding applications. */
public class ApplicationDao extends BaseDao {

    public ApplicationDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM owner_applications ORDER BY created_at DESC");
    }

    public void replace(List<JsonObject> apps) throws SQLException {
        exec("DELETE FROM owner_applications");
        String sql = "INSERT INTO owner_applications(id,user_id,brand,model,status,created_at,json) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (JsonObject a : apps) {
                ps.setString(1, Json.idOf(a));
                ps.setString(2, Json.str(a, "userId"));
                ps.setString(3, Json.str(a, "brand"));
                ps.setString(4, Json.str(a, "model"));
                ps.setString(5, Json.str(a, "status"));
                ps.setString(6, Json.str(a, "created"));
                ps.setString(7, a.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
