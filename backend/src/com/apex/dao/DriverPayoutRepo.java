package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.security.PayoutCrypto;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Driver payout accounts and immutable payout ledger. Sensitive values are encrypted at rest. */
public final class DriverPayoutRepo {
    private final Database db;
    private final PayoutCrypto crypto;

    public DriverPayoutRepo(Database db, PayoutCrypto crypto) {
        this.db = db;
        this.crypto = crypto;
    }

    public JsonObject account(long driverId) { return db.with(c -> account(c, driverId, true)); }

    /** Administrative workflow intentionally receives the configured destination only over an authenticated route. */
    public JsonObject account(PgConnection c, long driverId, boolean reveal) {
        QueryResult r = c.query("SELECT method, account_title_enc, account_number_enc, iban_enc, phone_enc," +
                " display_last4, updated_at FROM driver_payout_accounts WHERE driver_id=$1",
                new String[]{String.valueOf(driverId)});
        JsonObject out = new JsonObject();
        out.addProperty("driverId", driverId);
        out.addProperty("configured", r.rowCount() > 0);
        if (r.rowCount() == 0) return out;
        String[] x = r.rows.get(0);
        out.addProperty("method", x[0]);
        out.addProperty("displayLast4", empty(x[5]));
        out.addProperty("updatedAt", empty(x[6]));
        if (reveal) {
            out.addProperty("accountTitle", crypto.decrypt(x[1]));
            out.addProperty("accountNumber", crypto.decrypt(x[2]));
            out.addProperty("iban", crypto.decrypt(x[3]));
            out.addProperty("phone", crypto.decrypt(x[4]));
        }
        return out;
    }

    public JsonObject saveAccount(PgConnection c, long driverId, String method, String title,
                                  String account, String iban, String phone) {
        String destination = !account.isBlank() ? account : (!iban.isBlank() ? iban : phone);
        String display = last4(destination);
        c.query("INSERT INTO driver_payout_accounts(driver_id, method, account_title_enc, account_number_enc," +
                        " iban_enc, phone_enc, display_last4) VALUES ($1,$2,$3,$4,$5,$6,$7)" +
                        " ON CONFLICT (driver_id) DO UPDATE SET method=EXCLUDED.method," +
                        " account_title_enc=EXCLUDED.account_title_enc, account_number_enc=EXCLUDED.account_number_enc," +
                        " iban_enc=EXCLUDED.iban_enc, phone_enc=EXCLUDED.phone_enc," +
                        " display_last4=EXCLUDED.display_last4, updated_at=now()",
                new String[]{String.valueOf(driverId), method, crypto.encrypt(title), crypto.encrypt(account),
                        crypto.encrypt(iban), crypto.encrypt(phone), display});
        return account(c, driverId, true);
    }

    /** Locks are supplied by the service; this is only the unique-ledger insert. */
    public JsonObject record(PgConnection c, String bookingId, int itemPos, long driverId, long amount,
                             String method, String destination, String display, String reference,
                             String note, String paidBy) {
        QueryResult r = c.query("INSERT INTO driver_payouts(booking_id, booking_item_pos, driver_id, amount," +
                        " method, account_enc, account_display, reference, note, paid_by)" +
                        " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id, created_at",
                new String[]{bookingId, String.valueOf(itemPos), String.valueOf(driverId), String.valueOf(amount),
                        method, crypto.encrypt(destination), display, reference, note == null ? "" : note, paidBy});
        JsonObject out = new JsonObject();
        out.addProperty("id", Long.parseLong(r.rows.get(0)[0]));
        out.addProperty("bookingId", bookingId);
        out.addProperty("bookingItemPos", itemPos);
        out.addProperty("driverId", driverId);
        out.addProperty("amount", amount);
        out.addProperty("method", method);
        out.addProperty("accountDisplay", display);
        out.addProperty("reference", reference);
        out.addProperty("note", note == null ? "" : note);
        out.addProperty("createdAt", empty(r.rows.get(0)[1]));
        out.addProperty("alreadyPaid", false);
        return out;
    }

    public JsonObject payout(PgConnection c, String bookingId, int itemPos) {
        QueryResult r = c.query("SELECT id, booking_id, booking_item_pos, driver_id, amount, method, account_display," +
                        " reference, note, paid_by, created_at FROM driver_payouts WHERE booking_id=$1 AND booking_item_pos=$2",
                new String[]{bookingId, String.valueOf(itemPos)});
        return r.rowCount() == 0 ? null : payoutJson(r, 0, true);
    }

    public JsonArray history(PgConnection c, long driverId) {
        QueryResult r = c.query("SELECT id, booking_id, booking_item_pos, driver_id, amount, method, account_display," +
                        " reference, note, paid_by, created_at FROM driver_payouts WHERE driver_id=$1 ORDER BY id DESC LIMIT 200",
                new String[]{String.valueOf(driverId)});
        JsonArray a = new JsonArray();
        for (int i = 0; i < r.rowCount(); i++) a.add(payoutJson(r, i, false));
        return a;
    }

    private static JsonObject payoutJson(QueryResult r, int row, boolean alreadyPaid) {
        String[] x = r.rows.get(row);
        JsonObject o = new JsonObject();
        o.addProperty("id", Long.parseLong(x[0]));
        o.addProperty("bookingId", x[1]);
        o.addProperty("bookingItemPos", Integer.parseInt(x[2]));
        o.addProperty("driverId", Long.parseLong(x[3]));
        o.addProperty("amount", Long.parseLong(x[4]));
        o.addProperty("method", empty(x[5]));
        o.addProperty("accountDisplay", empty(x[6]));
        o.addProperty("reference", empty(x[7]));
        o.addProperty("note", empty(x[8]));
        o.addProperty("paidBy", empty(x[9]));
        o.addProperty("createdAt", empty(x[10]));
        o.addProperty("alreadyPaid", alreadyPaid);
        return o;
    }

    public JsonArray history(long driverId) { return db.with(c -> history(c, driverId)); }

    private static String empty(String s) { return s == null ? "" : s; }
    private static String last4(String s) {
        String value = s == null ? "" : s.replaceAll("\\s+", "");
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}
