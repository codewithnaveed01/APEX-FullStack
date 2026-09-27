package com.apex.db;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Minimal, self-contained PostgreSQL client (wire protocol v3).
 *
 * Why: the deployment environment has no access to the Maven Central
 * postgresql JDBC jar, so the driver is implemented here on top of
 * java.net/java.security only. It implements exactly what the APEX
 * backend needs:
 *
 *  - startup + authentication: cleartext, md5 and SCRAM-SHA-256
 *  - optional TLS (sslmode=require)
 *  - simple query protocol   (DDL, BEGIN/COMMIT/ROLLBACK)
 *  - extended query protocol (Parse/Bind/Describe/Execute/Sync) with
 *    text-format parameters - every query in this codebase is
 *    parameterized, which keeps SQL injection out by construction
 *
 * A connection is single-threaded; the pool in {@link Database}
 * hands each connection to exactly one request at a time.
 */
public final class PgConnection implements AutoCloseable {

    /** Error thrown for PostgreSQL ErrorResponse messages. */
    public static final class PgException extends RuntimeException {
        public final String pgCode;
        public PgException(String pgCode, String message) {
            super(message);
            this.pgCode = pgCode;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private Socket socket;
    private DataInputStream in;
    /** False while a response is half-read; Database must discard us if so. */
    private volatile boolean healthy = true;
    private DataOutputStream out;

    private final String user;
    private final String database;
    private volatile boolean closed = false;
    private int pid;

    public PgConnection(String host, int port, String user, String password,
                        String database, boolean ssl) {
        this.user = user;
        this.database = database;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 10_000);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30_000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            if (ssl) {
                negotiateSsl(host, port);
            }
            startup();
            authenticate(password);
            // The auth-phase greeting (ParameterStatus / BackendKeyData /
            // ReadyForQuery) arrives after AuthenticationOk - drain it so
            // the first query only sees its own response.
            readUntilReady("<startup>", null);
        } catch (PgException e) {
            hardClose();
            throw e;
        } catch (Exception e) {
            hardClose();
            throw new PgException("08006", "Cannot connect to PostgreSQL: " + rootMessage(e));
        }
    }

    /* ---------------- startup + authentication ---------------- */

    private void negotiateSsl(String host, int port) throws Exception {
        // SSLRequest: length(8) + code 80877103
        out.writeInt(8);
        out.writeInt(80877103);
        out.flush();
        char answer = (char) in.readUnsignedByte();
        if (answer != 'S') {
            throw new PgException("08004", "Server refused TLS but sslmode=require");
        }
        // Trust-all TLS: the goal is encrypting the channel to a managed
        // database whose CA is not in the JDK truststore (Railway etc.).
        // Authentication still protects credentials via SCRAM-SHA-256.
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) { }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) { }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        }}, new SecureRandom());
        // The wrap overload createSocket(Socket, String, int, boolean) is a
        // stable JDK API; some stripped JDK builds ship a broken compiler
        // symbol index for it, so it is invoked reflectively (verified at
        // runtime - it is present in every stock JDK 8+).
        java.lang.reflect.Method wrap = SSLSocketFactory.class.getMethod(
                "createSocket", Socket.class, String.class, int.class, boolean.class);
        socket = (Socket) wrap.invoke(ctx.getSocketFactory(), socket, host, port, true);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    private void startup() throws Exception {
        byte[] usr = ("user\0" + user + '\0').getBytes(StandardCharsets.UTF_8);
        byte[] db = ("database\0" + database + '\0').getBytes(StandardCharsets.UTF_8);
        int len = 4 + 4 + usr.length + db.length + 1;
        out.writeInt(len);
        out.writeInt(196608); // protocol 3.0
        out.write(usr);
        out.write(db);
        out.writeByte(0);
        out.flush();
    }

    private void authenticate(String password) throws Exception {
        // SCRAM state belongs to this connection, never to a static/shared field:
        // the pool may establish connections concurrently for different requests.
        String[] scramState = null;
        String expectedServerSignature = null;
        for (int guard = 0; guard < 20; guard++) {
            AuthMessage msg = readAuth();
            switch (msg.code) {
                case 0:  // AuthenticationOk
                    return;
                case 3:  // cleartext password
                    sendPassword(password);
                    break;
                case 5: { // md5: the 4-byte salt is binary, NOT UTF-8 text
                    if (msg.data.length != 4) throw new PgException("08006", "Invalid MD5 salt");
                    byte[] inner = md5Hex((password + user).getBytes(StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.US_ASCII);
                    byte[] salted = new byte[inner.length + msg.data.length];
                    System.arraycopy(inner, 0, salted, 0, inner.length);
                    System.arraycopy(msg.data, 0, salted, inner.length, msg.data.length);
                    sendPassword("md5" + md5Hex(salted));
                    break;
                }
                case 10: { // AuthenticationSASL: NUL-separated mechanism names
                    String mechs = "\0" + new String(msg.data, StandardCharsets.UTF_8) + "\0";
                    if (!mechs.contains("\0SCRAM-SHA-256\0")) {
                        throw new PgException("28000", "Server does not offer SCRAM-SHA-256");
                    }
                    String nonce = Scram.randomNonce();
                    String bare = "n=" + user.replace("=", "=3D").replace(",", "=2C") + ",r=" + nonce;
                    byte[] first = ("n,," + bare).getBytes(StandardCharsets.UTF_8);
                    byte[] mech = "SCRAM-SHA-256\0".getBytes(StandardCharsets.UTF_8);
                    out.writeByte('p');
                    out.writeInt(4 + mech.length + 4 + first.length);
                    out.write(mech);
                    out.writeInt(first.length);
                    out.write(first);
                    out.flush();
                    scramState = Scram.init(password, bare, nonce);
                    break;
                }
                case 11: { // AuthenticationSASLContinue: raw UTF-8, no length prefix
                    if (scramState == null) throw new PgException("28000", "Unexpected SCRAM challenge");
                    String[] r = Scram.step(new String(msg.data, StandardCharsets.UTF_8), scramState);
                    byte[] clientFinal = r[0].getBytes(StandardCharsets.UTF_8);
                    out.writeByte('p');
                    out.writeInt(4 + clientFinal.length);
                    out.write(clientFinal);
                    out.flush();
                    expectedServerSignature = r[1];
                    break;
                }
                case 12: // AuthenticationSASLFinal
                    if (expectedServerSignature == null) throw new PgException("28000", "Unexpected SCRAM reply");
                    Scram.verifyFinal(new String(msg.data, StandardCharsets.UTF_8), expectedServerSignature);
                    break;
                default:
                    throw new PgException("28000", "Unsupported authentication code " + msg.code);
            }
        }
        throw new PgException("28000", "Authentication did not complete");
    }

    private static final class AuthMessage {
        final int code;
        final byte[] data;
        AuthMessage(int code, byte[] data) { this.code = code; this.data = data; }
    }

    private AuthMessage readAuth() throws IOException {
        for (int guard = 0; guard < 20; guard++) {
            int type = in.readUnsignedByte();
            int len = in.readInt();
            if (len < 4 || len > 1_048_576) throw new IOException("Invalid PostgreSQL auth message length");
            if (type == 'R') {
                if (len < 8) throw new IOException("Truncated PostgreSQL auth message");
                int code = in.readInt();
                // Consume the WHOLE framed message, including SASL's final NUL;
                // the next read must start on the next message boundary.
                return new AuthMessage(code, readFixed(len - 8));
            }
            byte[] payload = readFixed(len - 4);
            if (type == 'E') throw parseError(payload); // e.g. wrong password / missing DB
            // ParameterStatus, Notice and BackendKeyData may precede auth.
        }
        throw new IOException("Too many PostgreSQL startup messages");
    }

    private void sendPassword(String pw) throws IOException {
        byte[] b = (pw + '\0').getBytes(StandardCharsets.UTF_8);
        out.writeByte('p');
        out.writeInt(4 + b.length);
        out.write(b);
        out.flush();
    }

    /* ---------------- queries ---------------- */

    /** Simple protocol: one statement, no parameters (DDL / BEGIN / COMMIT). */
    public void simpleQuery(String sql) {
        try {
            byte[] q = (sql + '\0').getBytes(StandardCharsets.UTF_8);
            out.writeByte('Q');
            out.writeInt(4 + q.length);
            out.write(q);
            out.flush();
            readUntilReady(sql, null);
        } catch (PgException e) {
            throw e;
        } catch (Exception e) {
            healthy = false;
            throw new PgException("08006", "Query failed: " + rootMessage(e));
        }
    }

    /** Extended protocol: parameterized query, text format in and out. */
    public QueryResult query(String sql, String[] params) {
        try {
            writeExtended(sql, params == null ? new String[0] : params);
            out.flush();
            QueryResult res = new QueryResult();
            readUntilReady(sql, res);
            return res;
        } catch (PgException e) {
            throw e;
        } catch (Exception e) {
            healthy = false; // an interrupted response must not be reused by the pool
            throw new PgException("08006", "Query failed: " + rootMessage(e));
        }
    }

    private void writeExtended(String sql, String[] params) throws IOException {
        // All names are C strings - the unnamed statement/portal is a lone NUL.
        byte[] unnamed = new byte[]{0};
        byte[] q = (sql + '\0').getBytes(StandardCharsets.UTF_8);
        // Parse
        out.writeByte('P');
        out.writeInt(4 + unnamed.length + q.length + 2);
        out.write(unnamed);
        out.write(q);
        out.writeShort(0);     // no parameter type OIDs (server infers)
        // Bind
        out.writeByte('B');
        int bodyLen = 4 + unnamed.length + unnamed.length + 2 + 2 + 2;
        for (String p : params) bodyLen += 4 + (p == null ? 0 : p.getBytes(StandardCharsets.UTF_8).length);
        out.writeInt(bodyLen);
        out.write(unnamed);    // unnamed portal
        out.write(unnamed);    // unnamed statement
        out.writeShort(0);     // no parameter format codes (all text)
        out.writeShort(params.length);
        for (String p : params) {
            if (p == null) {
                out.writeInt(-1);
            } else {
                byte[] b = p.getBytes(StandardCharsets.UTF_8);
                out.writeInt(b.length);
                out.write(b);
            }
        }
        out.writeShort(0);     // no result format codes (all text)
        // Describe portal
        out.writeByte('D');
        out.writeInt(4 + 1 + unnamed.length);
        out.writeByte('P');
        out.write(unnamed);
        // Execute
        out.writeByte('E');
        out.writeInt(4 + unnamed.length + 4);
        out.write(unnamed);
        out.writeInt(0);       // no row limit
        // Sync
        out.writeByte('S');
        out.writeInt(4);
    }

    private void readUntilReady(String sql, QueryResult res) throws Exception {
        PgException firstError = null;
        healthy = false; // only becomes true again once ReadyForQuery is consumed
        for (int guard = 0; guard < 100_000; guard++) {
            int type = in.readUnsignedByte();
            int len = in.readInt();
            switch (type) {
                case 'T': { // RowDescription
                    int n = in.readUnsignedShort();
                    List<String> cols = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        cols.add(readCString());
                        in.readInt(); in.readUnsignedShort(); in.readInt(); in.readUnsignedShort(); in.readInt(); in.readUnsignedShort();
                    }
                    if (res != null) res.columns = cols;
                    break;
                }
                case 'D': { // DataRow
                    int n = in.readUnsignedShort();
                    String[] row = new String[n];
                    for (int i = 0; i < n; i++) {
                        int vlen = in.readInt();
                        row[i] = vlen < 0 ? null : new String(readFixed(vlen), StandardCharsets.UTF_8);
                    }
                    if (res != null) res.rows.add(row);
                    break;
                }
                case 'C': { // CommandComplete
                    String tag = readCString();
                    if (res != null) res.tag = tag;
                    skipRestOfMessage(len - 4 - (tag.length() + 1));
                    break;
                }
                case 'E': { // ErrorResponse - drain through ReadyForQuery before throwing
                    PgException e = parseError(readFixed(len - 4));
                    if (firstError == null) firstError = e;
                    break;
                }
                case 'N': // NoticeResponse
                case 'W': // WarningResponse (PG 13+)
                    skipPayload(len);
                    break;
                case 't': { // ParameterDescription
                    int n = in.readUnsignedShort();
                    for (int i = 0; i < n; i++) in.readInt();
                    break;
                }
                case 'Z': // ReadyForQuery - done
                    in.readUnsignedByte();
                    healthy = true;
                    if (firstError != null) throw firstError;
                    return;
                case 'S': // ParameterStatus
                    skipRestOfMessage(len - 4);
                    break;
                case 'K': // BackendKeyData (pid, secret - both int32)
                    pid = in.readInt();
                    in.readInt();
                    break;
                case '1': case '2': case 'n': case 'I':
                    skipRestOfMessage(len - 4);
                    break;
                default:
                    // Unknown-but-well-framed message: skip it so the stream
                    // stays synchronized (poisoning the pool is far worse).
                    skipPayload(len);
                    break;
            }
        }
        throw new PgException("08006", "Query did not complete: " + shortSql(sql));
    }

    private static PgException parseError(byte[] payload) {
        String code = "XX000", message = "unknown error", severity = "ERROR";
        for (int i = 0; i < payload.length && payload[i] != 0; ) {
            int field = payload[i++];
            int end = i;
            while (end < payload.length && payload[end] != 0) end++;
            String val = new String(payload, i, end - i, StandardCharsets.UTF_8);
            switch (field) {
                case 'C': code = val; break;
                case 'M': message = val; break;
                case 'S': severity = val; break;
                default: break;
            }
            i = end + 1;
        }
        return new PgException(code, "[" + severity + "] " + message);
    }

    public boolean ping() {
        try {
            simpleQuery("SELECT 1");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public int getPid() { return pid; }

    public boolean isClosed() { return closed || socket == null || socket.isClosed(); }

    /** True only if the last exchange ended on a clean message boundary. */
    public boolean isHealthy() { return healthy && !isClosed(); }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            out.writeByte('X'); // Terminate has no payload
            out.writeInt(4);
            out.flush();
        } catch (Exception ignored) { }
        hardClose();
    }

    private void hardClose() {
        closed = true;
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
    }

    /* ---------------- low-level helpers ---------------- */

    private String readCString() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.readUnsignedByte()) != 0) b.write(c);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private byte[] readFixed(int len) throws IOException {
        byte[] b = new byte[len];
        in.readFully(b);
        return b;
    }

    private void skipPayload(int len) throws IOException {
        skipRestOfMessage(len - 4);
    }

    private void skipRestOfMessage(int remaining) throws IOException {
        byte[] buf = new byte[8192];
        while (remaining > 0) {
            int n = Math.min(buf.length, remaining);
            in.readFully(buf, 0, n);
            remaining -= n;
        }
    }

    private static String shortSql(String sql) {
        String s = sql == null ? "" : sql.trim();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage() == null ? c.toString() : c.getMessage();
    }

    private static String md5Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
