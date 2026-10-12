package com.personalsite.tasks;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalsite.tasks.IcsParser.IcsEvent;

/**
 * Turns the events in an .ics file (e.g. a Canvas calendar export) into tasks.
 * Each task remembers its event's UID, so importing a newer file updates tasks instead of duplicating them.
 */
@Service
public class IcsTaskImporter {

	private static final int TITLE_MAX = 255;

	private static final int NOTES_MAX = 2000;

	private final TaskRepository tasks;

	public IcsTaskImporter(TaskRepository tasks) {
		this.tasks = tasks;
	}

	public record ImportResult(int added, int updated, int unchanged, int skipped) {

		public String summary() {
			List<String> parts = new ArrayList<>();
			parts.add("Imported " + added + " new " + (added == 1 ? "task" : "tasks"));
			if (updated > 0) {
				parts.add("updated " + updated);
			}
			if (unchanged > 0) {
				parts.add(unchanged + " already up to date");
			}
			if (skipped > 0) {
				parts.add("skipped " + skipped);
			}
			return String.join(", ", parts) + ".";
		}

	}

	/**
	 * @param assignmentsOnly only import Canvas assignments, not other calendar events like lectures
	 * @param skipPast skip anything due before today
	 */
	@Transactional
	public ImportResult importIcs(String ics, boolean assignmentsOnly, boolean skipPast) {
		LocalDate today = LocalDate.now();
		int added = 0, updated = 0, unchanged = 0, skipped = 0;

		for (IcsEvent event : IcsParser.parse(ics, ZoneId.systemDefault())) {
			if (event.summary() == null
					|| (assignmentsOnly && !event.isCanvasAssignment())
					|| (skipPast && event.date() != null && event.date().isBefore(today))) {
				skipped++;
				continue;
			}
			String title = truncate(event.summary(), TITLE_MAX);

			Optional<Task> existing = event.uid() == null ? Optional.empty() : tasks.findByExternalId(event.uid());
			if (existing.isPresent()) {
				// Only refresh what Canvas owns; keep your own notes, priority, and done status.
				Task task = existing.get();
				if (Objects.equals(task.getTitle(), title) && Objects.equals(task.getDueDate(), event.date())) {
					unchanged++;
				}
				else {
					task.setTitle(title);
					task.setDueDate(event.date());
					tasks.save(task);
					updated++;
				}
			}
			else {
				Task task = new Task();
				task.setTitle(title);
				task.setDueDate(event.date());
				task.setNotes(notesFor(event));
				task.setExternalId(event.uid());
				tasks.save(task);
				added++;
			}
		}
		return new ImportResult(added, updated, unchanged, skipped);
	}

	private static String notesFor(IcsEvent event) {
		String notes = Objects.requireNonNullElse(event.description(), "");
		if (event.url() != null) {
			notes = (notes + "\n\n" + event.url()).strip();
		}
		return notes.isEmpty() ? null : truncate(notes, NOTES_MAX);
	}

	private static String truncate(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

}
