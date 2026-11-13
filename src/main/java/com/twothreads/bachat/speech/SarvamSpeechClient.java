package com.twothreads.bachat.speech;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.twothreads.bachat.config.SarvamProperties;

@Component
@ConditionalOnProperty(name = "bachat.mode", havingValue = "live", matchIfMissing = true)
public class SarvamSpeechClient implements SpeechPort {

    private static final Logger log = LoggerFactory.getLogger(SarvamSpeechClient.class);
    private static final String FAILED = "speech service failed";

    private final SarvamProperties props;
    private final RestClient client;

    @Autowired
    public SarvamSpeechClient(SarvamProperties props) {
        this(props, defaultClient(props));
    }

    SarvamSpeechClient(SarvamProperties props, RestClient client) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("SARVAM_API_KEY must be set when bachat.mode=live");
        }
        this.props = props;
        this.client = client;
    }

    private static RestClient defaultClient(SarvamProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(f).baseUrl(props.baseUrl()).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Transcript transcribe(byte[] audio, String filename, String contentType, String languageCode) {
        String name = (filename == null || filename.isBlank()) ? "audio.webm" : filename;
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(baseType(contentType)));
        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return name;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        body.add("model", props.model());
        body.add("language_code", languageCode == null ? "unknown" : languageCode);
        if (!props.keyterms().isEmpty()) {
            // Sarvam expects keyterms as a JSON-encoded array in a single form field.
            body.add("keyterms", toJsonArray(props.keyterms()));
        }

        try {
            Map<String, Object> res = client.post()
                    .uri("/speech-to-text")
                    .header("api-subscription-key", props.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (res == null || res.get("transcript") == null) {
                log.warn("Sarvam returned an empty response");
                throw new SpeechException(HttpStatus.BAD_GATEWAY, FAILED);
            }
            return new Transcript(str(res.get("transcript")), str(res.get("language_code")), str(res.get("request_id")));
        } catch (RestClientResponseException e) {
            log.warn("Sarvam STT failed: status={}", e.getStatusCode().value());
            throw new SpeechException(HttpStatus.BAD_GATEWAY, FAILED, e);
        } catch (RestClientException e) {
            log.warn("Sarvam STT call failed: {}", e.getClass().getSimpleName());
            throw new SpeechException(HttpStatus.BAD_GATEWAY, FAILED, e);
        }
    }

    private static String toJsonArray(Iterable<String> terms) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String t : terms) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(t.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private static String baseType(String contentType) {
        if (contentType == null || contentType.isBlank()) return "audio/webm";
        int i = contentType.indexOf(';');
        return (i >= 0 ? contentType.substring(0, i) : contentType).trim();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
