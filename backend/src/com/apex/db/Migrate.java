package com.apex.db;

import com.apex.log.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Startup migrator: applies every .sql file in the migrations directory
 * in filename order, tracking applied files in schema_migrations.
 * Statements are split on ';' outside quotes and comments.
 */
public final class Migrate {

    public static void run(Database db, Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Migrations directory not found: " + dir.toAbsolutePath());
        }
        db.tx(c -> {
            c.simpleQuery("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "name TEXT PRIMARY KEY," +
                    "applied_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            return null;
        });

        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".sql"))
             .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
             .forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list migrations: " + e.getMessage(), e);
        }
        if (files.isEmpty()) {
            Logger.warn("No .sql migrations found in {}", dir);
            return;
        }
        for (Path f : files) {
            String name = f.getFileName().toString();
            QueryResult done = db.with(c ->
                    c.query("SELECT 1 FROM schema_migrations WHERE name = $1", new String[]{name}));
            if (done.rowCount() > 0) continue;
            String sql;
            try {
                sql = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read migration " + name + ": " + e.getMessage(), e);
            }
            List<String> stmts = split(sql);
            Logger.info("Applying migration {}", name);
            db.tx(c -> {
                for (String s : stmts) {
                    String t = s.trim();
                    if (!t.isEmpty()) c.simpleQuery(t);
                }
                c.query("INSERT INTO schema_migrations(name) VALUES ($1)", new String[]{name});
                return null;
            });
            Logger.info("Applied migration {} ({} statements)", name, stmts.size());
        }
    }

    /** Split SQL on ';' - safe for single-quoted strings and -- comments. */
    static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        boolean inComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inComment) {
                if (ch == '\n') { inComment = false; cur.append(ch); }
                continue;
            }
            if (inQuote) {
                cur.append(ch);
                if (ch == '\'') inQuote = false;
                continue;
            }
            if (ch == '\'') { inQuote = true; cur.append(ch); continue; }
            if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inComment = true;
                continue;
            }
            if (ch == ';') {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        if (cur.toString().trim().length() > 0) out.add(cur.toString());
        return out;
    }
}
