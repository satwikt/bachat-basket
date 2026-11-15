# Implementation spec: Voice listing to offers (Bachat Basket)

Date: 2026-11-15
Builds on: `docs/sarvam-stt-implementation.md` (speech-to-text is done; this is the "Pack Reader" step that spec left out).
Implementer: Claude Code CLI. Work through the phases in order (section 12). Each phase should compile and pass its tests before the next starts.

**Branch:** work on `feature/voice-listing-and-customer-ask` only. It was created from `frontend` and holds this spec and `docs/customer-ask-implementation.md`; both specs live on this branch and nowhere else. Run `git branch --show-current` before each phase and stop if it is not this branch. Do not commit to `frontend` or `main`. Commit at the end of each phase with a message naming the phase. Do not push, open a PR, or merge unless the user asks.

## 1. Goal

A shopkeeper speaks a listing. The app turns it into rows in an internal table the shopkeeper can check and edit. When the shopkeeper confirms, the rows become live offers that shoppers see.

```
Shopkeeper speaks: "doodh 6 packet 22, bread 4 loaf 30, tamatar 5 kg 15"
        |
        v  POST /api/speech/transcribe            (already built)
Transcript: "doodh 6 packet 22, bread 4 loaf 30, tamatar 5 kg 15"
        |
        v  POST /api/shops/{shopId}/listings/parse   (NEW, pure parser, no DB write)
Internal table (merchant side, editable):
  Item   | Qty | Unit   | Offer ₹ | Original ₹ | % off | Flags
  Milk   |  6  | packet |   22    |    28      |  21%  |
  Bread  |  4  | loaf   |   30    |    --      |   --  | ORIGINAL PRICE MISSING
  Tomato |  5  | kg     |   15    |    30      |  50%  |
        |
        v  shopkeeper fixes flags, taps "List now"
        |
        v  POST /api/shops/{shopId}/listings           (NEW, saves ACTIVE listings)
        |
        v  GET  /api/shops/{shopId}/listings          (NEW)
Shopper side (Customer page):
  Milk     6 packets   ₹22  (was ₹28)   21% off   ends 9:00 pm
  Tomato   5 kg        ₹15  (was ₹30)   50% off   ends 9:00 pm
```

Terms used below:
- **Offer price**: the price the shopkeeper speaks. It is per unit (see decision D1).
- **Original price**: the usual or MRP price, shown struck through to shoppers. It is not spoken. It comes from a reference table and can be edited (decision D2).

## 2. Scope

In scope:
- Deterministic transcript parser (Java, no LLM) that returns structured lines and issues.
- Reference price table with seed rows for the two examples in this spec.
- Listing save and read endpoints, plus "mark all sold out".
- Merchant page: replace the dummy parsing in `web/src/pages/Merchant.jsx` with the real parse call and an editable internal table. Wire "List now" and "Mark all sold out" to the API.
- Customer page: replace hard-coded `SHOPS` data in `web/src/pages/Customer.jsx` with fetched listings.
- Unit, controller and repository tests. Manual end-to-end check.

Out of scope (later):
- Authentication and shop login. Shop id is hard-coded to `1` (Sharma General Store) for the MVP.
- Building the subscription backend inside this spec. Follow is server-side and specified in `docs/customer-ask-implementation.md` section 5.6; phase 5 here only calls those endpoints (they are built in that spec's phase 0, before this spec's phase 5).
- Photo reading (`source = PHOTO`). The enum value exists in the schema; do not implement it.
- LLM-based parsing fallback. Only add it if the deterministic parser fails on real recordings (see section 13).
- Storing audio or transcripts beyond the `raw_phrase` column.

## 3. Worked examples (these are the acceptance tests)

Input transcript -> expected parsed lines. Seed data in section 5 makes these results exact.

| Transcript segment | Item | Qty | Unit | Offer ₹ | Original ₹ | Issues |
|---|---|---|---|---|---|---|
| `doodh 6 packet 22` | Milk | 6 | packet | 22 | 28 (REFERENCE) | none |
| `bread 4 loaf 30` | Bread | 4 | loaf | 30 | null | ORIGINAL_PRICE_MISSING (warning) |
| `tamatar 5 kg 15 rs original price 30 rs` | Tomato | 5 | kg | 15 | 30 (MANUAL, spoken) | none |
| `doodh 6 22` | Milk | 6 | packet (default from reference) | 22 | 28 (REFERENCE) | NO_UNIT (warning) |
| `doodh 6 packet 30` | Milk | 6 | packet | 30 | 28 (REFERENCE) | PRICE_ABOVE_ORIGINAL (blocking) |
| `doodh 0 packet 22` | Milk | 0 | packet | 22 | 28 | ZERO_QUANTITY (blocking) |
| `hmm achha` | null | null | null | null | null | UNPARSED (blocking, raw text kept for manual fix) |

Input variants the parser must also accept: `Doodh 6 Packet 22 Rs`, `doodh six packet twenty two`, `doodh 6 packet ₹22`, `doodh 6 packet 22/-`, and newline-separated lines instead of commas.

## 4. Existing code you must read first

- `src/main/java/com/twothreads/bachat/speech/SpeechController.java`: the transcribe endpoint. Do not change its contract.
- `src/main/java/com/twothreads/bachat/config/ApiExceptionHandler.java`: error body is `{"error": "..."}`. Reuse this shape.
- `src/main/resources/db/migration/V1__init.sql`: `shop`, `listing`, `subscription`, `customer`. The `listing` table already has `source`, `sell_today`, `ends_at`, `status`.
- `src/main/resources/application.yml`: `spring.jpa.hibernate.ddl-auto: validate`. Every JPA entity must match the Flyway schema exactly, or startup fails.
- `web/src/pages/Merchant.jsx`: current dummy parse (`setItems([...])` with hard-coded rows), "List now" only sets local state, "Mark all sold out" only resets local state.
- `web/src/pages/Customer.jsx`: `SHOPS` constant with sample data. Keep the layout and classes; replace the data source.
- `web/vite.config.js`: already proxies `/api` to `http://localhost:8080`. No change needed.
- The repo has no JPA entities or repositories yet. You will create them.

Java 21, Spring Boot 4.1.x, base package `com.twothreads.bachat`. Use `java.time` and time zone `Asia/Kolkata` for all time logic.

## 5. Data model

### 5.1 Migration `V2__listing_voice_fields.sql`

```sql
-- Original (usual/MRP) price per unit, shown struck through to shoppers. Nullable: may be unknown.
alter table listing add column original_price_rupees numeric(10, 2)
    check (original_price_rupees is null or original_price_rupees >= 0);

-- Where the original price came from: REFERENCE (catalog), MANUAL (typed or spoken by merchant), NULL if unknown.
alter table listing add column original_price_source varchar(20)
    check (original_price_source in ('REFERENCE', 'MANUAL'));

-- What the shopkeeper actually said for this row (audit and debugging). Never log audio.
alter table listing add column raw_phrase varchar(300);

-- Canonical item name, used to look up reference prices and to group items.
alter table listing add column item_key varchar(60);

-- Seed shop for the MVP (no explicit id: the column is generated; a fresh DB gives it id 1).
-- Coordinates are used later by the customer "Ask" spec (placeholder Mumbai location).
insert into shop (name, owner_name, closing_time, latitude, longitude)
values ('Sharma General Store', 'Sharma', '21:00:00', 19.0760, 72.8777);

-- Catalog of usual prices per canonical item and unit. Merchants never edit this table in the MVP.
create table reference_price (
    id              bigint generated always as identity primary key,
    item_key        varchar(60) not null,
    unit            varchar(30) not null,
    usual_price_rupees numeric(10, 2) not null check (usual_price_rupees > 0),
    constraint uq_reference_price unique (item_key, unit)
);

-- Seed rows. The Milk and Tomato values come from the business example. Other rows: fill with real prices.
insert into reference_price (item_key, unit, usual_price_rupees) values
    ('MILK',   'packet', 28),
    ('TOMATO', 'kg',     30);
```

Use the same SQL on H2 (demo profile and tests), in PostgreSQL mode. Quantity stays `integer` (V1): the MVP supports whole quantities only, so "1.5 kg" is not parsed (it becomes UNPARSED and the shopkeeper types it). Do not alter the `quantity` column.

### 5.1a Run profiles (required; neither exists today)

`application.yml` currently needs `DB_URL`, `DB_USER` and `DB_PASSWORD`, so the app and `@SpringBootTest` cannot start without a database. Add:
- `src/main/resources/application-demo.yml`: datasource `jdbc:h2:mem:bachat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, user `sa`, empty password, Flyway enabled.
- `src/test/resources/application.yml` (or `.properties`): the same H2 datasource plus `bachat.mode: demo`, so `./mvnw test` runs with no environment variables.
- Demo run command: `SPRING_PROFILES_ACTIVE=demo BACHAT_MODE=demo ./mvnw spring-boot:run` (PowerShell: set both env vars first).

Phase 1 must end with `./mvnw test` green. If `BachatApplicationTests` still fails, fix the test config, not the test.

### 5.2 JPA entities and repositories

- `Shop` (maps `shop`). Fields: `id`, `name`, `closingTime` (`LocalTime`), `latitude` and `longitude` (`Double`, nullable). The customer "Ask" spec needs the coordinates.
- `Listing` (maps `listing`). All columns from V1 plus the four new ones. `source` enum `ListingSource {MANUAL, SPEECH, PHOTO}`. `status` enum `ListingStatus {ACTIVE, SOLD_OUT, EXPIRED}`. Money as `BigDecimal`.
- `ReferencePrice` (maps `reference_price`).
- `ListingRepository`, `ReferencePriceRepository`, `ShopRepository` (Spring Data JPA).

Verify that `ddl-auto: validate` passes against the migrated schema (run the app once with the demo profile).

Entity rules (avoid lazy-loading and validate failures):
- `Listing.shopId` is a plain `Long` column, not `@ManyToOne`.
- `created_at` is `insertable=false, updatable=false` (database default).
- `quantity` is `Integer`; money fields are `BigDecimal`; `ends_at` is `OffsetDateTime` or `Instant`.
- `open-in-view` is false, so services map entities to DTOs inside the transaction.

Spring Boot 4 test notes: `@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`, `@DataJpaTest` in `org.springframework.boot.data.jpa.test.autoconfigure`, use `@MockitoBean` (not `@MockBean`), and Jackson 3 is `tools.jackson`. Check what the existing tests in `src/test` import and copy that style.

## 6. Parser (pure Java, no Spring, no DB)

Package `com.twothreads.bachat.listing`. Classes:
- `VoiceListingParser` with method `List<ParsedLine> parse(String transcript, Map<String, List<ReferencePrice>> reference)`, keyed by `itemKey`. Pass the reference data in so the parser stays pure and testable. Lookup by `(itemKey, unit)` scans the list; "default unit" applies only when the list has exactly one row.
- `ParsedLine` (record): `rawPhrase`, `itemKey` (nullable), `itemName`, `quantity` (Integer, nullable), `unit`, `offerPrice` (nullable), `originalPrice` (nullable), `originalPriceSource` (`REFERENCE` | `MANUAL` | null), `issues` (List of `Issue`).
- `Issue` (enum): `UNPARSED`, `MISSING_PRICE`, `ZERO_QUANTITY`, `PRICE_ABOVE_ORIGINAL`, `NO_UNIT`, `ORIGINAL_PRICE_MISSING`, `UNKNOWN_ITEM`.
- `SynonymDictionary`: maps words to a canonical `itemKey` and display name. Keep it in a Java class or a small YAML/JSON resource. API: `Optional<Item> lookup(String normalisedWord)` and `Set<String> keys()`. It must cover, at minimum, keys `MILK`, `BREAD`, `TOMATO`, `EGG`, `CURD`, `PANEER`, `ONION`, each with: the English word and its plural (`milk`, `egg`, `eggs`, `tomato`, `tomatoes`), Hindi/Marathi romanised words (`doodh`, `dudh`, `tamatar`, `anda`, `dahi`, `pyaz`), and Devanagari forms (`दूध`, `ब्रेड`, `टमाटर`, `अंडा`, `दही`, `पनीर`, `कांदा`). The customer "Ask" spec reuses this class.
- `NumberWords`: maps `ek..bees` and English `one..twenty` to digits. Keep it small.

### 6.1 Steps

1. **Normalise**: trim, lowercase. Delete currency tokens entirely: `₹`, `rs.`, `rs`, `rupees`, `rupee`, `/-` (so `₹22`, `22rs` and `22/-` all become `22`). Collapse only spaces and tabs (`[ \t]+` to one space); do NOT collapse newlines, because step 2 splits on them.
2. **Split** into segments on `,`, `;`, newline, ` and `, ` aur `, ` then `. Drop empty segments. Known limitation: an item name containing "and" (for example "salt and pepper") is cut in two and becomes UNPARSED; the shopkeeper fixes it by hand.
3. **Number words to digits** (per segment, after splitting). Convert a word only when it is not the first word of the segment (the first word is the item, which protects names like `do`). Map `one..twenty`, `thirty..ninety`, and the Hindi/Marathi words `ek..das`. A tens word followed directly by a units word is summed (`twenty two` -> `22`, `bees do` is not required). Unmapped words are left alone.
4. **Match each segment** with this pattern (case-insensitive, applied to the normalised segment). Quantity is a whole number; prices may have decimals:

   ```
   ^(?<item>[\p{L}][\p{L} .'-]*?)\s+(?<qty>\d+)\s+(?:(?<unit>[\p{L}]+)\s+)?(?<price>\d+(?:\.\d+)?)(?:\s+(?:original(?:\s+price)?|mrp|was)\s+(?<orig>\d+(?:\.\d+)?))?$
   ```

   Non-match -> `ParsedLine` with `UNPARSED`, `rawPhrase` set, all other fields null. (A segment with no price, like `bread 4 loaf`, is therefore UNPARSED. `MISSING_PRICE` is only raised by the browser and server when a saved row has an empty or zero price.)

   The numbered steps below continue from here (items, units, quantity, prices).
5. **Item**: look up `item` in `SynonymDictionary`. Found -> `itemKey` and display name. Not found -> keep the spoken word as `itemName` and add `UNKNOWN_ITEM` (warning). Unit `packets`, `pack`, `pkt` -> `packet`. `kilo`, `kilogram` -> `kg`. `gram` -> `g`. `litre`, `ltr` -> `l`. `pieces`, `pc`, `nang` -> `pc`. Unit missing -> default from reference (only when that `itemKey` has exactly one reference row) and add `NO_UNIT` (warning). If there is no reference row, default to `pc`.
6. **Quantity**: must be > 0, else `ZERO_QUANTITY` (blocking).
7. **Offer price**: `price` must be > 0 else `MISSING_PRICE` (blocking).
8. **Original price**:
   - If spoken (`orig` group present) -> use it, `originalPriceSource = MANUAL`.
   - Else if reference row exists for `(itemKey, unit)` -> use `usual_price_rupees`, source `REFERENCE`.
   - Else -> null, add `ORIGINAL_PRICE_MISSING` (warning).
9. **Price check**: if `originalPrice` is set and `offerPrice >= originalPrice` -> `PRICE_ABOVE_ORIGINAL` (blocking). Use `>=` so an offer equal to the original is also flagged.

Blocking issues: `UNPARSED`, `MISSING_PRICE`, `ZERO_QUANTITY`, `PRICE_ABOVE_ORIGINAL`. Warnings: `NO_UNIT`, `ORIGINAL_PRICE_MISSING`, `UNKNOWN_ITEM`. A line with only warnings can be saved. A line with a blocking issue cannot.

Prices are `BigDecimal`, never `double`. Scale prices to 2 decimals (`setScale(2, HALF_UP)`) before returning or saving. Tests compare with `compareTo`, not `equals`.

### 6.2 Sarvam output risk

Sarvam may return Hindi words in Devanagari (`दूध`) when language is `hi-IN` or `unknown`. Test this on a real recording first. If it happens, either:
- add a Devanagari-to-key entry to `SynonymDictionary` (`दूध` -> `MILK`, `ब्रेड` -> `BREAD`, `टमाटर` -> `TOMATO`), or
- use the transliteration option if Sarvam documents one (check docs.sarvam.ai; do not guess the parameter name).

Record the choice in the README.

## 7. Backend API

Base path `/api/shops/{shopId}`. `shopId` is a path variable. No auth in MVP. Unknown shop -> 404.

### 7.1 `POST /api/shops/{shopId}/listings/parse`

Preview only. Writes nothing.

Request:
```json
{ "transcript": "doodh 6 packet 22, bread 4 loaf 30" }
```

200 OK:
```json
{
  "lines": [
    {
      "rawPhrase": "doodh 6 packet 22",
      "itemKey": "MILK",
      "itemName": "Milk",
      "quantity": 6,
      "unit": "packet",
      "offerPrice": 22.00,
      "originalPrice": 28.00,
      "originalPriceSource": "REFERENCE",
      "issues": []
    }
  ],
  "blocking": false
}
```

`blocking` is true if any line has a blocking issue.

400 `{"error": "transcript is required"}` for blank input.

### 7.2 `POST /api/shops/{shopId}/listings`

Saves the merchant's confirmed table. Creates `ACTIVE` listings with `source = SPEECH`.

Request:
```json
{
  "lines": [
    {
      "rawPhrase": "doodh 6 packet 22",
      "itemKey": "MILK",
      "itemName": "Milk",
      "quantity": 6,
      "unit": "packet",
      "offerPrice": 22.00,
      "originalPrice": 28.00,
      "originalPriceSource": "REFERENCE",
      "sellToday": false
    }
  ]
}
```

Rules:
- The server re-checks blocking rules: offer > 0, quantity > 0, offer < original when original is set, name not blank. Do not trust the client.
- `ends_at` rules, all in `Asia/Kolkata`:
  1. `closeAt` = today's `closing_time` (23:59 if null). If `closeAt` is already past when saving (for example a night-time test), use tomorrow's `closing_time` instead.
  2. If `expiry_date` is set and `sellToday` is false: `expiryEnd` = `expiry_date` at 23:59:59. `ends_at` = the earlier of `closeAt` and `expiryEnd`. If that is not in the future, return 422 "item already expired".
  3. If `sellToday` is true, ignore any `expiry_date` and use `closeAt`.
- Duplicates: the API allows them (listing Milk twice creates two rows). The UI disables "List now" after the first success so a double-click cannot create duplicates.
- Expiry date is not in the table yet. Accept it as an optional field per line, but the UI does not have to show it in the MVP.
- The server ignores the client's `itemKey` and re-derives it from `itemName` via `SynonymDictionary.lookup` on save (null if unknown). This keeps `listing.item_key` correct after the merchant edits an item, and lets the customer "Ask" search find the row.
- Save the whole batch in one transaction. Any failure rolls back everything.

201 Created: `{"created": 2, "listingIds": [101, 102]}`
422 Unprocessable Entity: `{"error": "line 2 has a blocking issue: PRICE_ABOVE_ORIGINAL"}`
404: unknown shop.

### 7.3 `GET /api/shops/{shopId}/listings`

Shopper-facing. Returns only `ACTIVE` listings whose `ends_at` is in the future, plus the shop's name and closing time.

200 OK:
```json
{
  "shop": { "id": 1, "name": "Sharma General Store", "closingTime": "21:00" },
  "listings": [
    {
      "id": 101,
      "itemName": "Milk",
      "quantity": 6,
      "unit": "packet",
      "offerPrice": 22.00,
      "originalPrice": 28.00,
      "discountPercent": 21,
      "endsAt": "2026-11-15T21:00:00+05:30"
    }
  ]
}
```

`discountPercent` = (original - offer) / original * 100 rounded `HALF_UP` to a whole number, using `BigDecimal`; null when original is null. Compute it on the server. Format `endsAt` in the response with the `Asia/Kolkata` offset (`atZoneSameInstant`), not UTC, because the database column returns UTC.

### 7.4 `POST /api/shops/{shopId}/listings/sold-out`

Sets every `ACTIVE` listing of the shop to `SOLD_OUT`. 200 OK: `{"updated": 2}`.

### 7.5 Error handling

Define `ShopNotFoundException` (404) and `ListingValidationException` (422) and map them in `ApiExceptionHandler`. Also map `MethodArgumentNotValidException` and `HttpMessageNotReadableException` to 400 `{"error": "..."}`, because Spring Boot 4 would otherwise return its own default body. Reject with 400 an empty or missing `lines` array, more than 50 lines, or a transcript over 2000 characters.

Add handlers to `ApiExceptionHandler` for the new exceptions. Use `{"error": "..."}` only. Never return stack traces or SQL. Log ids, not personal data.

## 8. Frontend

### 8.1 Merchant page (`web/src/pages/Merchant.jsx`)

Keep the recording and language controls as they are.

Replace the dummy logic:
1. After a successful transcribe, call `POST /api/shops/1/listings/parse` with `{transcript}`.
2. Show "We heard: <transcript>" as today.
3. Show the internal table (below) with the returned lines.
4. If the parse request fails, show the error and keep the transcript so the shopkeeper can type it. Add a textarea that sends the same parse request.

Internal table (editable). Columns:

| # | Heard | Item | Qty | Unit | Offer ₹ | Original ₹ | % off | Flags | |
|---|---|---|---|---|---|---|---|---|---|

- `Heard` shows `rawPhrase`, read-only, small text.
- `Item`, `Qty`, `Unit`, `Offer ₹`, `Original ₹` are inputs. Editing recalculates `% off` in the browser.
- `Original ₹` edits set `originalPriceSource = MANUAL`.
- `Flags`: one badge per issue. Blocking issues in red, warnings in amber. Use plain words: "Price above original", "Original price missing", "Unit missing", "Not understood".
- Row delete (✕). "Add row" button for a blank row.
- Row with a blocking issue: the row is highlighted. The browser has no reference table, so on edits it re-checks only: quantity > 0 (`ZERO_QUANTITY`), offer > 0 (`MISSING_PRICE`), offer < original when both are set (`PRICE_ABOVE_ORIGINAL`), and empty original (`ORIGINAL_PRICE_MISSING`). Editing Item or Unit clears the row's `itemKey` and the REFERENCE badge, so the original price then counts as MANUAL if it stays. The server re-checks everything in 7.2 and sets `originalPriceSource` itself (REFERENCE only if the saved original equals the reference price for the row's item and unit, otherwise MANUAL). The server is the final gate.
- "List now" is disabled while any row has a blocking issue. On click, POST to `/api/shops/1/listings`, then show "Live. Offers end at closing time." and the existing "Mark all sold out" button.
- "Mark all sold out" calls `POST /api/shops/1/listings/sold-out`, then clears the table.

Do not keep the dummy items array in the file.

Language select: default stays `unknown`. The parse call does not depend on language.

### 8.2 Customer page (`web/src/pages/Customer.jsx`)

- Replace `SHOPS` with a fetch of `GET /api/shops/1/listings` on mount. Show a loading line and an error line.
- Follow/Following uses the server-side endpoints from `docs/customer-ask-implementation.md` section 5.6 (create an anonymous customer, `GET /api/shops`, subscriptions, `PUT`/`DELETE` follow). Show all shops with a Follow toggle and listings only for followed shops. A new customer follows nothing, so show "Follow a shop to see its offers" until they follow one.
- Format: pluralise the unit when quantity > 1 ("6 packets", "5 kg" stays as is), and show `endsAt` with `toLocaleTimeString('en-IN', {hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata'})`.
- Each listing row: item name, quantity and unit, offer price in bold, original price struck through (only when present), discount badge, "ends at" time from `endsAt`.
- Shop header: name from the response, "offer ends <closingTime>".
- Empty state: "No offers right now."
- Keep the "Directions" button and the note text. Change the note to: "Prices are set by each shop. Original price is the usual price."

Use `₹` and the existing `price` / `old` CSS classes in `web/src/styles.css`.

### 8.3 Routing

No new routes. `/merchant` and `/customer` already exist and are forwarded by `SpaForwardController`.

## 9. Tests

### 9.1 Parser (`src/test/java/com/twothreads/bachat/listing/VoiceListingParserTest.java`)

Plain JUnit, no Spring. Cover every row of the worked examples in section 3, plus:
- Input variants from section 3 (case, `Rs`, `₹`, `/-`, `six` vs `6`, newlines).
- Unknown item -> `UNKNOWN_ITEM`, item kept.
- Gibberish segment -> `UNPARSED`, and the other segments still parse.
- Empty string -> empty list.
- `BigDecimal` values: `22` and `22.50` both parse exactly.

### 9.2 Controller (`ListingControllerTest`, `@WebMvcTest`)

- Parse: 200 with lines; 400 for blank transcript; 404 for unknown shop (mock the service).
- Create: 201 for valid lines; 422 when a blocking line is sent; 422 message names the line.
- Sold out: 200 with count.
- Listing read: 200 with `discountPercent` computed.

### 9.3 Repository / service (`@DataJpaTest` on H2)

- `ends_at` uses the earlier of closing time and expiry end of day.
- Listing read excludes `SOLD_OUT`, `EXPIRED`, and listings whose `ends_at` is past.
- Batch save rolls back when one line fails.

### 9.4 Manual end-to-end

1. `BACHAT_MODE=demo` (stub transcript `doodh 6 packet 22, bread 4 loaf 30`). Open `/merchant`. Expect the table with Milk (original 28, from reference) and Bread (original missing, warning). Fix Bread's original price, tap "List now".
2. Open `/customer` in another tab (same browser). Follow "Sharma General Store" first (a new customer follows nothing). Expect Milk and Bread with strike-through prices. Tap "Mark all sold out" on Merchant. Refresh Customer. Expect "No offers right now."
3. `BACHAT_MODE=live` with `SARVAM_API_KEY`. Speak the three example items in Chrome. Record which words Sarvam returned (digits, words, Devanagari) in the README.

### 9.5 Commands

```
./mvnw test
cd web && npm install && npm run build
```

## 10. Definition of done

- `./mvnw test` passes, including all worked examples in section 3.
- App starts in both `live` and `demo` modes. `ddl-auto: validate` passes.
- `npm run build` in `web/` passes.
- Merchant page: real parse, editable table, blocking rules enforced in UI and server, "List now" saves to DB.
- Customer page: shows saved listings from the API, with original price struck through and discount shown. No hard-coded shop data remains in `Customer.jsx`.
- No API key, audio or personal data in logs or source.
- README has a short "Voice listing" section: how to run, which Sarvam output format was observed.

## 11. Decisions and open questions

The defaults below are what the implementation uses. D1 and D2 were confirmed by the business owner on 2026-11-15. The rest stand unless changed.

| # | Question | Default used | Why |
|---|---|---|---|
| D1 | Is the spoken price per unit or for the whole quantity? | **Per unit** (22 per packet, 15 per kg) | "Tomato 5 kg 15" for the whole lot is implausible |
| D2 | Where does the original price come from? | **Reference table, merchant can override or speak it** | It is not in the speech example |
| D3 | Can an offer equal the original price? | **No, blocked** | Selling at or above MRP is a problem for packaged goods |
| D4 | Is "Follow" server-side? | **Local state in this spec; superseded by `docs/customer-ask-implementation.md` section 5.6, which moves Follow to the `subscription` table** | The customer Ask searches followed shops only |
| D5 | Is the shop fixed? | **Shop id 1, hard-coded** | Single-shop demo |
| D6 | Expiry date input? | **Not in UI for MVP; optional in API** | Speech rarely includes dates |

## 12. How to run this with Claude Code

Start a Claude Code session in the repo root. Give it one phase at a time so each one is reviewed before the next. Suggested prompts:

1. "Read `docs/listing-from-voice-implementation.md` and `docs/sarvam-stt-implementation.md`. Do phase 1 only: migration V2, entities, repositories, and verify `ddl-auto: validate`."
2. "Do phase 2: the parser and its tests (section 6 and 9.1). Do not touch controllers yet. Run `./mvnw test`."
3. "Do phase 3: the endpoints in section 7 with the tests in 9.2 and 9.3. Keep the controller thin."
4. "Do phase 4: `Merchant.jsx` per section 8.1. Check `vite.config.js` proxy first."
5. "Do phase 5 (only after the customer spec's phase 0 backend exists): `Customer.jsx` per section 8.2. Then run section 9.4 steps 1 and 2 and report what you saw."

Phases in this spec:
1. Migration, entities, repositories
2. Parser and unit tests
3. Endpoints and tests
4. Merchant page
5. Customer page and manual check

Before starting phase 1, run `git status` and commit or stash any unrelated changes. Do not commit on your own; review the diff first.

## 13. Risks and checks

- **Parser accuracy on real speech.** Sarvam may return words, digits, or Devanagari. Phase 2 must be tested with recordings, not only with typed strings. If accuracy is too low, the next step is an LLM parse fallback behind the same `parse` endpoint. That is a separate spec.
- **Spoken numbers and Hinglish.** "Chhe" and "das" are in the number map. Anything else goes to `UNPARSED` so the shopkeeper can type it. Do not guess.
- **Time zone.** All `ends_at` logic uses `Asia/Kolkata`. Test around midnight.
- **No auth.** Anyone with the URL can post or mark items sold out. Acceptable for the demo only.
- **Reference prices.** Only two seed rows are real (from the business example). Fill the rest with real local prices before any real use.
- **Browser mic.** Works on `localhost` and HTTPS only. Chrome recommended (see the STT spec).
