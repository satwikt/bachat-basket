package com.twothreads.bachat.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.twothreads.bachat.customer.CustomerNotFoundException;
import com.twothreads.bachat.listing.ListingValidationException;
import com.twothreads.bachat.listing.ShopNotFoundException;
import com.twothreads.bachat.speech.SpeechException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<Map<String, String>> customerNotFound(CustomerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "customer not found"));
    }

    @ExceptionHandler(SpeechException.class)
    ResponseEntity<Map<String, String>> speech(SpeechException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of("error", "audio too large"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<Map<String, String>> missingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "audio is required"));
    }

    @ExceptionHandler(ShopNotFoundException.class)
    ResponseEntity<Map<String, String>> shopNotFound(ShopNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "shop not found"));
    }

    @ExceptionHandler(ListingValidationException.class)
    ResponseEntity<Map<String, String>> listingInvalid(ListingValidationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid request body"));
    }
}
