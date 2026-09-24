package com.apex.log;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Tiny logger with {} placeholders. Never logs passwords, tokens or
 * full CNICs - call sites must redact before logging.
 */
public final class Logger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Logger() { }

    public static void info(String msg, Object... args)  { log("INFO", msg, args, null); }
    public static void warn(String msg, Object... args)  { log("WARN", msg, args, null); }
    public static void error(String msg, Object... args) { log("ERROR", msg, args, null); }

    public static void error(String msg, Throwable t) {
        log("ERROR", msg, new Object[0], t);
    }

    private static void log(String level, String msg, Object[] args, Throwable t) {
        String line = fill(msg, args);
        String ts = FMT.withZone(ZoneOffset.UTC).format(Instant.now());
        System.out.println(ts + " [" + level + "] [APEX] " + line);
        if (t != null) t.printStackTrace(System.out);
        System.out.flush();
    }

    private static String fill(String msg, Object[] args) {
        if (args == null || args.length == 0) return msg;
        StringBuilder sb = new StringBuilder();
        int ai = 0;
        for (int i = 0; i < msg.length(); i++) {
            if (ai < args.length && i + 1 < msg.length() && msg.charAt(i) == '{' && msg.charAt(i + 1) == '}') {
                sb.append(args[ai++]);
                i++;
            } else {
                sb.append(msg.charAt(i));
            }
        }
        return sb.toString();
    }
}
