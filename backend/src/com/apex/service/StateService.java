package com.apex.service;

import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.*;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bootstrap (full state for a session) and admin sync (push state).
 *
 * Sync rules:
 *  - fleet + drivers: full replace (the admin UI is the catalog truth)
 *  - users / orders / applications / notifications / chats: upsert-only,
 *    NEVER deleted server-side (protects history and other clients)
 *  - bannedCNICs + config: replace
 *  - wallet fields: ignored (wallet is server-own)
 *  - incoming passwords are hashed; stored hashes are never overwritten
 */
public final class StateService {

    private final Database db;
    private final CarRepo cars;
    private final DriverRepo drivers;
    private final UserRepo users;
    private final BookingRepo bookings;
    private final ApplicationRepo apps;
    private final NotificationRepo notifs;
    private final ChatRepo chats;
    private final BanRepo bans;
    private final SettingsRepo settings;
    private final WalletRepo wallet;

    public StateService(Database db, CarRepo cars, DriverRepo drivers, UserRepo users,
                        BookingRepo bookings, ApplicationRepo apps, NotificationRepo notifs,
                        ChatRepo chats, BanRepo bans, SettingsRepo settings, WalletRepo wallet) {
        this.db = db; this.cars = cars; this.drivers = drivers; this.users = users;
        this.bookings = bookings; this.apps = apps; this.notifs = notifs;
        this.chats = chats; this.bans = bans; this.settings = settings; this.wallet = wallet;
    }

    /* ---------------- bootstrap ---------------- */

    public JsonObject bootstrap(JsonObject session) {
        boolean admin = session != null && "admin".equals(Json.getStr(session, "role", ""));
        String userId = session != null ? Json.getStr(session, "userId", "") : "";
        return db.with(c -> {
            JsonObject o = new JsonObject();
            JsonArray fleet = new JsonArray();
            for (Car car : cars.list(c)) fleet.add(car.toJson());
            JsonArray drv = new JsonArray();
            for (Driver d : drivers.list(c)) drv.add(d.toJson());
            o.add("fleet", fleet);
            o.add("drivers", drv);

            if (admin) {
                JsonArray us = new JsonArray();
                for (User u : users.all(c)) {
                    JsonObject d = u.data.deepCopy();
                    d.remove("password");
                    us.add(d);
                }
                o.add("users", us);
                JsonArray orders = new JsonArray();
                for (Booking b : bookings.listAll(c)) orders.add(sanitize(b.data, false));
                o.add("orders", orders);
                JsonArray aps = new JsonArray();
                for (Application a : apps.list(c)) aps.add(a.toJson());
                o.add("applications", aps);
                JsonArray nf = new JsonArray();
                for (JsonObject n : notifs.listAll(c)) nf.add(n);
                o.add("notifications", nf);
                JsonArray ch = new JsonArray();
                for (ChatThread t : chats.allThreads(c)) ch.add(t.toJson());
                o.add("chats", ch);
                o.add("bannedCNICs", bans.list(c).size() > 0 ? bansArray(c) : new JsonArray());
                JsonObject aw = new JsonObject();
                aw.addProperty("balance", wallet.balance(c));
                o.add("adminWallet", aw);
                JsonObject ow = new JsonObject();
                wallet.ownerWallets(c).forEach(ow::addProperty);
                o.add("ownerWallets", ow);
            } else {
                JsonArray us = new JsonArray();
                for (User u : users.all(c)) us.add(u.publicJson());
                o.add("users", us);
                if (userId.isEmpty()) {
                    o.add("orders", new JsonArray());
                    o.add("notifications", new JsonArray());
                    o.add("chats", new JsonArray());
                    o.add("applications", new JsonArray());
                } else {
                    JsonArray orders = new JsonArray();
                    for (Booking b : bookings.listAll(c)) {
                        if (b.userId != null && b.userId.equals(userId)) orders.add(sanitize(b.data, true));
                    }
                    o.add("orders", orders);
                    JsonArray nf = new JsonArray();
                    for (JsonObject n : notifs.listForUser(c, userId)) nf.add(n);
                    o.add("notifications", nf);
                    JsonArray ch = new JsonArray();
                    ch.add(chats.threadFor(c, userId).toJson());
                    o.add("chats", ch);
                    JsonArray aps = new JsonArray();
                    for (Application a : apps.listByUser(c, userId)) aps.add(a.toJson());
                    o.add("applications", aps);
                }
                o.add("bannedCNICs", bans.list(c).size() > 0 ? bansArray(c) : new JsonArray());
                o.add("adminWallet", new JsonObject());
                o.add("ownerWallets", new JsonObject());
            }
            o.add("config", settings.getConfig(c));
            return o;
        });
    }

    private JsonArray bansArray(PgConnection c) {
        JsonArray a = new JsonArray();
        for (JsonObject b : bans.list(c)) a.add(b);
        return a;
    }

    /** Strip sensitive fields from an order for non-owners. */
    private static JsonObject sanitize(JsonObject data, boolean stripIdentity) {
        JsonObject d = data.deepCopy();
        if (stripIdentity) d.remove("identity");
        d.remove("password");
        return d;
    }

    /* ---------------- sync (admin) ---------------- */

    public JsonObject sync(JsonObject adminSession, JsonObject state) {
        String reviewer = Json.getStr(adminSession, "username", "admin");
        int[] counts = new int[8];
        db.tx(c -> {
            // 1) fleet (full replace)
            if (state.has("fleet")) {
                List<Car> fleet = new ArrayList<>();
                for (JsonElement el : arr(state, "fleet")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject d = el.getAsJsonObject();
                    String idStr = Json.getStr(d, "id", "");
                    if (idStr.isEmpty()) throw ApiException.validation("Sync fleet[] entries need an id");
                    try {
                        fleet.add(Car.fromJson(d));
                    } catch (NumberFormatException e) {
                        throw ApiException.validation("Invalid car id: " + idStr);
                    }
                }
                cars.replaceAll(c, fleet);
                counts[0] = fleet.size();
            }

            // 2) drivers (full replace)
            if (state.has("drivers")) {
                List<Driver> list = new ArrayList<>();
                for (JsonElement el : arr(state, "drivers")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject d = el.getAsJsonObject();
                    if (Json.getStr(d, "id", "").isEmpty()) throw ApiException.validation("Sync drivers[] entries need an id");
                    try {
                        list.add(Driver.fromJson(d));
                    } catch (NumberFormatException e) {
                        throw ApiException.validation("Invalid driver id: " + Json.getStr(d, "id", ""));
                    }
                }
                drivers.replaceAll(c, list);
                counts[1] = list.size();
            }

            // 3) users (upsert, never delete)
            if (state.has("users")) {
                int n = 0;
                for (JsonElement el : arr(state, "users")) {
                    if (!el.isJsonObject()) continue;
                    users.upsertFromSync(c, el.getAsJsonObject(), Json.getStr(el.getAsJsonObject(), "password", ""));
                    n++;
                }
                counts[2] = n;
            }

            // 4) orders (upsert, never delete) + overlap validation
            if (state.has("orders")) {
                List<Booking> incoming = new ArrayList<>();
                for (JsonElement el : arr(state, "orders")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject d = el.getAsJsonObject();
                    if (Json.getStr(d, "id", "").isEmpty()) throw ApiException.validation("Sync orders[] entries need an id");
                    Booking b = Booking.fromJson(d);
                    incoming.add(b);
                    bookings.upsert(c, b);
                }
                validateNoOverlaps(c, incoming);
                counts[3] = incoming.size();
            }

            // 5) applications (upsert, never delete)
            if (state.has("applications")) {
                int n = 0;
                for (JsonElement el : arr(state, "applications")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject d = el.getAsJsonObject();
                    if (Json.getStr(d, "id", "").isEmpty()) throw ApiException.validation("Sync applications[] entries need an id");
                    apps.upsert(c, Application.fromJson(d));
                    n++;
                }
                counts[4] = n;
            }

            // 6) notifications (upsert, never delete)
            if (state.has("notifications")) {
                int n = 0;
                for (JsonElement el : arr(state, "notifications")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject d = el.getAsJsonObject();
                    notifs.upsertFromSync(c,
                            Json.getStr(d, "id", ""),
                            Json.getStr(d, "userId", "all"),
                            Json.getStr(d, "title", ""),
                            Json.getStr(d, "msg", ""),
                            Json.getStr(d, "link", ""),
                            Json.getStr(d, "adminTab", ""),
                            Json.getBool(d, "read", false));
                    n++;
                }
                counts[5] = n;
            }

            // 7) chats (upsert meta + merge messages, update counters)
            if (state.has("chats")) {
                int n = 0;
                for (JsonElement el : arr(state, "chats")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject d = el.getAsJsonObject();
                    String userId = Json.getStr(d, "userId", "");
                    if (userId.isEmpty()) continue;
                    List<String[]> msgs = new ArrayList<>();
                    if (d.has("messages") && d.get("messages").isJsonArray()) {
                        for (JsonElement m : d.getAsJsonArray("messages")) {
                            if (!m.isJsonObject()) continue;
                            JsonObject mo = m.getAsJsonObject();
                            msgs.add(new String[]{
                                    Json.getStr(mo, "from", Json.getStr(mo, "sender", "user")),
                                    Json.getStr(mo, "text", ""),
                                    Json.getStr(mo, "time", "")});
                        }
                    }
                    chats.merge(c, userId, Json.getStr(d, "userName", ""),
                            Json.getStr(d, "lastTime", ""), msgs);
                    // explicit counters from the admin UI win (e.g. admin marked read)
                    if (d.has("unreadAdmin") || d.has("unreadUser")) {
                        chats.setCounters(c, userId,
                                Json.getInt(d, "unreadAdmin", 0), Json.getInt(d, "unreadUser", 0));
                    }
                    n++;
                }
                counts[6] = n;
            }

            // 8) banned CNICs (replace)
            if (state.has("bannedCNICs")) {
                List<String> cnics = new ArrayList<>();
                for (JsonElement el : arr(state, "bannedCNICs")) {
                    String cn = el.isJsonPrimitive() ? el.getAsString() : Json.getStr(el.getAsJsonObject(), "cnic", "");
                    cn = cn.trim();
                    if (cn.isEmpty()) continue;
                    if (!cn.matches("[0-9]{13}")) {
                        throw ApiException.validation("Invalid CNIC in banned list: " + cn);
                    }
                    cnics.add(cn);
                }
                bans.replaceAll(c, cnics);
                counts[7] = cnics.size();
            }

            // 9) config (replace)
            if (state.has("config") && state.get("config").isJsonObject()) {
                settings.setConfig(c, state.getAsJsonObject("config"));
            }
            return null;
        });

        JsonObject o = new JsonObject();
        o.addProperty("status", "synced");
        o.addProperty("by", reviewer);
        o.addProperty("fleet", counts[0]);
        o.addProperty("drivers", counts[1]);
        o.addProperty("users", counts[2]);
        o.addProperty("orders", counts[3]);
        o.addProperty("applications", counts[4]);
        o.addProperty("notifications", counts[5]);
        o.addProperty("chats", counts[6]);
        o.addProperty("bannedCNICs", counts[7]);
        return o;
    }

    /**
     * After upserting synced orders, make sure no two active bookings of the
     * same car overlap. Fail the whole sync (409) when the client state
     * contains a clash - the server never silently stores double bookings.
     */
    private void validateNoOverlaps(PgConnection c, List<Booking> incoming) {
        Map<Long, List<String[]>> byCar = new HashMap<>();
        for (Booking b : incoming) {
            if (b.data == null || b.data.get("items") == null) continue;
            if (b.status == null || b.status.equals("Cancelled") || b.status.equals("Completed") || b.status.equals("Rejected")) {
                continue;
            }
            for (JsonElement el : b.data.getAsJsonArray("items")) {
                if (!el.isJsonObject()) continue;
                JsonObject it = el.getAsJsonObject();
                long carId = Json.getLong(it, "carId", 0);
                String s = Json.getStr(it, "startDt", "");
                String e = Json.getStr(it, "endDt", "");
                if (carId <= 0 || s.isEmpty() || e.isEmpty()) continue;
                byCar.computeIfAbsent(carId, k -> new ArrayList<>()).add(new String[]{b.id, s, e});
            }
        }
        for (Map.Entry<Long, List<String[]>> en : byCar.entrySet()) {
            List<String[]> rows = new ArrayList<>(bookings.windowsForCar(c, en.getKey()));
            // drop DB rows that were just replaced by the incoming sync
            rows.removeIf(r -> en.getValue().stream().anyMatch(x -> x[0].equals(r[0])));
            rows.addAll(en.getValue());
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    String[] a = rows.get(i);
                    String[] b = rows.get(j);
                    if (a[0].equals(b[0])) continue;
                    if (a[1].compareTo(b[2]) < 0 && b[1].compareTo(a[2]) < 0) {
                        throw ApiException.conflict("Sync rejected: double booking detected for car " +
                                en.getKey() + " (bookings " + a[0] + " and " + b[0] + ")");
                    }
                }
            }
        }
    }

    private static JsonArray arr(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonArray()) return new JsonArray();
        return state.getAsJsonArray(key);
    }
}
