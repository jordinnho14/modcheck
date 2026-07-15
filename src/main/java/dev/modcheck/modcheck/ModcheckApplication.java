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

}
