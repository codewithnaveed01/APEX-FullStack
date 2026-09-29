package com.apex.service;

import com.apex.dao.BookingRepo;
import com.apex.dao.DriverPayoutRepo;
import com.apex.dao.DriverRepo;
import com.apex.dao.WalletRepo;
import com.apex.db.Database;
import com.apex.model.Booking;
import com.apex.model.Driver;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Server-authoritative driver payout workflow. Amount and payee come from the completed booking item only. */
public final class DriverPayoutService {
    private final Database db;
    private final BookingRepo bookings;
    private final DriverRepo drivers;
    private final DriverPayoutRepo payouts;
    private final WalletRepo wallet;

    public DriverPayoutService(Database db, BookingRepo bookings, DriverRepo drivers,
                               DriverPayoutRepo payouts, WalletRepo wallet) {
        this.db = db; this.bookings = bookings; this.drivers = drivers;
        this.payouts = payouts; this.wallet = wallet;
    }

    public JsonObject saveAccount(long driverId, JsonObject body) {
        String method = Validation.required(Json.getStr(body, "method", ""), "payout method", 3, 40);
        Validation.oneOf(method, "payout method", "Bank account / IBAN", "JazzCash", "Easypaisa");
        String title = Validation.required(Json.getStr(body, "accountTitle", ""), "account title", 2, 100);
        String account = Json.clean(Json.getStr(body, "accountNumber", ""));
        String iban = Json.clean(Json.getStr(body, "iban", "")).replace(" ", "").toUpperCase();
        String phone = Json.clean(Json.getStr(body, "phone", "")).replaceAll("[\\s-]", "");
        if ("Bank account / IBAN".equals(method)) {
            if (account.isBlank() && iban.isBlank()) throw ApiException.validation("Bank account number or IBAN is required");
            if (!iban.isBlank() && !iban.matches("^PK[0-9A-Z]{22}$")) throw ApiException.validation("Enter a valid Pakistan IBAN");
            if (!account.isBlank() && !account.matches("^[0-9A-Za-z-]{6,34}$")) throw ApiException.validation("Enter a valid bank account number");
            phone = "";
        } else {
            if (!phone.matches("^\\+?[0-9]{10,15}$")) throw ApiException.validation("Enter a valid wallet phone/account number");
            account = ""; iban = "";
        }
        final String fAccount = account, fIban = iban, fPhone = phone;
        return db.tx(c -> {
            if (drivers.get(c, driverId) == null) throw ApiException.notFound("Driver not found");
            return payouts.saveAccount(c, driverId, method, title, fAccount, fIban, fPhone);
        });
    }

    public JsonObject account(long driverId) {
        return db.with(c -> {
            if (drivers.get(c, driverId) == null) throw ApiException.notFound("Driver not found");
            return payouts.account(c, driverId, true);
        });
    }

    public JsonArray history(long driverId) {
        return db.with(c -> {
            if (drivers.get(c, driverId) == null) throw ApiException.notFound("Driver not found");
            return payouts.history(c, driverId);
        });
    }

    /** Does not accept amount or driverId from the client. Unique booking/item makes retries idempotent. */
    public JsonObject pay(JsonObject adminSession, JsonObject body) {
        String bookingId = Validation.required(Json.getStr(body, "bookingId", ""), "booking id", 2, 100);
        int itemPos = (int) Validation.longValue(Json.getStr(body, "bookingItemPos", ""), "booking item position");
        if (itemPos < 0 || itemPos > 20) throw ApiException.validation("Invalid booking item position");
        String reference = Validation.required(Json.getStr(body, "reference", ""), "payment reference", 2, 100);
        String note = Json.clean(Json.getStr(body, "note", ""));
        String paidBy = Json.getStr(adminSession, "username", "admin");

        return db.tx(c -> {
            // Serializes retries and concurrent administrators before any debit occurs.
            c.query("SELECT pg_advisory_xact_lock(hashtext('apex_driver_payout_' || $1 || ':' || $2))",
                    new String[]{bookingId, String.valueOf(itemPos)});
            JsonObject prior = payouts.payout(c, bookingId, itemPos);
            if (prior != null) return prior;

            Booking booking = bookings.get(c, bookingId);
            if (booking == null) throw ApiException.notFound("Booking not found");
            if (!"Completed".equals(booking.status)) throw ApiException.conflict("Drivers can be paid only after a completed booking");
            JsonArray items = booking.data.has("items") && booking.data.get("items").isJsonArray()
                    ? booking.data.getAsJsonArray("items") : new JsonArray();
            if (itemPos >= items.size() || !items.get(itemPos).isJsonObject()) {
                throw ApiException.validation("Booking item does not exist");
            }
            JsonObject item = items.get(itemPos).getAsJsonObject();
            long driverId = Json.getLong(item, "assignedDriver", 0);
            if (driverId <= 0) throw ApiException.conflict("This booking item has no assigned driver");
            Driver driver = drivers.get(c, driverId);
            if (driver == null || !driver.active || !("Approved".equalsIgnoreCase(driver.status) || "Active".equalsIgnoreCase(driver.status))) {
                throw ApiException.conflict("The assigned driver is not approved and active");
            }
            JsonObject price = Json.obj(item, "price");
            long amount = price == null ? 0 : Json.getLong(price, "driver", 0);
            if (amount <= 0) throw ApiException.conflict("This booking item has no server-calculated driver charge");

            JsonObject account = payouts.account(c, driverId, true);
            if (!Json.getBool(account, "configured", false)) throw ApiException.conflict("Driver payout account is not configured");
            String method = Json.getStr(account, "method", "");
            String destination = firstNonEmpty(Json.getStr(account, "accountNumber", ""),
                    Json.getStr(account, "iban", ""), Json.getStr(account, "phone", ""));
            if (destination.isBlank()) throw ApiException.conflict("Driver payout account is incomplete");
            if (!wallet.deductAdminIfSufficient(c, amount)) {
                throw ApiException.conflict("Insufficient admin wallet balance");
            }
            JsonObject paid = payouts.record(c, bookingId, itemPos, driverId, amount, method, destination,
                    Json.getStr(account, "displayLast4", ""), reference, note, paidBy);
            wallet.ledger(c, "driver-payout", amount, "driver:" + driverId,
                    "Driver payout for " + bookingId + " item " + itemPos + " · ref " + reference +
                            (note.isBlank() ? "" : " · " + note));
            return paid;
        });
    }

    public JsonObject itemPayout(String bookingId, int itemPos) {
        return db.with(c -> payouts.payout(c, bookingId, itemPos));
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
