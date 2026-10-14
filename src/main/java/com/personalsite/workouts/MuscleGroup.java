package com.personalsite.workouts;

/** Main muscle group an exercise works; used to group and filter the exercise library. */
public enum MuscleGroup {

	CHEST("Chest"),
	BACK("Back"),
	SHOULDERS("Shoulders"),
	BICEPS("Biceps"),
	TRICEPS("Triceps"),
	FOREARMS("Forearms"),
	QUADS("Quads"),
	HAMSTRINGS("Hamstrings"),
	GLUTES("Glutes"),
	CALVES("Calves"),
	CORE("Core"),
	FULL_BODY("Full body"),
	CARDIO("Cardio");

	private final String label;

	MuscleGroup(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}
