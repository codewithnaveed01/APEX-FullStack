package com.apex;

import com.apex.config.AppConfig;
import com.apex.dao.ApplicationDao;
import com.apex.dao.BanDao;
import com.apex.dao.BookingDao;
import com.apex.dao.CarDao;
import com.apex.dao.ChatDao;
import com.apex.dao.DocumentDao;
import com.apex.dao.DriverDao;
import com.apex.dao.NotificationDao;
import com.apex.dao.PaymentDao;
import com.apex.dao.ReviewDao;
import com.apex.dao.SessionDao;
import com.apex.dao.SettingsDao;
import com.apex.dao.UserDao;
import com.apex.dao.WalletDao;
import com.apex.db.Database;
import com.apex.service.AuthService;
import com.apex.service.StateService;
import com.apex.service.StatsService;
import com.apex.service.WalletService;
import com.apex.web.ApiRoutes;
import com.apex.web.Router;
import com.apex.web.StaticHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * APEX - Driven Beyond Ordinary.
 * Full-stack entry point: SQLite database + REST API + static frontend,
 * all served from one JDK-only process (plus gson & sqlite-jdbc jars).
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AppConfig cfg = new AppConfig();

        Database db = new Database(cfg);
        System.out.println("[APEX] Database engine: " + db.engine());

        CarDao carDao = new CarDao(db);
        DriverDao driverDao = new DriverDao(db);
        UserDao userDao = new UserDao(db);
        BookingDao bookingDao = new BookingDao(db);
        ApplicationDao applicationDao = new ApplicationDao(db);
        NotificationDao notificationDao = new NotificationDao(db);
        ChatDao chatDao = new ChatDao(db);
        BanDao banDao = new BanDao(db);
        WalletDao walletDao = new WalletDao(db);
        SettingsDao settingsDao = new SettingsDao(db);
        SessionDao sessionDao = new SessionDao(db);
        PaymentDao paymentDao = new PaymentDao(db);
        DocumentDao documentDao = new DocumentDao(db);
        ReviewDao reviewDao = new ReviewDao(db);

        StateService state = new StateService(db, carDao, driverDao, userDao, bookingDao, applicationDao,
                notificationDao, chatDao, banDao, walletDao, settingsDao);
        state.seedIfEmpty(cfg.getSeedFile());

        AuthService auth = new AuthService(db, sessionDao, userDao, cfg);
        WalletService wallet = new WalletService(db, walletDao);
        StatsService stats = new StatsService(db, carDao, bookingDao, userDao, walletDao, driverDao,
                applicationDao, settingsDao, chatDao, notificationDao, paymentDao, documentDao, reviewDao);

        Router router = new Router(auth);
        ApiRoutes.register(router, state, auth, wallet, stats, carDao, bookingDao, paymentDao,
                documentDao, reviewDao, notificationDao, cfg);

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", cfg.getPort()), 0);
        final Database dbLock = db;
        server.createContext("/api/", ex -> {
            // One shared JDBC connection: serialize ALL database work here so two
            // requests can never interleave statements or transactions on it.
            synchronized (dbLock) {
                try {
                    if (!router.handle(ex)) {
                        com.apex.web.HttpUtil.sendError(ex, 404, "No such API route");
                    }
                } catch (java.io.IOException ignored) { }
            }
        });
        server.createContext("/", new StaticHandler(cfg.getStaticDir()));
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("=====================================================");
        System.out.println(" APEX full-stack server ready");
        System.out.println("   URL     : http://localhost:" + cfg.getPort() + "/");
        System.out.println("   Admin   : http://localhost:" + cfg.getPort() + "/admin.html");
        System.out.println("   API     : http://localhost:" + cfg.getPort() + "/api/health");
        String jdbc = cfg.getJdbcUrl();
        if (jdbc != null) {
            System.out.println("   Database: MySQL " + jdbc.replaceAll("password=[^&]*", "password=***"));
        } else {
            System.out.println("   DB file : " + cfg.getDbFile());
        }
        System.out.println("   Static  : " + cfg.getStaticDir());
        System.out.println("=====================================================");
    }
}
