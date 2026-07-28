# Session handoff — the live-first pivot (2026-07-26)

> Snapshot of the session that turned WanderNear from **offline-first** into a
> **live online-first** app. Read this together with `CLAUDE.md` (conventions +
> full milestone log) and `PROJECT_STATUS.md` (status, build steps, gotchas).

---

## 1. What changed this session (the big one)

The owner reversed a **non-negotiable constraint**: the app used to be offline-first
(bundled Melbourne, download-a-city required). It is now **online-first**:

> **Online → fetch places LIVE from OpenStreetMap as you ask.
> Downloading a city is the user's OPTIONAL choice, for offline use.**

What did **not** change (and must never change):

- **Grounding (rule #5).** Every place shown is a REAL fetched row — live OSM or a
  downloaded pack. The on-device AI only *rewords* those rows; `GroundingCheck` still
  rejects any reply naming a place we didn't retrieve.
- **Privacy.** Data is fetched by **area NAME only**. "Near you" is ranked
  **on-device**. The user's raw GPS is **never** sent anywhere.
- **Free.** No paid APIs, no servers, no keys. On-device AI (Gemma) stays on-device.

---

## 2. Delivered in three phases — all committed AND pushed

| Phase | Commit | What it delivers |
|---|---|---|
| **L1** | `05fd7e0` | **Live chat.** Set an area by name → ask → real places fetched live from OSM, ranked on-device, reworded by on-device AI. |
| **L2** | `ef10ac1` | **Live home.** The whole home reflects the live area from ONE Overpass fetch (hero, daily-needs, worth-visiting, for-you, worship). |
| **L3** | `7ef81c7` | **Offline fallback.** Offline → the downloaded pack that MATCHES your area (never a different city), else a clean "You're offline" state. |

**Verified on the Pixel 6** in every state: online live, offline with a matching pack,
offline without one, and the VPN/airplane case.

### The new architecture in one picture

```
        ┌── ONLINE (default) ──────────────────────────────┐
        │  LiveSource → Overpass (bbox + categories)       │
ActiveArea ──┤    → parse to grounded Place[]              │──→ rank ON-DEVICE
(name+bbox)  │    → filter by SearchSpec                   │      ↓
        │                                                  │   templates / on-device AI
        └── OFFLINE (fallback) ────────────────────────────┘   (rewords only)
           CityPackBuilder pack, matched to the area by osmId       ↓
           else → "You're offline — reconnect or download"     GroundingCheck
```

### Key new/changed files

| File | Role |
|---|---|
| `core/model/ActiveArea.kt` | **NEW.** The area you're exploring: name + bbox + `osmId` (+ country/population). Set by name; only the name is ever sent. |
| `data/LiveSource.kt` | **NEW.** The live path: bounded, category-scoped Overpass fetch → grounded `Place[]` → filter → rank on-device. `isOnline()` picks live vs. pack. |
| `core/pack/OsmClassifier.kt` | Refactored: `selectorsFor(category)` is now **shared** by `overpassBody` (download) and the new `overpassBodyBbox` (live), so they can't drift. Also gained shared `address()`. |
| `data/CityPackBuilder.kt` | `packForOsmId()` finds the pack backing an area; `Match.toActiveArea()`. |
| `data/PreferencesRepository.kt` | Stores `activeArea` as a small JSON blob. |
| `ui/ChatScreen.kt` | Branches live vs. pack everywhere: 3 home effects + `runSearch`, via `resolveOfflinePack()` and the shared `buildRecommendation()`. Area-aware welcome. |
| `ui/CitiesSection.kt` | Confirm dialog now offers **Use live** (default) *or* **Download** (optional offline). |
| `data/CityDatabase.kt` | Bundled Melbourne is **no longer auto-seeded**. Presence is file-based (`isBundledInstalled` / `installBundled` / `hasAnyCity`). |

---

## 3. ⚠️ UNCOMMITTED work in progress — Phase 2 (live Wikipedia "stories")

**The working tree is NOT clean.** Four files carry unfinished Phase 2 work:

```
 M app/src/main/java/com/wandernear/core/model/Place.kt      (+ wikipedia/wikidata fields)
 M app/src/main/java/com/wandernear/data/LiveSource.kt       (+ enrichStories, carries wiki tags)
 M app/src/main/java/com/wandernear/ui/ChatScreen.kt         (enrich chat + for-you + notable; PlaceRow snippet)
?? app/src/main/java/com/wandernear/data/Wikipedia.kt        (NEW — the fetcher)
```

**Goal:** give ANY city the Wikipedia write-ups that only the bundled Melbourne had, so
attraction cards show the "why" and the home's "Worth visiting" gets a 1–2 line snippet.

**How it works (grounded):** a summary is fetched **only** for a place OSM *already links*
to Wikipedia (a `wikipedia` or `wikidata` tag). We never guess which article belongs to a
place. Disambiguation pages and empty extracts are dropped. It's a Kotlin port of the
existing `pipeline/enrich_wikipedia.py`.

### State of it — read this before touching it

- ✅ **Builds, and unit tests pass.**
- ✅ **The enrichment demonstrably runs** — device logs showed it iterating each place and
  checking its wiki tags.
- ❌ **NOT verified end-to-end.** A summary was **never seen rendering on screen**, because
  every place in the areas tested (Werribee, outer Melbourne) genuinely has **no** OSM
  wiki tag (`wp=null wd=null`) — including the Melbourne Star Observation Wheel. So the
  "no story shown" was *correct* behaviour, but it never proved the happy path.
- 🔴 **DEBUG LOGGING IS STILL IN THE CODE** — two `android.util.Log.d("WNWIKI", …)` lines
  in `data/LiveSource.kt` (~lines 128 and 132). **Remove them before committing.**

### To finish it

1. Remove the two `WNWIKI` debug log lines from `LiveSource.kt`.
2. Verify the happy path in a **wiki-dense area**: set the area to something small and
   landmark-rich (e.g. **Federation Square** or Melbourne CBD) rather than a whole city,
   then ask for `attractions` / `museums` and confirm a real Wikipedia sentence appears on
   the cards and in "Worth visiting".
3. Screenshot it, adversarially review, then commit + push.

**If it can't be made to show a story**, that is a legitimate finding worth reporting to
the owner (OSM wiki-tag coverage is thin outside major landmarks) — don't fake it, and
don't loosen the "only OSM-linked places" rule to force a match. Guessing the article is
exactly how you attach the wrong facts.

### Deliberately NOT done (owner's call)

**P2c — stories for DOWNLOADED cities.** The owner said *"make live, if wants then
download"*, so only the live path was attempted. `CityPackBuilder` still skips Wikipedia
enrichment, which means a downloaded pack has no summaries → Travel Mode's **Read/Listen**
story alerts still only fire in a pack that has them.

---

## 4. Two real bugs found on-device this session (both fixed, both in L3)

1. **A VPN with a dead tunnel fooled the offline check.** The owner's phone runs a VPN;
   when WiFi drops (airplane mode) the VPN keeps reporting `NET_CAPABILITY_VALIDATED`, so
   `isOnline()` said "online" and every live fetch hung ~20 s.
   **Fix:** when the active network is a VPN, trust it only if a **non-VPN** network is
   also validated. Detects the dead tunnel instantly.
   ⚠️ `NetworkCapabilities.getUnderlyingNetworks()` is a **system API** — it does not
   compile against the public SDK. Don't reach for it.

2. **`CityDatabase.open()` crashed** with `SQLITE_CANTOPEN` on launch. `activePack`
   defaults to `"melbourne.db"` while DataStore loads — and under online-first, Melbourne
   may not be installed, so it tried to open a missing file.
   **Fix:** `open()` now resolves to a pack that actually **exists**
   (requested → bundled if present → any downloaded).
   *This also explains the earlier "melbourne.db keeps disappearing" mystery — it was
   never disappearing; online-first simply doesn't install it, and the code had to cope.*

Belt-and-braces from the same lesson: `LiveSource.search/places` now return **`null` on a
FAILED fetch** (vs. an empty list = "fetched fine, no matches"), so the home and chat fall
back to the offline pack even when connectivity lies.

---

## 5. New gotchas (add to the pile in `PROJECT_STATUS.md`)

- **`adb shell svc wifi disable` is asynchronous.** Wait ~5 s and confirm
  `Active default network: none` before launching, or the app still sees a network and
  you'll misread the result. (This cost several confusing test rounds.)
- **The phone re-enables WiFi on its own** after a while — re-check connectivity between
  offline tests rather than trusting the earlier state.
- **An empty `files/packs/` and a missing `melbourne.db` is now CORRECT**, not a bug. A
  fresh install has no city at all; the welcome screen is the expected first run.
- **OSM wiki-tag coverage is thin.** Plenty of real landmarks carry no `wikipedia` /
  `wikidata` tag, so "no story" is often the honest answer, not a broken fetcher.
- **Don't put `e: ` in a PowerShell `Select-String` pattern** — it tripped a sandbox path
  guard. Match on `error:` or `BUILD FAILED` instead.

---

## 6. Current state of the connected Pixel 6

- **Active area:** `Melbourne, Victoria, Australia` (osmId `4246124`) — **live**.
- **Downloaded packs:** *none* (`files/packs/` is empty).
- **Built-in Melbourne:** *not installed* (correct for online-first; it's an optional
  offline sample under Preferences → Cities).
- **Consequence:** if the phone goes fully offline right now it will show
  **"You're offline — reconnect or download Melbourne"**. That is the intended L3
  behaviour, not a bug. To get an offline city back: Preferences → Cities → search a
  city → **Download** (a suburb like Werribee builds in well under a minute).
- On-device AI (Gemma, ~2.6 GB) is present and the toggle is ON.

---

## 7. What's next

1. **Finish Phase 2** — clean the debug logs, verify a story really renders in a
   wiki-dense area, commit + push. *(Work is already written; see §3.)*
2. **P2c (optional, owner's call)** — fetch summaries during a **download** too, so a
   downloaded city gets rich offline "Worth visiting" *and* Travel Mode Read/Listen.
3. **Phase 3 / M6.4e** — silent background refresh (WorkManager) of the active area when
   online, scoped to the area's name/bbox, never the live GPS fix.

Longer-term items are unchanged in `CLAUDE.md` (M7 Travel Journal v2, PT.2 prayer nudge,
accommodation) — plus the standing rejects: no live event feeds, hotel prices, or
popularity/"trending" signals, because none are free **and** groundable.
