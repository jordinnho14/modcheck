package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.ModRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModRequirementRepository extends JpaRepository<ModRequirement, Integer> {
}
