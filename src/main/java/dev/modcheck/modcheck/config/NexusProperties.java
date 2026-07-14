package dev.modcheck.modcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public record NexusProperties(
    String apiKey,
    String restBaseUrl,
    String graphqlUrl,
    String userAgent
) {}
