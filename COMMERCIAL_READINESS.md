# WanderNear — what shipping commercially actually requires

> Assessment written 2026-07-28, against commit `8417a35`. Ordered by what BLOCKS a
> release, not by what's most fun to build. Nothing here is a guess about the code —
> every claim names the file or quotes the source.

---

## 0. The blocker: you cannot ship this on the free endpoints

The app's entire data path is community infrastructure that explicitly excludes what
you're planning to do.

**Overpass** (`LiveSource.OVERPASS_ENDPOINTS`) — the operators' own usage guide lists
as misuse:

> "Operating commercial applications on public instances"

and warns against "setting up an app for more than just OSM mappers and relying on the
public instances as backend". Fair-use guidance is ~10,000 requests/day and <1 GB/day
across *everyone* using that endpoint; over it you get HTTP 429.

**Nominatim** (`CityPackBuilder.find`) — max **1 request per second**, and:

> "Applications... must make sure that they can switch the service at our request at any
> time (in particular, switching should be possible without requiring a software update)."

Our endpoints are compiled-in constants, so we already fail that clause.

Today every home load = 1 Overpass call. At even 2,000 daily users that's ~10,000+
calls/day from one app — the entire community budget, and we'd be throttled into the
"OpenStreetMap didn't answer" state permanently. This session already showed what that
looks like: both mirrors down for ~15 minutes and the app had nothing to show.

### The three real options

| | Approach | Cost | Verdict |
|---|---|---|---|
| **A** | Self-host Overpass + Nominatim on a VPS | ~$40–150/mo, real ops burden (planet imports, updates) | Works, but you now run infrastructure |
| **B** | Commercial OSM API (Geoapify, MapTiler, Stadia…) | Per-request pricing, free tiers exist | Fastest to ship; recurring cost scales with users |
| **C** | **Pre-built regional packs on a CDN** | Static file hosting (~$1–5/mo) | Recommended |

**Recommended: C, with live as an optional extra.**

You already have the whole machine for it: `pipeline/` builds a pack for ANY city from
free sources, `CityPackBuilder` proves the on-device path, and `CityDatabase` reads packs
today. Build packs on your PC, host them as plain files, let the app download the ones the
user needs.

Why it's the strongest commercial position, not a step backwards:

- **No per-user API calls**, so no rate limit and no one else's terms to violate.
- **Works with no signal** — the actual traveller use case, on a plane, abroad, on a SIM
  you haven't bought yet.
- **Fast everywhere.** A local SQLite query beats a 30-second Overpass round trip.
- **Costs ~nothing** and doesn't scale with users.

Note the irony honestly: this is close to the offline-first architecture you reversed on
2026-07-26. That reversal was right for the *experience* — nobody should have to pick a
city before the app works. Keep that. The fix is that **location-first and offline-first
are not opposites**: detect where the user is from their fix (as we do now), then serve it
from a downloaded regional pack instead of a live call. Live becomes a top-up for fresh
data, hitting *your* endpoint, not the community's.

Whatever you choose, **move the endpoints into a remote config file** the app fetches, so
you can switch providers without a release. Nominatim requires this; it's good practice
regardless.

---

## 1. "Works in any place" — where it currently doesn't

| Gap | Reality today | Fix |
|---|---|---|
| **Emergency numbers** | `CountryFacts` has **30 countries**. Everywhere else shows nothing. | Full ~195-country table of emergency number + currency. Static data, no API. **Safety-critical — do this first.** |
| **Fixed 3 km radius** | `HERE_RADIUS_KM = 3.0`. In Shinjuku that's thousands of rows in one query; in the outback it's zero. | Adaptive: start small, widen until you have enough results (and cap the row count in dense areas). |
| **No translation** | `strings.xml` holds **1 string**. 50+ English strings hardcoded in Kotlin. | Extract to resources, then translate. Also enable RTL (`supportsRtl`) and test Arabic/Hebrew layout. |
| **Place names** | OSM `name` is the local script — correct on the street sign, unreadable to a visitor in Tokyo or Cairo. | Show `name` + `name:en` (or the phone's locale) when they differ. Data is already in the tags. |
| **Voice is English-only** | Bundled `vosk-model-small-en-us`. | Per-language model, downloaded on demand — which also fixes the APK size below. |
| **AI is English-only** | Gemma prompts and templates are English. | Templates are the guaranteed path anyway; translate those first. |
| **Prayer times** | ✅ Already global — astronomical calculation, works anywhere. | — |

---

## 2. Optimization — measured, not guessed

1. **Uncategorised queries fetch EVERY category.** `LiveSource.search` passes
   `categories = null` when the parser found no category, so "zoo" pulls food + worship +
   attractions + outdoor + shopping + culture + safety + health + fuel + parking over the
   whole box. This visibly failed on-device this session. Infer a category set from the
   free-text terms, or cap it.
2. **The cache is memory-only and 10 minutes** (`HomeCache`). Every cold start re-fetches.
   Persist it — with packs (option C) this problem disappears entirely.
3. **The 190 MB debug APK.** The 40 MB Vosk model is a bundled Java resource shipped to
   every user, used by a minority. Make it an on-demand download. (Measure a release
   build with R8 before deciding anything else.)
4. **The 2.6 GB on-device AI is a commercial liability.** ~50 s first load on a Pixel 6,
   needs minSdk 31, and most users will never wait for a 2.6 GB download. It's already
   opt-in and templates already work without it — keep it that way and consider a much
   smaller model. This is a "Pro" feature at best, never the default experience.
5. **One Overpass call still blocks the whole home.** With packs this is a local query;
   until then, render the cards you have as they resolve rather than all-or-nothing.

---

## 3. Features worth adding — all free, all groundable

Ranked by traveller value per unit of work. Every one is a real OSM tag, so nothing here
breaks rule #5.

| Feature | OSM source | Why it matters |
|---|---|---|
| **"Open now"** | `opening_hours` | The single most useful missing fact. "Is it open?" is the question every traveller asks. Parse the tag on-device; say "hours not listed" when absent — never guess. |
| **Accommodation** | `tourism=hotel\|hostel\|guest_house\|motel` | Already scoped in the vision backlog. Show place + phone + directions only — never a price or availability, which aren't groundable. |
| **Public transport** | `public_transport=station\|stop_position`, `highway=bus_stop` | Enormous for a traveller in an unfamiliar city. "Nearest station" is pure retrieval. |
| **Practical needs** | `amenity=toilets\|drinking_water\|pharmacy\|atm\|post_office` | Unglamorous and constantly needed. Same generic pipeline as `safety`/`shopping`. |
| **Accessibility** | `wheelchair=yes\|limited\|no` | A genuine differentiator — very few travel apps do this, and it's a filter, not a guess. |
| **Multi-day itinerary** | Journal (M3) | Group saved places by day, on-device. No new data source. |
| ~~Currency conversion~~ | — | Needs a live rate feed. Not free, not offline, and a stale rate is a wrong fact. **Skip.** |

---

## 4. Legal and store checklist — do not skip

- **ODbL share-alike.** The bundled `assets/melbourne.db` is a *derived database* shipped
  inside the APK. Distributing it carries ODbL obligations. It's already dead weight after
  the location-first change (nothing in the UI offers it any more) — **delete it**, and get
  advice before distributing any pre-built pack under option C.
- **Attribution** is present (`© OpenStreetMap contributors · Wikipedia CC BY-SA 4.0`) — keep
  it visible on every screen showing data, and keep CC BY-SA on Wikipedia text.
- **Privacy policy — now mandatory.** Since 2026-07-28 the app sends a bounding box derived
  from the user's position to a third party. The Play Store data-safety form must declare
  approximate location leaving the device. The in-app wording is already honest; the policy
  must match it.
- **Permissions:** location and microphone both need in-context justification for review.
- Licences of what we ship are clean: Gemma Apache-2.0, Vosk Apache-2.0, OSM ODbL,
  Wikipedia CC BY-SA.

---

## 5. Suggested order

1. **Emergency numbers for every country.** Small, static, safety-critical, no dependencies.
2. **Decide the data backend** (recommend C). Everything else depends on this.
3. **Adaptive radius + category narrowing.** Makes it genuinely work anywhere, and cuts load.
4. **"Open now".** Highest-value feature per hour of work.
5. **Extract strings + i18n**, then translate the top languages for your market.
6. **Voice model on demand**, drop the bundled asset.
7. **Accommodation + transport + practical needs** — three more generic categories.
8. Store prep: privacy policy, data-safety form, release-build size, delete the bundled pack.

Items 1, 3 and 4 are self-contained and can start immediately. Item 2 is a business
decision only you can make.
