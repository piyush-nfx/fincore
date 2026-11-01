package com.piyush.fincore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // binds the fincore.* settings into typed records (JwtProperties, AdminProperties)
public class FincoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(FincoreApplication.class, args);
	}

}
