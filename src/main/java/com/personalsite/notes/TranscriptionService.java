package com.personalsite.notes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Turns a recording into text with a speech-to-text API that speaks the common "/audio/transcriptions" format
 * (OpenAI's Whisper, or Groq's faster/cheaper Whisper with a different base URL).
 * Off unless notes.transcription.api-key is set; the notes page then relies on the browser's live transcript.
 */
@Service
public class TranscriptionService {

	/** Both OpenAI and Groq reject uploads over 25 MB. */
	static final long MAX_BYTES = 25L * 1024 * 1024;

	private final String apiKey;

	private final String model;

	private final RestClient http;

	public TranscriptionService(@Value("${notes.transcription.api-key:}") String apiKey,
			@Value("${notes.transcription.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${notes.transcription.model:whisper-1}") String model) {
		this.apiKey = apiKey == null ? "" : apiKey.strip();
		this.model = model;
		var requests = new JdkClientHttpRequestFactory();
		// Long lectures take a while to transcribe.
		requests.setReadTimeout(Duration.ofMinutes(10));
		this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
	}

	public boolean isEnabled() {
		return !apiKey.isEmpty();
	}

	public String transcribe(Path audio) {
		if (!isEnabled()) {
			throw new NoteAiException("Transcription isn't set up on the server (no API key).");
		}
		try {
			if (Files.size(audio) > MAX_BYTES) {
				throw new NoteAiException("This recording is over 25 MB, which is the transcription service's limit. "
						+ "Record shorter clips (about an hour or less) to transcribe them.");
			}
			MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
			form.add("file", new FileSystemResource(audio));
			form.add("model", model);
			form.add("response_format", "json");
			Map<?, ?> response = http.post()
				.uri("/audio/transcriptions")
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(form)
				.retrieve()
				.body(Map.class);
			Object text = response == null ? null : response.get("text");
			if (text == null) {
				throw new NoteAiException("The transcription service sent back no text.");
			}
			return text.toString().strip();
		}
		catch (RestClientException ex) {
			throw new NoteAiException("The transcription service failed: " + ex.getMessage(), ex);
		}
		catch (java.io.IOException ex) {
			throw new NoteAiException("Couldn't read the recording file.", ex);
		}
	}

}
