# ModCheck — Engineering Notes

Running log of findings, decisions, and war stories from building this.
Kept for future-me: README material, cover letter specifics, interview stories.
Newest entries at the bottom of each section.

---

## Findings about the Nexus API

### GraphQL v2 docs say string filters; the schema wants typed values
- **What happened:** The node-nexus-api client library docs state all filter
  values must be strings, even numeric ones. The actual API rejects a string
  `modId` with a precise schema error: `Type mismatch (String! / Int!)`.
- **Resolution:** Trust the API's errors over its documentation. Switched the
  GraphQL variable to `Int!`.
- **Follow-up:** Worth filing as feedback on their API forum / client repo —
  v2 is in beta and they ask for feedback.
- **Where:** Stage 0 spike, call 2.

### Mod IDs are only unique per game
- **What happened:** Queried `modFileContents` for mod 1090 expecting
  Apocalypse (Skyrim SE) and got tactical vests from a Ghost Recon mod —
  5,970 files across every game on Nexus. Mod IDs are scoped to a game;
  the filter needs `gameId` alongside `modId`.
- **Resolution:** gameId filter on the query, and the invariant cast into the
  schema as `UNIQUE (game_id, nexus_mod_id)` on the mod table — a learned
  domain fact enforced by the database, not a comment.
- **Where:** Stage 0 spike, call 2; V1 migration.

### File listings hit Elasticsearch's max_result_window at 10,000
- **What happened:** During first full ingestion, mod 156035 (PunknPatch —
  a mega resource pack with >10k archive files) blew up the pagination loop
  at offset 10,080. The GraphQL error contained a raw Elasticsearch response:
  `Result window is too large, from + size must be less than or equal
  to: [10000]` — shard failures, index name (`file-content_*`), the lot.
  So: their file-contents search runs on ES, deep pagination is capped by
  `index.max_result_window`, and the backend error leaks through the API.
- **Resolution:** Capped the client's pagination at offset 10,000 and log a
  warning naming fetched-vs-total when a listing is truncated.
- **Impact honestly assessed:** affects only 10k+-file mods (mega asset
  packs — the extreme tail); dependency and version checks unaffected;
  conflict detection covers the first 10k files. Asset-pack overwrites are
  also the least dangerous conflict class (last-loaded-wins textures vs.
  crashing plugins).
- **Possible refinement (parked):** spend the 10k budget on conflict-relevant
  extensions first (.esp/.esl/.dll/.pex) via the filter, so truncation eats
  textures rather than plugins.
- **Where:** NexusClient.getModFiles; first ingestion run.

### Rate limits are generous but real
- 2,000 requests/hour, 20,000/day (x-rl-* headers on REST v1).
- A first ingest of a ~99-file collection costs roughly 200 calls
  (metadata + paginated file listings per new mod). Fine, but caching
  matters from the second collection onward — Skyrim collections overlap
  heavily (USSEP, SkyUI, Address Library are in everything).

### Both API generations are load-bearing
- Collections and modFileContents exist only on GraphQL v2 (beta, may
  change). Mod metadata lives on stable REST v1. The client uses each
  where appropriate: new capability on v2, fixed-shape lookups on v1,
  minimising exposure to the beta surface.

---

## Design decisions worth remembering

- **Two truths, two tables:** the mod table stores *current* state from
  metadata (current_version, last_fetched_at); check_input_file stores the
  *pinned* state from the collection revision (file_version). The staleness
  report is literally the diff between the two — blur the sources and the
  feature disappears. Seen live in the spike: powerofthree's Tweaks pinned
  1.15.1 vs current 1.16.0.
- **Entities model relationships, LAZY everywhere:** eager fetching is a
  per-query decision (join fetch), never a per-entity one. N+1 avoidance.
- **Flyway owns the schema; Hibernate only validates.** ddl-auto: validate
  caught nothing because transcription was checked against the migration —
  the migration file is the source of truth, the entity its mirror.
- **@Transactional wraps the whole ingest:** a failure at mod 60 rolls back
  everything — observed twice during first-run debugging; zero partial
  garbage. Known trade-off: a long transaction around ~200 external calls.
  Fine for v1; async ingestion with a status endpoint is the grown-up shape.
- **DB DEFAULTs are invisible to Hibernate:** it writes every mapped column,
  so `DEFAULT now()` never fires — timestamps like last_fetched_at are set
  in code, which is semantically right anyway (app-level bookkeeping).
- **GraphQL errors arrive as HTTP 200 with an errors array and null data.**
  Both response records carry an errors component; the client throws with
  the message content instead of NPE-ing on .data(). This is what turned
  the ES discovery from a mystery NPE into a self-describing error.

---

## War stories (chronological)

1. **The URL joiner that wasn't** — first REST call 404'd; theorised about
   Spring base-URL/path joining; a request interceptor printing the actual
   URL revealed `games{game}` — a missing slash in the uri template.
   Lesson: print the actual thing, don't reason about the imagined thing.
   The interceptor stayed (log.debug on every outbound call) and paid off
   again within the hour.
2. **Work-laptop gauntlet** — Betfair Artifactory hijacking Maven (fixed
   with project-local .mvn/settings.xml), Java 17 vs 21 (SDKMAN + .sdkmanrc),
   work git identity (per-repo user config), Apple's 2007 Bash blocking
   SDKMAN (brew bash). Every fix scoped to the repo; the build is now
   self-contained on any machine.
3. **First ingestion, two failures, both instructive** — NPE on null data()
   (missing GraphQL error handling; added it), then the self-identifying
   ES pagination error (capped; see findings). ~60 mods ingested cleanly
   before each failure; @Transactional rolled back both times as designed.

---

## Parked / roadmap

- Redis @Cacheable on client methods (metadata + file listings) — the
  second-ingest speedup is the demo.
- NexusApiException (typed) to replace IllegalStateException in the client.
- Async ingestion + status endpoint (long transaction trade-off).
- Extension-prioritised file fetching within the 10k cap.
- JSpecify @NullMarked adoption (repo-wide null-safety polish).
- Testcontainers integration tests (@DataJpaTest against real Postgres).
- File feedback to Nexus: docs/Int! mismatch; possibly the leaking ES
  errors too (arguably an information-exposure nit on their side).