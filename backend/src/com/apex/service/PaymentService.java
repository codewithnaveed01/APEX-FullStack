package com.apex.service;

import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.model.Booking;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Payment flow:
 *   customer submits receipt -> Pending Verification (NEVER auto-verified)
 *   admin reviews: verify (credits wallet) | reject | request re-upload
 * Wallet money movements are ledgered in the same transaction.
 */
public final class PaymentService {

    private final Database db;
    private final BookingRepo bookings;
    private final PaymentRepo payments;
    private final DocumentRepo documents;
    private final WalletRepo wallet;
    private final NotificationService notifs;

    public PaymentService(Database db, BookingRepo bookings, PaymentRepo payments,
                          DocumentRepo documents, WalletRepo wallet, NotificationService notifs) {
        this.db = db; this.bookings = bookings; this.payments = payments;
        this.documents = documents; this.wallet = wallet; this.notifs = notifs;
    }

    public JsonObject submit(JsonObject session, JsonObject body) {
        boolean isAdmin = "admin".equals(Json.getStr(session, "role", ""));
        String userId = Json.getStr(session, "userId", "");
        String bookingId = Validation.required(Json.getStr(body, "bookingId", ""), "bookingId");
        String method = Validation.required(Json.getStr(body, "method", ""), "payment method", 2, 40);
        long amount = Validation.amount(Json.getStr(body, "amount", ""), "amount");
        if (amount <= 0) throw ApiException.validation("Amount must be greater than zero");
        String tid = Validation.required(Json.getStr(body, "tid", ""), "transaction ID", 4, 64);
        long receiptDoc = Validation.longValue(Json.getStr(body, "receiptDoc", ""), "receipt document");
        if (receiptDoc <= 0) throw ApiException.validation("Receipt document is required");

        return db.tx(c -> {
            Booking b = bookings.get(c, bookingId);
            if (b == null) throw ApiException.notFound("Booking not found");
            if (!isAdmin && !b.userId.equals(userId)) {
                throw ApiException.forbidden("You can only pay for your own bookings");
            }
            if ("Cancelled".equals(b.status) || "Completed".equals(b.status)) {
                throw ApiException.conflict("This booking is already " + b.status.toLowerCase() + " - no payment needed");
            }
            if (payments.pendingForBooking(c, bookingId) != null) {
                throw ApiException.conflict("A payment is already pending verification for this booking");
            }
            if (!documents.exists(c, receiptDoc)) {
                throw ApiException.validation("Receipt document does not exist");
            }
            long id = payments.insert(c, bookingId, method, amount, tid, receiptDoc);
            b.data.addProperty("status", "Pending Verification");
            b.data.addProperty("paymentStatus", "Pending Verification");
            b.data.addProperty("tid", tid);
            b.data.addProperty("receiptDoc", receiptDoc);
            b.data.addProperty("payment", method);
            bookings.upsert(c, b);

            notifs.notifyAdmins(c, "Payment verification needed",
                    b.customerName + " submitted " + amount + " PKR (" + method + ", TID " + tid +
                            ") for booking " + b.id, "booking/" + b.id, "Payments");
            notifs.notify(c, b.userId, "Payment submitted",
                    "Your payment of " + amount + " PKR for booking " + b.id +
                    " is pending admin verification.", "booking/" + b.id, null);
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("status", "Pending Verification");
            return o;
        });
    }

    public JsonObject review(JsonObject adminSession, long id, JsonObject body) {
        String action = Validation.required(Json.getStr(body, "action", ""), "action", 2, 20);
        String note = Json.clean(Json.getStr(body, "note", ""));
        String reviewer = Json.getStr(adminSession, "username", "admin");

        return db.tx(c -> {
            JsonObject p = payments.get(c, id);
            if (p == null) throw ApiException.notFound("Payment not found");
            if (!"Pending Verification".equals(p.get("status").getAsString())) {
                throw ApiException.conflict("Payment is already " + p.get("status").getAsString().toLowerCase());
            }
            String bookingId = p.get("bookingId").getAsString();
            Booking b = bookings.get(c, bookingId);
            long amount = p.get("amount").getAsLong();
            String status;
            switch (action) {
                case "verify": {
                    status = "Verified";
                    payments.updateStatus(c, id, status, note, reviewer);
                    wallet.addAdmin(c, amount);
                    wallet.ledger(c, "payment", amount, "",
                            "Verified payment for booking " + bookingId +
                            (p.get("tid").getAsString().isEmpty() ? "" : " (TID " + p.get("tid").getAsString() + ")") +
                            (note.isEmpty() ? "" : " - " + note));
                    if (b != null) {
                        b.data.addProperty("paymentStatus", "Verified");
                        if ("Pending Verification".equals(b.status)) {
                            b.data.addProperty("status", "Pickup Pending");
                        }
                        if (amount > b.paid) b.data.addProperty("paid", amount);
                        bookings.upsert(c, b);
                        notifs.notify(c, b.userId, "Payment verified",
                                "Your payment of " + amount + " PKR for booking " + bookingId +
                                        " has been verified. " +
                                        ("Pickup Pending".equals(b.data.get("status").getAsString())
                                                ? "Your vehicle is ready for pickup." : ""),
                                "booking/" + bookingId, null);
                    }
                    break;
                }
                case "reject": {
                    status = "Rejected";
                    payments.updateStatus(c, id, status, note, reviewer);
                    if (b != null) {
                        b.data.addProperty("paymentStatus", "Rejected");
                        bookings.upsert(c, b);
                        notifs.notify(c, b.userId, "Payment rejected",
                                "Payment for booking " + bookingId + " was rejected." +
                                        (note.isEmpty() ? "" : " Reason: " + note), "booking/" + bookingId, null);
                    }
                    break;
                }
                case "reupload": {
                    status = "Reupload";
                    payments.updateStatus(c, id, status, note, reviewer);
                    if (b != null) {
                        b.data.addProperty("paymentStatus", "Reupload Requested");
                        bookings.upsert(c, b);
                        notifs.notify(c, b.userId, "Payment receipt needs re-upload",
                                "Please re-upload a valid payment receipt for booking " + bookingId +
                                        (note.isEmpty() ? "" : ". " + note), "booking/" + bookingId, null);
                    }
                    break;
                }
                default:
                    throw ApiException.validation("action must be verify, reject or reupload");
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("status", status);
            return o;
        });
    }

    public JsonObject record(JsonObject adminSession, JsonObject body) {
        long amount = Validation.amount(Json.getStr(body, "amount", ""), "amount");
        if (amount <= 0) throw ApiException.validation("Amount must be greater than zero");
        String type = Json.clean(Json.getStr(body, "type", "cash"));
        if (type.isEmpty()) type = "cash";
        String note = Json.clean(Json.getStr(body, "note", ""));
        String account = Json.clean(Json.getStr(body, "account", ""));
        String reviewer = Json.getStr(adminSession, "username", "admin");
        final String fType = type;
        final String fAccount = account;
        final String fNote = note;

        return db.tx(c -> {
            wallet.addAdmin(c, amount);
            wallet.ledger(c, fType, amount, fAccount,
                    fNote.isEmpty() ? "Recorded by " + reviewer : fNote);
            JsonObject o = new JsonObject();
            o.addProperty("balance", walletAdminBalance(c));
            o.addProperty("status", "recorded");
            return o;
        });
    }

    public JsonObject walletInfo() {
        return db.with(c -> {
            JsonObject o = new JsonObject();
            o.addProperty("balance", walletAdminBalance(c));
            JsonObject owners = new JsonObject();
            wallet.ownerWallets(c).forEach((k, v) -> owners.addProperty(k, v));
            o.add("ownerWallets", owners);
            o.add("transactions", wallet.transactions(c, 200));
            return o;
        });
    }

    public JsonObject addCash(JsonObject adminSession, JsonObject body) {
        long amount = Validation.amount(Json.getStr(body, "amount", ""), "amount");
        if (amount <= 0) throw ApiException.validation("Amount must be greater than zero");
        String note = Json.clean(Json.getStr(body, "note", "Cash added to wallet"));
        String reviewer = Json.getStr(adminSession, "username", "admin");
        return db.tx(c -> {
            wallet.addAdmin(c, amount);
            wallet.ledger(c, "cash", amount, "", note + " (by " + reviewer + ")");
            JsonObject o = new JsonObject();
            o.addProperty("balance", walletAdminBalance(c));
            return o;
        });
    }

    public JsonObject withdraw(JsonObject adminSession, JsonObject body) {
        long amount = Validation.amount(Json.getStr(body, "amount", ""), "amount");
        if (amount <= 0) throw ApiException.validation("Amount must be greater than zero");
        String account = Json.clean(Json.getStr(body, "account", ""));
        if (account.isEmpty()) throw ApiException.validation("Missing payout account/owner");
        String note = Json.clean(Json.getStr(body, "note", "Wallet withdrawal"));
        String reviewer = Json.getStr(adminSession, "username", "admin");

        return db.tx(c -> {
            long balance = walletAdminBalance(c);
            if (amount > balance) {
                throw ApiException.conflict("Insufficient wallet balance: " + balance);
            }
            wallet.addAdmin(c, -amount);
            wallet.ledger(c, "withdrawal", amount, account, note + " (by " + reviewer + ")");
            JsonObject o = new JsonObject();
            o.addProperty("balance", walletAdminBalance(c));
            o.addProperty("status", "Withdrawn");
            return o;
        });
    }

    private static long walletAdminBalance(com.apex.db.PgConnection c) {
        var r = c.query("SELECT balance FROM wallet WHERE id = 1", null);
        return r.rowCount() > 0 && r.rows.get(0)[0] != null ? Long.parseLong(r.rows.get(0)[0]) : 0;
    }
}
