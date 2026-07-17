-- MOD_LIST input support. A mod-list check has no pinned file - there's no
-- collection revision to pin from, so it's checking "is this mod present",
-- not "is this exact file version present". nexus_file_id, file_name and
-- file_version only ever meant something for COLLECTION-sourced rows;
-- file_name/file_version were already nullable, nexus_file_id wasn't.
ALTER TABLE check_input_file ALTER COLUMN nexus_file_id DROP NOT NULL;