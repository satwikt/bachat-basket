package com.twothreads.bachat.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.twothreads.bachat.config.SarvamProperties;

class SarvamSpeechClientTest {

    private static final String URL = "https://api.sarvam.ai/speech-to-text";
    private MockRestServiceServer server;
    private SarvamSpeechClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.sarvam.ai");
        server = MockRestServiceServer.bindTo(builder).build();
        SarvamProperties props = new SarvamProperties("test-key", "https://api.sarvam.ai", "saaras:v4", List.of("doodh"));
        client = new SarvamSpeechClient(props, builder.build());
    }

    private Transcript call() {
        return client.transcribe(new byte[] {1, 2, 3}, "r.webm", "audio/webm;codecs=opus", "hi-IN");
    }

    @Test
    void success() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", "test-key"))
                .andRespond(withSuccess(
                        "{\"transcript\":\"doodh 6 packet 22\",\"language_code\":\"hi-IN\",\"request_id\":\"r1\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(call()).isEqualTo(new Transcript("doodh 6 packet 22", "hi-IN", "r1"));
    }

    @Test
    void clientError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(this::call).isInstanceOf(SpeechException.class).hasMessage("speech service failed");
    }

    @Test
    void serverError() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(this::call).isInstanceOf(SpeechException.class).hasMessage("speech service failed");
    }

    @Test
    void timeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertThatThrownBy(this::call).isInstanceOf(SpeechException.class).hasMessage("speech service failed");
    }

    @Test
    void blankKeyFailsFast() {
        assertThatThrownBy(() -> new SarvamSpeechClient(new SarvamProperties("", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
