package com.personalsite.workouts;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A small line chart of an exercise's best set per workout, laid out here so the template only has to
 * draw SVG. Coordinates are in a fixed {@link #WIDTH} × {@link #HEIGHT} viewBox.
 */
public record Chart(String line, List<Point> points, List<Tick> ticks, String firstDate, String lastDate) {

	public static final int WIDTH = 640;

	public static final int HEIGHT = 220;

	static final int LEFT = 48;

	static final int RIGHT = 16;

	static final int TOP = 16;

	static final int BOTTOM = 28;

	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM d");

	public record Point(double x, double y, String date, String value) {
	}

	public record Tick(double y, String label) {
	}

	/** Builds the chart from sessions in date order (oldest first); null when there's nothing to plot. */
	static Chart of(List<WorkoutService.Session> sessions) {
		List<WorkoutService.Session> plotted = sessions.stream().filter(s -> s.best() > 0).toList();
		if (plotted.size() < 2) {
			return null;
		}
		double max = plotted.stream().mapToDouble(WorkoutService.Session::best).max().orElse(1);
		double min = plotted.stream().mapToDouble(WorkoutService.Session::best).min().orElse(0);
		double step = niceStep((max - min) / 3);
		double low = Math.floor(min / step) * step;
		double high = Math.ceil(max / step) * step;
		if (high == low) {
			high = low + step;
		}

		double plotWidth = WIDTH - LEFT - RIGHT;
		double plotHeight = HEIGHT - TOP - BOTTOM;
		List<Point> points = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < plotted.size(); i++) {
			WorkoutService.Session session = plotted.get(i);
			double x = LEFT + plotWidth * i / (plotted.size() - 1);
			double y = TOP + plotHeight * (1 - (session.best() - low) / (high - low));
			line.append(i == 0 ? "M" : " L").append(round(x)).append(' ').append(round(y));
			points.add(new Point(round(x), round(y), session.workout().getPerformedOn().format(LABEL),
					WorkoutSet.fmt(Math.round(session.best() * 10) / 10.0)));
		}

		List<Tick> ticks = new ArrayList<>();
		int tickCount = (int) Math.round((high - low) / step);
		for (int i = 0; i <= tickCount; i++) {
			double value = Math.round((low + i * step) * 100) / 100.0;
			ticks.add(new Tick(round(TOP + plotHeight * (1 - (value - low) / (high - low))), WorkoutSet.fmt(value)));
		}
		return new Chart(line.toString(), points, ticks, points.getFirst().date(), points.getLast().date());
	}

	/** Rounds a raw step up to 1, 2, 2.5 or 5 times a power of ten so axis labels read cleanly. */
	static double niceStep(double raw) {
		if (raw <= 0) {
			return 1;
		}
		double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		for (double factor : new double[] { 1, 2, 2.5, 5, 10 }) {
			if (raw <= factor * magnitude) {
				return factor * magnitude;
			}
		}
		return 10 * magnitude;
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}

	public int getWidth() {
		return WIDTH;
	}

	public int getHeight() {
		return HEIGHT;
	}

	public int getLeft() {
		return LEFT;
	}

	public int getRight() {
		return WIDTH - RIGHT;
	}

	public int getBottom() {
		return HEIGHT - BOTTOM;
	}

}
