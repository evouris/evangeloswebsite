package com.personalsite.workouts;

/** What gets logged for each set of an exercise, which decides the columns on the workout page. */
public enum Tracking {

	/** Weight (lb) and reps, e.g. bench press. */
	WEIGHT_REPS("Weight + reps"),
	/** Reps only, e.g. pull-ups or push-ups. */
	REPS("Reps only"),
	/** Minutes and an optional distance (mi), e.g. a run or a plank. */
	DURATION("Time + distance");

	private final String label;

	Tracking(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}
