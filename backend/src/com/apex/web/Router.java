package com.apex.web;

import com.apex.log.Logger;
import com.apex.service.AuthService;
import com.apex.util.Json;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny routing layer over com.sun.net.httpserver.
 *
 * Levels:
 *  PUBLIC - no token required (optionally authenticated when a token is sent)
 *  ANY    - token required (customer or admin)
 *  ADMIN  - admin token required
 */
public final class Router {

    public enum Level { PUBLIC, ANY, ADMIN }

    /** Everything a handler needs about the current request. */
    public static final class Ctx {
        public final HttpExchange ex;
        public final JsonObject body;
        public final Map<String, String> query;
        public final List<String> segments;
        public final String method;
        /** Path parameters resolved from {name} segments. */
        public Map<String, String> params = new java.util.HashMap<>();
        /** null when anonymous. */
        public JsonObject session;
        public boolean isAdmin() { return session != null && "admin".equals(Json.getStr(session, "role", "")); }
        public String param(String name) {
            String v = params.get(name);
            return v == null ? "" : v;
        }
        public Ctx(HttpExchange ex, JsonObject body, Map<String, String> query, List<String> segments, String method) {
            this.ex = ex; this.body = body; this.query = query; this.segments = segments; this.method = method;
        }
    }

    public interface Handler { void handle(Ctx ctx) throws Exception; }

    private static final class Route {
        final Level level; final Handler handler;
        Route(Level level, Handler handler) { this.level = level; this.handler = handler; }
    }

    private final AuthService auth;
    private final Map<String, Route> routes = new HashMap<>();
    private final RateLimiter rateLimiter;

    public Router(AuthService auth, RateLimiter rateLimiter) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
    }

    public Router get(String path, Level level, Handler h)  { add("GET", path, level, h); return this; }
    public Router post(String path, Level level, Handler h) { add("POST", path, level, h); return this; }
    public Router put(String path, Level level, Handler h)  { add("PUT", path, level, h); return this; }
    public Router del(String path, Level level, Handler h)  { add("DELETE", path, level, h); return this; }

    private void add(String method, String path, Level level, Handler h) {
        routes.put(method + " " + path, new Route(level, h));
    }

    private static final class Matched {
        final Route route;
        final Map<String, String> params;
        Matched(Route route, Map<String, String> params) { this.route = route; this.params = params; }
    }

    private Matched match(String method, List<String> segments) {
        String exact = method + " /" + String.join("/", segments);
        Route r = routes.get(exact);
        if (r != null) return new Matched(r, new java.util.HashMap<String, String>());
        for (Map.Entry<String, Route> e : routes.entrySet()) {
            String[] mk = e.getKey().split(" ", 2);
            if (!mk[0].equals(method)) continue;
            List<String> mSegs = splitPath(mk[1]);
            if (mSegs.size() != segments.size()) continue;
            Map<String, String> params = new java.util.HashMap<>();
            boolean ok = true;
            for (int i = 0; i < mSegs.size(); i++) {
                String seg = mSegs.get(i);
                if (seg.startsWith("{") && seg.endsWith("}")) {
                    params.put(seg.substring(1, seg.length() - 1), segments.get(i));
                    continue;
                }
                if (!seg.equals(segments.get(i))) { ok = false; break; }
            }
            if (ok) return new Matched(e.getValue(), params);
        }
        return null;
    }

    public HttpHandler handler() {
        return ex -> {
            try {
                String method = ex.getRequestMethod().toUpperCase();
                if ("OPTIONS".equals(method)) {
                    ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
                    ex.sendResponseHeaders(204, -1);
                    ex.close();
                    return;
                }
                String path = ex.getRequestURI().getPath();
                List<String> segments = splitPath(path);
                Matched matched = match(method, segments);
                if (matched == null) {
                    HttpUtil.sendError(ex, 404, "Not found: " + method + " " + path);
                    return;
                }
                Route route = matched.route;
                if (rateLimiter.blocked(ex, path, method)) {
                    HttpUtil.sendError(ex, 429, "Too many requests - slow down");
                    return;
                }
                Ctx ctx = new Ctx(ex,
                        (method.equals("POST") || method.equals("PUT")) ? Json.readObject(ex.getRequestBody()) : new JsonObject(),
                        HttpUtil.query(ex),
                        segments,
                        method);
                ctx.params = matched.params;
                String token = HttpUtil.bearer(ex);
                if (route.level != Level.PUBLIC) {
                    JsonObject sess = token != null ? auth.validate(token) : null;
                    if (sess == null) {
                        HttpUtil.sendError(ex, 401, "Authentication required");
                        return;
                    }
                    ctx.session = sess;
                    if (route.level == Level.ADMIN && !"admin".equals(Json.getStr(sess, "role", ""))) {
                        HttpUtil.sendError(ex, 403, "Admin access required");
                        return;
                    }
                } else if (token != null) {
                    ctx.session = auth.validate(token);
                }
                route.handler.handle(ctx);
            } catch (ApiException e) {
                try {
                    HttpUtil.sendError(ex, e.status, e.getMessage());
                } catch (IOException ioe) { /* connection gone */ }
            } catch (Exception e) {
                Logger.error("Unhandled error on " + ex.getRequestMethod() + " " + ex.getRequestURI(), e);
                try {
                    HttpUtil.sendError(ex, 500, "Server error");
                } catch (IOException ioe) { /* connection gone */ }
            } finally {
                ex.close();
            }
        };
    }

    private static List<String> splitPath(String p) {
        List<String> out = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (!seg.isEmpty()) out.add(seg);
        }
        return out;
    }
}
