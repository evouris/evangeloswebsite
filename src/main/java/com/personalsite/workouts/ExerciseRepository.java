package com.personalsite.workouts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

	List<Exercise> findAllByOrderByNameAsc();

	Optional<Exercise> findByNameIgnoreCase(String name);

}
