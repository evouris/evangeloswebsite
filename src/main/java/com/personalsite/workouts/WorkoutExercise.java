package com.personalsite.workouts;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** An exercise as done in one particular workout, with its sets. */
@Entity
@Table(name = "workout_exercises")
public class WorkoutExercise {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Workout workout;

	@ManyToOne(optional = false)
	private Exercise exercise;

	/** Order within the workout, smallest first. */
	private int position;

	@OneToMany(mappedBy = "workoutExercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@OrderBy("id ASC")
	private List<WorkoutSet> sets = new ArrayList<>();

	public WorkoutExercise() {
	}

	public WorkoutExercise(Exercise exercise) {
		this.exercise = exercise;
	}

	public Long getId() {
		return id;
	}

	public Workout getWorkout() {
		return workout;
	}

	void setWorkout(Workout workout) {
		this.workout = workout;
	}

	public Exercise getExercise() {
		return exercise;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public List<WorkoutSet> getSets() {
		return sets;
	}

	public void addSet(WorkoutSet set) {
		set.setWorkoutExercise(this);
		sets.add(set);
	}

	/** What to show in each set's first column: "W" for warm-ups, otherwise 1, 2, 3... counting working sets only. */
	public List<String> getLabels() {
		List<String> labels = new ArrayList<>();
		int number = 0;
		for (WorkoutSet set : sets) {
			labels.add(set.isWarmup() ? "W" : String.valueOf(++number));
		}
		return labels;
	}

	public int getCompletedSets() {
		return (int) sets.stream().filter(WorkoutSet::isCompleted).count();
	}

	public boolean isAllDone() {
		return !sets.isEmpty() && getCompletedSets() == sets.size();
	}

	public double getVolume() {
		return sets.stream().filter(WorkoutSet::isCompleted).mapToDouble(WorkoutSet::getVolume).sum();
	}

}
