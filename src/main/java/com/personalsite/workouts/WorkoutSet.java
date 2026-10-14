package com.personalsite.workouts;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One set of an exercise. Which fields are used depends on the exercise's {@link Tracking}:
 * weight + reps, reps only, or minutes + distance.
 */
@Entity
@Table(name = "workout_sets")
public class WorkoutSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private WorkoutExercise workoutExercise;

	/** Pounds. */
	private Double weight;

	private Integer reps;

	private Double minutes;

	/** Miles. */
	private Double distance;

	/** Warm-up sets are shown differently and left out of personal records. */
	private boolean warmup;

	private boolean completed;

	public WorkoutSet() {
	}

	/** A fresh, unchecked set with the same numbers as {@code other}, used to pre-fill from a past workout. */
	public static WorkoutSet copyOf(WorkoutSet other) {
		WorkoutSet set = new WorkoutSet();
		set.weight = other.weight;
		set.reps = other.reps;
		set.minutes = other.minutes;
		set.distance = other.distance;
		set.warmup = other.warmup;
		return set;
	}

	public Long getId() {
		return id;
	}

	public WorkoutExercise getWorkoutExercise() {
		return workoutExercise;
	}

	void setWorkoutExercise(WorkoutExercise workoutExercise) {
		this.workoutExercise = workoutExercise;
	}

	public Double getWeight() {
		return weight;
	}

	public void setWeight(Double weight) {
		this.weight = weight;
	}

	public Integer getReps() {
		return reps;
	}

	public void setReps(Integer reps) {
		this.reps = reps;
	}

	public Double getMinutes() {
		return minutes;
	}

	public void setMinutes(Double minutes) {
		this.minutes = minutes;
	}

	public Double getDistance() {
		return distance;
	}

	public void setDistance(Double distance) {
		this.distance = distance;
	}

	public boolean isWarmup() {
		return warmup;
	}

	public void setWarmup(boolean warmup) {
		this.warmup = warmup;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	public double getVolume() {
		return weight == null || reps == null ? 0 : weight * reps;
	}

	/** Estimated one-rep max (Epley formula), or 0 when there's no weight or reps. */
	public double getEstimatedOneRepMax() {
		if (weight == null || reps == null || reps <= 0 || weight <= 0) {
			return 0;
		}
		return reps == 1 ? weight : weight * (1 + reps / 30.0);
	}

	/** Input values without a trailing ".0", so 135.0 shows as "135". */
	public String getWeightText() {
		return weight == null ? null : fmt(weight);
	}

	public String getMinutesText() {
		return minutes == null ? null : fmt(minutes);
	}

	public String getDistanceText() {
		return distance == null ? null : fmt(distance);
	}

	/** Short text like "135 × 8", "12 reps" or "20 min · 2 mi", for the "previous" column and history. */
	public String summary(Tracking tracking) {
		return switch (tracking) {
			case WEIGHT_REPS -> weight == null && reps == null ? "" : fmt(weight) + " × " + (reps == null ? "–" : reps);
			case REPS -> reps == null ? "" : reps + " reps" + (weight != null && weight > 0 ? " +" + fmt(weight) : "");
			case DURATION -> (minutes == null ? "" : fmt(minutes) + " min")
					+ (distance == null ? "" : (minutes == null ? "" : " · ") + fmt(distance) + " mi");
		};
	}

	/** Drops a trailing ".0" so 135.0 shows as "135" but 2.5 stays "2.5". */
	static String fmt(Double value) {
		if (value == null) {
			return "–";
		}
		return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
	}

}
