package com.personalsite.portfolio;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortfolioController {

	private final ProjectRepository projects;

	public PortfolioController(ProjectRepository projects) {
		this.projects = projects;
	}

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("featured", projects.findByFeaturedTrueOrderBySortOrderAscIdAsc());
		return "portfolio/home";
	}

	@GetMapping("/about")
	public String about() {
		return "portfolio/about";
	}

	@GetMapping("/projects")
	public String projects(Model model) {
		model.addAttribute("projects", projects.findAllByOrderBySortOrderAscIdAsc());
		return "portfolio/projects";
	}

}
