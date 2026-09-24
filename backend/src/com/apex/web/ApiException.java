package com.apex.web;

/**
 * Centralized API error. Caught once in the router and mapped to a JSON
 * response: {"error": "..."} with the proper HTTP status.
 */
public class ApiException extends RuntimeException {

    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int VALIDATION = 422;
    public static final int SERVER = 500;

    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException bad(String m)              { return new ApiException(BAD_REQUEST, m); }
    public static ApiException unauth(String m)           { return new ApiException(UNAUTHORIZED, m); }
    public static ApiException forbidden(String m)        { return new ApiException(FORBIDDEN, m); }
    public static ApiException notFound(String m)         { return new ApiException(NOT_FOUND, m); }
    public static ApiException conflict(String m)         { return new ApiException(CONFLICT, m); }
    public static ApiException validation(String m)       { return new ApiException(VALIDATION, m); }
    public static ApiException server(String m)           { return new ApiException(SERVER, m); }
}
