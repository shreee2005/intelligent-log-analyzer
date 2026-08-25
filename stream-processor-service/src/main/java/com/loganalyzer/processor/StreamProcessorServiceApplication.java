package com.loganalyzer.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StreamProcessorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamProcessorServiceApplication.class, args);
	}

}
