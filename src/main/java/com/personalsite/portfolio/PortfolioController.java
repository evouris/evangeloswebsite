package com.personalsite.portfolio;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortfolioController {

	@GetMapping("/")
	public String home() {
		return "portfolio/home";
	}

	@GetMapping("/about")
	public String about() {
		return "portfolio/about";
	}

	@GetMapping("/projects")
	public String projects() {
		return "portfolio/projects";
	}

}
