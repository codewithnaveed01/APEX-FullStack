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
            // a customer may only talk from their own thread
            if (!from.equals("admin") && !"admin".equals(Json.getStr(ctx.session, "role", ""))) {
                from = "user";
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
                app.chats.merge(c, fUserId, userName, time, msgs);
                return app.chats.threadFor(c, fUserId);
            });
            HttpUtil.sendJson(ctx.ex, 201, thread.toJson());
        });

        r.get("/api/chats", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (ChatThread t : app.chats.allThreads()) a.add(t.toJson());
            HttpUtil.sendJson(ctx.ex, 200, a);
        });
    }
}
