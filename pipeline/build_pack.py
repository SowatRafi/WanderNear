"""Build a WanderNear data pack from a Geofabrik OpenStreetMap extract.

WHY THIS EXISTS
---------------
The other pipeline scripts fetch from the public Overpass API. That is fine for one
person building one city, but Overpass's own usage guide names "operating commercial
applications on public instances" as misuse and asks for bulk work to use a planet dump
instead. It is also simply unreliable — during development it failed for the best part
of an hour, and the app has nothing to show when it does.

So for shipping, packs are built HERE, on your own machine, from Geofabrik's free daily
regional extracts, and then hosted as ordinary static files. No API calls per user, no
rate limits, nothing that can go down while somebody is using the app.

    https://download.geofabrik.de/   (free, ODbL, updated daily)

WHAT IT PRODUCES
----------------
Exactly the same SQLite pack the app already reads — same `schema.sql`, same columns —
so nothing in the app has to change to open it. The classification rules are IMPORTED
from build_db.py rather than copied, so the pack can never drift from what the pipeline
and `core/pack/OsmClassifier.kt` agree a "restaurant" or an "attraction" is.

USAGE
-----
    python build_pack.py --pbf victoria-latest.osm.pbf --name "Melbourne" \\
        --country Australia --out out/melbourne.db

    # only the places inside a bounding box (south,west,north,east):
    python build_pack.py --pbf victoria-latest.osm.pbf --name "Werribee" \\
        --country Australia --bbox -37.95,144.60,-37.85,144.72 --out out/werribee.db

Needs pyosmium:  python -m pip install osmium
"""

import argparse
import os
import sqlite3
import sys
from datetime import date, datetime, timezone

import osmium

import build_db          # classify() + address() — the single source of truth
import config            # ATTRIBUTION


def parse_bbox(text):
    """"south,west,north,east" → a 4-tuple of floats, or None."""
    if not text:
        return None
    parts = [float(p) for p in text.split(",")]
    if len(parts) != 4:
        raise ValueError("bbox must be south,west,north,east")
    return tuple(parts)


def in_bbox(lat, lng, bbox):
    if bbox is None:
        return True
    south, west, north, east = bbox
    return south <= lat <= north and west <= lng <= east


def way_centre(way):
    """One representative point for a way — the average of its nodes.

    This is what OSM's own `out center` gives us, and it's all the app needs: a pin to
    navigate to. Returns None when the extract has no locations for the way's nodes,
    which happens at the edges of a regional extract where ways run off the boundary.
    """
    lats, lngs = [], []
    for node in way.nodes:
        try:
            if node.location.valid():
                lats.append(node.location.lat)
                lngs.append(node.location.lon)
        except osmium.InvalidLocationError:
            continue
    if not lats:
        return None
    return sum(lats) / len(lats), sum(lngs) / len(lngs)


def main():
    ap = argparse.ArgumentParser(description="Build a WanderNear pack from an OSM extract")
    ap.add_argument("--pbf", required=True, help="path to a Geofabrik .osm.pbf extract")
    ap.add_argument("--name", required=True, help="city/region name shown in the app")
    ap.add_argument("--country", default=None, help="English country name (for currency/emergency)")
    ap.add_argument("--bbox", default=None, help="clip to south,west,north,east")
    ap.add_argument("--out", required=True, help="output .db path")
    args = ap.parse_args()

    bbox = parse_bbox(args.bbox)
    if not os.path.exists(args.pbf):
        sys.exit(f"No such extract: {args.pbf}")

    # Start fresh every run, so a rebuild is repeatable and never half-old.
    if os.path.exists(args.out):
        os.remove(args.out)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    conn = sqlite3.connect(args.out)
    with open(os.path.join(os.path.dirname(__file__), "schema.sql"), encoding="utf-8") as fh:
        conn.executescript(fh.read())

    # The city row has to exist before any place can reference it. Its bounding box is
    # filled in at the end, measured from the places we actually kept.
    conn.execute(
        """INSERT INTO city (id, name, country, population, osm_type, osm_id,
               min_lat, min_lng, max_lat, max_lng, data_version, fetched_at, attribution)
           VALUES (1, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?, ?, ?)""",
        (args.name, args.country, date.today().isoformat(),
         datetime.now(timezone.utc).isoformat(), config.ATTRIBUTION),
    )

    kept = skipped = 0
    counts = {}
    # Track the real extent of what we kept, so the app knows which pack covers where
    # the user is standing. Derived from the DATA, never assumed.
    #
    # Seeded with infinities, NOT with latitude limits: longitude runs to +/-180, so
    # seeding min_lng at 90 left Melbourne (~145E) reporting a west edge of 90 — a pack
    # that would have claimed to cover everything from India eastwards, and been offered
    # to users thousands of km away.
    min_lat = min_lng = float("inf")
    max_lat = max_lng = float("-inf")

    print(f"Reading {args.pbf} …")
    # with_locations() keeps a node-location cache so ways can be given a centre.
    # "flex_mem" grows as needed — fine for a country-sized extract on a normal PC.
    processor = osmium.FileProcessor(args.pbf).with_locations("flex_mem")

    for obj in processor:
        # ponytail: nodes and ways only. Relations (a few large parks, some building
        # complexes) need full area assembly for a centre; they're a small minority of
        # named POIs. Add osmium's AreaManager here if they turn out to matter.
        is_node = obj.is_node()
        if not is_node and not obj.is_way():
            continue

        tags = dict(obj.tags)
        name = tags.get("name")
        if not name:                       # nothing to recommend without a name
            skipped += 1
            continue
        category, subcategory = build_db.classify(tags)
        if category is None:
            skipped += 1
            continue

        if is_node:
            if not obj.location.valid():
                skipped += 1
                continue
            lat, lng = obj.location.lat, obj.location.lon
        else:
            centre = way_centre(obj)
            if centre is None:
                skipped += 1
                continue
            lat, lng = centre

        if not in_bbox(lat, lng, bbox):
            continue

        cursor = conn.execute(
            """INSERT OR IGNORE INTO place (city_id, osm_type, osm_id, name,
                   category, subcategory, lat, lng, address, suburb, cuisine, religion,
                   denomination, opening_hours, phone, website, wikidata_qid,
                   summary, summary_url, summary_license, thumbnail_url)
               VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)""",
            ("node" if is_node else "way", obj.id, name, category, subcategory, lat, lng,
             build_db.address(tags), tags.get("addr:suburb"), tags.get("cuisine"),
             tags.get("religion"), tags.get("denomination"), tags.get("opening_hours"),
             tags.get("phone") or tags.get("contact:phone"),
             tags.get("website") or tags.get("contact:website"),
             tags.get("wikidata")),
        )
        if cursor.rowcount == 0:           # same OSM feature seen twice
            continue
        place_id = cursor.lastrowid
        kept += 1
        counts[category] = counts.get(category, 0) + 1
        min_lat, max_lat = min(min_lat, lat), max(max_lat, lat)
        min_lng, max_lng = min(min_lng, lng), max(max_lng, lng)

        for key, value in tags.items():
            if key.startswith("diet:"):
                conn.execute(
                    "INSERT OR IGNORE INTO place_diet (place_id, diet, value) VALUES (?, ?, ?)",
                    (place_id, key[len("diet:"):], value),
                )
        conn.execute(
            "INSERT INTO places_fts (docid, name, summary, cuisine, subcategory) "
            "VALUES (?, ?, NULL, ?, ?)",
            (place_id, name, tags.get("cuisine"), subcategory),
        )

    if kept == 0:
        conn.close()
        os.remove(args.out)
        sys.exit("Kept 0 places — wrong extract, or a bbox that covers nothing.")

    # Now that we know what we kept, record the real extent — this is what tells the app
    # which pack covers where the user is standing.
    conn.execute(
        "UPDATE city SET min_lat = ?, min_lng = ?, max_lat = ?, max_lng = ? WHERE id = 1",
        (min_lat, min_lng, max_lat, max_lng),
    )
    conn.commit()
    conn.execute("VACUUM")               # smallest possible file — it gets downloaded
    conn.close()

    size_mb = os.path.getsize(args.out) / (1024 * 1024)
    print(f"\nBuilt {args.out}  ({size_mb:.1f} MB)")
    print(f"  kept {kept} places, skipped {skipped} (no name / unknown type / no location)")
    print(f"  bounds {min_lat:.4f},{min_lng:.4f} .. {max_lat:.4f},{max_lng:.4f}")
    for category, n in sorted(counts.items()):
        print(f"    {category:11} {n}")


if __name__ == "__main__":
    main()
