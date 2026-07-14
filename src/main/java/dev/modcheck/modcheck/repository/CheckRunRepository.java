package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.CheckRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckRunRepository extends JpaRepository<CheckRun, Long> {
}
