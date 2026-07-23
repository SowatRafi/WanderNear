# Next-session starter prompt

Copy everything in the box below and paste it as your first message in a new
Claude Code session (run from `C:\Users\sowad\Documents\WanderNear`).
Replace `<<PICK ONE>>` with the feature you want — options are listed underneath.

---

```
We're continuing work on WanderNear (offline-first, private Android local-guide app for travellers).

First, read these two files fully before doing anything:
- CLAUDE.md          (project conventions, decisions, full milestone log)
- PROJECT_STATUS.md  (current status + handoff, build/run steps, gotchas)

Status: M1–M5, Travel Mode, and M6.1–M6.3 + M6.4a–c + M6.5 are done and pushed.
Latest feature commit: 08fc72e (M6.5 traveller home). Working tree is clean.

Follow the project rules exactly:
- Plan first, get my approval, THEN build. Small, well-commented steps.
- Always use the `ponytail` skill on coding (simplest solution, no over-engineering),
  the `ui-ux-pro-max` skill on any UI/UX work, and `ruflo` as well — its cross-session
  memory already has notes under namespace "wandernear", so search it before planning.
- Never hallucinate: every recommendation must come from a real SQLite row; refuse
  honestly when there's no match. Keep core/ free of Android imports.
- Never send my location off the device. "Which suburb am I in" is derived ON-DEVICE
  from the pack (CityDatabase.nearestSuburb). No user data ever leaves the phone.
- Verify each change (build with the Android Studio JBR; run it on the connected
  Pixel 6 for any UI change), adversarially review it, then commit + push with a
  clear message. Commits are authored by me ALONE — never add a Co-Authored-By or
  "Generated with Claude Code" line.

Two things that will bite you if you forget:
- If you rebuild the bundled melbourne.db, BUMP CityDatabase.BUNDLED_PACK_VERSION,
  or the new pack will never reach an existing install.
- adb is not on PATH. Use:
  C:\Users\sowad\AppData\Local\Android\Sdk\platform-tools\adb.exe

What I want to build next: <<PICK ONE>>

Start by proposing a short plan for that feature and wait for my go-ahead.
```

---

## What's left — pick ONE for the `<<PICK ONE>>` slot

| Option | What it means |
|---|---|
| **M6.4d — the real "Download data for [city]?" UI** | Search a city → confirm the geocoded match → progress → switch to it. The on-device builder already works; this is the UI. **Also delete the temporary `TempPackBuilderSection` dev trigger in `PreferencesScreen.kt`.** |
| **M6.4e — silent background refresh** | WorkManager job that refreshes the active city's pack when online, and swaps it in. Pack versioning already exists. |
| **M6 — annual festivals** | The last M6 item. Needs a decision FIRST on a free/offline/**groundable** source — recurring dates are the hard part (OSM has none; Wikidata dates are unreliable). |
| **M7 — Travel Journal v2** | Voice + video diary memos, and a smarter "you forgot this" nudge that surfaces unfinished bucket-list items when you return near a saved place. |

## Where things stand (one-glance summary)

- **Done:** M1 data pipeline · M2 app + templates + GPS · M3 Travel Journal · M4 on-device AI
  (Gemma 4 + grounding guard) · M5 offline voice · Travel Mode · M6.1 City Info · M6.2 Safety ·
  M6.3 Shopping · **M6.4a–c on-device "download a city" + active-city switch** · **M6.5 traveller home**.
- **The app right now:** opens on a traveller home showing your ACTUAL suburb (on-device),
  "Worth visiting nearby" (grounded suggestions), and "Daily needs near you"
  (nearest police / hospital / fuel / parking) — then a grounded chat below.
- **Melbourne pack:** 22,624 places (148 hospital, 917 parking, 868 fuel, 480 shopping,
  114 police, 3,858 with a suburb) + full-text search.
- Full detail lives in `CLAUDE.md` (milestone log) and `PROJECT_STATUS.md` (status + gotchas).
