package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.ChatThread;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ChatRepo {

    private final Database db;

    public ChatRepo(Database db) { this.db = db; }

    public List<ChatThread> allThreads() {
        return db.with(c -> allThreads(c));
    }

    public List<ChatThread> allThreads(PgConnection c) {
        QueryResult t = c.query("SELECT user_id, user_name, last_time, unread_admin, unread_user" +
                " FROM chat_threads ORDER BY last_time IS NULL, last_time DESC", null);
        List<ChatThread> out = new ArrayList<>();
        for (int i = 0; i < t.rowCount(); i++) {
            String userId = t.rows.get(i)[0];
            out.add(build(c, userId, t, i));
        }
        return out;
    }

    public ChatThread threadFor(PgConnection c, String userId) {
        QueryResult t = c.query("SELECT user_id, user_name, last_time, unread_admin, unread_user" +
                " FROM chat_threads WHERE user_id = $1", new String[]{userId});
        if (t.rowCount() == 0) return new ChatThread(userId, "", null, 0, 0, new JsonArray());
        return build(c, userId, t, 0);
    }

    private ChatThread build(PgConnection c, String userId, QueryResult t, int row) {
        String[] r = t.rows.get(row);
        QueryResult m = c.query("SELECT sender, body, created_at FROM chat_messages WHERE user_id = $1 ORDER BY id",
                new String[]{userId});
        JsonArray msgs = new JsonArray();
        for (int i = 0; i < m.rowCount(); i++) {
            String[] x = m.rows.get(i);
            JsonObject mo = new JsonObject();
            mo.addProperty("from", "admin".equals(x[0]) ? "admin" : "user");
            mo.addProperty("sender", x[0]);
            mo.addProperty("text", x[1]);
            mo.addProperty("time", x[2] == null ? "" : x[2]);
            msgs.add(mo);
        }
        return new ChatThread(r[0], r[1], r[2], r[3] == null ? 0 : Integer.parseInt(r[3]),
                r[4] == null ? 0 : Integer.parseInt(r[4]), msgs);
    }

    /** Upsert thread meta + insert messages (deduped) + update unread counters. Returns inserted count. */
    public int merge(PgConnection c, String userId, String userName, String lastTimeIso,
                     List<String[]> messages) {
        c.query("INSERT INTO chat_threads(user_id, user_name, last_time)" +
                " VALUES ($1,$2,NULLIF($3,'')::timestamptz)" +
                " ON CONFLICT (user_id) DO UPDATE SET" +
                " user_name = CASE WHEN EXCLUDED.user_name IS NOT NULL AND EXCLUDED.user_name <> ''" +
                "        THEN EXCLUDED.user_name ELSE chat_threads.user_name END," +
                " last_time = COALESCE(EXCLUDED.last_time, chat_threads.last_time)," +
                " updated_at = now()",
                new String[]{userId, Json.clean(userName), Json.clean(lastTimeIso)});
        if (messages == null || messages.isEmpty()) return 0;
        int userAdded = 0, adminAdded = 0;
        for (String[] m : messages) {
            // m = {sender, body, timeIso}
            QueryResult r = c.query(
                    "INSERT INTO chat_messages(user_id, sender, body, created_at)" +
                    " VALUES ($1,$2,$3,COALESCE(NULLIF($4,'')::timestamptz, now()))" +
                    " ON CONFLICT (user_id, sender, md5(body), created_at) DO NOTHING" +
                    " RETURNING id",
                    new String[]{userId, "admin".equals(m[0]) ? "admin" : "user",
                            Json.clean(m[1]), Json.clean(m[2])});
            if (r.rowCount() > 0) {
                if ("admin".equals(m[0])) adminAdded++; else userAdded++;
            }
        }
        if (userAdded > 0) {
            c.query("UPDATE chat_threads SET unread_admin = unread_admin + $1, updated_at = now() WHERE user_id = $2",
                    new String[]{String.valueOf(userAdded), userId});
        }
        if (adminAdded > 0) {
            c.query("UPDATE chat_threads SET unread_user = unread_user + $1, updated_at = now() WHERE user_id = $2",
                    new String[]{String.valueOf(adminAdded), userId});
        }
        return userAdded + adminAdded;
    }

    /** Reset only the reader's unread counter; never clear the other side's. */
    public ChatThread markRead(PgConnection c, String userId, boolean asAdmin) {
        c.query("UPDATE chat_threads SET " + (asAdmin ? "unread_admin" : "unread_user") +
                " = 0, updated_at = now() WHERE user_id = $1", new String[]{userId});
        return threadFor(c, userId);
    }

    /** Admin marked the user's thread as read (from sync or read endpoint). */
    public void setCounters(PgConnection c, String userId, int unreadAdmin, int unreadUser) {
        c.query("UPDATE chat_threads SET unread_admin = $1, unread_user = $2, updated_at = now() WHERE user_id = $3",
                new String[]{String.valueOf(Math.max(0, unreadAdmin)), String.valueOf(Math.max(0, unreadUser)), userId});
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM chat_threads", null));
        return Long.parseLong(r.first());
    }

    public long unreadTotal(PgConnection c) {
        QueryResult r = c.query("SELECT COALESCE(sum(unread_admin + unread_user), 0) FROM chat_threads", null);
        return r.rowCount() > 0 && r.rows.get(0)[0] != null ? Long.parseLong(r.rows.get(0)[0]) : 0;
    }
}
