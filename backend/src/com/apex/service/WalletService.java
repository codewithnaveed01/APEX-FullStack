package com.apex.service;

import com.apex.dao.WalletDao;
import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Admin wallet operations with an audit ledger. */
public class WalletService {

    private final Database db;
    private final WalletDao wallet;

    public WalletService(Database db, WalletDao wallet) {
        this.db = db;
        this.wallet = wallet;
    }

    public JsonObject info() throws SQLException {
        return db.with(c -> {
            JsonObject out = Json.obj();
            out.addProperty("balance", wallet.adminBalance());
            JsonObject ow = Json.obj();
            for (Map.Entry<String, Long> e : wallet.ownerWallets().entrySet()) ow.addProperty(e.getKey(), e.getValue());
            out.add("ownerWallets", ow);
            out.add("transactions", ledgerArray(wallet.transactions()));
            return out;
        });
    }

    private static JsonArray ledgerArray(List<JsonObject> rows) {
        JsonArray a = Json.arr();
        rows.forEach(a::add);
        return a;
    }

    public JsonObject addCash(long amount, String note) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        return db.tx(c -> {
            wallet.setAdminBalance(wallet.adminBalance() + amount);
            wallet.ledger("add-cash", amount, note == null ? "Cash added manually" : note, null);
            return info0();
        });
    }

    /** Withdrawal requires a destination account (bank / JazzCash / easypaisa). */
    public JsonObject withdraw(long amount, String account, String note) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (account == null || account.isBlank()) throw new IllegalArgumentException("Destination account is required for withdrawal");
        return db.tx(c -> {
            long bal = wallet.adminBalance();
            if (amount > bal) throw new IllegalArgumentException("Insufficient wallet balance");
            wallet.setAdminBalance(bal - amount);
            wallet.ledger("withdraw", -amount, note == null ? "Withdrawal" : note, account);
            return info0();
        });
    }

    /** Customer payment intake - lands in the admin wallet with a ledger entry. */
    public JsonObject record(long amount, String type, String note) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        return db.tx(c -> {
            wallet.setAdminBalance(wallet.adminBalance() + amount);
            wallet.ledger(type == null || type.isBlank() ? "payment" : type, amount, note, null);
            return info0();
        });
    }

    private JsonObject info0() throws SQLException {
        JsonObject out = Json.obj();
        out.addProperty("balance", wallet.adminBalance());
        out.add("transactions", ledgerArray(wallet.transactions()));
        return out;
    }
}
