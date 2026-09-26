package com.apex.web.controllers;

import com.apex.App;
import com.apex.model.ChatThread;
import com.apex.util.Json;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Authenticated, recipient-scoped notification feed and read state. */
public final class NotificationController {
    private final App app;

    public NotificationController(App app) { this.app = app; }

    private String recipient(Router.Ctx ctx) {
        return ctx.isAdmin() ? "admin" : Json.getStr(ctx.session, "userId", "");
    }

    public void register(Router r) {
        // One small, recipient-scoped request per poll (rather than two).
        r.get("/api/live", Router.Level.ANY, ctx -> {
            JsonObject result = app.db.with(c -> {
                JsonObject out = new JsonObject();
                JsonArray notifications = new JsonArray();
                for (JsonObject n : app.notifs.listForUser(c, recipient(ctx))) notifications.add(n);
                out.add("notifications", notifications);
                JsonArray chats = new JsonArray();
                if (ctx.isAdmin()) {
                    for (ChatThread t : app.chats.allThreads(c)) chats.add(t.toJson());
                } else {
                    chats.add(app.chats.threadFor(c, recipient(ctx)).toJson());
                }
                out.add("chats", chats);
                return out;
            });
            HttpUtil.sendJson(ctx.ex, 200, result);
        });

        r.get("/api/notifications/mine", Router.Level.ANY, ctx -> {
            JsonArray out = new JsonArray();
            app.db.with(c -> {
                for (JsonObject n : app.notifs.listForUser(c, recipient(ctx))) out.add(n);
                return null;
            });
            HttpUtil.sendJson(ctx.ex, 200, out);
        });

        r.post("/api/notifications/read", Router.Level.ANY, ctx -> {
            app.db.tx(c -> {
                app.notifs.markReadForUser(c, recipient(ctx));
                return null;
            });
            JsonObject result = new JsonObject();
            result.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, result);
        });

        r.del("/api/notifications/mine", Router.Level.ANY, ctx -> {
            app.db.tx(c -> {
                app.notifs.clearForUser(c, recipient(ctx));
                return null;
            });
            JsonObject result = new JsonObject();
            result.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, result);
        });
    }
}
