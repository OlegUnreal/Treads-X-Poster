package com.behindthesmile.posting.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConfiguration(IllegalStateException exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleServerError(Throwable exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, Throwable exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", friendlyMessage(exception));
        body.put("type", exception.getClass().getSimpleName());
        return ResponseEntity.status(status).body(body);
    }

    private String friendlyMessage(Throwable exception) {
        StringBuilder message = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            String part = current.getMessage();
            if (part != null && !part.isBlank() && !message.toString().contains(part)) {
                if (!message.isEmpty()) {
                    message.append(" | Caused by: ");
                }
                message.append(part);
            }
            current = current.getCause();
        }
        return message.isEmpty() ? exception.getClass().getSimpleName() : message.toString();
    }
}
