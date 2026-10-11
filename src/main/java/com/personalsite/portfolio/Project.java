package com.personalsite.portfolio;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "projects")
public class Project {

	// Links must be empty or start with http(s):// so a stray "javascript:" URL can't end up in an href.
	private static final String URL_PATTERN = "^$|^https?://.+";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	private String title;

	@Column(length = 2000)
	private String description;

	/** Comma-separated, e.g. "Java, Spring Boot, Tailwind". */
	private String techStack;

	@Pattern(regexp = URL_PATTERN, message = "must start with http:// or https://")
	private String repoUrl;

	@Pattern(regexp = URL_PATTERN, message = "must start with http:// or https://")
	private String liveUrl;

	private boolean featured;

	/** Lower numbers show first. */
	private int sortOrder;

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getTechStack() {
		return techStack;
	}

	public void setTechStack(String techStack) {
		this.techStack = techStack;
	}

	public List<String> getTechList() {
		if (techStack == null) {
			return List.of();
		}
		return Arrays.stream(techStack.split(","))
			.map(String::trim)
			.filter(tech -> !tech.isEmpty())
			.toList();
	}

	public String getRepoUrl() {
		return repoUrl;
	}

	public void setRepoUrl(String repoUrl) {
		this.repoUrl = repoUrl;
	}

	public String getLiveUrl() {
		return liveUrl;
	}

	public void setLiveUrl(String liveUrl) {
		this.liveUrl = liveUrl;
	}

	public boolean isFeatured() {
		return featured;
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

}
