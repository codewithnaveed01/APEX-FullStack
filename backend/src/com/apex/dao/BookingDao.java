package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Rental reservations + vehicle line items. Totals, payment/verification
 * status, pickup/return timestamps and late charges are real columns so the
 * admin console and statistics are plain SQL.
 */
public class BookingDao extends BaseDao {

    public BookingDao(Database db) { super(db); }

    public List<JsonObject> all() throws SQLException {
        return selectJson("SELECT json FROM bookings ORDER BY created_at DESC");
    }

    public void replace(List<JsonObject> bookings) throws SQLException {
        exec("DELETE FROM booking_items");
        exec("DELETE FROM bookings");
        String sql = "INSERT INTO bookings(id,user_id,customer_name,email,phone,destination,pickup_mode," +
                "payment,status,payment_status,rental,deposit,home_delivery,total,paid,cancellation_fee," +
                "start_dt,end_dt,pickup_at,actual_return,extra_hours,extra_charges,final_amount,created_at,json) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String itemSql = "INSERT INTO booking_items(booking_id,pos,car_id,start_date,end_date,start_dt,end_dt,city,service,assigned_driver,json) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql);
             PreparedStatement ips = db.conn().prepareStatement(itemSql)) {
            for (JsonObject o : bookings) {
                JsonObject t = o.has("totals") && o.get("totals").isJsonObject() ? o.getAsJsonObject("totals") : new JsonObject();
                ps.setString(1, Json.idOf(o));
                ps.setString(2, Json.str(o, "userId"));
                ps.setString(3, Json.str(o, "name"));
                ps.setString(4, Json.str(o, "email"));
                ps.setString(5, Json.str(o, "phone"));
                ps.setString(6, Json.str(o, "destination"));
                ps.setString(7, Json.str(o, "pickupMode"));
                ps.setString(8, Json.str(o, "payment"));
                ps.setString(9, Json.str(o, "status"));
                ps.setString(10, Json.str(o, "paymentStatus"));
                ps.setObject(11, num(t, "rental"));
                ps.setObject(12, num(t, "deposit"));
                ps.setObject(13, num(t, "homeDelivery"));
                ps.setObject(14, num(t, "total"));
                ps.setObject(15, num(o, "paid"));
                ps.setObject(16, num(o, "cancellationFee"));
                ps.setString(17, Json.str(o, "startDt"));
                ps.setString(18, Json.str(o, "endDt"));
                ps.setString(19, Json.str(o, "pickupAt"));
                ps.setString(20, Json.str(o, "actualReturn"));
                ps.setObject(21, num(o, "extraHours"));
                ps.setObject(22, num(o, "extraCharges"));
                ps.setObject(23, num(o, "finalAmount"));
                ps.setString(24, Json.str(o, "created"));
                ps.setString(25, o.toString());
                ps.addBatch();

                JsonElement itemsEl = o.get("items");
                if (itemsEl != null && itemsEl.isJsonArray()) {
                    JsonArray items = itemsEl.getAsJsonArray();
                    for (int i = 0; i < items.size(); i++) {
                        JsonElement it = items.get(i);
                        if (!it.isJsonObject()) continue;
                        JsonObject item = it.getAsJsonObject();
                        ips.setString(1, Json.idOf(o));
                        ips.setInt(2, i);
                        ips.setObject(3, num(item, "carId"));
                        ips.setString(4, Json.str(item, "start"));
                        ips.setString(5, Json.str(item, "end"));
                        ips.setString(6, Json.str(item, "startDt"));
                        ips.setString(7, Json.str(item, "endDt"));
                        ips.setString(8, Json.str(item, "city"));
                        ips.setString(9, Json.str(item, "service"));
                        ips.setObject(10, num(item, "assignedDriver"));
                        ips.setString(11, item.toString());
                        ips.addBatch();
                    }
                }
            }
            ps.executeBatch();
            ips.executeBatch();
        }
    }

    /** Insert-or-replace ONE booking (+its items) without touching other rows. */
    public void upsert(JsonObject o) throws SQLException {
        String id = Json.idOf(o);
        exec("DELETE FROM booking_items WHERE booking_id='" + id.replace("'", "''") + "'");
        try (PreparedStatement dp = db.conn().prepareStatement("DELETE FROM bookings WHERE id=?")) {
            dp.setString(1, id);
            dp.executeUpdate();
        }
        String sql = "INSERT INTO bookings(id,user_id,customer_name,email,phone,destination,pickup_mode," +
                "payment,status,payment_status,rental,deposit,home_delivery,total,paid,cancellation_fee," +
                "start_dt,end_dt,pickup_at,actual_return,extra_hours,extra_charges,final_amount,created_at,json) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String itemSql = "INSERT INTO booking_items(booking_id,pos,car_id,start_date,end_date,start_dt,end_dt,city,service,assigned_driver,json) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.conn().prepareStatement(sql);
             PreparedStatement ips = db.conn().prepareStatement(itemSql)) {
            insertOne(ps, ips, o);
            ps.executeBatch();
            ips.executeBatch();
        }
    }

    private void insertOne(PreparedStatement ps, PreparedStatement ips, JsonObject o) throws SQLException {
        JsonObject t = o.has("totals") && o.get("totals").isJsonObject() ? o.getAsJsonObject("totals") : new JsonObject();
        ps.setString(1, Json.idOf(o));
        ps.setString(2, Json.str(o, "userId"));
        ps.setString(3, Json.str(o, "name"));
        ps.setString(4, Json.str(o, "email"));
        ps.setString(5, Json.str(o, "phone"));
        ps.setString(6, Json.str(o, "destination"));
        ps.setString(7, Json.str(o, "pickupMode"));
        ps.setString(8, Json.str(o, "payment"));
        ps.setString(9, Json.str(o, "status"));
        ps.setString(10, Json.str(o, "paymentStatus"));
        ps.setObject(11, num(t, "rental"));
        ps.setObject(12, num(t, "deposit"));
        ps.setObject(13, num(t, "homeDelivery"));
        ps.setObject(14, num(t, "total"));
        ps.setObject(15, num(o, "paid"));
        ps.setObject(16, num(o, "cancellationFee"));
        ps.setString(17, Json.str(o, "startDt"));
        ps.setString(18, Json.str(o, "endDt"));
        ps.setString(19, Json.str(o, "pickupAt"));
        ps.setString(20, Json.str(o, "actualReturn"));
        ps.setObject(21, num(o, "extraHours"));
        ps.setObject(22, num(o, "extraCharges"));
        ps.setObject(23, num(o, "finalAmount"));
        ps.setString(24, Json.str(o, "created"));
        ps.setString(25, o.toString());
        ps.addBatch();

        JsonElement itemsEl = o.get("items");
        if (itemsEl != null && itemsEl.isJsonArray()) {
            JsonArray items = itemsEl.getAsJsonArray();
            for (int i = 0; i < items.size(); i++) {
                JsonElement it = items.get(i);
                if (!it.isJsonObject()) continue;
                JsonObject item = it.getAsJsonObject();
                ips.setString(1, Json.idOf(o));
                ips.setInt(2, i);
                ips.setObject(3, num(item, "carId"));
                ips.setString(4, Json.str(item, "start"));
                ips.setString(5, Json.str(item, "end"));
                ips.setString(6, Json.str(item, "startDt"));
                ips.setString(7, Json.str(item, "endDt"));
                ips.setString(8, Json.str(item, "city"));
                ips.setString(9, Json.str(item, "service"));
                ips.setObject(10, num(item, "assignedDriver"));
                ips.setString(11, item.toString());
                ips.addBatch();
            }
        }
    }

    private static Integer num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsInt();
    }
}
