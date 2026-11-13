package com.twothreads.bachat.speech;

public interface SpeechPort {
    Transcript transcribe(byte[] audio, String filename, String contentType, String languageCode);
}
