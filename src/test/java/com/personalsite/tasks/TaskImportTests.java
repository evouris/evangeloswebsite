package com.personalsite.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:taskimporttests")
@AutoConfigureMockMvc
@WithMockUser
class TaskImportTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TaskRepository tasks;

	@BeforeEach
	void clearTasks() {
		tasks.deleteAll();
	}

	private static String ics(String assignmentTitle, LocalDate assignmentDue) {
		DateTimeFormatter day = DateTimeFormatter.BASIC_ISO_DATE;
		return String.join("\r\n",
				"BEGIN:VCALENDAR",
				"BEGIN:VEVENT",
				"UID:event-assignment-1",
				"DTSTART;VALUE=DATE:" + assignmentDue.format(day),
				"SUMMARY:" + assignmentTitle,
				"URL:https://canvas.example.edu/assignments/1",
				"END:VEVENT",
				"BEGIN:VEVENT",
				"UID:event-assignment-2",
				"DTSTART;VALUE=DATE:" + LocalDate.now().minusDays(10).format(day),
				"SUMMARY:Old homework",
				"END:VEVENT",
				"BEGIN:VEVENT",
				"UID:event-calendar-event-3",
				"DTSTART;VALUE=DATE:" + LocalDate.now().plusDays(2).format(day),
				"SUMMARY:Lecture",
				"END:VEVENT",
				"END:VCALENDAR");
	}

	private void upload(String ics, String expectedMessage) throws Exception {
		mvc.perform(multipart("/tasks/import")
				.file(new MockMultipartFile("file", "canvas.ics", "text/calendar", ics.getBytes(StandardCharsets.UTF_8)))
				.param("assignmentsOnly", "true")
				.param("skipPast", "true")
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("importMessage", expectedMessage));
	}

	@Test
	void importsUpcomingAssignmentsOnly() throws Exception {
		LocalDate due = LocalDate.now().plusDays(5);
		upload(ics("Lab 5", due), "Imported 1 new task, skipped 2.");

		Task lab = tasks.findByExternalId("event-assignment-1").orElseThrow();
		assertEquals("Lab 5", lab.getTitle());
		assertEquals(due, lab.getDueDate());
		assertEquals("https://canvas.example.edu/assignments/1", lab.getNotes());
		assertEquals(1, tasks.count());
	}

	@Test
	void reimportingUpdatesInsteadOfDuplicating() throws Exception {
		LocalDate due = LocalDate.now().plusDays(5);
		upload(ics("Lab 5", due), "Imported 1 new task, skipped 2.");

		// Mark it done and give it a priority; a re-import must keep those.
		Task lab = tasks.findByExternalId("event-assignment-1").orElseThrow();
		lab.setCompleted(true);
		lab.setPriority(Priority.HIGH);
		tasks.save(lab);

		upload(ics("Lab 5", due), "Imported 0 new tasks, 1 already up to date, skipped 2.");
		upload(ics("Lab 5 (extended)", due.plusDays(2)), "Imported 0 new tasks, updated 1, skipped 2.");

		lab = tasks.findByExternalId("event-assignment-1").orElseThrow();
		assertEquals(1, tasks.count());
		assertEquals("Lab 5 (extended)", lab.getTitle());
		assertEquals(due.plusDays(2), lab.getDueDate());
		assertEquals(true, lab.isCompleted());
		assertEquals(Priority.HIGH, lab.getPriority());
	}

	@Test
	void rejectsNonCalendarFiles() throws Exception {
		mvc.perform(multipart("/tasks/import")
				.file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("importError", "That doesn't look like a calendar (.ics) file."));
		assertEquals(0, tasks.count());
	}

}
