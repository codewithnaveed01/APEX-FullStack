package com.apex.service;

import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Server-side pricing - the ONLY source of truth for money.
 *
 * Mirrors the frontend quote exactly:
 *   hours  = ceil(duration / 1h), min 1
 *   days   = floor(hours / 24)
 *   extra  = (hours % 24) * hourlyRate, capped at the daily rate
 *   saving = 10% after 7+ days, 20% after 30+ days (on base rent)
 *   driver = driverRate * ceil(hours/24) when "With driver"
 *   deposit= car deposit for self-drive, 0 with driver
 */
public final class PricingService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private PricingService() { }

    public static LocalDateTime parse(String dt) {
        String s = normalize(dt);
        try {
            return LocalDateTime.parse(s, FMT);
        } catch (DateTimeParseException e) {
            throw ApiException.validation("Invalid date/time: " + dt + " (expected YYYY-MM-DDTHH:mm)");
        }
    }

    /** Accepts "YYYY-MM-DDTHH:mm", full ISO instants, or "YYYY-MM-DD HH:mm". */
    private static String normalize(String dt) {
        String s = dt == null ? "" : dt.trim();
        if (s.length() > 16) s = s.substring(0, 16);
        s = s.replace(' ', 'T');
        return s;
    }

    public static String fmt(LocalDateTime t) {
        return t.format(FMT);
    }

    public static int hoursBetween(String start, String end) {
        LocalDateTime s = parse(start);
        LocalDateTime e = parse(end);
        if (!e.isAfter(s)) throw ApiException.validation("Return time must be after pickup time");
        long ms = Duration.between(s, e).toMillis();
        long h = (ms + 3_599_999L) / 3_600_000L;
        return (int) Math.max(1, h);
    }

    /**
     * @param hourly fallback hourly rate (daily/10) when the car has none
     * @return price object in the exact frontend shape
     */
    public static JsonObject quote(String startDt, String endDt,
                                   long dailyRate, long carHourlyRate, long carDeposit,
                                   String service, Long itemDriverRate, long configDriverRate,
                                   boolean capExtraAtDaily) {
        int hours = hoursBetween(startDt, endDt);
        long hourly = carHourlyRate > 0 ? carHourlyRate : Math.max(1, dailyRate / 10);
        int days = hours / 24;
        int extraHours = hours % 24;
        long extra = (long) extraHours * hourly;
        if (capExtraAtDaily && extra > dailyRate) extra = dailyRate;
        int n = Math.max(1, (int) Math.ceil(hours / 24.0));
        double disc = days >= 30 ? 0.20 : days >= 7 ? 0.10 : 0.0;
        long base = dailyRate * days + extra;
        long saving = Math.round(dailyRate * days * disc);
        long driver = "With driver".equals(service)
                ? (itemDriverRate != null && itemDriverRate > 0 ? itemDriverRate : configDriverRate) * n
                : 0;
        long deposit = "With driver".equals(service) ? 0 : carDeposit;
        long rental = base - saving + driver;
        long total = rental + deposit;

        JsonObject p = new JsonObject();
        p.addProperty("n", n);
        p.addProperty("hours", hours);
        p.addProperty("days", days);
        p.addProperty("extraHours", extraHours);
        p.addProperty("base", base);
        p.addProperty("saving", saving);
        p.addProperty("driver", driver);
        p.addProperty("rental", rental);
        p.addProperty("deposit", deposit);
        p.addProperty("total", total);
        return p;
    }

    public static final class Late {
        public final long lateMinutes;
        public final long extraHours;
        public final long charges;
        public final boolean overGrace;
        Late(long lateMinutes, long extraHours, long charges, boolean overGrace) {
            this.lateMinutes = lateMinutes; this.extraHours = extraHours;
            this.charges = charges; this.overGrace = overGrace;
        }
    }

    /**
     * Late return charges: past end + grace, billed per started hour at the
     * effective hourly rate (falling back to daily/10), capped per day at
     * the daily rate when capExtraAtDaily is on.
     */
    public static Late lateCharges(String expectedEnd, String actualReturn,
                                   long dailyRate, long carHourlyRate, int graceMinutes, boolean capExtraAtDaily) {
        LocalDateTime exp = parse(expectedEnd);
        LocalDateTime act = parse(actualReturn);
        long lateMs = Duration.between(exp, act).toMillis();
        if (lateMs <= 0) return new Late(0, 0, 0, false);
        long lateMinutes = (long) Math.ceil(lateMs / 60000.0);
        long grace = Math.max(0, graceMinutes);
        if (lateMinutes <= grace) return new Late(lateMinutes, 0, 0, false);
        long effectiveLateMinutes = lateMinutes - grace;
        long extraHours = Math.max(1, (effectiveLateMinutes + 59) / 60);
        long hourly = carHourlyRate > 0 ? carHourlyRate : Math.max(1, dailyRate / 10);
        long charges = extraHours * hourly;
        long dayCap = (dailyRate > 0 ? dailyRate : charges) * Math.max(1, (extraHours + 23) / 24);
        if (capExtraAtDaily && charges > dayCap) charges = dayCap;
        return new Late(lateMinutes, extraHours, charges, true);
    }

    public static boolean capFromConfig(JsonObject config) {
        String v = Json.getStr(config, "capExtraAtDaily", "true");
        return !"false".equalsIgnoreCase(v) && !"0".equals(v);
    }

    public static int graceFromConfig(JsonObject config) {
        long g = Json.getLong(config, "graceMinutes", 30);
        return (int) Math.max(0, Math.min(g, 720));
    }
}
