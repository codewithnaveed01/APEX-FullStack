package com.apex.web.controllers;

import com.apex.App;
import com.apex.dao.ChatRepo;
import com.apex.model.ChatThread;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Support chat:
 *  POST /api/chats/send - customer or admin; server computes unread
 *                         counters (the frontend only renders them).
 *  GET  /api/chats      - admin: all threads
 */
public final class ChatController {

    private final App app;

    public ChatController(App app) { this.app = app; }

    public void register(Router r) {
        r.post("/api/chats/send", Router.Level.ANY, ctx -> {
            String text = Json.clean(Json.getStr(ctx.body, "text", ""));
            if (text.isEmpty()) throw ApiException.validation("Message is empty");
            if (text.length() > 2000) throw ApiException.validation("Message is too long (max 2000)");
            String from = Json.clean(Json.getStr(ctx.body, "from", ""));
            if (!from.equals("user") && !from.equals("admin")) {
                throw ApiException.validation("from must be user or admin");
            }
            // A customer cannot impersonate an admin or choose another thread.
            if (from.equals("admin") && !ctx.isAdmin()) {
                throw ApiException.forbidden("Only admins can send admin replies");
            }
            if (from.equals("user") && ctx.body.has("userId") &&
                    !Json.clean(Json.getStr(ctx.body, "userId", ""))
                            .equals(Json.getStr(ctx.session, "userId", ""))) {
                throw ApiException.forbidden("Cannot send as another user");
            }
            String threadUserId = from.equals("user")
                    ? Json.getStr(ctx.session, "userId", "")
                    : Json.getStr(ctx.body, "userId", "");
            if (threadUserId.isEmpty()) throw ApiException.bad("Missing chat user id");

            final String fFrom = from;
            final String fText = text;
            final String fUserId = threadUserId;
            ChatThread thread = app.db.tx(c -> {
                String userName = fFrom.equals("user")
                        ? Json.getStr(ctx.session, "username", "Customer")
                        : Json.getStr(ctx.body, "userName", "");
                String time = Json.clean(Json.getStr(ctx.body, "time", ""));
                if (time.isEmpty()) time = com.apex.service.AuthService.nowIso();
                java.util.List<String[]> msgs = java.util.Collections.singletonList(new String[]{fFrom, fText, time});
                int added = app.chats.merge(c, fUserId, userName, time, msgs);
                if (added > 0) {
                    if (fFrom.equals("user")) {
                        app.notifications.notifyAdmins(c, "New message from " + userName,
                                fText.length() > 100 ? fText.substring(0, 100) + "…" : fText,
                                "chat/" + fUserId, null);
                    } else {
                        app.notifications.notify(c, fUserId, "New support reply",
                                fText.length() > 100 ? fText.substring(0, 100) + "…" : fText,
                                "chat/" + fUserId, null);
                    }
                }
                return app.chats.threadFor(c, fUserId);
            });
            HttpUtil.sendJson(ctx.ex, 201, thread.toJson());
        });

        r.get("/api/chats", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (ChatThread t : app.chats.allThreads()) a.add(t.toJson());
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.get("/api/chats/mine", Router.Level.ANY, ctx -> {
            String userId = Json.getStr(ctx.session, "userId", "");
            ChatThread thread = app.db.with(c -> app.chats.threadFor(c, userId));
            HttpUtil.sendJson(ctx.ex, 200, thread.toJson());
        });

        r.post("/api/chats/read", Router.Level.ANY, ctx -> {
            String userId = ctx.isAdmin()
                    ? Json.clean(Json.getStr(ctx.body, "userId", ""))
                    : Json.getStr(ctx.session, "userId", "");
            if (userId.isEmpty()) throw ApiException.bad("Missing chat user id");
            ChatThread thread = app.db.tx(c -> app.chats.markRead(c, userId, ctx.isAdmin()));
            HttpUtil.sendJson(ctx.ex, 200, thread.toJson());
        });
    }
}
