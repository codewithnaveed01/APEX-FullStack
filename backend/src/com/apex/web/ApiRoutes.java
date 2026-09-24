package com.apex.web;

import com.apex.config.AppConfig;
import com.apex.dao.BookingDao;
import com.apex.dao.CarDao;
import com.apex.dao.DocumentDao;
import com.apex.dao.NotificationDao;
import com.apex.dao.PaymentDao;
import com.apex.dao.ReviewDao;
import com.apex.model.Car;
import com.apex.model.Driver;
import com.apex.service.AuthService;
import com.apex.service.PricingService;
import com.apex.service.StateService;
import com.apex.service.StatsService;
import com.apex.service.WalletService;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Every HTTP endpoint. Access levels:
 *  PUBLIC - anyone          ANY - any logged-in user (customer or admin)
 *  ADMIN  - admin only (enforced by the Router; normal users get 403)
 */
public class ApiRoutes {

    private static final long MAX_UPLOAD_B64 = 3_400_000; // ~2.5 MB of image data

    public static void register(Router r, StateService st, AuthService au, WalletService ws, StatsService ss,
                                CarDao carDao, BookingDao bookingDao, PaymentDao payDao, DocumentDao docDao,
                                ReviewDao revDao, NotificationDao notifDao, AppConfig cfg) {

        /* ---------------- health + hydration ---------------- */

        r.get("/api/health", ctx -> {
            JsonObject o = Json.obj();
            o.addProperty("ok", true);
            o.addProperty("service", "apex-backend");
            o.addProperty("engine", st.engineName());
            ctx.ok(o);
        });

        r.get("/api/bootstrap", ctx -> ctx.ok(st.bootstrap(au.validate(HttpUtil.bearer(ctx.ex)))));

        r.add("PUT", "/api/sync", "ADMIN", ctx -> {
            JsonObject body = requireBody(ctx);
            st.sync(body);
            ctx.ok(Json.obj());
        });

        /* ---------------- auth ---------------- */

        r.add("POST", "/api/auth/login", "PUBLIC", ctx -> {
            JsonObject body = requireBody(ctx);
            String ip = HttpUtil.clientIp(ctx.ex);
            if (RateLimiter.locked("login:" + ip)) {
                HttpUtil.sendError(ctx.ex, 429, "Too many failed attempts. Try again later.");
                return;
            }
            if (!RateLimiter.allow("login:" + ip, 10, 60_000)) {
                HttpUtil.sendError(ctx.ex, 429, "Too many attempts. Slow down.");
                return;
            }
            JsonObject out = au.login(Json.str(body, "username"), Json.str(body, "password"));
            if (out == null) {
                if (RateLimiter.strike("login:" + ip, 5, 5 * 60_000, 5 * 60_000))
                    HttpUtil.sendError(ctx.ex, 429, "Too many failed attempts. Locked for 5 minutes.");
                else HttpUtil.sendError(ctx.ex, 401, "Invalid username or password");
                return;
            }
            RateLimiter.clearStrikes("login:" + ip);
            ctx.ok(out);
        });

        r.add("POST", "/api/auth/register", "PUBLIC", ctx -> {
            JsonObject body = requireBody(ctx);
            String ip = HttpUtil.clientIp(ctx.ex);
            if (!RateLimiter.allow("reg:" + ip, 5, 60_000)) {
                HttpUtil.sendError(ctx.ex, 429, "Too many signups from this network.");
                return;
            }
            ctx.created(au.register(body));
        });

        r.any("POST", "/api/auth/logout", ctx -> {
            au.logout(HttpUtil.bearer(ctx.ex));
            ctx.ok(Json.obj());
        });

        r.add("GET", "/api/auth/me", "ANY", ctx -> ctx.ok(ctx.session));

        /* ---------------- admin dashboard ---------------- */

        r.add("GET", "/api/stats", "ADMIN", ctx -> ctx.ok(ss.overview("admin")));

        r.add("GET", "/api/wallet", "ADMIN", ctx -> ctx.ok(ws.info()));

        r.post("/api/wallet/add-cash", ctx -> {
            JsonObject body = requireBody(ctx);
            ctx.ok(ws.addCash(Json.longVal(body, "amount", 0), Json.str(body, "note", "")));
        });

        r.post("/api/wallet/withdraw", ctx -> {
            JsonObject body = requireBody(ctx);
            ctx.ok(ws.withdraw(Json.longVal(body, "amount", 0), Json.str(body, "account", ""), Json.str(body, "note", "")));
        });

        /* ---------------- payments: receipt + TID verification flow ---------------- */

        r.add("GET", "/api/payments", "ADMIN", ctx -> {
            JsonArray a = Json.arr();
            payDao.all().forEach(a::add);
            ctx.ok(a);
        });

        // customer submits receipt + transaction id -> "Pending Verification"
        r.any("POST", "/api/payments/submit", ctx -> {
            JsonObject body = requireBody(ctx);
            String bookingId = Json.str(body, "bookingId", "");
            long amount = Json.longVal(body, "amount", 0);
            String tid = Json.str(body, "tid", "").trim();
            long receiptDoc = Json.longVal(body, "receiptDoc", 0);
            if (bookingId.isEmpty()) throw new IllegalArgumentException("bookingId is required");
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
            if (tid.length() < 4) throw new IllegalArgumentException("Transaction ID (TID) is required");
            if (receiptDoc <= 0) throw new IllegalArgumentException("Payment receipt image is required");
            JsonObject order = findOrder(bookingDao, bookingId);
            if (order == null) throw new IllegalArgumentException("Booking not found");
            if (!ctx.isAdmin() && !ctx.userId().equals(Json.str(order, "userId", "")))
                throw new IllegalArgumentException("This booking belongs to another user");
            long id = payDao.insert(bookingId, Json.str(body, "method", "Online"), amount, tid, (int) receiptDoc, "Pending Verification");
            order.addProperty("paymentStatus", "Pending Verification");
            order.addProperty("status", "Pending Verification");
            st.patchOrder(order);
            notify(notifDao, "admin", "Payment verification needed",
                    "Booking " + bookingId + " - Rs " + amount + " (TID " + tid + ")", "", "Payments");
            notify(notifDao, Json.str(order, "userId", ""), "Payment received - pending verification",
                    "Admin will verify your payment for booking " + bookingId + " shortly.", "account");
            JsonObject out = Json.obj();
            out.addProperty("id", id);
            out.addProperty("status", "Pending Verification");
            ctx.created(out);
        });

        // admin verify / reject / request re-upload
        r.post("/api/payments/{id}/review", ctx -> {
            JsonObject body = requireBody(ctx);
            long id = Long.parseLong(ctx.param("id"));
            String action = Json.str(body, "action", "");
            String note = Json.str(body, "note", "");
            JsonObject pay = payDao.find(id);
            if (pay == null) throw new IllegalArgumentException("Payment not found");
            String bookingId = Json.str(pay, "bookingId");
            JsonObject order = findOrder(bookingDao, bookingId);
            switch (action) {
                case "verify": {
                    payDao.review(id, "Verified", note);
                    long amount = Json.longVal(pay, "amount", 0);
                    if (amount > 0) ws.record(amount, "payment", "Verified payment for booking " + bookingId + " (TID " + Json.str(pay, "tid") + ")");
                    if (order != null) {
                        order.addProperty("paymentStatus", "Verified");
                        order.addProperty("status", "Pickup Pending");
                        order.addProperty("paid", amount);
                        st.patchOrder(order);
                    }
                    notify(notifDao, order == null ? "admin" : Json.str(order, "userId"),
                            "Payment verified", "Your payment for booking " + bookingId + " is verified. Pickup is now pending.", "account");
                    break;
                }
                case "reject": {
                    payDao.review(id, "Rejected", note);
                    if (order != null) {
                        order.addProperty("paymentStatus", "Rejected");
                        st.patchOrder(order);
                    }
                    notify(notifDao, order == null ? "admin" : Json.str(order, "userId"),
                            "Payment rejected", "Payment for booking " + bookingId + " was rejected. " + note, "account");
                    break;
                }
                case "reupload": {
                    payDao.review(id, "Reupload", note);
                    if (order != null) {
                        order.addProperty("paymentStatus", "Reupload Requested");
                        st.patchOrder(order);
                    }
                    notify(notifDao, order == null ? "admin" : Json.str(order, "userId"),
                            "Receipt re-upload needed", "Please upload a clear receipt again for booking " + bookingId + ". " + note, "account");
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
            ctx.ok(Json.obj());
        });

        // manual intake (admin console)
        r.add("POST", "/api/payments/record", "ADMIN", ctx -> {
            JsonObject body = requireBody(ctx);
            String ip = HttpUtil.clientIp(ctx.ex);
            if (!RateLimiter.allow("pay:" + ip, 20, 60_000)) {
                HttpUtil.sendError(ctx.ex, 429, "Too many payment requests");
                return;
            }
            ctx.ok(ws.record(Json.longVal(body, "amount", 0), Json.str(body, "type", ""), Json.str(body, "note", "")));
        });

        /* ---------------- booking lifecycle: pickup + return settlement ---------------- */

        r.post("/api/bookings/{id}/pickup", ctx -> {
            JsonObject body = ctx.body == null ? Json.obj() : ctx.body;
            String id = ctx.param("id");
            JsonObject order = findOrder(bookingDao, id);
            if (order == null) throw new IllegalArgumentException("Booking not found");
            String stt = Json.str(order, "status");
            if (!"Pickup Pending".equals(stt))
                throw new IllegalArgumentException("Only 'Pickup Pending' bookings can be handed over (current: " + stt + ")");
            String driverId = Json.str(body, "driverId", "");
            if (!driverId.isEmpty() && order.has("items") && order.get("items").isJsonArray()) {
                for (var el : order.getAsJsonArray("items")) {
                    if (el.isJsonObject()) el.getAsJsonObject().addProperty("assignedDriver", driverId);
                }
            }
            order.addProperty("status", "Active");
            order.addProperty("pickupAt", Instant.now().toString());
            st.patchOrder(order);
            notify(notifDao, Json.str(order, "userId", ""), "Car picked up - rental active",
                    "Booking " + id + " is now active. Safe journey!", "account");
            ctx.ok(order);
        });

        r.post("/api/bookings/{id}/return", ctx -> {
            JsonObject body = ctx.body == null ? Json.obj() : ctx.body;
            String id = ctx.param("id");
            JsonObject order = findOrder(bookingDao, id);
            if (order == null) throw new IllegalArgumentException("Booking not found");
            if (!"Active".equals(Json.str(order, "status")))
                throw new IllegalArgumentException("Only active rentals can be returned");
            String expected = Json.str(order, "endDt");
            String actual = Json.str(body, "actualReturn", "");
            if (actual.isEmpty()) actual = PricingService.fmt(LocalDateTime.now());
            // pricing rules come from settings - never hard-coded
            long hourly = 0, daily = 0;
            String carId = firstCarId(order);
            for (JsonObject car : carDao.all()) {
                if (String.valueOf(Json.intVal(car, "id", -1)).equals(carId)) {
                    daily = Json.longVal(car, "rate", 0);
                    hourly = Json.longVal(car, "hourlyRate", daily / 24);
                }
            }
            String graceS = cfgSetting(st, "graceMinutes", "30");
            int grace = Integer.parseInt(graceS.replaceAll("\\D", "").isEmpty() ? "30" : graceS.replaceAll("\\D", ""));
            boolean cap = !"false".equals(cfgSetting(st, "capExtraAtDaily", "true"));
            long extraHours = 0, charges = 0;
            if (!expected.isEmpty()) {
                JsonObject late = PricingService.lateCharges(expected, actual, hourly, daily, grace, cap);
                extraHours = Json.longVal(late, "extraHours", 0);
                charges = Json.longVal(late, "charges", 0);
            }
            long total = totalOf(order);
            order.addProperty("status", "Completed");
            order.addProperty("actualReturn", actual);
            order.addProperty("extraHours", extraHours);
            order.addProperty("extraCharges", charges);
            order.addProperty("finalAmount", total + charges);
            order.addProperty("paymentStatus", charges > 0 ? "Late Charges Due" : "Settled");
            st.patchOrder(order);
            if (charges > 0 && "Online".equals(Json.str(order, "payment", ""))) {
                ws.record(charges, "late-fee", "Late return charges for booking " + id);
            }
            notify(notifDao, Json.str(order, "userId", ""), "Rental completed",
                    charges > 0
                            ? "Booking " + id + " returned late by " + extraHours + "h. Late charges: Rs " + charges + ". Final: Rs " + (total + charges)
                            : "Booking " + id + " completed on time. Final amount: Rs " + total, "account");
            ctx.ok(order);
        });

        /* ---------------- uploads: CNIC / licence / receipts ---------------- */

        r.any("POST", "/api/uploads", ctx -> {
            JsonObject body = requireBody(ctx);
            String dataUrl = Json.str(body, "dataUrl", "");
            String kind = Json.str(body, "kind", "doc");
            String ownerType = Json.str(body, "ownerType", "user");
            String ownerId = Json.str(body, "ownerId", ctx.userId());
            if (!dataUrl.startsWith("data:image/")) throw new IllegalArgumentException("Only image uploads are allowed");
            if (dataUrl.length() > MAX_UPLOAD_B64) throw new IllegalArgumentException("Image too large (max ~2.5 MB)");
            int comma = dataUrl.indexOf(',');
            String meta = dataUrl.substring(5, comma);       // e.g. image/png;base64
            String ext = meta.contains("png") ? "png" : meta.contains("webp") ? "webp" : "jpg";
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Corrupt image data");
            }
            Path dir = cfg.getUploadsDir();
            Files.createDirectories(dir);
            String fname = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Files.write(dir.resolve(fname), bytes);
            long docId = docDao.insert(ownerType, ownerId, kind, fname, "image/" + ext);
            JsonObject out = Json.obj();
            out.addProperty("id", docId);
            out.addProperty("kind", kind);
            out.addProperty("name", fname);
            out.addProperty("bytes", bytes.length);
            out.addProperty("status", "Pending");
            ctx.created(out);
        });

        r.add("GET", "/api/documents", "ADMIN", ctx -> {
            JsonArray a = Json.arr();
            docDao.all().forEach(a::add);
            ctx.ok(a);
        });

        // sensitive: admin or the owning user only - never public
        r.add("GET", "/api/documents/{id}", "ANY", ctx -> {
            long id = Long.parseLong(ctx.param("id"));
            JsonObject doc = docDao.find(id);
            if (doc == null) throw new IllegalArgumentException("Document not found");
            if (!ctx.isAdmin() && !ctx.userId().equals(Json.str(doc, "ownerId")))
                throw new IllegalArgumentException("You do not have access to this document");
            byte[] bytes = Files.readAllBytes(cfg.getUploadsDir().resolve(Json.str(doc, "path")));
            JsonObject out = Json.obj();
            out.addProperty("id", id);
            out.addProperty("kind", Json.str(doc, "kind"));
            out.addProperty("contentType", Json.str(doc, "contentType"));
            out.addProperty("status", Json.str(doc, "status"));
            out.addProperty("dataUrl", "data:" + Json.str(doc, "contentType") + ";base64," + Base64.getEncoder().encodeToString(bytes));
            ctx.ok(out);
        });

        r.post("/api/documents/{id}/review", ctx -> {
            JsonObject body = requireBody(ctx);
            long id = Long.parseLong(ctx.param("id"));
            String status = Json.str(body, "status", "");
            if (!status.equals("Verified") && !status.equals("Rejected"))
                throw new IllegalArgumentException("status must be Verified or Rejected");
            if (docDao.find(id) == null) throw new IllegalArgumentException("Document not found");
            docDao.updateStatus(id, status);
            ctx.ok(Json.obj());
        });

        /* ---------------- reviews with moderation ---------------- */

        r.add("GET", "/api/reviews", "ADMIN", ctx -> {
            JsonArray a = Json.arr();
            revDao.all().forEach(a::add);
            ctx.ok(a);
        });

        r.get("/api/reviews/car/{id}", ctx -> {
            JsonArray a = Json.arr();
            revDao.approvedFor(Integer.parseInt(ctx.param("id"))).forEach(a::add);
            ctx.ok(a);
        });

        r.any("POST", "/api/reviews", ctx -> {
            JsonObject body = requireBody(ctx);
            String bookingId = Json.str(body, "bookingId", "");
            int carId = Json.intVal(body, "carId", 0);
            int rating = Json.intVal(body, "rating", 0);
            String text = Json.str(body, "body", "");
            if (carId <= 0 || rating < 1 || rating > 5) throw new IllegalArgumentException("carId and rating 1-5 are required");
            if (text.length() > 500) throw new IllegalArgumentException("Review too long (max 500 chars)");
            JsonObject order = findOrder(bookingDao, bookingId);
            if (order == null) throw new IllegalArgumentException("Booking not found");
            if (!ctx.isAdmin() && !ctx.userId().equals(Json.str(order, "userId", "")))
                throw new IllegalArgumentException("You can only review your own rentals");
            if (!"Completed".equals(Json.str(order, "status")))
                throw new IllegalArgumentException("Only completed rentals can be reviewed");
            if (revDao.existsForBooking(bookingId)) throw new IllegalArgumentException("You already reviewed this rental");
            long id = revDao.insert(bookingId, carId, ctx.userId(), rating, text);
            notify(notifDao, "admin", "New review pending", "Review for car #" + carId + " needs moderation.", "", "Payments");
            JsonObject out = Json.obj();
            out.addProperty("id", id);
            out.addProperty("status", "Pending");
            ctx.created(out);
        });

        r.post("/api/reviews/{id}/review", ctx -> {
            JsonObject body = requireBody(ctx);
            long id = Long.parseLong(ctx.param("id"));
            String status = Json.str(body, "status", "");
            if (!status.equals("Approved") && !status.equals("Hidden"))
                throw new IllegalArgumentException("status must be Approved or Hidden");
            if (revDao.find(id) == null) throw new IllegalArgumentException("Review not found");
            revDao.updateStatus(id, status);
            ctx.ok(Json.obj());
        });

        r.delete("/api/reviews/{id}", ctx -> {
            long id = Long.parseLong(ctx.param("id"));
            if (revDao.find(id) == null) throw new IllegalArgumentException("Review not found");
            revDao.updateStatus(id, "Hidden");
            ctx.ok(Json.obj());
        });

        /* ---------------- backend-side availability check ---------------- */

        r.get("/api/availability", ctx -> {
            String carId = ctx.ex.getRequestURI().getQuery() == null ? "" : HttpUtil.query(ctx.ex, "carId");
            String start = HttpUtil.query(ctx.ex, "start");
            String end = HttpUtil.query(ctx.ex, "end");
            if (carId == null || start == null || end == null || start.isEmpty() || end.isEmpty())
                throw new IllegalArgumentException("carId, start and end are required");
            if (start.compareTo(end) >= 0) throw new IllegalArgumentException("End must be after start");
            JsonObject out = Json.obj();
            out.addProperty("carId", carId);
            out.addProperty("start", start);
            out.addProperty("end", end);
            String clash = null;
            for (JsonObject o : bookingDao.all()) {
                if ("Cancelled".equals(Json.str(o, "status"))) continue;
                if (!carId.equals(firstCarId(o))) continue;
                String oS = Json.str(o, "startDt"), oE = Json.str(o, "endDt");
                if (oS.isEmpty() || oE.isEmpty()) continue;
                if (start.compareTo(oE) < 0 && oS.compareTo(end) < 0) { clash = Json.str(o, "id"); break; }
            }
            out.addProperty("available", clash == null);
            out.addProperty("clashWith", clash == null ? "" : clash);
            ctx.ok(out);
        });

        /* ---------------- settings + moderation ---------------- */

        r.get("/api/settings", ctx -> ctx.ok(st.getConfig()));
        r.put("/api/settings", ctx -> {
            st.setConfig(requireBody(ctx));
            ctx.ok(Json.obj());
        });

        r.get("/api/banned-cnic", ctx -> ctx.ok(st.banned()));
        r.post("/api/banned-cnic", ctx -> {
            JsonObject body = requireBody(ctx);
            String cnic = Json.str(body, "cnic", "").replaceAll("\\D", "");
            if (cnic.length() != 13) throw new IllegalArgumentException("CNIC must be 13 digits");
            st.addBan(cnic);
            ctx.ok(Json.obj());
        });
        r.delete("/api/banned-cnic/{cnic}", ctx -> {
            st.removeBan(ctx.param("cnic"));
            ctx.ok(Json.obj());
        });

        /* ---------------- customer self-service orders + chat ---------------- */

        // a customer may only create orders for THEMSELVES; admin may create for anyone
        r.any("POST", "/api/orders", ctx -> {
            JsonObject body = requireBody(ctx);
            if (!ctx.isAdmin()) body.addProperty("userId", ctx.userId());
            st.assertNoClash(body);
            JsonObject saved = st.insert("orders", body);
            notify(notifDao, "admin", "New booking " + Json.idOf(saved),
                    Json.str(saved, "name", "") + " - " + Json.str(saved, "status", ""), "", "Reservations");
            ctx.created(saved);
        });

        r.any("POST", "/api/chats/send", ctx -> {
            JsonObject body = requireBody(ctx);
            String text = Json.str(body, "text", "").trim();
            if (text.isEmpty() || text.length() > 2000) throw new IllegalArgumentException("Message empty or too long");
            String userId = ctx.isAdmin() ? Json.str(body, "userId", "") : ctx.userId();
            if (userId.isEmpty()) throw new IllegalArgumentException("userId is required");
            JsonObject thread = chatDaoThread(body, userId);
            st.upsertChat(thread);
            ctx.ok(thread);
        });

        /* ---------------- generic collections ---------------- */

        crud(r, st, "/api/orders", "orders"); // GET/PUT/DELETE admin; POST shadowed by ANY route above
        crud(r, st, "/api/fleet", "fleet");
        crud(r, st, "/api/drivers", "drivers");
        crud(r, st, "/api/users", "users");
        crud(r, st, "/api/applications", "applications");
        crud(r, st, "/api/notifications", "notifications");
        crud(r, st, "/api/chats", "chats");
    }

    private static void crud(Router r, StateService st, String path, String coll) {
        r.get(path, ctx -> ctx.ok(st.collection(coll)));
        r.get(path + "/{id}", ctx -> {
            String id = ctx.param("id");
            for (var el : st.collection(coll)) {
                JsonObject o = el.getAsJsonObject();
                if (String.valueOf(Json.idOf(o)).equals(id)) { ctx.ok(o); return; }
            }
            HttpUtil.sendError(ctx.ex, 404, "Not found");
        });
        r.post(path, ctx -> {
            JsonObject body = requireBody(ctx);
            validate(coll, body);
            JsonObject saved = st.insert(coll, body);
            ctx.created(saved);
        });
        r.put(path + "/{id}", ctx -> {
            JsonObject body = requireBody(ctx);
            validate(coll, body);
            JsonObject saved = st.update(coll, ctx.param("id"), body);
            if (saved == null) HttpUtil.sendError(ctx.ex, 404, "Not found");
            else ctx.ok(saved);
        });
        r.delete(path + "/{id}", ctx -> {
            if (!st.delete(coll, ctx.param("id"))) HttpUtil.sendError(ctx.ex, 404, "Not found");
            else ctx.ok(Json.obj());
        });
    }

    /* ---------------- helpers ---------------- */

    private static JsonObject chatDaoThread(JsonObject body, String userId) {
        JsonObject thread = body.has("thread") && body.get("thread").isJsonObject()
                ? Json.parseObject(body.get("thread").toString()) : Json.obj();
        thread.addProperty("userId", userId);
        if (!thread.has("messages") || !thread.get("messages").isJsonArray()) thread.add("messages", Json.arr());
        JsonObject msg = Json.obj();
        msg.addProperty("from", Json.str(body, "from", "customer"));
        msg.addProperty("text", Json.str(body, "text", ""));
        msg.addProperty("time", Instant.now().toString());
        thread.getAsJsonArray("messages").add(msg);
        thread.addProperty("lastTime", Instant.now().toString());
        return thread;
    }

    /** Booking money lives under totals.total (frontend shape). */
    private static long totalOf(JsonObject o) {
        if (o.has("totals") && o.get("totals").isJsonObject())
            return Json.longVal(o.getAsJsonObject("totals"), "total", 0);
        return Json.longVal(o, "total", 0);
    }

    private static JsonObject findOrder(BookingDao bookingDao, String id) throws java.sql.SQLException {
        for (JsonObject o : bookingDao.all()) if (Json.idOf(o).equals(id)) return o;
        return null;
    }

    private static String firstCarId(JsonObject order) {
        if (!order.has("items") || !order.get("items").isJsonArray()) return "";
        JsonArray items = order.getAsJsonArray("items");
        if (items.size() == 0 || !items.get(0).isJsonObject()) return "";
        return String.valueOf(Json.intVal(items.get(0).getAsJsonObject(), "carId", -1));
    }

    private static String cfgSetting(StateService st, String key, String dflt) throws java.sql.SQLException {
        JsonObject cfg = st.getConfig();
        return Json.str(cfg, key, dflt);
    }

    private static void notify(NotificationDao notifDao, String recipient, String title, String message, String link) {
        notify(notifDao, recipient, title, message, link, null);
    }

    private static void notify(NotificationDao notifDao, String recipient, String title, String message, String link, String adminTab) {
        try {
            JsonObject n = Json.obj();
            n.addProperty("id", "N" + System.nanoTime());
            n.addProperty("userId", recipient);
            n.addProperty("title", title);
            n.addProperty("msg", message);
            n.addProperty("link", link == null ? "" : link);
            n.addProperty("adminTab", adminTab == null ? "" : adminTab);
            n.addProperty("read", false);
            n.addProperty("time", Instant.now().toString());
            notifDao.insert(n);
        } catch (Exception ignored) { }
    }

    private static JsonObject requireBody(Router.Ctx ctx) {
        if (ctx.body == null) throw new IllegalArgumentException("JSON body is required");
        return ctx.body;
    }

    private static void validate(String coll, JsonObject body) {
        switch (coll) {
            case "fleet": Json.GSON.fromJson(body, Car.class).validate(); break;
            case "drivers": Json.GSON.fromJson(body, Driver.class).validate(); break;
            default: break;
        }
    }
}
