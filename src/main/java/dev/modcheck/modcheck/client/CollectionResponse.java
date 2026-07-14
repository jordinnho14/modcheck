package dev.modcheck.modcheck.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionResponse(CollectionData data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CollectionData(ModCollection collection) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModCollection(String name, String slug, GameRef game, Revision latestPublishedRevision) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameRef(String domainName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Revision(int revisionNumber, List<CollectionModFile> modFiles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CollectionModFile(boolean optional, FileInfo file) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileInfo(int fileId, String name, String version, ModRef mod) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModRef(int modId, String name, String version) {}
}
