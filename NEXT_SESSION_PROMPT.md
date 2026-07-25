# Next-session starter — WanderNear

Copy the box below and paste it as your **first message** in a new Claude Code session
(run from `C:\Users\sowad\Documents\WanderNear`). It's self-contained: it points the
new session at the docs, restates the rules, and defines the next task (no `<<PICK ONE>>`).

---

```
We're continuing WanderNear — a native Android (Kotlin + Jetpack Compose) local-guide app for travellers.

Read these two files FULLY before doing anything:
- CLAUDE.md          (conventions, decisions, full milestone log)
- PROJECT_STATUS.md  (current status, build/run steps, gotchas, vision backlog)

State: latest commit fcb9764, working tree clean, everything verified on my Pixel 6 and pushed to origin/main.

Standing rules — follow exactly:
- Plan first, get my approval, THEN build. Small, well-commented steps a beginner can follow.
- Always use the `ponytail` skill (simplest solution, no over-engineering) on coding, the
  `ui-ux-pro-max` skill on any UI/UX work, and `ruflo` — search its "wandernear" namespace
  memory before planning. (Don't re-dump the skills every time; apply them.)
- NEVER hallucinate: every recommendation comes from a real retrieved SQLite row; refuse honestly
  when there's no match. The AI only REWORDS retrieved rows — it never fetches or invents.
  Keep core/ free of Android imports.
- NEVER send my GPS off the device. Fetch data by city/area NAME only; rank "near me" on-device.
- Verify each change: build with the Android Studio JBR, run on my connected Pixel 6 for any UI
  change (screenshot it), adversarially review, THEN commit + push with a clear message.
  Commits are authored by me ALONE — never add a Co-Authored-By or "Generated with Claude Code" line.
- adb is NOT on PATH — use C:\Users\sowad\AppData\Local\Android\Sdk\platform-tools\adb.exe .
  Run builds/adb via PowerShell (not Git Bash). JAVA_HOME = C:\Program Files\Android\Android Studio\jbr .
  PowerShell mangles multi-line git -m; use `git commit -F <file>`. `>` corrupts PNGs — use
  `adb shell screencap -p /sdcard/x.png` then `adb pull`.

NEXT TASK — make the app ONLINE-FIRST with offline as the FALLBACK, and remove the bundled Melbourne.
Agreed model (grounding + privacy stay intact):
- Online  -> fetch the city's data fresh from the internet (OpenStreetMap + Wikipedia), save it on the phone.
- Offline -> fall back to that saved data. Offline still works; it's the fallback now, not the default.
- The fetcher hits the internet; the AI only rewords the fetched-and-stored rows (still can't invent).
- Fetch by city/area NAME; rank "near you" locally — my GPS never leaves the phone.

Do it in safe phases (plan EACH with me first):
1. Remove built-in Melbourne + online-first first run: fresh app -> "No city yet" welcome -> fetch my
   city while online -> fully offline after. (The fetch flow + the "No city yet" screen already exist.)
2. Enrich fetched cities: add Wikipedia place "stories" to the on-device fetch so ANY city gets the
   travel-buddy Listen / rich "Worth visiting" that only the bundled Melbourne has today.
3. Auto-refresh the active city when online (the deferred background refresh, M6.4e).

IMPORTANT timing: my phone is often in AIRPLANE MODE with only Melbourne installed. Do NOT remove the
bundled Melbourne until I'm online / have fetched another city, or I'll be left with no data.

Start with Phase 1: search ruflo "wandernear", read the code it touches, propose a short plan, and wait
for my go-ahead.
```

---

## What we did THIS session (all committed + pushed, verified on the Pixel 6)

Newest first — commits `e646d02` → `fcb9764`:

| Commit | What it delivered |
|---|---|
| `fcb9764` | **Delete your last city.** Removed the "keep at least one city" guard; deleting your only city now shows a friendly **"No city yet"** welcome instead of crashing. New `CityDatabase.hasAnyCity(context)` gates every home query/search so a missing pack never reaches `open()`. Fixed a trap: the "Restore built-in Melbourne" button was hidden at zero cities — now shown whenever Melbourne is hidden. |
| `462388f` | **Deletable built-in Melbourne (restorable).** Melbourne is bundled + auto-re-seeds, so a hidden-marker file (`melbourne.db.hidden`) makes `seedBundled` bail out; `CityDatabase.deleteBundled`/`restoreBundled` toggle it; a "Restore built-in Melbourne" button re-seeds from the APK. |
| `40bcb67` | **Travel-buddy story alerts + Listen (TM.4).** Passing a place that has a Wikipedia summary → a single grounded "there's a story here" alert → **Read** (in-app `StorySheet` with the stored summary + CC BY-SA + Directions) or **Listen** (on-device `TextToSpeech` reads the grounded summary aloud). Ask-first, one de-duped alert per place. TTS is owned by `MainActivity` (singleTop), opened via `getActivity` intents — no background service to zombie. Adversarially reviewed (5 fixes). |
| `98a2088` | **Warmer home hero + delete downloaded cities.** Hero now opens with a time-aware greeting ("Good morning/afternoon/evening" + a pin on the place name) replacing the clinical "WHERE YOU ARE". Each downloaded city got a trash button (`CityPackBuilder.deleteInstalled`). |
| `13acae7` | **Full UI redesign** — a warm **teal + amber** design system. New `ui/theme/` (hand-tuned light+dark `ColorScheme` + `categoryTint`, type scale, shapes, edge-to-edge) and `ui/Components.kt` (`WnCard`, `SectionHeader`, `CategoryBadge`, `WnActionButton`, `MoodChip`). Every screen reworked (hero, colourful place cards, chat bubbles, pill + circular-FAB input, Preferences, My Trips); real vector nav icons via `material-icons-extended`. **No data/retrieval/grounding logic changed.** Adversarially reviewed by 2 agents (12 fixes: touch targets, WCAG contrast, RTL icons, a latent home-dedup bug). |
| `e646d02` | **Preference-aware retrieval (PH.1).** Fixed the reported bug: with faith set (e.g. Buddhist), "I want to see the religious places" returned nothing. `QueryParser` now understands "religious/religion/faith/spiritual", and a saved faith filters worship (Buddhist → Buddhist temples), mirroring the diet→food rule; the reply reads it back ("Buddhist places of worship"). New JVM tests. |

## What's NEXT (approved direction — NOT started)

The **online-first, offline-fallback re-architecture** described in the prompt box above, in 3 phases.
Key decisions already made with the owner:
- Remove the bundled Melbourne; the app fetches the user's city from the internet (already possible via
  `CityPackBuilder` / "Add a city"), stores it, and works offline after that.
- Offline is NOT optional — fetched cities are still saved and fully usable offline; there's just no
  pre-loaded city.
- Grounding and privacy are non-negotiable and stay: deterministic fetch → grounded SQLite rows → AI
  rewords them; fetch by area name, rank near-me on-device.
- Fetched cities are currently "thinner" than bundled Melbourne (no place-level Wikipedia summaries →
  no Listen / barer "Worth visiting"); Phase 2 adds that enrichment.
- The bundled Melbourne is the ONLY pack with place summaries + the richest content today.

## One-glance state of the app

- Opens on a warm home (greeting + your suburb/city, essentials as pills), a "mood" chip row, a "For you"
  card of grounded places matching your Preferences, prayer times / nearest place of worship for your
  faith, "daily needs", festivals — then a grounded chat with reworded AI replies + rich cards.
- Travel Mode: ongoing banner + "around you now" + grounded "worth a visit" / "there's a story here"
  alerts (Read / Listen). Cities: switch, add (download any city), delete (incl. Melbourne, restorable).
- Bundled Melbourne pack (v3): ~22.6k places incl. culture venues + 22 festivals; worship for every faith.
- JVM unit tests green (grounding, query parser, classifier, festivals, prayer times, nearby).
- Full detail: `CLAUDE.md` (milestone log + decisions) and `PROJECT_STATUS.md` (status, gotchas, vision).
