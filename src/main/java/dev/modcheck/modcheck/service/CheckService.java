package dev.modcheck.modcheck.service;

import dev.modcheck.modcheck.client.CollectionResponse.*;
import dev.modcheck.modcheck.client.ModMetadata;
import dev.modcheck.modcheck.client.ModRequirementsResponse;
import dev.modcheck.modcheck.client.NexusClient;
import dev.modcheck.modcheck.domain.*;
import dev.modcheck.modcheck.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckService {

    private final NexusClient nexusClient;
    private final GameRepository gameRepository;
    private final ModRepository modRepository;
    private final ModArchiveFileRepository modArchiveFileRepository;
    private final CheckRunRepository checkRunRepository;
    private final CheckInputFileRepository checkInputFileRepository;
    private final ModRequirementRepository modRequirementRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public IngestResult ingestCollection(String slug) {
        final ModCollection collection = nexusClient.getCollection(slug);
        String domainName = collection.game().domainName();

        List<CollectionModFile> modFiles = collection.latestPublishedRevision().modFiles();
        if (modFiles.isEmpty()) {
            throw new IllegalArgumentException("Collection " + slug + " has no mod files.");
        }

        Game game = gameRepository.findByDomainName(domainName)
            .orElseGet(() -> createGame(domainName, modFiles.getFirst().file().mod().modId()));

        CheckRun checkRun = CheckRun.builder()
            .game(game)
            .inputType(InputType.COLLECTION)
            .sourceRef(slug)
            .revision(collection.latestPublishedRevision().revisionNumber())
            .createdAt(java.time.OffsetDateTime.now())
            .build();

        checkRunRepository.save(checkRun);

        int fileCount = 0;
        Set<Long> seenModIds = new HashSet<>();

        for (CollectionModFile modFile : modFiles) {
            int modId = modFile.file().mod().modId();
            Mod mod = modRepository.findByGameAndNexusModId(game, modId)
                .orElseGet(() -> createModWithFiles(game, modFile.file().mod()));

            checkInputFileRepository.save(CheckInputFile.builder()
                .checkRun(checkRun)
                .mod(mod)
                .nexusFileId(modFile.file().fileId())
                .fileName(modFile.file().name())
                .fileVersion(modFile.file().version())
                .optional(modFile.optional())
                .build());
            fileCount++;
            seenModIds.add(mod.getId());
        }
        return new IngestResult(checkRun.getId(), seenModIds.size(), fileCount);

    }

    @Transactional
    public IngestResult ingestModList(String gameDomain, List<Integer> modIds) {
        if (modIds == null || modIds.isEmpty()) {
            throw new IllegalArgumentException("Mod list must contain at least one mod id.");
        }

        Game game = gameRepository.findByDomainName(gameDomain)
            .orElseGet(() -> createGame(gameDomain, modIds.getFirst()));

        CheckRun checkRun = CheckRun.builder()
            .game(game)
            .inputType(InputType.MOD_LIST)
            .sourceRef(null)
            .revision(null)
            .createdAt(OffsetDateTime.now())
            .build();

        checkRunRepository.save(checkRun);

        int fileCount = 0;
        Set<Long> seenModIds = new HashSet<>();

        for (Integer modId : modIds) {
            Mod mod = modRepository.findByGameAndNexusModId(game, modId)
                .orElseGet(() -> createModWithFiles(game, new ModRef(modId, "Unknown mod " + modId, null)));

            checkInputFileRepository.save(CheckInputFile.builder()
                .checkRun(checkRun)
                .mod(mod)
                .nexusFileId(null)
                .fileName(null)
                .fileVersion(null)
                .optional(false)
                .build());
            fileCount++;
            seenModIds.add(mod.getId());
        }
        return new IngestResult(checkRun.getId(), seenModIds.size(), fileCount);
    }

    private Game createGame(String domainName, int firstModId) {
        ModMetadata meta = nexusClient.getModMetadata(domainName, firstModId);
        Game game = new Game();
        game.setDomainName(domainName);
        game.setNexusGameId(meta.gameId());

        return gameRepository.save(game);

    }

    private Mod createModWithFiles(Game game, ModRef modRef) {
        ModMetadata meta;

        try {
            meta = nexusClient.getModMetadata(game.getDomainName(), modRef.modId());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Mod {} not found on Nexus (delisted?) — storing placeholder from collection data", modRef.modId());
            return modRepository.save(Mod.builder()
                .game(game)
                .nexusModId(modRef.modId())
                .name(modRef.name())
                .currentVersion(modRef.version())
                .available(false)
                .adult(false)
                .lastFetchedAt(OffsetDateTime.now())
                .build());
        }
        Mod mod = Mod.builder()
            .game(game)
            .nexusModId(meta.modId())
            .name(meta.name())
            .currentVersion(meta.version())
            .author(meta.author())
            .available(meta.available())
            .adult(meta.containsAdultContent())
            .updatedAt(meta.updatedTime())
            .lastFetchedAt(OffsetDateTime.now())
            .build();

        mod = modRepository.save(mod);

        var archiveFiles = nexusClient.getModFiles(game.getNexusGameId(), meta.modId());
        for (var af : archiveFiles) {
            modArchiveFileRepository.save(ModArchiveFile.builder()
                .mod(mod)
                .filePath(af.filePath())
                .fileName(af.fileName())
                .extension(af.fileExtension())
                .fileSize(af.fileSize() == null ? null : Long.parseLong(af.fileSize()))
                .build());
        }

        var requirements = nexusClient.getModRequirements(game.getNexusGameId(), meta.modId());
        for (var req : requirements) {
            modRequirementRepository.save(ModRequirement.builder()
                .mod(mod)
                .requiredMod(resolveRequiredMod(game, req))
                .requiredName(req.modName())
                .raw(toJson(req))
                .requiredNexusModId((Integer.parseInt(req.modId())))
                .build());
        }
        return mod;
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private Mod resolveRequiredMod(Game game, ModRequirementsResponse.RequirementNode req) {
        if (req.externalRequirement()) {
            return null;   // not a Nexus mod; nothing to link
        }
        return modRepository.findByGameAndNexusModId(game, Integer.parseInt(req.modId()))
            .orElse(null);
    }


    public record IngestResult(Long checkRunId, int modCount, int fileCount) {}
}