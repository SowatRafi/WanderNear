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
- **M6 — Any city + richer pack**: "Download data for [city]?" flow + silent
  background refresh; plus a City Info card (population/currency/emergency number),
  a Safety section (police stations), shopping spots, annual festivals, and
  Call/Directions buttons. (Live/real-time events, "current leaders", and
  voice-command auto-calling were considered and dropped — not free/offline/
  groundable, or unsafe to auto-trigger.)
- **M7 — Travel Journal v2**: voice + video diary memos, and a smarter "you forgot
  this" nudge that surfaces unfinished bucket-list items when you return near a place.

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
