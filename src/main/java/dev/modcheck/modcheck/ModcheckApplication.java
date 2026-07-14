package dev.modcheck.modcheck;

import dev.modcheck.modcheck.client.NexusClient;
import dev.modcheck.modcheck.config.NexusProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ModcheckApplication {

	public static void main(String[] args) {
		SpringApplication.run(ModcheckApplication.class, args);
	}

    @Bean
    CommandLineRunner checkpointA(NexusClient client) {
        return args -> {
            var mod = client.getModMetadata("skyrimspecialedition", 1090);
            System.out.println("Fetched: " + mod.name() + " v" + mod.version()
                + " by " + mod.author() + " (available: " + mod.available() + ")");


            var collection = client.getCollection("xk05aw");
            System.out.println("Collection: " + collection.name()
                + " rev " + collection.latestPublishedRevision().revisionNumber()
                + " with " + collection.latestPublishedRevision().modFiles().size() + " files");

            var files = client.getModFiles(1704, 1090);
            System.out.println("Apocalypse has " + files.size() + " archive files");
        };
    }

}
