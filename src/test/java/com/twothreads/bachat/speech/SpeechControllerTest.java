package com.twothreads.bachat.speech;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.twothreads.bachat.config.ApiExceptionHandler;

@WebMvcTest(SpeechController.class)
@Import(ApiExceptionHandler.class)
class SpeechControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SpeechPort speech;

    private final MockMultipartFile audio = new MockMultipartFile("audio", "r.webm", "audio/webm", new byte[] {1, 2});

    @Test
    void ok() throws Exception {
        when(speech.transcribe(any(), any(), any(), any())).thenReturn(new Transcript("hello", "en-IN", "r1"));
        mvc.perform(multipart("/api/speech/transcribe").file(audio).param("language", "en-IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("hello"))
                .andExpect(jsonPath("$.languageCode").value("en-IN"))
                .andExpect(jsonPath("$.requestId").value("r1"));
    }

    @Test
    void missingFile() throws Exception {
        mvc.perform(multipart("/api/speech/transcribe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("audio is required"));
    }

    @Test
    void badLanguage() throws Exception {
        mvc.perform(multipart("/api/speech/transcribe").file(audio).param("language", "fr-FR"))
                .andExpect(status().isBadRequest());
    }
}
