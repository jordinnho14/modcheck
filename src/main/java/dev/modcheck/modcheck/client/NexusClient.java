package dev.modcheck.modcheck.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NexusClient {

    private static final int FILE_PAGE_SIZE = 100;
    private static final int MAX_FILE_OFFSET = 10_000;
    private final RestClient nexusRestClient;
    private final RestClient nexusGraphQLClient;

    public NexusClient(RestClient nexusRestClient, RestClient nexusGraphQLClient) {
        this.nexusRestClient = nexusRestClient;
        this.nexusGraphQLClient = nexusGraphQLClient;
    }

    @Cacheable(cacheNames = "modMetadata", key = "#gameDomain + ':' + #modId")
    public ModMetadata getModMetadata(String gameDomain, int modId) {
        return nexusRestClient.get()
            .uri("/games/{game}/mods/{id}.json", gameDomain, modId)
            .retrieve()
            .body(ModMetadata.class);
    }

    public CollectionResponse.ModCollection getCollection(String slug) {
        String query = """
        query CollectionMods($slug: String!) {
          collection(slug: $slug) {
            name
            slug
            game { domainName }
            latestPublishedRevision {
              revisionNumber
              modFiles {
                optional
                file { fileId name version mod { modId name version } }
              }
            }
          }
        }
        """;
        var response = nexusGraphQLClient.post()
            .body(Map.of("query", query, "variables", Map.of("slug", slug)))
            .retrieve()
            .body(CollectionResponse.class);

        if (response.data() == null) {
            throw new IllegalStateException("GraphQL error fetching collection: " + slug
                + ": " + response.errors());
        }
        return response.data().collection();
    }


    @Cacheable(cacheNames = "modFiles", key = "#gameId + ':' + #modId")
    public List<ModFilesResponse.ArchiveFile> getModFiles(int gameId, int modId) {
        String query = """
        query ModFiles($modId: Int!, $gameId: Int!, $offset: Int!, $count: Int!) {
          modFileContents(
            filter: {
              modId:  [{ value: $modId,  op: EQUALS }],
              gameId: [{ value: $gameId, op: EQUALS }]
            },
            offset: $offset,
            count: $count
          ) {
            totalCount
            nodes { modId filePath fileName fileExtension fileSize }
          }
        }
        """;

        List<ModFilesResponse.ArchiveFile> allFiles = new ArrayList<>();
        int offset = 0;
        int totalCount = 0;
        do {
            var pageResponse = nexusGraphQLClient.post()
                .body(Map.of("query", query, "variables", Map.of(
                    "modId", modId,
                    "gameId", gameId,
                    "offset", offset,
                    "count", FILE_PAGE_SIZE
                )))
                .retrieve()
                .body(ModFilesResponse.class);

            if (pageResponse.data() == null) {
                throw new IllegalStateException("GraphQL error fetching files for mod " + modId
                    + ": " + pageResponse.errors());
            }

            var contents = pageResponse.data().modFileContents();
            if (contents.nodes().isEmpty()) {
                break;
            }
            allFiles.addAll(contents.nodes());
            totalCount = contents.totalCount();
            offset += FILE_PAGE_SIZE;
        } while (allFiles.size() < totalCount && offset < MAX_FILE_OFFSET);

        if (allFiles.size() < totalCount) {
            log.warn("File listing truncated for mod {}: fetched {} of {} (Nexus pagination limit)",
                modId, allFiles.size(), totalCount);
        }

        return allFiles;
    }

    @Cacheable(cacheNames = "modRequirements", key = "#gameId + ':' + #modId")
    public List<ModRequirementsResponse.RequirementNode> getModRequirements(int gameId, int modId) {
        String query = """
        query Reqs($modId: ID!, $gameId: ID!) {
          mod(modId: $modId, gameId: $gameId) {
            name
            modRequirements {
              nexusRequirements {
                totalCount
                nodes { modId gameId modName externalRequirement notes url }
              }
            }
          }
        }
        """;

        var response = nexusGraphQLClient.post()
            .body(Map.of("query", query, "variables", Map.of(
                "modId", String.valueOf(modId),
                "gameId", String.valueOf(gameId)
            )))
            .retrieve()
            .body(ModRequirementsResponse.class);

        if (response.data() == null || response.data().mod() == null) {
            throw new IllegalStateException("GraphQL error fetching requirements for mod " + modId
                + ": " + response.errors());
        }

        var nexusReqs = response.data().mod().modRequirements().nexusRequirements();
        if (nexusReqs.nodes().size() < nexusReqs.totalCount()) {
            log.warn("Requirements list truncated for mod {}: fetched {} of {}",
                modId, nexusReqs.nodes().size(), nexusReqs.totalCount());
        }
        return nexusReqs.nodes();
    }
}
