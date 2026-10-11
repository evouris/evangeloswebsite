package com.personalsite.tasks;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/tasks")
public class TaskController {

	private final TaskRepository tasks;

	public TaskController(TaskRepository tasks) {
		this.tasks = tasks;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("tasks", tasks.findAllByOrderByCompletedAscDueDateAscIdAsc());
		model.addAttribute("task", new Task());
		return "tasks/list";
	}

	@PostMapping
	public String add(@Valid @ModelAttribute("task") Task task, BindingResult result, Model model) {
		if (result.hasErrors()) {
			model.addAttribute("tasks", tasks.findAllByOrderByCompletedAscDueDateAscIdAsc());
			return "tasks/list";
		}
		tasks.save(task);
		return "redirect:/tasks";
	}

	@PostMapping("/{id}/complete")
	public String complete(@PathVariable Long id) {
		tasks.findById(id).ifPresent(task -> {
			task.setCompleted(!task.isCompleted());
			tasks.save(task);
		});
		return "redirect:/tasks";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		tasks.deleteById(id);
		return "redirect:/tasks";
	}

}
