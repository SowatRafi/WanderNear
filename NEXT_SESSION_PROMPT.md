# Next-session starter — WanderNear

Copy the box below and paste it as your **first message** in a new Claude Code session
(run from `C:\Users\sowad\Documents\WanderNear`). It's self-contained: it points the
new session at the docs, restates the rules, and defines the next task.

---

```
We're continuing WanderNear — a native Android (Kotlin + Jetpack Compose) local-guide app for travellers.

Read these three files FULLY before doing anything:
- CLAUDE.md            (conventions, decisions, full milestone log)
- PROJECT_STATUS.md    (current status, build/run steps, gotchas)
- SESSION_HANDOFF.md   (what last session did, and the UNFINISHED work waiting for you)

STATE — read carefully, the working tree is NOT clean:
- Last session completed the ONLINE-FIRST pivot in three phases: L1 live chat (05fd7e0),
  L2 live home (ef10ac1), L3 offline fallback (7ef81c7). All committed, pushed, verified on my Pixel 6.
- The app now fetches places LIVE from OpenStreetMap by default; downloading a city is my
  OPTIONAL choice for offline use. Bundled Melbourne is no longer auto-loaded.
- UNCOMMITTED work in progress: "Phase 2" — Wikipedia stories for live results
  (new data/Wikipedia.kt + changes to Place.kt, LiveSource.kt, ChatScreen.kt).
  It builds and tests pass, but it is NOT verified and it still has debug logging in it.

Standing rules — follow exactly:
- Plan first, get my approval, THEN build. Small, well-commented steps a beginner can follow.
- Always use the `ponytail` skill (simplest solution, no over-engineering) on coding, the
  `ui-ux-pro-max` skill on any UI/UX work, and `ruflo` — search its "wandernear" namespace
  memory before planning. (Don't re-dump the skills every time; apply them.)
- NEVER hallucinate: every recommendation comes from a REAL retrieved row — live OSM or a
  downloaded pack. Refuse honestly when there's no match. The AI only REWORDS retrieved rows;
  it never fetches or invents. Keep core/ free of Android imports.
- NEVER send my GPS off the device. Fetch data by city/area NAME only; rank "near me" on-device.
- Verify each change: build with the Android Studio JBR, run on my connected Pixel 6 for any UI
  change (screenshot it), adversarially review, THEN commit + push with a clear message.
  Commits are authored by me ALONE — never add a Co-Authored-By or "Generated with Claude Code" line.
- adb is NOT on PATH — use C:\Users\sowad\AppData\Local\Android\Sdk\platform-tools\adb.exe .
  Run builds/adb via PowerShell (not Git Bash). JAVA_HOME = C:\Program Files\Android\Android Studio\jbr .
  PowerShell mangles multi-line git -m; use `git commit -F <file>`. `>` corrupts PNGs — use
  `adb shell screencap -p /sdcard/x.png` then `adb pull`.

NEXT TASK — finish and verify Phase 2 (Wikipedia "stories" for live results).
The point: give ANY city the rich write-ups only the old bundled Melbourne had, so attraction
cards show a real "why" and the home's "Worth visiting" shows a 1-2 line snippet.
Grounding rule for this feature: fetch a summary ONLY for a place OpenStreetMap ALREADY links
to Wikipedia (its `wikipedia` or `wikidata` tag). Never guess which article belongs to a place.

Do this:
1. Review the uncommitted Phase 2 code and tell me plainly whether it's sound.
2. REMOVE the leftover debug logging — two android.util.Log.d("WNWIKI", ...) lines in
   data/LiveSource.kt (~lines 128 and 132).
3. Verify the happy path properly on my Pixel 6. Last session never actually saw a story
   render, because everything it tested (Werribee, outer Melbourne) has NO OSM wiki tags.
   Use a small, landmark-rich area instead (e.g. Federation Square or Melbourne CBD), then ask
   for "attractions"/"museums" and screenshot a real Wikipedia sentence on the cards.
   If it genuinely can't show one, tell me that honestly — do NOT loosen the OSM-link rule to
   force a match, and do not fake it.
4. Then commit + push.

After that, ask me before starting either of these:
- P2c: fetch summaries during a DOWNLOAD too, so a downloaded city also gets offline stories
  and Travel Mode's Read/Listen.
- Phase 3 (M6.4e): silent background refresh of the active area when online.

Heads-up from last session's testing (saves you pain):
- My phone runs a VPN. When WiFi drops, the VPN still claims to be connected — that already
  bit us once. `adb shell svc wifi disable` is also async: wait ~5s and confirm
  "Active default network: none" before you launch, and re-check it between offline tests
  because the phone re-enables WiFi on its own.
- An empty files/packs/ and a missing melbourne.db is CORRECT now (online-first), not a bug.
- My phone currently has NO downloaded city and its active area is Melbourne (live), so it
  will show "You're offline" if it loses signal. That's the intended behaviour.
```
