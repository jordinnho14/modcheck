package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.ModRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModRequirementRepository extends JpaRepository<ModRequirement, Integer> {
    @Query("""
    select r from ModRequirement r
    where r.mod.id in (select cif.mod.id from CheckInputFile cif where cif.checkRun.id = :checkRunId)
      and r.requiredNexusModId is not null
      and r.requiredNexusModId <> 0
      and r.requiredNexusModId not in (
          select m.nexusModId from CheckInputFile cif2 join cif2.mod m where cif2.checkRun.id = :checkRunId)
    """)
    List<ModRequirement> findMissingRequirements(Long checkRunId);
}
