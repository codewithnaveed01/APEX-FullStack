package com.apex.dao;

import com.apex.db.Database;
import com.apex.db.PgConnection;
import com.apex.db.QueryResult;
import com.apex.model.Car;

import java.util.ArrayList;
import java.util.List;

public final class CarRepo {
    private final Database db;
    private static final String COLS =
            "id, name, brand, category, origin, image, year, rate, hourly_rate, deposit, seats," +
            " engine, power, fuel, km, color, plate, car_condition, status, market_note, owner_id, data," +
            " application_id, main_image_id, interior_image_id, detail_image_id";

    public CarRepo(Database db) { this.db = db; }
    public List<Car> list() { return db.with(this::list); }
    public List<Car> list(PgConnection c) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars ORDER BY id", null);
        return rows(r);
    }
    public List<Car> listByOwner(PgConnection c, String ownerId) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars WHERE owner_id = $1 ORDER BY created_at DESC",
                new String[]{ownerId});
        return rows(r);
    }
    public Car get(PgConnection c, long id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars WHERE id = $1", new String[]{String.valueOf(id)});
        return r.rowCount() > 0 ? Car.fromRow(r, 0) : null;
    }
    public Car getForUpdate(PgConnection c, long id) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars WHERE id = $1 FOR UPDATE",
                new String[]{String.valueOf(id)});
        return r.rowCount() > 0 ? Car.fromRow(r, 0) : null;
    }
    public Car getByApplication(PgConnection c, String applicationId) {
        QueryResult r = c.query("SELECT " + COLS + " FROM cars WHERE application_id = $1",
                new String[]{applicationId});
        return r.rowCount() > 0 ? Car.fromRow(r, 0) : null;
    }
    public Car get(long id) { return db.with(c -> get(c, id)); }
    public long count() {
        QueryResult r = db.with(c -> c.query("SELECT count(*) FROM cars", null));
        return Long.parseLong(r.first());
    }
    public void insert(PgConnection c, Car car) { upsert(c, car); }

    public void upsert(PgConnection c, Car car) {
        c.query("INSERT INTO cars(id, name, brand, category, origin, image, year, rate, hourly_rate," +
                " deposit, seats, engine, power, fuel, km, color, plate, car_condition, status," +
                " market_note, owner_id, data, application_id, main_image_id, interior_image_id, detail_image_id)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22," +
                " $23,$24,$25,$26) ON CONFLICT (id) DO UPDATE SET" +
                " name=EXCLUDED.name, brand=EXCLUDED.brand, category=EXCLUDED.category, origin=EXCLUDED.origin," +
                " image=EXCLUDED.image, year=EXCLUDED.year, rate=EXCLUDED.rate, hourly_rate=EXCLUDED.hourly_rate," +
                " deposit=EXCLUDED.deposit, seats=EXCLUDED.seats, engine=EXCLUDED.engine, power=EXCLUDED.power," +
                " fuel=EXCLUDED.fuel, km=EXCLUDED.km, color=EXCLUDED.color, plate=EXCLUDED.plate," +
                " car_condition=EXCLUDED.car_condition, status=EXCLUDED.status, market_note=EXCLUDED.market_note," +
                " owner_id=EXCLUDED.owner_id, data=EXCLUDED.data, application_id=EXCLUDED.application_id," +
                " main_image_id=EXCLUDED.main_image_id, interior_image_id=EXCLUDED.interior_image_id," +
                " detail_image_id=EXCLUDED.detail_image_id, updated_at=now()",
                new String[]{String.valueOf(car.id), nil(car.name), nil(car.brand), nil(car.category),
                        nil(car.origin), nil(car.image), String.valueOf(car.year), String.valueOf(car.rate),
                        String.valueOf(car.hourlyRate), String.valueOf(car.deposit), String.valueOf(car.seats),
                        nil(car.engine), nil(car.power), nil(car.fuel), nil(car.km), nil(car.color), nil(car.plate),
                        nil(car.carCondition), nil(car.status), nil(car.marketNote), nil(car.ownerId),
                        car.data.toString(), nil(car.applicationId), idOrNull(car.mainImageId),
                        idOrNull(car.interiorImageId), idOrNull(car.detailImageId)});
    }

    /**
     * Admin browser sync remains compatible for regular fleet rows. Partner
     * rows are preserved and tombstoned applications can never be resurrected
     * by a stale browser after an owner deletion.
     */
    public void replaceAll(PgConnection c, List<Car> incoming) {
        c.simpleQuery("DELETE FROM cars WHERE application_id IS NULL");
        for (Car car : incoming) {
            if (car.applicationId == null || car.applicationId.isBlank()) {
                upsert(c, car);
                continue;
            }
            QueryResult live = c.query("SELECT 1 FROM owner_applications" +
                    " WHERE id = $1 AND deleted_at IS NULL AND status = 'Approved for onboarding'",
                    new String[]{car.applicationId});
            if (live.rowCount() > 0) upsert(c, car);
        }
    }

    public void delete(PgConnection c, long id) {
        c.query("DELETE FROM cars WHERE id = $1", new String[]{String.valueOf(id)});
    }

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
                value(row[3]), value(row[4]), value(row[5]));
    }

    private static List<Car> rows(QueryResult r) {
        List<Car> out = new ArrayList<>();
        for (int i = 0; i < r.rowCount(); i++) out.add(Car.fromRow(r, i));
        return out;
    }
    private static long parse(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }
    private static String nil(String s) { return s == null || s.isBlank() ? null : s; }
    private static String idOrNull(long id) { return id > 0 ? String.valueOf(id) : null; }
    private static String value(String s) { return s == null ? "" : s; }
}
