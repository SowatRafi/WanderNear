# Next-session starter prompt

Copy everything in the box below and paste it as your first message in a new
Claude Code session (run from `C:\Users\sowad\Documents\WanderNear`).
Replace `<<PICK ONE>>` at the bottom with the feature you want — options are
listed underneath the box.

---

```
We're continuing work on WanderNear (offline-first, private, native Android local-guide app for travellers).

First, read these two files fully before doing anything:
- CLAUDE.md          (project conventions, decisions, full milestone log)
- PROJECT_STATUS.md  (current status + handoff, build/run steps, gotchas, vision backlog)

Status: everything below is DONE, verified on my Pixel 6, and pushed. Latest commit d76d01b; working tree clean.
- M1 data pipeline · M2 app + templates + GPS · M3 Travel Journal · M4 on-device AI (Gemma 4 + grounding guard)
- M5 offline voice · Travel Mode (TM.1-TM.3 "Around you now") · M6.1 City Info · M6.2 Safety · M6.3 Shopping
- M6.4 download-a-city (a-d, incl. the real "Download data for [city]?" UI) · M6.5 traveller home
- M6.6 culture venues + annual festivals (Wikipedia, no invented dates)
- PT.1 on-device prayer times + nearest mosque · worship for ALL faiths (Christian/Hindu/Jewish/Buddhist/Sikh/Muslim)
- A calmer, personalised home: quick-start chips + a "For you" card driven by my Preferences; cards de-duplicated.

Follow the project rules exactly:
- Plan first, get my approval, THEN build. Small, well-commented steps a beginner can follow.
- Always use the `ponytail` skill on coding (simplest solution, no over-engineering),
  the `ui-ux-pro-max` skill on any UI/UX work, and `ruflo` as well — its cross-session
  memory has notes under namespace "wandernear", so search it before planning.
- Never hallucinate: every recommendation must come from a real SQLite row; refuse
  honestly when there's no match. Keep core/ free of Android imports.
- Never send my location off the device. On-device only (e.g. CityDatabase.nearestSuburb).
- Verify each change: build with the Android Studio JBR, run it on the connected Pixel 6
  for any UI change, adversarially review it, THEN commit + push with a clear message.
  Commits are authored by me ALONE — never add a Co-Authored-By or "Generated with Claude Code" line.

Two things that will bite you if you forget:
- If you rebuild the bundled melbourne.db, BUMP CityDatabase.BUNDLED_PACK_VERSION (currently 3),
  or the new pack never reaches an existing install. (To force a re-seed on my device:
  adb shell run-as com.wandernear rm -f files/melbourne.db files/melbourne.db.version)
- adb is not on PATH. Use: C:\Users\sowad\AppData\Local\Android\Sdk\platform-tools\adb.exe
  Build/adb via PowerShell (not Git Bash). JAVA_HOME = C:\Program Files\Android\Android Studio\jbr

What I want to build next: <<PICK ONE>>

Start by proposing a short plan for that feature and wait for my go-ahead.
```

---

## What's left — pick ONE for the `<<PICK ONE>>` slot

| Option | What it means |
|---|---|
| **PT.2 — proactive prayer/worship nudge** | While Travel Mode is on, quietly update the single banner as a prayer time approaches, pointing to the nearest mosque. Rides the ONE notification (TM.3 pattern), opt-in. |
| **Accommodation** | A new grounded category (OSM `tourism=hotel\|hostel\|guest_house\|motel\|apartment`) added to BOTH the pipeline and the on-device builder — like culture/shopping. Place + Directions + phone only; NEVER price/availability (not free/groundable). |
| **M6.4e — silent background refresh** | WorkManager job that refreshes the active city's pack when online, scoped to the pack's area (never the live GPS fix). Pack versioning already exists. |
| **M7 — Travel Journal v2** | Voice + video diary memos, and a smarter "you forgot this" nudge that surfaces unfinished bucket-list items when you return near a saved place. |
| **Jewish zmanim** | The one other faith with a real astronomically-calculated daily timetable — could join Islam's prayer-times card. |

(Full backlog with the free/offline/groundable verdict per item is the "Vision backlog" section in PROJECT_STATUS.md.)

## Where things stand (one-glance summary)

- **The app right now:** opens on a calm home — your city/suburb, tailored quick-start chips, a
  "For you" card of grounded places matching your Preferences, prayer times / nearest place of
  worship for your faith, "daily needs" (police/hospital/fuel/parking), festivals — then a grounded
  chat below. Everything works offline; nothing leaves the phone.
- **Melbourne pack (bundled, v3):** ~23,451 places incl. 832 culture venues + 22 annual festivals;
  worship grounded for every faith (Christian 929, Buddhist, Muslim, Jewish, Hindu, Sikh …).
- **Tests:** 43 JVM unit tests green (grounding, query parser, classifier, festivals, prayer times, nearby).
- Full detail: `CLAUDE.md` (milestone log + decisions) and `PROJECT_STATUS.md` (status, gotchas, vision).
