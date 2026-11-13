# Implementation spec: Speech-to-text with Sarvam (Bachat Basket)

Goal: a shopkeeper speaks a listing ("doodh 6 packet 22, bread 4 loaf 30"), the browser records it,
the backend sends it to Sarvam speech-to-text, and the transcript comes back as JSON.
This is the skeleton for the later Pack Reader step (transcript -> items), which is NOT part of this task.

## Scope
In scope:
- `POST /api/speech/transcribe` (multipart audio in, transcript JSON out)
- `SpeechPort` interface, `SarvamSpeechClient` (real) and `StubSpeechClient` (demo)
- Config in `application.yml`, error handling, one unit test, one manual test
- The test recorder page already at `src/main/resources/static/recorder.html` (served at `/recorder.html`)

Out of scope: parsing the transcript into items, vision/photo reading, auth, storing audio.

## Existing project facts
- Spring Boot 4.1.x, Maven, Java 21 target, base package `com.twothreads.bachat`
- Supabase Postgres via Flyway; env vars `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SARVAM_API_KEY`
- Never hard-code or log the API key. Never log audio bytes.

## Sarvam API (verify against https://docs.sarvam.ai before coding)
- `POST https://api.sarvam.ai/speech-to-text`
- Header: `api-subscription-key: <SARVAM_API_KEY>`
- Multipart form fields:
  - `file`: audio (WAV, MP3, OGG, OPUS, WebM supported; 16 kHz is best)
  - `model`: `saaras:v4` (default; `saaras:v3` also exists)
  - `language_code`: `hi-IN`, `mr-IN`, `gu-IN`, `en-IN`, or `unknown` for auto-detect
  - `keyterms`: optional, up to 50 terms, `saaras:v4` only (use for words like doodh, bread, packet, loaf)
- Response JSON: `transcript`, `language_code`, `request_id`
- The REST endpoint is for audio under about 30 seconds. Longer audio needs the batch API (not needed now).

## Package layout (create)
```
com.twothreads.bachat.speech
  SpeechPort.java            interface
  Transcript.java            record
  SarvamSpeechClient.java    @Component @ConditionalOnProperty(bachat.mode=live, matchIfMissing=true)
  StubSpeechClient.java      @Component @ConditionalOnProperty(bachat.mode=demo)
  SpeechController.java      POST /api/speech/transcribe
  SpeechException.java       runtime exception -> 502 / 400
com.twothreads.bachat.config
  SarvamProperties.java      @ConfigurationProperties("sarvam")
  ApiExceptionHandler.java   @RestControllerAdvice
```

## Contracts
```java
public interface SpeechPort {
    Transcript transcribe(byte[] audio, String filename, String contentType, String languageCode);
}
public record Transcript(String transcript, String languageCode, String requestId) {}
```

Endpoint:
```
POST /api/speech/transcribe
Content-Type: multipart/form-data
  audio     (file, required)
  language  (text, optional, default "unknown"; allowed: hi-IN, mr-IN, gu-IN, en-IN, unknown)

200 OK
{ "transcript": "doodh 6 packet 22, bread 4 loaf 30", "languageCode": "hi-IN", "requestId": "..." }

400 { "error": "audio is required" }            missing/empty file, unsupported language
413 { "error": "audio too large" }              over limit
502 { "error": "speech service failed" }        Sarvam error or timeout (no internal details)
```

## Configuration
Add to `application.yml`:
```yaml
bachat:
  mode: ${BACHAT_MODE:live}        # live | demo
sarvam:
  api-key: ${SARVAM_API_KEY:}
  base-url: https://api.sarvam.ai
  model: saaras:v4
  keyterms: [doodh, bread, packet, loaf, dozen, anda, dahi, paneer]
spring:
  servlet:
    multipart:
      max-file-size: 5MB           # default is 1MB, too small for audio
      max-request-size: 5MB
```
`SarvamProperties` binds `sarvam.*`. In `live` mode fail fast at startup if the key is blank.

## SarvamSpeechClient behaviour
- Use Spring `RestClient` with connect timeout 5 s and read timeout 20 s.
- Build the multipart body with `MultiValueMap<String,Object>`; the file part must be a `ByteArrayResource`
  that overrides `getFilename()` and carries the right content type (browser sends `audio/webm`).
- Send `model`, `language_code`, and `keyterms` as Sarvam documents them (check the docs for how a list is encoded).
- Map the response to `Transcript`. If Jackson 3 is in use (Boot 4), `@JsonProperty` for snake_case fields still comes
  from `com.fasterxml.jackson.annotation`; confirm it compiles, or use a `Map`.
- On HTTP error or timeout throw `SpeechException` with a safe message. Log status code and `request_id` only.

## StubSpeechClient (demo mode)
Returns a fixed transcript `"doodh 6 packet 22, bread 4 loaf 30"`, language `hi-IN`, requestId `stub`.
This is the offline fallback if venue Wi-Fi or Sarvam fails. Enabled by `BACHAT_MODE=demo`.

## Controller rules
- Reject empty file, reject size > 5 MB, reject content types other than audio/*.
- Reject `language` not in the allowed list.
- Keep the controller thin: validate, call `SpeechPort`, return JSON.

## Tests
1. Unit: `SarvamSpeechClient` against a mock HTTP server (MockRestServiceServer or WireMock) for success, 4xx, 5xx, timeout.
2. Web slice: `SpeechController` with a mocked `SpeechPort` for 200, 400 (no file), 400 (bad language).
3. Manual (needs real key): open `http://localhost:8080/recorder.html`, record 5-10 s, press Send, expect a transcript.
   Try Hindi, Marathi and mixed Hinglish with numbers and note accuracy in the README.

## Definition of done
- App starts in `live` mode with the key set, and in `demo` mode with no key and no database variables
  (demo mode needs an H2 profile; if that is not set up yet, skip this check and note it).
- Recorder page returns a real transcript.
- No secret appears in logs, source or git.
- `./mvnw test` passes.

## Known risks to check while building
- Safari records `audio/mp4`, which Sarvam may not accept. Use Chrome for the demo, or convert server side later.
- Browser microphone access works on `localhost` and on HTTPS only (Cloud Run is HTTPS).
- Numbers may come back as words or digits; the later parsing step must handle both.
