package com.twothreads.bachat.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Offline fallback for demos (BACHAT_MODE=demo). */
@Component
@ConditionalOnProperty(name = "bachat.mode", havingValue = "demo")
public class StubSpeechClient implements SpeechPort {
    @Override
    public Transcript transcribe(byte[] audio, String filename, String contentType, String languageCode) {
        return new Transcript(
                "doodh 6 packet, original price 30 discounted price 23, "
                        + "bread 4 loaf, original price 35 discounted price 30",
                "hi-IN", "stub");
    }
}
