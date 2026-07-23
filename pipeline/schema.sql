-- WanderNear city data pack — database schema.
--
-- This file defines every table in a city's data pack. The app treats this
-- database as the ONLY source of truth: it never invents a place, address,
-- opening time, or fact that is not stored here. Columns that are often empty
-- are simply left NULL when OpenStreetMap / Wikipedia don't have that fact —
-- we never fill a gap with a guess.

PRAGMA foreign_keys = ON;

-- One row describing the city this pack covers.
CREATE TABLE city (
    id           INTEGER PRIMARY KEY,
    name         TEXT NOT NULL,
    country      TEXT,
    population   INTEGER,        -- from OSM/Wikidata; NULL when unknown (never guessed)
    osm_type     TEXT,          -- relation / way / node used to define the area
    osm_id       INTEGER,
    min_lat      REAL,          -- bounding box of the city (for "any city" later)
    min_lng      REAL,
    max_lat      REAL,
    max_lng      REAL,
    data_version TEXT,          -- ISO date this pack was built
    fetched_at   TEXT,          -- ISO timestamp of the fetch
    attribution  TEXT NOT NULL  -- required credit text the app must display
);

-- A single place: a food spot, place of worship, attraction, or outdoor spot.
CREATE TABLE place (
    id              INTEGER PRIMARY KEY,
    city_id         INTEGER NOT NULL REFERENCES city(id),
    osm_type        TEXT NOT NULL,   -- node / way / relation
    osm_id          INTEGER NOT NULL,
    name            TEXT NOT NULL,   -- we skip unnamed places (nothing to recommend)
    category        TEXT NOT NULL,   -- food | worship | attraction | outdoor
    subcategory     TEXT,            -- e.g. restaurant, viewpoint, mosque, beach
    lat             REAL NOT NULL,
    lng             REAL NOT NULL,
    address         TEXT,
    suburb          TEXT,            -- addr:suburb, for on-device "which suburb am I in"
    cuisine         TEXT,            -- raw OSM cuisine value, e.g. "indian;pizza"
    religion        TEXT,            -- for places of worship
    denomination    TEXT,
    opening_hours   TEXT,            -- raw OSM opening_hours string
    phone           TEXT,
    website         TEXT,
    wikidata_qid    TEXT,
    summary         TEXT,            -- short "why it matters" text from Wikipedia
    summary_url     TEXT,            -- link to the source article (attribution)
    summary_license TEXT,            -- e.g. "CC BY-SA 4.0"
    thumbnail_url   TEXT,
    UNIQUE(osm_type, osm_id)         -- never store the same OSM feature twice
);

-- Dietary options for a place. A place can have several rows here
-- (e.g. both halal=yes and vegetarian=yes). This is how diet filters work.
CREATE TABLE place_diet (
    place_id INTEGER NOT NULL REFERENCES place(id),
    diet     TEXT NOT NULL,   -- halal | vegetarian | vegan | kosher | gluten_free ...
    value    TEXT,            -- yes | only | limited | no  (from OSM)
    PRIMARY KEY (place_id, diet)
);

-- Festivals / cultural events. The table exists now; we populate it in a
-- later milestone. Wikidata event data is sparse and its dates are unreliable,
-- so when_text is a human-readable hint, NOT a guaranteed schedule.
CREATE TABLE event (
    id              INTEGER PRIMARY KEY,
    city_id         INTEGER NOT NULL REFERENCES city(id),
    name            TEXT NOT NULL,
    summary         TEXT,
    summary_url     TEXT,
    summary_license TEXT,
    wikidata_qid    TEXT,
    when_text       TEXT
);

-- The user's saved favourites. Filled in by the app, not the pipeline.
CREATE TABLE favorite (
    id       INTEGER PRIMARY KEY,
    place_id INTEGER NOT NULL REFERENCES place(id),
    saved_at TEXT
);

-- Full-text search index over the words we search on, so keyword lookups are
-- fast. FTS4 is used because it works on every Android device with no extra
-- dependencies. Each row's rowid matches the place.id it describes.
CREATE VIRTUAL TABLE places_fts USING fts4(
    name,
    summary,
    cuisine,
    subcategory
);
