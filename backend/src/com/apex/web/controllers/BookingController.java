package com.apex.web.controllers;

import com.apex.App;
import com.apex.model.Booking;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonObject;

/**
 * Bookings ("orders" in the frontend):
 *  POST /api/orders                 - create (customer or admin)
 *  GET  /api/orders                 - list (admin)
 *  GET  /api/orders/{id}            - owner or admin
 *  POST /api/bookings/{id}/pickup   - admin: Pickup Pending -> Active
 *  POST /api/bookings/{id}/return   - admin: Active -> Completed (late fees + payout)
 *  POST /api/bookings/{id}/cancel   - owner or admin (5% fee)
 *  GET  /api/availability           - public window check
 */
public final class BookingController {

    private final App app;

    public BookingController(App app) { this.app = app; }

    public void register(Router r) {
        r.post("/api/orders", Router.Level.ANY, ctx -> {
            JsonObject order = app.bookingService.create(ctx.session, ctx.body);
            HttpUtil.sendJson(ctx.ex, 201, order);
        });

        r.get("/api/orders", Router.Level.ADMIN, ctx -> {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (Booking b : app.bookings.listAll()) a.add(b.toJson());
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.get("/api/orders/{id}", Router.Level.ANY, ctx -> {
            String id = Json.clean(ctx.param("id"));
            final JsonObject[] out = new JsonObject[1];
            app.db.with(c -> {
                Booking b = app.bookings.get(c, id);
                if (b == null) throw ApiException.notFound("Booking not found");
                boolean owner = b.userId != null && b.userId.equals(Json.getStr(ctx.session, "userId", ""));
                if (!ctx.isAdmin() && !owner) throw ApiException.forbidden("Access to this booking is restricted");
                out[0] = b.data.deepCopy();
                return null;
            });
            HttpUtil.sendJson(ctx.ex, 200, out[0]);
        });

        r.put("/api/orders/{id}", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            app.db.tx(c -> {
                Booking existing = app.bookings.get(c, id);
                if (existing == null) throw ApiException.notFound("Booking not found");
                JsonObject d = existing.data.deepCopy();
                for (var e : ctx.body.entrySet()) d.add(e.getKey(), e.getValue());
                d.addProperty("id", id);
                app.bookings.upsert(c, Booking.fromJson(d));
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.del("/api/orders/{id}", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            app.db.tx(c -> {
                if (app.bookings.get(c, id) == null) throw ApiException.notFound("Booking not found");
                if (!"Cancelled".equals(app.bookings.get(c, id).status)) {
                    throw ApiException.conflict("Cancel the booking before deleting it");
                }
                app.bookings.delete(c, id);
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.post("/api/bookings/{id}/pickup", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            Long driverId = null;
            if (ctx.body.has("driverId")) {
                long v = Json.getLong(ctx.body, "driverId", 0);
                driverId = v > 0 ? v : null;
            }
            JsonObject b = app.bookingService.pickup(ctx.session, id, driverId);
            HttpUtil.sendJson(ctx.ex, 200, b);
        });

        r.post("/api/bookings/{id}/return", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            JsonObject b = app.bookingService.ret(ctx.session, id, ctx.body);
            HttpUtil.sendJson(ctx.ex, 200, b);
        });

        r.post("/api/bookings/{id}/cancel", Router.Level.ANY, ctx -> {
            String id = Json.clean(ctx.param("id"));
            JsonObject b = app.bookingService.cancel(ctx.session, id);
            HttpUtil.sendJson(ctx.ex, 200, b);
        });

        r.get("/api/availability", Router.Level.PUBLIC, ctx -> {
            String carId = Json.clean(ctx.query.get("carId"));
            String start = Json.clean(ctx.query.get("start"));
            String end = Json.clean(ctx.query.get("end"));
            JsonObject a = app.bookingService.availability(carId, start, end);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });
    }
}
