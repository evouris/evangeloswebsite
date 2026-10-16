package com.personalsite.notes;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One voice recording attached to a note. The audio itself lives on disk (see {@link AudioStorage}). */
@Entity
@Table(name = "note_recordings")
public class Recording {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	private Note note;

	/** Name of the audio file inside the storage folder. */
	private String fileName;

	private String contentType;

	private long sizeBytes;

	private long durationSeconds;

	@Lob
	private String transcript;

	/** "live" (the browser's speech recognition while recording), "ai" (transcription service) or "edited". */
	private String transcriptSource;

	private Instant createdAt = Instant.now();

	protected Recording() {
	}

	public Recording(String fileName, String contentType, long sizeBytes, long durationSeconds) {
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.durationSeconds = durationSeconds;
	}

	public Long getId() {
		return id;
	}

	public Note getNote() {
		return note;
	}

	void setNote(Note note) {
		this.note = note;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public long getDurationSeconds() {
		return durationSeconds;
	}

	public String getTranscript() {
		return transcript;
	}

	public String getTranscriptSource() {
		return transcriptSource;
	}

	public void setTranscript(String transcript, String source) {
		this.transcript = transcript == null || transcript.isBlank() ? null : transcript.strip();
		this.transcriptSource = this.transcript == null ? null : source;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** e.g. "4:07" or "1:02:15". */
	public String getDurationLabel() {
		return clock(durationSeconds);
	}

	static String clock(long seconds) {
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		return h > 0 ? "%d:%02d:%02d".formatted(h, m, s) : "%d:%02d".formatted(m, s);
	}

	/** e.g. "2.4 MB". */
	public String getSizeLabel() {
		if (sizeBytes < 1024 * 1024) {
			return Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB";
		}
		return "%.1f MB".formatted(sizeBytes / (1024.0 * 1024.0));
	}

}
