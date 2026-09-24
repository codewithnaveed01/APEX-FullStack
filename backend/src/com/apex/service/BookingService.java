package com.apex.service;

import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.model.Booking;
import com.apex.model.Car;
import com.apex.model.Driver;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Bookings: creation (with server-side quote + double-booking protection),
 * cancellation (5% fee), pickup and return (late fees + 90/10 owner payout).
 */
public final class BookingService {

    private static final double CANCELLATION_RATE = 0.05;

    private final Database db;
    private final CarRepo cars;
    private final DriverRepo drivers;
    private final BookingRepo bookings;
    private final BanRepo bans;
    private final DocumentRepo documents;
    private final SettingsRepo settings;
    private final WalletRepo wallet;
    private final NotificationService notifs;

    public BookingService(Database db, CarRepo cars, DriverRepo drivers, BookingRepo bookings,
                          BanRepo bans, DocumentRepo documents, SettingsRepo settings,
                          WalletRepo wallet, NotificationService notifs) {
        this.db = db; this.cars = cars; this.drivers = drivers; this.bookings = bookings;
        this.bans = bans; this.documents = documents; this.settings = settings;
        this.wallet = wallet; this.notifs = notifs;
    }

    /* ---------------- availability ---------------- */

    public JsonObject availability(String carIdStr, String start, String end) {
        long carId = Validation.longValue(carIdStr, "carId");
        String s = Validation.date(start, "start");
        String e = Validation.date(end, "end");
        if (e.compareTo(s) < 0) throw ApiException.validation("End date must be after start date");
        String startDt = s + "T00:00";
        String endDt = e + "T23:59";
        PricingService.hoursBetween(startDt, endDt); // validates parse

        return db.with(c -> {
            Car car = cars.get(c, carId);
            if (car == null) throw ApiException.notFound("Car not found");
            String clash = bookings.findOverlap(c, carId, startDt, endDt, "");
            JsonObject o = new JsonObject();
            o.addProperty("carId", carId);
            o.addProperty("start", s);
            o.addProperty("end", e);
            o.addProperty("available", clash == null);
            o.addProperty("clashWith", clash == null ? "" : clash);
            return o;
        });
    }

    /* ---------------- create ---------------- */

    public JsonObject create(JsonObject session, JsonObject body) {
        boolean isAdmin = "admin".equals(Json.getStr(session, "role", ""));
        String userId = isAdmin
                ? Json.getStr(body, "userId", "")
                : Json.getStr(session, "userId", "");
        if (userId.isEmpty()) throw ApiException.unauth("Unknown customer for this booking");

        String name = Validation.required(Json.getStr(body, "name", ""), "name", 2, 70);
        String phone = Validation.required(Validation.phone(Json.getStr(body, "phone", "")), "phone", 5, 20);
        String email = Json.getStr(body, "email", "");
        if (email.isEmpty()) email = "guest@apex.local";
        if (!Validation.email(email)) throw ApiException.validation("Invalid email address");

        String identityType = Json.clean(Json.getStr(body, "identityType", "CNIC"));
        if (identityType.isEmpty()) identityType = "CNIC";
        String identity = Json.clean(Json.getStr(body, "identity", ""));
        if (identityType.equals("CNIC")) {
            identity = Validation.cnic(identity);
        } else if (identityType.equals("Passport")) {
            identity = Validation.passport(identity);
        } else {
            throw ApiException.validation("identityType must be CNIC or Passport");
        }

        String dobStr = Json.clean(Json.getStr(body, "dob", ""));
        if (!dobStr.isEmpty()) {
            try {
                Validation.assertMinAge(LocalDate.parse(dobStr));
            } catch (RuntimeException e) {
                throw ApiException.validation("Invalid date of birth");
            }
        }

        JsonArray identityDocs = body.has("identityDocs") && body.get("identityDocs").isJsonArray()
                ? body.getAsJsonArray("identityDocs") : null;

        String status0 = Json.clean(Json.getStr(body, "status", ""));
        String payment = Json.clean(Json.getStr(body, "payment", ""));
        if (payment.isEmpty()) payment = "Cash on pickup";

        String start = Json.clean(Json.getStr(body, "startDt", ""));
        String end = Json.clean(Json.getStr(body, "endDt", ""));

        // Validate items + collect unique car ids (advisory locks in sorted order)
        JsonArray items = body.get("items") != null && body.get("items").isJsonArray()
                ? body.getAsJsonArray("items") : null;
        if (items == null || items.size() == 0) throw ApiException.validation("At least one car is required");
        if (items.size() > 5) throw ApiException.validation("Maximum 5 cars per booking");

        JsonObject config = settings.getConfig();
        long cfgDriverRate = Json.getLong(config, "driverRate", 4500);
        boolean cap = PricingService.capFromConfig(config);
        long homeDeliveryCharge = Json.getLong(config, "homeDeliveryCharge", 1500);

        TreeSet<Long> carIds = new TreeSet<>();
        for (JsonElement el : items) {
            if (!el.isJsonObject()) throw ApiException.validation("Invalid booking item");
            JsonObject it = el.getAsJsonObject();
            long carId = Validation.longValue(Json.getStr(it, "carId", ""), "carId");
            if (carId <= 0) throw ApiException.validation("Invalid carId");
            carIds.add(carId);
            String service = Json.clean(Json.getStr(it, "service", "Self-drive"));
            if (service.isEmpty()) service = "Self-drive";
            Validation.oneOf(service, "service", "Self-drive", "With driver");
            String city = Validation.required(Json.getStr(it, "city", ""), "city", 2, 40);
            String itStart = Json.clean(Json.getStr(it, "start", ""));
            String itEnd = Json.clean(Json.getStr(it, "end", ""));
            if (itStart.isEmpty()) itStart = Json.clean(start);
            if (itEnd.isEmpty()) itEnd = Json.clean(end);
            Validation.date(itStart, "start");
            Validation.date(itEnd, "end");
            if (itEnd.compareTo(itStart) < 0) throw ApiException.validation("Item end date must be after start date");
            String itStartDt = Json.clean(Json.getStr(it, "startDt", ""));
            String itEndDt = Json.clean(Json.getStr(it, "endDt", ""));
            if (itStartDt.isEmpty()) itStartDt = itStart + "T09:00";
            if (itEndDt.isEmpty()) itEndDt = itEnd + "T09:00";
            PricingService.hoursBetween(itStartDt, itEndDt);
            it.addProperty("carId", carId);
            it.addProperty("service", service);
            it.addProperty("city", city);
            it.addProperty("start", itStart);
            it.addProperty("end", itEnd);
            it.addProperty("startDt", itStartDt);
            it.addProperty("endDt", itEndDt);
        }

        final String fIdentityType = identityType;
        final String fIdentity = identity;
        final String fEmail = email;
        final String fPayment = payment;

        return db.tx(c -> {
            // 1) per-car advisory locks (sorted -> deadlock free)
            for (long id : carIds) {
                c.query("SELECT pg_advisory_xact_lock(hashtext('apex_car_' || $1))",
                        new String[]{String.valueOf(id)});
            }
            // 2) ban check
            if (bans.contains(c, fIdentity)) {
                throw ApiException.forbidden("This CNIC is banned from services");
            }
            // 3) re-validate identity docs exist
            if (identityDocs != null) {
                for (JsonElement el : identityDocs) {
                    long docId = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
                            ? el.getAsJsonPrimitive().getAsLong() : -1;
                    if (docId <= 0 || !documents.exists(c, docId)) {
                        throw ApiException.validation("An attached identity document is invalid");
                    }
                }
            }
            // 4) quote + clash check, server side
            long sumBase = 0, sumSaving = 0, sumDriver = 0, sumRental = 0, sumDeposit = 0;
            for (JsonElement el : items) {
                JsonObject it = el.getAsJsonObject();
                long carId = Json.getLong(it, "carId", 0);
                Car car = cars.get(c, carId);
                if (car == null) throw ApiException.notFound("Car not found");
                if (!"Active".equals(car.status)) {
                    throw ApiException.conflict("Car is not available for booking: " + car.name);
                }
                String clash = bookings.findOverlap(c, carId, it.get("startDt").getAsString(),
                        it.get("endDt").getAsString(), "");
                if (clash != null) {
                    throw ApiException.conflict("Time clash: " + car.name + " is already booked with " + clash);
                }
                Long itemDriverRate = it.has("driverRate") && Json.getLong(it, "driverRate", -1) > 0
                        ? Long.valueOf(Json.getLong(it, "driverRate", 0)) : null;
                JsonObject price = PricingService.quote(it.get("startDt").getAsString(),
                        it.get("endDt").getAsString(),
                        car.rate, car.hourlyRate, car.deposit,
                        it.get("service").getAsString(), itemDriverRate, cfgDriverRate, cap);
                it.add("price", price);
                sumBase += price.get("base").getAsLong();
                sumSaving += price.get("saving").getAsLong();
                sumDriver += price.get("driver").getAsLong();
                sumRental += price.get("rental").getAsLong();
                sumDeposit += price.get("deposit").getAsLong();
            }

            String pickupMode = Json.clean(Json.getStr(body, "pickupMode", "Office"));
            if (pickupMode.isEmpty()) pickupMode = "Office";
            long homeDelivery = "Home delivery".equals(pickupMode) || "Home".equals(pickupMode)
                    ? homeDeliveryCharge : 0;
            long total = sumRental + sumDeposit + homeDelivery;

            JsonObject totals = new JsonObject();
            totals.addProperty("base", sumBase);
            totals.addProperty("saving", sumSaving);
            totals.addProperty("driver", sumDriver);
            totals.addProperty("homeDelivery", homeDelivery);
            totals.addProperty("deposit", sumDeposit);
            totals.addProperty("rental", sumRental);
            totals.addProperty("total", total);

            // 5) assemble order (keep the client's extra fields, override truth)
            JsonObject order = body.deepCopy();
            if (!order.has("items")) order.add("items", items);
            order.addProperty("id", nextId());
            order.addProperty("userId", userId);
            order.addProperty("name", name);
            order.addProperty("email", fEmail);
            order.addProperty("phone", phone);
            order.addProperty("identityType", fIdentityType);
            order.addProperty("identity", fIdentity);
            order.addProperty("identityMasked", maskIdentity(fIdentityType, fIdentity));
            order.addProperty("identityStatus", Json.clean(Json.getStr(body, "identityStatus", "")).isEmpty()
                    ? "Pending" : Json.getStr(body, "identityStatus", "Pending"));
            order.add("totals", totals);
            order.addProperty("payment", fPayment);
            if (cashPayment(fPayment)) {
                order.addProperty("status", isAdmin && !status0.isEmpty() && "Confirmed".equals(status0)
                        ? "Confirmed" : "Pickup Pending");
                order.addProperty("paymentStatus", "Pay at pickup");
            } else {
                order.addProperty("status", "Pending Verification");
                order.addProperty("paymentStatus", "Pending Verification");
            }
            if (!isAdmin) {
                order.addProperty("paid", 0);
                order.addProperty("depositPaid", 0);
            }
            if (order.has("receiptDoc")) {
                long rd = Json.getLong(order, "receiptDoc", -1);
                if (rd > 0 && !documents.exists(c, rd)) {
                    throw ApiException.validation("Receipt document is invalid");
                }
            }
            order.addProperty("created", AuthService.nowIso());
            if (!order.has("items")) order.add("items", items);

            Booking b = Booking.fromJson(order);
            if (b == null) throw ApiException.bad("Cannot build booking");
            // ensure totals columns are filled from the computed totals
            b.data.add("totals", totals);
            bookings.upsert(c, b);

            notifs.notifyAdmins(c, "New booking " + b.id,
                    name + " booked " + itemCount(items) + " vehicle(s) - " + start + " to " + end,
                    "account", "Bookings");
            notifs.notify(c, userId, "Booking received",
                    "Your booking " + b.id + " has been received. " +
                    (cashPayment(fPayment) ? "Payment is due at pickup." : "Please upload your payment receipt."),
                    "account", null);
            return order;
        });
    }

    /* ---------------- cancel ---------------- */

    public JsonObject cancel(JsonObject session, String id) {
        boolean isAdmin = "admin".equals(Json.getStr(session, "role", ""));
        String userId = Json.getStr(session, "userId", "");
        return db.tx(c -> {
            Booking b = bookings.get(c, id);
            if (b == null) throw ApiException.notFound("Booking not found");
            if (!isAdmin && !b.userId.equals(userId)) {
                throw ApiException.forbidden("You can only cancel your own bookings");
            }
            List<String> allowed = List.of("Confirmed", "Pending Verification", "Pickup Pending");
            if (!allowed.contains(b.status)) {
                throw ApiException.conflict("Booking cannot be cancelled in status: " + b.status);
            }
            long fee = Math.round(b.rental * CANCELLATION_RATE);
            b.data.addProperty("status", "Cancelled");
            b.data.addProperty("cancellationFee", fee);
            b.data.addProperty("cancelledAt", AuthService.nowIso());
            if (fee > 0) {
                wallet.addAdmin(c, fee);
                wallet.ledger(c, "cancellation-fee", fee, "",
                        "Cancellation fee for booking " + b.id + " (" + b.customerName + ")");
            }
            bookings.upsert(c, b);
            notifs.notify(c, b.userId, "Booking cancelled",
                    "Booking " + b.id + " was cancelled." +
                    (fee > 0 ? " A 5% cancellation fee of " + fee + " PKR applies." : ""),
                    "account", null);
            notifs.notifyAdmins(c, "Booking cancelled: " + b.id,
                    b.customerName + " - " + (fee > 0 ? "fee " + fee : "no fee"), "account", "Bookings");
            return b.data;
        });
    }

    /* ---------------- pickup ---------------- */

    public JsonObject pickup(JsonObject session, String id, Long driverId) {
        return db.tx(c -> {
            Booking b = bookings.get(c, id);
            if (b == null) throw ApiException.notFound("Booking not found");
            if (!"Pickup Pending".equals(b.status) && !"Confirmed".equals(b.status)) {
                throw ApiException.conflict("Only Pickup Pending or Confirmed bookings can start (current: " + b.status + ")");
            }
            if (driverId != null && driverId > 0) {
                Driver d = drivers.get(c, driverId);
                if (d == null) throw ApiException.notFound("Driver not found");
                JsonArray items = b.data.get("items") != null && b.data.getAsJsonArray("items") != null
                        ? b.data.getAsJsonArray("items") : new JsonArray();
                for (JsonElement el : items) {
                    if (el.isJsonObject()) el.getAsJsonObject().addProperty("assignedDriver", driverId);
                }
            }
            b.data.addProperty("status", "Active");
            b.data.addProperty("pickupAt", AuthService.nowIso());
            bookings.upsert(c, b);
            notifs.notify(c, b.userId, "Vehicle picked up",
                    "Your booking " + b.id + " is now active. Enjoy the ride!", "account", null);
            return b.data;
        });
    }

    /* ---------------- return + late fees + payout ---------------- */

    public JsonObject ret(JsonObject session, String id, JsonObject body) {
        JsonObject config = settings.getConfig();
        int grace = PricingService.graceFromConfig(config);
        boolean cap = PricingService.capFromConfig(config);
        String actual = Json.clean(Json.getStr(body, "actualReturn", ""));

        return db.tx(c -> {
            Booking b = bookings.get(c, id);
            if (b == null) throw ApiException.notFound("Booking not found");
            if (!"Active".equals(b.status)) {
                throw ApiException.conflict("Only Active bookings can be returned (current: " + b.status + ")");
            }
            String expected = b.endDt != null && !b.endDt.isEmpty() ? b.endDt : expectedFromItems(b.data);
            if (expected.isEmpty()) throw ApiException.bad("Booking has no end time - cannot return");
            String actualReturn = actual.isEmpty() ? AuthService.nowIso() : actual;
            try {
                PricingService.parse(actualReturn);
            } catch (RuntimeException e) {
                throw ApiException.validation("Invalid actualReturn (expected YYYY-MM-DDTHH:mm)");
            }
            Car firstCar = firstCar(c, b);
            PricingService.Late late = PricingService.lateCharges(expected, actualReturn,
                    firstCar == null ? 0 : firstCar.rate,
                    firstCar == null ? 0 : firstCar.hourlyRate,
                    grace, cap);

            long finalAmount = b.total + late.charges;
            b.data.addProperty("status", "Completed");
            b.data.addProperty("actualReturn", actualReturn);
            b.data.addProperty("extraHours", late.extraHours);
            b.data.addProperty("extraCharges", late.charges);
            b.data.addProperty("finalAmount", finalAmount);
            b.data.addProperty("paymentStatus", late.charges > 0 ? "Late Charges Due" : "Settled");
            bookings.upsert(c, b);

            // 90/10 owner payout for every item that has an owner (company keeps 10%)
            JsonArray items = b.data.get("items") != null && b.data.getAsJsonArray("items") != null
                    ? b.data.getAsJsonArray("items") : new JsonArray();
            for (JsonElement el : items) {
                if (!el.isJsonObject()) continue;
                JsonObject it = el.getAsJsonObject();
                JsonObject price = Json.obj(it, "price");
                long rental = price != null ? Json.getLong(price, "rental", 0) : 0;
                if (rental <= 0) continue;
                Car car = cars.get(c, Json.getLong(it, "carId", 0));
                String ownerId = car == null || car.ownerId == null ? "" : car.ownerId;
                if (ownerId.isEmpty()) continue;
                long share = Math.round(rental * 0.90);
                if (share <= 0) continue;
                wallet.addOwner(c, ownerId, share);
                wallet.addAdmin(c, -share);
                wallet.ledger(c, "owner-payout", share, ownerId,
                        "90% payout to owner " + ownerId + " for " +
                                (car == null ? "car" : car.name) + " in booking " + b.id +
                                " (company keeps 10%)");
                notifs.notify(c, ownerId, "Payout released",
                        "Ride completed for " + (car == null ? "your car" : car.name) +
                                " - " + share + " PKR credited to your wallet (90% of " + rental + ").",
                        "wallet", null);
            }
            if (late.charges > 0) {
                wallet.ledger(c, "late-fee", late.charges, "",
                        "Late charges due from " + b.customerName + " for booking " + b.id +
                                " (" + late.extraHours + " late hour(s))");
            }
            notifs.notify(c, b.userId, "Booking completed",
                    "Booking " + b.id + " is completed." +
                    (late.charges > 0
                            ? " " + late.extraHours + " late hour(s) after " + grace + " min grace - " +
                              late.charges + " PKR late charges apply."
                            : " Thank you for choosing APEX!"),
                    "account", null);
            notifs.notifyAdmins(c, "Booking completed: " + b.id,
                    b.customerName + (late.charges > 0 ? " - late charges " + late.charges : ""),
                    "account", "Bookings");
            return b.data;
        });
    }

    /* ---------------- helpers ---------------- */

    private static boolean cashPayment(String payment) {
        String p = payment.toLowerCase();
        // "Cash on pickup" - must not match "JazzCash"/"easypaisa"
        return p.startsWith("cash");
    }

    private final java.security.SecureRandom idRandom = new java.security.SecureRandom();

    private String nextId() {
        long t = System.currentTimeMillis();
        String rand = Long.toString(idRandom.nextLong() & 0xFFFFFFFFL, 36).toUpperCase();
        return "B" + Long.toString(t, 36).toUpperCase() + rand;
    }

    private static String maskIdentity(String type, String identity) {
        if (identity.length() < 4) return "****";
        if ("CNIC".equals(type)) {
            return identity.substring(0, 5) + " **** " + identity.substring(identity.length() - 2);
        }
        return identity.substring(0, 2) + "****" + identity.substring(identity.length() - 2);
    }

    private static String expectedFromItems(JsonObject data) {
        if (data == null || data.get("items") == null || !data.get("items").isJsonArray()) return "";
        JsonArray items = data.getAsJsonArray("items");
        if (items.size() == 0) return "";
        String max = "";
        for (JsonElement el : items) {
            if (!el.isJsonObject()) continue;
            String e = Json.getStr(el.getAsJsonObject(), "endDt", "");
            if (e.compareTo(max) > 0) max = e;
        }
        return max;
    }

    private Car firstCar(com.apex.db.PgConnection c, Booking b) {
        if (b.data == null || b.data.get("items") == null || !b.data.get("items").isJsonArray()) return null;
        JsonArray items = b.data.getAsJsonArray("items");
        if (items.size() == 0) return null;
        JsonObject it = items.get(0).isJsonObject() ? items.get(0).getAsJsonObject() : null;
        if (it == null) return null;
        return cars.get(c, Json.getLong(it, "carId", 0));
    }

    private static int itemCount(JsonArray items) {
        return items == null ? 0 : items.size();
    }
}
