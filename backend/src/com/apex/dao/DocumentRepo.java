package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Sensitive uploads (CNIC, licences, receipts, photos).
 * Bytes live in BYTEA (Postgres) and on disk; they are only ever
 * returned to the owner or an admin, never through public URLs.
 */
public final class DocumentRepo {

    private final Database db;

    public DocumentRepo(Database db) { this.db = db; }

    public long insert(PgConnection c, String ownerType, String ownerId, String kind,
                       String path, String contentType, byte[] content) {
        // hex-encode for the text-format parameter
        StringBuilder hex = new StringBuilder("\\x");
        for (byte b : content) hex.append(String.format("%02x", b));
        QueryResult r = c.query("INSERT INTO documents(owner_type, owner_id, kind, path, content_type, content)" +
                " VALUES ($1,$2,$3,$4,$5, $6::bytea) RETURNING id",
                new String[]{ownerType, ownerId, kind, path, contentType, hex.toString()});
        return Long.parseLong(r.first());
    }

    public JsonObject get(PgConnection c, long id) {
        QueryResult r = c.query("SELECT id, owner_type, owner_id, kind, path, content_type, status, created_at" +
                " FROM documents WHERE id = $1", new String[]{String.valueOf(id)});
        if (r.rowCount() == 0) return null;
        String[] x = r.rows.get(0);
        JsonObject o = new JsonObject();
        o.addProperty("id", Long.parseLong(x[0]));
        o.addProperty("ownerType", x[1]);
        o.addProperty("ownerId", x[2]);
        o.addProperty("kind", x[3]);
        o.addProperty("path", x[4]);
        o.addProperty("contentType", x[5]);
        o.addProperty("status", x[6]);
        o.addProperty("created", x[7] == null ? "" : x[7]);
        return o;
    }

    public byte[] content(PgConnection c, long id) {
        QueryResult r = c.query("SELECT content FROM documents WHERE id = $1", new String[]{String.valueOf(id)});
        if (r.rowCount() == 0 || r.rows.get(0)[0] == null) return null;
        String s = r.rows.get(0)[0];
        if (s.startsWith("\\x")) {
            s = s.substring(2);
            byte[] out = new byte[s.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return new byte[0];
    }

    public boolean exists(PgConnection c, long id) {
        QueryResult r = c.query("SELECT 1 FROM documents WHERE id = $1", new String[]{String.valueOf(id)});
        return r.rowCount() > 0;
    }

    public List<JsonObject> list() {
        return db.with(c -> {
            QueryResult r = c.query("SELECT id, owner_type, owner_id, kind, status, created_at" +
                    " FROM documents ORDER BY id DESC LIMIT 500", null);
            List<JsonObject> out = new ArrayList<>();
            for (int i = 0; i < r.rowCount(); i++) {
                String[] x = r.rows.get(i);
                JsonObject o = new JsonObject();
                o.addProperty("id", Long.parseLong(x[0]));
                o.addProperty("ownerType", x[1]);
                o.addProperty("ownerId", x[2]);
                o.addProperty("kind", x[3]);
                o.addProperty("status", x[4]);
                o.addProperty("created", x[5] == null ? "" : x[5]);
                out.add(o);
            }
            return out;
        });
    }

    public void updateStatus(PgConnection c, long id, String status) {
        c.query("UPDATE documents SET status = $1 WHERE id = $2", new String[]{status, String.valueOf(id)});
    }

    public long countPending() {
        return db.with(this::countPending);
    }

    public long countPending(PgConnection c) {
        QueryResult r = c.query("SELECT count(*) FROM documents WHERE status = 'Pending'", null);
        return Long.parseLong(r.first());
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM documents", null));
        return Long.parseLong(r.first());
    }
}
