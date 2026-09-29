package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.security.PayoutCrypto;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Owner payout accounts and owner-facing earnings/read models. */
public final class OwnerRepo {
    private final Database db;
    private final PayoutCrypto crypto;

    public OwnerRepo(Database db, PayoutCrypto crypto) {
        this.db = db;
        this.crypto = crypto;
    }

    public JsonObject payoutAccount(String ownerId) {
        return db.with(c -> payoutAccount(c, ownerId));
    }

    public JsonObject payoutAccount(PgConnection c, String ownerId) {
        QueryResult r = c.query("SELECT method, account_title_enc, account_number_enc, iban_enc, phone_enc," +
                " display_last4, updated_at FROM owner_payout_accounts WHERE owner_id = $1",
                new String[]{ownerId});
        JsonObject out = new JsonObject();
        out.addProperty("configured", r.rowCount() > 0);
        if (r.rowCount() == 0) return out;
        String[] x = r.rows.get(0);
        out.addProperty("method", value(x[0]));
        out.addProperty("accountTitle", crypto.decrypt(x[1]));
        out.addProperty("accountNumber", crypto.decrypt(x[2]));
        out.addProperty("iban", crypto.decrypt(x[3]));
        out.addProperty("phone", crypto.decrypt(x[4]));
        out.addProperty("displayLast4", value(x[5]));
        out.addProperty("updatedAt", value(x[6]));
        return out;
    }

    public JsonObject savePayoutAccount(PgConnection c, String ownerId, String method,
                                        String accountTitle, String accountNumber,
                                        String iban, String phone) {
        String display = !iban.isEmpty() ? iban : !accountNumber.isEmpty() ? accountNumber : phone;
        String last4 = display.length() <= 4 ? display : display.substring(display.length() - 4);
        c.query("INSERT INTO owner_payout_accounts(owner_id, method, account_title_enc," +
                " account_number_enc, iban_enc, phone_enc, display_last4)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7)" +
                " ON CONFLICT (owner_id) DO UPDATE SET method=EXCLUDED.method," +
                " account_title_enc=EXCLUDED.account_title_enc," +
                " account_number_enc=EXCLUDED.account_number_enc, iban_enc=EXCLUDED.iban_enc," +
                " phone_enc=EXCLUDED.phone_enc, display_last4=EXCLUDED.display_last4, updated_at=now()",
                new String[]{ownerId, method, crypto.encrypt(accountTitle), crypto.encrypt(accountNumber),
                        crypto.encrypt(iban), crypto.encrypt(phone), last4});
        return payoutAccount(c, ownerId);
    }

    public JsonObject earnings(String ownerId) {
        return db.with(c -> {
            JsonObject out = new JsonObject();
            QueryResult balance = c.query("SELECT COALESCE(balance, 0) FROM owner_wallets WHERE owner_id = $1",
                    new String[]{ownerId});
            out.addProperty("availableBalance", balance.rowCount() == 0 ? 0 : parse(balance.first()));
            QueryResult total = c.query("SELECT COALESCE(sum(owner_share), 0) FROM owner_payout_ledger" +
                    " WHERE owner_id = $1 AND status = 'Completed'", new String[]{ownerId});
            out.addProperty("totalEarnings", parse(total.first()));

            QueryResult ledger = c.query("SELECT l.id, l.booking_id, l.car_id, COALESCE(ca.name, l.car_name, '')," +
                    " l.gross_rental, l.company_share, l.owner_share, l.status, l.note," +
                    " l.created_at, l.settled_at FROM owner_payout_ledger l" +
                    " LEFT JOIN cars ca ON ca.id = l.car_id WHERE l.owner_id = $1 ORDER BY l.id DESC LIMIT 200",
                    new String[]{ownerId});
            JsonArray payouts = new JsonArray();
            for (int i = 0; i < ledger.rowCount(); i++) {
                String[] x = ledger.rows.get(i);
                JsonObject row = new JsonObject();
                row.addProperty("id", parse(x[0]));
                row.addProperty("bookingId", value(x[1]));
                row.addProperty("carId", parse(x[2]));
                row.addProperty("carName", value(x[3]));
                row.addProperty("grossRental", parse(x[4]));
                row.addProperty("companyShare", parse(x[5]));
                row.addProperty("ownerShare", parse(x[6]));
                row.addProperty("status", value(x[7]));
                row.addProperty("note", value(x[8]));
                row.addProperty("createdAt", value(x[9]));
                row.addProperty("settledAt", value(x[10]));
                payouts.add(row);
            }
            out.add("payoutHistory", payouts);

            QueryResult rentals = c.query("SELECT DISTINCT b.id, i.car_id, ca.name, b.status, b.payment_status," +
                    " COALESCE((i.price->>'rental')::bigint, 0), b.created_at" +
                    " FROM booking_items i JOIN bookings b ON b.id = i.booking_id" +
                    " JOIN cars ca ON ca.id = i.car_id WHERE ca.owner_id = $1" +
                    " ORDER BY b.created_at DESC LIMIT 200", new String[]{ownerId});
            JsonArray rentalRows = new JsonArray();
            for (int i = 0; i < rentals.rowCount(); i++) {
                String[] x = rentals.rows.get(i);
                long gross = parse(x[5]);
                JsonObject row = new JsonObject();
                row.addProperty("bookingId", value(x[0]));
                row.addProperty("carId", parse(x[1]));
                row.addProperty("carName", value(x[2]));
                row.addProperty("rentalStatus", value(x[3]));
                row.addProperty("paymentStatus", value(x[4]));
                row.addProperty("expectedEarning", Math.round(gross * 0.90));
                row.addProperty("createdAt", value(x[6]));
                rentalRows.add(row);
            }
            out.add("rentals", rentalRows);
            return out;
        });
    }

    private static long parse(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String value(String s) { return s == null ? "" : s; }
}
