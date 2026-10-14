package com.personalsite.workouts;

import static com.personalsite.workouts.Equipment.BAND;
import static com.personalsite.workouts.Equipment.BARBELL;
import static com.personalsite.workouts.Equipment.BODYWEIGHT;
import static com.personalsite.workouts.Equipment.CABLE;
import static com.personalsite.workouts.Equipment.DUMBBELL;
import static com.personalsite.workouts.Equipment.KETTLEBELL;
import static com.personalsite.workouts.Equipment.MACHINE;
import static com.personalsite.workouts.Equipment.OTHER;
import static com.personalsite.workouts.MuscleGroup.BACK;
import static com.personalsite.workouts.MuscleGroup.BICEPS;
import static com.personalsite.workouts.MuscleGroup.CALVES;
import static com.personalsite.workouts.MuscleGroup.CARDIO;
import static com.personalsite.workouts.MuscleGroup.CHEST;
import static com.personalsite.workouts.MuscleGroup.CORE;
import static com.personalsite.workouts.MuscleGroup.FOREARMS;
import static com.personalsite.workouts.MuscleGroup.FULL_BODY;
import static com.personalsite.workouts.MuscleGroup.GLUTES;
import static com.personalsite.workouts.MuscleGroup.HAMSTRINGS;
import static com.personalsite.workouts.MuscleGroup.QUADS;
import static com.personalsite.workouts.MuscleGroup.SHOULDERS;
import static com.personalsite.workouts.MuscleGroup.TRICEPS;
import static com.personalsite.workouts.Tracking.DURATION;
import static com.personalsite.workouts.Tracking.REPS;
import static com.personalsite.workouts.Tracking.WEIGHT_REPS;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Fills the exercise library with common gym movements. Runs on every start but only inserts names
 * that aren't there yet, so adding a line here later shows up without touching your logged workouts.
 */
@Component
public class ExerciseSeeder implements CommandLineRunner {

	static final List<Exercise> BUILT_IN = List.of(
			// Chest
			new Exercise("Bench Press", CHEST, BARBELL, WEIGHT_REPS),
			new Exercise("Incline Bench Press", CHEST, BARBELL, WEIGHT_REPS),
			new Exercise("Decline Bench Press", CHEST, BARBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Bench Press", CHEST, DUMBBELL, WEIGHT_REPS),
			new Exercise("Incline Dumbbell Press", CHEST, DUMBBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Fly", CHEST, DUMBBELL, WEIGHT_REPS),
			new Exercise("Incline Dumbbell Fly", CHEST, DUMBBELL, WEIGHT_REPS),
			new Exercise("Cable Crossover", CHEST, CABLE, WEIGHT_REPS),
			new Exercise("Low-to-High Cable Fly", CHEST, CABLE, WEIGHT_REPS),
			new Exercise("Chest Press Machine", CHEST, MACHINE, WEIGHT_REPS),
			new Exercise("Pec Deck", CHEST, MACHINE, WEIGHT_REPS),
			new Exercise("Smith Machine Bench Press", CHEST, MACHINE, WEIGHT_REPS),
			new Exercise("Push-Up", CHEST, BODYWEIGHT, REPS),
			new Exercise("Chest Dip", CHEST, BODYWEIGHT, REPS),
			// Back
			new Exercise("Deadlift", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("Barbell Row", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("Pendlay Row", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("T-Bar Row", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("Rack Pull", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Row", BACK, DUMBBELL, WEIGHT_REPS),
			new Exercise("Chest-Supported Dumbbell Row", BACK, DUMBBELL, WEIGHT_REPS),
			new Exercise("Lat Pulldown", BACK, CABLE, WEIGHT_REPS),
			new Exercise("Close-Grip Lat Pulldown", BACK, CABLE, WEIGHT_REPS),
			new Exercise("Seated Cable Row", BACK, CABLE, WEIGHT_REPS),
			new Exercise("Straight-Arm Pulldown", BACK, CABLE, WEIGHT_REPS),
			new Exercise("Machine Row", BACK, MACHINE, WEIGHT_REPS),
			new Exercise("Assisted Pull-Up", BACK, MACHINE, WEIGHT_REPS),
			new Exercise("Pull-Up", BACK, BODYWEIGHT, REPS),
			new Exercise("Chin-Up", BACK, BODYWEIGHT, REPS),
			new Exercise("Inverted Row", BACK, BODYWEIGHT, REPS),
			new Exercise("Back Extension", BACK, BODYWEIGHT, REPS),
			new Exercise("Good Morning", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("Barbell Shrug", BACK, BARBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Shrug", BACK, DUMBBELL, WEIGHT_REPS),
			// Shoulders
			new Exercise("Overhead Press", SHOULDERS, BARBELL, WEIGHT_REPS),
			new Exercise("Push Press", SHOULDERS, BARBELL, WEIGHT_REPS),
			new Exercise("Seated Dumbbell Press", SHOULDERS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Arnold Press", SHOULDERS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Lateral Raise", SHOULDERS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Front Raise", SHOULDERS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Rear Delt Fly", SHOULDERS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Cable Lateral Raise", SHOULDERS, CABLE, WEIGHT_REPS),
			new Exercise("Face Pull", SHOULDERS, CABLE, WEIGHT_REPS),
			new Exercise("Reverse Pec Deck", SHOULDERS, MACHINE, WEIGHT_REPS),
			new Exercise("Shoulder Press Machine", SHOULDERS, MACHINE, WEIGHT_REPS),
			new Exercise("Upright Row", SHOULDERS, BARBELL, WEIGHT_REPS),
			new Exercise("Band Pull-Apart", SHOULDERS, BAND, REPS),
			// Biceps
			new Exercise("Barbell Curl", BICEPS, BARBELL, WEIGHT_REPS),
			new Exercise("EZ-Bar Curl", BICEPS, BARBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Curl", BICEPS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Hammer Curl", BICEPS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Incline Dumbbell Curl", BICEPS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Concentration Curl", BICEPS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Preacher Curl", BICEPS, MACHINE, WEIGHT_REPS),
			new Exercise("Cable Curl", BICEPS, CABLE, WEIGHT_REPS),
			new Exercise("Bayesian Cable Curl", BICEPS, CABLE, WEIGHT_REPS),
			// Triceps
			new Exercise("Close-Grip Bench Press", TRICEPS, BARBELL, WEIGHT_REPS),
			new Exercise("Skull Crusher", TRICEPS, BARBELL, WEIGHT_REPS),
			new Exercise("Tricep Pushdown", TRICEPS, CABLE, WEIGHT_REPS),
			new Exercise("Rope Pushdown", TRICEPS, CABLE, WEIGHT_REPS),
			new Exercise("Overhead Cable Extension", TRICEPS, CABLE, WEIGHT_REPS),
			new Exercise("Overhead Dumbbell Extension", TRICEPS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Kickback", TRICEPS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Tricep Dip", TRICEPS, BODYWEIGHT, REPS),
			new Exercise("Diamond Push-Up", TRICEPS, BODYWEIGHT, REPS),
			// Forearms
			new Exercise("Wrist Curl", FOREARMS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Reverse Wrist Curl", FOREARMS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Reverse Curl", FOREARMS, BARBELL, WEIGHT_REPS),
			new Exercise("Farmer's Carry", FOREARMS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Dead Hang", FOREARMS, BODYWEIGHT, DURATION),
			// Quads
			new Exercise("Back Squat", QUADS, BARBELL, WEIGHT_REPS),
			new Exercise("Front Squat", QUADS, BARBELL, WEIGHT_REPS),
			new Exercise("Leg Press", QUADS, MACHINE, WEIGHT_REPS),
			new Exercise("Hack Squat", QUADS, MACHINE, WEIGHT_REPS),
			new Exercise("Leg Extension", QUADS, MACHINE, WEIGHT_REPS),
			new Exercise("Smith Machine Squat", QUADS, MACHINE, WEIGHT_REPS),
			new Exercise("Goblet Squat", QUADS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Bulgarian Split Squat", QUADS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Walking Lunge", QUADS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Reverse Lunge", QUADS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Step-Up", QUADS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Bodyweight Squat", QUADS, BODYWEIGHT, REPS),
			new Exercise("Wall Sit", QUADS, BODYWEIGHT, DURATION),
			// Hamstrings
			new Exercise("Romanian Deadlift", HAMSTRINGS, BARBELL, WEIGHT_REPS),
			new Exercise("Stiff-Leg Deadlift", HAMSTRINGS, BARBELL, WEIGHT_REPS),
			new Exercise("Dumbbell Romanian Deadlift", HAMSTRINGS, DUMBBELL, WEIGHT_REPS),
			new Exercise("Lying Leg Curl", HAMSTRINGS, MACHINE, WEIGHT_REPS),
			new Exercise("Seated Leg Curl", HAMSTRINGS, MACHINE, WEIGHT_REPS),
			new Exercise("Nordic Curl", HAMSTRINGS, BODYWEIGHT, REPS),
			// Glutes
			new Exercise("Hip Thrust", GLUTES, BARBELL, WEIGHT_REPS),
			new Exercise("Glute Bridge", GLUTES, BODYWEIGHT, REPS),
			new Exercise("Sumo Deadlift", GLUTES, BARBELL, WEIGHT_REPS),
			new Exercise("Cable Kickback", GLUTES, CABLE, WEIGHT_REPS),
			new Exercise("Hip Abduction Machine", GLUTES, MACHINE, WEIGHT_REPS),
			new Exercise("Hip Adduction Machine", GLUTES, MACHINE, WEIGHT_REPS),
			new Exercise("Kettlebell Swing", GLUTES, KETTLEBELL, WEIGHT_REPS),
			// Calves
			new Exercise("Standing Calf Raise", CALVES, MACHINE, WEIGHT_REPS),
			new Exercise("Seated Calf Raise", CALVES, MACHINE, WEIGHT_REPS),
			new Exercise("Leg Press Calf Raise", CALVES, MACHINE, WEIGHT_REPS),
			new Exercise("Dumbbell Calf Raise", CALVES, DUMBBELL, WEIGHT_REPS),
			// Core
			new Exercise("Plank", CORE, BODYWEIGHT, DURATION),
			new Exercise("Side Plank", CORE, BODYWEIGHT, DURATION),
			new Exercise("Crunch", CORE, BODYWEIGHT, REPS),
			new Exercise("Sit-Up", CORE, BODYWEIGHT, REPS),
			new Exercise("Hanging Leg Raise", CORE, BODYWEIGHT, REPS),
			new Exercise("Hanging Knee Raise", CORE, BODYWEIGHT, REPS),
			new Exercise("Lying Leg Raise", CORE, BODYWEIGHT, REPS),
			new Exercise("Russian Twist", CORE, BODYWEIGHT, REPS),
			new Exercise("Bicycle Crunch", CORE, BODYWEIGHT, REPS),
			new Exercise("Mountain Climber", CORE, BODYWEIGHT, REPS),
			new Exercise("Dead Bug", CORE, BODYWEIGHT, REPS),
			new Exercise("Ab Wheel Rollout", CORE, OTHER, REPS),
			new Exercise("Cable Crunch", CORE, CABLE, WEIGHT_REPS),
			new Exercise("Pallof Press", CORE, CABLE, WEIGHT_REPS),
			new Exercise("Decline Sit-Up", CORE, BODYWEIGHT, REPS),
			// Full body
			new Exercise("Power Clean", FULL_BODY, BARBELL, WEIGHT_REPS),
			new Exercise("Hang Clean", FULL_BODY, BARBELL, WEIGHT_REPS),
			new Exercise("Clean and Jerk", FULL_BODY, BARBELL, WEIGHT_REPS),
			new Exercise("Snatch", FULL_BODY, BARBELL, WEIGHT_REPS),
			new Exercise("Thruster", FULL_BODY, BARBELL, WEIGHT_REPS),
			new Exercise("Kettlebell Clean and Press", FULL_BODY, KETTLEBELL, WEIGHT_REPS),
			new Exercise("Turkish Get-Up", FULL_BODY, KETTLEBELL, WEIGHT_REPS),
			new Exercise("Burpee", FULL_BODY, BODYWEIGHT, REPS),
			new Exercise("Box Jump", FULL_BODY, BODYWEIGHT, REPS),
			new Exercise("Sled Push", FULL_BODY, OTHER, DURATION),
			new Exercise("Battle Ropes", FULL_BODY, OTHER, DURATION),
			// Cardio
			new Exercise("Running", CARDIO, OTHER, DURATION),
			new Exercise("Treadmill", CARDIO, MACHINE, DURATION),
			new Exercise("Incline Walk", CARDIO, MACHINE, DURATION),
			new Exercise("Cycling", CARDIO, OTHER, DURATION),
			new Exercise("Stationary Bike", CARDIO, MACHINE, DURATION),
			new Exercise("Rowing Machine", CARDIO, MACHINE, DURATION),
			new Exercise("Elliptical", CARDIO, MACHINE, DURATION),
			new Exercise("Stair Climber", CARDIO, MACHINE, DURATION),
			new Exercise("Jump Rope", CARDIO, OTHER, DURATION),
			new Exercise("Swimming", CARDIO, OTHER, DURATION),
			new Exercise("Walking", CARDIO, OTHER, DURATION));

	private final ExerciseRepository exercises;

	public ExerciseSeeder(ExerciseRepository exercises) {
		this.exercises = exercises;
	}

	@Override
	public void run(String... args) {
		Set<String> existing = exercises.findAll().stream()
			.map(exercise -> exercise.getName().toLowerCase(Locale.ROOT))
			.collect(Collectors.toSet());
		exercises.saveAll(BUILT_IN.stream()
			.filter(template -> !existing.contains(template.getName().toLowerCase(Locale.ROOT)))
			.map(template -> new Exercise(template.getName(), template.getMuscleGroup(), template.getEquipment(),
					template.getTracking()))
			.toList());
	}

}
