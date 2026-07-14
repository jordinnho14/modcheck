package dev.modcheck.modcheck.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "game")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nexus_game_id", nullable = false, unique = true)
    private Integer nexusGameId;

    @Column(name = "domain_name", nullable = false, unique = true)
    private String domainName;
}
