# ModCheck

Pre-install dependency and conflict checker for [Nexus Mods](https://www.nexusmods.com/) collections.

Give it a collection slug, and it tells you — *before* you install anything —
which required mods are missing from the collection, which pinned mods no
longer exist on Nexus, and where pinned versions don't match current ones.

**Stack:** Java 21 · Spring Boot 4 · PostgreSQL (Flyway) · Redis · Nexus Mods REST v1 + GraphQL v2

## What it does

```
POST /check  {"collectionSlug": "xk05aw"}
```

ingests the collection's latest revision — every pinned file, each mod's
current metadata, its archive file listings, and its declared requirements —
into Postgres. Then:

```
GET /check/{id}/report
```

returns the findings. Real output against a popular Skyrim SE collection:

```json
{
  "checkRunId": 1,
  "sourceRef": "xk05aw",
  "revision": 311,
  "missingDependencies": [
    {
      "nexusModId": 74689,
      "name": "MergeMapper",
      "requiredBy": ["FormList Manipulator - FLM", "Keyword Item Distributor (KID)"]
    },
    {
      "nexusModId": 1988,
      "name": "XP32 Maximum Skeleton Special Extended - XPMSSE",
      "requiredBy": ["Auto Skeleton Patch - Universal Behaviour Runtime"]
    }
  ],
  "unavailableMods": [
    { "nexusModId": 631, "name": "Bethini Pie (Performance INI Editor)" }
  ],
  "outdatedPins": [
    {
      "modName": "powerofthree's Tweaks",
      "pinnedVersion": "1.15.1",
      "currentVersion": "1.16.0"
    }
  ]
}
```

## Running it

You'll need Docker, Java 21, and a [Nexus Mods API key](https://www.nexusmods.com/users/myaccount?tab=api).

```bash
docker compose up -d                # Postgres + Redis
export NEXUS_API_KEY=your_key
./mvnw spring-boot:run              # Flyway applies the schema on startup
```

Then:

```bash
curl -X POST localhost:8080/check \
  -H 'Content-Type: application/json' \
  -d '{"collectionSlug":"xk05aw"}'

curl localhost:8080/check/1/report
```

A first ingest makes a few hundred API calls (~1 minute). Repeat ingests hit
the Redis cache (~190 calls → 1, roughly 9× faster) — metadata, file
listings, and requirements are cached for 6 hours.

## How it works

- **Ingestion** walks the collection via GraphQL v2 (collections and file
  contents only exist there), fetches per-mod metadata via the stable REST
  v1, and persists everything through a Flyway-versioned schema. The whole
  ingest is one transaction — a failure rolls back cleanly.
- **The mod table stores current truth** (from metadata); **check_input_file
  stores pinned truth** (from the collection revision). The version-mismatch
  report is literally the diff between the two.
- **Mod identity is `(game_id, nexus_mod_id)`** — Nexus mod IDs are only
  unique per game, a fact enforced by a composite unique constraint after
  being discovered the hard way.
- **The dependency check is a set difference at query time:** requirements
  declared by the input mods, minus externals, minus mods actually present.
  Requirements store Nexus's raw mod id at ingest; presence is resolved when
  you ask, not when data is written.

## Known limitations (honest ones)

- **VR-variant noise:** mod authors declare requirements for both flat and
  VR editions, and the API doesn't platform-scope them — so a flat-screen
  collection's report may list SKSEVR-style VR variants as "missing."
- **File listings cap at 10,000 per mod** — Nexus's file search runs on
  Elasticsearch and deep pagination hits its `max_result_window`. Only
  mega asset packs are affected; truncation is logged.
- **Version comparison is string inequality,** not semver — and the mod
  page's headline version can lag file versions, so "outdated" pins are
  really *mismatches*, directionless.
- **External requirements** (VC++ redistributables, off-Nexus tools) are
  stored but excluded from the missing check — they can't be in a
  collection by definition.
- File-overlap conflict detection is planned but not yet built.

## Notes

An engineering log of findings, decisions, and war stories lives in
[NOTES.md](NOTES.md) — including the Elasticsearch discovery, the GraphQL
docs-vs-reality mismatch, and why the requirements link needed a V2 migration.