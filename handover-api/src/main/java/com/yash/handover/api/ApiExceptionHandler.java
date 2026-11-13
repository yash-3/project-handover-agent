package com.yash.handover.api;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.yash.handover.config.ConfigException;
import com.yash.handover.core.BudgetExceededException;
import com.yash.handover.core.GuardrailViolationException;

/** Turns failures into small JSON errors. Stack traces and unexpected messages are logged, never returned. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        String msg = e instanceof IllegalArgumentException ? e.getMessage() : "Malformed request body";
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(JobService.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(JobService.Conflict e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ApiController.Busy.class)
    ResponseEntity<Map<String, String>> busy(ApiController.Busy e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler({ConfigException.class, BudgetExceededException.class, GuardrailViolationException.class})
    ResponseEntity<Map<String, String>> unprocessable(RuntimeException e) {
        log.warn("Request could not be processed: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** Spring's own client errors (no such route, wrong method, bad media type) keep their 4xx status. */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class})
    ResponseEntity<Map<String, String>> frameworkClientError(Exception e) {
        HttpStatus status = HttpStatus.resolve(((ErrorResponse) e).getStatusCode().value());
        return error(status == null ? HttpStatus.BAD_REQUEST : status,
                status == null ? "Bad request" : status.getReasonPhrase());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> other(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
