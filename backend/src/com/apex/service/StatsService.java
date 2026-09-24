package com.apex.service;

import com.apex.dao.BookingDao;
import com.apex.dao.CarDao;
import com.apex.dao.ChatDao;
import com.apex.dao.DocumentDao;
import com.apex.dao.DriverDao;
import com.apex.dao.NotificationDao;
import com.apex.dao.ApplicationDao;
import com.apex.dao.PaymentDao;
import com.apex.dao.ReviewDao;
import com.apex.dao.SettingsDao;
import com.apex.dao.UserDao;
import com.apex.dao.WalletDao;
import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.util.List;

/**
 * Everything the ADMIN OVERVIEW dashboard shows, straight from the database:
 * earnings, bookings (active / pending / completed), payments (verified / pending /
 * rejected), withdrawals, users + their listed cars, driver applications, pending
 * document verifications, pending reviews, unread messages, fleet + settings.
 */
public class StatsService {

    private final Database db;
    private final CarDao carDao;
    private final BookingDao bookingDao;
    private final UserDao userDao;
    private final WalletDao walletDao;
    private final DriverDao driverDao;
    private final ApplicationDao ownerAppDao;
    private final SettingsDao settingsDao;
    private final ChatDao chatDao;
    private final NotificationDao notifDao;
    private final PaymentDao paymentDao;
    private final DocumentDao documentDao;
    private final ReviewDao reviewDao;

    public StatsService(Database db, CarDao carDao, BookingDao bookingDao, UserDao userDao,
                        WalletDao walletDao, DriverDao driverDao, ApplicationDao ownerAppDao,
                        SettingsDao settingsDao, ChatDao chatDao, NotificationDao notifDao,
                        PaymentDao paymentDao, DocumentDao documentDao, ReviewDao reviewDao) {
        this.db = db;
        this.carDao = carDao;
        this.bookingDao = bookingDao;
        this.userDao = userDao;
        this.walletDao = walletDao;
        this.driverDao = driverDao;
        this.ownerAppDao = ownerAppDao;
        this.settingsDao = settingsDao;
        this.chatDao = chatDao;
        this.notifDao = notifDao;
        this.paymentDao = paymentDao;
        this.documentDao = documentDao;
        this.reviewDao = reviewDao;
    }

    public JsonObject overview(String adminUser) throws SQLException {
        return db.with(c -> {
            List<JsonObject> cars = carDao.all();
            List<JsonObject> orders = bookingDao.all();

            long revenue = 0, depositHeld = 0, extraCollected = 0;
            int completed = 0, active = 0, pending = 0;
            for (JsonObject o : orders) {
                String st = Json.str(o, "status");
                long tot = o.has("totals") && o.get("totals").isJsonObject()
                        ? Json.longVal(o.getAsJsonObject("totals"), "total", 0)
                        : Json.longVal(o, "total", 0);
                long dep = Json.longVal(o, "depositHeld", 0);
                if ("Cancelled".equals(st)) continue;
                if ("Completed".equals(st)) { completed++; depositHeld += dep; }
                else if ("Active".equals(st)) { active++; pending += 0; }
                else pending++;
                revenue += tot;
                extraCollected += Json.longVal(o, "extraCharges", 0);
            }

            List<JsonObject> payments = paymentDao.all();
            int payPending = 0, payVerified = 0, payRejected = 0;
            long verifiedAmount = 0, pendingAmount = 0;
            for (JsonObject p : payments) {
                String s = Json.str(p, "status");
                long amt = Json.longVal(p, "amount", 0);
                if ("Pending Verification".equals(s)) { payPending++; pendingAmount += amt; }
                else if ("Verified".equals(s)) { payVerified++; verifiedAmount += amt; }
                else if ("Rejected".equals(s)) payRejected++;
            }

            List<JsonObject> walletTxs = walletDao.transactions();
            long withdrawPending = 0, withdrawPaid = 0;
            for (JsonObject t : walletTxs) {
                long v = Math.abs(Json.longVal(t, "amount", 0));
                String s = Json.str(t, "status");
                if ("Pending".equals(s)) withdrawPending += v;
                if ("Paid".equals(s)) withdrawPaid += v;
            }

            // registered users (customer rows only) with the cars they listed
            JsonArray users = Json.arr();
            int listedCars = 0;
            for (JsonObject u : userDao.all()) {
                String id = Json.str(u, "id");
                if ("admin".equals(Json.str(u, "role")) || id.startsWith("S")) continue;
                JsonArray ucars = Json.arr();
                for (JsonObject car : cars) {
                    if (id.equals(Json.str(car, "ownerId"))) ucars.add(car.get("id"));
                }
                listedCars += ucars.size();
                JsonObject uu = Json.obj();
                for (String k : new String[]{"id", "username", "email", "name", "phone", "role", "created"}) {
                    if (u.has(k)) uu.add(k, u.get(k));
                }
                uu.add("cars", ucars);
                users.add(uu);
            }

            List<JsonObject> apps = ownerAppDao.all();
            int appsPending = 0;
            for (JsonObject a : apps) if ("Pending".equals(Json.str(a, "status"))) appsPending++;

            List<JsonObject> drivers = driverDao.all();
            int driversPending = 0, driversApproved = 0;
            for (JsonObject d : drivers) {
                if ("Pending".equals(Json.str(d, "status"))) driversPending++;
                if ("Approved".equals(Json.str(d, "status"))) driversApproved++;
            }

            List<JsonObject> docs = documentDao.all();
            int docsPending = 0;
            for (JsonObject d : docs) if ("Pending".equals(Json.str(d, "status"))) docsPending++;

            List<JsonObject> reviews = reviewDao.all();
            int reviewsPending = 0;
            for (JsonObject rv : reviews) if ("Pending".equals(Json.str(rv, "status"))) reviewsPending++;

            // unread support messages addressed to admin
            long unreadMessages = 0;
            for (JsonObject chat : chatDao.all()) unreadMessages += Json.longVal(chat, "unreadAdmin", 0);

            JsonObject stats = Json.obj();
            stats.addProperty("revenue", revenue);
            stats.addProperty("depositHeld", depositHeld);
            stats.addProperty("extraCollected", extraCollected);
            stats.addProperty("walletBalance", walletDao.adminBalance());
            stats.addProperty("withdrawPending", withdrawPending);
            stats.addProperty("withdrawPaid", withdrawPaid);
            stats.addProperty("payPending", payPending);
            stats.addProperty("payPendingAmount", pendingAmount);
            stats.addProperty("payVerified", payVerified);
            stats.addProperty("payVerifiedAmount", verifiedAmount);
            stats.addProperty("payRejected", payRejected);
            stats.addProperty("bookings", orders.size());
            stats.addProperty("active", active);
            stats.addProperty("pending", pending);
            stats.addProperty("completed", completed);
            stats.addProperty("rentedNow", active);
            stats.addProperty("cars", cars.size());
            stats.addProperty("users", users.size());
            stats.addProperty("userListedCars", listedCars);
            stats.addProperty("applications", apps.size());
            stats.addProperty("applicationsPending", appsPending);
            stats.addProperty("drivers", drivers.size());
            stats.addProperty("driversPending", driversPending);
            stats.addProperty("driversApproved", driversApproved);
            stats.addProperty("docsPending", docsPending);
            stats.addProperty("reviewsPending", reviewsPending);
            stats.addProperty("unread", notifDao.unreadCount(adminUser));
            stats.addProperty("unreadMessages", unreadMessages);

            JsonObject out = Json.obj();
            out.add("stats", stats);
            out.add("cars", arr(cars));
            out.add("orders", arr(orders));
            out.add("users", users);
            out.add("applications", arr(apps));
            out.add("drivers", arr(drivers));
            out.add("documents", arr(docs));
            out.add("reviews", arr(reviews));
            JsonObject wal = Json.obj();
            wal.addProperty("balance", walletDao.adminBalance());
            JsonObject ow = Json.obj();
            walletDao.ownerWallets().forEach((k, v) -> ow.addProperty(k, v));
            wal.add("ownerWallets", ow);
            out.add("wallet", wal);
            out.add("transactions", arr(walletTxs));
            out.add("payments", arr(payments));
            out.add("chats", arr(chatDao.all()));
            JsonObject cfg = Json.obj();
            String cfgJson = settingsDao.get("config");
            out.add("settings", cfgJson != null ? Json.parse(cfgJson) : cfg);
            return out;
        });
    }

    private static JsonArray arr(List<JsonObject> rows) {
        JsonArray a = Json.arr();
        rows.forEach(a::add);
        return a;
    }
}
