package com.pawtrack.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/** Turns proxy failures into RFC 7807 responses instead of a bare 500. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Downstream service not reachable (connection refused, timeout). */
    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail serviceUnavailable(ResourceAccessException ex) {
        log.warn("Downstream service unavailable: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "The service behind this route is not reachable. Try again later.");
        problem.setTitle("Service unavailable");
        return problem;
    }
}
