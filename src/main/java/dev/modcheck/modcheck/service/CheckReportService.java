package dev.modcheck.modcheck.service;

import dev.modcheck.modcheck.domain.CheckInputFile;
import dev.modcheck.modcheck.domain.CheckRun;
import dev.modcheck.modcheck.domain.ModArchiveFile;
import dev.modcheck.modcheck.domain.ModRequirement;
import dev.modcheck.modcheck.repository.CheckInputFileRepository;
import dev.modcheck.modcheck.repository.CheckRunRepository;
import dev.modcheck.modcheck.repository.ModArchiveFileRepository;
import dev.modcheck.modcheck.repository.ModRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckReportService {

    private static final Set<String> HIGH_RISK_EXTENSIONS = Set.of("esp", "esl", "esm", "dll", "pex");

    private final CheckRunRepository checkRunRepository;
    private final ModRequirementRepository modRequirementRepository;
    private final CheckInputFileRepository checkInputFileRepository;
    private final ModArchiveFileRepository modArchiveFileRepository;

    @Transactional(readOnly = true)
    public CheckReport getReport(Long checkRunId) {
        CheckRun run = checkRunRepository.findById(checkRunId)
            .orElseThrow(() -> new IllegalArgumentException("No check run with id " + checkRunId));

        List<ModRequirement> missingRequirements = modRequirementRepository.findMissingRequirements(checkRunId);

        Map<Integer, List<ModRequirement>> byRequiredId = missingRequirements.stream()
            .collect(Collectors.groupingBy(ModRequirement::getRequiredNexusModId));

        List<CheckReport.MissingDependency> missingDependencies = byRequiredId.entrySet().stream()
            .map(entry -> new CheckReport.MissingDependency(
                entry.getKey(),
                entry.getValue().getFirst().getRequiredName(),
                entry.getValue().stream()
                    .map(r -> r.getMod().getName())
                    .sorted()
                    .toList()
            ))
            .sorted(Comparator.comparing(d -> -d.requiredBy().size()))
            .toList();

        List<CheckInputFile> inputFiles = checkInputFileRepository.findByCheckRunId(checkRunId);

        List<CheckReport.UnavailableMod> unavailableMods = inputFiles.stream()
            .map(CheckInputFile::getMod)
            .filter(m -> Boolean.FALSE.equals(m.getAvailable()))
            .distinct()
            .map(m -> new CheckReport.UnavailableMod(m.getNexusModId(), m.getName()))
            .toList();

        List<CheckReport.OutdatedPin> outdatedPins = inputFiles.stream()
            .filter(f -> hasText(f.getFileVersion())
                && hasText(f.getMod().getCurrentVersion())
                && !f.getFileVersion().equals(f.getMod().getCurrentVersion())
            )
            .map(f -> new CheckReport.OutdatedPin(
                f.getMod().getName(),
                f.getFileVersion(),
                f.getMod().getCurrentVersion()
            ))
            .toList();

        List<ModArchiveFile> conflictingFiles = modArchiveFileRepository.findConflictingFiles(checkRunId);

        Map<String, List<ModArchiveFile>> byFilePath = conflictingFiles.stream()
            .collect(Collectors.groupingBy(ModArchiveFile::getFilePath));

        List<CheckReport.FileConflict> fileConflicts = byFilePath.entrySet().stream()
            .map(entry -> new CheckReport.FileConflict(
                entry.getKey(),
                severityOf(entry.getValue().getFirst().getExtension()),
                entry.getValue().stream()
                    .map(af -> af.getMod().getName())
                    .distinct()
                    .sorted()
                    .toList()
            ))
            .sorted(Comparator
                .comparing((CheckReport.FileConflict c) -> "high".equals(c.severity()) ? 0 : 1)
                .thenComparing(c -> -c.mods().size()))
            .toList();

        return new CheckReport(
            run.getId(),
            run.getSourceRef(),
            run.getRevision(),
            missingDependencies,
            unavailableMods,
            outdatedPins,
            fileConflicts
        );
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String severityOf(String extension) {
        if (extension == null) {
            return "low";
        }
        String normalized = extension.startsWith(".") ? extension.substring(1) : extension;
        return HIGH_RISK_EXTENSIONS.contains(normalized.toLowerCase()) ? "high" : "low";
    }

    public record CheckReport(
        Long checkRunId,
        String sourceRef,
        Integer revision,
        List<MissingDependency> missingDependencies,
        List<UnavailableMod> unavailableMods,
        List<OutdatedPin> outdatedPins,
        List<FileConflict> fileConflicts) {

        public record MissingDependency(Integer nexusModId, String name, List<String> requiredBy) {}
        public record UnavailableMod(Integer nexusModId, String name) {}
        public record OutdatedPin(String modName, String pinnedVersion, String currentVersion) {}
        public record FileConflict(String filePath, String severity, List<String> mods) {}
    }

}