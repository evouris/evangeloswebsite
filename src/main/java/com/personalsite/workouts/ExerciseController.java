package com.personalsite.workouts;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/** The exercise library: browse, add your own, and see each exercise's history and records. */
@Controller
@RequestMapping("/workouts/exercises")
public class ExerciseController {

	private final ExerciseRepository exercises;

	private final WorkoutExerciseRepository entries;

	private final WorkoutService service;

	public ExerciseController(ExerciseRepository exercises, WorkoutExerciseRepository entries,
			WorkoutService service) {
		this.exercises = exercises;
		this.entries = entries;
		this.service = service;
	}

	@GetMapping
	public String library(@RequestParam(required = false) MuscleGroup group, Model model) {
		fillLibrary(group, model);
		model.addAttribute("exercise", new Exercise());
		return "workouts/exercises";
	}

	@PostMapping
	public String add(@Valid @ModelAttribute("exercise") Exercise exercise, BindingResult result,
			@RequestParam(required = false) MuscleGroup group, Model model, RedirectAttributes redirect) {
		if (!result.hasFieldErrors("name")) {
			exercise.setName(exercise.getName().strip());
			if (exercises.findByNameIgnoreCase(exercise.getName()).isPresent()) {
				result.rejectValue("name", "duplicate", "That exercise is already in the library.");
			}
		}
		if (result.hasErrors()) {
			fillLibrary(group, model);
			return "workouts/exercises";
		}
		exercise.setCustom(true);
		exercises.save(exercise);
		redirect.addFlashAttribute("message", "Added " + exercise.getName() + ".");
		return "redirect:/workouts/exercises?group=" + exercise.getMuscleGroup();
	}

	@GetMapping("/{id}")
	public String show(@PathVariable Long id, Model model) {
		return exercises.findById(id).map(exercise -> {
			model.addAttribute("history", service.history(exercise));
			return "workouts/exercise";
		}).orElse("redirect:/workouts/exercises");
	}

	/** Only exercises you added and never logged can be deleted, so history is never lost. */
	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		exercises.findById(id).ifPresent(exercise -> {
			if (!exercise.isCustom()) {
				redirect.addFlashAttribute("error", "Built-in exercises can't be deleted.");
			}
			else if (entries.existsByExerciseId(id)) {
				redirect.addFlashAttribute("error", exercise.getName() + " is in a logged workout, so it stays.");
			}
			else {
				exercises.delete(exercise);
				redirect.addFlashAttribute("message", "Deleted " + exercise.getName() + ".");
			}
		});
		return "redirect:/workouts/exercises";
	}

	private void fillLibrary(MuscleGroup group, Model model) {
		List<Exercise> all = exercises.findAllByOrderByNameAsc();
		model.addAttribute("exercises", group == null ? all
				: all.stream().filter(exercise -> exercise.getMuscleGroup() == group).toList());
		model.addAttribute("group", group);
		model.addAttribute("total", all.size());
		model.addAttribute("muscleGroups", MuscleGroup.values());
		model.addAttribute("equipment", Equipment.values());
		model.addAttribute("trackings", Tracking.values());
	}

}
