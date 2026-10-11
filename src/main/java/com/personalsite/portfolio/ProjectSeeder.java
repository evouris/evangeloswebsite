package com.personalsite.portfolio;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Adds a first project when the projects table is empty, so the page isn't blank on a fresh database. */
@Component
public class ProjectSeeder implements CommandLineRunner {

	private final ProjectRepository projects;

	public ProjectSeeder(ProjectRepository projects) {
		this.projects = projects;
	}

	@Override
	public void run(String... args) {
		if (projects.count() > 0) {
			return;
		}
		Project site = new Project();
		site.setTitle("evangeloswebsite");
		site.setDescription("This site: a portfolio plus a personal task manager and calendar.");
		site.setTechStack("Java, Spring Boot, Thymeleaf, Tailwind, H2");
		site.setRepoUrl("https://github.com/evouris/evangeloswebsite");
		site.setFeatured(true);
		projects.save(site);
	}

}
