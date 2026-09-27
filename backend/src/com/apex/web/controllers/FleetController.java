package com.apex.web.controllers;

import com.apex.App;
import com.apex.model.Car;
import com.apex.model.Driver;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Catalog endpoints.
 * GET /api/fleet and GET /api/drivers are public (customers need the
 * fleet to browse). Mutations are admin-only and the PUT /api/sync
 * full-replace is the canonical admin path.
 */
public final class FleetController {

    private final App app;

    public FleetController(App app) { this.app = app; }

    public void register(Router r) {
        // ---- cars ----
        r.get("/api/fleet", Router.Level.PUBLIC, ctx -> {
            JsonArray a = new JsonArray();
            for (Car car : app.cars.list()) a.add(car.toJson());
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.get("/api/fleet/{id}", Router.Level.PUBLIC, ctx -> {
            long id = idParam(ctx);
            Car car = app.cars.get(id);
            if (car == null) throw ApiException.notFound("Car not found");
            HttpUtil.sendJson(ctx.ex, 200, car.toJson());
        });

        r.post("/api/fleet", Router.Level.ADMIN, ctx -> {
            Car car = app.db.tx(c -> {
                JsonObject d = ctx.body.deepCopy();
                long newId = nextCarId(c);
                d.addProperty("id", newId);
                Car created = Car.fromJson(d);
                if (created.name.isEmpty()) throw ApiException.validation("Car name is required");
                app.cars.upsert(c, created);
                return created;
            });
            HttpUtil.sendJson(ctx.ex, 201, car.toJson());
        });

        r.put("/api/fleet/{id}", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            Car car = app.db.tx(c -> {
                Car existing = app.cars.get(c, id);
                if (existing == null) throw ApiException.notFound("Car not found");
                // Merge: a partial PUT must not wipe unspecified fields (ownerId etc.)
                JsonObject d = existing.data.deepCopy();
                for (var e : ctx.body.entrySet()) d.add(e.getKey(), e.getValue());
                d.addProperty("id", id);
                Car updated = Car.fromJson(d);
                app.cars.upsert(c, updated);
                return updated;
            });
            HttpUtil.sendJson(ctx.ex, 200, car.toJson());
        });

        r.del("/api/fleet/{id}", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            app.db.tx(c -> {
                if (app.cars.get(c, id) == null) throw ApiException.notFound("Car not found");
                app.cars.delete(c, id);
                return null;
            });
            ok(ctx);
        });

        // ---- drivers ----
        r.get("/api/drivers", Router.Level.PUBLIC, ctx -> {
            JsonArray a = new JsonArray();
            for (Driver d : app.drivers.list()) a.add(d.toJson());
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.post("/api/drivers", Router.Level.ADMIN, ctx -> {
            Driver d = app.db.tx(c -> {
                JsonObject o = ctx.body.deepCopy();
                if (Json.getStr(o, "id", "").isEmpty()) o.addProperty("id", nextDriverId(c));
                Driver created = Driver.fromJson(o);
                if (created.name.isEmpty()) throw ApiException.validation("Driver name is required");
                app.drivers.upsert(c, created);
                return created;
            });
            HttpUtil.sendJson(ctx.ex, 201, d.toJson());
        });

        r.put("/api/drivers/{id}", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            Driver d = app.db.tx(c -> {
                Driver existing = app.drivers.get(c, id);
                if (existing == null) throw ApiException.notFound("Driver not found");
                JsonObject o = existing.data.deepCopy();
                for (var e : ctx.body.entrySet()) o.add(e.getKey(), e.getValue());
                o.addProperty("id", id);
                Driver updated = Driver.fromJson(o);
                app.drivers.upsert(c, updated);
                return updated;
            });
            HttpUtil.sendJson(ctx.ex, 200, d.toJson());
        });

        r.del("/api/drivers/{id}", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            app.db.tx(c -> {
                if (app.drivers.get(c, id) == null) throw ApiException.notFound("Driver not found");
                app.drivers.delete(c, id);
                return null;
            });
            ok(ctx);
        });

        // ---- users (admin management) ----
        r.get("/api/users", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (var u : app.users.listAll()) {
                JsonObject d = u.data.deepCopy();
                d.remove("password");
                a.add(d);
            }
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.del("/api/users/{id}", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            app.db.tx(c -> {
                var u = app.users.get(c, id);
                if (u == null) throw ApiException.notFound("User not found");
                if (u.isAdmin()) throw ApiException.conflict("Cannot delete an admin account");
                app.users.remove(c, id);
                return null;
            });
            ok(ctx);
        });

        // ---- applications (owner onboarding) ----
        r.get("/api/applications", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (var a0 : app.apps.list()) a.add(a0.toJson());
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.get("/api/applications/{id}", Router.Level.ANY, ctx -> {
            String id = Json.clean(ctx.param("id"));
            JsonObject item = app.db.with(c -> {
                var application = app.apps.get(c, id);
                if (application == null) throw ApiException.notFound("Application not found");
                if (!ctx.isAdmin() && !application.userId.equals(Json.getStr(ctx.session, "userId", ""))) {
                    throw ApiException.forbidden("Access to this application is restricted");
                }
                return application.toJson();
            });
            HttpUtil.sendJson(ctx.ex, 200, item);
        });

        r.post("/api/applications", Router.Level.ANY, ctx -> {
            JsonObject created = app.db.tx(c -> {
                JsonObject d = ctx.body.deepCopy();
                if (Json.getStr(d, "id", "").isEmpty()) {
                    d.addProperty("id", "A" + Long.toString(System.currentTimeMillis(), 36).toUpperCase());
                }
                if (!"admin".equals(Json.getStr(ctx.session, "role", ""))) {
                    d.addProperty("userId", Json.getStr(ctx.session, "userId", ""));
                }
                var a0 = com.apex.model.Application.fromJson(d);
                app.apps.upsert(c, a0);
                app.notifications.notifyAdmins(c, "New owner application",
                        a0.owner + " applied with " + a0.brand + " " + a0.model,
                        "application/" + a0.id, "Partner applications");
                if (!a0.userId.isEmpty()) {
                    app.notifications.notify(c, a0.userId, "Application submitted",
                            "Your vehicle application has been received.", "application/" + a0.id, null);
                }
                return d;
            });
            HttpUtil.sendJson(ctx.ex, 201, created);
        });

        r.put("/api/applications/{id}", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            app.db.tx(c -> {
                var existing = app.apps.get(c, id);
                if (existing == null) throw ApiException.notFound("Application not found");
                JsonObject d = existing.data.deepCopy();
                for (var e : ctx.body.entrySet()) d.add(e.getKey(), e.getValue());
                d.addProperty("id", id);
                app.apps.upsert(c, com.apex.model.Application.fromJson(d));
                return null;
            });
            ok(ctx);
        });

        r.del("/api/applications/{id}", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            app.db.tx(c -> {
                if (app.apps.get(c, id) == null) throw ApiException.notFound("Application not found");
                app.apps.delete(c, id);
                return null;
            });
            ok(ctx);
        });

        // ---- notifications (admin) ----
        r.get("/api/notifications", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (JsonObject n : app.notifs.listAll()) a.add(n);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.post("/api/notifications", Router.Level.ADMIN, ctx -> {
            app.db.tx(c -> {
                JsonObject d = ctx.body.deepCopy();
                String id = Json.getStr(d, "id", "");
                if (id.isEmpty()) {
                    id = "N" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
                    d.addProperty("id", id);
                }
                app.notifs.upsertFromSync(c, id,
                        Json.getStr(d, "userId", "all"),
                        Json.getStr(d, "title", "Notification"),
                        Json.getStr(d, "msg", ""),
                        Json.getStr(d, "link", ""),
                        Json.getStr(d, "adminTab", ""),
                        Json.getBool(d, "read", false));
                return null;
            });
            ok(ctx);
        });

        r.del("/api/notifications/{id}", Router.Level.ADMIN, ctx -> {
            String id = Json.clean(ctx.param("id"));
            app.db.tx(c -> {
                app.notifs.delete(c, id);
                return null;
            });
            ok(ctx);
        });
    }

    private long nextCarId(com.apex.db.PgConnection c) {
        var r = c.query("SELECT COALESCE(max(id), 1000) + 1 FROM cars", null);
        return Long.parseLong(r.first());
    }

    private long nextDriverId(com.apex.db.PgConnection c) {
        var r = c.query("SELECT COALESCE(max(id), 100) + 1 FROM drivers", null);
        return Long.parseLong(r.first());
    }

    private long idParam(Router.Ctx ctx) {
        String last = Json.clean(ctx.param("id"));
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            throw ApiException.bad("Invalid id");
        }
    }

    static void ok(Router.Ctx ctx) throws java.io.IOException {
        JsonObject o = new JsonObject();
        o.addProperty("status", "ok");
        HttpUtil.sendJson(ctx.ex, 200, o);
    }
}
