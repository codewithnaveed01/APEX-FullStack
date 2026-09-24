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
 * Sensitive documents:
 *  POST /api/uploads              - any authenticated user (or admin)
 *  GET  /api/documents            - admin list (metadata only)
 *  GET  /api/documents/{id}       - owner or admin (full image)
 *  POST /api/documents/{id}/review - admin verify/reject
 */
public final class DocumentController {

    private final App app;

    public DocumentController(App app) { this.app = app; }

    public void register(Router r) {
        r.post("/api/uploads", Router.Level.ANY, ctx -> {
            JsonObject o = app.uploadService.upload(ctx.session, ctx.body);
            HttpUtil.sendJson(ctx.ex, 201, o);
        });

        r.get("/api/documents", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (JsonObject d : app.documents.list()) a.add(d);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.get("/api/documents/{id}", Router.Level.ANY, ctx -> {
            long id = idParam(ctx);
            HttpUtil.sendJson(ctx.ex, 200, app.uploadService.view(ctx.session, id));
        });

        r.post("/api/documents/{id}/review", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            app.uploadService.review(ctx.session, id, ctx.body);
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
            throw ApiException.bad("Invalid document id");
        }
    }
}
