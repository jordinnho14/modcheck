package dev.modcheck.modcheck.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "check_input_file")
public class CheckInputFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_run_id", nullable = false)
    private CheckRun checkRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_id", nullable = false)
    private Mod mod;

    @Column(name = "nexus_file_id")
    private Integer nexusFileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_version")
    private String fileVersion;

    @Column(nullable = false)
    private Boolean optional;

}
