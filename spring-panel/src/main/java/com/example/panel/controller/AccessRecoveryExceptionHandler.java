package com.example.panel.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {PasswordResetRequestApiController.class, AttentionApiController.class})
public class AccessRecoveryExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("success", false, "error", String.valueOf(error.getReason())));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException error) {
        return ResponseEntity.status(403).cacheControl(CacheControl.noStore()).body(Map.of("success", false, "error", error.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalidArgument(IllegalArgumentException error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("success", false, "error", "Некорректный запрос"));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> unavailable(DataAccessException error) {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("success", false, "error", "Сервис временно недоступен. Попробуйте позже."));
    }
}
