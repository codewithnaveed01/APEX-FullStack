package com.apex.web.controllers;

import com.apex.App;
import com.apex.model.User;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonObject;

/** /api/auth/* - register, login, logout, me. */
public final class AuthController {

    private final App app;

    public AuthController(App app) { this.app = app; }

    public void register(Router r) {
        r.post("/api/auth/login", Router.Level.PUBLIC, ctx -> {
            JsonObject s = app.auth.login(Json.getStr(ctx.body, "username", ""),
                    Json.getStr(ctx.body, "password", ""));
            if (s == null) throw ApiException.unauth("Invalid username or password");
            HttpUtil.sendJson(ctx.ex, 200, s);
        });

        r.post("/api/auth/register", Router.Level.PUBLIC, ctx -> {
            JsonObject s = app.auth.register(ctx.body);
            HttpUtil.sendJson(ctx.ex, 201, s);
        });

        r.post("/api/auth/logout", Router.Level.ANY, ctx -> {
            app.auth.logout(Json.getStr(ctx.session, "token", ""));
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.get("/api/auth/me", Router.Level.ANY, ctx -> {
            JsonObject me = ctx.session;
            JsonObject o = new JsonObject();
            o.addProperty("token", me.get("token").getAsString());
            o.addProperty("role", me.get("role").getAsString());
            o.addProperty("username", me.get("username").getAsString());
            o.addProperty("userId", me.get("userId").getAsString());
            o.addProperty("expiresAt", me.get("expiresAt").getAsString());
            User user = app.users.get(me.get("userId").getAsString());
            o.add("user", user == null ? new JsonObject() : user.publicJson());
            HttpUtil.sendJson(ctx.ex, 200, o);
        });
    }
}
