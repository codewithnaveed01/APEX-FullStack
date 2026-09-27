package com.apex.service;

import com.apex.dao.NotificationRepo;
import com.apex.db.PgConnection;
import com.apex.util.Json;

import java.security.SecureRandom;

/** Builds + inserts notifications inside the caller's transaction. */
public final class NotificationService {

    private final NotificationRepo repo;
    private final SecureRandom random = new SecureRandom();

    public NotificationService(NotificationRepo repo) {
        this.repo = repo;
    }

    public void notify(PgConnection c, String userId, String title, String msg, String link, String adminTab) {
        String id = "N" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                + Integer.toString(random.nextInt(1296), 36).toUpperCase();
        repo.insert(c, id, userId, title, msg, link, adminTab);
    }

    /** Admin alerts must not appear in a customer's notification feed. */
    public void notifyAdmins(PgConnection c, String title, String msg, String link, String adminTab) {
        notify(c, "admin", title, msg, link, adminTab);
    }
}
