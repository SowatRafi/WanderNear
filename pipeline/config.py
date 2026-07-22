"""Shared settings for the WanderNear data pipeline.

To build a data pack for a DIFFERENT city later, you only change CITY and
CITY_SLUG here and re-run the three scripts. Nothing else is city-specific —
this is the "generic city template" the project is built around.
"""

import os

# The city to build a pack for. Use a specific, unambiguous name so the
# geocoder finds the right place ("Melbourne, Victoria, Australia", not just
# "Melbourne" — there are several around the world).
CITY = "Melbourne, Victoria, Australia"
CITY_SLUG = "melbourne"          # short name used for output filenames

# OpenStreetMap and Wikipedia REQUIRE a descriptive User-Agent that says who is
# calling and how to reach them. We use your email so they can contact you if a
# script ever misbehaves. Requests without this are throttled or blocked.
USER_AGENT = "WanderNear/0.1 (sowat.rafi.98@gmail.com)"

# Where generated files go. All of pipeline/out/ is git-ignored and can be
# regenerated at any time.
OUT_DIR = os.path.join(os.path.dirname(__file__), "out")
OSM_JSON = os.path.join(OUT_DIR, f"{CITY_SLUG}_osm.json")
ENRICHED_JSON = os.path.join(OUT_DIR, f"{CITY_SLUG}_enriched.json")
DB_PATH = os.path.join(OUT_DIR, f"{CITY_SLUG}.db")

# Public Overpass API mirrors. We try them in order if one is busy.
OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]

# Nominatim turns a city name into an OpenStreetMap area we can query.
NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"

# The legally-required credit that the app must show its users. OpenStreetMap
# data is under the ODbL licence; Wikipedia text is under CC BY-SA 4.0.
ATTRIBUTION = (
    "© OpenStreetMap contributors (ODbL). "
    "Cultural descriptions from Wikipedia (CC BY-SA 4.0)."
)
