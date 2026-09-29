package com.apex.web.controllers;

import com.apex.App;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonObject;

/** Authenticated owner marketplace, earnings and payout account APIs. */
public final class OwnerController {
    private final App app;
    public OwnerController(App app) { this.app = app; }

    public void register(Router r) {
        r.get("/api/owner/cars", Router.Level.ANY, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.ownerVehicleService.listedCars(ctx.session)));

        r.put("/api/owner/cars/{id}/edit", Router.Level.ANY, ctx -> {
            JsonObject result = app.ownerVehicleService.requestCarEdit(ctx.session, carId(ctx), ctx.body);
            HttpUtil.sendJson(ctx.ex, 200, result);
        });

        r.del("/api/owner/cars/{id}", Router.Level.ANY, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.ownerVehicleService.deleteCar(ctx.session, carId(ctx))));

        r.get("/api/owner/payout-account", Router.Level.ANY, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.ownerVehicleService.payoutAccount(ctx.session, null)));

        r.put("/api/owner/payout-account", Router.Level.ANY, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.ownerVehicleService.savePayoutAccount(ctx.session, ctx.body)));

        r.get("/api/admin/owners/{id}/payout-account", Router.Level.ADMIN, ctx ->
                HttpUtil.sendJson(ctx.ex, 200,
                        app.ownerVehicleService.payoutAccount(ctx.session, Json.clean(ctx.param("id")))));

        r.get("/api/owner/wallet", Router.Level.ANY, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.ownerVehicleService.earnings(ctx.session)));

        // A document becomes public only through a live, approved car link.
        r.get("/api/vehicle-images/{id}", Router.Level.PUBLIC, ctx -> {
            long id = documentId(ctx);
            JsonObject meta = app.db.with(c -> app.documents.publicVehicleImage(c, id));
            if (meta == null) throw ApiException.notFound("Vehicle image not available");
            byte[] bytes = app.db.with(c -> app.documents.content(c, id));
            if (bytes == null || bytes.length == 0) throw ApiException.notFound("Vehicle image content missing");
            HttpUtil.sendBytes(ctx.ex, 200, meta.get("contentType").getAsString(), bytes,
                    "public, max-age=86400, immutable");
        });
    }

    private static long carId(Router.Ctx ctx) {
        try { return Long.parseLong(Json.clean(ctx.param("id"))); }
        catch (NumberFormatException e) { throw ApiException.bad("Invalid car id"); }
    }
    private static long documentId(Router.Ctx ctx) {
        try { return Long.parseLong(Json.clean(ctx.param("id"))); }
        catch (NumberFormatException e) { throw ApiException.bad("Invalid image id"); }
    }
}
