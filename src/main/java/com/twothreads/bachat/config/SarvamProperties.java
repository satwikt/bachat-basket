package com.twothreads.bachat.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sarvam")
public record SarvamProperties(String apiKey, String baseUrl, String model, List<String> keyterms) {
    public SarvamProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.sarvam.ai";
        if (model == null || model.isBlank()) model = "saaras:v4";
        if (keyterms == null) keyterms = List.of();
    }
}
