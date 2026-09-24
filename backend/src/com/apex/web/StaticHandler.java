package com.apex.web;

import com.apex.log.Logger;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Serves the frontend directory with path-traversal protection. */
public final class StaticHandler {

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put(".html", "text/html; charset=utf-8");
        MIME.put(".css", "text/css; charset=utf-8");
        MIME.put(".js", "application/javascript; charset=utf-8");
        MIME.put(".json", "application/json; charset=utf-8");
        MIME.put(".jpg", "image/jpeg");
        MIME.put(".jpeg", "image/jpeg");
        MIME.put(".png", "image/png");
        MIME.put(".gif", "image/gif");
        MIME.put(".webp", "image/webp");
        MIME.put(".svg", "image/svg+xml");
        MIME.put(".ico", "image/x-icon");
        MIME.put(".woff", "font/woff");
        MIME.put(".woff2", "font/woff2");
        MIME.put(".txt", "text/plain; charset=utf-8");
    }

    private final Path root;

    public StaticHandler(String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            Logger.warn("Static dir not found: {} (API will still work)", root);
        }
    }

    public void serve(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!"/".equals(path)) {
            path = path.replace("..", "");
        }
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root) || Files.isDirectory(file)) {
            file = root.resolve("index.html");
        }
        if (!Files.isRegularFile(file)) {
            HttpUtil.sendError(ex, 404, "Not found");
            return;
        }
        String ext = extOf(file.getFileName().toString()).toLowerCase();
        String mime = MIME.getOrDefault(ext, "application/octet-stream");
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i);
    }
}
