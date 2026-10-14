package com.personalsite.workouts;

public enum Equipment {

	BARBELL("Barbell"),
	DUMBBELL("Dumbbell"),
	MACHINE("Machine"),
	CABLE("Cable"),
	BODYWEIGHT("Bodyweight"),
	KETTLEBELL("Kettlebell"),
	BAND("Band"),
	OTHER("Other");

	private final String label;

	Equipment(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}
