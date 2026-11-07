package com.pawtrack.health.exception;

/** A request that is well-formed but contradicts itself (e.g. {@code from} after {@code to}). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
