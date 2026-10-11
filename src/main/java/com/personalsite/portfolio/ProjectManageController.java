package com.personalsite.portfolio;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

/** Add, edit, and delete portfolio projects. Everything under /projects/manage gets locked behind login in Stage 4. */
@Controller
@RequestMapping("/projects/manage")
public class ProjectManageController {

	private final ProjectRepository projects;

	public ProjectManageController(ProjectRepository projects) {
		this.projects = projects;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("projects", projects.findAllByOrderBySortOrderAscIdAsc());
		model.addAttribute("project", new Project());
		return "portfolio/manage-projects";
	}

	@PostMapping
	public String add(@Valid @ModelAttribute("project") Project project, BindingResult result, Model model) {
		if (result.hasErrors()) {
			model.addAttribute("projects", projects.findAllByOrderBySortOrderAscIdAsc());
			return "portfolio/manage-projects";
		}
		projects.save(project);
		return "redirect:/projects/manage";
	}

	@GetMapping("/{id}")
	public String edit(@PathVariable Long id, Model model) {
		return projects.findById(id).map(project -> {
			model.addAttribute("projects", projects.findAllByOrderBySortOrderAscIdAsc());
			model.addAttribute("project", project);
			model.addAttribute("editingId", id);
			return "portfolio/manage-projects";
		}).orElse("redirect:/projects/manage");
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Valid @ModelAttribute("project") Project form, BindingResult result,
			Model model) {
		if (result.hasErrors()) {
			model.addAttribute("projects", projects.findAllByOrderBySortOrderAscIdAsc());
			model.addAttribute("editingId", id);
			return "portfolio/manage-projects";
		}
		projects.findById(id).ifPresent(project -> {
			project.setTitle(form.getTitle());
			project.setDescription(form.getDescription());
			project.setTechStack(form.getTechStack());
			project.setRepoUrl(form.getRepoUrl());
			project.setLiveUrl(form.getLiveUrl());
			project.setFeatured(form.isFeatured());
			project.setSortOrder(form.getSortOrder());
			projects.save(project);
		});
		return "redirect:/projects/manage";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		projects.deleteById(id);
		return "redirect:/projects/manage";
	}

}
