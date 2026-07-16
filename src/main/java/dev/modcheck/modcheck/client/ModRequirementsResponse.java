package dev.modcheck.modcheck.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModRequirementsResponse(ModRequirementsResponse.Data data, List<ModRequirementsResponse.GraphQLError> errors) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(ModNode mod) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModNode(String name, Requirements modRequirements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requirements(NexusRequirements nexusRequirements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NexusRequirements(int totalCount, List<RequirementNode> nodes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementNode(String modId, String gameId, String modName,
                                  boolean externalRequirement, String notes, String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphQLError(String message) {}
}
