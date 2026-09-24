package com.apex.web;

import com.apex.service.AuthService;
import com.apex.util.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny path router with {param} segments and three access levels:
 *  PUBLIC - anyone
 *  ANY    - any logged-in user (customer or admin)
 *  ADMIN  - admin role only (normal users can NEVER reach these)
 */
public class Router {

    public interface Handler {
        void handle(Ctx ctx) throws Exception;
    }

    /** Per-request context handed to every handler. */
    public static class Ctx {
        public final HttpExchange ex;
        public final Map<String, String> params;
        public JsonObject body;
        public JsonObject session;

        Ctx(HttpExchange ex, Map<String, String> params) {
            this.ex = ex;
            this.params = params;
        }

        public String param(String key) { return params.get(key); }
        public boolean isAdmin() { return session != null && "admin".equals(Json.str(session, "role")); }
        public String userId() { return session == null ? null : Json.str(session, "username"); }
        public void ok(Object payload) throws IOException { HttpUtil.sendJson(ex, 200, payload); }
        public void created(Object payload) throws IOException { HttpUtil.sendJson(ex, 201, payload); }
    }

    private static final class Route {
        final String method;
        final String[] segments;
        final String access;   // PUBLIC | ANY | ADMIN
        final Handler handler;

        Route(String method, String path, String access, Handler handler) {
            this.method = method;
            this.segments = split(path);
            this.access = access;
            this.handler = handler;
        }
    }

    private final List<Route> routes = new ArrayList<>();
    private final AuthService auth;

    public Router(AuthService auth) {
        this.auth = auth;
    }

    public Router add(String method, String path, String access, Handler handler) {
        routes.add(new Route(method, path, access, handler));
        return this;
    }

    public Router get(String path, Handler h) { return add("GET", path, "PUBLIC", h); }
    public Router post(String path, Handler h) { return add("POST", path, "ADMIN", h); }
    public Router put(String path, Handler h) { return add("PUT", path, "ADMIN", h); }
    public Router delete(String path, Handler h) { return add("DELETE", path, "ADMIN", h); }
    public Router any(String method, String path, Handler h) { return add(method, path, "ANY", h); }

    /** Handles the exchange; returns false when no route matched (404 by caller). */
    public boolean handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("OPTIONS".equals(method)) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        String[] pathSegs = split(URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8));
        for (Route r : routes) {
            if (!r.method.equals(method)) continue;
            Map<String, String> params = match(r.segments, pathSegs);
            if (params == null) continue;
            Ctx ctx = new Ctx(ex, params);
            String raw = HttpUtil.readBody(ex);
            if (!raw.isBlank()) {
                try {
                    JsonElement el = Json.parse(raw);
                    if (el.isJsonObject()) ctx.body = el.getAsJsonObject();
                } catch (Exception ignored) { }
            }
            try {
                if (!"PUBLIC".equals(r.access)) {
                    ctx.session = auth.validate(HttpUtil.bearer(ex));
                    if (ctx.session == null) {
                        HttpUtil.sendError(ex, 401, "Login required");
                        return true;
                    }
                    if ("ADMIN".equals(r.access) && !ctx.isAdmin()) {
                        HttpUtil.sendError(ex, 403, "Admin access required");
                        return true;
                    }
                }
                r.handler.handle(ctx);
            } catch (IllegalArgumentException e) {
                HttpUtil.sendError(ex, 400, e.getMessage());
            } catch (Exception e) {
                HttpUtil.sendError(ex, 500, "Server error");
            }
            return true;
        }
        return false;
    }

    private static String[] split(String path) {
        List<String> parts = new ArrayList<>();
        for (String p : path.split("/")) if (!p.isBlank()) parts.add(p);
        return parts.toArray(new String[0]);
    }

    private static Map<String, String> match(String[] route, String[] path) {
        if (route.length != path.length) return null;
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < route.length; i++) {
            if (route[i].startsWith("{") && route[i].endsWith("}")) {
                params.put(route[i].substring(1, route[i].length() - 1), path[i]);
            } else if (!route[i].equals(path[i])) {
                return null;
            }
        }
        return params;
    }
}
