# Next-session starter prompt

Copy everything in the box below and paste it as your first message in a new
Claude Code session (run from `C:\Users\sowad\Documents\WanderNear`).

---

```
We're continuing work on WanderNear (offline-first Android local-guide app).

First, read these two files fully before doing anything:
- CLAUDE.md          (project conventions, decisions, milestone log)
- PROJECT_STATUS.md  (current status + handoff, build/run steps, gotchas)

Status: M1–M5, Travel Mode (TM), and M6.1 (City Info) + M6.2 (Safety/police)
are done and pushed (25 commits, latest ea0c75a). Working tree is clean.

Follow the project rules exactly:
- Plan first, get my approval, THEN build. Small, well-commented steps.
- Use the `ponytail` skill on coding (simplest solution, no over-engineering)
  and the `ui-ux-pro-max` skill on any UI/UX work.
- Never hallucinate: every recommendation must come from a real SQLite row;
  refuse honestly when there's no match. Keep `core/` free of Android imports.
- Verify each change (build with the Android Studio JBR; run on the connected
  device when it's a UI change), then commit + push with a clear message.
  Commits are authored by me ALONE — no Co-Authored-By / "Generated with" lines.

Heads-up (from PROJECT_STATUS.md): the bundled melbourne.db is copied from
assets to filesDir only ONCE, so an updated pack won't reach an existing
install without `adb shell pm clear com.wandernear` (or the future M6 refresh).

What I want to build next: <<PICK ONE — e.g. "M6: the Download-a-city flow +
silent background refresh" OR "M6: annual festivals" OR "M6: shopping spots"
OR "M7: smarter you-forgot-this nudge">>.

Start by proposing a short plan for that feature and wait for my go-ahead.
```

---

## Quick reference — what's left

- **M6 (rest):** Download-a-city flow + silent background refresh · shopping spots · annual festivals.
- **M7:** Travel Journal v2 — voice + video memos · smarter "you forgot this" bucket-list nudge.

Pick one, drop it into the `<<PICK ONE …>>` slot, and delete the rest.
