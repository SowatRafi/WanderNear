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

- **M1 — Data pipeline** (in progress): fetch Melbourne from OSM + Wikipedia into
  SQLite with full-text search; prove with "halal restaurants" and "temples".
- **M2 — App skeleton + templated answers**: Compose chat UI + preferences,
  loads the M1 database, returns real recommendations via templates. The MVP
  success test passes here, offline, before the LLM.
- **M3 — On-device AI**: LiteRT-LM + Gemma 4 over the same retrieval, with
  grounding guardrails + the trick-test suite.
- **M4 — Location + voice**: GPS "near me" + Vosk offline voice.
- **M5 — Any city**: "Download data for [city]?" flow + silent background refresh.

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
