# WanderNear — Project Conventions & Decisions

This file is the shared memory for the project. Read it at the start of every
session so decisions stay consistent. Keep it up to date as we go.

## What we're building

A native **Android** app that acts as a personal, on-device local guide for
travellers. The user says what they like (by text or voice) and an on-device AI
recommends real local places — food (with dietary filters), festivals/cultural
events, hidden local hangouts, cultural/religious landmarks, and outdoor spots —
each with a short reason why.

## Non-negotiable constraints

1. **Offline-first & private.** The data pack and the AI live on the phone. All
   search and inference happen on-device. No user data ever leaves the phone.
   Must be fully functional in airplane mode. When online, it silently refreshes
   the current city's data in the background.
2. **Free to build and run.** Open-source tools and free data sources only. No
   paid APIs, no servers.
3. **Native Android, portable core.** Kotlin + Jetpack Compose. Core logic
   (models, retrieval, ranking, prompts) is kept free of Android imports so it
   could be ported later.
4. **Generic city template.** One pipeline builds a data pack for ANY city from
   free sources. Never hand-curate data per city.
5. **Never hallucinate.** Every recommendation must be grounded in a retrieved
   database record. If the data has no good answer, say so honestly and offer to
   download/refresh — never invent a place, address, time, or fact.

## Core architecture principle (how we guarantee #5)

**SQLite is the only source of truth. The AI never retrieves or knows facts.**

- Retrieve places deterministically with SQL: full-text search + structured
  filters (diet, category, distance) + ranking. Take the top ~5.
- If retrieval returns **zero rows**, return a fixed "I don't have that in my
  data" message and DO NOT call the AI at all.
- **Templates first** (Milestone 2): build the friendly answer from the
  retrieved rows using sentence templates — structurally impossible to invent.
- **LLM later** (Milestone 3): the model only rewords the retrieved rows, at
  temperature 0, told to use only those places and refuse otherwise. Hard facts
  (address, hours) are printed from the row, not generated. The model's output
  is validated — any place it names that isn't in the retrieved set is rejected.
- A small adversarial test suite tries to trick the agent into inventing places.

## Tech stack (with status as of 2026-07)

| Area | Choice |
|------|--------|
| Language / UI | Kotlin, Jetpack Compose + Material 3 |
| Async | Coroutines + Flow |
| Local DB | SQLite via Room, full-text search via Room `@Fts4` |
| Data pipeline | Python 3 + `requests` + stdlib `sqlite3` |
| Places data | OpenStreetMap Overpass API (no key) |
| Culture data | Wikipedia REST + Wikidata APIs (no key) |
| On-device LLM (M3) | **LiteRT-LM** (Kotlin) running **Gemma 4 E2B**, downloaded on first run |
| Voice (M4) | **Vosk** small model bundled in app assets (true offline; no hallucination) |
| Location (M4) | FusedLocationProvider, with "which city?" fallback |
| Directions | `geo:` intent to the phone's Maps app (no SDK/key) |
| User preferences | Jetpack DataStore (not the data pack DB) |

Notes:
- Google's older **MediaPipe LLM Inference API is maintenance-only** in 2026;
  new work targets **LiteRT-LM**. Don't follow old MediaPipe tutorials.
- Gemma 4 (April 2026) is **Apache-2.0** — clean to ship.

## Data licensing / attribution (mandatory in-app)

- Show **"© OpenStreetMap contributors"** (ODbL) wherever data is displayed.
- Show a **CC BY-SA 4.0** credit + link next to any Wikipedia text.
- Wikidata fields (ids, coordinates) are CC0 and need no attribution.

## Data schema

See `pipeline/schema.sql`. Tables: `city`, `place`, `place_diet`, `event`,
`favorite`, and the `places_fts` full-text index. Empty facts are left NULL —
never guessed.

## Milestones

- **M1 — Data pipeline** ✅ Done: Melbourne fetched from OSM + Wikipedia into
  SQLite (`pipeline/`) — 20,092 places + full-text search; proven with
  "halal restaurants", "temples", "vegetarian near me".
- **M2 — App skeleton + templated answers** ✅ Done: Compose chat + preferences,
  loads the M1 database, returns real grounded recommendations via templates,
  one-shot GPS "near me" (falls back to the city centre), Directions +
  attribution, and an honest refusal on no match. MVP success test passes
  offline, before the LLM. Package `com.wandernear`; `core/` is Android-free.
- **M3 — Travel Journal** ✅ Done (J1–J4: notes, bucket list, visit dates, photos,
  anniversary + nearby-nudge reminders): private "My Trips" — save visited places,
  notes, bucket list (todo/done), visit dates, photos (copied to app-private
  storage), anniversary reminders, and an on-open "you're back nearby" nudge.
  See "Travel Journal design" below.
- **M4 — On-device AI** ✅ Done — M4.1: LiteRT-LM 0.14.0 + Gemma 4 E2B
  (2.6 GB, Apache-2.0, ungated) running on-device on CPU; model download manager
  + Preferences toggle + "Test AI" verified a real reworded reply on a Pixel 6.
  Required a toolchain upgrade — Kotlin 2.3.21, KSP 2.3.10, Room 2.8.4, AGP
  8.10.1, Compose BOM 2026.06.01, **minSdk 31** — because LiteRT-LM ships Kotlin
  2.3 metadata. M4.2 done: AI-reworded replies in the chat — grounded CONTEXT
  built from the retrieved rows, warm Gemma 4 prose with the fact cards preserved
  beneath, a "warming up" state for the slow first load, and a template fallback
  if the model isn't ready or returns nothing. M4.3 done: `GroundingCheck`
  (core) scans the AI reply for capitalized venue/proper-noun phrases and rejects
  any that match no retrieved place → falls back to the template, so an invented
  place is never shown; covered by JVM unit tests (`GroundingCheckTest`, JUnit).
  M4.4 done: adversarial trick-test suite — 16 JVM unit tests (invented-place
  rejection, prompt-injection via data blocked, refuse-on-empty, parser
  correctness), all green. On-device Gemma 4 over the same retrieval, grounded
  and trick-tested; templates remain the guaranteed fallback.
- **M5 — Voice input** ✅ Done. M5.1: Vosk 0.3.75 offline STT, mic button in the
  chat input, RECORD_AUDIO requested in-context, transcribe-to-input (review-then-
  send); GPS "near me" was already done in M2.4. The bundled ~40 MB model lives in
  `app/src/main/resources/` (Java resources), NOT `assets/`, because AAPT2 crashes
  linking a large asset; it's unzipped once into filesDir and loaded. M5.2 polish
  done in three verified steps: (1) honest voice states Idle→Preparing→Listening
  (the slow first load never loses the first words), a gentle "didn't catch that"
  nudge on silence, and a privacy fix — the mic is released the instant Vosk returns
  a phrase (it used to keep recording); (2) the emoji mic replaced by inline vector
  mic/stop icons (no new dependency — deliberately avoids material-icons-extended),
  a spinner for Preparing and a soft "sonar" pulse for Listening, screen-reader
  labels + a polite liveRegion, with the pulse living only while listening (no idle
  frame cost) and respecting the system "remove animations" setting; (3) graceful
  permission handling — a soft first denial gets a retry hint, a permanent denial
  opens an AlertDialog offering "Open Settings" (chosen via
  shouldShowRequestPermissionRationale), rememberSaveable so it survives rotation.
  Each step was adversarially self-reviewed (multi-agent, findings verified) before
  commit. Build gotcha hit here: a corrupted Gradle transforms cache made AAPT2
  crash on link even for known-good code — fix is
  `rm -rf ~/.gradle/caches/<ver>/transforms` then rebuild.
- **M6 — Any city + richer pack** (in progress): "Download data for [city]?" flow
  + silent background refresh; plus a City Info card, a Safety section, shopping
  spots, annual festivals, and Call/Directions buttons. (Live/real-time events,
  "current leaders", and voice-command auto-calling were considered and dropped —
  not free/offline/groundable, or unsafe to auto-trigger.)
    - **M6.1** ✅ City Info card (population/currency/emergency) with a safe
      Call-emergency dial.
    - **M6.2** ✅ Safety section — the nearest police stations on the home screen,
      each with Directions (always) and Call (only when OSM lists a phone). The
      generic pipeline now fetches `amenity=police` (new `safety` category) so ANY
      city gets them; the Melbourne pack was rebuilt (114 stations, 46 with phones).
      Grounded like everything else: every station is a real retrieved row.
    - **M6.3** ✅ Shopping spots — a new `shopping` retrieval category added the same
      generic way as M6.2: the pipeline fetches `amenity=marketplace` +
      `shop=mall|department_store`, so ANY city gets them; the Melbourne pack was
      rebuilt (480 shopping rows: 275 mall, 164 department store, 41 marketplace).
      Unlike police it's a *searchable* category (no new card) — "shopping"/"markets"
      ride the existing retrieval→template/AI→cards path (Directions + Save +
      attribution), grounded to real rows. An adversarial self-review (multi-agent,
      finding verified) caught that the AI grounding guard's venue vocabulary lacked
      shopping words, so `GroundingCheck.VENUE_WORDS` was extended (mall/store/plaza/
      emporium/…) to keep an invented shopping name out of the reworded intro —
      parity with the other categories, covered by a new unit test. Also fixed a
      latent UX bug the tall City-Info/police cards exposed: the empty home screen
      now scrolls, so the example chips are reachable on smaller screens.
    - **M6.4** Download-a-city (on-device — the "any city" keystone): with no server,
      the app itself calls the free OSM/Wikipedia APIs and builds a SQLite pack on the
      phone (a focused Kotlin port of the Python pipeline; v1 skips Wikipedia enrichment).
        - **M6.4a** ✅ `core/pack/OsmClassifier` — pure-Kotlin classify + Overpass-query
          builder; the OSM value lists are a single source of truth (no drift). Unit-tested.
        - **M6.4b** ✅ `data/CityPackBuilder` — geocode (Nominatim) → Overpass → stream with
          `android.util.JsonReader` → SQLite pack + FTS in `filesDir/packs/`. No new
          dependency. Verified on device: built Geelong = 233 real grounded places.
          Adversarial review → 8 fixes (area-preferring geocode, schema inline-comment
          split, always-close transaction/connections, blank name/address parity,
          `.part`+`-journal` cleanup, `Thread.sleep`→cancellation-aware `delay`, OSM-id
          filename so same-named cities never collide).
        - **M6.4c** ✅ Multi-city storage + active-city switch — `CityDatabase` opens the
          ACTIVE pack (bundled Melbourne stays the seeded default); `activePack` lives in
          DataStore; the whole home screen reactively reloads on switch. **Fixes the
          copy-once gotcha.** Verified on device (Melbourne↔Geelong). Adversarial review →
          5 fixes, all leftover Melbourne hard-codes the multi-city switch exposed:
          pack-local id de-dup cleared on switch, home cards keyed to the pack (no stale
          window), city centre = place **centroid** not bbox midpoint (~8 km vs ~35 km
          from CBD), city-agnostic "no results" text, and grounding safe-words now take the
          active city's name instead of hard-coding Melbourne.
        - **M6.4d** ✅ The real "Download data for [city]?" UI — a **Cities card in
          Preferences** (same place as the AI-model download; no new tab). Switch between
          installed packs via a radio list whose names are read from each pack's own `city`
          row; or type a city → `CityPackBuilder.find()` returns up to 5 real Nominatim
          AREAS → tap one → "Download data for X?" dialog → determinate progress with
          Cancel → auto-switch. The temporary `TempPackBuilderSection` is DELETED.
          Build v1 took the first geocode hit blindly; now `build()` takes the confirmed
          `Match`, so "Paris" can't quietly fetch Paris, Texas, and we never geocode twice.
          Two real bugs found by verifying on-device: (1) Nominatim answers in each place's
          OWN language — "Kyoto" came back as 京都市 and would have become the stored city
          name on every heading; fixed with an `Accept-Language` header set from the phone's
          locale. (2) With a far-away pack active, `fixInCity()` now falls back to the city
          centre — before it, downloading Kyoto from Melbourne showed "Daily needs near you"
          **8,147 km away** and an empty "Worth visiting nearby". Verified on a Pixel 6:
          Kyoto = 10,326 grounded places, switch back to Melbourne still shows the real
          suburb. Deliberately skipped: deleting packs, and surviving a tab switch mid-
          download (same screen-scoped limit as the AI model download; marked `ponytail:`).
        - Remaining: **M6.4e** silent background refresh (WorkManager) of the active city
          when online.
    - **M6.5** ✅ Traveller home (where-am-I + suggestions + essentials): the home now shows
      your ACTUAL suburb (e.g. "Werribee") derived ON-DEVICE from a new `place.suburb` column
      (the nearest place's addr:suburb, within a 25 km guard) — **no GPS ever leaves the
      phone**; a "Worth visiting nearby" card of grounded notable places (with their Wikipedia
      why); and a "Daily needs near you" card — nearest police / hospital / fuel / parking (new
      `health`/`fuel`/`parking` categories, added to BOTH the pipeline and the on-device
      `OsmClassifier` so ANY city gets them; named-only so grounded + small). Melbourne pack
      rebuilt (22,624 places). An adversarial review caught the first attempt reverse-geocoding
      via Nominatim (which leaked the user's GPS off-device — a #1 violation) plus a false
      "suburb in the wrong city" composition; both fixed by going fully on-device with the
      distance guard, off-main-thread location reads, and a fresh-fix requirement for the label.
    - **M6.6 — Events & festivals** ✅ Done. Two halves, both generic for ANY city:
        - **`culture` category** (theatre / arts_centre / cinema / community_centre /
          events_venue / stadium) added to BOTH `OsmClassifier` and the Python pipeline —
          the grounded "where things happen here". `sports_centre` was tried and **removed**:
          it's gyms and tennis clubs and swamped the category (578 of 1410 rows) so "theatres"
          answered with a tennis club. Searchable ("theatre", "live music", "events",
          "festivals" → culture), wired through QueryParser + Recommender ("venues") +
          `GroundingCheck.VENUE_WORDS`. Melbourne rebuilt → 832 culture rows.
        - **Annual festivals** into the existing `event` table, from **Wikipedia's
          `Category:Festivals in <City>`** — NOT Wikidata. *Research finding:* Wikidata can't
          answer this — its festival items are only reachable by coordinates or "located in",
          and the ones travellers care about (Melbourne Comedy Festival Q17012417) have
          neither, so a structured query returns 6 (one defunct) and misses Comedy/Moomba/
          Fringe; Wikipedia's category returns all 25. **NO DATES** stored (`when_text` NULL):
          no free source publishes a reliable recurring-festival date (Wikidata's `P837` is
          empty), so the card says "Dates change each year" instead of guessing. Past-tense
          articles ("MEL&NYC festival *was*…") are filtered out. Rules shared by pipeline +
          on-device builder via `core/pack/Festivals.kt`. Melbourne → 22 festivals.
        - **Owner asked for council open-data too;** researched and **dropped** — Melbourne's
          portals publish only event *permits 2014-2018* and *past* events 2001-2017, no
          live "what's on" feed. Not built (the registry would add complexity for zero yield);
          revisit only if a city with a real dated CC0/CC-BY feed turns up.
        - **Adversarial review (multi-agent) → fixes:** (1) HIGH — Wikipedia caps EXTRACTS at
          20/request even though the generator lists all members, so >20-festival cities
          silently lost their alphabetical tail (Melbourne dropped Rising + St Kilda Festival);
          fixed by following the `continue` token in BOTH fetchers → 22 not 17. (2) the
          on-device `summary_url` wasn't URL-encoded (pipeline used `urllib.quote`); fixed with
          `Uri.encode`. (3) "…and N more" was a dead end → now tap-to-expand. (4) README/status
          didn't list `fetch_festivals.py`.
        - **New pipeline step:** `fetch_festivals.py` runs AFTER `build_db.py` (opens the
          finished pack, fills `event`). Skipping it just leaves no festivals. `CityEvent`
          model + `CityDatabase.festivals()` + `FestivalsCard`. Bundled pack v3.
        - **Deferred:** live/dated events (no free groundable source); per-city council feeds.
- **TM.3 — "Around you now"** ✅ Done. While Travel Mode is on, the nearest **food /
  shopping / outdoors** from the active pack appear in an "Around you now" card under City
  Info, and in the ongoing banner. **The banner Travel Mode already shows IS the digest** —
  there is still exactly ONE notification, updated in place as you move, so it can never
  become a stream of buzzes; only the TM.2 "worth a visit" hit still makes a sound. The
  banner is `VISIBILITY_PRIVATE` with a **redacted public version**, so a locked screen
  shows that Travel Mode is on (the privacy guarantee) but not which café you're beside.
  Fuel/parking/police/hospital are deliberately NOT repeated — they're already on "Daily
  needs near you". **"Local hotspot" has no groundable popularity source in OSM**, so it
  isn't a bucket: cafés/bars/pubs come through `food`, parks/beaches/viewpoints through
  `outdoor`, and anything with a Wikipedia article through "Worth visiting" — we never
  claim "locals love this" (that would be invented → rule #5).
    - The screen reads the service's `StateFlow` instead of requesting its own location, so
      Travel Mode stays the single place that watches you.
    - Three subtleties found while building: the digest must seed from ANY last-known fix
      (updates only arrive once you've MOVED, so a stationary user would see nothing) while
      the ALERT stays fresh-fix-only; a digest is tagged with its pack and dropped if it
      doesn't match the active city (it only refreshes on movement, so after a switch the
      old one would list another city's places); and `nearestEssentials` needed an optional
      radius + bbox prefilter, or "nearest food" measured every café in the pack every
      couple of minutes.
    - `fixInCity`, `categoryLabel` and `distanceLabel` now live in `core/` (unit-tested), so
      the service and the home screen share one rule instead of each deriving it. One shared
      `NearbyCard` renders both cards at three lines per entry instead of five, and
      distances read as metres up close, rounded to 10 m (a fix isn't metre-accurate).
- **PT — Prayer times + mosque awareness** (from the owner's "no guidebook" vision).
    - **PT.1** ✅ Done. The five daily prayer times, **calculated ON-DEVICE** (offline, free)
      — `core/prayer/PrayerTimes.kt`, a pure-Kotlin PrayTimes.org port, unit-tested against
      the INDEPENDENT Aladhan API (Melbourne, ±1 min). Opt-in (off by default), with a
      method picker (MWL/ISNA/Egypt/Karachi/Umm-al-Qura) + Standard/Hanafi Asr in
      Preferences. A "Prayer times today" home card shows the times labelled honestly as
      CALCULATED (the start of each prayer; a mosque may pray later) with the method named,
      plus the **nearest mosque** (grounded `worship`+`religion=muslim`) and its OSM
      website/phone so the user confirms the mosque's own **Friday** time — which no free
      source lists and we never invent. Computes from an in-city fix when we have one, else
      the city centre + phone timezone. **Owner's original ask was to fetch each mosque's
      times from its website / Google; researched and declined** — only 1 of 38 Melbourne
      mosques has any `service_times` tag (and it's blank), scraping mosque sites is fragile +
      ungrounded (rule #5) and not offline, and there's no free times API. On-device
      calculation + a link to the mosque's own site is the honest, offline, free path.
    - **PT.2** (deferred): the proactive "prayer approaching → nearest mosque" nudge riding
      the single Travel Mode notification (TM.3 pattern), opt-in and quiet.
    - **All faiths** ✅ (2026-07-25): the worship feature covers EVERY religion, not just Islam.
      `core/model/Faith.kt` maps each faith (Muslim/Christian/Hindu/Buddhist/Jewish/Sikh) to its
      OSM `religion` key + place type. A "Faith & worship" picker replaced the Islam-only prayer
      toggle; the home shows the **nearest real place of worship** for your faith (church/temple/
      synagogue/gurdwara/mosque) with its own website/phone for service times — grounded, never
      invented. `nearestMosque`→`nearestWorship(religion)`; `PrayerCard`→`FaithCard` (calculated
      **times stay Islam-only** — the only faith with a universal astronomical daily timetable;
      others show the place only). Melbourne pack has all faiths grounded (Christian 929, …).
- **PH — Personalised home** ✅ (2026-07-25). The home reflects your Preferences: the quick-start
  chips are built from your diets + interests + faith (halal+attractions+Christian → "Halal food",
  "Museums", "Churches"), and a **"For you" card** leads the suggestions — nearby places in your
  chosen interests, diet-filtered for food (`CityDatabase.forYou`). Practical cards (city, daily
  needs) always stay; generic "Worth visiting" + festivals stay below. "What you love" gained
  Shopping + Culture. Nothing invented — every suggestion is a real retrieved row.
- **M7 — Travel Journal v2**: voice + video diary memos, and a smarter "you forgot
  this" nudge that surfaces unfinished bucket-list items when you return near a place.
- **TM — Travel Mode** ✅ Done (TM.1–TM.2): an opt-in Preferences toggle that runs
  a WHILE-IN-USE location foreground service (never background — no
  ACCESS_BACKGROUND_LOCATION) with an always-visible "Travel Mode is on" banner.
  TM.1: the toggle + service + banner + Stop, started only from the on-screen tap,
  START_NOT_STICKY (a killed process never silently restarts), and MainActivity
  self-heals a stale "on" switch on launch. TM.2: a battery-friendly live location
  watch (~2 min / ~120 m cadence, low-power network provider preferred) that fires
  grounded "worth a visit nearby" alerts within 300 m — each alert is a REAL
  retrieved DB row (`CityDatabase.nearbyNotable`: places with a Wikipedia summary or
  an attraction), never invented, de-duped per session. The banner is the privacy
  guarantee: we refuse to start the location service if its notification channel is
  muted, and the immediate seed check only runs off a *fresh* fix so a stale
  last-known can't falsely claim you're near a place you left.

## Travel Journal design (M3)

Personal, private, offline. Stored in a SEPARATE database from the city data
packs so refreshing/replacing a city can NEVER delete the user's memories; each
saved place snapshots its own name + coordinates so it is self-contained.

Journal tables: `saved_place` (name, lat/lng snapshot, free-text notes),
`visit_date` (one per visit → one anniversary each), `bucket_item`
(text + status todo/done — "what's left" = the todo items), `photo`
(copied into app-private storage, optional caption). Everything is editable
and deletable with a confirm on delete.

Android mechanisms: photos via the no-permission Android Photo Picker copied
into `filesDir`; anniversary reminders via WorkManager + POST_NOTIFICATIONS;
the "you're back" nudge is a one-shot GPS check on app open (foreground location
only) comparing saved places within ~300 m — NO background location. Journal
CRUD uses Room; the read-only city pack is opened directly.

## Git & commit rules

- Remote: https://github.com/SowatRafi/WanderNear.git
- Commit at each milestone with short, clear messages; push after each.
- **All commits are authored by the owner alone.** Never add a
  `Co-Authored-By: Claude` trailer or a "Generated with Claude Code" line to any
  commit message or pull request.

## Working style

- Plan first, get approval, then build — for every feature or change.
- Small steps. Keep code simple and well-commented. Explain non-obvious things
  in one or two plain sentences. Don't dump large amounts of unexplained code.
- **Always use the `ponytail` skill** on coding tasks (simplest solution that
  works — no over-engineering) and the **`ui-ux-pro-max` skill** on UI/UX tasks.
- **Comment code thoroughly** so a beginner can follow every file.
- **Commit + push professionally** at each verified step (clear messages).
