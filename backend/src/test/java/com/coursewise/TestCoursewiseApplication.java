package com.coursewise;

import org.springframework.boot.SpringApplication;

public class TestCoursewiseApplication {

	public static void main(String[] args) {
		SpringApplication.from(CoursewiseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
