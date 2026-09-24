package com.apex.web.controllers;

import com.apex.App;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonObject;

/**
 * Admin surface:
 *  GET /api/bootstrap - full state for the current session
 *                       (admin: everything; customer: their own slice)
 *  PUT /api/sync      - admin pushes state (fleet/drivers replace,
 *                       users/orders/etc. upsert-only, wallets ignored)
 *  GET /api/stats     - admin dashboard numbers (all real)
 *  GET /api/health    - liveness incl. database check
 */
public final class AdminController {

    private final App app;

    public AdminController(App app) { this.app = app; }

    public void register(Router r) {
        r.get("/api/bootstrap", Router.Level.PUBLIC, ctx -> {
            HttpUtil.sendJson(ctx.ex, 200, app.stateService.bootstrap(ctx.session));
        });

        r.put("/api/sync", Router.Level.ADMIN, ctx -> {
            HttpUtil.sendJson(ctx.ex, 200, app.stateService.sync(ctx.session, ctx.body));
        });

        r.get("/api/stats", Router.Level.ADMIN, ctx -> {
            HttpUtil.sendJson(ctx.ex, 200, app.statsService.stats());
        });

        r.get("/api/health", Router.Level.PUBLIC, ctx -> {
            HttpUtil.sendJson(ctx.ex, 200, health());
        });
    }

    public JsonObject health() {
        JsonObject db = new JsonObject();
        boolean ok = app.db.ping();
        db.addProperty("ok", ok);
        db.addProperty("host", app.cfg.dbHost);
        db.addProperty("port", app.cfg.dbPort);
        db.addProperty("database", app.cfg.dbDatabase);
        JsonObject o = new JsonObject();
        o.addProperty("status", ok ? "ok" : "degraded");
        o.addProperty("db", ok ? "up" : "down");
        o.addProperty("time", com.apex.service.AuthService.nowIso());
        o.addProperty("version", "2.0.0");
        o.add("database", db);
        return o;
    }
}
