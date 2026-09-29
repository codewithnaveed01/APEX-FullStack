package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/** Owner vehicle application. The JSON snapshot is the review/publishing payload. */
public final class Application {
    public final String id, userId, owner, phone, email, brand, model, registration, cnic, city;
    public final String preference, availableFrom, status, verification, editStatus;
    public final int year, mileage, photoCount, revision;
    public final long rate, liveCarId;
    public final JsonObject data;

    private Application(String id, String userId, String owner, String phone, String email,
                        String brand, String model, Integer year, String registration, String cnic,
                        String city, Integer mileage, Long rate, String preference,
                        String availableFrom, Integer photoCount, String status, String verification,
                        String editStatus, Integer revision, Long liveCarId, JsonObject data) {
        this.id = id; this.userId = userId; this.owner = owner; this.phone = phone;
        this.email = email; this.brand = brand; this.model = model;
        this.year = year == null ? 0 : year; this.registration = registration;
        this.cnic = cnic; this.city = city; this.mileage = mileage == null ? 0 : mileage;
        this.rate = rate == null ? 0 : rate; this.preference = preference;
        this.availableFrom = availableFrom; this.photoCount = photoCount == null ? 0 : photoCount;
        this.status = status; this.verification = verification; this.editStatus = editStatus;
        this.revision = revision == null ? 1 : revision;
        this.liveCarId = liveCarId == null ? 0 : liveCarId;
        this.data = data;
    }

    public JsonObject toJson() { return data.deepCopy(); }

    public static Application fromJson(JsonObject j) {
        if (j == null) return null;
        JsonObject d = j.deepCopy();
        return new Application(
                Json.getStr(d, "id", ""), Json.getStr(d, "userId", ""),
                Json.getStr(d, "owner", ""), Json.getStr(d, "phone", ""),
                Json.getStr(d, "email", ""), Json.getStr(d, "brand", ""),
                Json.getStr(d, "model", ""), d.has("year") ? Json.getInt(d, "year", 0) : null,
                Json.getStr(d, "registration", ""), Json.getStr(d, "cnic", ""),
                Json.getStr(d, "city", ""), d.has("mileage") ? Json.getInt(d, "mileage", 0) : null,
                d.has("rate") ? Json.getLong(d, "rate", 0) : null,
                Json.getStr(d, "preference", ""), Json.getStr(d, "availableFrom", ""),
                d.has("photoCount") ? Json.getInt(d, "photoCount", 0) : null,
                Json.getStr(d, "status", "Submitted"), Json.getStr(d, "verification", "Pending"),
                Json.getStr(d, "editStatus", "Pending approval"),
                d.has("revision") ? Json.getInt(d, "revision", 1) : null,
                d.has("liveCarId") ? Json.getLong(d, "liveCarId", 0) : null, d);
    }

    public static Application fromRow(com.apex.db.QueryResult qr, int row) {
        String raw = qr.col("data", row);
        JsonObject d = raw == null ? new JsonObject() : safeParse(raw);
        put(d, "id", qr.col("id", row));
        put(d, "userId", qr.col("user_id", row));
        put(d, "owner", qr.col("owner", row));
        put(d, "phone", qr.col("phone", row));
        put(d, "email", qr.col("email", row));
        put(d, "brand", qr.col("brand", row));
        put(d, "model", qr.col("model", row));
        putNumber(d, "year", qr.col("year", row));
        put(d, "registration", qr.col("registration", row));
        put(d, "cnic", qr.col("cnic", row));
        put(d, "license", qr.col("license", row));
        put(d, "city", qr.col("city", row));
        putNumber(d, "mileage", qr.col("mileage", row));
        put(d, "condition", qr.col("car_condition", row));
        putNumber(d, "rate", qr.col("rate", row));
        put(d, "preference", qr.col("preference", row));
        put(d, "availableFrom", qr.col("available_from", row));
        putNumber(d, "photoCount", qr.col("photo_count", row));
        put(d, "notes", qr.col("notes", row));
        put(d, "status", qr.col("status", row));
        put(d, "verification", qr.col("verification", row));
        putNumber(d, "liveCarId", qr.col("live_car_id", row));
        putNumber(d, "revision", qr.col("revision", row));
        put(d, "editStatus", qr.col("edit_status", row));
        put(d, "approvedBy", qr.col("approved_by", row));
        put(d, "approvedAt", qr.col("approved_at", row));
        put(d, "deletedAt", qr.col("deleted_at", row));
        put(d, "createdAt", qr.col("created_at", row));
        put(d, "updatedAt", qr.col("updated_at", row));
        return fromJson(d);
    }

    private static void put(JsonObject d, String key, String value) {
        if (value != null) d.addProperty(key, value);
    }
    private static void putNumber(JsonObject d, String key, String value) {
        if (value == null || value.isBlank()) return;
        try { d.addProperty(key, Long.parseLong(value)); } catch (NumberFormatException ignored) { }
    }
    private static JsonObject safeParse(String s) {
        try { return com.google.gson.JsonParser.parseString(s).getAsJsonObject(); }
        catch (RuntimeException e) { return new JsonObject(); }
    }
}
