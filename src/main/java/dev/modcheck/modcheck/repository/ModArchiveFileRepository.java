package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.ModArchiveFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModArchiveFileRepository extends JpaRepository<ModArchiveFile, Long> {
}
