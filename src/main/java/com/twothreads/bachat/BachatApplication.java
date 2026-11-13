package com.twothreads.bachat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
public class BachatApplication {

	public static void main(String[] args) {
		SpringApplication.run(BachatApplication.class, args);
	}

}
