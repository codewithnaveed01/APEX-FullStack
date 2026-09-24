package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Driver;

import java.util.ArrayList;
import java.util.List;

public final class DriverRepo {

    private final Database db;
    private static final String COLS = "id, name, phone, city, experience, license, active, status, data";

    public DriverRepo(Database db) { this.db = db; }

    public List<Driver> list() {
        return db.with(c -> list(c));
    }

    public List<Driver> list(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM drivers ORDER BY id", null);
        List<Driver> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(Driver.fromRow(r, i));
        return out;
    }

    public Driver get(PgConnection c, long id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM drivers WHERE id = $1", new String[]{String.valueOf(id)});
        return r.rowCount() > 0 ? Driver.fromRow(r, 0) : null;
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM drivers", null));
        return Long.parseLong(r.first());
    }

    public void upsert(PgConnection c, Driver d) {
        c.query("INSERT INTO drivers(id, name, phone, city, experience, license, active, status, data)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)" +
                " ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, phone=EXCLUDED.phone," +
                " city=EXCLUDED.city, experience=EXCLUDED.experience, license=EXCLUDED.license," +
                " active=EXCLUDED.active, status=EXCLUDED.status, data=EXCLUDED.data, updated_at=now()",
                new String[]{String.valueOf(d.id), nullIfEmpty(d.name), nullIfEmpty(d.phone),
                        nullIfEmpty(d.city), String.valueOf(d.experience), nullIfEmpty(d.license),
                        String.valueOf(d.active), nullIfEmpty(d.status), d.data.toString()});
    }

    public void replaceAll(PgConnection c, List<Driver> drivers) {
        c.simpleQuery("DELETE FROM drivers");
        for (Driver d : drivers) upsert(c, d);
    }

    public void delete(PgConnection c, long id) {
        c.query("DELETE FROM drivers WHERE id = $1", new String[]{String.valueOf(id)});
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
