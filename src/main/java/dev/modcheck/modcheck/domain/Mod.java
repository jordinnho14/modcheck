package dev.modcheck.modcheck.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mod", uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "nexus_mod_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "nexus_mod_id", nullable = false)
    private Integer nexusModId;

    @Column(nullable = false)
    private String name;

    @Column(name = "current_version")
    private String currentVersion;

    @Column
    private String author;

    @Column(nullable = false)
    private Boolean available;

    @Column(nullable = false)
    private Boolean adult;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name="last_fetched_at", nullable = false)
    private OffsetDateTime lastFetchedAt;
}
