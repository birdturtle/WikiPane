package com.wikipane.api;

/** Small user-facing vocabulary; exception detail belongs in diagnostic logs. */
public final class WikiErrors {
    private WikiErrors() {}
    public static String message(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.UnknownHostException || cause instanceof java.net.ConnectException)
                return "Couldn't reach the Wiki. Check your connection and try again.";
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.io.InterruptedIOException)
                return "The Wiki took too long to respond. Please try again.";
            if (cause instanceof javax.net.ssl.SSLException)
                return "Couldn't establish a secure connection to the Wiki. Please try again later.";
        }
        String message = error.getMessage();
        if (error instanceof java.io.IOException && message != null && message.matches(
            "(?s)^(?:The Wiki|This Wiki|Wiki request|Wiki content|This page|No single article|Too many Wiki|Unexpected Wiki|Unsupported entity).*"))
            return message;
        return "Couldn't load the Wiki information. Please try again.";
    }
}
