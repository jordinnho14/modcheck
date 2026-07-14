package dev.modcheck.modcheck.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class NexusClientConfig {

    @Bean
    RestClient nexusRestClient(NexusProperties props) {
        return RestClient.builder()
            .baseUrl(props.restBaseUrl())
            .defaultHeader("apikey", props.apiKey())
            .defaultHeader("User-Agent", props.userAgent())
            .defaultHeader("Accept", "application/json")
            .requestInterceptor((request, body, execution) -> {
                log.debug("rest>>> {} {}", request.getURI(), request.getURI());
                return execution.execute(request, body);
            })
            .build();
    }

    @Bean
    RestClient nexusGraphQLClient(NexusProperties props) {
        return RestClient.builder()
            .baseUrl(props.graphqlUrl())
            .defaultHeader("apikey", props.apiKey())
            .defaultHeader("User-Agent", props.userAgent())
            .defaultHeader("Accept", "application/json")
            .requestInterceptor((request, body, execution) -> {
                log.debug("graphql>>> {} {}", request.getURI(), request.getURI());
                return execution.execute(request, body);
            })
            .build();
    }
}
