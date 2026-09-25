package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Booking;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class BookingRepo {

    private final Database db;

    public BookingRepo(Database db) { this.db = db; }

    public List<Booking> listAll() {
        return db.with(c -> listAll(c));
    }

    public List<Booking> listAll(PgConnection c) {
        QueryResult r = c.query("SELECT id, data, created_at FROM bookings ORDER BY created_at DESC", null);
        List<Booking> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(Booking.fromRow(r, i));
        return out;
    }

    public Booking get(PgConnection c, String id) {
        QueryResult r = c.query("SELECT id, data, created_at FROM bookings WHERE id = $1", new String[]{id});
        return r.rowCount() > 0 ? Booking.fromRow(r, 0) : null;
    }

    public Booking get(String id) {
        return db.with(c -> get(c, id));
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM bookings", null));
        return Long.parseLong(r.first());
    }

    /** Public availability summary: only car ids, never customer/booking details. */
    public List<Long> rentedCarIds(String startDt, String endDt) {
        return db.with(c -> {
            QueryResult r = c.query("SELECT DISTINCT i.car_id FROM booking_items i" +
                    " JOIN bookings b ON b.id = i.booking_id" +
                    " WHERE b.status NOT IN ('Cancelled', 'Completed', 'Rejected')" +
                    " AND i.start_dt IS NOT NULL AND i.end_dt IS NOT NULL" +
                    " AND i.start_dt < $2 AND i.end_dt > $1",
                    new String[]{startDt, endDt});
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < r.rowCount(); i++) ids.add(Long.parseLong(r.rows.get(i)[0]));
            return ids;
        });
    }

    /** Insert or update the booking row + its items (items are replaced). */
    public void upsert(PgConnection c, Booking b) {
        if (b.id.isEmpty()) throw com.apex.web.ApiException.bad("Booking id is required");
        JsonArray items = b.data.get("items") != null && b.data.getAsJsonArray("items") != null
                ? b.data.getAsJsonArray("items") : new JsonArray();
        c.query("INSERT INTO bookings(id, user_id, customer_name, email, phone, destination, pickup_mode," +
                " home_address, office_address, identity_type, identity, identity_masked, identity_status," +
                " payment, status, payment_status, totals, rental, deposit, home_delivery, total, paid," +
                " deposit_paid, cancellation_fee, start_dt, end_dt, pickup_at, actual_return," +
                " extra_hours, extra_charges, final_amount, tid, receipt_doc, owner_payout_done, data)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23,$24,$25,$26,$27,$28,$29,$30,$31,$32,$33,$34,$35)" +
                " ON CONFLICT (id) DO UPDATE SET" +
                " user_id=EXCLUDED.user_id, customer_name=EXCLUDED.customer_name, email=EXCLUDED.email," +
                " phone=EXCLUDED.phone, destination=EXCLUDED.destination, pickup_mode=EXCLUDED.pickup_mode," +
                " home_address=EXCLUDED.home_address, office_address=EXCLUDED.office_address," +
                " identity_type=EXCLUDED.identity_type, identity=EXCLUDED.identity," +
                " identity_masked=EXCLUDED.identity_masked, identity_status=EXCLUDED.identity_status," +
                " payment=EXCLUDED.payment, status=EXCLUDED.status, payment_status=EXCLUDED.payment_status," +
                " totals=EXCLUDED.totals, rental=EXCLUDED.rental, deposit=EXCLUDED.deposit," +
                " home_delivery=EXCLUDED.home_delivery, total=EXCLUDED.total, paid=EXCLUDED.paid," +
                " deposit_paid=EXCLUDED.deposit_paid, cancellation_fee=EXCLUDED.cancellation_fee," +
                " start_dt=EXCLUDED.start_dt, end_dt=EXCLUDED.end_dt, pickup_at=EXCLUDED.pickup_at," +
                " actual_return=EXCLUDED.actual_return, extra_hours=EXCLUDED.extra_hours," +
                " extra_charges=EXCLUDED.extra_charges, final_amount=EXCLUDED.final_amount," +
                " tid=EXCLUDED.tid, receipt_doc=EXCLUDED.receipt_doc," +
                " owner_payout_done=EXCLUDED.owner_payout_done, data=EXCLUDED.data, updated_at=now()",
                new String[]{b.id, nullIfEmpty(b.userId), nullIfEmpty(b.customerName), nullIfEmpty(b.email),
                        nullIfEmpty(b.phone), nullIfEmpty(b.destination), nullIfEmpty(b.pickupMode),
                        nullIfEmpty(b.homeAddress), nullIfEmpty(b.officeAddress), nullIfEmpty(b.identityType),
                        nullIfEmpty(b.identity), nullIfEmpty(b.identityMasked), nullIfEmpty(b.identityStatus),
                        nullIfEmpty(b.payment), nullIfEmpty(b.status), nullIfEmpty(b.paymentStatus),
                        b.totals.toString(), String.valueOf(b.rental), String.valueOf(b.deposit),
                        String.valueOf(b.homeDelivery), String.valueOf(b.total), String.valueOf(b.paid),
                        String.valueOf(b.depositPaid), String.valueOf(b.cancellationFee),
                        nullIfEmpty(b.startDt), nullIfEmpty(b.endDt), nullIfEmpty(b.pickupAt),
                        nullIfEmpty(b.actualReturn),
                        b.extraHours == null ? null : String.valueOf(b.extraHours),
                        b.extraCharges == null ? null : String.valueOf(b.extraCharges),
                        b.finalAmount == null ? null : String.valueOf(b.finalAmount),
                        nullIfEmpty(b.tid),
                        b.receiptDoc == null ? null : String.valueOf(b.receiptDoc),
                        String.valueOf(b.ownerPayoutDone),
                        b.data.toString()});

        c.query("DELETE FROM booking_items WHERE booking_id = $1", new String[]{b.id});
        int pos = 0;
        for (JsonElement el : items) {
            if (!el.isJsonObject()) continue;
            JsonObject it = el.getAsJsonObject();
            c.query("INSERT INTO booking_items(booking_id, pos, car_id, start_date, end_date, start_dt, end_dt," +
                    " city, service, driver_rate, assigned_driver, price, self_driver, data)" +
                    " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)",
                    new String[]{b.id, String.valueOf(pos),
                            String.valueOf(Json.getLong(it, "carId", 0)),
                            nullIfEmpty(Json.getStr(it, "start", "")),
                            nullIfEmpty(Json.getStr(it, "end", "")),
                            nullIfEmpty(Json.getStr(it, "startDt", "")),
                            nullIfEmpty(Json.getStr(it, "endDt", "")),
                            nullIfEmpty(Json.getStr(it, "city", "")),
                            nullIfEmpty(Json.getStr(it, "service", "Self-drive")),
                            it.has("driverRate") && Json.getLong(it, "driverRate", -1) > 0
                                    ? String.valueOf(Json.getLong(it, "driverRate", 0)) : null,
                            it.has("assignedDriver") && Json.getLong(it, "assignedDriver", -1) > 0
                                    ? String.valueOf(Json.getLong(it, "assignedDriver", 0)) : null,
                            it.has("price") && it.get("price").isJsonObject() ? it.get("price").toString() : null,
                            it.has("selfDriver") && it.get("selfDriver").isJsonObject() ? it.get("selfDriver").toString() : null,
                            it.toString()});
            pos++;
        }
    }

    /**
     * Double-booking guard: any non-Cancelled/non-Completed booking of the
     * same car whose window overlaps [start, end). Must run inside the
     * caller's transaction (with the car's advisory lock held).
     */
    public String findOverlap(PgConnection c, long carId, String startDt, String endDt, String excludeBookingId) {
        QueryResult r = c.query(
                "SELECT b.id FROM booking_items i" +
                " JOIN bookings b ON b.id = i.booking_id" +
                " WHERE i.car_id = $1" +
                " AND b.id <> $2" +
                " AND b.status NOT IN ('Cancelled', 'Completed', 'Rejected')" +
                " AND i.start_dt IS NOT NULL AND i.end_dt IS NOT NULL" +
                " AND i.start_dt < $4 AND i.end_dt > $3" +
                " ORDER BY i.start_dt LIMIT 1",
                new String[]{String.valueOf(carId), excludeBookingId == null ? "" : excludeBookingId,
                        startDt, endDt});
        if (r.rowCount() == 0) return null;
        return r.rows.get(0)[0];
    }

    /** All active windows for one car - used for sync overlap validation. */
    public List<String[]> windowsForCar(PgConnection c, long carId) {
        List<String[]> out = new ArrayList<>();
        QueryResult r = c.query(
                "SELECT b.id, i.start_dt, i.end_dt FROM booking_items i" +
                " JOIN bookings b ON b.id = i.booking_id" +
                " WHERE i.car_id = $1 AND b.status NOT IN ('Cancelled', 'Completed', 'Rejected')",
                new String[]{String.valueOf(carId)});
        for (int i = 0; i < r.rowCount(); i++) out.add(r.rows.get(i));
        return out;
    }

    public void delete(PgConnection c, String id) {
        c.query("DELETE FROM bookings WHERE id = $1", new String[]{id});
    }

    public long countByStatus(PgConnection c, String status) {
        QueryResult r = c.query("SELECT count(*) FROM bookings WHERE status = $1", new String[]{status});
        return Long.parseLong(r.first());
    }

    public static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
