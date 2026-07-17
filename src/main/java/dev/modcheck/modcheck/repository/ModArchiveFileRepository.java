package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.ModArchiveFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModArchiveFileRepository extends JpaRepository<ModArchiveFile, Long> {

    @Query("""
        select af from ModArchiveFile af
        where af.mod.id in (select cif.mod.id from CheckInputFile cif where cif.checkRun.id = :checkRunId)
        """)
    List<ModArchiveFile> findArchiveFilesForCheckRun(Long checkRunId);
}