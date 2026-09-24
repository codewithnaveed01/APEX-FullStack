package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/** A user account. The frontend shape is kept in `data` (password removed). */
public final class User {
    public final String id;
    public final String username;
    public final String email;
    public final String phone;
    public final String name;
    public final String cnic;
    public final String role;
    public final String passwordHash;
    public final String createdAt;
    public final JsonObject data;

    public User(String id, String username, String email, String phone, String name,
                String cnic, String role, String passwordHash, String createdAt, JsonObject data) {
        this.id = id; this.username = username; this.email = email; this.phone = phone;
        this.name = name; this.cnic = cnic; this.role = role;
        this.passwordHash = passwordHash; this.createdAt = createdAt; this.data = data;
    }

    public boolean isAdmin() { return "admin".equals(role); }

    /** Shape shared with the frontend: never includes the password. */
    public JsonObject publicJson() {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("username", username);
        j.addProperty("email", email == null ? "" : email);
        j.addProperty("name", name);
        j.addProperty("phone", phone == null ? "" : phone);
        j.addProperty("cnic", cnic == null ? "" : cnic);
        j.addProperty("role", role);
        j.addProperty("created", createdAt == null ? "" : createdAt);
        return j;
    }

    public static User fromRow(com.apex.db.QueryResult qr, int row) {
        String[] r = qr.rows.get(row);
        JsonObject data = parseData(qr, row);
        return new User(
                r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8],
                data == null ? new JsonObject() : data);
    }

    private static JsonObject parseData(com.apex.db.QueryResult qr, int row) {
        String json = qr.col("data", row);
        if (json == null || json.isEmpty()) return new JsonObject();
        try {
            return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }
}
