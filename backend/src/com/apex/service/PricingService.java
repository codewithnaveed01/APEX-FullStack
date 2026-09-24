package com.apex.service;

import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Rental pricing engine - configurable, never hard-coded.
 *
 * Rules (admin configurable through settings):
 *  - every car has an hourly AND a daily rate
 *  - duration = ceil(end - start) in hours
 *  - cost = fullDays * daily + remainderHours * hourly
 *  - when capRemainderAtDaily = true the remainder is capped at one daily rate
 *    (renting 25h never costs more than 2 days)
 *  - late returns: graceMinutes free, then the same day/hour rules apply to
 *    the extra time so customers can't escape late payment
 */
public class PricingService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** Parses "yyyy-MM-dd'T'HH:mm" (also tolerates full ISO). */
    public static LocalDateTime parse(String s) {
        String t = s == null ? "" : s.trim();
        if (t.length() > 16 && t.charAt(10) == 'T') t = t.substring(0, 16);
        return LocalDateTime.parse(t, FMT);
    }

    public static String fmt(LocalDateTime t) {
        return t.format(FMT);
    }

    public static long hoursBetween(String startDt, String endDt) {
        Duration d = Duration.between(parse(startDt), parse(endDt));
        long minutes = d.toMinutes();
        if (minutes < 0) throw new IllegalArgumentException("Return time must be after start time");
        return (minutes + 59) / 60; // round up to started hours
    }

    /** Cost for a duration with the configured rules. */
    public static long cost(long totalHours, long hourly, long daily, boolean capAtDaily) {
        long days = totalHours / 24;
        long rem = totalHours % 24;
        long remCost = rem * hourly;
        if (capAtDaily && remCost > daily) remCost = daily;
        return days * daily + remCost;
    }

    /** Full breakdown used by quotes, receipts and the admin console. */
    public static JsonObject breakdown(String startDt, String endDt, long hourly, long daily,
                                       int graceMinutes, boolean capAtDaily) {
        long hours = hoursBetween(startDt, endDt);
        JsonObject o = new JsonObject();
        o.addProperty("hours", hours);
        o.addProperty("days", hours / 24);
        o.addProperty("extraHours", hours % 24);
        o.addProperty("amount", cost(hours, hourly, daily, capAtDaily));
        return o;
    }

    /**
     * Late-return charges: anything beyond expected + grace is billed with the
     * same day/hour strategy.
     */
    public static JsonObject lateCharges(String expectedDt, String actualDt, long hourly, long daily,
                                         int graceMinutes, boolean capAtDaily) {
        long extraMinutes = Duration.between(parse(expectedDt), parse(actualDt)).toMinutes();
        long billable = Math.max(0, extraMinutes - graceMinutes);
        long extraHours = (billable + 59) / 60;
        JsonObject o = new JsonObject();
        o.addProperty("lateMinutes", Math.max(0, extraMinutes));
        o.addProperty("graceMinutes", graceMinutes);
        o.addProperty("extraHours", extraHours);
        o.addProperty("charges", extraHours == 0 ? 0 : cost(extraHours, hourly, daily, capAtDaily));
        return o;
    }
}
