package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Persistence for the vehicle fleet (normalized columns + raw JSON). */
public class CarDao extends BaseDao {

    public CarDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM cars ORDER BY id");
    }

    public void replace(List<JsonObject> cars) throws SQLException {
        exec("DELETE FROM cars");
        String sql = "INSERT INTO cars(id,name,brand,category,origin,image,year,rate,hourly_rate,deposit,seats," +
                "engine,power,fuel,km,color,plate,car_condition,status,market_note,owner_id,json) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (JsonObject c : cars) {
                ps.setInt(1, Json.intVal(c, "id", 0));
                ps.setString(2, Json.str(c, "name"));
                ps.setString(3, Json.str(c, "brand"));
                ps.setString(4, Json.str(c, "category"));
                ps.setString(5, Json.str(c, "origin"));
                ps.setString(6, Json.str(c, "image"));
                ps.setObject(7, num(c, "year"));
                ps.setObject(8, num(c, "rate"));
                ps.setObject(9, num(c, "hourlyRate"));
                ps.setObject(10, num(c, "deposit"));
                ps.setObject(11, num(c, "seats"));
                ps.setString(12, Json.str(c, "engine"));
                ps.setString(13, Json.str(c, "power"));
                ps.setString(14, Json.str(c, "fuel"));
                ps.setString(15, Json.str(c, "km"));
                ps.setString(16, Json.str(c, "color"));
                ps.setString(17, Json.str(c, "plate"));
                ps.setString(18, Json.str(c, "condition"));
                ps.setString(19, Json.str(c, "status"));
                ps.setString(20, Json.str(c, "marketNote"));
                ps.setString(21, Json.str(c, "ownerId"));
                ps.setString(22, c.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Integer num(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : null;
    }
}
