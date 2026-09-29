package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Sensitive uploads (CNIC, licences, receipts, photos).
 * Bytes live in BYTEA (Postgres) and on disk. Documents stay private except
 * vehicle photos currently referenced by a live fleet row, which have a
 * narrowly scoped public byte endpoint.
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

    /** Receipt ownership and lifecycle check shared by submission and review. */
    public boolean isOwnedPendingReceipt(PgConnection c, long id, String userId) {
        QueryResult r = c.query("SELECT 1 FROM documents d WHERE d.id=$1 AND d.owner_type='user'" +
                        " AND d.owner_id=$2 AND d.kind='receipt' AND d.status='Pending'",
                new String[]{String.valueOf(id), userId});
        return r.rowCount() > 0;
    }

    /** A new receipt must additionally not be linked to a previous payment. */
    public boolean isDedicatedPendingReceipt(PgConnection c, long id, String userId) {
        if (!isOwnedPendingReceipt(c, id, userId)) return false;
        QueryResult r = c.query("SELECT 1 FROM payments WHERE receipt_doc=$1", new String[]{String.valueOf(id)});
        return r.rowCount() == 0;
    }

    /** A vehicle photo may only be attached by the user who uploaded it. */
    public boolean isOwnedVehiclePhoto(PgConnection c, long id, String ownerId) {
        QueryResult r = c.query("SELECT 1 FROM documents WHERE id = $1 AND owner_type = 'user'" +
                " AND owner_id = $2 AND kind = 'photo'", new String[]{String.valueOf(id), ownerId});
        return r.rowCount() > 0;
    }

    public boolean vehiclePhotoAttachedElsewhere(PgConnection c, long id, String applicationId) {
        QueryResult r = c.query("SELECT 1 FROM vehicle_application_images WHERE document_id = $1" +
                " AND application_id <> $2", new String[]{String.valueOf(id), applicationId == null ? "" : applicationId});
        return r.rowCount() > 0;
    }

    /** A generic fleet image must be an admin upload for this car or an existing owner-application image. */
    public boolean isFleetPhoto(PgConnection c, long documentId, long carId, String applicationId) {
        QueryResult r = c.query("SELECT 1 FROM documents d WHERE d.id=$1 AND d.kind='photo' AND (" +
                " (d.owner_type='car' AND d.owner_id=$2) OR EXISTS (SELECT 1 FROM vehicle_application_images vi" +
                " WHERE vi.document_id=d.id AND vi.application_id=$3)) LIMIT 1",
                new String[]{String.valueOf(documentId), String.valueOf(carId),
                        applicationId == null ? "" : applicationId});
        return r.rowCount() > 0;
    }

    /** Public bytes are exposed only while this exact document is referenced by a live fleet row. */
    public JsonObject publicVehicleImage(PgConnection c, long id) {
        QueryResult r = c.query("SELECT d.content_type, d.content FROM documents d" +
                " JOIN cars ca ON d.id IN (ca.main_image_id, ca.interior_image_id, ca.detail_image_id)" +
                " WHERE d.id = $1 AND ca.status <> 'Deleted' AND (" +
                " (d.owner_type='car' AND d.owner_id=ca.id::text) OR EXISTS (" +
                " SELECT 1 FROM vehicle_application_images vi WHERE vi.document_id=d.id" +
                " AND vi.application_id=ca.application_id)) LIMIT 1",
                new String[]{String.valueOf(id)});
        if (r.rowCount() == 0) return null;
        JsonObject out = new JsonObject();
        out.addProperty("contentType", r.rows.get(0)[0] == null ? "image/jpeg" : r.rows.get(0)[0]);
        out.addProperty("hex", r.rows.get(0)[1] == null ? "" : r.rows.get(0)[1]);
        return out;
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
