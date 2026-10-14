package com.personalsite.workouts;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One gym session: a list of exercises, each with its sets. */
@Entity
@Table(name = "workouts")
public class Workout {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 100)
	private String name;

	@NotNull
	private LocalDate performedOn = LocalDate.now();

	private LocalDateTime startedAt = LocalDateTime.now();

	/** Set when you hit "Finish"; null while the workout is still in progress. */
	private LocalDateTime finishedAt;

	@Column(length = 2000)
	private String notes;

	// Eager so pages can read the whole workout after the transaction closes (open-in-view is off).
	// SUBSELECT loads children in one extra query per level instead of a join, which Hibernate needs for nested lists.
	@OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@OrderBy("position ASC, id ASC")
	private List<WorkoutExercise> exercises = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getPerformedOn() {
		return performedOn;
	}

	public void setPerformedOn(LocalDate performedOn) {
		this.performedOn = performedOn;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public List<WorkoutExercise> getExercises() {
		return exercises;
	}

	public void addExercise(WorkoutExercise entry) {
		entry.setWorkout(this);
		entry.setPosition(exercises.stream().mapToInt(WorkoutExercise::getPosition).max().orElse(-1) + 1);
		exercises.add(entry);
	}

	public boolean isFinished() {
		return finishedAt != null;
	}

	public int getTotalSets() {
		return exercises.stream().mapToInt(entry -> entry.getSets().size()).sum();
	}

	public int getCompletedSets() {
		return exercises.stream().mapToInt(WorkoutExercise::getCompletedSets).sum();
	}

	/** Total pounds lifted (weight × reps) over completed sets. */
	public double getVolume() {
		return exercises.stream().mapToDouble(WorkoutExercise::getVolume).sum();
	}

	/** How long the workout took, or null if it has no start/finish time. */
	public Duration getDuration() {
		if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
			return null;
		}
		return Duration.between(startedAt, finishedAt);
	}

	/** Seconds since the workout started, for the live timer (counted on the server so time zones don't matter). */
	public long getElapsedSeconds() {
		return startedAt == null ? 0 : Math.max(0, Duration.between(startedAt, LocalDateTime.now()).toSeconds());
	}

	/** e.g. "1h 05m" or "48m". */
	public String getDurationLabel() {
		Duration duration = getDuration();
		if (duration == null) {
			return null;
		}
		long hours = duration.toHours();
		int minutes = duration.toMinutesPart();
		if (hours == 0 && minutes == 0) {
			return "<1m";
		}
		return hours > 0 ? "%dh %02dm".formatted(hours, minutes) : minutes + "m";
	}

}
