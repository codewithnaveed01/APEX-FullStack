package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonObject;

/** App settings. The admin UI's `config` object is stored under one key. */
public final class SettingsRepo {

    private final Database db;
    private static final String KEY = "config";

    public SettingsRepo(Database db) { this.db = db; }

    public JsonObject getConfig() {
        return db.with(c -> getConfig(c));
    }

    public JsonObject getConfig(PgConnection c) {
        QueryResult r = c.query("SELECT value FROM app_settings WHERE setting_key = $1", new String[]{KEY});
        if (r.rowCount() == 0 || r.rows.get(0)[0] == null) return new JsonObject();
        try {
            return com.google.gson.JsonParser.parseString(r.rows.get(0)[0]).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    public void setConfig(PgConnection c, JsonObject config) {
        c.query("INSERT INTO app_settings(setting_key, value) VALUES ($1, $2)" +
                " ON CONFLICT (setting_key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()",
                new String[]{KEY, (config == null ? new JsonObject() : config).toString()});
    }
}
