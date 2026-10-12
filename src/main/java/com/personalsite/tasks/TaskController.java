package com.personalsite.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/tasks")
public class TaskController {

	/** Filter tabs, in display order. */
	private static final Map<String, Predicate<Task>> FILTERS = new LinkedHashMap<>();
	static {
		FILTERS.put("all", task -> true);
		FILTERS.put("open", task -> !task.isCompleted());
		FILTERS.put("overdue", Task::isOverdue);
		FILTERS.put("done", Task::isCompleted);
	}

	/** Open tasks first, then soonest due date (no date last), then highest priority. */
	private static final Comparator<Task> DISPLAY_ORDER = Comparator.comparing(Task::isCompleted)
		.thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Task::getPriority, Comparator.reverseOrder())
		.thenComparing(Task::getId);

	private final TaskRepository tasks;

	private final IcsTaskImporter importer;

	public TaskController(TaskRepository tasks, IcsTaskImporter importer) {
		this.tasks = tasks;
		this.importer = importer;
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "all") String filter, Model model) {
		fillList(filter, model);
		model.addAttribute("task", new Task());
		return "tasks/list";
	}

	@PostMapping
	public String add(@Valid @ModelAttribute("task") Task task, BindingResult result,
			@RequestParam(defaultValue = "all") String filter, Model model) {
		if (result.hasErrors()) {
			fillList(filter, model);
			return "tasks/list";
		}
		tasks.save(task);
		return redirectTo(filter);
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable Long id, @RequestParam(defaultValue = "all") String filter, Model model) {
		return tasks.findById(id).map(task -> {
			fillList(filter, model);
			model.addAttribute("task", task);
			model.addAttribute("editingId", id);
			return "tasks/list";
		}).orElse(redirectTo(filter));
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Valid @ModelAttribute("task") Task form, BindingResult result,
			@RequestParam(defaultValue = "all") String filter, Model model) {
		if (result.hasErrors()) {
			fillList(filter, model);
			model.addAttribute("editingId", id);
			return "tasks/list";
		}
		tasks.findById(id).ifPresent(task -> {
			task.setTitle(form.getTitle());
			task.setDueDate(form.getDueDate());
			task.setNotes(form.getNotes());
			task.setPriority(form.getPriority());
			tasks.save(task);
		});
		return redirectTo(filter);
	}

	/** Upload an .ics file (e.g. exported from Canvas) and turn its events into tasks. */
	@PostMapping("/import")
	public String importIcs(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean assignmentsOnly,
			@RequestParam(defaultValue = "false") boolean skipPast, RedirectAttributes redirect) {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("importError", "Choose an .ics file first.");
			return redirectTo("all");
		}
		try {
			String ics = new String(file.getBytes(), StandardCharsets.UTF_8);
			redirect.addFlashAttribute("importMessage", importer.importIcs(ics, assignmentsOnly, skipPast).summary());
		}
		catch (IOException | IllegalArgumentException ex) {
			redirect.addFlashAttribute("importError",
					ex instanceof IllegalArgumentException ? ex.getMessage() : "Couldn't read that file.");
		}
		return redirectTo("all");
	}

	@PostMapping("/{id}/complete")
	public String complete(@PathVariable Long id, @RequestParam(defaultValue = "all") String filter) {
		tasks.findById(id).ifPresent(task -> {
			task.setCompleted(!task.isCompleted());
			tasks.save(task);
		});
		return redirectTo(filter);
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam(defaultValue = "all") String filter) {
		tasks.deleteById(id);
		return redirectTo(filter);
	}

	private void fillList(String filter, Model model) {
		String active = FILTERS.containsKey(filter) ? filter : "all";
		List<Task> all = tasks.findAll();

		Map<String, Long> counts = new LinkedHashMap<>();
		FILTERS.forEach((name, test) -> counts.put(name, all.stream().filter(test).count()));

		model.addAttribute("tasks", all.stream().filter(FILTERS.get(active)).sorted(DISPLAY_ORDER).toList());
		model.addAttribute("filter", active);
		model.addAttribute("counts", counts);
		model.addAttribute("priorities", Priority.values());
	}

	private String redirectTo(String filter) {
		return "redirect:/tasks?filter=" + (FILTERS.containsKey(filter) ? filter : "all");
	}

}
