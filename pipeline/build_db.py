"""Stage 3 of the pipeline: build the SQLite data pack the app will ship.

Reads : pipeline/out/<city>_osm.json        (raw places, from stage 1)
        pipeline/out/<city>_enriched.json   (Wikipedia blurbs, from stage 2)
Writes: pipeline/out/<city>.db              (the finished data pack)

Run it with:  python build_db.py
"""

import json
import os
import sqlite3
from datetime import date, datetime, timezone

import config

# Which OSM tag values map to which of our app categories.
FOOD = {"restaurant", "cafe", "fast_food", "food_court", "bar", "pub", "ice_cream"}
TOURISM = {"attraction", "viewpoint", "museum", "artwork", "gallery",
           "zoo", "theme_park", "aquarium"}
NATURAL = {"beach", "peak", "waterfall", "water", "cliff", "cave_entrance",
           "spring", "bay", "hot_spring", "volcano"}
LEISURE = {"park", "nature_reserve", "garden", "beach_resort"}
# Traveller shopping destinations — kept tight (markets, malls, department stores)
# on purpose, so we surface real "go shopping here" spots, not every corner store.
SHOPPING = {"mall", "department_store"}


def classify(tags):
    """Decide a place's (category, subcategory) from its OSM tags."""
    amenity = tags.get("amenity")
    if amenity in FOOD:
        return "food", amenity
    if amenity == "place_of_worship":
        return "worship", tags.get("religion") or "place_of_worship"
    if amenity == "police":
        return "safety", "police"
    if amenity == "marketplace":
        return "shopping", "marketplace"
    if tags.get("shop") in SHOPPING:
        return "shopping", tags["shop"]
    if tags.get("tourism") in TOURISM:
        return "attraction", tags["tourism"]
    if tags.get("historic"):
        return "attraction", tags["historic"]
    if tags.get("natural") in NATURAL:
        return "outdoor", tags["natural"]
    if tags.get("leisure") in LEISURE:
        return "outdoor", tags["leisure"]
    if tags.get("route") == "hiking":
        return "outdoor", "hiking"
    return None, None


def coordinates(element):
    """Get a lat/lon for a place (nodes have their own; areas use 'center')."""
    if "lat" in element and "lon" in element:
        return element["lat"], element["lon"]
    center = element.get("center")
    if center:
        return center["lat"], center["lon"]
    return None, None


def address(tags):
    """Build a readable address from OSM's addr:* tags, if present."""
    street = " ".join(
        part for part in [tags.get("addr:housenumber"), tags.get("addr:street")] if part
    )
    parts = [p for p in [street, tags.get("addr:suburb"),
                         tags.get("addr:city"), tags.get("addr:postcode")] if p]
    return ", ".join(parts) if parts else None


def city_facts_for_build(city_meta):
    """Country + population for the city row. Use whatever stage 1 already
    captured; for an older cache that predates those fields, geocode once as a
    best-effort fallback. Never guesses — returns None when unknown."""
    if "country" in city_meta or "population" in city_meta:
        return city_meta.get("country"), city_meta.get("population")
    try:
        import fetch_osm
        return fetch_osm.city_facts(fetch_osm.geocode(config.CITY))
    except Exception as err:                      # offline / API down → build anyway
        print(f"  (couldn't fetch city facts: {err}; leaving country/population blank)")
        return None, None


def main():
    with open(config.OSM_JSON, encoding="utf-8") as fh:
        raw = json.load(fh)
    city_meta, elements = raw["city"], raw["elements"]

    try:
        with open(config.ENRICHED_JSON, encoding="utf-8") as fh:
            enrichment = json.load(fh)
    except FileNotFoundError:
        enrichment = {}
        print("(no enrichment file found — building without Wikipedia summaries)")

    # Start a fresh database each time so re-runs are clean and repeatable.
    if os.path.exists(config.DB_PATH):
        os.remove(config.DB_PATH)
    conn = sqlite3.connect(config.DB_PATH)
    with open(os.path.join(os.path.dirname(__file__), "schema.sql"), encoding="utf-8") as fh:
        conn.executescript(fh.read())

    # Insert the single city row. Country + population come from the geocode
    # (real values, never guessed); either may be NULL if the source lacks them.
    country, population = city_facts_for_build(city_meta)
    conn.execute(
        """INSERT INTO city (id, name, country, population, osm_type, osm_id,
               min_lat, min_lng, max_lat, max_lng, data_version, fetched_at, attribution)
           VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (city_meta["name"], country, population, city_meta["osm_type"], city_meta["osm_id"],
         city_meta["min_lat"], city_meta["min_lng"], city_meta["max_lat"],
         city_meta["max_lng"], date.today().isoformat(),
         datetime.now(timezone.utc).isoformat(), config.ATTRIBUTION),
    )

    kept, skipped = 0, 0
    counts = {}   # category -> how many kept; grows as new categories appear

    for element in elements:
        tags = element.get("tags", {})
        name = tags.get("name")
        if not name:                      # nothing to recommend without a name
            skipped += 1
            continue

        category, subcategory = classify(tags)
        if category is None:
            skipped += 1
            continue

        lat, lng = coordinates(element)
        if lat is None:                   # can't place it on a map → skip
            skipped += 1
            continue

        info = enrichment.get(f"{element['type']}/{element['id']}", {})

        cursor = conn.execute(
            """INSERT OR IGNORE INTO place (city_id, osm_type, osm_id, name,
                   category, subcategory, lat, lng, address, cuisine, religion,
                   denomination, opening_hours, phone, website, wikidata_qid,
                   summary, summary_url, summary_license, thumbnail_url)
               VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (element["type"], element["id"], name, category, subcategory, lat, lng,
             address(tags), tags.get("cuisine"), tags.get("religion"),
             tags.get("denomination"), tags.get("opening_hours"),
             tags.get("phone") or tags.get("contact:phone"),
             tags.get("website") or tags.get("contact:website"),
             tags.get("wikidata"), info.get("summary"), info.get("summary_url"),
             info.get("summary_license"), info.get("thumbnail_url")),
        )
        if cursor.rowcount == 0:          # duplicate OSM feature, already inserted
            continue
        place_id = cursor.lastrowid
        kept += 1
        counts[category] = counts.get(category, 0) + 1

        # Dietary tags: diet:halal=yes, diet:vegetarian=only, etc.
        for key, value in tags.items():
            if key.startswith("diet:"):
                conn.execute(
                    "INSERT OR IGNORE INTO place_diet (place_id, diet, value) VALUES (?, ?, ?)",
                    (place_id, key[len("diet:"):], value),
                )

        # Mirror the searchable text into the full-text index (same rowid).
        conn.execute(
            "INSERT INTO places_fts (docid, name, summary, cuisine, subcategory) "
            "VALUES (?, ?, ?, ?, ?)",
            (place_id, name, info.get("summary"), tags.get("cuisine"), subcategory),
        )

    conn.commit()
    conn.close()

    print(f"\nBuilt {config.DB_PATH}")
    print(f"  kept {kept} places, skipped {skipped} (no name / unknown type / no location)")
    for category, n in counts.items():
        print(f"    {category:11} {n}")


if __name__ == "__main__":
    main()
