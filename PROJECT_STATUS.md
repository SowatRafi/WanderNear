# WanderNear — Project Status & Handoff

> Snapshot for resuming work in a fresh session. Read this **and** `CLAUDE.md`
> (project conventions/decisions) at the start of every session.

## What WanderNear is

A native **Android** app: an offline-first, private, on-device local guide for
travellers. You tell it what you like (typed or by voice) and an on-device AI
recommends **real** local places (food with dietary filters, temples/worship,
attractions, outdoor spots) with a short reason why. It also has a private
**Travel Journal**. Everything runs on the phone; nothing leaves it; it works in
airplane mode. Free/open tools and data only.

**Non-negotiables:** offline-first & private · free to build/run · native Android
with an Android-free portable `core/` · one generic pipeline for ANY city ·
**never hallucinate** (every recommendation grounded in a retrieved DB row).

## Status: M1–M5 + Travel Mode + M6.1–M6.3, M6.4a–d, M6.5 done ✅ (35 commits, all pushed; latest `8a0f505`)

| Milestone | Status | What it delivered |
|---|---|---|
| **M1** Data pipeline | ✅ | Python (`pipeline/`) builds `melbourne.db` from OSM Overpass + Wikipedia. Now **22,624 places** (incl. 148 hospital, 917 parking, 868 fuel, 480 shopping, 114 police; 3,858 with a suburb) + FTS. |
| **M2** App + templates + GPS | ✅ | Compose chat + preferences; loads the DB; grounded **templated** recommendations; one-shot GPS "near me" (falls back to CBD); Directions (`geo:` intent) + attribution; honest refusal on no match. **MVP success test passes offline.** |
| **M3** Travel Journal | ✅ | Private "My Trips" (separate Room `journal.db`): save places, notes, bucket list (todo/done), visit dates, photos (Photo Picker → app-private storage), anniversary reminders (WorkManager) + on-open "you're back nearby" nudge. Edit/delete everywhere with confirm. |
| **M4** On-device AI | ✅ | **LiteRT-LM 0.14.0 + Gemma 4 E2B** rewords the retrieved rows into warm prose (opt-in, temperature 0). 5-layer anti-hallucination + `GroundingCheck` validator + **16-test** adversarial suite. Template stays the guaranteed fallback. |
| **M5** Voice input | ✅ | **Vosk 0.3.75** offline STT; mic button in chat; transcribe-to-input (review-then-send). **M5.2 polish:** honest Idle→Preparing→Listening states + mic released on phrase (privacy); inline vector mic/stop icons + a listening pulse (no new dep, a11y labels, reduced-motion); graceful mic-permission handling with an "Open Settings" recovery. Verified on device. |
| **TM** Travel Mode | ✅ | Opt-in Preferences toggle → a WHILE-IN-USE location foreground service with an always-visible "Travel Mode is on" banner (no background location). **TM.1:** toggle + service + banner + Stop; START_NOT_STICKY; MainActivity self-heals a stale switch. **TM.2:** battery-friendly live location watch (~2 min / ~120 m) firing grounded "worth a visit nearby" alerts within 300 m (`CityDatabase.nearbyNotable` — real rows only, de-duped). Banner is the privacy guarantee; refuses to run if its channel is muted. |
| **M6.1** City Info card | ✅ | Home-screen card: population/currency/emergency number, with a safe Call-emergency dial (`ACTION_DIAL`, never auto-calls). Pipeline captures country + population. |
| **M6.2** Safety section | ✅ | Home-screen **"Nearest police"** card under City Info: 3 nearest police stations, each with Directions (always) + Call (only when OSM lists a phone). Generic pipeline now fetches `amenity=police` (new `safety` category) so ANY city gets them; Melbourne pack rebuilt (114 stations, 46 with phones). Verified on device. |
| **M6.3** Shopping spots | ✅ | New **`shopping`** retrieval category (markets + malls + department stores) via `amenity=marketplace` + `shop=mall\|department_store`, so ANY city gets them; Melbourne rebuilt (480 rows). **Searchable, no new card:** "shopping"/"markets" ride the existing retrieval→template/AI→cards path (Directions + Save + attribution), grounded to real rows. Also fixed a latent UX bug the tall cards exposed — the empty home screen now scrolls, so example chips are reachable. Verified end-to-end on device. |
| **M6.4** Download-a-city (a–c) | ✅ | On-device **"any city"**: the app fetches OSM/Wikipedia and builds a SQLite pack on the phone (no server; Kotlin twin of the pipeline; v1 skips enrichment). **a** `core/OsmClassifier` (pure-Kotlin classify + Overpass query, unit-tested); **b** `CityPackBuilder` (geocode→Overpass→`JsonReader`→SQLite+FTS in `filesDir/packs/`; **Geelong = 233 grounded places**; 8 review fixes); **c** multi-city storage + active-city switch (`CityDatabase` opens the active pack; `activePack` in DataStore; whole home reactively reloads; **fixes the copy-once gotcha**; 5 review fixes removing leftover Melbourne hard-codes). Verified Melbourne↔Geelong on device. **Remaining:** M6.4d download-UI, M6.4e background refresh. |
| **M6.4d** Download-a-city UI | ✅ | The real **"Download data for [city]?"** flow, as a **Cities card in Preferences** (temp dev trigger deleted). Radio list of installed packs (names read from each pack's own `city` row) + search → up to 5 real Nominatim **areas** shown with their full display name → confirm dialog → determinate progress + Cancel → auto-switch. `CityPackBuilder.find()` exposes the matches so `build()` takes a **confirmed** `Match` (no blind first-hit, no double geocode). Two bugs caught on-device: Nominatim replying in the place's own language (fixed with `Accept-Language`), and "near you" ranking from a fix 8,147 km outside the pack (fixed by `fixInCity()` → city-centre fallback). Verified on a Pixel 6 (Kyoto = 10,326 places). |
| **M6.5** Traveller home | ✅ | Home shows your **ACTUAL suburb** (on-device `nearestSuburb` from a new `place.suburb` column, 25 km guard — **no GPS leaves the phone**), a grounded **"Worth visiting nearby"** card, and a **"Daily needs near you"** card (nearest police/hospital/fuel/parking via new `health`/`fuel`/`parking` categories in BOTH the pipeline and the on-device builder). Melbourne rebuilt (22,624 places). A review caught + fixed a GPS-egress (Nominatim reverse-geocode) that violated non-negotiable #1 → reworked fully on-device. Verified on device. |

### Remaining — agreed order (owner, 2026-07-24)
1. **TM.3 — "Around you now"** (next): while Travel Mode is on, surface food / shopping / fuel / parking / local favourites / worth-visiting near the current fix, from the ACTIVE pack. All those categories already exist in `OsmClassifier`; `TravelModeService` currently only uses `nearbyNotable`, so this is mostly wiring. **Must be a digest** — ONE low-priority notification that updates in place + an in-app card; a buzz per café is unusable. **"Local hotspot" is defined as** has-a-Wikipedia-summary OR `tourism=attraction|viewpoint` OR marketplace/park/bar/pub/café, labelled "Local favourites" — OSM has no popularity data, and claiming "locals love this" would be invented (rule #5).
2. **M6.6 — Events & festivals**: Wikidata (SPARQL, CC0) annual festivals + Wikipedia REST descriptions → the existing `event` table. Plus **owner-approved exception to non-negotiable #4**: per-city council open data (CKAN/Socrata) via a **JSON data registry** (`city osm_id → portal type, base URL, dataset id, column map`) read by both the pipeline and the on-device builder, so adding a city is a data row not code; licence-gated to CC0/CC-BY. Cities with no entry still get Wikidata festivals + an honest "nothing listed". Rejected: scraping event sites, Eventbrite/Ticketmaster/Meetup (keys/paid/no-caching terms).
3. **M6.4e** — silent background refresh (WorkManager) of the active city when online. Scoped to the **active pack's area, never the live GPS fix**.
4. **M7** — Travel Journal v2: voice + video memos, and a smarter "you forgot this" bucket-list nudge when you return near a saved place.

Known gaps, deliberately deferred: no way to DELETE a downloaded pack yet; a download is tied to the Preferences screen (same limit as the AI-model download); downloaded packs have no Wikipedia summaries (v1 skips enrichment), so "Worth visiting nearby" is thinner there than in bundled Melbourne.

## Tech stack (EXACT pinned versions — don't change casually)

- **Build:** AGP **8.10.1**, Gradle **8.11.1** (wrapper), Kotlin **2.3.21**, KSP **2.3.10**, Java **17** target (runs on Android Studio's bundled JBR 21).
- **App:** `minSdk 31` (Android 12 — required by LiteRT-LM), `compileSdk`/`targetSdk 35`, package `com.wandernear`.
- **UI:** Jetpack Compose (BOM **2026.06.01**) + Material 3.
- **Data:** SQLite opened directly for the read-only city pack (FTS4); **Room 2.8.4** (via KSP) for the writable journal; **DataStore 1.1.1** for preferences.
- **On-device AI:** `com.google.ai.edge.litertlm:litertlm-android:0.14.0`, model `gemma-4-E2B-it.litertlm` (~2.6 GB, Apache-2.0, ungated) from HF `litert-community/gemma-4-E2B-it-litert-lm`.
- **Voice:** `com.alphacephei:vosk-android:0.3.75` + `vosk-model-small-en-us-0.15` (~40 MB) bundled in `app/src/main/resources/` (Java resources, NOT assets).
- **Also:** Coil 2.7.0 (photos), WorkManager 2.9.1 (reminders), JUnit 4.13.2 (unit tests).
- Data pipeline: Python 3 + `requests` (only dep).

## Repo layout

```
pipeline/                     Python data pipeline (run on PC): fetch_osm.py → enrich_wikipedia.py → build_db.py → query_demo.py
                              schema.sql = the pack schema (single source; copied to app assets)
app/src/main/
  assets/melbourne.db         bundled city data pack (4.9 MB, 22,624 places)
  assets/schema.sql           same schema, used by the ON-DEVICE pack builder
  resources/vosk-...zip       bundled voice model (40 MB, Java resource)
  java/com/wandernear/
    core/        PURE Kotlin, no Android imports — portable
      model/       Place, LatLng, CityInfo, CountryFacts, UserPreferences
      retrieval/   QueryParser (words → SearchSpec)
      response/    Recommender (templates + AI prompt), GroundingCheck (anti-hallucination guard)
      pack/        OsmClassifier — classify + Overpass query, SHARED by pipeline parity + on-device builder
    data/        CityDatabase (reads the ACTIVE pack; nearestSuburb/nearestEssentials/nearbyNotable),
                 CityPackBuilder (on-device "download a city"), PreferencesRepository (incl. activePack),
                 LocationProvider, journal/ (Room)
    ai/          ModelManager (LLM download), LlmEngine (LiteRT-LM wrapper)
    voice/       VoiceRecognizer (Vosk wrapper)
    reminders/   Notifier + JournalReminders (WorkManager)
    travel/      TravelModeService (WHILE-IN-USE location foreground service)
    ui/          ChatScreen (traveller home + chat), PreferencesScreen, MyTripsScreen,
                 AiSettingsSection, TravelModeSection, CitiesSection (switch/add a city)
  test/          JVM unit tests (GroundingCheckTest, QueryParserTest, RecommenderTest, OsmClassifierTest)
CLAUDE.md                     conventions, decisions, milestone log
PROJECT_STATUS.md             this file
NEXT_SESSION_PROMPT.md        copy-paste starter prompt for a fresh session
```

## How to build & run

**Use PowerShell for Gradle/adb, NOT Git Bash** — the MSYS environment can break native tools (aapt2). SDK: `C:/Users/sowad/AppData/Local/Android/Sdk`. Device: a **Pixel 6** (Android 12+) connects over USB.

```powershell
Set-Location "C:\Users\sowad\Documents\WanderNear"
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug                 # build
.\gradlew.bat :app:testDebugUnitTest             # run the grounding/trick tests
```
Install/launch:
```
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.wandernear/.MainActivity
```

**On-device models:**
- **Voice** model is bundled in the APK (offline from install).
- **LLM** model (~2.6 GB) is downloaded on first use via Preferences → "Download AI model", OR side-loaded. It is already present in the app's private storage on the connected Pixel 6 (survives `install -r`). To re-side-load: `adb push <model> /data/local/tmp/…` then `adb shell run-as com.wandernear cp … files/models/gemma-4-E2B-it.litertlm`.

## Architecture & key decisions

- **SQLite is the only source of truth.** Retrieval = plain SQL (FTS + structured filters + distance). If 0 rows → fixed refusal, the AI is **never called**.
- **Never-hallucinate (5 layers):** SQL retrieval → refuse-on-empty → grounded prompt (model sees ONLY retrieved rows' structured fields, never a place's free-text summary) → hard facts printed from the row (cards) → `GroundingCheck` rejects any AI reply naming a non-retrieved place and falls back to the template. Covered by JVM unit tests.
- **Journal is a SEPARATE database** (`journal.db`) from the city pack, so refreshing a city can never delete memories; each saved place snapshots its own name+coords.
- **`core/` has no Android imports** (portable). UI = MVVM-lite with Compose state + repositories; no DI framework.
- **AI/voice are opt-in and degrade gracefully** — the app is fully functional on templates + typing without either model.

## Gotchas learned (save future pain)

- **AAPT2 "Unexpected error during link" with empty output** = a **corrupted Gradle transforms cache**. Fix: `Remove-Item -Recurse -Force "C:\Users\sowad\.gradle\caches\8.11.1\transforms"` then rebuild. (It is NOT your code.)
- **Large bundled files:** AAPT2 crashes linking a big `assets/` file, so the 40 MB voice model lives in `src/main/resources/` (read via classloader, unzipped to `filesDir`).
- **LiteRT-LM ships Kotlin 2.3 metadata** — that forced the whole toolchain up (Kotlin 2.3.21, KSP 2.3.10, Room 2.8.4, AGP 8.10.1, Compose 2026) and `minSdk 31`.
- **Run one shell** (PowerShell) for builds; don't mix with Git Bash `./gradlew`.
- Pixel 6 (Tensor G1, 2021): first LLM load ~50 s, then ~8–18 tok/s. Voice model is small — best for short, clear phrases.
- **Bundled-pack "copy-once" — FIXED in M6.5.** The pack used to be copied assets→`filesDir` only when absent, so a rebuilt `melbourne.db` never reached an existing install (and after M6.5 added a `suburb` column, an old pack would have *crashed* the home screen). `CityDatabase` now writes a `melbourne.db.version` marker and re-installs the pack whenever `BUNDLED_PACK_VERSION` differs. ⚠️ **Bump `CityDatabase.BUNDLED_PACK_VERSION` every time you rebuild the bundled pack**, or the new one won't ship to existing installs. (`nearestSuburb` also degrades to null on an older pack instead of throwing.) Manual override if ever needed: `adb shell run-as com.wandernear rm -f files/melbourne.db` then relaunch — unlike `pm clear`, this does NOT wipe the side-loaded 2.6 GB LLM in `files/models/`.
- 🔒 **PRIVACY RULE (learned the hard way).** NEVER send the user's GPS off the device. "Which suburb am I in" is derived ON-DEVICE from the pack's `place.suburb` (`CityDatabase.nearestSuburb`). An earlier attempt reverse-geocoded the user's exact coordinates via Nominatim — an adversarial review caught it as a direct violation of non-negotiable #1 before it shipped. Sending a user-typed *city name* to geocode a pack is fine; sending their *position* is not.
- **Kotlin block comments NEST.** A `/*` inside a KDoc — e.g. writing a path like `pipeline/` + `*.py` — opens a nested comment and swallows the rest of the file ("Unclosed comment" at EOF). Avoid `/*` inside comment text.
- **Nominatim answers in the PLACE's own language.** Searching "Kyoto" returns `京都市`, which we'd store as the city's name and show on every heading. Send an `Accept-Language` header (we use the phone's locale) on any Nominatim request. Overpass is unaffected — place `name` tags stay local (correct: that's what's on the street sign).
- **"Near you" is a lie once packs can be anywhere.** With a far-away pack active, ranking by the real fix gave "Daily needs near you … 8,147 km away" and an empty "Worth visiting nearby". `ChatScreen.fixInCity()` drops the fix beyond `AWAY_FROM_CITY_KM` (100 km) so both the cards and the chat fall back to the city centre. Add the same guard to anything new that ranks by distance.
- **PowerShell `>` CORRUPTS binary output.** `adb exec-out screencap -p > x.png` writes a BOM + re-encodes → an unreadable PNG. Use `adb shell screencap -p /sdcard/x.png` then `adb pull`.
- **Nominatim's top hit for a city is often a NODE**, and a node can't scope an Overpass `area(...)` query. Ask for several results and take the first `relation`/`way` — this is what makes "any city" actually work (plain "Geelong" fails otherwise).
- **`schema.sql` has inline `--` comments containing `;`** — strip inline comments BEFORE splitting on `;`, or SQLite throws `incomplete input (code 1)` when applying the schema on-device.
- **SQLite leaves a `<db>-journal` sidecar.** When publishing or cleaning up a pack, delete `-journal` / `-wal` / `-shm` alongside the `.db`, or you orphan files.
- **Overpass rate-limits repeated queries from one IP.** The mirror retry + backoff handles it, but a rebuild can stall for a few minutes — that's throttling, not a hang.
- **`adb` is not on PATH** — use `C:\Users\sowad\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **A background review that reports "0 confirmed" may be incomplete.** If verify agents fail (e.g. session limit), findings come back with `verdict: null` — check for those and verify them yourself; a HIGH-severity crash was found exactly that way.

## Conventions (from CLAUDE.md)

- **Plan first, get approval, then build** — for every feature/change. Small steps, thorough comments, plain-English explanations.
- **Always use the `ponytail` skill** (simplest solution) on coding and **`ui-ux-pro-max`** on UI.
- **Commit + push at each verified step**, clear messages. **All commits authored by the owner alone** — never add a `Co-Authored-By: Claude` trailer or "Generated with Claude Code" line.
- Git remote: https://github.com/SowatRafi/WanderNear.git (branch `main`).

## Verify it's all there (fresh session)

```powershell
git log --oneline        # 36 commits, latest = M6.4d docs (code = 8a0f505)
git status               # clean
```

> **Note:** a `ruflo` MCP server may be connected in your session (agent/swarm/memory
> tooling). It's optional — the app is built with native tools + the `ponytail` and
> `ui-ux-pro-max` skills. Its scratch dirs (`.claude-flow/`, `.claude/proven-config*`)
> and `.claude/settings.local.json` are git-ignored.
