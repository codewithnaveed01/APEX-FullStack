package com.apex.service;

import com.apex.config.AppConfig;
import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.model.Car;
import com.apex.model.Driver;
import com.apex.model.User;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Startup seeding:
 *  - the admin account is created exactly once (never duplicated,
 *    password from APEX_ADMIN_USER / APEX_ADMIN_PASS, stored BCrypt)
 *  - an empty database gets the bundled seed.json (fleet, drivers, config)
 */
public final class SeedService {

    private final Database db;
    private final UserRepo users;
    private final CarRepo cars;
    private final DriverRepo drivers;
    private final SettingsRepo settings;
    private final AppConfig cfg;

    public SeedService(Database db, UserRepo users, CarRepo cars, DriverRepo drivers,
                       SettingsRepo settings, AppConfig cfg) {
        this.db = db; this.users = users; this.cars = cars; this.drivers = drivers;
        this.settings = settings; this.cfg = cfg;
    }

    public void run() {
        ensureAdmin();
        seedFleetIfEmpty();
    }

    private void ensureAdmin() {
        db.tx(c -> {
            User existing = users.findByLogin(c, cfg.adminUsername);
            if (existing != null) {
                if (!existing.isAdmin()) {
                    throw ApiException.server("User '" + cfg.adminUsername + "' exists but is not an admin - refusing to seed");
                }
                com.apex.log.Logger.info("Admin account '{}' already present - skipping", cfg.adminUsername);
                return null;
            }
            String id = "UADMIN";
            JsonObject data = new JsonObject();
            data.addProperty("id", id);
            data.addProperty("username", cfg.adminUsername);
            data.addProperty("email", "admin@apex.local");
            data.addProperty("name", "APEX Admin");
            data.addProperty("phone", "");
            data.addProperty("cnic", "");
            data.addProperty("role", "admin");
            User u = new User(id, cfg.adminUsername, "admin@apex.local", "", "APEX Admin", "",
                    "admin", BCrypt.hashpw(cfg.adminPassword, BCrypt.gensalt(10)),
                    AuthService.nowIso(), data);
            users.insert(c, u, u.passwordHash);
            com.apex.log.Logger.info("Seeded admin account '{}'", cfg.adminUsername);
            return null;
        });
    }

    private void seedFleetIfEmpty() {
        if (cars.count() > 0) {
            com.apex.log.Logger.info("Fleet already present ({} cars) - skipping seed.json", cars.count());
            return;
        }
        Path seed = Paths.get(cfg.home, "seed.json");
        if (!Files.isRegularFile(seed)) {
            com.apex.log.Logger.warn("seed.json not found at {} - starting with an empty fleet", seed);
            return;
        }
        JsonObject s = Json.readFile(seed);
        db.tx(c -> {
            int nCars = 0, nDrivers = 0;
            if (s.has("fleet") && s.get("fleet").isJsonArray()) {
                for (JsonElement el : s.getAsJsonArray("fleet")) {
                    if (!el.isJsonObject()) continue;
                    Car car = Car.fromJson(el.getAsJsonObject());
                    if (car == null || car.name.isEmpty()) continue;
                    cars.upsert(c, car);
                    nCars++;
                }
            }
            if (s.has("drivers") && s.get("drivers").isJsonArray()) {
                for (JsonElement el : s.getAsJsonArray("drivers")) {
                    if (!el.isJsonObject()) continue;
                    Driver d = Driver.fromJson(el.getAsJsonObject());
                    if (d == null || d.name.isEmpty()) continue;
                    drivers.upsert(c, d);
                    nDrivers++;
                }
            }
            if (s.has("config") && s.get("config").isJsonObject()) {
                settings.setConfig(c, s.getAsJsonObject("config"));
            }
            com.apex.log.Logger.info("Seeded fleet from seed.json: {} cars, {} drivers", nCars, nDrivers);
            return null;
        });
    }
}
