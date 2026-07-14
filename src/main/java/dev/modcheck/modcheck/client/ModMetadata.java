package dev.modcheck.modcheck.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModMetadata(
    int modId,
    int gameId,
    String name,
    String version,
    String author,
    boolean available,
    String status,
    boolean containsAdultContent,
    OffsetDateTime updatedTime
)
{}
