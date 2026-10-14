package com.personalsite.workouts;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One movement in the exercise library, e.g. "Bench Press". */
@Entity
@Table(name = "exercises")
public class Exercise {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 100)
	private String name;

	@NotNull
	@Enumerated(EnumType.STRING)
	private MuscleGroup muscleGroup = MuscleGroup.CHEST;

	@NotNull
	@Enumerated(EnumType.STRING)
	private Equipment equipment = Equipment.BARBELL;

	@NotNull
	@Enumerated(EnumType.STRING)
	private Tracking tracking = Tracking.WEIGHT_REPS;

	/** True for exercises you added yourself (only those can be deleted); false for the built-in list. */
	private boolean custom;

	public Exercise() {
	}

	Exercise(String name, MuscleGroup muscleGroup, Equipment equipment, Tracking tracking) {
		this.name = name;
		this.muscleGroup = muscleGroup;
		this.equipment = equipment;
		this.tracking = tracking;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public MuscleGroup getMuscleGroup() {
		return muscleGroup;
	}

	public void setMuscleGroup(MuscleGroup muscleGroup) {
		this.muscleGroup = muscleGroup;
	}

	public Equipment getEquipment() {
		return equipment;
	}

	public void setEquipment(Equipment equipment) {
		this.equipment = equipment;
	}

	public Tracking getTracking() {
		return tracking;
	}

	public void setTracking(Tracking tracking) {
		this.tracking = tracking;
	}

	public boolean isCustom() {
		return custom;
	}

	public void setCustom(boolean custom) {
		this.custom = custom;
	}

}
