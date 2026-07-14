package dev.modcheck.modcheck.repository;

import dev.modcheck.modcheck.domain.Game;
import dev.modcheck.modcheck.domain.Mod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModRepository extends JpaRepository<Mod, Long> {
    Optional<Mod> findByGameAndNexusModId(Game game, Integer nexusModId);
}
