"""Stage 2 of the pipeline: add cultural "why it matters" text from Wikipedia.

Only places that OpenStreetMap already links to Wikipedia/Wikidata get a blurb —
we never guess which article belongs to a place, because guessing is the main
way you end up attaching the wrong facts. Places without a link simply get no
summary (that's fine; the app shows what it has and nothing more).

Reads : pipeline/out/<city>_osm.json   (from stage 1)
Writes: pipeline/out/<city>_enriched.json

Run it with:  python enrich_wikipedia.py
"""

import json
import os
import time
from urllib.parse import quote

import requests

import config

HEADERS = {"User-Agent": config.USER_AGENT}


def title_from_wikidata(qid):
    """Given a Wikidata id like 'Q1234', find its English Wikipedia article."""
    url = f"https://www.wikidata.org/wiki/Special:EntityData/{qid}.json"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=60)
        if resp.status_code != 200:
            return None
        entity = resp.json().get("entities", {}).get(qid, {})
        sitelinks = entity.get("sitelinks", {})
        if "enwiki" in sitelinks:
            return ("en", sitelinks["enwiki"]["title"])
    except (requests.RequestException, ValueError):
        return None
    return None


def fetch_summary(lang, title):
    """Fetch the short article summary + thumbnail for one Wikipedia page."""
    encoded = quote(title.replace(" ", "_"), safe="")
    url = f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{encoded}"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=60)
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        data = resp.json()
    except (requests.RequestException, ValueError):
        return None

    # Skip disambiguation pages and empty summaries — no useful text there.
    if data.get("type") == "disambiguation" or not data.get("extract"):
        return None

    return {
        "summary": data["extract"],
        "summary_url": data.get("content_urls", {}).get("desktop", {}).get("page"),
        "summary_license": "CC BY-SA 4.0",
        "thumbnail_url": data.get("thumbnail", {}).get("source"),
    }


def resolve_title(tags):
    """Work out which Wikipedia article (language + title) a place points to."""
    wp = tags.get("wikipedia")            # e.g. "en:Federation Square"
    if wp and ":" in wp:
        lang, title = wp.split(":", 1)
        return (lang, title)
    qid = tags.get("wikidata")            # e.g. "Q1234"
    if qid:
        return title_from_wikidata(qid)
    return None


def main():
    with open(config.OSM_JSON, encoding="utf-8") as fh:
        elements = json.load(fh)["elements"]

    # Only bother with places that actually carry a Wikipedia/Wikidata link.
    linked = [
        e for e in elements
        if e.get("tags", {}).get("wikidata") or e.get("tags", {}).get("wikipedia")
    ]
    print(f"{len(linked)} of {len(elements)} places have a Wikipedia/Wikidata link")

    enrichment = {}
    for i, element in enumerate(linked, start=1):
        title = resolve_title(element["tags"])
        if title:
            info = fetch_summary(*title)
            if info:
                enrichment[f"{element['type']}/{element['id']}"] = info
        # Be a polite API citizen: small pause between requests.
        time.sleep(0.1)
        if i % 25 == 0:
            print(f"  processed {i}/{len(linked)} ...")

    os.makedirs(config.OUT_DIR, exist_ok=True)
    with open(config.ENRICHED_JSON, "w", encoding="utf-8") as fh:
        json.dump(enrichment, fh, ensure_ascii=False)
    print(f"Added summaries for {len(enrichment)} places -> {config.ENRICHED_JSON}")


if __name__ == "__main__":
    main()
