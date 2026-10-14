package com.personalsite.workouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:workouttests")
@AutoConfigureMockMvc
@WithMockUser
class WorkoutTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WorkoutRepository workouts;

	@Autowired
	private ExerciseRepository exercises;

	@Autowired
	private WorkoutService service;

	@BeforeEach
	void clean() {
		workouts.deleteAll();
	}

	@Test
	void libraryIsSeededWithBuiltInExercises() {
		assertThat(exercises.count()).isGreaterThanOrEqualTo(ExerciseSeeder.BUILT_IN.size());
		assertThat(exercises.findByNameIgnoreCase("bench press")).isPresent();
	}

	@Test
	void startingFromAPresetAddsItsExercisesWithThreeSetsEach() throws Exception {
		mvc.perform(post("/workouts").param("preset", "Push").with(csrf())).andExpect(status().is3xxRedirection());

		Workout workout = workouts.findAll().getFirst();
		assertThat(workout.getName()).isEqualTo("Push Day");
		assertThat(workout.getExercises()).extracting(entry -> entry.getExercise().getName())
			.containsExactlyElementsOf(WorkoutService.PRESETS.get("Push"));
		assertThat(workout.getExercises().getFirst().getSets()).hasSize(3);

		mvc.perform(get("/workouts/" + workout.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Bench Press")))
			.andExpect(content().string(containsString("data-set-form")));
	}

	@Test
	void checkingASetInTheBackgroundReturnsProgressAsJson() throws Exception {
		Workout workout = service.start("Test", "Push");
		WorkoutSet set = workout.getExercises().getFirst().getSets().getFirst();

		mvc.perform(post("/workouts/{id}/sets/{s}", workout.getId(), set.getId())
			.param("weight", "135")
			.param("reps", "8")
			.param("toggle", "true")
			.header("X-Requested-With", "fetch")
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completed").value(true))
			.andExpect(jsonPath("$.doneSets").value(1))
			.andExpect(jsonPath("$.volume").value(1080));

		WorkoutSet saved = workouts.findById(workout.getId()).orElseThrow().getExercises().getFirst().getSets().getFirst();
		assertThat(saved.getWeight()).isEqualTo(135.0);
		assertThat(saved.getReps()).isEqualTo(8);
		assertThat(saved.isCompleted()).isTrue();
	}

	@Test
	void plainFormSaveRedirectsBackToTheExercise() throws Exception {
		Workout workout = service.start("Test", "Legs");
		WorkoutExercise entry = workout.getExercises().getFirst();
		WorkoutSet set = entry.getSets().getFirst();

		mvc.perform(post("/workouts/{id}/sets/{s}", workout.getId(), set.getId()).param("weight", "225")
			.param("reps", "abc")
			.with(csrf()))
			.andExpect(status().isSeeOther())
			.andExpect(redirectedUrl("/workouts/" + workout.getId() + "#entry-" + entry.getId()));
	}

	@Test
	void nextWorkoutIsPrefilledFromLastTimeAndBeatingItIsAPersonalRecord() {
		Exercise bench = exercises.findByNameIgnoreCase("Bench Press").orElseThrow();

		Workout first = service.start("Day 1", null);
		first.setPerformedOn(LocalDate.now().minusDays(3));
		workouts.save(first);
		service.addExercise(first.getId(), bench.getId());
		first = workouts.findById(first.getId()).orElseThrow();
		for (WorkoutSet set : first.getExercises().getFirst().getSets()) {
			service.updateSet(first.getId(), set.getId(), new WorkoutService.SetForm(135.0, 8, null, null, true, false));
		}

		Workout second = service.start("Day 2", null);
		service.addExercise(second.getId(), bench.getId());
		second = workouts.findById(second.getId()).orElseThrow();
		WorkoutExercise entry = second.getExercises().getFirst();
		assertThat(entry.getSets()).hasSize(3).allSatisfy(set -> {
			assertThat(set.getWeight()).isEqualTo(135.0);
			assertThat(set.getReps()).isEqualTo(8);
			assertThat(set.isCompleted()).isFalse();
		});
		assertThat(service.previousSets(second).get(entry.getId())).containsOnly("135 × 8");

		WorkoutSet heavier = entry.getSets().getFirst();
		service.updateSet(second.getId(), heavier.getId(), new WorkoutService.SetForm(145.0, 8, null, null, true, false));
		assertThat(service.personalRecords(workouts.findById(second.getId()).orElseThrow()))
			.containsExactly(heavier.getId());

		var history = service.history(bench);
		assertThat(history.sessions()).hasSize(2);
		assertThat(history.heaviest()).isEqualTo(145.0);
		assertThat(history.chart()).isNotNull();
	}

	@Test
	void repeatCopiesExercisesAndSetsUnchecked() throws Exception {
		Workout original = service.start("Pull", "Pull");
		mvc.perform(post("/workouts/{id}/repeat", original.getId()).with(csrf())).andExpect(status().is3xxRedirection());

		assertThat(workouts.count()).isEqualTo(2);
		Workout copy = workouts.findAllByOrderByPerformedOnDescIdDesc().getFirst();
		assertThat(copy.getId()).isNotEqualTo(original.getId());
		assertThat(copy.getTotalSets()).isEqualTo(original.getTotalSets());
		assertThat(copy.getCompletedSets()).isZero();
	}

	@Test
	void editingAWorkout() throws Exception {
		Workout workout = service.start("Test", "Upper");
		Long id = workout.getId();
		Long firstEntry = workout.getExercises().getFirst().getId();

		mvc.perform(post("/workouts/{id}/exercises/{e}/move", id, firstEntry).param("direction", "down").with(csrf()));
		assertThat(workouts.findById(id).orElseThrow().getExercises().get(1).getId()).isEqualTo(firstEntry);

		mvc.perform(post("/workouts/{id}/exercises/{e}/sets", id, firstEntry).with(csrf()));
		assertThat(workouts.findById(id).orElseThrow().getExercises().get(1).getSets()).hasSize(4);

		mvc.perform(post("/workouts/{id}/exercises/{e}/delete", id, firstEntry).with(csrf()));
		assertThat(workouts.findById(id).orElseThrow().getExercises()).hasSize(5);

		mvc.perform(post("/workouts/{id}/finish", id).with(csrf()));
		assertThat(workouts.findById(id).orElseThrow().isFinished()).isTrue();

		mvc.perform(get("/workouts")).andExpect(status().isOk()).andExpect(content().string(containsString("Test")));
	}

	@Test
	void customExercisesCanBeAddedButNotDuplicated() throws Exception {
		mvc.perform(post("/workouts/exercises").param("name", "Landmine Press")
			.param("muscleGroup", "SHOULDERS")
			.param("equipment", "BARBELL")
			.param("tracking", "WEIGHT_REPS")
			.with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(exercises.findByNameIgnoreCase("landmine press")).get().matches(Exercise::isCustom);

		mvc.perform(post("/workouts/exercises").param("name", "bench press")
			.param("muscleGroup", "CHEST")
			.param("equipment", "BARBELL")
			.param("tracking", "WEIGHT_REPS")
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("already in the library")));

		Exercise bench = exercises.findByNameIgnoreCase("Bench Press").orElseThrow();
		mvc.perform(get("/workouts/exercises/{id}", bench.getId())).andExpect(status().isOk());
	}

}
