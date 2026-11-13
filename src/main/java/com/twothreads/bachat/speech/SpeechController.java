package com.twothreads.bachat.speech;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> LANGUAGES = Set.of("hi-IN", "mr-IN", "gu-IN", "en-IN", "unknown");

    private final SpeechPort speech;

    public SpeechController(SpeechPort speech) {
        this.speech = speech;
    }

    @PostMapping("/transcribe")
    public Transcript transcribe(@RequestParam(value = "audio", required = false) MultipartFile audio,
                                 @RequestParam(value = "language", defaultValue = "unknown") String language)
            throws IOException {
        if (audio == null || audio.isEmpty()) {
            throw new SpeechException(HttpStatus.BAD_REQUEST, "audio is required");
        }
        if (audio.getSize() > MAX_BYTES) {
            throw new SpeechException(HttpStatus.CONTENT_TOO_LARGE, "audio too large");
        }
        String type = audio.getContentType();
        if (type == null || !type.toLowerCase().startsWith("audio/")) {
            throw new SpeechException(HttpStatus.BAD_REQUEST, "audio is required");
        }
        if (!LANGUAGES.contains(language)) {
            throw new SpeechException(HttpStatus.BAD_REQUEST, "unsupported language");
        }
        return speech.transcribe(audio.getBytes(), audio.getOriginalFilename(), type, language);
    }
}
