"""Proof that the data pack works: runs a few real searches and prints results.

This is also a preview of the retrieval logic we'll port to Kotlin in the app:
find places with plain SQL (full-text search + structured filters + distance),
never with an AI. If a search finds nothing, it says so honestly.

Run it with:  python query_demo.py
"""

import math
import sqlite3

import config

# Roughly the centre of Melbourne's CBD — stands in for "near me" in this demo.
HERE = (-37.8136, 144.9631)


def distance_km(lat1, lng1, lat2, lng2):
    """Straight-line distance between two lat/lon points, in kilometres."""
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return r * 2 * math.asin(math.sqrt(a))


def show(title, rows):
    print(f"\n=== {title} — {len(rows)} result(s) ===")
    if not rows:
        print("  (nothing in the data — the app would say 'I don't have that "
              "in my data' instead of inventing something)")
        return
    for row in rows[:5]:
        dist = distance_km(HERE[0], HERE[1], row["lat"], row["lng"])
        line = f"  • {row['name']}  [{row['subcategory']}]  {dist:.1f} km"
        if row["cuisine"]:
            line += f"  cuisine={row['cuisine']}"
        print(line)


def search_text(conn, text, category=None, diet=None):
    """Keyword search over the full-text index, with optional filters."""
    sql = ("SELECT p.* FROM places_fts f JOIN place p ON p.id = f.docid "
           "WHERE places_fts MATCH ?")
    params = [text]
    if category:
        sql += " AND p.category = ?"
        params.append(category)
    if diet:
        sql += (" AND EXISTS (SELECT 1 FROM place_diet d WHERE d.place_id = p.id "
                "AND d.diet = ? AND (d.value IS NULL OR d.value != 'no'))")
        params.append(diet)
    return conn.execute(sql, params).fetchall()


def search_filters(conn, category=None, diet=None, religion=None):
    """Structured search with no keywords — pure filters."""
    sql = "SELECT p.* FROM place p WHERE 1 = 1"
    params = []
    if category:
        sql += " AND p.category = ?"
        params.append(category)
    if religion:
        sql += " AND p.religion = ?"
        params.append(religion)
    if diet:
        sql += (" AND EXISTS (SELECT 1 FROM place_diet d WHERE d.place_id = p.id "
                "AND d.diet = ? AND (d.value IS NULL OR d.value != 'no'))")
        params.append(diet)
    return conn.execute(sql, params).fetchall()


def main():
    conn = sqlite3.connect(config.DB_PATH)
    conn.row_factory = sqlite3.Row

    total = conn.execute("SELECT COUNT(*) FROM place").fetchone()[0]
    print(f"Data pack has {total} places.")

    # How many places carry each dietary tag (shows real OSM coverage).
    print("\nDietary tag coverage:")
    for diet, n in conn.execute(
        "SELECT diet, COUNT(*) FROM place_diet GROUP BY diet ORDER BY COUNT(*) DESC"
    ).fetchall():
        print(f"  {diet:12} {n}")

    # The two proof searches the milestone asked for.
    show("halal restaurants", search_text(conn, "halal", category="food")
         or search_filters(conn, category="food", diet="halal"))
    show("temples", search_text(conn, "temple"))

    # The MVP success scenario: vegetarian food, ranked by distance from 'here'.
    veg = search_filters(conn, category="food", diet="vegetarian")
    veg = sorted(veg, key=lambda r: distance_km(HERE[0], HERE[1], r["lat"], r["lng"]))
    show("vegetarian food near the CBD", veg)

    conn.close()


if __name__ == "__main__":
    main()
