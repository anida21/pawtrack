package com.pawtrack.health.exception;

/** Pet could not be reached, so the request cannot be answered safely (503). */
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
