package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Admin wallet (all payments land here first), per-owner payout wallets and
 * the audit ledger of wallet movements.
 */
public class WalletDao extends BaseDao {

    public WalletDao(Database db) { super(db); }

    public long adminBalance() throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("SELECT balance FROM wallet WHERE id=1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    public void setAdminBalance(long balance) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement("UPDATE wallet SET balance=? WHERE id=1")) {
            ps.setLong(1, balance);
            ps.executeUpdate();
        }
    }

    public Map<String, Long> ownerWallets() throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = db.conn().prepareStatement("SELECT owner_id, balance FROM owner_wallets");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
        }
        return out;
    }

    public void replaceOwnerWallets(Map<String, Long> wallets) throws SQLException {
        exec("DELETE FROM owner_wallets");
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO owner_wallets(owner_id, balance) VALUES (?, ?)")) {
            for (Map.Entry<String, Long> e : wallets.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setLong(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void ledger(String type, long amount, String note, String account) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO wallet_transactions(type, amount, note, account, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, type);
            ps.setLong(2, amount);
            ps.setString(3, note);
            ps.setString(4, account);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    public List<JsonObject> transactions() throws SQLException {
        List<JsonObject> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id, type, amount, note, account, created_at FROM wallet_transactions ORDER BY id DESC LIMIT 200");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject o = Json.obj();
                o.addProperty("id", rs.getInt(1));
                o.addProperty("type", rs.getString(2));
                o.addProperty("amount", rs.getLong(3));
                o.addProperty("note", rs.getString(4));
                o.addProperty("account", rs.getString(5));
                o.addProperty("created", rs.getString(6));
                out.add(o);
            }
        }
        return out;
    }
}
