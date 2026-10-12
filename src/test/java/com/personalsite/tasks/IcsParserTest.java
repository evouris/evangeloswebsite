package com.personalsite.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.personalsite.tasks.IcsParser.IcsEvent;

class IcsParserTest {

	private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

	// Shaped like a real Canvas export: CRLF line endings, folded lines, escaped text, UTC due times.
	private static final String CANVAS = String.join("\r\n",
			"BEGIN:VCALENDAR",
			"VERSION:2.0",
			"PRODID:-//Instructure//Canvas//EN",
			"BEGIN:VEVENT",
			"UID:event-assignment-101",
			"DTSTART:20261016T045900Z",
			"DTEND:20261016T045900Z",
			"SUMMARY:Lab 5 [COMS 2270 (Fall 2026)]",
			"DESCRIPTION:Read chapter 4\\, then answer\\nthe questions\\; show work.",
			"URL;VALUE=URI:https://canvas.iastate.edu/courses/1/assignments/101",
			"BEGIN:VALARM",
			"DESCRIPTION:Reminder text that should be ignored",
			"END:VALARM",
			"END:VEVENT",
			"BEGIN:VEVENT",
			"UID:event-calendar-event-202",
			"DTSTART;VALUE=DATE:20261020",
			"SUMMARY:Midterm review session with a very long title that Canvas folds onto",
			"  a second line",
			"END:VEVENT",
			"BEGIN:VEVENT",
			"UID:event-assignment-303",
			"DTSTART;TZID=America/New_York:20261021T003000",
			"SUMMARY:Quiz 2",
			"END:VEVENT",
			"END:VCALENDAR",
			"");

	@Test
	void readsCanvasAssignment() {
		IcsEvent lab = IcsParser.parse(CANVAS, CENTRAL).get(0);
		assertEquals("event-assignment-101", lab.uid());
		assertEquals("Lab 5 [COMS 2270 (Fall 2026)]", lab.summary());
		assertEquals("Read chapter 4, then answer\nthe questions; show work.", lab.description());
		assertEquals("https://canvas.iastate.edu/courses/1/assignments/101", lab.url());
		assertTrue(lab.isCanvasAssignment());
	}

	@Test
	void convertsUtcDueTimeToLocalDate() {
		// 04:59 UTC on the 16th is 11:59pm Central on the 15th.
		assertEquals(LocalDate.of(2026, 10, 15), IcsParser.parse(CANVAS, CENTRAL).get(0).date());
	}

	@Test
	void handlesAllDayEventsFoldedLinesAndNonAssignments() {
		IcsEvent review = IcsParser.parse(CANVAS, CENTRAL).get(1);
		assertEquals(LocalDate.of(2026, 10, 20), review.date());
		assertEquals("Midterm review session with a very long title that Canvas folds onto a second line", review.summary());
		assertFalse(review.isCanvasAssignment());
	}

	@Test
	void convertsTzidTimes() {
		// 00:30 New York on the 21st is 11:30pm Central on the 20th.
		assertEquals(LocalDate.of(2026, 10, 20), IcsParser.parse(CANVAS, CENTRAL).get(2).date());
	}

	@Test
	void ignoresAlarmProperties() {
		List<IcsEvent> events = IcsParser.parse(CANVAS, CENTRAL);
		assertEquals(3, events.size());
		assertFalse(events.get(0).description().contains("Reminder"));
	}

	@Test
	void toleratesByteOrderMarkAndBareNewlines() {
		String ics = (char) 0xFEFF + "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Essay\nDTSTART:bogus\nEND:VEVENT\nEND:VCALENDAR\n";
		IcsEvent essay = IcsParser.parse(ics, CENTRAL).get(0);
		assertEquals("Essay", essay.summary());
		assertNull(essay.date());
		assertNull(essay.uid());
	}

	@Test
	void rejectsFilesThatAreNotCalendars() {
		assertThrows(IllegalArgumentException.class, () -> IcsParser.parse("hello", CENTRAL));
	}

}
