package com.hrflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // binds @ConfigurationProperties records like AppProperties
public class HrflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrflowApplication.class, args);
	}

}
