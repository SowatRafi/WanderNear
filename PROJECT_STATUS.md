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

## Status: M1–M6.6 + Travel Mode (TM.1–TM.3) + PT.1 + all-faiths worship + personalised home ✅ — all verified on device and pushed (latest `d76d01b`)

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
| **M6.6** Events & festivals | ✅ | Two generic halves. **`culture` category** (theatre/arts_centre/cinema/community_centre/events_venue/stadium) in BOTH `OsmClassifier` + pipeline → searchable ("theatre"/"live music"/"events"/"festivals"), Melbourne = 832 rows. `sports_centre` removed (gyms/tennis clubs swamped it → "theatres" returned a tennis club). **Annual festivals** into `event` table from **Wikipedia `Category:Festivals in <City>`** (NOT Wikidata — it can't reach the festivals travellers want; see CLAUDE.md), **no dates** stored (`when_text` NULL, card says "Dates change each year"), past-tense articles filtered, rules shared via `core/pack/Festivals.kt`. Melbourne = 22 festivals. New `fetch_festivals.py` step (after build_db). Council open-data researched + dropped (only permits/past events published). Multi-agent review → 4 fixes incl. HIGH Wikipedia 20-extract cap (was dropping Rising + St Kilda Festival) fixed via `continue` token in both fetchers. Bundled pack v3. |
| **PH** Personalised home + all faiths | ✅ | **Home reflects Preferences**: quick-start chips built from your diets + interests + faith (`exampleChips`), and a **"For you"** card of nearby places matching your interests, diet-filtered for food (`CityDatabase.forYou`); practical cards always stay, generic Worth-visiting/festivals below. "What you love" gained Shopping + Culture (emoji labels dropped). **Worship for ALL faiths**: `core/model/Faith.kt` (religion key + place type), a "Faith & worship" picker (`FaithSettingsSection`) replacing the Islam-only prayer toggle, `nearestWorship(religion)`, and `FaithCard` — nearest church/temple/synagogue/gurdwara/mosque with website/phone for service times; **calculated times stay Islam-only** (only faith with a universal astronomical timetable). Verified on a Pixel 6 (Christian→church no times; Muslim→times+mosque). 43 tests. |
| **PT.1** Prayer times + mosque | ✅ | The five daily prayers **calculated on-device** (`core/prayer/PrayerTimes.kt`, pure-Kotlin PrayTimes.org port, no Android/network), unit-tested against the **independent Aladhan API** (Melbourne, ±1 min). Opt-in, off by default; method picker (MWL/ISNA/Egypt/Karachi/Umm-al-Qura) + Standard/Hanafi Asr in Preferences. "Prayer times today" home card labels them honestly as **calculated** (start of each prayer) with the method named + **nearest mosque** (grounded worship+muslim) and its OSM website/phone for the mosque's own **Friday** time (no free source lists it → never invented). Computes from an in-city fix, else the city centre + phone tz. **Owner's ask to scrape mosque sites/Google for times was researched + declined** (1/38 mosques has any service_times, blank; not offline/free/groundable). Verified on a Pixel 6. |
| **TM.3** Around you now | ✅ | While Travel Mode is on: nearest **food / shopping / outdoors** from the active pack, in an "Around you now" card under City Info **and in the ongoing banner itself** — still exactly ONE notification, updated in place, so it can never become a stream of buzzes (only the TM.2 "worth a visit" hit makes a sound). Banner is `VISIBILITY_PRIVATE` + a redacted public version: a locked screen shows Travel Mode is on but not which café you're beside. The screen reads the service's `StateFlow` rather than requesting its own location. `fixInCity`/`categoryLabel`/`distanceLabel` moved to `core/` (unit-tested); one shared `NearbyCard` renders both cards at 3 lines per entry instead of 5; distances in metres up close, rounded to 10 m. Verified on a Pixel 6 in Werribee. |

### Remaining — agreed order (owner, 2026-07-24)
1. **M6.4e** — silent background refresh (WorkManager) of the active city when online. Scoped to the **active pack's area, never the live GPS fix**.
2. **M7** — Travel Journal v2: voice + video memos, and a smarter "you forgot this" bucket-list nudge when you return near a saved place.
3. **Bigger vision (owner, 2026-07-24)** — "a traveller needs no guidebook": drive-past worth-a-visit alerts (TM.2 has the mechanism), an ask-first "want the history?" flow at historic sites, prayer-time + nearest-mosque awareness, accommodation. See the "Vision backlog" section below for what's free/groundable vs not.

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
pipeline/                     Python data pipeline (run on PC): fetch_osm.py → enrich_wikipedia.py → build_db.py → fetch_festivals.py → query_demo.py
                              schema.sql = the pack schema (single source; copied to app assets)
app/src/main/
  assets/melbourne.db         bundled city data pack (4.9 MB, 22,624 places)
  assets/schema.sql           same schema, used by the ON-DEVICE pack builder
  resources/vosk-...zip       bundled voice model (40 MB, Java resource)
  java/com/wandernear/
    core/        PURE Kotlin, no Android imports — portable
      model/       Place, LatLng, CityInfo, CountryFacts, UserPreferences, Faith, CityEvent
      retrieval/   QueryParser (words → SearchSpec)
      response/    Recommender (templates + AI prompt), GroundingCheck (anti-hallucination guard)
      pack/        OsmClassifier + Festivals — classify/query rules SHARED by pipeline parity + on-device builder
      prayer/      PrayerTimes — on-device prayer-time calculation (PrayTimes.org port, unit-tested)
    data/        CityDatabase (reads the ACTIVE pack; nearestSuburb/nearestEssentials/nearbyNotable),
                 CityPackBuilder (on-device "download a city"), PreferencesRepository (incl. activePack),
                 LocationProvider, journal/ (Room)
    ai/          ModelManager (LLM download), LlmEngine (LiteRT-LM wrapper)
    voice/       VoiceRecognizer (Vosk wrapper)
    reminders/   Notifier + JournalReminders (WorkManager)
    travel/      TravelModeService (WHILE-IN-USE location service + "around you now" digest)
    ui/          ChatScreen (traveller home + chat), PreferencesScreen, MyTripsScreen,
                 AiSettingsSection, TravelModeSection, CitiesSection, FaithSettingsSection
  test/          JVM unit tests (GroundingCheck, QueryParser, Recommender, OsmClassifier, Nearby, Festivals, PrayerTimes)
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
- **Wikipedia's extract API caps at 20 extracts PER REQUEST** even when the generator lists more members — the response carries a `continue.excontinue` token for the rest. A one-shot `generator=categorymembers&prop=extracts` silently drops everything past the 20th (alphabetically), so a >20-festival city loses its tail. Both `fetch_festivals.py` and `CityPackBuilder.insertFestivals` follow the token. Any future "list a Wikipedia category with extracts" code must do the same.
- **Wikidata is the WRONG source for city festivals.** Its festival items are reachable only by coordinates or `P131` "located in", and the big ones (Melbourne Comedy Festival Q17012417) have neither — a structured query returns a handful, one defunct, missing the majors. Wikipedia's `Category:Festivals in <City>` returns them all. And Wikidata has NO usable recurring-festival dates (`P837` empty; only one-off past editions carry dates). Use Wikipedia categories, store no date.
- **Nominatim answers in the PLACE's own language.** Searching "Kyoto" returns `京都市`, which we'd store as the city's name and show on every heading. Send an `Accept-Language` header (we use the phone's locale) on any Nominatim request. Overpass is unaffected — place `name` tags stay local (correct: that's what's on the street sign).
- **"Near you" is a lie once packs can be anywhere.** With a far-away pack active, ranking by the real fix gave "Daily needs near you … 8,147 km away" and an empty "Worth visiting nearby". `core/model.fixInCity()` drops the fix beyond `AWAY_FROM_CITY_KM` (100 km) so the cards, the chat AND Travel Mode fall back to the city centre. Add the same guard to anything new that ranks by distance.
- **Anything cached per-fix must be tagged with its pack.** Travel Mode's digest only refreshes when you MOVE, so after switching city the previous city's places would linger under the new city's name. `TravelModeService.Around` carries its `packName` and the screen drops any mismatch — same lesson as the M6.4c review.
- **A location watch with `minDistance = 120 m` delivers NOTHING while you stand still.** Anything that must be on screen as soon as Travel Mode starts has to be seeded from `lastKnown` (any age); only the "you've arrived somewhere" alert should insist on `recentLastKnown`.
- **PowerShell `>` CORRUPTS binary output.** `adb exec-out screencap -p > x.png` writes a BOM + re-encodes → an unreadable PNG. Use `adb shell screencap -p /sdcard/x.png` then `adb pull`.
- **Nominatim's top hit for a city is often a NODE**, and a node can't scope an Overpass `area(...)` query. Ask for several results and take the first `relation`/`way` — this is what makes "any city" actually work (plain "Geelong" fails otherwise).
- **`schema.sql` has inline `--` comments containing `;`** — strip inline comments BEFORE splitting on `;`, or SQLite throws `incomplete input (code 1)` when applying the schema on-device.
- **SQLite leaves a `<db>-journal` sidecar.** When publishing or cleaning up a pack, delete `-journal` / `-wal` / `-shm` alongside the `.db`, or you orphan files.
- **Overpass rate-limits repeated queries from one IP.** The mirror retry + backoff handles it, but a rebuild can stall for a few minutes — that's throttling, not a hang.
- **`adb` is not on PATH** — use `C:\Users\sowad\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **A background review that reports "0 confirmed" may be incomplete.** If verify agents fail (e.g. session limit), findings come back with `verdict: null` — check for those and verify them yourself; a HIGH-severity crash was found exactly that way.

## Vision backlog — "a traveller needs no guidebook" (owner, 2026-07-24)

The owner's north star: the app carries everything a traveller needs to blend in — shop/
market, accommodation, history, hidden gems, a full archived journey — and *proactively*
speaks up like a knowledgeable friend. Each item below is tagged with whether it's
free + offline + **groundable** (rule #5), so nothing here invents a fact.

- **Drive-past "worth a visit" alert** — ✅ mechanism EXISTS (TM.2 fires grounded alerts
  within 300 m of a real DB row while Travel Mode is on). Enhancement: widen/annotate for
  driving speed. Free/groundable.
- **Historic site → "want to know the history?" ask-first flow** — groundable: `attraction`
  places already carry a Wikipedia `summary`. Plan: when Travel Mode passes within N m of a
  place that HAS a summary, a low-key notification offers "Read about this?" → opens the
  stored summary + Directions. Ask-first (never auto-reads). Free, offline, grounded.
- **Halal/dietary "nearest now"** — ✅ already works: `diet:halal` is in the pack + retrieval.
  Enhancement: a one-tap "nearest halal" from Travel Mode, not just chat.
- **Prayer-time + nearest mosque** — ✅ **PT.1 DONE** (see the table above): daily times
  calculated on-device (offline, method-configurable, honest), nearest mosque grounded, the
  mosque's own website/phone for its Friday time. The owner's ask to fetch times from mosque
  websites / Google was researched and declined (not offline/free/groundable — 1/38 mosques
  even has a `service_times` tag, and it's blank). **PT.2** (the proactive "prayer approaching
  → nearest mosque" nudge riding the single Travel Mode notification) is the remaining half.
- **Accommodation** — OSM has `tourism=hotel|hostel|guest_house|motel|apartment`. Groundable
  as a new category exactly like `culture`/`shopping`. Free. (Booking/prices are NOT free/
  groundable — show the place + Directions + phone only, never a price or availability.)
- **Hidden gems** — no honest "hidden" signal exists in OSM (popularity isn't tagged). Closest
  grounded proxy: places WITH a Wikipedia article but LOW prominence, or non-touristy
  categories. Frame as "local spots", never "hidden gem locals love" (that's invented).
- **Full journey archive** — the Travel Journal (M3) already stores places/notes/photos/dates;
  M7 adds voice+video memos. Extend to auto-log Travel-Mode places visited (opt-in).
- **REJECTED / not free-groundable:** live event listings, real-time "what's on tonight",
  hotel prices/availability, popularity or "trending", anything needing a paid/keyed API.

Design rule for every "agent speaks first" feature: it rides the ONE Travel Mode notification
(updated in place, never a stream — see TM.3), is opt-in, ask-first for anything it reads
aloud, and every word comes from a retrieved row or an on-device calculation — never invented.

## Conventions (from CLAUDE.md)

- **Plan first, get approval, then build** — for every feature/change. Small steps, thorough comments, plain-English explanations.
- **Always use the `ponytail` skill** (simplest solution) on coding and **`ui-ux-pro-max`** on UI.
- **Commit + push at each verified step**, clear messages. **All commits authored by the owner alone** — never add a `Co-Authored-By: Claude` trailer or "Generated with Claude Code" line.
- Git remote: https://github.com/SowatRafi/WanderNear.git (branch `main`).

## Verify it's all there (fresh session)

```powershell
git log --oneline -1     # latest = d76d01b (dedupe + docs); working tree clean
git status               # clean, all pushed to origin/main
```
Recent commits (newest first): `d76d01b` dedupe home cards + docs · `94bd861` personalised
home + all-faiths worship · `51893ea` calmer home UI · `4e26059`/`b8d746e` PT.1 prayer times ·
`3eaf6d6` M6.6 culture + festivals · `7f31b5c`/`0a22816` TM.3 · `0827c8f`/`8a0f505` M6.4d.

> **Note:** a `ruflo` MCP server may be connected in your session (agent/swarm/memory
> tooling). It's optional — the app is built with native tools + the `ponytail` and
> `ui-ux-pro-max` skills. Its scratch dirs (`.claude-flow/`, `.claude/proven-config*`)
> and `.claude/settings.local.json` are git-ignored.
