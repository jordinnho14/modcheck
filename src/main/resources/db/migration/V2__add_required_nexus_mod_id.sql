-- The nexus-side mod id of a requirement, columnised for check-time joins.
-- required_mod_id (FK) is ingestion-order-dependent (only links mods already
-- ingested), so the check resolves against this instead. 0 = external/none.
ALTER TABLE mod_requirement ADD COLUMN required_nexus_mod_id INT;
CREATE INDEX idx_requirement_required_nexus ON mod_requirement (required_nexus_mod_id);