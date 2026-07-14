package dev.modcheck.modcheck.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModFilesResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(ModFileContents modFileContents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModFileContents(int totalCount, List<ArchiveFile> nodes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArchiveFile(int modId, String filePath, String fileName,
                              String fileExtension, String fileSize) {}
}
