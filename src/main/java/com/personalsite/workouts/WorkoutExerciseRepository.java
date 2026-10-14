package com.personalsite.workouts;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, Long> {

	/** Every time an exercise was done, newest workout first. */
	List<WorkoutExercise> findByExerciseIdOrderByWorkoutPerformedOnDescWorkoutIdDesc(Long exerciseId);

	boolean existsByExerciseId(Long exerciseId);

}
