package com.apex.service;

import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Application;
import com.apex.model.Car;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Transactional owner application, approval, edit and deletion workflow. */
public final class OwnerVehicleService {
    private static final String APPROVED = "Approved for onboarding";
    private final Database db;
    private final ApplicationRepo applications;
    private final CarRepo cars;
    private final DocumentRepo documents;
    private final BanRepo bans;
    private final NotificationService notifications;
    private final OwnerRepo owners;

    public OwnerVehicleService(Database db, ApplicationRepo applications, CarRepo cars,
                               DocumentRepo documents, BanRepo bans,
                               NotificationService notifications, OwnerRepo owners) {
        this.db = db; this.applications = applications; this.cars = cars;
        this.documents = documents; this.bans = bans; this.notifications = notifications;
        this.owners = owners;
    }

    public JsonObject createApplication(JsonObject session, JsonObject body) {
        String ownerId = userId(session);
        return db.tx(c -> {
            JsonObject data = editable(new JsonObject(), body);
            data.addProperty("id", newApplicationId());
            data.addProperty("userId", ownerId);
            data.addProperty("email", Json.getStr(session, "email", Json.getStr(data, "email", "")));
            data.addProperty("status", "Submitted");
            data.addProperty("verification", "Pending");
            data.addProperty("editStatus", "Pending approval");
            data.addProperty("revision", 1);
            data.addProperty("createdAt", AuthService.nowIso());
            List<Long> imageIds = requiredImages(body);
            validateApplication(c, data, ownerId, imageIds);
            addImages(data, imageIds);
            Application application = Application.fromJson(data);
            applications.upsert(c, application);
            applications.setImages(c, application.id, ownerId, imageIds);
            application = applications.get(c, application.id);
            applications.history(c, application, "Submitted", ownerId, "owner");
            notifications.notifyAdmins(c, "New owner application",
                    application.owner + " applied with " + application.brand + " " + application.model,
                    "application/" + application.id, "Partner applications");
            notifications.notify(c, ownerId, "Application submitted",
                    "Your " + application.brand + " " + application.model +
                            " application is pending admin approval.",
                    "application/" + application.id, null);
            return application.toJson();
        });
    }

    /** Owner edits a listed car; the live row is intentionally untouched. */
    public JsonObject requestCarEdit(JsonObject session, long carId, JsonObject body) {
        String ownerId = userId(session);
        return db.tx(c -> {
            Car car = cars.getForUpdate(c, carId);
            if (car == null) throw ApiException.notFound("Listed car not found");
            if (!ownerId.equals(car.ownerId)) {
                throw ApiException.forbidden("You can only edit your own listed cars");
            }
            if (car.applicationId == null || car.applicationId.isBlank()) {
                throw ApiException.conflict("This legacy listing has no application workflow; contact APEX support");
            }
            Application existing = applications.getForUpdate(c, car.applicationId);
            if (existing == null || existing.data.has("deletedAt")) {
                throw ApiException.notFound("Vehicle application not found");
            }
            JsonObject data = editable(existing.data, body);
            List<Long> ids = body.has("photoIds") ? requiredImages(body) : applications.imageIds(c, existing.id);
            validateApplication(c, data, ownerId, ids);
            data.addProperty("id", existing.id);
            data.addProperty("userId", ownerId);
            data.addProperty("liveCarId", car.id);
            data.addProperty("status", "Submitted");
            data.addProperty("verification", "Pending");
            data.addProperty("editStatus", "Pending approval");
            data.addProperty("revision", existing.revision + 1);
            data.addProperty("submittedAt", AuthService.nowIso());
            data.remove("approvedAt");
            data.remove("approvedBy");
            addImages(data, ids);
            Application updated = Application.fromJson(data);
            applications.upsert(c, updated);
            if (body.has("photoIds")) applications.setImages(c, updated.id, ownerId, ids);
            updated = applications.get(c, updated.id);
            applications.history(c, updated, "Owner edit submitted", ownerId, "owner");
            notifications.notifyAdmins(c, "Owner vehicle update needs approval",
                    updated.owner + " submitted changes for " + updated.brand + " " + updated.model,
                    "application/" + updated.id, "Partner applications");
            notifications.notify(c, ownerId, "Vehicle update submitted",
                    "Your changes are pending approval. The current live listing remains unchanged.",
                    "application/" + updated.id, null);
            return updated.toJson();
        });
    }

    /** Owner may also correct an application before its first approval. */
    public JsonObject requestApplicationEdit(JsonObject session, String id, JsonObject body) {
        String ownerId = userId(session);
        Application current = db.with(c -> applications.get(c, id));
        if (current == null) throw ApiException.notFound("Application not found");
        if (!ownerId.equals(current.userId)) {
            throw ApiException.forbidden("You can only edit your own vehicle application");
        }
        if (current.liveCarId > 0) return requestCarEdit(session, current.liveCarId, body);
        return db.tx(c -> {
            Application existing = applications.getForUpdate(c, id);
            if (existing == null) throw ApiException.notFound("Application not found");
            if (!ownerId.equals(existing.userId)) throw ApiException.forbidden("You can only edit your own vehicle application");
            JsonObject data = editable(existing.data, body);
            List<Long> ids = body.has("photoIds") ? requiredImages(body) : applications.imageIds(c, id);
            validateApplication(c, data, ownerId, ids);
            data.addProperty("id", id); data.addProperty("userId", ownerId);
            data.addProperty("status", "Submitted"); data.addProperty("verification", "Pending");
            data.addProperty("editStatus", "Pending approval");
            data.addProperty("revision", existing.revision + 1);
            data.addProperty("submittedAt", AuthService.nowIso());
            addImages(data, ids);
            Application updated = Application.fromJson(data);
            applications.upsert(c, updated);
            if (body.has("photoIds")) applications.setImages(c, id, ownerId, ids);
            updated = applications.get(c, id);
            applications.history(c, updated, "Owner application edit submitted", ownerId, "owner");
            notifications.notifyAdmins(c, "Owner application updated",
                    updated.owner + " updated " + updated.brand + " " + updated.model,
                    "application/" + id, "Partner applications");
            return updated.toJson();
        });
    }

    /** Admin edits review fields and optionally approves/rejects in one atomic operation. */
    public JsonObject adminUpdate(JsonObject session, String id, JsonObject body) {
        String adminId = userId(session);
        return db.tx(c -> {
            Application existing = applications.getForUpdate(c, id);
            if (existing == null) throw ApiException.notFound("Application not found");
            if (existing.data.has("deletedAt") || "Deleted".equals(existing.status)) {
                throw ApiException.conflict("Deleted applications cannot be approved");
            }
            String targetStatus = Json.clean(Json.getStr(body, "status", existing.status));
            String verification = Json.clean(Json.getStr(body, "verification", existing.verification));
            JsonObject data = editable(existing.data, body);
            data.addProperty("id", existing.id);
            data.addProperty("userId", existing.userId);
            data.addProperty("status", targetStatus);
            data.addProperty("verification", verification.isEmpty() ? "Pending" : verification);
            data.addProperty("revision", existing.revision);
            if (body.has("note")) data.addProperty("note", Json.clean(Json.getStr(body, "note", "")));

            List<Long> ids = body.has("photoIds") ? requiredImages(body) : applications.imageIds(c, id);
            validateApplication(c, data, existing.userId, ids);
            addImages(data, ids);
            if (body.has("photoIds")) applications.setImages(c, id, existing.userId, ids);

            if (APPROVED.equals(targetStatus)) {
                if (!"Verified".equals(verification)) {
                    throw ApiException.validation("Verify the application and all three vehicle images before approval");
                }
                if (bans.contains(c, Validation.cnic(Json.getStr(data, "cnic", "")))) {
                    throw ApiException.forbidden("Approval blocked because this CNIC is banned");
                }
                if (APPROVED.equals(existing.status) && "Published".equals(existing.editStatus)) {
                    throw ApiException.conflict("This application has already been approved");
                }
                return approve(c, existing, data, ids, adminId);
            }

            data.addProperty("editStatus", "Rejected".equals(targetStatus) ? "Rejected" : "Pending approval");
            Application updated = Application.fromJson(data);
            applications.upsert(c, updated);
            updated = applications.get(c, id);
            applications.history(c, updated, "Admin review: " + targetStatus, adminId, "admin");
            notifications.notify(c, existing.userId, "Application " + targetStatus,
                    "Your vehicle application " + id + " is now " + targetStatus + ".",
                    "application/" + id, null);
            return updated.toJson();
        });
    }

    public JsonArray listedCars(JsonObject session) {
        String ownerId = userId(session);
        return db.with(c -> {
            JsonArray out = new JsonArray();
            for (Car car : cars.listByOwner(c, ownerId)) {
                JsonObject row = car.toJson();
                if (car.applicationId != null && !car.applicationId.isBlank()) {
                    Application app = applications.get(c, car.applicationId);
                    if (app != null) {
                        row.addProperty("applicationStatus", app.status);
                        row.addProperty("editStatus", app.editStatus);
                        row.addProperty("applicationRevision", app.revision);
                    }
                }
                out.add(row);
            }
            return out;
        });
    }

    public JsonObject deleteCar(JsonObject session, long carId) {
        String ownerId = userId(session);
        return db.tx(c -> {
            // Same lock namespace as booking creation: a booking cannot appear
            // between the blocker checks and the durable deletion.
            c.query("SELECT pg_advisory_xact_lock(hashtext('apex_car_' || $1))",
                    new String[]{String.valueOf(carId)});
            Car car = cars.getForUpdate(c, carId);
            if (car == null) throw ApiException.notFound("Listed car not found");
            if (!ownerId.equals(car.ownerId)) {
                throw ApiException.forbidden("You can only delete your own listed cars");
            }
            String blocker = deletionBlocker(c, carId);
            if (blocker != null) throw ApiException.conflict(blocker);

            c.query("INSERT INTO owner_car_deletions(car_id, application_id, owner_id, snapshot, deleted_by)" +
                    " VALUES ($1,$2,$3,$4::jsonb,$5)",
                    new String[]{String.valueOf(carId), emptyToNull(car.applicationId), ownerId,
                            car.data.toString(), ownerId});
            if (car.applicationId != null && !car.applicationId.isBlank()) {
                Application application = applications.getForUpdate(c, car.applicationId);
                if (application != null) {
                    JsonObject data = application.data.deepCopy();
                    data.addProperty("status", "Deleted");
                    data.addProperty("editStatus", "Deleted");
                    data.addProperty("deletedAt", AuthService.nowIso());
                    Application deleted = Application.fromJson(data);
                    applications.upsert(c, deleted);
                    applications.history(c, deleted, "Owner deleted listing", ownerId, "owner");
                }
            }
            cars.delete(c, carId);
            notifications.notifyAdmins(c, "Owner listing deleted",
                    car.name + " (car " + carId + ") was deleted by its owner after settlement checks passed.",
                    "", "Fleet");
            JsonObject out = new JsonObject();
            out.addProperty("status", "deleted");
            out.addProperty("carId", carId);
            return out;
        });
    }

    public JsonObject savePayoutAccount(JsonObject session, JsonObject body) {
        String ownerId = userId(session);
        String method = Validation.required(Json.getStr(body, "method", ""), "payout method", 3, 40);
        Validation.oneOf(method, "payout method", "Bank account / IBAN", "JazzCash", "Easypaisa");
        String title = Validation.required(Json.getStr(body, "accountTitle", ""), "account title", 2, 100);
        String account = Json.clean(Json.getStr(body, "accountNumber", ""));
        String iban = Json.clean(Json.getStr(body, "iban", "")).replace(" ", "").toUpperCase();
        String phone = Json.clean(Json.getStr(body, "phone", "")).replaceAll("[\\s-]", "");
        if ("Bank account / IBAN".equals(method)) {
            if (account.isEmpty() && iban.isEmpty()) throw ApiException.validation("Bank account number or IBAN is required");
            if (!iban.isEmpty() && !iban.matches("^PK[0-9A-Z]{22}$")) {
                throw ApiException.validation("Enter a valid 24-character Pakistan IBAN");
            }
            if (!account.isEmpty() && !account.matches("^[0-9A-Za-z-]{6,34}$")) {
                throw ApiException.validation("Enter a valid bank account number");
            }
            phone = "";
        } else {
            if (!phone.matches("^\\+?[0-9]{10,15}$")) {
                throw ApiException.validation("Enter a valid wallet phone/account number");
            }
            account = ""; iban = "";
        }
        final String fAccount = account, fIban = iban, fPhone = phone;
        return db.tx(c -> owners.savePayoutAccount(c, ownerId, method, title, fAccount, fIban, fPhone));
    }

    public JsonObject payoutAccount(JsonObject session, String requestedOwnerId) {
        boolean admin = "admin".equals(Json.getStr(session, "role", ""));
        String viewer = userId(session);
        String ownerId = requestedOwnerId == null || requestedOwnerId.isBlank() ? viewer : requestedOwnerId;
        if (!admin && !viewer.equals(ownerId)) {
            throw ApiException.forbidden("You can only access your own payout account");
        }
        return owners.payoutAccount(ownerId);
    }

    public JsonObject earnings(JsonObject session) { return owners.earnings(userId(session)); }

    private JsonObject approve(PgConnection c, Application previous, JsonObject data,
                               List<Long> ids, String adminId) {
        Car live = cars.getByApplication(c, previous.id);
        long carId = live == null ? nextCarId(c) : live.id;
        c.query("SELECT pg_advisory_xact_lock(hashtext('apex_car_' || $1))",
                new String[]{String.valueOf(carId)});
        JsonObject car = live == null ? new JsonObject() : live.data.deepCopy();
        String name = Json.getStr(data, "brand", "") + " " + Json.getStr(data, "model", "");
        car.addProperty("id", carId);
        car.addProperty("name", name.trim());
        car.addProperty("brand", Json.getStr(data, "brand", ""));
        car.addProperty("category", defaultValue(Json.getStr(data, "category", ""), "Sedan"));
        car.addProperty("origin", defaultValue(Json.getStr(data, "origin", ""), "Pakistan"));
        car.addProperty("image", defaultValue(Json.getStr(data, "image", ""), "pk-city"));
        car.addProperty("year", Json.getInt(data, "year", 0));
        long rate = Json.getLong(data, "rate", 0);
        car.addProperty("rate", rate);
        car.addProperty("hourlyRate", positiveOr(Json.getLong(data, "hourlyRate", 0), Math.max(1, rate / 10)));
        car.addProperty("deposit", positiveOr(Json.getLong(data, "deposit", 0), rate * 5));
        car.addProperty("seats", positiveOr(Json.getInt(data, "seats", 0), 5));
        car.addProperty("engine", defaultValue(Json.getStr(data, "engine", ""), "Not specified"));
        car.addProperty("power", defaultValue(Json.getStr(data, "power", ""), "Not specified"));
        car.addProperty("fuel", defaultValue(Json.getStr(data, "fuel", ""), "Petrol"));
        car.addProperty("km", Json.getInt(data, "mileage", 0) + " km");
        car.addProperty("color", defaultValue(Json.getStr(data, "color", ""), "Not specified"));
        car.addProperty("plate", Json.getStr(data, "registration", ""));
        car.addProperty("condition", Json.getStr(data, "condition", "Good"));
        car.addProperty("status", "Active");
        car.addProperty("marketNote", "Verified APEX partner vehicle");
        car.addProperty("ownerId", previous.userId);
        car.addProperty("applicationId", previous.id);
        car.addProperty("ownerShare", 0.90);
        car.addProperty("companyShare", 0.10);
        JsonArray features = data.has("features") && data.get("features").isJsonArray()
                ? data.getAsJsonArray("features").deepCopy() : new JsonArray();
        if (features.size() == 0) features.add("Verified partner vehicle");
        car.add("features", features);
        JsonArray urls = new JsonArray();
        for (Long id : ids) urls.add("/api/vehicle-images/" + id);
        car.add("images", urls);
        car.add("imageIds", longs(ids));
        car.addProperty("mainImageId", ids.get(0));
        car.addProperty("interiorImageId", ids.get(1));
        car.addProperty("detailImageId", ids.get(2));
        car.addProperty("customImage", "/api/vehicle-images/" + ids.get(0));
        JsonObject custom = new JsonObject();
        String key = Json.getStr(car, "image", "pk-city");
        custom.addProperty(key + "-interior", "/api/vehicle-images/" + ids.get(1));
        custom.addProperty(key + "-detail", "/api/vehicle-images/" + ids.get(2));
        custom.addProperty(key + "-engine", "/api/vehicle-images/" + ids.get(2));
        car.add("customImages", custom);
        cars.upsert(c, Car.fromJson(car));

        data.addProperty("status", APPROVED);
        data.addProperty("editStatus", "Published");
        data.addProperty("verification", "Verified");
        data.addProperty("liveCarId", carId);
        data.addProperty("approvedBy", adminId);
        data.addProperty("approvedAt", AuthService.nowIso());
        addImages(data, ids);
        Application approved = Application.fromJson(data);
        applications.upsert(c, approved);
        approved = applications.get(c, approved.id);
        applications.history(c, approved, live == null ? "Approved and published" : "Update approved and published",
                adminId, "admin");
        for (Long docId : ids) documents.updateStatus(c, docId, "Verified");
        notifications.notify(c, previous.userId, "Vehicle listing approved",
                name.trim() + " is now live in the APEX fleet.", "application/" + previous.id, null);
        JsonObject result = approved.toJson();
        result.add("car", Car.fromJson(car).toJson());
        return result;
    }

    private String deletionBlocker(PgConnection c, long carId) {
        String id = String.valueOf(carId);
        QueryResult active = c.query("SELECT b.id, b.status FROM booking_items i JOIN bookings b ON b.id=i.booking_id" +
                " WHERE i.car_id=$1 AND b.status NOT IN ('Cancelled','Completed','Rejected') LIMIT 1",
                new String[]{id});
        if (active.rowCount() > 0) return "Cannot delete: booking " + active.rows.get(0)[0] +
                " is " + active.rows.get(0)[1] + ". Complete or cancel it first.";
        QueryResult payment = c.query("SELECT DISTINCT b.id FROM booking_items i JOIN bookings b ON b.id=i.booking_id" +
                " LEFT JOIN payments p ON p.booking_id=b.id WHERE i.car_id=$1 AND (" +
                " p.status='Pending Verification' OR COALESCE(b.payment_status,'') IN" +
                " ('Pending Verification','Reupload Requested','Rejected','Refund Pending','Deposit Refund Pending')) LIMIT 1",
                new String[]{id});
        if (payment.rowCount() > 0) return "Cannot delete: payment/refund verification is pending for booking " + payment.first() + ".";
        QueryResult settlement = c.query("SELECT b.id, COALESCE(b.payment_status,'') FROM booking_items i" +
                " JOIN bookings b ON b.id=i.booking_id WHERE i.car_id=$1 AND b.status='Completed' AND (" +
                " b.owner_payout_done=FALSE OR COALESCE(b.payment_status,'') IN" +
                " ('Pending Verification','Reupload Requested','Rejected','Late Charges Due','Refund Pending','Deposit Refund Pending')" +
                " OR (b.data ? 'refundStatus' AND COALESCE(b.data->>'refundStatus','') NOT IN ('Completed','Refunded','Not required'))" +
                " OR (b.data ? 'depositRefundStatus' AND COALESCE(b.data->>'depositRefundStatus','') NOT IN ('Completed','Refunded','Not required'))" +
                ") LIMIT 1", new String[]{id});
        if (settlement.rowCount() > 0) return "Cannot delete: rental/settlement is incomplete for booking " +
                settlement.rows.get(0)[0] + " (" + settlement.rows.get(0)[1] + ").";
        QueryResult payout = c.query("SELECT booking_id FROM owner_payout_ledger WHERE car_id=$1" +
                " AND status NOT IN ('Completed','Rejected') LIMIT 1", new String[]{id});
        if (payout.rowCount() > 0) return "Cannot delete: owner payout is pending for booking " + payout.first() + ".";
        return null;
    }

    private void validateApplication(PgConnection c, JsonObject d, String ownerId, List<Long> ids) {
        Validation.required(Json.getStr(d, "owner", ""), "owner name", 2, 70);
        Validation.required(Validation.phone(Json.getStr(d, "phone", "")), "phone", 6, 20);
        Validation.required(Json.getStr(d, "brand", ""), "vehicle make", 1, 50);
        Validation.required(Json.getStr(d, "model", ""), "vehicle model", 1, 70);
        int year = Json.getInt(d, "year", 0);
        if (year < 1990 || year > LocalDate.now().getYear() + 1) throw ApiException.validation("Vehicle year is invalid");
        Validation.required(Json.getStr(d, "registration", ""), "registration", 3, 40);
        d.addProperty("cnic", Validation.cnic(Json.getStr(d, "cnic", "")));
        Validation.required(Json.getStr(d, "license", ""), "driving licence", 3, 60);
        Validation.required(Json.getStr(d, "city", ""), "city", 2, 50);
        if (Json.getInt(d, "mileage", -1) < 0) throw ApiException.validation("Odometer mileage is invalid");
        if (Json.getLong(d, "rate", 0) <= 0) throw ApiException.validation("Daily rate must be greater than zero");
        if (ids.size() != 3) throw ApiException.validation("Exactly 3 vehicle images are required");
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != 3) throw ApiException.validation("The 3 vehicle images must be different files");
        for (Long imageId : ids) {
            if (imageId <= 0 || !documents.isOwnedVehiclePhoto(c, imageId, ownerId)) {
                throw ApiException.validation("Every vehicle image must be an uploaded photo owned by this account");
            }
            if (documents.vehiclePhotoAttachedElsewhere(c, imageId, Json.getStr(d, "id", ""))) {
                throw ApiException.validation("A vehicle image cannot be reused by another application");
            }
        }
    }

    private static JsonObject editable(JsonObject base, JsonObject changes) {
        JsonObject out = base == null ? new JsonObject() : base.deepCopy();
        String[] fields = {"owner","phone","email","brand","model","year","registration","cnic","license",
                "city","mileage","condition","rate","hourlyRate","deposit","preference","availableFrom","notes",
                "category","origin","engine","power","fuel","color","seats","features","image"};
        for (String field : fields) if (changes.has(field)) out.add(field, changes.get(field).deepCopy());
        return out;
    }

    private static List<Long> requiredImages(JsonObject body) {
        if (!body.has("photoIds") || !body.get("photoIds").isJsonArray()) {
            throw ApiException.validation("Exactly 3 vehicle images are required");
        }
        JsonArray a = body.getAsJsonArray("photoIds");
        if (a.size() != 3) throw ApiException.validation("Exactly 3 vehicle images are required");
        List<Long> ids = new ArrayList<>();
        for (JsonElement el : a) {
            try { ids.add(el.getAsLong()); }
            catch (RuntimeException e) { throw ApiException.validation("Invalid vehicle image reference"); }
        }
        return ids;
    }

    private static void addImages(JsonObject data, List<Long> ids) {
        data.add("photoIds", longs(ids));
        JsonArray urls = new JsonArray();
        for (Long id : ids) urls.add("/api/vehicle-images/" + id);
        data.add("imageUrls", urls);
        data.addProperty("photoCount", ids.size());
    }
    private static JsonArray longs(List<Long> ids) {
        JsonArray a = new JsonArray(); for (Long id : ids) a.add(id); return a;
    }
    private static long nextCarId(PgConnection c) {
        c.simpleQuery("SELECT pg_advisory_xact_lock(hashtext('apex_next_car_id'))");
        QueryResult r = c.query("SELECT COALESCE(max(id), 1000) + 1 FROM cars", null);
        return Long.parseLong(r.first());
    }
    private static String newApplicationId() {
        return "APP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
    private static String userId(JsonObject session) {
        String id = Json.getStr(session, "userId", "");
        if (id.isBlank()) throw ApiException.unauth("Account session is required");
        return id;
    }
    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private static long positiveOr(long value, long fallback) { return value > 0 ? value : fallback; }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
