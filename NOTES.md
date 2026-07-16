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

### Collections can pin mods that no longer exist
- **What happened:** Mod 631, pinned in the collection revision, returns 404
  from the metadata endpoint — delisted/hidden/deleted from Nexus but still
  referenced by the collection. Third distinct failure of first ingestion.
- **Resolution:** Catch the 404, store a placeholder Mod built from the
  collection's pinned data (name/version from ModRef), flagged
  `available = false`. The one legitimate exception to the vintage rule:
  when current truth is gone, pinned truth is all there is.
- **Product insight:** this isn't just error handling — "this collection
  depends on a mod that no longer exists" is one of the most valuable
  warnings the checker can give. The `available` column was designed for
  exactly this; increment 2's report should surface it.

### totalCount doesn't always match retrievable results
- **What happened:** several truncation warns fired *below* the 10k cap
  ("fetched 560 of 686", "fetched 8000 of 21584") — the empty-page guard
  tripped while totalCount claimed more existed. Their ES totalCount appears
  inflated (or differently filtered) relative to what pagination actually
  serves.
- **Status:** behaviour is correct (take what exists, log the gap); the warn
  message wording ("pagination limit") is inaccurate for the sub-cap cases —
  soften to "fetched X of reported Y". Not yet investigated further.

### Requirements schema mapped via introspection (increment 2 spike)
- **How:** walked the type graph with raw `__type` introspection curls —
  no playground, no docs. Mod → modRequirements (NON_NULL object) →
  { nexusRequirements (ModRequirementPage: totalCount + nodes),
  dlcRequirements (official DLC deps — future feature: "needs Anniversary
  Edition"), modsRequiringThisMod (reverse edge — v2 recommender fuel) }.
- **ModRequirement node fields:** modId + gameId (**GraphQL ID scalars —
  serialize as JSON strings**, e.g. "30379"), modName, externalRequirement
  (Boolean — marks non-Nexus requirements like ENB; SKSE is *false*
  because it has a Nexus page despite off-site distribution), notes, url.
  NON_NULL honoured with **empty strings, not nulls** — blank-checks
  needed, not null-checks.
- **Lookup confirmed:** `mod(modId: ID!, gameId: ID!)` — guessed right,
  verified by introspecting the Query type's args.
- **Proof query:** SkyUI (12604) → totalCount 1: "Skyrim Script Extender
  (SKSE64)" modId 30379. The textbook dependency, found first try.
- **Pagination:** page furniture present but requirement lists are tiny
  (handfuls, not thousands) — reuse the existing loop pattern anyway,
  it's already written.

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
3. **First ingestion, three failures, all instructive** — (1) NPE on null
   data() — GraphQL errors arrive as HTTP 200 with an errors array; added
   error components + guards, which turned failure (2) into a
   self-describing message: the ES max_result_window discovery (capped).
   (3) 404 on a delisted mod still pinned by the collection (placeholder +
   available=false). ~60, ~60, and ~90 mods deep respectively — each fix
   cleared more of the gauntlet. @Transactional rolled back cleanly all
   three times; the successful run was checkRunId 4 because Postgres
   sequences don't roll back (runs 1–3 are the ghosts of the failures).

---

## Increment 1 — COMPLETE (receipt: {"checkRunId":4,"modCount":96,"fileCount":99})

**What was built:** a Spring Boot 4 / Java 21 service that ingests a Nexus
collection end to end. POST /check with a collection slug → GraphQL walk of
the collection's latest revision → per new mod, REST metadata fetch +
paginated GraphQL file-contents fetch → everything persisted to Postgres
(Flyway-versioned schema, 7 tables). Redis is provisioned but not yet used.

**The pipeline:** NexusClient (2 pre-configured RestClients, auth baked in,
GraphQL envelope/pagination/ES-cap absorbed, errors surfaced) → CheckService
(@Transactional orchestration: game resolve-or-create via first-mod
metadata borrow, mod upsert on the (game, nexus_mod_id) composite key,
delisted-mod placeholders, per-file CheckInputFile rows) → thin
CheckController.

**Proven against real-world hostility:** wrong-typed docs, per-game ID
scoping, GraphQL errors-as-200s, an upstream Elasticsearch pagination
ceiling, inflated totalCounts, and delisted-but-pinned mods — all in one
99-file collection.

**The receipt decoded:** 99 files = complete collection recorded; 96 mods =
dedupe working (multi-file mods collapse to one row); available=false on
one row = the delisted mod, already useful signal for increment 2's report.

**Not yet:** caching (first ingest ≈ 200 API calls / ~90s; second ingest of
overlapping collections should be near-instant once @Cacheable lands — the
demo), requirements ingestion, any actual *checking*. That's increments
1.5–2.

---

## Increment 1.5 — Redis caching (receipt: 53.3s → 6.0s, 9× faster)

**What was added:** @EnableCaching + @Cacheable on the two expensive
per-mod client calls (getModMetadata, getModFiles), Redis-backed via the
existing docker-compose Redis. getCollection deliberately uncached —
revisions move, and it's one call per ingest anyway.

**The A/B demo (measured):**
- Run A — cold everything (fresh volumes, empty cache): **53.3s**, ~190
  HTTP calls to Nexus, both Postgres and Redis filling as side effects.
- Truncate Postgres only (RESTART IDENTITY CASCADE), keep Redis warm.
- Run B — identical database work, warm cache: **6.0s**, **1** API call
  (the uncached collection query). The 47-second difference is exactly
  the network cost caching deleted.
- Side benefit: ~190 calls → 1 against their rate limit — the cache is
  also politeness toward Nexus.

**Config details:**
- Keys are composite and game-scoped (modMetadata::skyrimspecialedition:45148,
  modFiles::1704:76892) — the per-game ID lesson again, in cache-key form.
- Values stored as human-readable JSON with an @class type hint, verified
  by reading an entry back via redis-cli.
- Serializer: GenericJacksonJsonRedisSerializer — Boot 4 / Spring Data
  Redis 4.0 renamed it (no version in the name now; the Jackson-2
  "GenericJackson2..." variant is deprecated-for-removal). No no-arg
  constructor anymore: built via its builder with Spring-cache null-value
  support and default typing enabled (polymorphic type validation
  restricting deserialization to our packages + java.util).
- TTL 6h — mod metadata changes on a days-to-months scale; hours-fresh
  is fresh enough for a pre-install check.

**Mechanism note (interview-grade):** @Cacheable works by proxy — the
same CGLIB wrapping as @Transactional (visible as $$SpringCGLIB$$ in
every stack trace). Cache lives in the wrapper, so self-invocation
bypasses it — the classic "why isn't my annotation working" trap.

**Boot 4 / Jackson 3 migration scar count: three** — annotation package
split in the records (annotations stayed com.fasterxml, databind moved
to tools.jackson), dual databind jars on the classpath, and the Redis
serializer rename/builder API. Pattern: prefer the tools.jackson-flavoured
option, follow deprecation Javadoc pointers, trust the compiler over docs.

---

## NEXT SESSION — Increment 2 build plan (spike done, all known moves)

**Part 1 — requirements ingestion:**
1. `client/ModRequirementsResponse.java` — nested records, established
   pattern: Data(mod) → ModNode(name, modRequirements) →
   Requirements(nexusRequirements) → NexusRequirements(totalCount, nodes)
   → RequirementNode(String modId, String gameId, String modName,
   boolean externalRequirement, String notes, String url).
   **IDs as String** (GraphQL ID scalar), @JsonIgnoreProperties throughout.
2. `NexusClient.getModRequirements(int gameId, int modId)` — the proof
   query verbatim (query Reqs($modId: ID!, $gameId: ID!) { mod(...) {
   name modRequirements { nexusRequirements { totalCount nodes { modId
   gameId modName externalRequirement notes url } } } } }); variables as
   strings (String.valueOf). Null-data error guard from birth.
   @Cacheable(cacheNames = "modRequirements", key = "#gameId + ':' + #modId").
3. New `ModRequirementRepository` (plain JpaRepository extension).
4. In `CheckService.createModWithFiles`, after archive files: fetch
   requirements, save ModRequirement entities. Mapping:
    - requiredName = modName
    - requiredMod = findByGameAndNexusModId(game, Integer.parseInt(modId))
      — usually misses (required mod not necessarily ingested); null is
      fine and expected; resolution happens at CHECK time, not ingest time
    - raw = the node serialized as JSON (jsonb column's first customer)
5. Truncate + re-ingest (6s on warm cache) and verify:
   SELECT m.name, r.required_name FROM mod_requirement r
   JOIN mod m ON m.id = r.mod_id LIMIT 20;
   — expect SkyUI → SKSE64 among the rows.

**Part 2 — the actual check + report:**
6. Check query: for run N, which required (game, nexus_mod_id) pairs are
   NOT among the run's input mods? (@Query on repository, or stream over
   loaded data — 96 mods, either works.) Only non-external, hard
   requirements in v1.
7. `GET /check/{id}/report` — response record with three sections, two
   nearly free from existing data:
    - missingDependencies (the new query)
    - unavailableMods (available = false rows — the delisted-mod warning)
    - outdatedPins (check_input_file.file_version vs mod.current_version —
      the two-truths comparison, stored since increment 1)
8. Definition of done: curl the report, see real findings (this
   collection genuinely has deps not in the list).

**Housekeeping riding along:** soften the truncation warn wording
("fetched X of reported Y" — sub-cap cases aren't the pagination limit);
NexusApiException still parked.

## Parked / roadmap

- NexusApiException (typed) to replace IllegalStateException in the client.
- Async ingestion + status endpoint (long transaction trade-off).
- Extension-prioritised file fetching within the 10k cap.
- JSpecify @NullMarked adoption (repo-wide null-safety polish).
- Testcontainers integration tests (@DataJpaTest against real Postgres).
- File feedback to Nexus: docs/Int! mismatch; possibly the leaking ES
  errors too (arguably an information-exposure nit on their side).