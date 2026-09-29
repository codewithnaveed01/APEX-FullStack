package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Application;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class ApplicationRepo {
    private final Database db;
    private static final String COLS =
            "id, user_id, owner, phone, email, brand, model, year, registration, cnic, license, city, mileage," +
            " car_condition, rate, preference, available_from, photo_count, notes, status, verification, data," +
            " live_car_id, revision, edit_status, approved_by, approved_at, deleted_at, created_at, updated_at";

    public ApplicationRepo(Database db) { this.db = db; }

    public List<Application> list() { return db.with(this::list); }
    public List<Application> list(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications ORDER BY created_at DESC", null);
        return rows(c, r);
    }
    public List<Application> listByUser(PgConnection c, String userId) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications" +
                " WHERE user_id = $1 ORDER BY created_at DESC", new String[]{userId});
        return rows(c, r);
    }
    public Application get(PgConnection c, String id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications WHERE id = $1", new String[]{id});
        return r.rowCount() > 0 ? withImages(c, Application.fromRow(r, 0)) : null;
    }
    public Application getForUpdate(PgConnection c, String id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications WHERE id = $1 FOR UPDATE",
                new String[]{id});
        return r.rowCount() > 0 ? withImages(c, Application.fromRow(r, 0)) : null;
    }

    public void upsert(PgConnection c, Application a) {
        if (a.id.isEmpty()) throw com.apex.web.ApiException.bad("Application id is required");
        JsonObject d = a.data;
        c.query("INSERT INTO owner_applications(id, user_id, owner, phone, email, brand, model, year, registration," +
                " cnic, license, city, mileage, car_condition, rate, preference, available_from," +
                " photo_count, notes, status, verification, data, live_car_id, revision, edit_status," +
                " approved_by, approved_at, deleted_at)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22," +
                " $23,$24,$25,$26,$27::timestamptz,$28::timestamptz)" +
                " ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, owner=EXCLUDED.owner," +
                " phone=EXCLUDED.phone, email=EXCLUDED.email, brand=EXCLUDED.brand, model=EXCLUDED.model," +
                " year=EXCLUDED.year, registration=EXCLUDED.registration, cnic=EXCLUDED.cnic," +
                " license=EXCLUDED.license, city=EXCLUDED.city, mileage=EXCLUDED.mileage," +
                " car_condition=EXCLUDED.car_condition, rate=EXCLUDED.rate," +
                " preference=EXCLUDED.preference, available_from=EXCLUDED.available_from," +
                " photo_count=EXCLUDED.photo_count, notes=EXCLUDED.notes, status=EXCLUDED.status," +
                " verification=EXCLUDED.verification, data=EXCLUDED.data, live_car_id=EXCLUDED.live_car_id," +
                " revision=EXCLUDED.revision, edit_status=EXCLUDED.edit_status," +
                " approved_by=EXCLUDED.approved_by, approved_at=EXCLUDED.approved_at," +
                " deleted_at=EXCLUDED.deleted_at, updated_at=now()",
                new String[]{a.id, nil(a.userId), nil(a.owner), nil(a.phone), nil(a.email), nil(a.brand),
                        nil(a.model), number(a.year), nil(a.registration), nil(a.cnic),
                        nil(Json.getStr(d, "license", "")), nil(a.city), number(a.mileage),
                        nil(Json.getStr(d, "condition", "")), number(a.rate), nil(a.preference),
                        nil(a.availableFrom), number(a.photoCount), nil(Json.getStr(d, "notes", "")),
                        nil(a.status), nil(a.verification), d.toString(), a.liveCarId > 0 ? number(a.liveCarId) : null,
                        number(a.revision), nil(a.editStatus), nil(Json.getStr(d, "approvedBy", "")),
                        nil(Json.getStr(d, "approvedAt", "")), nil(Json.getStr(d, "deletedAt", ""))});
    }

    public void setImages(PgConnection c, String applicationId, String ownerId, List<Long> ids) {
        c.query("DELETE FROM vehicle_application_images WHERE application_id = $1", new String[]{applicationId});
        for (int i = 0; i < ids.size(); i++) {
            c.query("INSERT INTO vehicle_application_images(application_id, position, document_id, owner_id)" +
                    " VALUES ($1,$2,$3,$4)",
                    new String[]{applicationId, String.valueOf(i + 1), String.valueOf(ids.get(i)), ownerId});
        }
    }

    public List<Long> imageIds(PgConnection c, String applicationId) {
        QueryResult r = c.query("SELECT document_id FROM vehicle_application_images" +
                " WHERE application_id = $1 ORDER BY position", new String[]{applicationId});
        List<Long> out = new ArrayList<>();
        for (String[] row : r.rows) out.add(Long.parseLong(row[0]));
        return out;
    }

    public void history(PgConnection c, Application a, String action, String actorId, String actorRole) {
        c.query("INSERT INTO owner_application_history(application_id, revision, action, actor_id," +
                " actor_role, status, snapshot) VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb)",
                new String[]{a.id, String.valueOf(a.revision), action, actorId, actorRole, a.status,
                        a.data.toString()});
    }

    public void delete(PgConnection c, String id) {
        c.query("DELETE FROM owner_applications WHERE id = $1", new String[]{id});
    }
    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM owner_applications", null));
        return Long.parseLong(r.first());
    }

    private List<Application> rows(PgConnection c, QueryResult r) {
        List<Application> out = new ArrayList<>();
        // Application lists are operational queues, so enriching each row with
        // its three ordered references is small and keeps one canonical shape.
        for (int i = 0; i < r.rowCount(); i++) {
            out.add(withImages(c, Application.fromRow(r, i)));
        }
        return out;
    }

    /** Adds the canonical ordered IDs/URLs to one application snapshot. */
    private Application withImages(PgConnection c, Application a) {
        if (a == null) return null;
        List<Long> ids = imageIds(c, a.id);
        if (!ids.isEmpty()) {
            JsonArray photoIds = new JsonArray();
            JsonArray urls = new JsonArray();
            for (Long id : ids) {
                photoIds.add(id);
                urls.add("/api/vehicle-images/" + id);
            }
            a.data.add("photoIds", photoIds);
            a.data.add("imageUrls", urls);
            a.data.addProperty("photoCount", ids.size());
        }
        return a;
    }

    public Application enrich(PgConnection c, Application a) { return withImages(c, a); }

    private static String nil(String s) { return s == null || s.isBlank() ? null : s; }
    private static String number(long n) { return String.valueOf(n); }
}
