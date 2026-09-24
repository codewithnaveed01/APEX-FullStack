package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Persistence for the chauffeur roster. */
public class DriverDao extends BaseDao {

    public DriverDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM drivers ORDER BY id");
    }

    public void replace(List<JsonObject> drivers) throws SQLException {
        exec("DELETE FROM drivers");
        String sql = "INSERT INTO drivers(id,name,phone,city,experience,license,active,json) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (JsonObject d : drivers) {
                ps.setLong(1, Json.longVal(d, "id", 0));
                ps.setString(2, Json.str(d, "name"));
                ps.setString(3, Json.str(d, "phone"));
                ps.setString(4, Json.str(d, "city"));
                ps.setObject(5, d.has("experience") && !d.get("experience").isJsonNull() ? d.get("experience").getAsInt() : null);
                ps.setString(6, Json.str(d, "license"));
                ps.setInt(7, Json.boolVal(d, "active", true) ? 1 : 0);
                ps.setString(8, d.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
