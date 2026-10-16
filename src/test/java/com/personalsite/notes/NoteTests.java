package com.personalsite.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:notetests", "notes.storage-dir=target/test-recordings",
		"notes.anthropic.api-key=", "notes.transcription.api-key=" })
@AutoConfigureMockMvc
@WithMockUser
class NoteTests {

	private static final byte[] FAKE_AUDIO = "not really opus but bytes are bytes".getBytes(StandardCharsets.UTF_8);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private NoteRepository notes;

	@Autowired
	private NoteFolderRepository folders;

	@Autowired
	private AudioStorage storage;

	@BeforeEach
	void clean() {
		notes.deleteAll();
		folders.deleteAll();
	}

	@Test
	void creatingANoteOpensItInTheEditor() throws Exception {
		mvc.perform(post("/notes").param("title", "Lecture 4").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("/notes/*"));

		Note note = notes.findAll().getFirst();
		assertThat(note.getTitle()).isEqualTo("Lecture 4");

		mvc.perform(get("/notes/" + note.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-recorder")))
			.andExpect(content().string(containsString("Lecture 4")));
		mvc.perform(get("/notes")).andExpect(status().isOk()).andExpect(content().string(containsString("Lecture 4")));
	}

	@Test
	void backgroundSaveUpdatesTextAndFolder() throws Exception {
		NoteFolder folder = folders.save(new NoteFolder("CS 101"));
		Note note = notes.save(new Note());

		mvc.perform(post("/notes/{id}", note.getId())
			.param("title", "Recursion")
			.param("body", "Base case first.")
			.param("folderId", folder.getId().toString())
			.header("X-Requested-With", "fetch")
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.saved").value(true));

		Note saved = notes.findById(note.getId()).orElseThrow();
		assertThat(saved.getBody()).isEqualTo("Base case first.");
		assertThat(saved.getFolder().getName()).isEqualTo("CS 101");

		mvc.perform(get("/notes").param("folder", folder.getId().toString()))
			.andExpect(content().string(containsString("Recursion")));
		mvc.perform(get("/notes").param("folder", "unfiled"))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Recursion"))));
		mvc.perform(get("/notes").param("q", "base case"))
			.andExpect(content().string(containsString("Recursion")));
	}

	@Test
	void uploadedRecordingIsStoredWithItsLiveTranscriptAndCanBePlayedBack() throws Exception {
		Note note = notes.save(new Note());
		var audio = new MockMultipartFile("audio", "recording.webm", "audio/webm;codecs=opus", FAKE_AUDIO);

		mvc.perform(multipart("/notes/{id}/recordings", note.getId())
			.file(audio)
			.param("duration", "65")
			.param("transcript", "today we cover recursion")
			.header("X-Requested-With", "fetch")
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.saved").value(true));

		Note saved = notes.findById(note.getId()).orElseThrow();
		Recording recording = saved.getRecordings().getFirst();
		assertThat(recording.getTranscript()).isEqualTo("today we cover recursion");
		assertThat(recording.getTranscriptSource()).isEqualTo("live");
		assertThat(recording.getDurationLabel()).isEqualTo("1:05");
		assertThat(recording.getContentType()).isEqualTo("audio/webm");
		assertThat(storage.exists(recording.getFileName())).isTrue();
		assertThat(saved.getTitle()).startsWith("Recording");

		String url = "/notes/" + note.getId() + "/recordings/" + recording.getId() + "/audio";
		mvc.perform(get(url))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "audio/webm"))
			.andExpect(content().bytes(FAKE_AUDIO));
		// Phones seek with Range requests.
		mvc.perform(get(url).header("Range", "bytes=0-2"))
			.andExpect(status().isPartialContent())
			.andExpect(content().bytes(new byte[] { 'n', 'o', 't' }));

		mvc.perform(post("/notes/{id}/delete", note.getId()).with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(storage.exists(recording.getFileName())).isFalse();
	}

	@Test
	void nonAudioUploadsAreRejected() throws Exception {
		Note note = notes.save(new Note());
		var file = new MockMultipartFile("audio", "evil.html", "text/html", "<script>".getBytes());

		mvc.perform(multipart("/notes/{id}/recordings", note.getId())
			.file(file)
			.header("X-Requested-With", "fetch")
			.with(csrf()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.error").exists());
		assertThat(notes.findById(note.getId()).orElseThrow().getRecordings()).isEmpty();
	}

	@Test
	void withoutAnApiKeyTheBuiltInSummarizerStillWritesASummary() throws Exception {
		Note note = new Note();
		note.setBody("Recursion is when a function calls itself. Every recursive function needs a base case. "
				+ "Without a base case the recursion never stops and the stack overflows. "
				+ "We need to finish problem set three by Friday. Recursion and the base case will be on the exam.");
		notes.save(note);

		mvc.perform(post("/notes/{id}/summary", note.getId()).header("X-Requested-With", "fetch").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("local"));

		Note saved = notes.findById(note.getId()).orElseThrow();
		assertThat(saved.getSummary()).startsWith("Summary").contains("Action items");
		assertThat(saved.getSummaryLines()).anyMatch(SummaryLine::isHeading).anyMatch(SummaryLine::isBullet);
	}

	@Test
	void summarizingAnEmptyNoteExplainsWhy() throws Exception {
		Note note = notes.save(new Note());
		mvc.perform(post("/notes/{id}/summary", note.getId()).header("X-Requested-With", "fetch").with(csrf()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.error", containsString("Nothing to summarize")));
	}

	@Test
	void deletingAFolderKeepsItsNotes() throws Exception {
		mvc.perform(post("/notes/folders").param("name", "Biology").with(csrf()))
			.andExpect(status().is3xxRedirection());
		NoteFolder folder = folders.findByNameIgnoreCase("biology").orElseThrow();
		Note note = new Note();
		note.setFolder(folder);
		notes.save(note);

		mvc.perform(post("/notes/folders/{id}/delete", folder.getId()).with(csrf()))
			.andExpect(status().is3xxRedirection());

		assertThat(folders.count()).isZero();
		assertThat(notes.findById(note.getId()).orElseThrow().getFolder()).isNull();
	}

	@Test
	void exportIncludesSummaryAndTranscripts() throws Exception {
		Note note = new Note();
		note.setTitle("Meeting");
		note.setBody("Agenda");
		note.setSummary("Summary\nWe met.", "local");
		notes.save(note);

		mvc.perform(get("/notes/{id}/export", note.getId()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("Meeting.md")))
			.andExpect(content().string(containsString("# Meeting")))
			.andExpect(content().string(containsString("We met.")));
	}

}
