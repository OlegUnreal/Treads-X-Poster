package com.behindthesmile.posting.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleServerError(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, Exception exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", friendlyMessage(exception));
        body.put("type", exception.getClass().getSimpleName());
        return ResponseEntity.status(status).body(body);
    }

    private String friendlyMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
