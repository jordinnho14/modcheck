package dev.modcheck.modcheck.web;

import dev.modcheck.modcheck.client.NexusApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<Map<String, String>> handleNotFound(HttpClientErrorException.NotFound e) {
        log.warn("Nexus 404: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "error", "Not found on Nexus",
                "message", "The game domain or mod id in your request doesn't exist on Nexus."
            ));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, String>> handleNexusHttpError(HttpClientErrorException e) {
        log.error("Nexus API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of(
                "error", "Nexus API error",
                "message", "Nexus returned an error: " + e.getStatusCode()
            ));
    }

    @ExceptionHandler(NexusApiException.class)
    public ResponseEntity<Map<String, String>> handleNexusApiException(NexusApiException e) {
        log.error("Nexus GraphQL error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "Nexus API error", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "Invalid request", "message", e.getMessage()));
    }
}