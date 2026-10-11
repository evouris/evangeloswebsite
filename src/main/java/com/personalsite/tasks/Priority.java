package com.personalsite.tasks;

/** Declared lowest to highest, so sorting in reverse puts HIGH first. */
public enum Priority {

	LOW("Low"),
	MEDIUM("Medium"),
	HIGH("High");

	private final String label;

	Priority(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}
