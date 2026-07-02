package com.krypton.exception;

import com.krypton.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProgramNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProgramNotFound(ProgramNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Program not found", ex.getMessage());
    }

    @ExceptionHandler(ImportFailedException.class)
    public ResponseEntity<ErrorResponse> handleImportFailed(ImportFailedException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Import failed", ex.getMessage());
    }

    @ExceptionHandler(ClinicalExtractionException.class)
    public ResponseEntity<ErrorResponse> handleClinicalExtraction(ClinicalExtractionException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Clinical extraction failed", ex.getMessage());
    }

    @ExceptionHandler(InvalidMetricsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMetrics(InvalidMetricsException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid metrics", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEntryException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEntry(DuplicateEntryException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Duplicate entry", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(response, status);
    }
}\n