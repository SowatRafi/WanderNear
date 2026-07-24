"""Fill a pack's `event` table with the city's ANNUAL FESTIVALS, from Wikipedia.

Run this AFTER build_db.py — it opens the finished pack, reads the city's own name,
and adds the festivals it finds. If it finds none the pack is simply left without
events, which the app says honestly rather than inventing anything.

WHY WIKIPEDIA AND NOT WIKIDATA
    Wikidata looks like the obvious source, but it can't answer this question. Its
    festival items are reachable only by coordinates or by "located in", and the
    festivals a traveller actually cares about have neither: Melbourne International
    Comedy Festival (Q17012417) has no P625 and no P131, so no structured query
    returns it. Querying Melbourne by coordinates yields 6 items, one of them
    defunct, and misses the Comedy Festival, Moomba, the Fringe and the Food and
    Wine Festival. Wikipedia's own "Category:Festivals in <City>" returns all 25.

    Wikidata also has NO usable dates: P837 ("day in year for periodic occurrence")
    is empty across the board, and the only dates attached are on one-off past
    editions ("1996 Australian Grand Prix"). So we do not claim to know when a
    festival runs — `when_text` is left NULL and the app says so.

GENERIC, NOT CURATED
    The category title is a FORMULA built from the city's name, not a per-city list:
    "Category:Festivals in Melbourne". Yield varies honestly with how well a city is
    documented — Melbourne 25, Paris 9, Kyoto 4, Geelong 0 — and a city with none
    just gets no events.

Licensing: Wikipedia text is CC BY-SA 4.0, recorded per row and credited in the app.
"""

import json
import sqlite3
import sys
import urllib.parse
import urllib.request

from config import DB_PATH

WIKIPEDIA_API = "https://en.wikipedia.org/w/api.php"
USER_AGENT = "WanderNear/0.1 (sowat.rafi.98@gmail.com)"
LICENSE = "CC BY-SA 4.0"

# Enough to cover even a festival-heavy city without turning the card into a list
# nobody reads.
MAX_FESTIVALS = 30


def get_json(url):
    """GET and parse JSON, or None on any failure (a missing category is normal)."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except Exception:
        return None


def festivals(city_name):
    """
    A city's festivals — each title + its opening sentence.

    `generator=categorymembers` feeds the category straight into `prop=extracts`, so
    one request covers the whole category instead of a call per festival.

    BUT Wikipedia returns at most 20 EXTRACTS per request even though the generator
    lists every member, handing back a `continue` token for the rest. Without
    following it a city with >20 festivals silently loses its alphabetical tail —
    Melbourne (25) dropped Rising and the St Kilda Festival. So we follow the token,
    merging pages and keeping whichever request supplied each festival's summary.
    """
    category = "Category:Festivals in %s" % city_name
    base = (
        "%s?action=query&generator=categorymembers&gcmtitle=%s&gcmlimit=%d"
        "&gcmtype=page&prop=extracts&exintro=1&explaintext=1&exsentences=1&format=json"
        % (WIKIPEDIA_API, urllib.parse.quote(category), MAX_FESTIVALS)
    )
    out = {}
    cont = ""
    for _ in range(2 + MAX_FESTIVALS // 20):        # enough rounds to exhaust MAX
        data = get_json(base + cont)
        if not data:
            break
        for page in data.get("query", {}).get("pages", {}).values():
            title = page.get("title")
            if not title:
                continue
            extract = (page.get("extract") or "").strip()
            if extract or title not in out:         # never overwrite a summary with a blank
                out[title] = extract
        more = data.get("continue")
        if not more or "excontinue" not in more:
            break
        cont = "&excontinue=%d&continue=%s" % (
            more["excontinue"], urllib.parse.quote(more["continue"]))
    return [{"title": t, "summary": s} for t, s in out.items()]


def is_historical(summary):
    """
    True if the article describes a festival in the past tense — "MEL&NYC festival
    WAS a cultural festival ... in 2018". A traveller looking for what's on should not
    be shown a festival that stopped running.

    ponytail: a tense heuristic on the first sentence, not real language parsing. It
    reads what Wikipedia actually wrote (we never invent a status); the worst case is
    that an odd phrasing keeps or drops one festival. Revisit only if it misfires.
    """
    text = " %s " % summary.lower()
    past = " was " in text or " were " in text
    present = " is " in text or " are " in text
    return past and not present


def main():
    db_path = sys.argv[1] if len(sys.argv) > 1 else DB_PATH
    db = sqlite3.connect(db_path)

    row = db.execute("SELECT id, name FROM city LIMIT 1").fetchone()
    if not row:
        print("No city row in %s — run build_db.py first." % db_path)
        return
    city_id, city_full_name = row
    # "Melbourne, Victoria, Australia" -> "Melbourne": the category is named after
    # the city alone.
    city = city_full_name.split(",")[0].strip()

    print("Looking for annual festivals in %s ..." % city)
    found = festivals(city)
    if not found:
        print("  Wikipedia lists no festival category for %s — leaving events empty." % city)
        print("  (That is the honest result; the app shows nothing rather than inventing.)")
        return
    print("  found %d festival articles" % len(found))

    db.execute("DELETE FROM event WHERE city_id = ?", (city_id,))
    kept = 0
    skipped = 0
    for festival in sorted(found, key=lambda f: f["title"]):
        summary = festival["summary"] or None    # a stub article may have no intro text
        # Only a festival Wikipedia describes IN THE PAST is dropped — we can't tense-check
        # a name-only entry, but its name is still a real, grounded category member.
        if summary and is_historical(summary):
            skipped += 1
            continue
        db.execute(
            "INSERT INTO event (city_id, name, summary, summary_url, summary_license, "
            "wikidata_qid, when_text) VALUES (?, ?, ?, ?, ?, NULL, NULL)",
            (
                city_id,
                festival["title"],
                summary,
                "https://en.wikipedia.org/wiki/%s"
                % urllib.parse.quote(festival["title"].replace(" ", "_")),
                LICENSE,
            ),
        )
        kept += 1
    db.commit()
    if skipped:
        print("  skipped %d that Wikipedia describes in the past tense (no longer running)" % skipped)
    # when_text stays NULL on purpose: no free source gives a reliable date for a
    # recurring festival, so the app says "dates change each year" instead of guessing.
    print("  saved %d festivals (no dates — none are published in a form we can trust)" % kept)
    db.close()


if __name__ == "__main__":
    main()
