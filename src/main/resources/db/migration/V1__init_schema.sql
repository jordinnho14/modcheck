-- ModCheck schema, V1.
-- Design notes (from the Stage 0 spike):
--   * Nexus mod IDs are only unique PER GAME (mod 1090 exists in every game domain),
--     so the natural key for a mod is (game_id, nexus_mod_id), never nexus_mod_id alone.
--   * A collection revision can pin multiple files from the same mod (e.g. PunknPatch
--     supplying two files), so check_input rows key on the FILE, not the mod.
--   * file version can lag the mod's current version — that gap IS the "outdated" check,
--     so we store both.

CREATE TABLE game (
                      id            BIGSERIAL PRIMARY KEY,
                      nexus_game_id INT         NOT NULL UNIQUE,      -- e.g. 1704
                      domain_name   TEXT        NOT NULL UNIQUE       -- e.g. 'skyrimspecialedition'
);

CREATE TABLE mod (
                     id              BIGSERIAL PRIMARY KEY,
                     game_id         BIGINT      NOT NULL REFERENCES game(id),
                     nexus_mod_id    INT         NOT NULL,
                     name            TEXT        NOT NULL,
                     current_version TEXT,
                     author          TEXT,
                     available       BOOLEAN     NOT NULL DEFAULT TRUE,
                     adult           BOOLEAN     NOT NULL DEFAULT FALSE,
                     updated_at      TIMESTAMPTZ,
                     last_fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                     UNIQUE (game_id, nexus_mod_id)                  -- the real identity of a mod
);

-- Files *inside* a mod's archive (from modFileContents). Increment 3's conflict
-- detection works off file_path; index accordingly.
CREATE TABLE mod_archive_file (
                                  id           BIGSERIAL PRIMARY KEY,
                                  mod_id       BIGINT NOT NULL REFERENCES mod(id) ON DELETE CASCADE,
                                  file_path    TEXT   NOT NULL,
                                  file_name    TEXT   NOT NULL,
                                  extension    TEXT,
                                  file_size    BIGINT
);
CREATE INDEX idx_archive_file_path ON mod_archive_file (file_path);
CREATE INDEX idx_archive_file_mod  ON mod_archive_file (mod_id);

-- Declared requirements (increment 2). required_mod_id is nullable because
-- authors sometimes declare off-site or free-text requirements.
CREATE TABLE mod_requirement (
                                 id              BIGSERIAL PRIMARY KEY,
                                 mod_id          BIGINT NOT NULL REFERENCES mod(id) ON DELETE CASCADE,
                                 required_mod_id BIGINT REFERENCES mod(id),
                                 required_name   TEXT,
                                 raw             JSONB
);
CREATE INDEX idx_requirement_mod ON mod_requirement (mod_id);

-- One run of the checker against one input list.
CREATE TABLE check_run (
                           id           BIGSERIAL PRIMARY KEY,
                           game_id      BIGINT      NOT NULL REFERENCES game(id),
                           input_type   TEXT        NOT NULL,               -- 'COLLECTION' | 'MOD_LIST'
                           source_ref   TEXT,                               -- collection slug, if applicable
                           revision     INT,                                -- collection revision number
                           created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                           summary      JSONB                               -- filled by increments 2-4
);

-- The pinned files that made up the input (a mod may appear more than once).
CREATE TABLE check_input_file (
                                  id             BIGSERIAL PRIMARY KEY,
                                  check_run_id   BIGINT  NOT NULL REFERENCES check_run(id) ON DELETE CASCADE,
                                  mod_id         BIGINT  NOT NULL REFERENCES mod(id),
                                  nexus_file_id  INT     NOT NULL,
                                  file_name      TEXT,
                                  file_version   TEXT,                             -- pinned version in the collection
                                  optional       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_check_input_run ON check_input_file (check_run_id);