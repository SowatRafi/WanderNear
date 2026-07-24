# WanderNear

An offline-first, private, native **Android** app that acts like a knowledgeable
local friend. You tell it what you're into ("I'm vegetarian and I love temples")
and it recommends genuine local spots — food, festivals, hidden hangouts,
cultural/religious landmarks, and outdoor adventures — with a short reason why.

Everything runs **on the phone**: the city data and the AI both live on-device,
so it works fully in airplane mode and no personal data ever leaves the phone.

## How it stays honest (never invents places)

The app treats an on-device **SQLite database as the only source of truth**. It
finds places with plain database queries, and the AI is only ever allowed to
reword places that were actually found. If nothing matches, it says so — and
offers to download or refresh that city's data — instead of making something up.

## Data sources (all free, no API keys)

- **OpenStreetMap** via the Overpass API — places, cuisine, dietary tags.
  Data © OpenStreetMap contributors, licensed under the ODbL.
- **Wikipedia / Wikidata** — cultural and religious significance.
  Text under CC BY-SA 4.0.

## Repository layout

```
pipeline/   Python scripts that build a city's data pack (run on your computer)
app/        The native Android app (added in Milestone 2)
CLAUDE.md   Project conventions, decisions, and constraints
```

## Building a city data pack (Milestone 1)

```bash
cd pipeline
pip install -r requirements.txt
python fetch_osm.py          # 1. download places from OpenStreetMap
python enrich_wikipedia.py   # 2. add cultural summaries from Wikipedia
python build_db.py           # 3. build out/melbourne.db (the data pack)
python fetch_festivals.py    # 4. add the city's annual festivals from Wikipedia
python query_demo.py         # 5. prove it works with real searches
```

`fetch_festivals.py` runs **after** `build_db.py` — it opens the finished pack and
fills its `event` table. Skipping it just leaves a pack with no festivals (the app
then shows none rather than inventing any).

To build a different city, change `CITY` in `pipeline/config.py` and re-run. If you
rebuild the bundled `melbourne.db`, bump `CityDatabase.BUNDLED_PACK_VERSION` so the
new pack reaches existing installs.
