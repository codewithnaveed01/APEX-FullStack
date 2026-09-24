package com.apex;

import com.apex.config.AppConfig;
import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.service.*;

/** Application context: wires config, database, repositories and services. */
public final class App {

    public final AppConfig cfg;
    public final Database db;

    public final UserRepo users;
    public final SessionRepo sessions;
    public final CarRepo cars;
    public final DriverRepo drivers;
    public final BookingRepo bookings;
    public final ApplicationRepo apps;
    public final NotificationRepo notifs;
    public final ChatRepo chats;
    public final BanRepo bans;
    public final WalletRepo wallet;
    public final SettingsRepo settings;
    public final PaymentRepo payments;
    public final DocumentRepo documents;
    public final ReviewRepo reviews;

    public final AuthService auth;
    public final NotificationService notifications;
    public final BookingService bookingService;
    public final PaymentService paymentService;
    public final UploadService uploadService;
    public final StatsService statsService;
    public final StateService stateService;
    public final SeedService seedService;

    public App(AppConfig cfg, Database db) {
        this.cfg = cfg;
        this.db = db;

        users = new UserRepo(db);
        sessions = new SessionRepo(db);
        cars = new CarRepo(db);
        drivers = new DriverRepo(db);
        bookings = new BookingRepo(db);
        apps = new ApplicationRepo(db);
        notifs = new NotificationRepo(db);
        chats = new ChatRepo(db);
        bans = new BanRepo(db);
        wallet = new WalletRepo(db);
        settings = new SettingsRepo(db);
        payments = new PaymentRepo(db);
        documents = new DocumentRepo(db);
        reviews = new ReviewRepo(db);

        auth = new AuthService(db, users, sessions, cfg.sessionHours);
        notifications = new NotificationService(notifs);
        bookingService = new BookingService(db, cars, drivers, bookings, bans, documents, settings, wallet, notifications);
        paymentService = new PaymentService(db, bookings, payments, documents, wallet, notifications);
        uploadService = new UploadService(db, documents, cfg.uploadsDir);
        statsService = new StatsService(db, bookings, cars, drivers, users, payments, documents, reviews, wallet, apps, chats);
        stateService = new StateService(db, cars, drivers, users, bookings, apps, notifs, chats, bans, settings, wallet);
        seedService = new SeedService(db, users, cars, drivers, settings, cfg);
    }

    public void close() {
        db.close();
    }
}
