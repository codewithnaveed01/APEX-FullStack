package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Application;
import com.apex.util.Json;

import java.util.ArrayList;
import java.util.List;

public final class ApplicationRepo {

    private final Database db;
    private static final String COLS =
            "id, user_id, owner, phone, email, brand, year, registration, cnic, license, city, mileage," +
            " car_condition, rate, preference, available_from, photo_count, notes, status, verification, data";

    public ApplicationRepo(Database db) { this.db = db; }

    public List<Application> list() {
        return db.with(c -> list(c));
    }

    public List<Application> list(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications ORDER BY created_at DESC", null);
        List<Application> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(Application.fromRow(r, i));
        return out;
    }

    public List<Application> listByUser(PgConnection c, String userId) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications WHERE user_id = $1 ORDER BY created_at DESC",
                new String[]{userId});
        List<Application> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(Application.fromRow(r, i));
        return out;
    }

    public Application get(PgConnection c, String id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM owner_applications WHERE id = $1", new String[]{id});
        return r.rowCount() > 0 ? Application.fromRow(r, 0) : null;
    }

    public void upsert(PgConnection c, Application a) {
        if (a.id.isEmpty()) throw com.apex.web.ApiException.bad("Application id is required");
        c.query("INSERT INTO owner_applications(id, user_id, owner, phone, email, brand, year, registration," +
                " cnic, license, city, mileage, car_condition, rate, preference, available_from," +
                " photo_count, notes, status, verification, data)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21)" +
                " ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, owner=EXCLUDED.owner," +
                " phone=EXCLUDED.phone, email=EXCLUDED.email, brand=EXCLUDED.brand, year=EXCLUDED.year," +
                " registration=EXCLUDED.registration, cnic=EXCLUDED.cnic, license=EXCLUDED.license," +
                " city=EXCLUDED.city, mileage=EXCLUDED.mileage, car_condition=EXCLUDED.car_condition," +
                " rate=EXCLUDED.rate, preference=EXCLUDED.preference, available_from=EXCLUDED.available_from," +
                " photo_count=EXCLUDED.photo_count, notes=EXCLUDED.notes, status=EXCLUDED.status," +
                " verification=EXCLUDED.verification, data=EXCLUDED.data, updated_at=now()",
                new String[]{a.id, nullIfEmpty(a.userId), nullIfEmpty(a.owner), nullIfEmpty(a.phone),
                        nullIfEmpty(a.email), nullIfEmpty(a.brand), String.valueOf(a.year),
                        nullIfEmpty(a.registration), nullIfEmpty(a.cnic), nullIfEmpty(Json.getStr(a.data, "license", "")),
                        nullIfEmpty(a.city), String.valueOf(a.mileage), nullIfEmpty(Json.getStr(a.data, "condition", "")),
                        String.valueOf(a.rate), nullIfEmpty(a.preference), nullIfEmpty(a.availableFrom),
                        String.valueOf(a.photoCount), nullIfEmpty(Json.getStr(a.data, "notes", "")),
                        nullIfEmpty(a.status), nullIfEmpty(a.verification), a.data.toString()});
    }

    public void delete(PgConnection c, String id) {
        c.query("DELETE FROM owner_applications WHERE id = $1", new String[]{id});
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM owner_applications", null));
        return Long.parseLong(r.first());
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
