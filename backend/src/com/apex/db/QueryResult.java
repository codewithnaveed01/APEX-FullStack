package com.apex.db;

import java.util.ArrayList;
import java.util.List;

/** Result of a parameterized query: column names + text rows + command tag. */
public final class QueryResult {
    public List<String> columns = new ArrayList<>();
    public List<String[]> rows = new ArrayList<>();
    public String tag = "";

    public int rowCount() { return rows.size(); }

    /** First column of first row, or null. Use for RETURNING id. */
    public String first() {
        if (rows.isEmpty() || rows.get(0).length == 0) return null;
        return rows.get(0)[0];
    }

    public String col(String name, int rowIdx) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equals(name) && rowIdx < rows.size() && i < rows.get(rowIdx).length) {
                return rows.get(rowIdx)[i];
            }
        }
        return null;
    }
}
