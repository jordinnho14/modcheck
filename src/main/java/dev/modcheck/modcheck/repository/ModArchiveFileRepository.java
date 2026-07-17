package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.ModArchiveFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModArchiveFileRepository extends JpaRepository<ModArchiveFile, Long> {

    @Query("""
    select af from ModArchiveFile af
    where af.mod.id in (select cif.mod.id from CheckInputFile cif where cif.checkRun.id = :checkRunId)
      and af.filePath in (
          select af2.filePath from ModArchiveFile af2
          where af2.mod.id in (select cif2.mod.id from CheckInputFile cif2 where cif2.checkRun.id = :checkRunId)
          group by af2.filePath
          having count(distinct af2.mod.id) > 1
      )
    """)
    List<ModArchiveFile> findConflictingFiles(Long checkRunId);
}