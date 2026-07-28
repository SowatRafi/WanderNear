"""Build the pack CATALOGUE — the index the app reads to find the right pack.

The app never guesses what data exists. It fetches this one small JSON file, looks for a
pack whose bounding box contains the user's position, and downloads that. Everything the
app needs to make that decision is in here, so the catalogue is the only thing that has
to be fetched before a first download.

Because the catalogue carries the URLs, you can move your hosting later — a different
bucket, a different provider — by republishing this file. Nothing in the app is pinned to
a host, which is also what OpenStreetMap's Nominatim policy asks of any app that uses a
service it doesn't own: be able to switch "without requiring a software update".

USAGE
-----
    python make_catalogue.py --base-url https://<your-bucket>.r2.dev/ \\
        --packs out/melbourne.db out/andorra.db --out out/packs.json

Then upload the .db files AND packs.json to that same base URL. See DEPLOY.md.
"""

import argparse
import json
import os
import sqlite3
import sys
from datetime import datetime, timezone


def describe(path, base_url):
    """Read a finished pack and describe it for the catalogue.

    Everything here is READ FROM THE PACK, never typed by hand, so the catalogue can't
    disagree with the file it points at.
    """
    conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        row = conn.execute(
            "SELECT name, country, min_lat, min_lng, max_lat, max_lng, data_version FROM city"
        ).fetchone()
        if row is None:
            sys.exit(f"{path} has no city row — was it built by build_pack.py?")
        name, country, south, west, north, east, built = row
        if south is None:
            sys.exit(f"{path} has no bounding box — the app couldn't tell where it covers.")
        places = conn.execute("SELECT COUNT(*) FROM place").fetchone()[0]
    finally:
        conn.close()

    filename = os.path.basename(path)
    return {
        "id": os.path.splitext(filename)[0],
        "name": name,
        "country": country,
        # The area this pack covers, measured from its own places.
        "south": south, "west": west, "north": north, "east": east,
        "places": places,
        "bytes": os.path.getsize(path),
        "built": built,
        "url": base_url.rstrip("/") + "/" + filename,
    }


def main():
    ap = argparse.ArgumentParser(description="Build the WanderNear pack catalogue")
    ap.add_argument("--packs", nargs="+", required=True, help="finished .db packs")
    ap.add_argument("--base-url", required=True, help="public URL the packs are served from")
    ap.add_argument("--out", required=True, help="output packs.json")
    args = ap.parse_args()

    entries = [describe(p, args.base_url) for p in args.packs]
    # Smallest first, so when two packs cover the user the app can prefer the tighter
    # (and therefore more local) one.
    entries.sort(key=lambda e: (e["north"] - e["south"]) * (e["east"] - e["west"]))

    catalogue = {
        "version": 1,
        "generated": datetime.now(timezone.utc).isoformat(),
        "attribution": "© OpenStreetMap contributors (ODbL).",
        "packs": entries,
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(catalogue, fh, indent=2)

    print(f"Wrote {args.out} with {len(entries)} pack(s):")
    for e in entries:
        print(f"  {e['name']:20} {e['places']:>7} places  {e['bytes'] / 1048576:>6.1f} MB  {e['url']}")


if __name__ == "__main__":
    main()
