package com.nobx.rocketdrive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RocketdriveTaskManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RocketdriveTaskManagerApplication.class, args);
		
	}

}
