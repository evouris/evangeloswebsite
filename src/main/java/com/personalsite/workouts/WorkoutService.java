package com.personalsite.workouts;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starting, filling in and summarising workouts. */
@Service
@Transactional
public class WorkoutService {

	/** Ready-made starting points on the "start workout" form: name → exercises from the library. */
	static final Map<String, List<String>> PRESETS = new LinkedHashMap<>();
	static {
		PRESETS.put("Push", List.of("Bench Press", "Incline Dumbbell Press", "Overhead Press", "Lateral Raise",
				"Tricep Pushdown", "Overhead Cable Extension"));
		PRESETS.put("Pull", List.of("Pull-Up", "Barbell Row", "Lat Pulldown", "Seated Cable Row", "Face Pull",
				"Dumbbell Curl", "Hammer Curl"));
		PRESETS.put("Legs", List.of("Back Squat", "Romanian Deadlift", "Leg Press", "Lying Leg Curl", "Leg Extension",
				"Standing Calf Raise"));
		PRESETS.put("Upper", List.of("Bench Press", "Barbell Row", "Overhead Press", "Lat Pulldown", "Dumbbell Curl",
				"Tricep Pushdown"));
		PRESETS.put("Lower", List.of("Back Squat", "Romanian Deadlift", "Bulgarian Split Squat", "Seated Leg Curl",
				"Standing Calf Raise", "Hanging Leg Raise"));
		PRESETS.put("Full Body", List.of("Back Squat", "Bench Press", "Barbell Row", "Overhead Press",
				"Romanian Deadlift", "Plank"));
	}

	/** Sets added for an exercise you've never logged before. */
	private static final int DEFAULT_SETS = 3;

	private final WorkoutRepository workouts;

	private final WorkoutExerciseRepository entries;

	private final ExerciseRepository exercises;

	public WorkoutService(WorkoutRepository workouts, WorkoutExerciseRepository entries, ExerciseRepository exercises) {
		this.workouts = workouts;
		this.entries = entries;
		this.exercises = exercises;
	}

	// ----- Starting workouts -----

	/** A new workout for today, optionally filled from a preset (Push, Pull, ...). */
	public Workout start(String name, String preset) {
		Workout workout = new Workout();
		String presetName = PRESETS.containsKey(preset) ? preset : null;
		workout.setName(name != null && !name.isBlank() ? name.strip()
				: presetName != null ? presetName + " Day" : defaultName());
		workouts.save(workout);
		if (presetName != null) {
			for (String exerciseName : PRESETS.get(presetName)) {
				exercises.findByNameIgnoreCase(exerciseName).ifPresent(exercise -> addPrefilled(workout, exercise));
			}
		}
		return workout;
	}

	/** A new workout for today with the same exercises, sets and numbers as an old one, all unchecked. */
	public Optional<Workout> repeat(Long id) {
		return workouts.findById(id).map(source -> {
			Workout copy = new Workout();
			copy.setName(source.getName());
			for (WorkoutExercise sourceEntry : source.getExercises()) {
				WorkoutExercise entry = new WorkoutExercise(sourceEntry.getExercise());
				sourceEntry.getSets().forEach(set -> entry.addSet(WorkoutSet.copyOf(set)));
				copy.addExercise(entry);
			}
			return workouts.save(copy);
		});
	}

	/** Adds an exercise with as many sets as last time, pre-filled with last time's numbers. */
	public void addExercise(Long workoutId, Long exerciseId) {
		workouts.findById(workoutId).ifPresent(workout -> exercises.findById(exerciseId)
			.ifPresent(exercise -> addPrefilled(workout, exercise)));
	}

	private void addPrefilled(Workout workout, Exercise exercise) {
		WorkoutExercise entry = new WorkoutExercise(exercise);
		List<WorkoutSet> last = lastTime(workout, exercise.getId()).map(WorkoutExercise::getSets).orElse(List.of());
		if (last.isEmpty()) {
			int count = exercise.getTracking() == Tracking.DURATION ? 1 : DEFAULT_SETS;
			for (int i = 0; i < count; i++) {
				entry.addSet(new WorkoutSet());
			}
		}
		else {
			last.forEach(set -> entry.addSet(WorkoutSet.copyOf(set)));
		}
		workout.addExercise(entry);
		workouts.save(workout);
	}

	private String defaultName() {
		int hour = LocalDateTime.now().getHour();
		return hour < 12 ? "Morning Workout" : hour < 17 ? "Afternoon Workout" : "Evening Workout";
	}

	// ----- Editing -----

	public void removeExercise(Long workoutId, Long entryId) {
		workouts.findById(workoutId).ifPresent(workout -> {
			workout.getExercises().removeIf(entry -> entry.getId().equals(entryId));
			workouts.save(workout);
		});
	}

	/** Swaps an exercise with its neighbour; direction is "up" or "down". */
	public void moveExercise(Long workoutId, Long entryId, String direction) {
		workouts.findById(workoutId).ifPresent(workout -> {
			List<WorkoutExercise> list = workout.getExercises();
			int index = indexOf(list, entryId);
			int other = "up".equals(direction) ? index - 1 : index + 1;
			if (index < 0 || other < 0 || other >= list.size()) {
				return;
			}
			// Renumber first so old rows with equal positions still swap cleanly.
			for (int i = 0; i < list.size(); i++) {
				list.get(i).setPosition(i);
			}
			list.get(index).setPosition(other);
			list.get(other).setPosition(index);
			workouts.save(workout);
		});
	}

	/** Adds one more set, copying the numbers of the last set so you only need to tick it. */
	public void addSet(Long workoutId, Long entryId) {
		findEntry(workoutId, entryId).ifPresent(entry -> {
			List<WorkoutSet> sets = entry.getSets();
			WorkoutSet set = sets.isEmpty() ? new WorkoutSet() : WorkoutSet.copyOf(sets.getLast());
			set.setWarmup(false);
			entry.addSet(set);
			entries.save(entry);
		});
	}

	public void removeSet(Long workoutId, Long setId) {
		workouts.findById(workoutId).ifPresent(workout -> {
			workout.getExercises().forEach(entry -> entry.getSets().removeIf(set -> set.getId().equals(setId)));
			workouts.save(workout);
		});
	}

	/** Saves the numbers typed into a set row; toggles its check mark / warm-up flag when asked. */
	public Optional<WorkoutSet> updateSet(Long workoutId, Long setId, SetForm form) {
		return findSet(workoutId, setId).map(set -> {
			set.setWeight(nonNegative(form.weight()));
			set.setReps(form.reps() == null || form.reps() < 0 ? null : form.reps());
			set.setMinutes(nonNegative(form.minutes()));
			set.setDistance(nonNegative(form.distance()));
			if (form.toggle()) {
				set.setCompleted(!set.isCompleted());
			}
			if (form.toggleWarmup()) {
				set.setWarmup(!set.isWarmup());
			}
			workouts.save(set.getWorkoutExercise().getWorkout());
			return set;
		});
	}

	/** Form fields of one set row. {@code toggle} flips the check mark, {@code toggleWarmup} the warm-up flag. */
	public record SetForm(Double weight, Integer reps, Double minutes, Double distance, boolean toggle,
			boolean toggleWarmup) {
	}

	private static Double nonNegative(Double value) {
		return value == null || value < 0 || value.isNaN() || value.isInfinite() ? null : value;
	}

	private Optional<WorkoutExercise> findEntry(Long workoutId, Long entryId) {
		return workouts.findById(workoutId)
			.flatMap(workout -> workout.getExercises().stream().filter(e -> e.getId().equals(entryId)).findFirst());
	}

	private Optional<WorkoutSet> findSet(Long workoutId, Long setId) {
		return workouts.findById(workoutId).flatMap(workout -> workout.getExercises()
			.stream()
			.flatMap(entry -> entry.getSets().stream())
			.filter(set -> set.getId().equals(setId))
			.findFirst());
	}

	private static int indexOf(List<WorkoutExercise> list, Long entryId) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getId().equals(entryId)) {
				return i;
			}
		}
		return -1;
	}

	// ----- History & records -----

	/** The most recent earlier workout that included this exercise, if any. */
	@Transactional(readOnly = true)
	public Optional<WorkoutExercise> lastTime(Workout workout, Long exerciseId) {
		return entries.findByExerciseIdOrderByWorkoutPerformedOnDescWorkoutIdDesc(exerciseId)
			.stream()
			.filter(entry -> isBefore(entry.getWorkout(), workout) && !entry.getSets().isEmpty())
			.findFirst();
	}

	/** For each exercise in the workout: entry id → what you did set-by-set last time ("135 × 8"). */
	@Transactional(readOnly = true)
	public Map<Long, List<String>> previousSets(Workout workout) {
		Map<Long, List<String>> previous = new HashMap<>();
		for (WorkoutExercise entry : workout.getExercises()) {
			Tracking tracking = entry.getExercise().getTracking();
			lastTime(workout, entry.getExercise().getId()).ifPresent(last -> previous.put(entry.getId(),
					last.getSets().stream().map(set -> set.summary(tracking)).toList()));
		}
		return previous;
	}

	/**
	 * Ids of completed sets in this workout that beat every earlier set of the same exercise
	 * (by estimated 1RM, reps, or minutes depending on the exercise). First-ever sessions don't count.
	 */
	@Transactional(readOnly = true)
	public Set<Long> personalRecords(Workout workout) {
		Set<Long> records = new HashSet<>();
		for (WorkoutExercise entry : workout.getExercises()) {
			ToDoubleFunction<WorkoutSet> score = scoreFor(entry.getExercise().getTracking());
			List<WorkoutExercise> earlier = entries
				.findByExerciseIdOrderByWorkoutPerformedOnDescWorkoutIdDesc(entry.getExercise().getId())
				.stream()
				.filter(other -> isBefore(other.getWorkout(), workout))
				.toList();
			if (earlier.isEmpty()) {
				continue;
			}
			double best = earlier.stream()
				.flatMap(other -> other.getSets().stream())
				.filter(set -> set.isCompleted() && !set.isWarmup())
				.mapToDouble(score)
				.max()
				.orElse(0);
			for (WorkoutSet set : entry.getSets()) {
				double value = score.applyAsDouble(set);
				if (set.isCompleted() && !set.isWarmup() && value > best) {
					records.add(set.getId());
					best = value;
				}
			}
		}
		return records;
	}

	static ToDoubleFunction<WorkoutSet> scoreFor(Tracking tracking) {
		return switch (tracking) {
			case WEIGHT_REPS -> WorkoutSet::getEstimatedOneRepMax;
			case REPS -> set -> set.getReps() == null ? 0 : set.getReps();
			case DURATION -> set -> set.getMinutes() == null ? 0 : set.getMinutes();
		};
	}

	/** True when {@code a} happened before {@code b}: earlier day, or same day but created first. */
	private static boolean isBefore(Workout a, Workout b) {
		int byDate = a.getPerformedOn().compareTo(b.getPerformedOn());
		return byDate < 0 || (byDate == 0 && a.getId() < b.getId());
	}

	// ----- Dashboard -----

	@Transactional(readOnly = true)
	public Dashboard dashboard() {
		List<Workout> all = workouts.findAllByOrderByPerformedOnDescIdDesc();
		LocalDate today = LocalDate.now();
		LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Set<LocalDate> days = new HashSet<>();
		all.forEach(workout -> days.add(workout.getPerformedOn()));

		List<WeekDay> week = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			LocalDate day = weekStart.plusDays(i);
			week.add(new WeekDay(day, days.contains(day), day.equals(today)));
		}

		long thisWeek = all.stream().filter(w -> !w.getPerformedOn().isBefore(weekStart)).count();
		long thisMonth = all.stream().filter(w -> !w.getPerformedOn().isBefore(today.withDayOfMonth(1))).count();
		double weekVolume = all.stream()
			.filter(w -> !w.getPerformedOn().isBefore(weekStart))
			.mapToDouble(Workout::getVolume)
			.sum();

		// Weeks in a row with at least one workout. This week only counts once you've trained, so a fresh
		// Monday doesn't break the streak.
		int streak = 0;
		LocalDate cursor = days.stream().anyMatch(d -> !d.isBefore(weekStart)) ? weekStart : weekStart.minusWeeks(1);
		while (true) {
			LocalDate start = cursor;
			if (days.stream().noneMatch(d -> !d.isBefore(start) && d.isBefore(start.plusWeeks(1)))) {
				break;
			}
			streak++;
			cursor = cursor.minusWeeks(1);
		}

		Workout inProgress = all.stream()
			.filter(w -> !w.isFinished())
			.max(Comparator.comparing(Workout::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
			.orElse(null);
		return new Dashboard(all, inProgress, week, thisWeek, thisMonth, weekVolume, streak);
	}

	public record Dashboard(List<Workout> workouts, Workout inProgress, List<WeekDay> week, long thisWeek,
			long thisMonth, double weekVolume, int weekStreak) {
	}

	public record WeekDay(LocalDate date, boolean trained, boolean today) {
	}

	// ----- Exercise history -----

	@Transactional(readOnly = true)
	public ExerciseHistory history(Exercise exercise) {
		Tracking tracking = exercise.getTracking();
		ToDoubleFunction<WorkoutSet> score = scoreFor(tracking);
		List<Session> sessions = new ArrayList<>();
		double heaviest = 0;
		double bestOneRepMax = 0;
		int mostReps = 0;
		double longest = 0;
		double bestVolume = 0;
		int totalSets = 0;
		for (WorkoutExercise entry : entries.findByExerciseIdOrderByWorkoutPerformedOnDescWorkoutIdDesc(exercise.getId())) {
			List<WorkoutSet> done = entry.getSets().stream().filter(WorkoutSet::isCompleted).toList();
			if (done.isEmpty()) {
				continue;
			}
			List<WorkoutSet> working = done.stream().filter(set -> !set.isWarmup()).toList();
			totalSets += done.size();
			for (WorkoutSet set : working) {
				heaviest = Math.max(heaviest, set.getWeight() == null ? 0 : set.getWeight());
				bestOneRepMax = Math.max(bestOneRepMax, set.getEstimatedOneRepMax());
				mostReps = Math.max(mostReps, set.getReps() == null ? 0 : set.getReps());
				longest = Math.max(longest, set.getMinutes() == null ? 0 : set.getMinutes());
			}
			double volume = entry.getVolume();
			bestVolume = Math.max(bestVolume, volume);
			WorkoutSet top = working.stream().max(Comparator.comparingDouble(score)).orElse(null);
			sessions.add(new Session(entry.getWorkout(), done.stream().map(set -> set.summary(tracking)).toList(),
					top == null ? 0 : score.applyAsDouble(top), volume));
		}
		return new ExerciseHistory(exercise, sessions, heaviest, bestOneRepMax, mostReps, longest, bestVolume,
				totalSets, Chart.of(sessions.reversed()));
	}

	/** One workout's worth of an exercise: the sets you checked off and the best of them. */
	public record Session(Workout workout, List<String> sets, double best, double volume) {
	}

	public record ExerciseHistory(Exercise exercise, List<Session> sessions, double heaviest, double bestOneRepMax,
			int mostReps, double longestMinutes, double bestVolume, int totalSets, Chart chart) {
	}

}
