package com.apex.service;

import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.model.Booking;
import com.apex.model.Car;
import com.apex.model.User;
import com.apex.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Admin dashboard stats - every number is computed from real rows.
 */
public final class StatsService {

    private final Database db;
    private final BookingRepo bookings;
    private final CarRepo cars;
    private final DriverRepo drivers;
    private final UserRepo users;
    private final PaymentRepo payments;
    private final DocumentRepo documents;
    private final ReviewRepo reviews;
    private final WalletRepo wallet;
    private final ApplicationRepo apps;
    private final ChatRepo chats;

    public StatsService(Database db, BookingRepo bookings, CarRepo cars, DriverRepo drivers,
                        UserRepo users, PaymentRepo payments, DocumentRepo documents,
                        ReviewRepo reviews, WalletRepo wallet, ApplicationRepo apps, ChatRepo chats) {
        this.db = db; this.bookings = bookings; this.cars = cars; this.drivers = drivers;
        this.users = users; this.payments = payments; this.documents = documents;
        this.reviews = reviews; this.wallet = wallet; this.apps = apps; this.chats = chats;
    }

    public JsonObject stats() {
        return db.with(c -> {
            List<Booking> orders = bookings.listAll(c);
            List<Car> fleet = cars.list(c);
            List<User> userRows = users.all(c);

            long revenue = 0, depositHeld = 0, extraCollected = 0, userCars = 0, approved = 0, pendingDrivers = 0;
            long active = 0, pending = 0, completed = 0;
            for (Booking b : orders) {
                if ("Active".equals(b.status)) {
                    active++;
                    depositHeld += b.deposit;
                } else if ("Completed".equals(b.status)) {
                    completed++;
                    revenue += (b.finalAmount != null ? b.finalAmount : b.total);
                    if (b.extraCharges != null) extraCollected += b.extraCharges;
                } else if (b.status != null && !b.status.equals("Cancelled") && !b.status.equals("Rejected")) {
                    pending++;
                }
            }
            for (Car car : fleet) {
                if (car.ownerId != null && !car.ownerId.isEmpty()) userCars++;
            }
            for (var d : drivers.list(c)) {
                String st = d.status == null ? "" : d.status;
                if (st.equalsIgnoreCase("Approved") || st.equalsIgnoreCase("Active")) approved++;
                else if (st.equalsIgnoreCase("Pending") || st.isEmpty()) pendingDrivers++;
            }

            long payPending = payments.countStatus(c, "Pending Verification");
            long payVerified = payments.countStatus(c, "Verified");

            JsonObject s = new JsonObject();
            s.addProperty("revenue", revenue);
            s.addProperty("walletBalance", wallet.balance(c));
            s.addProperty("payPending", payPending);
            s.addProperty("payPendingAmount", payments.sumStatus(c, "Pending Verification"));
            s.addProperty("payVerified", payVerified);
            s.addProperty("payVerifiedAmount", payments.sumStatus(c, "Verified"));
            s.addProperty("bookings", orders.size());
            s.addProperty("rentedNow", active);
            s.addProperty("pending", pending);
            s.addProperty("completed", completed);
            s.addProperty("depositHeld", depositHeld);
            s.addProperty("extraCollected", extraCollected);
            s.addProperty("cars", fleet.size());
            s.addProperty("users", userRows.size());
            s.addProperty("userListedCars", userCars);
            s.addProperty("driversApproved", approved);
            s.addProperty("driversPending", pendingDrivers);
            s.addProperty("docsPending", documents.countPending(c));
            s.addProperty("reviewsPending", reviews.countPending(c));
            s.addProperty("unreadMessages", chats.unreadTotal(c));
            s.addProperty("withdrawPending", wallet.pendingWithdrawals(c).getOrDefault("count", 0L));

            JsonObject o = new JsonObject();
            o.add("stats", s);
            return o;
        });
    }
}
