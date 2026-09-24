package com.apex.web.controllers;

import com.apex.App;
import com.apex.dao.ReviewRepo;
import com.apex.db.PgConnection;
import com.apex.model.Booking;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Reviews:
 *  GET  /api/reviews              - admin (all)
 *  GET  /api/reviews/car/{id}     - public (approved only)
 *  POST /api/reviews              - any authenticated user, own COMPLETED booking only
 *  POST /api/reviews/{id}/review  - admin approve/hide
 *  DELETE /api/reviews/{id}       - admin hide
 */
public final class ReviewController {

    private final App app;

    public ReviewController(App app) { this.app = app; }

    public void register(Router r) {
        r.get("/api/reviews", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (JsonObject rev : app.reviews.list()) a.add(rev);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.get("/api/reviews/car/{id}", Router.Level.PUBLIC, ctx -> {
            long carId = idParam(ctx);
            JsonArray a = new JsonArray();
            for (JsonObject rev : app.reviews.approvedFor(carId)) a.add(rev);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.post("/api/reviews", Router.Level.ANY, ctx -> {
            String bookingId = Validation.required(Json.getStr(ctx.body, "bookingId", ""), "bookingId");
            long carId = Validation.longValue(Json.getStr(ctx.body, "carId", ""), "carId");
            int rating = Json.getInt(ctx.body, "rating", 0);
            if (rating < 1 || rating > 5) throw ApiException.validation("Rating must be between 1 and 5");
            String body = Json.clean(Json.getStr(ctx.body, "body", ""));
            if (body.length() > 500) throw ApiException.validation("Review body is too long (max 500)");
            String userId = Json.getStr(ctx.session, "userId", "");

            long newId = app.db.tx(c -> {
                Booking b = app.bookings.get(c, bookingId);
                if (b == null) throw ApiException.notFound("Booking not found");
                if (!b.userId.equals(userId)) throw ApiException.forbidden("You can only review your own bookings");
                if (!"Completed".equals(b.status)) {
                    throw ApiException.conflict("Reviews are only allowed after the booking is completed");
                }
                if (app.reviews.existsForBooking(c, bookingId)) {
                    throw ApiException.conflict("This booking already has a review");
                }
                return app.reviews.insert(c, bookingId, carId, userId, rating, body);
            });
            JsonObject o = new JsonObject();
            o.addProperty("id", newId);
            o.addProperty("status", "Pending");
            HttpUtil.sendJson(ctx.ex, 201, o);
        });

        r.post("/api/reviews/{id}/review", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            String status = Json.clean(Json.getStr(ctx.body, "status", ""));
            if (!status.equals("Approved") && !status.equals("Hidden")) {
                throw ApiException.validation("status must be Approved or Hidden");
            }
            app.db.tx(c -> {
                if (app.reviews.get(c, id) == null) throw ApiException.notFound("Review not found");
                app.reviews.updateStatus(c, id, status);
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.del("/api/reviews/{id}", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            app.db.tx(c -> {
                if (app.reviews.get(c, id) == null) throw ApiException.notFound("Review not found");
                app.reviews.updateStatus(c, id, "Hidden");
                return null;
            });
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });
    }

    private static long idParam(Router.Ctx ctx) {
        String last = Json.clean(ctx.param("id"));
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            throw ApiException.bad("Invalid id");
        }
    }
}
