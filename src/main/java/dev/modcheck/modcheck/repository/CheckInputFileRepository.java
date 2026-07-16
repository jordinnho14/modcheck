package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.CheckInputFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckInputFileRepository extends JpaRepository<CheckInputFile, Long> {
    List<CheckInputFile> findByCheckRunId(Long checkRunId);
}
