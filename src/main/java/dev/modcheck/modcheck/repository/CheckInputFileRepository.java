package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.CheckInputFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInputFileRepository extends JpaRepository<CheckInputFile, Long> {
}
