package com.personalsite.notes;

/** Transcription or summarizing failed; the message is safe to show on the page. */
public class NoteAiException extends RuntimeException {

	public NoteAiException(String message) {
		super(message);
	}

	public NoteAiException(String message, Throwable cause) {
		super(message, cause);
	}

}
