package com.apex;

import com.apex.config.AppConfig;
import com.apex.db.Database;
import com.apex.db.Migrate;
import com.apex.log.Logger;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.apex.web.StaticHandler;
import com.apex.web.RateLimiter;
import com.apex.web.controllers.*;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * APEX backend entry point.
 *
 * 1. load config (env-driven)
 * 2. open the PostgreSQL pool (retry for cloud cold starts)
 * 3. run migrations
 * 4. seed (admin once, fleet from seed.json when empty)
 * 5. serve API + frontend on $PORT
 */
public final class Main {

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
        Logger.info("APEX backend starting (Java {})...", System.getProperty("java.version"));

        AppConfig cfg = AppConfig.load();
        Logger.info("port={} static={} db={}", cfg.port, cfg.staticDir, cfg.databaseUrl());

        Database db = Database.open(new Database.Cfg()
                .host(cfg.dbHost).port(cfg.dbPort).user(cfg.dbUser)
                .password(cfg.dbPassword).database(cfg.dbDatabase).ssl(cfg.dbSsl),
                cfg.dbPoolSize, 30, 2000);

        Migrate.run(db, Paths.get(cfg.home, "migrations"));

        App app = new App(cfg, db);
        app.seedService.run();

        Router router = new Router(app.auth, new RateLimiter());
        new AdminController(app).register(router);
        new AuthController(app).register(router);
        new FleetController(app).register(router);
        new BookingController(app).register(router);
        new PaymentController(app).register(router);
        new DocumentController(app).register(router);
        new ReviewController(app).register(router);
        new ChatController(app).register(router);
        new SettingsController(app).register(router);

        StaticHandler statics = new StaticHandler(cfg.staticDir);
        AdminController admin = new AdminController(app);

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", cfg.port), 128);
        } catch (IOException e) {
            Logger.error("Cannot bind port {}: {}", cfg.port, e.getMessage());
            System.exit(1);
            return;
        }
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/health")) {
                try {
                    HttpUtil.sendJson(ex, 200, admin.health());
                } finally {
                    ex.close();
                }
            } else if (path.startsWith("/api/") || path.startsWith("/healthz")) {
                try {
                    router.handler().handle(ex);
                } finally {
                    ex.close();
                }
            } else {
                try {
                    statics.serve(ex);
                } finally {
                    ex.close();
                }
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(32));
        server.start();
        Logger.info("APEX ready in {} ms - http://0.0.0.0:{} (frontend: {})",
                System.currentTimeMillis() - t0, cfg.port, cfg.staticDir);
    }
}
