package com.apex.dao;

import com.apex.db.Database;
import com.apex.util.Json;
import com.google.gson.JsonObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Shared JDBC helpers for every DAO. */
public abstract class BaseDao {

    protected final Database db;

    protected BaseDao(Database db) {
        this.db = db;
    }

    /** SELECT whose first column is a JSON document. */
    protected List<JsonObject> selectJson(String sql) throws SQLException {
        List<JsonObject> out = new ArrayList<>();
        try (Statement st = db.conn().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(Json.parseObject(rs.getString(1)));
        }
        return out;
    }

    protected List<String> selectStrings(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = db.conn().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    protected void exec(String sql) throws SQLException {
        try (Statement st = db.conn().createStatement()) {
            st.execute(sql);
        }
    }
}
