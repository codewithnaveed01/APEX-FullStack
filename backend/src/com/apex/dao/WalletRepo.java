package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin wallet + per-owner wallets + an immutable ledger.
 * Every balance change writes a wallet_transactions row in the same tx.
 */
public final class WalletRepo {

    private final Database db;

    public WalletRepo(Database db) { this.db = db; }

    public long adminBalance() {
        return db.with(c -> balance(c));
    }

    public long balance(PgConnection c) {
        QueryResult r = c.query("SELECT balance FROM wallet WHERE id = 1", null);
        return r.rowCount() > 0 && r.rows.get(0)[0] != null ? Long.parseLong(r.rows.get(0)[0]) : 0;
    }

    /** Delta can be negative (payouts to owners). */
    public void addAdmin(PgConnection c, long delta) {
        c.query("UPDATE wallet SET balance = balance + $1 WHERE id = 1", new String[]{String.valueOf(delta)});
    }

    public long addOwner(PgConnection c, String ownerId, long delta) {
        QueryResult r = c.query("INSERT INTO owner_wallets(owner_id, balance) VALUES ($1, $2)" +
                " ON CONFLICT (owner_id) DO UPDATE SET balance = owner_wallets.balance + $2" +
                " RETURNING balance",
                new String[]{ownerId, String.valueOf(delta)});
        return Long.parseLong(r.first());
    }

    public Map<String, Long> ownerWallets(PgConnection c) {
        Map<String, Long> m = new HashMap<>();
        QueryResult r = c.query("SELECT owner_id, balance FROM owner_wallets ORDER BY owner_id", null);
        for (int i = 0; i < r.rowCount(); i++) m.put(r.rows.get(i)[0], Long.parseLong(r.rows.get(i)[1]));
        return m;
    }

    public void ledger(PgConnection c, String type, long amount, String account, String note) {
        c.query("INSERT INTO wallet_transactions(type, amount, account, note, status)" +
                " VALUES ($1,$2,$3,$4,'Paid')",
                new String[]{type, String.valueOf(amount), account == null ? "" : account,
                        note == null ? "" : note});
    }

    public JsonArray transactions(PgConnection c, int limit) {
        JsonArray out = new JsonArray();
        QueryResult r = c.query("SELECT id, type, amount, account, note, status, created_at" +
                " FROM wallet_transactions ORDER BY id DESC LIMIT " + Math.max(1, Math.min(limit, 500)), null);
        for (int i = 0; i < r.rowCount(); i++) {
            String[] x = r.rows.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("id", Long.parseLong(x[0]));
            o.addProperty("type", x[1]);
            o.addProperty("amount", Long.parseLong(x[2]));
            o.addProperty("account", x[3] == null ? "" : x[3]);
            o.addProperty("note", x[4] == null ? "" : x[4]);
            o.addProperty("status", x[5] == null ? "Paid" : x[5]);
            o.addProperty("time", x[6] == null ? "" : x[6]);
            out.add(o);
        }
        return out;
    }

    public Map<String, Long> pendingWithdrawals(PgConnection c) {
        Map<String, Long> m = new HashMap<>();
        QueryResult r = c.query("SELECT count(*), COALESCE(sum(amount), 0) FROM wallet_transactions" +
                " WHERE type = 'withdrawal' AND status = 'Pending'", null);
        if (r.rowCount() > 0) {
            m.put("count", Long.parseLong(r.rows.get(0)[0]));
            m.put("amount", Long.parseLong(r.rows.get(0)[1]));
        }
        return m;
    }
}
