package com.apex.web.controllers;

import com.apex.App;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Settings + banned CNIC list:
 *  GET  /api/settings             - public (office info, phone numbers)
 *  PUT  /api/settings             - admin
 *  GET  /api/banned-cnic          - public (the app shows the warning)
 *  POST /api/banned-cnic          - admin add
 *  DELETE /api/banned-cnic/{cnic} - admin remove
 */
public final class SettingsController {

    private final App app;

    public SettingsController(App app) { this.app = app; }

    public void register(Router r) {
        r.get("/api/settings", Router.Level.PUBLIC, ctx -> {
            HttpUtil.sendJson(ctx.ex, 200, app.settings.getConfig());
        });

        r.put("/api/settings", Router.Level.ADMIN, ctx -> {
            JsonObject cfg = ctx.body;
            // sanity-check the money-ish fields so bad admin input never reaches pricing
            long driverRate = Json.getLong(cfg, "driverRate", -1);
            if (driverRate < 0) throw ApiException.validation("driverRate is invalid");
            long homeDelivery = Json.getLong(cfg, "homeDeliveryCharge", -1);
            if (homeDelivery < 0) throw ApiException.validation("homeDeliveryCharge is invalid");
            app.db.tx(c -> {
                app.settings.setConfig(c, cfg);
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.get("/api/banned-cnic", Router.Level.PUBLIC, ctx -> {
            JsonArray a = new JsonArray();
            for (JsonObject b : app.bans.list()) a.add(b);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.post("/api/banned-cnic", Router.Level.ADMIN, ctx -> {
            String cnic = Validation.cnic(Json.getStr(ctx.body, "cnic", ""));
            app.db.tx(c -> {
                app.bans.add(c, cnic);
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 201, o);
        });

        r.del("/api/banned-cnic/{cnic}", Router.Level.ADMIN, ctx -> {
            String cnic = Json.clean(ctx.param("cnic"));
            if (!cnic.matches("[0-9]{13}")) throw ApiException.validation("CNIC must be 13 digits");
            app.db.tx(c -> {
                app.bans.remove(c, cnic);
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });
    }
}
