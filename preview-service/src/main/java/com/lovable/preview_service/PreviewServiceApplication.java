package com.lovable.preview_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PreviewServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PreviewServiceApplication.class, args);
	}

}
