package com.personalsite.notes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One note: typed text, any number of voice recordings (each with a transcript), and a summary of it all. */
@Entity
@Table(name = "notes")
public class Note {

	public static final String UNTITLED = "Untitled note";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 150)
	private String title = UNTITLED;

	@Lob
	private String body;

	@Lob
	private String summary;

	/** "claude" when the AI wrote the summary, "local" for the built-in fallback. */
	private String summarySource;

	private Instant summarizedAt;

	private boolean pinned;

	/** Null means unfiled. Eager so pages can show the folder after the transaction closes (open-in-view is off). */
	@ManyToOne(fetch = FetchType.EAGER)
	private NoteFolder folder;

	private Instant createdAt = Instant.now();

	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@OrderBy("createdAt ASC, id ASC")
	private List<Recording> recordings = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getSummary() {
		return summary;
	}

	public String getSummarySource() {
		return summarySource;
	}

	public Instant getSummarizedAt() {
		return summarizedAt;
	}

	public void setSummary(String summary, String source) {
		this.summary = summary;
		this.summarySource = summary == null ? null : source;
		this.summarizedAt = summary == null ? null : Instant.now();
	}

	public boolean isPinned() {
		return pinned;
	}

	public void setPinned(boolean pinned) {
		this.pinned = pinned;
	}

	public NoteFolder getFolder() {
		return folder;
	}

	public void setFolder(NoteFolder folder) {
		this.folder = folder;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void touch() {
		this.updatedAt = Instant.now();
	}

	public List<Recording> getRecordings() {
		return recordings;
	}

	public void addRecording(Recording recording) {
		recording.setNote(this);
		recordings.add(recording);
	}

	/** Every transcript in recording order, blank ones skipped. */
	public List<String> getTranscripts() {
		return recordings.stream()
			.map(Recording::getTranscript)
			.filter(text -> text != null && !text.isBlank())
			.toList();
	}

	public boolean hasContent() {
		return (body != null && !body.isBlank()) || !getTranscripts().isEmpty();
	}

	public long getTotalSeconds() {
		return recordings.stream().mapToLong(Recording::getDurationSeconds).sum();
	}

	/** Total recorded time, e.g. "4:07" or "1:02:15". */
	public String getTotalLabel() {
		return Recording.clock(getTotalSeconds());
	}

	/** A line or two for the notes list: the body if there is one, otherwise the first transcript. */
	public String getPreview() {
		String source = body != null && !body.isBlank() ? body
				: getTranscripts().stream().findFirst().orElse(summary == null ? "" : summary);
		String flat = source.replaceAll("\\s+", " ").strip();
		return flat.length() > 180 ? flat.substring(0, 180).strip() + "…" : flat;
	}

	/** True when the note's text (title, body, transcripts, summary) contains every word of the query. */
	public boolean matches(String query) {
		StringBuilder haystack = new StringBuilder(title);
		if (body != null) {
			haystack.append(' ').append(body);
		}
		if (summary != null) {
			haystack.append(' ').append(summary);
		}
		getTranscripts().forEach(text -> haystack.append(' ').append(text));
		String text = haystack.toString().toLowerCase();
		for (String word : query.toLowerCase().split("\\s+")) {
			if (!word.isBlank() && !text.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/** The summary split into headings, bullets and paragraphs so the page can style each. */
	public List<SummaryLine> getSummaryLines() {
		return SummaryLine.parse(summary);
	}

}
