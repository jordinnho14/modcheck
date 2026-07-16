package dev.modcheck.modcheck.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "mod_requirement")
@AllArgsConstructor
@Builder
public class ModRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_id", nullable = false)
    private Mod mod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_mod_id")
    private Mod requiredMod;

    @Column(name = "required_name")
    private String requiredName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String raw;

    @Column(name = "required_nexus_mod_id")
    private Integer requiredNexusModId;

}
