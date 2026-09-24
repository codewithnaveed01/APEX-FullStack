package com.apex.service;

import com.apex.dao.DocumentRepo;
import com.apex.db.Database;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;

/**
 * Sensitive uploads (CNIC front/back, licence, receipts, photos).
 * - image types only, validated by magic bytes (not just the header)
 * - hard 2.5 MB cap
 * - stored in Postgres (BYTEA) and mirrored to disk
 * - readable by the owner or an admin only; no public URLs
 */
public final class UploadService {

    public static final long MAX_BYTES = 2_500_000L;
    public static final int MAX_B64_CHARS = 3_500_000;
    private static final Set<String> KINDS = Set.of("cnic_front", "cnic_back", "license", "receipt", "photo", "doc");
    private static final Set<String> OWNER_TYPES = Set.of("user", "driver", "booking");

    private final Database db;
    private final DocumentRepo docs;
    private final Path uploadsDir;

    public UploadService(Database db, DocumentRepo docs, String uploadsDir) {
        this.db = db;
        this.docs = docs;
        this.uploadsDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create uploads dir: " + e.getMessage(), e);
        }
    }

    public JsonObject upload(JsonObject session, JsonObject body) {
        boolean isAdmin = "admin".equals(Json.getStr(session, "role", ""));
        String kind = Json.clean(Json.getStr(body, "kind", ""));
        if (!KINDS.contains(kind)) throw ApiException.validation("Invalid document kind");
        String ownerType = Json.clean(Json.getStr(body, "ownerType", "user"));
        if (!OWNER_TYPES.contains(ownerType)) throw ApiException.validation("Invalid ownerType");
        String ownerId = Json.clean(Json.getStr(body, "ownerId", ""));
        if (ownerId.isEmpty()) {
            if (isAdmin) throw ApiException.validation("Missing ownerId");
            ownerId = Json.getStr(session, "userId", "");
        }
        if (ownerType.equals("user") && !isAdmin && !ownerId.equals(Json.getStr(session, "userId", ""))) {
            ownerId = Json.getStr(session, "userId", ""); // never trust the client
        }
        if (ownerId.isEmpty()) throw ApiException.unauth("Cannot determine document owner");

        String dataUrl = Json.getStr(body, "dataUrl", "");
        if (!dataUrl.startsWith("data:image/")) {
            throw ApiException.validation("Only image uploads are allowed");
        }
        if (dataUrl.length() > MAX_B64_CHARS) {
            throw ApiException.validation("File too large (max 2.5 MB)");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) throw ApiException.validation("Malformed data URL");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("Malformed image data");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw ApiException.validation("File too large (max 2.5 MB)");
        }
        String[] detected = detect(bytes);
        if (detected == null) {
            throw ApiException.validation("Unsupported or corrupt image (only JPEG/PNG/WebP/GIF)");
        }
        String ext = detected[0];
        String contentType = detected[1];
        String fname = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // mirror to disk (best effort; Postgres is the source of truth)
        try {
            Files.write(uploadsDir.resolve(fname), bytes);
        } catch (IOException e) {
            // disk mirror is optional - log and continue
            System.out.println("[APEX] upload disk mirror failed: " + e.getMessage());
        }

        final String fName = fname;
        final byte[] fBytes = bytes;
        final String fOwnerId = ownerId;
        return db.tx(c -> {
            long id = docs.insert(c, ownerType, fOwnerId, kind, fName, contentType, fBytes);
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("kind", kind);
            o.addProperty("name", fName);
            o.addProperty("bytes", bytes.length);
            o.addProperty("status", "Pending");
            return o;
        });
    }

    public JsonObject view(JsonObject session, long id) {
        boolean isAdmin = "admin".equals(Json.getStr(session, "role", ""));
        String userId = Json.getStr(session, "userId", "");
        return db.with(c -> {
            JsonObject doc = docs.get(c, id);
            if (doc == null) throw ApiException.notFound("Document not found");
            if (!isAdmin && !doc.get("ownerId").getAsString().equals(userId)) {
                throw ApiException.forbidden("Access to this document is restricted");
            }
            byte[] content = docs.content(c, id);
            if (content == null) throw ApiException.notFound("Document content missing");
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("kind", doc.get("kind").getAsString());
            o.addProperty("contentType", doc.get("contentType").getAsString());
            o.addProperty("status", doc.get("status").getAsString());
            o.addProperty("dataUrl", "data:" + doc.get("contentType").getAsString() + ";base64," +
                    Base64.getEncoder().encodeToString(content));
            o.addProperty("created", doc.get("created").getAsString());
            return o;
        });
    }

    public void review(JsonObject adminSession, long id, JsonObject body) {
        String status = Json.clean(Json.getStr(body, "status", ""));
        if (!status.equals("Verified") && !status.equals("Rejected")) {
            throw ApiException.validation("status must be Verified or Rejected");
        }
        db.tx(c -> {
            JsonObject doc = docs.get(c, id);
            if (doc == null) throw ApiException.notFound("Document not found");
            docs.updateStatus(c, id, status);
            return null;
        });
    }

    /** Magic-byte sniffing: returns {ext, contentType} or null. */
    static String[] detect(byte[] b) {
        if (b.length < 12) return null;
        // JPEG FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new String[]{"jpg", "image/jpeg"};
        }
        // PNG 89 50 4E 47 0D 0A 1A 0A
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return new String[]{"png", "image/png"};
        }
        // GIF "GIF8"
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return new String[]{"gif", "image/gif"};
        }
        // WebP: RIFF....WEBP
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' &&
                b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new String[]{"webp", "image/webp"};
        }
        return null;
    }
}
