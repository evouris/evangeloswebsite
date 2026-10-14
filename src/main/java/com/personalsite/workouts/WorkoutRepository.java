package com.personalsite.workouts;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

	List<Workout> findAllByOrderByPerformedOnDescIdDesc();

	List<Workout> findByPerformedOnGreaterThanEqual(LocalDate since);

}
