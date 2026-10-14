package com.personalsite.workouts;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/workouts")
public class WorkoutController {

	/** Sent by workout.js so set changes save in the background instead of reloading the page. */
	private static final String FETCH_HEADER = "X-Requested-With";

	private final WorkoutRepository workouts;

	private final ExerciseRepository exercises;

	private final WorkoutService service;

	public WorkoutController(WorkoutRepository workouts, ExerciseRepository exercises, WorkoutService service) {
		this.workouts = workouts;
		this.exercises = exercises;
		this.service = service;
	}

	@GetMapping
	public String dashboard(Model model) {
		model.addAttribute("dash", service.dashboard());
		model.addAttribute("presets", WorkoutService.PRESETS);
		return "workouts/dashboard";
	}

	@PostMapping
	public String start(@RequestParam(required = false) String name, @RequestParam(required = false) String preset) {
		return redirectTo(service.start(name, preset).getId());
	}

	@GetMapping("/{id}")
	public String show(@PathVariable Long id, Model model) {
		return workouts.findById(id).map(workout -> {
			model.addAttribute("workout", workout);
			model.addAttribute("previous", service.previousSets(workout));
			model.addAttribute("records", service.personalRecords(workout));
			Map<MuscleGroup, List<Exercise>> groups = libraryByGroup();
			model.addAttribute("groups", groups);
			model.addAttribute("libraryCount", groups.values().stream().mapToInt(List::size).sum());
			return "workouts/workout";
		}).orElse("redirect:/workouts");
	}

	/** Rename, re-date or add notes to a workout. */
	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @RequestParam String name,
			@RequestParam(required = false) LocalDate performedOn, @RequestParam(required = false) String notes) {
		workouts.findById(id).ifPresent(workout -> {
			if (!name.isBlank()) {
				workout.setName(name.strip().substring(0, Math.min(100, name.strip().length())));
			}
			if (performedOn != null) {
				workout.setPerformedOn(performedOn);
			}
			workout.setNotes(notes == null || notes.isBlank() ? null : notes.strip());
			workouts.save(workout);
		});
		return redirectTo(id);
	}

	/** Finish a workout, or re-open a finished one. */
	@PostMapping("/{id}/finish")
	public String finish(@PathVariable Long id, RedirectAttributes redirect) {
		workouts.findById(id).ifPresent(workout -> {
			workout.setFinishedAt(workout.isFinished() ? null : LocalDateTime.now());
			workouts.save(workout);
			if (workout.isFinished()) {
				redirect.addFlashAttribute("finished", true);
			}
		});
		return redirectTo(id);
	}

	@PostMapping("/{id}/repeat")
	public String repeat(@PathVariable Long id) {
		return service.repeat(id).map(copy -> redirectTo(copy.getId())).orElse("redirect:/workouts");
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		workouts.deleteById(id);
		return "redirect:/workouts";
	}

	@PostMapping("/{id}/exercises")
	public String addExercise(@PathVariable Long id, @RequestParam Long exerciseId) {
		service.addExercise(id, exerciseId);
		return redirectTo(id) + "#end";
	}

	@PostMapping("/{id}/exercises/{entryId}/delete")
	public String removeExercise(@PathVariable Long id, @PathVariable Long entryId) {
		service.removeExercise(id, entryId);
		return redirectTo(id);
	}

	@PostMapping("/{id}/exercises/{entryId}/move")
	public String moveExercise(@PathVariable Long id, @PathVariable Long entryId, @RequestParam String direction) {
		service.moveExercise(id, entryId, direction);
		return redirectTo(id) + "#entry-" + entryId;
	}

	@PostMapping("/{id}/exercises/{entryId}/sets")
	public String addSet(@PathVariable Long id, @PathVariable Long entryId) {
		service.addSet(id, entryId);
		return redirectTo(id) + "#entry-" + entryId;
	}

	/**
	 * Saves one set row. Plain form posts (no JavaScript) get a redirect back; background saves from
	 * workout.js get JSON with the new state and the workout's progress.
	 */
	@PostMapping("/{id}/sets/{setId}")
	public ResponseEntity<?> updateSet(@PathVariable Long id, @PathVariable Long setId,
			@RequestParam(required = false) String weight, @RequestParam(required = false) String reps,
			@RequestParam(required = false) String minutes, @RequestParam(required = false) String distance,
			@RequestParam(defaultValue = "false") boolean toggle,
			@RequestParam(defaultValue = "false") boolean toggleWarmup,
			@RequestHeader(name = FETCH_HEADER, required = false) String requestedWith) {
		var form = new WorkoutService.SetForm(parseDouble(weight), parseInt(reps), parseDouble(minutes),
				parseDouble(distance), toggle, toggleWarmup);
		var saved = service.updateSet(id, setId, form);
		if (requestedWith == null) {
			String anchor = saved.map(set -> "#entry-" + set.getWorkoutExercise().getId()).orElse("");
			return ResponseEntity.status(HttpStatus.SEE_OTHER)
				.location(URI.create("/workouts/" + id + anchor))
				.build();
		}
		if (saved.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		Workout workout = workouts.findById(id).orElseThrow();
		Set<Long> records = service.personalRecords(workout);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("completed", saved.get().isCompleted());
		body.put("warmup", saved.get().isWarmup());
		body.put("doneSets", workout.getCompletedSets());
		body.put("totalSets", workout.getTotalSets());
		body.put("volume", Math.round(workout.getVolume()));
		body.put("records", records);
		return ResponseEntity.ok(body);
	}

	@PostMapping("/{id}/sets/{setId}/delete")
	public String removeSet(@PathVariable Long id, @PathVariable Long setId,
			@RequestParam(required = false) Long entryId) {
		service.removeSet(id, setId);
		return redirectTo(id) + (entryId == null ? "" : "#entry-" + entryId);
	}

	/** The exercise picker: every muscle group with its exercises, alphabetically. */
	private Map<MuscleGroup, List<Exercise>> libraryByGroup() {
		List<Exercise> all = exercises.findAllByOrderByNameAsc();
		Map<MuscleGroup, List<Exercise>> groups = new LinkedHashMap<>();
		Arrays.stream(MuscleGroup.values()).forEach(group -> {
			List<Exercise> inGroup = all.stream().filter(e -> e.getMuscleGroup() == group).toList();
			if (!inGroup.isEmpty()) {
				groups.put(group, inGroup);
			}
		});
		return groups;
	}

	private static String redirectTo(Long id) {
		return "redirect:/workouts/" + id;
	}

	/** Blank or junk input clears the field rather than failing the whole save. */
	private static Double parseDouble(String value) {
		try {
			return value == null || value.isBlank() ? null : Double.valueOf(value.strip());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer parseInt(String value) {
		Double parsed = parseDouble(value);
		return parsed == null ? null : (int) Math.round(parsed);
	}

}
