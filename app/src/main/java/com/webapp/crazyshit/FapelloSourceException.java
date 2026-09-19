package com.webapp.crazyshit;

import java.io.IOException;

/** A typed Fapello failure so an unavailable source is not mistaken for an empty creator. */
final class FapelloSourceException extends IOException {
    enum Reason {
        BLOCKED,
        RATE_LIMITED,
        NOT_FOUND,
        HTTP,
        MALFORMED,
        PARSER,
        NETWORK
    }

    final Reason reason;
    final int statusCode;

    FapelloSourceException(Reason reason, String message) {
        this(reason, 0, message, null);
    }

    FapelloSourceException(Reason reason, int statusCode, String message) {
        this(reason, statusCode, message, null);
    }

    FapelloSourceException(Reason reason, String message, Throwable cause) {
        this(reason, 0, message, cause);
    }

    FapelloSourceException(Reason reason, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? Reason.NETWORK : reason;
        this.statusCode = statusCode;
    }

    String userMessage() {
        switch (reason) {
            case BLOCKED:
                return "Fapello blocked this request. Other OnlyFap sources can still load.";
            case RATE_LIMITED:
                return "Fapello is rate limiting requests. Try again in a few minutes.";
            case NOT_FOUND:
                return "This Fapello creator or media page is no longer available.";
            case MALFORMED:
            case PARSER:
                return "Fapello returned a page the app could not read.";
            case HTTP:
                return statusCode > 0
                        ? "Fapello returned HTTP " + statusCode + "."
                        : "Fapello returned an HTTP error.";
            case NETWORK:
            default:
                return "Fapello could not be reached.";
        }
    }
}
