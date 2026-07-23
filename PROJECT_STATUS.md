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

## Status: M1–M5 + Travel Mode + M6.1–M6.3 + M6.4a–c done ✅ (32 commits, all pushed; latest `06b5b8a`)

| Milestone | Status | What it delivered |
|---|---|---|
| **M1** Data pipeline | ✅ | Python (`pipeline/`) builds `melbourne.db` from OSM Overpass + Wikipedia. Now **20,693 places** (incl. 480 shopping, 114 police) + FTS. |
| **M2** App + templates + GPS | ✅ | Compose chat + preferences; loads the DB; grounded **templated** recommendations; one-shot GPS "near me" (falls back to CBD); Directions (`geo:` intent) + attribution; honest refusal on no match. **MVP success test passes offline.** |
| **M3** Travel Journal | ✅ | Private "My Trips" (separate Room `journal.db`): save places, notes, bucket list (todo/done), visit dates, photos (Photo Picker → app-private storage), anniversary reminders (WorkManager) + on-open "you're back nearby" nudge. Edit/delete everywhere with confirm. |
| **M4** On-device AI | ✅ | **LiteRT-LM 0.14.0 + Gemma 4 E2B** rewords the retrieved rows into warm prose (opt-in, temperature 0). 5-layer anti-hallucination + `GroundingCheck` validator + **16-test** adversarial suite. Template stays the guaranteed fallback. |
| **M5** Voice input | ✅ | **Vosk 0.3.75** offline STT; mic button in chat; transcribe-to-input (review-then-send). **M5.2 polish:** honest Idle→Preparing→Listening states + mic released on phrase (privacy); inline vector mic/stop icons + a listening pulse (no new dep, a11y labels, reduced-motion); graceful mic-permission handling with an "Open Settings" recovery. Verified on device. |
| **TM** Travel Mode | ✅ | Opt-in Preferences toggle → a WHILE-IN-USE location foreground service with an always-visible "Travel Mode is on" banner (no background location). **TM.1:** toggle + service + banner + Stop; START_NOT_STICKY; MainActivity self-heals a stale switch. **TM.2:** battery-friendly live location watch (~2 min / ~120 m) firing grounded "worth a visit nearby" alerts within 300 m (`CityDatabase.nearbyNotable` — real rows only, de-duped). Banner is the privacy guarantee; refuses to run if its channel is muted. |
| **M6.1** City Info card | ✅ | Home-screen card: population/currency/emergency number, with a safe Call-emergency dial (`ACTION_DIAL`, never auto-calls). Pipeline captures country + population. |
| **M6.2** Safety section | ✅ | Home-screen **"Nearest police"** card under City Info: 3 nearest police stations, each with Directions (always) + Call (only when OSM lists a phone). Generic pipeline now fetches `amenity=police` (new `safety` category) so ANY city gets them; Melbourne pack rebuilt (114 stations, 46 with phones). Verified on device. |
| **M6.3** Shopping spots | ✅ | New **`shopping`** retrieval category (markets + malls + department stores) via `amenity=marketplace` + `shop=mall\|department_store`, so ANY city gets them; Melbourne rebuilt (480 rows). **Searchable, no new card:** "shopping"/"markets" ride the existing retrieval→template/AI→cards path (Directions + Save + attribution), grounded to real rows. Also fixed a latent UX bug the tall cards exposed — the empty home screen now scrolls, so example chips are reachable. Verified end-to-end on device. |
| **M6.4** Download-a-city (a–c) | ✅ | On-device **"any city"**: the app fetches OSM/Wikipedia and builds a SQLite pack on the phone (no server; Kotlin twin of the pipeline; v1 skips enrichment). **a** `core/OsmClassifier` (pure-Kotlin classify + Overpass query, unit-tested); **b** `CityPackBuilder` (geocode→Overpass→`JsonReader`→SQLite+FTS in `filesDir/packs/`; **Geelong = 233 grounded places**; 8 review fixes); **c** multi-city storage + active-city switch (`CityDatabase` opens the active pack; `activePack` in DataStore; whole home reactively reloads; **fixes the copy-once gotcha**; 5 review fixes removing leftover Melbourne hard-codes). Verified Melbourne↔Geelong on device. **Remaining:** M6.4d download-UI, M6.4e background refresh. |
| **M6.5** Traveller home | ✅ | Home shows your **ACTUAL suburb** (on-device `nearestSuburb` from a new `place.suburb` column, 25 km guard — **no GPS leaves the phone**), a grounded **"Worth visiting nearby"** card, and a **"Daily needs near you"** card (nearest police/hospital/fuel/parking via new `health`/`fuel`/`parking` categories in BOTH the pipeline and the on-device builder). Melbourne rebuilt (22,624 places). A review caught + fixed a GPS-egress (Nominatim reverse-geocode) that violated non-negotiable #1 → reworked fully on-device. Verified on device. |

### Remaining
- **M6.4 (rest)** — **M6.4d** the "Download data for [city]?" search/confirm/progress UI (replaces the temporary dev trigger in Preferences), and **M6.4e** silent background refresh (WorkManager) of the active city when online. (M6.4a–c done; the copy-once gotcha is already fixed by M6.4c's active-pack storage.)
- **M6 (rest)** — annual festivals. (Dropped as not free/offline/groundable or unsafe to auto-trigger: live events, "current leaders", voice-command auto-calling.)
- **M7** — Travel Journal v2: voice + video memos, and a smarter "you forgot this" bucket-list nudge when you return near a saved place.

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
app/src/main/
  assets/melbourne.db         bundled city data pack (4.5 MB, 20,206 places)
  resources/vosk-...zip        bundled voice model (40 MB, Java resource)
  java/com/wandernear/
    core/        PURE Kotlin, no Android imports (model, retrieval, response) — portable
    data/        CityDatabase (read-only pack), PreferencesRepository, LocationProvider, journal/ (Room)
    ai/          ModelManager (LLM download), LlmEngine (LiteRT-LM wrapper)
    voice/       VoiceRecognizer (Vosk wrapper)
    reminders/   Notifier + JournalReminders (WorkManager)
    travel/      TravelModeService (WHILE-IN-USE location foreground service)
    ui/          ChatScreen, PreferencesScreen, MyTripsScreen, AiSettingsSection, TravelModeSection
  test/          JVM unit tests (GroundingCheckTest, QueryParserTest, RecommenderTest)
CLAUDE.md                     conventions, decisions, milestone log
PROJECT_STATUS.md             this file
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
- **Bundled pack is copied assets→`filesDir` ONCE** (`CityDatabase.open()` only copies `if (!dbFile.exists())`). So shipping a NEW `melbourne.db` (e.g. M6.2 added police) does **not** reach an existing install via `adb install -r` — the old DB persists in `filesDir`. To see a new pack: `adb shell pm clear com.wandernear` (or uninstall). The future M6 refresh flow must version the pack and re-copy when the bundled one is newer. **Tip:** `pm clear` also wipes the 2.6 GB side-loaded LLM (`files/models/`); to load a new pack WITHOUT re-downloading it, instead run `adb shell run-as com.wandernear rm -f files/melbourne.db` and relaunch — the app re-copies the fresh pack and the model survives. (Used this for the M6.3 rebuild.)

## Conventions (from CLAUDE.md)

- **Plan first, get approval, then build** — for every feature/change. Small steps, thorough comments, plain-English explanations.
- **Always use the `ponytail` skill** (simplest solution) on coding and **`ui-ux-pro-max`** on UI.
- **Commit + push at each verified step**, clear messages. **All commits authored by the owner alone** — never add a `Co-Authored-By: Claude` trailer or "Generated with Claude Code" line.
- Git remote: https://github.com/SowatRafi/WanderNear.git (branch `main`).

## Verify it's all there (fresh session)

```powershell
git log --oneline        # 32 commits, latest = 06b5b8a (M6.4c active-city switch)
git status               # clean
```

> **Note:** a `ruflo` MCP server may be connected in your session (agent/swarm/memory
> tooling). It's optional — the app is built with native tools + the `ponytail` and
> `ui-ux-pro-max` skills. Its scratch dirs (`.claude-flow/`, `.claude/proven-config*`)
> and `.claude/settings.local.json` are git-ignored.
