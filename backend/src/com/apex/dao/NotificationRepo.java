package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications. Rows are id-keyed; sync upserts never delete history.
 * The frontend "read" state arrives through sync (is_read mirror).
 */
public final class NotificationRepo {

    private final Database db;

    public NotificationRepo(Database db) { this.db = db; }

    public List<JsonObject> listAll() {
        return db.with(c -> listAll(c));
    }

    public List<JsonObject> listAll(PgConnection c) {
        QueryResult r = c.query("SELECT id, user_id, title, msg, link, admin_tab, is_read, created_at" +
                " FROM notifications ORDER BY created_at DESC", null);
        List<JsonObject> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(toJson(r, i));
        return out;
    }

    /** Own notifications; legacy 'all' rows were admin alerts, not public broadcasts. */
    public List<JsonObject> listForUser(PgConnection c, String userId) {
        QueryResult r = c.query("SELECT id, user_id, title, msg, link, admin_tab, is_read, created_at" +
                " FROM notifications WHERE user_id = $1 OR ($1 = 'admin' AND user_id = 'all')" +
                " ORDER BY created_at DESC",
                new String[]{userId});
        List<JsonObject> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(toJson(r, i));
        return out;
    }

    public long unreadCount(PgConnection c, String userId) {
        QueryResult r = c.query("SELECT count(*) FROM notifications" +
                " WHERE (user_id = $1 OR ($1 = 'admin' AND user_id = 'all')) AND is_read = FALSE",
                new String[]{userId});
        return Long.parseLong(r.first());
    }

    public void markReadForUser(PgConnection c, String userId) {
        c.query("UPDATE notifications SET is_read = TRUE" +
                " WHERE (user_id = $1 OR ($1 = 'admin' AND user_id = 'all')) AND is_read = FALSE",
                new String[]{userId});
    }

    public void clearForUser(PgConnection c, String userId) {
        c.query("DELETE FROM notifications WHERE user_id = $1 OR ($1 = 'admin' AND user_id = 'all')",
                new String[]{userId});
    }

    public void insert(PgConnection c, String id, String userId, String title, String msg,
                       String link, String adminTab) {
        c.query("INSERT INTO notifications(id, user_id, title, msg, link, admin_tab, is_read)" +
                " VALUES ($1,$2,$3,$4,$5,$6,FALSE) ON CONFLICT (id) DO NOTHING",
                new String[]{id, userId == null ? "all" : userId, title,
                        msg == null ? "" : msg, link == null ? "" : link, adminTab == null ? "" : adminTab});
    }

    /** Upsert from sync - never deletes, only inserts/refreshes. */
    public void upsertFromSync(PgConnection c, String id, String userId, String title, String msg,
                               String link, String adminTab, boolean isRead) {
        if (id.isEmpty()) throw com.apex.web.ApiException.validation("Sync notifications[] entries need an id");
        c.query("INSERT INTO notifications(id, user_id, title, msg, link, admin_tab, is_read)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7)" +
                " ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id, title=EXCLUDED.title," +
                " msg=EXCLUDED.msg, link=EXCLUDED.link, admin_tab=EXCLUDED.admin_tab, is_read=EXCLUDED.is_read",
                new String[]{id, userId == null || userId.isEmpty() ? "all" : userId,
                        title == null ? "" : title, msg == null ? "" : msg,
                        link == null ? "" : link, adminTab == null ? "" : adminTab, String.valueOf(isRead)});
    }

    public void delete(PgConnection c, String id) {
        c.query("DELETE FROM notifications WHERE id = $1", new String[]{id});
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM notifications", null));
        return Long.parseLong(r.first());
    }

    public static JsonObject toJson(QueryResult r, int row) {
        String[] x = r.rows.get(row);
        JsonObject o = new JsonObject();
        o.addProperty("id", x[0]);
        o.addProperty("userId", x[1]);
        o.addProperty("title", x[2] == null ? "" : x[2]);
        o.addProperty("msg", x[3] == null ? "" : x[3]);
        o.addProperty("link", x[4] == null ? "" : x[4]);
        o.addProperty("adminTab", x[5] == null ? "" : x[5]);
        o.addProperty("read", "t".equals(x[6]));
        o.addProperty("time", x[7] == null ? "" : x[7]);
        return o;
    }
}
