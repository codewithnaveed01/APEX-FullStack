package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Car;
import com.apex.util.Json;

import java.util.List;

public final class CarRepo {

    private final Database db;
    private static final String COLS =
            "id, name, brand, category, origin, image, year, rate, hourly_rate, deposit, seats," +
            " engine, power, fuel, km, color, plate, car_condition, status, market_note, owner_id, data";

    public CarRepo(Database db) { this.db = db; }

    public List<Car> list() {
        return db.with(c -> list(c));
    }

    public List<Car> list(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars ORDER BY id", null);
        java.util.ArrayList<Car> out = new java.util.ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(Car.fromRow(r, i));
        return out;
    }

    public Car get(PgConnection c, long id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars WHERE id = $1", new String[]{String.valueOf(id)});
        return r.rowCount() > 0 ? Car.fromRow(r, 0) : null;
    }

    public Car get(long id) {
        return db.with(c -> get(c, id));
    }

    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM cars", null));
        return Long.parseLong(r.first());
    }

    public void insert(PgConnection c, Car car) {
        upsert(c, car);
    }

    public void upsert(PgConnection c, Car car) {
        c.query("INSERT INTO cars(id, name, brand, category, origin, image, year, rate, hourly_rate," +
                " deposit, seats, engine, power, fuel, km, color, plate, car_condition, status," +
                " market_note, owner_id, data)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22)" +
                " ON CONFLICT (id) DO UPDATE SET" +
                " name=EXCLUDED.name, brand=EXCLUDED.brand, category=EXCLUDED.category, origin=EXCLUDED.origin," +
                " image=EXCLUDED.image, year=EXCLUDED.year, rate=EXCLUDED.rate, hourly_rate=EXCLUDED.hourly_rate," +
                " deposit=EXCLUDED.deposit, seats=EXCLUDED.seats, engine=EXCLUDED.engine, power=EXCLUDED.power," +
                " fuel=EXCLUDED.fuel, km=EXCLUDED.km, color=EXCLUDED.color, plate=EXCLUDED.plate," +
                " car_condition=EXCLUDED.car_condition, status=EXCLUDED.status, market_note=EXCLUDED.market_note," +
                " owner_id=EXCLUDED.owner_id, data=EXCLUDED.data, updated_at=now()",
                new String[]{String.valueOf(car.id), nullIfEmpty(car.name), nullIfEmpty(car.brand),
                        nullIfEmpty(car.category), nullIfEmpty(car.origin), nullIfEmpty(car.image),
                        String.valueOf(car.year), String.valueOf(car.rate), String.valueOf(car.hourlyRate),
                        String.valueOf(car.deposit), String.valueOf(car.seats), nullIfEmpty(car.engine),
                        nullIfEmpty(car.power), nullIfEmpty(car.fuel), nullIfEmpty(car.km),
                        nullIfEmpty(car.color), nullIfEmpty(car.plate), nullIfEmpty(car.carCondition),
                        nullIfEmpty(car.status), nullIfEmpty(car.marketNote), nullIfEmpty(car.ownerId),
                        car.data.toString()});
    }

    /** Full catalog replace (admin sync is the source of truth for the fleet). */
    public void replaceAll(PgConnection c, List<Car> cars) {
        c.simpleQuery("DELETE FROM cars");
        for (Car car : cars) upsert(c, car);
    }

    public void delete(PgConnection c, long id) {
        c.query("DELETE FROM cars WHERE id = $1", new String[]{String.valueOf(id)});
    }

    /** Pricing inputs for one car (server-side quoting only). */
    public static final class Pricing {
        public final long rate, hourlyRate, deposit;
        public final String ownerId, name, status;
        Pricing(long rate, long hourly, long deposit, String ownerId, String name, String status) {
            this.rate = rate; this.hourlyRate = hourly; this.deposit = deposit;
            this.ownerId = ownerId; this.name = name; this.status = status;
        }
    }

    public Pricing pricing(PgConnection c, long id) {
        QueryResult r = c.query("SELECT rate, hourly_rate, deposit, owner_id, name, status FROM cars WHERE id = $1",
                new String[]{String.valueOf(id)});
        if (r.rowCount() == 0) return null;
        String[] row = r.rows.get(0);
        return new Pricing(parse(row[0]), parse(row[1]), parse(row[2]),
                row[3] == null ? "" : row[3], row[4] == null ? "" : row[4], row[5] == null ? "" : row[5]);
    }

    private static long parse(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
