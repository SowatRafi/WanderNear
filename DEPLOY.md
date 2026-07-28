# Publishing WanderNear data packs — the free, no-server setup

This is how the app gets its data once it ships: **you** build packs on your PC from free
OpenStreetMap extracts, and host them as ordinary static files. The app downloads the one
that covers where the user is.

**No per-user API calls.** That is the whole point:

- Nothing to rate-limit. Overpass's usage guide names *"operating commercial applications
  on public instances"* as misuse — this avoids it entirely.
- Nothing to go down. During development the public Overpass mirrors failed for the best
  part of an hour and the app had nothing to show. A downloaded pack can't do that.
- Faster. A local SQLite query beats a 30-second network round trip, everywhere.
- **$0/month** at the scale you'll start at (see costs below).

---

## One-time setup

### 1. Install the build tool

```bash
python -m pip install osmium
```

That's the only new dependency, and it never ships in the app — it's build-time only.

### 2. Create free hosting (Cloudflare R2)

1. Sign up at <https://dash.cloudflare.com/> (free, no card for the free tier).
2. **R2** → **Create bucket** → name it e.g. `wandernear-packs`.
3. **Settings** → **Public access** → enable the `r2.dev` public URL.
4. Note the public base URL — it looks like `https://pub-xxxx.r2.dev`.

**Free tier:** 10 GB storage, 1M writes, 10M reads per month, and **egress is free** —
which is the part that matters, because downloads are almost all of what you'll use.
Melbourne is ~5 MB for 22,000 places, so ~2,000 city packs fit inside the free tier.

> Cloudflare's free tier is genuinely free, not a trial. If you outgrow it, R2 storage is
> about $0.015/GB-month — pennies at this size. Egress stays free.

---

## Building and publishing a pack

### 1. Get the OSM extract

Download the region you want from <https://download.geofabrik.de/> — free, ODbL, rebuilt
daily. Pick the smallest region that covers your city (a state, not a continent).

```
https://download.geofabrik.de/australia-oceania/australia/victoria-latest.osm.pbf
```

> Geofabrik extracts are the *intended* way to do bulk OSM work. Overpass's own
> documentation says to use a planet dump rather than the API for this.

### 2. Build the pack

```bash
cd pipeline
python build_pack.py --pbf victoria-latest.osm.pbf --name "Melbourne" --country Australia --out out/melbourne.db
```

To cover just a city rather than a whole state, clip it (`south,west,north,east`):

```bash
python build_pack.py --pbf victoria-latest.osm.pbf --name "Melbourne" --country Australia \
    --bbox -38.05,144.55,-37.55,145.35 --out out/melbourne.db
```

The pack's bounding box is measured from the places actually kept, so the catalogue can
never claim coverage the file doesn't have.

### 3. Optional — add the Wikipedia write-ups

`build_pack.py` leaves `summary` empty. Run the existing enrichment step to fill in the
"why it's worth visiting" text and Travel Mode's Read/Listen stories.

### 4. Build the catalogue

```bash
python make_catalogue.py --base-url https://pub-xxxx.r2.dev \
    --packs out/melbourne.db out/sydney.db --out out/packs.json
```

### 5. Upload

Drag `out/*.db` **and** `out/packs.json` into the R2 bucket (or use `rclone`/`wrangler`).
Confirm the catalogue is publicly readable:

```bash
curl https://pub-xxxx.r2.dev/packs.json
```

### 6. Refreshing

Geofabrik rebuilds daily; re-running steps 1–5 republishes. Because the app reads the
catalogue every time, a new pack reaches users with no app update. Give updated files a
dated name (`melbourne-2026-07-28.db`) so an old catalogue never points at a changed file.

---

## Licensing — read before publishing

A pack is a **Derivative Database** of OpenStreetMap under **ODbL 1.0**, and publishing it
means distributing that database.

- Keep the attribution the pack already carries (`city.attribution`) visible in the app —
  it is, on every screen showing data.
- Publish a note stating the packs are derived from OpenStreetMap and offered under ODbL,
  and say where the source extract came from.
- Wikipedia summaries are **CC BY-SA 4.0** and credited separately.

None of this costs anything, but it is a legal obligation, not a courtesy. **Get proper
advice before charging money for the app** — this file is not legal advice.

---

## What the app still needs (not built yet)

The pack format, builder and catalogue are done and tested. The app currently downloads a
city by calling Overpass itself (`CityPackBuilder`). To finish the switch it needs:

1. `PackCatalogue` — fetch `packs.json`, find packs whose box contains the user's fix.
2. Download the chosen `.db` straight into `filesDir/packs/` (simpler than today's build:
   no Overpass, no streaming parse, no SQLite assembly — just a file download).
3. The Cities screen to offer catalogue packs instead of a name search.
4. The catalogue URL in a small remote config, so hosting can move without an app update.

Step 2 **deletes** more code than it adds.
