package com.twothreads.bachat.speech;

import org.springframework.http.HttpStatus;

/** Safe-to-expose failure from the speech layer. */
public class SpeechException extends RuntimeException {
    private final HttpStatus status;

    public SpeechException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public SpeechException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
