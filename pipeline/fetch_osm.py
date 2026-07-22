"""Stage 1 of the pipeline: fetch raw places from OpenStreetMap.

What it does, in plain English:
  1. Turns the city name into an OpenStreetMap "area" (via Nominatim).
  2. Asks the Overpass API for every food spot, place of worship, attraction,
     and outdoor spot inside that area.
  3. Saves the raw answer to pipeline/out/<city>_osm.json so we only ever
     fetch each city once (the next stages read this file from disk).

Run it with:  python fetch_osm.py
"""

import json
import os
import time

import requests

import config


def geocode(city):
    """Look up a city name and return its OpenStreetMap record."""
    params = {"q": city, "format": "json", "limit": 1}
    headers = {"User-Agent": config.USER_AGENT}
    resp = requests.get(config.NOMINATIM_URL, params=params, headers=headers, timeout=60)
    resp.raise_for_status()
    results = resp.json()
    if not results:
        raise SystemExit(f"Could not find a place called: {city}")
    return results[0]


def area_id_for(osm_type, osm_id):
    """Convert an OSM feature id into the special id Overpass uses for areas.

    Overpass area ids = the relation/way id plus a fixed offset. This is the
    reliable way to scope a query to a city (far better than guessing names).
    Returns None if the feature can't be an area (a plain point).
    """
    if osm_type == "relation":
        return 3600000000 + int(osm_id)
    if osm_type == "way":
        return 2400000000 + int(osm_id)
    return None


def build_query(area_id):
    """Build the Overpass query that collects our four place categories.

    `nwr` means "nodes, ways and relations". `out center tags;` gives every
    result a single lat/lon point plus its tags — exactly what we need for map
    pins. The regexes list the specific OSM tag values we care about.
    """
    return f"""
[out:json][timeout:180];
area({area_id})->.a;
(
  nwr["amenity"~"^(restaurant|cafe|fast_food|food_court|bar|pub|ice_cream)$"](area.a);
  nwr["amenity"="place_of_worship"](area.a);
  nwr["tourism"~"^(attraction|viewpoint|museum|artwork|gallery|zoo|theme_park|aquarium)$"](area.a);
  nwr["historic"~"^(monument|memorial|castle|ruins|archaeological_site|monastery)$"](area.a);
  nwr["natural"~"^(beach|peak|waterfall|water|cliff|cave_entrance|spring|bay|hot_spring|volcano)$"](area.a);
  nwr["leisure"~"^(park|nature_reserve|garden|beach_resort)$"](area.a);
  relation["route"="hiking"](area.a);
);
out center tags;
""".strip()


def fetch_overpass(query):
    """Send the query to Overpass, retrying politely if a server is busy."""
    headers = {"User-Agent": config.USER_AGENT}
    for endpoint in config.OVERPASS_ENDPOINTS:
        print(f"  querying {endpoint} ...")
        for attempt in range(1, 5):
            try:
                resp = requests.post(
                    endpoint, data={"data": query}, headers=headers, timeout=300
                )
                if resp.status_code == 200:
                    return resp.json()
                # 429 = too many requests, 504 = server overloaded → wait & retry.
                if resp.status_code in (429, 504):
                    wait = 5 * attempt
                    print(f"    server busy ({resp.status_code}); waiting {wait}s")
                    time.sleep(wait)
                    continue
                resp.raise_for_status()
            except requests.RequestException as err:
                wait = 5 * attempt
                print(f"    network hiccup ({err}); retrying in {wait}s")
                time.sleep(wait)
        print("    this mirror kept failing; trying the next one")
    raise SystemExit("All Overpass mirrors failed. Please try again later.")


def main():
    os.makedirs(config.OUT_DIR, exist_ok=True)

    print(f"Geocoding '{config.CITY}' ...")
    place = geocode(config.CITY)
    osm_type, osm_id = place["osm_type"], place["osm_id"]
    print(f"  found: {place['display_name']}  ({osm_type} {osm_id})")

    area_id = area_id_for(osm_type, osm_id)
    if area_id is None:
        raise SystemExit(
            "The geocoder returned a single point, not an area. Try a more "
            "specific city name in config.CITY."
        )

    print("Fetching places from OpenStreetMap (this can take up to a minute) ...")
    data = fetch_overpass(build_query(area_id))
    elements = data.get("elements", [])
    print(f"  received {len(elements)} raw OSM features")

    # Nominatim gives the bounding box as [south, north, west, east] strings.
    south, north, west, east = (float(x) for x in place["boundingbox"])

    out = {
        "city": {
            "name": config.CITY,
            "osm_type": osm_type,
            "osm_id": int(osm_id),
            "min_lat": south,
            "max_lat": north,
            "min_lng": west,
            "max_lng": east,
        },
        "elements": elements,
    }
    with open(config.OSM_JSON, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False)
    print(f"Saved raw OSM data -> {config.OSM_JSON}")


if __name__ == "__main__":
    main()
