package dev.modcheck.modcheck.service;

import dev.modcheck.modcheck.domain.CheckInputFile;
import dev.modcheck.modcheck.domain.CheckRun;
import dev.modcheck.modcheck.domain.ModRequirement;
import dev.modcheck.modcheck.repository.CheckInputFileRepository;
import dev.modcheck.modcheck.repository.CheckRunRepository;
import dev.modcheck.modcheck.repository.ModRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckReportService {

    private final CheckRunRepository checkRunRepository;
    private final ModRequirementRepository modRequirementRepository;
    private final CheckInputFileRepository checkInputFileRepository;

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

        return new CheckReport(
            run.getId(),
            run.getSourceRef(),
            run.getRevision(),
            missingDependencies,
            unavailableMods,
            outdatedPins
        );
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public record CheckReport(
        Long checkRunId,
        String sourceRef,
        Integer revision,
        List<MissingDependency> missingDependencies,
        List<UnavailableMod> unavailableMods,
        List<OutdatedPin> outdatedPins) {

        public record MissingDependency(Integer nexusModId, String name, List<String> requiredBy) {}
        public record UnavailableMod(Integer nexusModId, String name) {}
        public record OutdatedPin(String modName, String pinnedVersion, String currentVersion) {}
    }

}
