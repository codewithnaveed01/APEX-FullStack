package com.apex.web.controllers;

import com.apex.App;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;

/** Admin-only driver account and completed-booking payout workflow. */
public final class DriverPayoutController {
    private final App app;
    public DriverPayoutController(App app) { this.app = app; }

    public void register(Router r) {
        // Register both verbs so an anonymous probe receives 401 rather than 404.
        r.get("/api/driver-payouts", Router.Level.ADMIN, ctx -> {
            String bookingId = Json.clean(ctx.query.get("bookingId"));
            String pos = Json.clean(ctx.query.get("bookingItemPos"));
            if (!bookingId.isEmpty() || !pos.isEmpty()) {
                if (bookingId.isEmpty()) throw ApiException.bad("bookingId is required");
                try {
                    int itemPos = Integer.parseInt(pos);
                    com.google.gson.JsonObject payout = app.driverPayoutService.itemPayout(bookingId, itemPos);
                    HttpUtil.sendJson(ctx.ex, 200, payout == null ? new com.google.gson.JsonObject() : payout);
                } catch (NumberFormatException e) { throw ApiException.bad("Invalid booking item position"); }
                return;
            }
            HttpUtil.sendJson(ctx.ex, 200, new com.google.gson.JsonArray());
        });
        r.post("/api/driver-payouts", Router.Level.ADMIN, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.driverPayoutService.pay(ctx.session, ctx.body)));
        r.get("/api/drivers/{id}/payout-account", Router.Level.ADMIN, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.driverPayoutService.account(driverId(ctx))));
        r.put("/api/drivers/{id}/payout-account", Router.Level.ADMIN, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.driverPayoutService.saveAccount(driverId(ctx), ctx.body)));
        r.get("/api/drivers/{id}/payouts", Router.Level.ADMIN, ctx ->
                HttpUtil.sendJson(ctx.ex, 200, app.driverPayoutService.history(driverId(ctx))));
    }

    private static long driverId(Router.Ctx ctx) {
        try { return Long.parseLong(Json.clean(ctx.param("id"))); }
        catch (NumberFormatException e) { throw ApiException.bad("Invalid driver id"); }
    }
}
