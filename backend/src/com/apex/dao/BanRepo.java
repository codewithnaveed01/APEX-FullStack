package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** CNIC ban list - checked before every booking. */
public final class BanRepo {

    private final Database db;

    public BanRepo(Database db) { this.db = db; }

    public List<JsonObject> list() {
        return db.with(c -> list(c));
    }

    public List<JsonObject> list(PgConnection c) {
        QueryResult r = c.query("SELECT cnic, created_at FROM banned_cnic ORDER BY created_at DESC", null);
        List<JsonObject> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("cnic", r.rows.get(i)[0]);
            o.addProperty("created", r.rows.get(i)[1] == null ? "" : r.rows.get(i)[1]);
            out.add(o);
        }
        return out;
    }

    public boolean contains(PgConnection c, String cnic) {
        QueryResult r = c.query("SELECT 1 FROM banned_cnic WHERE cnic = $1", new String[]{Json.clean(cnic)});
        return r.rowCount() > 0;
    }

    public void add(PgConnection c, String cnic) {
        c.query("INSERT INTO banned_cnic(cnic) VALUES ($1) ON CONFLICT (cnic) DO NOTHING",
                new String[]{Json.clean(cnic)});
    }

    public void remove(PgConnection c, String cnic) {
        c.query("DELETE FROM banned_cnic WHERE cnic = $1", new String[]{Json.clean(cnic)});
    }

    public void replaceAll(PgConnection c, List<String> cnics) {
        c.simpleQuery("DELETE FROM banned_cnic");
        for (String cn : cnics) {
            String v = Json.clean(cn);
            if (v.length() > 0) add(c, v);
        }
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM banned_cnic", null));
        return Long.parseLong(r.first());
    }
}
