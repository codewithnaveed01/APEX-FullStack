package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reviews: one per completed booking, moderated (Pending/Approved/Hidden).
 * Only Approved reviews are public.
 */
public final class ReviewRepo {

    private final Database db;

    public ReviewRepo(Database db) { this.db = db; }

    public List<JsonObject> list() {
        return db.with(c -> {
            QueryResult r = c.query("SELECT id, booking_id, car_id, user_id, rating, body, status, created_at" +
                    " FROM reviews ORDER BY id DESC", null);
            List<JsonObject> out = new ArrayList<>();
            for (int i = 0; i < r.rowCount(); i++) out.add(toJson(r, i));
            return out;
        });
    }

    public List<JsonObject> approvedFor(long carId) {
        return db.with(c -> approvedFor(c, carId));
    }

    public List<JsonObject> approvedFor(PgConnection c, long carId) {
        QueryResult r = c.query("SELECT id, booking_id, car_id, user_id, rating, body, status, created_at" +
                " FROM reviews WHERE car_id = $1 AND status = 'Approved' ORDER BY created_at DESC",
                new String[]{String.valueOf(carId)});
        List<JsonObject> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(toJson(r, i));
        return out;
    }

    public JsonObject get(PgConnection c, long id) {
        QueryResult r = c.query("SELECT id, booking_id, car_id, user_id, rating, body, status, created_at" +
                " FROM reviews WHERE id = $1", new String[]{String.valueOf(id)});
        return r.rowCount() > 0 ? toJson(r, 0) : null;
    }

    public boolean existsForBooking(PgConnection c, String bookingId) {
        QueryResult r = c.query("SELECT 1 FROM reviews WHERE booking_id = $1", new String[]{bookingId});
        return r.rowCount() > 0;
    }

    public long insert(PgConnection c, String bookingId, long carId, String userId, int rating, String body) {
        QueryResult r = c.query("INSERT INTO reviews(booking_id, car_id, user_id, rating, body)" +
                " VALUES ($1,$2,$3,$4,$5) RETURNING id",
                new String[]{bookingId, String.valueOf(carId), userId, String.valueOf(rating),
                        body == null ? "" : body});
        return Long.parseLong(r.first());
    }

    public void updateStatus(PgConnection c, long id, String status) {
        c.query("UPDATE reviews SET status = $1 WHERE id = $2", new String[]{status, String.valueOf(id)});
    }

    public long countPending() {
        return db.with(this::countPending);
    }

    public long countPending(PgConnection c) {
        QueryResult r = c.query("SELECT count(*) FROM reviews WHERE status = 'Pending'", null);
        return Long.parseLong(r.first());
    }

    public static JsonObject toJson(QueryResult r, int row) {
        String[] x = r.rows.get(row);
        JsonObject o = new JsonObject();
        o.addProperty("id", Long.parseLong(x[0]));
        o.addProperty("bookingId", x[1]);
        o.addProperty("carId", Long.parseLong(x[2]));
        o.addProperty("userId", x[3]);
        o.addProperty("rating", Integer.parseInt(x[4]));
        o.addProperty("body", x[5] == null ? "" : x[5]);
        o.addProperty("status", x[6] == null ? "Pending" : x[6]);
        o.addProperty("created", x[7] == null ? "" : x[7]);
        return o;
    }
}
