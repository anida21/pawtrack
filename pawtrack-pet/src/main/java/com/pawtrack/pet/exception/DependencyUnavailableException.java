package com.pawtrack.pet.exception;

/** Identity or MinIO could not be reached, so the request cannot be answered safely (503). */
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
