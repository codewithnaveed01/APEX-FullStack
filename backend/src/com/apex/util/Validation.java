package com.apex.util;

import com.apex.web.ApiException;

/** Input validation - every field that touches the database goes through here. */
public final class Validation {

    private Validation() { }

    public static String required(String value, String field, int min, int max) {
        String v = Json.clean(value);
        if (v.length() < min) throw ApiException.validation("Missing or invalid " + field);
        if (v.length() > max) throw ApiException.validation(field + " is too long");
        return v;
    }

    public static String required(String value, String field) {
        String v = Json.clean(value);
        if (v.isEmpty()) throw ApiException.validation("Missing " + field);
        return v;
    }

    public static boolean email(String v) {
        return v != null && v.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public static String phone(String value) {
        String v = Json.clean(value).replaceAll("[\\s-]", "");
        if (!v.isEmpty() && !v.matches("^\\+?[0-9]{6,15}$")) {
            throw ApiException.validation("Invalid phone number");
        }
        return v;
    }

    /** CNIC: exactly 13 digits (old and new format). */
    public static String cnic(String value) {
        String v = Json.clean(value).replaceAll("[\\s-]", "");
        if (!v.matches("^[0-9]{13}$")) {
            throw ApiException.validation("CNIC must be 13 digits");
        }
        return v;
    }

    public static String passport(String value) {
        String v = Json.clean(value).toUpperCase();
        if (!v.matches("^[A-Z0-9]{6,12}$")) {
            throw ApiException.validation("Invalid passport number");
        }
        return v;
    }

    public static void oneOf(String value, String field, String... allowed) {
        String v = Json.clean(value);
        for (String a : allowed) {
            if (a.equals(v)) return;
        }
        throw ApiException.validation("Invalid " + field + ": " + v);
    }

    public static long amount(String value, String field) {
        String v = Json.clean(value).replaceAll("[^0-9.]", "");
        if (v.isEmpty()) throw ApiException.validation("Missing " + field);
        try {
            long x = (long) Math.round(Double.parseDouble(v));
            if (x < 0) throw ApiException.validation(field + " cannot be negative");
            return x;
        } catch (NumberFormatException e) {
            throw ApiException.validation("Invalid " + field);
        }
    }

    public static long longValue(String value, String field) {
        String v = Json.clean(value).replaceAll("[^0-9-]", "");
        if (v.isEmpty()) throw ApiException.validation("Missing " + field);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw ApiException.validation("Invalid " + field);
        }
    }

    public static int intValue(String value, String field) {
        return (int) longValue(value, field);
    }

    public static String date(String value, String field) {
        String v = Json.clean(value);
        if (!v.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw ApiException.validation("Invalid " + field + " (expected YYYY-MM-DD)");
        }
        try {
            java.time.LocalDate.parse(v);
        } catch (java.time.format.DateTimeParseException ex) {
            throw ApiException.validation("Invalid " + field + " (expected YYYY-MM-DD)");
        }
        return v;
    }

    public static void assertMinAge(java.time.LocalDate dob) {
        if (dob == null) return;
        java.time.Period p = java.time.Period.between(dob, java.time.LocalDate.now());
        if (p.getYears() < 18) throw ApiException.validation("Booker must be at least 18 years old");
    }
}
