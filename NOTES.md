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

## Increment 2 — COMPLETE (first real report issued)

**What was built:** requirements ingestion + the first actual check.
- `getModRequirements` on the client (proof-query verbatim, @Cacheable,
  truncation sentinel instead of a pagination loop — requirement lists
  are single digits).
- V2 Flyway migration adding `required_nexus_mod_id` — first real use of
  the migration machinery. Reason: the `required_mod_id` FK was
  **ingestion-order-dependent** (only linked mods already ingested when
  the requirement row was written — Address Library linked for
  late-alphabet mods, not early ones). Fix: store *their* stable fact
  (the nexus id) at write time; resolve presence at **check time** by
  query. Principle: store raw facts on write, compute conclusions on read.
- `findMissingRequirements` JPQL: requirements of the run's input mods,
  excluding externals (modId 0), whose required nexus id is not among the
  input mods' nexus ids. Set difference in JPQL clothes.
- `CheckReportService` (@Transactional(readOnly = true) — lazy walks safe
  by design) + `GET /check/{id}/report`: three sections —
  missingDependencies (grouped, most-required first), unavailableMods
  (available=false), outdatedPins (pinned vs current version).

**First report findings (run 1, xk05aw rev 311):**
- 16 missing dependencies, ~12 genuine (MergeMapper, XPMSSE, Crash
  Logger, Community Shaders, SkyUI Survival Mode Integration...).
- **VR noise confirmed:** 4 entries are VR variants (SKSEVR, VR Address
  Library, poT Tweaks VR, BOS VR) — authors declare requirements for both
  editions; the API doesn't platform-scope them. Decision: documented
  limitation for v1 rather than a name-substring filter (crude heuristics
  misfire; honest limitation reads better).
- **SKSE64 was NOT missing** — this collection bundles it. (Prediction
  wrong, check right — it read the data, not the expectation.)
- **outdatedPins is really versionMismatches:** PunknPatch pinned 3.0 vs
  "current" 1.0, SkyUI 6.11 vs 6.9 — the mod page's headline version can
  LAG file versions, so mismatches are directionless. Rename or document.
- unavailableMods: exactly one — mod 631, the delisted placeholder,
  identified as "Bethini Pie". Delisted-mod handling end-to-end.

**Also learned:** externals (VC++ redist etc.) arrive with modId "0",
empty names, payload in url — their null convention. The `raw` jsonb
column paid off immediately (forensics on blank rows) and is queryable
(`raw->>'externalRequirement'`), not just archival. Future cheap feature:
an externalRequirements FYI report section, deduped by URL.

**Open decisions carried:** VR-variant handling (limitation vs filter),
outdatedPins naming, externals FYI section.

---

## Increment 3 — file-overlap conflict detection

**What was built:** the file-conflict half of the "conflict checker" pitch
that's been in the README title since day one but unimplemented until now.
- `findConflictingFiles` on `ModArchiveFileRepository`: same
  subquery-in-subquery shape as `findMissingRequirements` — restrict to the
  check run's input mods, group by `file_path`, keep only paths with
  `count(distinct mod_id) > 1`.
- `CheckReportService` groups the results by path into a new `fileConflicts`
  section, severity-tagged by extension.
- **Severity is a plain extension allowlist** (`.esp/.esl/.esm/.dll/.pex` =
  high, everything else = low) — not real load-order or plugin-record
  analysis, just the "textures vs. plugins" distinction from the ES-cap
  discovery above, finally acted on instead of just noted.
- The `file_path` index has been sitting in the V1 migration, unused, since
  the very first schema — the migration comment literally says "Increment
  3's conflict detection works off file_path; index accordingly." Nice to
  finally cash that in.

**Verified against a live run (xk05aw rev 311):** 52 file conflicts found,
all correctly matched against real ingested data — but every single one
came back `"severity":"low"`, including two `.pex` script files that
should have been `"high"`. Root cause: Nexus's GraphQL API returns
`fileExtension` **with a leading dot** (`.pex`, not `pex`) — confirmed by
querying `mod_archive_file` directly. The extension allowlist was checking
for bare values, so every comparison silently failed. Fixed by stripping
a leading `.` before the lowercase comparison, so it's tolerant of either
format rather than assuming one. Exactly the failure mode `.pex`-style
"trust the API's errors over its documentation" lessons from Stage 0 exist
to catch — didn't verify the actual field format before writing the
allowlist, and real data caught it immediately.

**Parked, not done:** severity is still just an extension list, not real
plugin-record inspection — documented honestly as a limitation rather than
oversold.

**Second round, same real run:** the fix above was correct, but the same
xk05aw run surfaced a domain-knowledge gap rather than a code bug — most
of the 52 "conflicts" were `fomod/ModuleConfig.xml`, `fomod/info.xml` and
similar, appearing under three different cases (`fomod/`, `FOMOD/`,
`FOMod/`) as three separate entries. Two things going on:
- **FOMOD files aren't real conflicts at all.** They're installer metadata
  the mod manager reads during setup, never extracted into the game
  folder — every FOMOD-packaged mod has them, so they were pure noise,
  not even low-risk. Excluded entirely now (`lower(file_path) not like
  'fomod/%'`), same move as excluding externals from the missing-dependency
  check.
- **Path comparison was case-sensitive**, which doesn't match how the
  actual target filesystem (NTFS, on basically every real install) treats
  paths. Fixed query and grouping to compare on `lower(file_path)`
  throughout, picking one real-cased path per group for display rather
  than showing the lowercased key.

**Regression tests added, finally.** `normalize`, `isInstallerMetadata`,
and `severityOf` are pure functions with no Spring/DB dependency — dropped
from `private` to package-private so `CheckReportServiceTest` can call
them directly, no `@SpringBootTest` or Postgres needed. Every case is one
of the three real bugs above, not an invented one: dotted vs. bare
extensions, fomod exclusion (including under a `Data/` prefix, and *not*
matching "fomod" as a mere substring of a filename), and the `Data/`-prefix
strip itself (including a short-string edge case that could've thrown on
`regionMatches`). Would've caught all three bugs before manual testing did,
had they existed first — the honest order was bug found live, then test
written after the fact, not test-first.

---

## Increment 4 — MOD_LIST input type

**What was built:** the second half of `InputType` that had sat unused
since the V1 migration — the schema comment literally said `'COLLECTION' |
'MOD_LIST'` from day one, but only `COLLECTION` was ever wired up.
`POST /check/mod-list` `{"gameDomain": "...", "modIds": [...]}` checks a
plain list of mod ids with no Nexus Collection involved — useful for
someone with their own local pile of mods rather than a published,
curated Collection.

**One real schema gap, not a shortcut:** `check_input_file.nexus_file_id`
was `NOT NULL`, but a mod-list check has no pinned file to report — no
collection revision to pin *from*. Rather than reuse a sentinel value
(the project already learned that lesson the hard way in Increment 2,
with the ingestion-order-dependent `required_mod_id` FK), this got a real
V3 migration dropping the `NOT NULL`, with the entity updated to match.
`source_ref`/`revision` on `check_run` were already nullable from the very
first migration — nice bit of foresight that meant no change needed there.

**Zero changes needed in `CheckReportService`.** Every report query
filters by `check_run_id` → `mod_id`, generically, with no awareness of
*how* those rows got created. `outdatedPins`' existing `hasText(fileVersion)`
guard already skips rows with no pinned version to compare — exactly what
a mod-list row has — without a single line changed. The "two truths, two
tables" / generic `CheckInputFile` modeling from Increment 1 paying off
three increments later, for a case it wasn't explicitly designed for.

**Shared code:** `createGame` was collection-shaped (took a `List<CollectionModFile>`
just to reach into the first one for a bootstrap mod id) — refactored to take
a plain `int firstModId`, so both ingestion paths share it. Delisted-mod
handling is shared too: mod-list ids with no name/version to fall back on
(no collection data to borrow from) get a synthetic placeholder
(`"Unknown mod " + modId`) through the same `ModRef`-shaped path collection
ingestion already uses for its own delisted mods.

**Verified against a live run.** `POST /check/mod-list` with SkyUI (12604)
alone → report correctly flags SKSE64 (30379) as missing, required by
SkyUI — the exact same `findMissingRequirements` query collection-based
checks have always used, with zero special-casing for how the input rows
were created. Checking SkyUI + SKSE64 together → `missingDependencies`
correctly empty. `sourceRef`/`revision` both `null` as expected for a
mod-list run. Two genuinely different ingestion paths, one reporting
layer, no branching needed — the schema modeling did its job.

**Third round, same real run:** a false negative this time, not a false
positive — worse, since the whole point of the tool is not missing things.
Several genuine conflicts were split in half: `meshes/.../milllogpile.nif`
and `Data/meshes/.../milllogpile.nif` showed up as two separate, smaller
conflicts instead of one real one, because some mods package their archive
with a `Data/` root and others don't — both land in the same place on
install, but the tool compared the raw strings and saw two unrelated paths.
At least 5 pairs like this in one run.

Fixing it meant a third normalization rule (strip a leading `Data/` before
comparing) on top of the two already there (fomod exclusion, case-folding).
Stacking a third `CASE`-style rule into the nested SQL subquery was going to
stop being readable, so the matching logic moved out of SQL entirely:
`findArchiveFilesForCheckRun` now just fetches the check run's archive
files, and `CheckReportService` does the normalize → group → filter
(`count(distinct mod) > 1`) in plain Java. Slightly more rows fetched than
the old query pulled, a non-issue at this scale (hundreds of mods, not
millions of rows) — and each future packaging quirk is one more line
instead of one more layer of subquery.

---

## Parked / roadmap

- NexusApiException (typed) to replace IllegalStateException in the client.
- Async ingestion + status endpoint (long transaction trade-off).
- Extension-prioritised file fetching within the 10k cap.
- JSpecify @NullMarked adoption (repo-wide null-safety polish).
- Testcontainers integration tests (@DataJpaTest against real Postgres).
- File feedback to Nexus: docs/Int! mismatch; possibly the leaking ES
  errors too (arguably an information-exposure nit on their side).