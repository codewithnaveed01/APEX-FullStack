package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/**
 * A booking (the frontend calls it an "order").
 * `data` is the exact frontend shape; normalized columns exist so the
 * server can query, index and double-booking-check without trusting JS.
 */
public final class Booking {
    public final String id, userId, customerName, email, phone, destination;
    public final String pickupMode, homeAddress, officeAddress;
    public final String identityType, identity, identityMasked, identityStatus;
    public final String payment, status, paymentStatus;
    public final long rental, deposit, homeDelivery, total, paid, depositPaid, cancellationFee;
    public final String startDt, endDt, pickupAt, actualReturn;
    public final Integer extraHours;
    public final Long extraCharges, finalAmount;
    public final String tid;
    public final Long receiptDoc;
    public final boolean ownerPayoutDone;
    public final JsonObject totals;
    public final JsonObject data;

    private Booking(String id, String userId, String customerName, String email, String phone,
                    String destination, String pickupMode, String homeAddress, String officeAddress,
                    String identityType, String identity, String identityMasked, String identityStatus,
                    String payment, String status, String paymentStatus,
                    long rental, long deposit, long homeDelivery, long total,
                    long paid, long depositPaid, long cancellationFee,
                    String startDt, String endDt, String pickupAt, String actualReturn,
                    Integer extraHours, Long extraCharges, Long finalAmount,
                    String tid, Long receiptDoc, boolean ownerPayoutDone,
                    JsonObject totals, JsonObject data) {
        this.id = id; this.userId = userId; this.customerName = customerName; this.email = email;
        this.phone = phone; this.destination = destination; this.pickupMode = pickupMode;
        this.homeAddress = homeAddress; this.officeAddress = officeAddress;
        this.identityType = identityType; this.identity = identity;
        this.identityMasked = identityMasked; this.identityStatus = identityStatus;
        this.payment = payment; this.status = status; this.paymentStatus = paymentStatus;
        this.rental = rental; this.deposit = deposit; this.homeDelivery = homeDelivery;
        this.total = total; this.paid = paid; this.depositPaid = depositPaid;
        this.cancellationFee = cancellationFee;
        this.startDt = startDt; this.endDt = endDt; this.pickupAt = pickupAt;
        this.actualReturn = actualReturn; this.extraHours = extraHours;
        this.extraCharges = extraCharges; this.finalAmount = finalAmount;
        this.tid = tid; this.receiptDoc = receiptDoc; this.ownerPayoutDone = ownerPayoutDone;
        this.totals = totals == null ? new JsonObject() : totals;
        this.data = data;
    }

    public JsonObject toJson() { return data; }

    /** Build a booking from a raw frontend order object (trust nothing but validate). */
    public static Booking fromJson(JsonObject j) {
        if (j == null) return null;
        JsonObject d = j.deepCopy();
        JsonObject totals = Json.obj(d, "totals");
        long rental = totals != null ? Json.getLong(totals, "rental", 0) : 0;
        long deposit = totals != null ? Json.getLong(totals, "deposit", 0) : 0;
        long homeDelivery = totals != null ? Json.getLong(totals, "homeDelivery", 0) : 0;
        long total = totals != null ? Json.getLong(totals, "total", 0) : 0;
        return new Booking(
                Json.getStr(d, "id", ""),
                Json.getStr(d, "userId", ""),
                Json.getStr(d, "name", ""),
                Json.getStr(d, "email", ""),
                Json.getStr(d, "phone", ""),
                Json.getStr(d, "destination", ""),
                Json.getStr(d, "pickupMode", "Office"),
                Json.getStr(d, "homeAddress", ""),
                Json.getStr(d, "officeAddress", ""),
                Json.getStr(d, "identityType", "CNIC"),
                Json.getStr(d, "identity", ""),
                Json.getStr(d, "identityMasked", ""),
                Json.getStr(d, "identityStatus", "Pending"),
                Json.getStr(d, "payment", ""),
                Json.getStr(d, "status", "Confirmed"),
                Json.getStr(d, "paymentStatus", "Pending Verification"),
                rental, deposit, homeDelivery, total,
                Json.getLong(d, "paid", 0),
                Json.getLong(d, "depositPaid", 0),
                Json.getLong(d, "cancellationFee", 0),
                Json.getStr(d, "startDt", ""),
                Json.getStr(d, "endDt", ""),
                Json.getStr(d, "pickupAt", ""),
                Json.getStr(d, "actualReturn", ""),
                d.has("extraHours") ? Integer.valueOf(Json.getInt(d, "extraHours", 0)) : null,
                d.has("extraCharges") ? Long.valueOf(Json.getLong(d, "extraCharges", 0)) : null,
                d.has("finalAmount") ? Long.valueOf(Json.getLong(d, "finalAmount", 0)) : null,
                Json.getStr(d, "tid", ""),
                d.has("receiptDoc") && Json.getLong(d, "receiptDoc", -1) > 0 ? Long.valueOf(Json.getLong(d, "receiptDoc", 0)) : null,
                Json.getBool(d, "ownerPayoutDone", false),
                totals,
                d);
    }

    public static Booking fromRow(com.apex.db.QueryResult qr, int row) {
        String data = qr.col("data", row);
        JsonObject d = data == null ? new JsonObject() : safeParse(data);
        return fromJson(d);
    }

    private static JsonObject safeParse(String s) {
        try {
            return com.google.gson.JsonParser.parseString(s).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }
}
