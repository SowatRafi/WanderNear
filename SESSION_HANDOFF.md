# Session handoff — location-first (2026-07-28)

> Read with `CLAUDE.md` (conventions + full milestone log) and `PROJECT_STATUS.md`
> (status, build steps, gotchas). Working tree is CLEAN; everything below is pushed.

---

## 1. What the owner asked for, in their words

> *"I told you to make an online and live agent, it will pull my location, look for the
> best places to visit, I can instruct it my taste and also my religious and food
> preference. When you are showing me, Melbourne every time? Also, you say add built-in
> Melbourne (Offline Sample). It the matter of the user if he/she wants to download
> before visiting somewhere or not!"*

The app was still asking **which city** — the explored area came ONLY from a name typed
in Preferences, so whatever was typed once (Melbourne) headlined every screen forever.

## 2. What changed — the app is now LOCATION-FIRST

Commit `859d6a5`. The key realisation: the home and chat already keyed off one value,
`activeArea`, so changing **where that value comes from** made everything location-first
without rewriting the cards.

| Piece | What it does |
|---|---|
| `core/model/ActiveArea.hereArea(fix)` | Builds the explored box straight from the phone's fix (±3 km; longitude span ÷ cos(latitude)). Unit-tested, 6 tests. |
| `Place.suburb` + `LiveSource` | Carries `addr:suburb`/`addr:city`, so the nearest fetched place NAMES where you are — on-device, no reverse-geocode. |
| `LocationProvider.currentFix` | Platform one-shot `getCurrentLocation` (API 30+), so a fresh permission grant doesn't sit with no cached fix. |
| `LocationProvider.countryName` | Country from the SIM's **network** country → currency + emergency number. No request. |
| `packContaining` | Offline pack matched by **geography** (an around-you box has no OSM id) — the downloaded city you're inside, never another. |
| `ChatScreen` welcome | Locating / Turn on location / Open Settings / Try again / You're offline / OpenStreetMap didn't answer. |

**No fallback to a previously-picked city.** If we can't get a fix we say so. That
fallback is *exactly* how Melbourne kept coming back — it was found and removed during
this session's own adversarial review, before it shipped.

**Melbourne is gone from the UI**: both "try/add the built-in Melbourne sample" buttons
deleted, `MELBOURNE_CBD` fallback origin deleted (an empty pack now shows nothing rather
than pretending you're in Melbourne). Preferences → Cities is now **"Offline cities"**,
framed as *"you don't need any of this day to day"*.

## 3. Phase 2 (Wikipedia stories) — finished and verified

A summary is fetched **only** for a place OSM itself links (`wikipedia`/`wikidata` tag);
the article is never guessed. It reaches the **cards only, never the AI prompt**, so the
grounding guarantee is untouched (`Recommender` doesn't read `summary`). Debug logging
removed.

Verified on a Pixel 6: **"zoo" → Werribee Open Range Zoo with its real Wikipedia
sentence**, via `wikidata=Q14935316`.

⚠️ **OSM wiki-tag coverage is thin.** Of the 6 named attractions in that 3 km box,
exactly ONE has any wiki link. "No story" is usually the honest answer, not a bug.

## 4. ⚠️ The privacy rule CHANGED — this was the owner's explicit decision

Auto-detection cannot work without sending *something* about where you are. The owner was
offered a coarse ~5 km snapped grid, an exact box, or keeping manual naming, and **chose
the exact box**.

- A **bbox centred on the fix (±3 km) IS sent to OpenStreetMap.** That is the one thing
  that leaves the phone about your position.
- Everything else stays on-device: distance ranking, the locality name, the country,
  prayer times, the AI.
- **Still forbidden:** reverse-geocoding the user's coordinates, sending a fix anywhere
  other than the OSM place query, any account/history/telemetry.
- **Never write "your location never leaves the phone" in the app again** — it's no longer
  true. The welcome screen states plainly what is sent. CLAUDE.md #1 and PROJECT_STATUS.md
  were updated to match.

## 5. Verified on the Pixel 6 (screenshots taken)

- Opens straight onto **"Good evening / Werribee"** — no city typed, no Melbourne.
- Live "For you" from one Overpass fetch: 590 m / 810 m / 860 m — real distances.
- Chat "attractions" → on-device Gemma rewording real live rows, grounding-checked.
- Chat "zoo" → the Wikipedia story renders.
- **Fully offline** (WiFi off + airplane): downloaded `werribee_2431045.db` found by
  geography; full home incl. Australia / 50,027 / AUD / Emergency 000.
- Welcome states: Locating, and "OpenStreetMap didn't answer".

## 6. Open items for the next session

1. **The country pills are blank ONLINE while the phone is in airplane-mode + WiFi.**
   `networkCountryIso` is empty with no mobile network, so currency/emergency are omitted
   — deliberately, rather than guessing from the SIM's *home* country (wrong for a
   traveller abroad). Offline-with-a-pack shows them because the pack stores them.
   Decide whether a "last known country" cache is worth it.
2. **Unexplained preferences change.** At 17:42 the phone had faith=Muslim + gluten-free +
   attractions; at 17:43 the stored prefs became faith=none + halal + shopping/culture, and
   stayed that way. Nothing in this session's diff writes preferences (`setDiets`/`setFaith`
   only fire on an explicit chip tap), so the most likely cause is a stray adb test gesture.
   **Ask the owner what their settings should be; don't assume.** Related owner confusion
   worth pre-empting in the UI: **"Halal" lives under Dietary needs, not Faith & worship**,
   so setting faith to None correctly leaves the "Halal food" chip in place. Consider making
   that separation clearer on screen.
3. **`Welcome.Blocked` / `Welcome.NoFix` not exercised on device** (they need a denied
   permission / disabled location). Logic is simple but unproven.
4. **P2c** — fetch summaries during a DOWNLOAD too, so a downloaded city gets offline
   stories and Travel Mode Read/Listen.
5. **M6.4e** — silent background refresh (WorkManager) of the area when online.
6. `ponytail:` debt — the bundled 5 MB `melbourne.db` asset now has no UI entry point once
   deleted; drop the asset and its install/restore code.

## 7. Gotchas learned this session

- **Overpass's free mirrors go down.** Both failed even a trivial `node(1)` probe for
  ~15 minutes. A failed fetch on working WiFi must say *"OpenStreetMap didn't answer"*,
  never *"you're offline"*. `LiveSource` now does two passes over the mirrors
  (readTimeout 90 s → 30 s so the retry stays bounded).
- **Overpass rate-limits per IP, and your PC shares the phone's.** Don't probe Overpass
  from the desktop while verifying on-device — it makes the app look broken.
- **`adb shell input tap` takes DEVICE pixels.** The Pixel 6 is 1080x2400 but screenshots
  are shown scaled (900x2000) — multiply, or every tap lands somewhere else.
- `adb shell svc wifi disable` is async: wait ~8 s and confirm
  `Active default network: none`.
