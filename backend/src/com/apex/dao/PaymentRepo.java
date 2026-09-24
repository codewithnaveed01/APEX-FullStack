package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Payment submissions. Status flow:
 * Pending Verification -> Verified | Rejected | Reupload
 * (Reupload -> a new submission is made and the loop repeats)
 */
public final class PaymentRepo {

    private final Database db;

    public PaymentRepo(Database db) { this.db = db; }

    public List<JsonObject> list() {
        return db.with(c -> list(c));
    }

    public List<JsonObject> list(PgConnection c) {
        QueryResult r = c.query("SELECT id, booking_id, method, amount, tid, receipt_doc, status," +
                " submitted_at, reviewed_at, reviewed_by, note FROM payments ORDER BY id DESC", null);
        List<JsonObject> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(toJson(r, i));
        return out;
    }

    public JsonObject get(PgConnection c, long id) {
        QueryResult r = c.query("SELECT id, booking_id, method, amount, tid, receipt_doc, status," +
                " submitted_at, reviewed_at, reviewed_by, note FROM payments WHERE id = $1",
                new String[]{String.valueOf(id)});
        return r.rowCount() > 0 ? toJson(r, 0) : null;
    }

    public long insert(PgConnection c, String bookingId, String method, long amount, String tid, long receiptDoc) {
        QueryResult r = c.query("INSERT INTO payments(booking_id, method, amount, tid, receipt_doc, status)" +
                " VALUES ($1,$2,$3,$4,$5,'Pending Verification') RETURNING id",
                new String[]{bookingId, method, String.valueOf(amount), tid, String.valueOf(receiptDoc)});
        return Long.parseLong(r.first());
    }

    public void updateStatus(PgConnection c, long id, String status, String note, String reviewedBy) {
        c.query("UPDATE payments SET status = $1, note = $2, reviewed_at = now(), reviewed_by = $3 WHERE id = $4",
                new String[]{status, note == null ? "" : note, reviewedBy, String.valueOf(id)});
    }

    /** A booking must not have more than one payment awaiting review. */
    public JsonObject pendingForBooking(PgConnection c, String bookingId) {
        QueryResult r = c.query("SELECT id, booking_id, method, amount, tid, receipt_doc, status," +
                " submitted_at, reviewed_at, reviewed_by, note FROM payments" +
                " WHERE booking_id = $1 AND status = 'Pending Verification' ORDER BY id DESC LIMIT 1",
                new String[]{bookingId});
        return r.rowCount() > 0 ? toJson(r, 0) : null;
    }

    public long countStatus(PgConnection c, String status) {
        QueryResult r = c.query("SELECT count(*) FROM payments WHERE status = $1", new String[]{status});
        return Long.parseLong(r.first());
    }

    public long sumStatus(PgConnection c, String status) {
        QueryResult r = c.query("SELECT COALESCE(sum(amount), 0) FROM payments WHERE status = $1", new String[]{status});
        return r.rowCount() > 0 && r.rows.get(0)[0] != null ? Long.parseLong(r.rows.get(0)[0]) : 0;
    }

    public static JsonObject toJson(QueryResult r, int row) {
        String[] x = r.rows.get(row);
        JsonObject o = new JsonObject();
        o.addProperty("id", Long.parseLong(x[0]));
        o.addProperty("bookingId", x[1]);
        o.addProperty("method", x[2] == null ? "" : x[2]);
        o.addProperty("amount", Long.parseLong(x[3]));
        o.addProperty("tid", x[4] == null ? "" : x[4]);
        o.addProperty("receiptDoc", x[5] == null ? 0 : Long.parseLong(x[5]));
        o.addProperty("status", x[6] == null ? "Pending Verification" : x[6]);
        o.addProperty("submittedAt", x[7] == null ? "" : x[7]);
        o.addProperty("reviewedAt", x[8] == null ? "" : x[8]);
        o.addProperty("reviewedBy", x[9] == null ? "" : x[9]);
        o.addProperty("note", x[10] == null ? "" : x[10]);
        return o;
    }
}
