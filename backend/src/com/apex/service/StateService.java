package com.apex.service;

import com.apex.dao.ApplicationDao;
import com.apex.dao.BanDao;
import com.apex.dao.BookingDao;
import com.apex.dao.CarDao;
import com.apex.dao.ChatDao;
import com.apex.dao.DriverDao;
import com.apex.dao.NotificationDao;
import com.apex.dao.SettingsDao;
import com.apex.dao.UserDao;
import com.apex.dao.WalletDao;
import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single write-path of the system. The frontend pushes its whole state
 * document here; the service normalises it into the relational tables inside
 * one transaction. Bootstrap does the reverse so any client can hydrate.
 */
public class StateService {

    private final Database db;
    private final CarDao cars;
    private final DriverDao drivers;
    private final UserDao users;
    private final BookingDao bookings;
    private final ApplicationDao applications;
    private final NotificationDao notifications;
    private final ChatDao chats;
    private final BanDao bans;
    private final WalletDao wallet;
    private final SettingsDao settings;

    public StateService(Database db, CarDao cars, DriverDao drivers, UserDao users, BookingDao bookings,
                        ApplicationDao applications, NotificationDao notifications, ChatDao chats,
                        BanDao bans, WalletDao wallet, SettingsDao settings) {
        this.db = db;
        this.cars = cars;
        this.drivers = drivers;
        this.users = users;
        this.bookings = bookings;
        this.applications = applications;
        this.notifications = notifications;
        this.chats = chats;
        this.bans = bans;
        this.wallet = wallet;
        this.settings = settings;
    }

    /* ------------------------------------------------ read side */

    /**
     * Full state for authenticated admins; sanitized state for the public.
     * Public viewers never see customer CNIC/phone or wallet balances.
     */
    public JsonObject bootstrap(JsonObject session) throws SQLException {
        boolean full = session != null && "admin".equals(Json.str(session, "role"));
        String me = session == null ? null : Json.str(session, "username");
        return db.with(c -> {
            JsonObject s = Json.obj();
            s.add("fleet", toArray(cars.all()));
            s.add("drivers", toArray(drivers.all()));
            if (full) {
                s.add("users", toArray(users.all()));
                s.addProperty("adminWallet", wallet.adminBalance());
                JsonObject ow = Json.obj();
                wallet.ownerWallets().forEach((k, v) -> ow.addProperty(k, v));
                s.add("ownerWallets", ow);
            } else {
                JsonArray pub = Json.arr();
                for (JsonObject u : users.all()) {
                    JsonObject p = Json.obj();
                    p.addProperty("id", Json.str(u, "id"));
                    p.addProperty("username", Json.str(u, "username"));
                    p.addProperty("email", Json.str(u, "email"));
                    p.addProperty("name", Json.str(u, "name"));
                    pub.add(p);
                }
                s.add("users", pub);
            }
            // privacy: non-admins only ever see THEIR OWN orders/notifications/chats,
            // and identity numbers are stripped from the wire entirely.
            JsonArray myOrders = Json.arr();
            for (JsonObject o : bookings.all()) {
                if (!full && !o.equals(null) && me != null && me.equals(Json.str(o, "userId"))) {
                    JsonObject copy = Json.parseObject(o.toString());
                    if (copy.has("identity")) copy.remove("identity");
                    myOrders.add(copy);
                } else if (full) myOrders.add(o);
            }
            s.add("orders", myOrders);
            JsonArray myApps = Json.arr();
            for (JsonObject a : applications.all())
                if (full || (me != null && me.equals(Json.str(a, "userId")))) myApps.add(a);
            s.add("applications", myApps);
            JsonArray myNotifs = Json.arr();
            for (JsonObject n : notifications.all()) {
                String to = Json.str(n, "userId");
                if (full || (me != null && (me.equals(to) || "all".equals(to)))) myNotifs.add(n);
            }
            s.add("notifications", myNotifs);
            JsonArray myChats = Json.arr();
            for (JsonObject ch : chats.all())
                if (full || (me != null && me.equals(Json.str(ch, "userId")))) myChats.add(ch);
            s.add("chats", myChats);
            JsonArray banned = Json.arr();
            if (full || session != null) bans.all().forEach(banned::add);
            s.add("bannedCNICs", banned);
            String cfg = settings.get("config");
            s.add("config", cfg != null ? Json.parse(cfg) : Json.obj());
            return s;
        });
    }

    public JsonArray collection(String name) throws SQLException {
        List<JsonObject> rows;
        switch (name) {
            case "fleet": rows = cars.all(); break;
            case "drivers": rows = drivers.all(); break;
            case "users": rows = users.all(); break;
            case "orders": rows = bookings.all(); break;
            case "applications": rows = applications.all(); break;
            case "notifications": rows = notifications.all(); break;
            case "chats": rows = chats.all(); break;
            default: throw new IllegalArgumentException("Unknown collection: " + name);
        }
        return toArray(rows);
    }

    public JsonObject getConfig() throws SQLException {
        String cfg = settings.get("config");
        return cfg != null ? Json.parseObject(cfg) : Json.obj();
    }

    /* ------------------------------------------------ write side */

    public void sync(JsonObject s) throws SQLException {
        db.tx(c -> {
            cars.replace(objectList(s, "fleet"));
            drivers.replace(objectList(s, "drivers"));
            users.replace(objectList(s, "users"));
            List<JsonObject> incoming = objectList(s, "orders");
            checkOverlaps(incoming);
            bookings.replace(incoming);
            applications.replace(objectList(s, "applications"));
            notifications.replace(objectList(s, "notifications"));
            chats.replace(objectList(s, "chats"));
            List<String> banned = new ArrayList<>();
            if (s.has("bannedCNICs") && s.get("bannedCNICs").isJsonArray())
                s.getAsJsonArray("bannedCNICs").forEach(e -> banned.add(e.getAsString()));
            bans.replace(banned);
            if (s.has("adminWallet") && s.get("adminWallet").isJsonPrimitive())
                wallet.setAdminBalance(s.get("adminWallet").getAsLong());
            Map<String, Long> ow = new LinkedHashMap<>();
            if (s.has("ownerWallets") && s.get("ownerWallets").isJsonObject())
                for (Map.Entry<String, JsonElement> e : s.getAsJsonObject("ownerWallets").entrySet())
                    ow.put(e.getKey(), e.getValue().getAsLong());
            wallet.replaceOwnerWallets(ow);
            if (s.has("config") && s.get("config").isJsonObject())
                settings.set("config", s.getAsJsonObject("config").toString());
            return null;
        });
    }

    public void setCollection(String name, JsonArray items) throws SQLException {
        List<JsonObject> list = new ArrayList<>();
        items.forEach(e -> { if (e.isJsonObject()) list.add(e.getAsJsonObject()); });
        db.tx(c -> {
            switch (name) {
                case "fleet": cars.replace(list); break;
                case "drivers": drivers.replace(list); break;
                case "users": users.replace(list); break;
                case "orders": bookings.replace(list); break;
                case "applications": applications.replace(list); break;
                case "notifications": notifications.replace(list); break;
                case "chats": chats.replace(list); break;
                default: throw new IllegalArgumentException("Unknown collection: " + name);
            }
            return null;
        });
    }

    public void setConfig(JsonObject cfg) throws SQLException {
        db.tx(c -> { settings.set("config", cfg.toString()); return null; });
    }

    /* ------------------------------------------------ banned CNIC control */

    public JsonArray banned() throws SQLException {
        JsonArray a = Json.arr();
        db.with(c -> { bans.all().forEach(a::add); return null; });
        return a;
    }

    public void addBan(String cnic) throws SQLException {
        db.tx(c -> { bans.add(cnic); return null; });
    }

    public void removeBan(String cnic) throws SQLException {
        db.tx(c -> { bans.remove(cnic); return null; });
    }

    /* ------------------------------------------------ first-run seed */

    public void seedIfEmpty(Path seedFile) throws SQLException, IOException {
        boolean empty = db.with(c -> cars.all().isEmpty() && settings.get("config") == null);
        if (!empty) return;
        JsonObject seed = null;
        if (seedFile != null && Files.exists(seedFile)) {
            seed = Json.parseObject(Files.readString(seedFile));
        }
        if (seed == null) seed = Json.obj();
        sync(seed);
    }

    /* ------------------------------------------------ single-row patches + crud */

    public String engineName() { return db.engine(); }

    public void upsertChat(JsonObject thread) throws SQLException {
        db.tx(c -> { chats.upsertThread(thread); return null; });
    }

    /** Full replace of ONE order (server flows: pickup, return, payment review). */
    public void patchOrder(JsonObject order) throws SQLException {
        db.tx(c -> { bookings.upsert(order); return null; });
    }

    public JsonObject insert(String coll, JsonObject body) throws SQLException {
        JsonArray list = collection(coll);
        if (!body.has("id") || body.get("id").isJsonNull()) {
            if ("fleet".equals(coll) || "drivers".equals(coll)) {
                int max = 0;
                for (var el : list) max = Math.max(max, Json.intVal(el.getAsJsonObject(), "id", 0));
                body.addProperty("id", max + 1);
            } else {
                body.addProperty("id", "X" + System.currentTimeMillis());
            }
        }
        list.add(body);
        setCollection(coll, list);
        return body;
    }

    public JsonObject update(String coll, String id, JsonObject body) throws SQLException {
        JsonArray list = collection(coll);
        JsonObject found = null;
        for (int i = 0; i < list.size(); i++) {
            JsonObject o = list.get(i).getAsJsonObject();
            if (String.valueOf(Json.idOf(o)).equals(id)) { found = o; list.set(i, body); }
        }
        if (found == null) return null;
        if (!body.has("id")) body.add("id", found.get("id"));
        setCollection(coll, list);
        return body;
    }

    public boolean delete(String coll, String id) throws SQLException {
        JsonArray list = collection(coll);
        JsonArray kept = Json.arr();
        boolean removed = false;
        for (var el : list) {
            if (String.valueOf(Json.idOf(el.getAsJsonObject())).equals(id)) removed = true;
            else kept.add(el);
        }
        if (!removed) return false;
        setCollection(coll, kept);
        return true;
    }

    /** Public guard used before inserting a single order. */
    public void assertNoClash(JsonObject order) throws SQLException {
        List<JsonObject> all = new ArrayList<>(bookings.all());
        all.add(order);
        checkOverlaps(all);
    }

    /** Backend-side availability guard: same car, overlapping start/end windows. */
    private static void checkOverlaps(List<JsonObject> orders) {
        Map<String, List<JsonObject>> byCar = new LinkedHashMap<>();
        for (JsonObject o : orders) {
            if ("Cancelled".equals(Json.str(o, "status"))) continue;
            String carId = firstCar(o);
            if (carId.isEmpty()) continue;
            byCar.computeIfAbsent(carId, k -> new ArrayList<>()).add(o);
        }
        for (Map.Entry<String, List<JsonObject>> e : byCar.entrySet()) {
            List<JsonObject> l = e.getValue();
            for (int i = 0; i < l.size(); i++) {
                for (int j = i + 1; j < l.size(); j++) {
                    String aS = Json.str(l.get(i), "startDt"), aE = Json.str(l.get(i), "endDt");
                    String bS = Json.str(l.get(j), "startDt"), bE = Json.str(l.get(j), "endDt");
                    if (aS == null || aE == null || bS == null || bE == null) continue;
                    if (aS.compareTo(bE) < 0 && bS.compareTo(aE) < 0) {
                        throw new IllegalArgumentException("Time clash: car " + e.getKey()
                                + " is already booked for that window (bookings "
                                + Json.idOf(l.get(i)) + " & " + Json.idOf(l.get(j)) + ")");
                    }
                }
            }
        }
    }

    private static String firstCar(JsonObject o) {
        if (!o.has("items") || !o.get("items").isJsonArray()) return "";
        var items = o.getAsJsonArray("items");
        if (items.size() == 0 || !items.get(0).isJsonObject()) return "";
        return String.valueOf(Json.intVal(items.get(0).getAsJsonObject(), "carId", -1));
    }

    /* ------------------------------------------------ helpers */

    private static JsonArray toArray(List<JsonObject> rows) {
        JsonArray a = Json.arr();
        rows.forEach(a::add);
        return a;
    }

    private static List<JsonObject> objectList(JsonObject s, String key) {
        List<JsonObject> out = new ArrayList<>();
        if (s.has(key) && s.get(key).isJsonArray())
            s.getAsJsonArray(key).forEach(e -> { if (e.isJsonObject()) out.add(e.getAsJsonObject()); });
        return out;
    }
}
