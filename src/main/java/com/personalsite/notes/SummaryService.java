package com.personalsite.notes;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

/**
 * Writes a note's summary. With notes.anthropic.api-key set it asks Claude; without one (or if Claude can't be
 * reached) it falls back to {@link LocalSummarizer} so the button always works.
 */
@Service
public class SummaryService {

	/** Lets the API retry on another model if the main one declines a request. */
	private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

	private static final String SYSTEM_PROMPT = """
			You summarize one person's own notes: typed notes and transcripts of things they recorded on their phone, \
			such as lectures, meetings, classes and voice memos. Transcripts come from speech recognition, so expect \
			filler words and misheard words; work out what was meant.

			Reply in plain text (no Markdown symbols like ** or #) using exactly these sections, each heading on its own line:
			Summary
			Two to four sentences on what this is about and the main takeaway.

			Key points
			- One bullet per important idea, fact, definition, decision or example, in the order they came up.

			Action items
			- Only include this section if there are tasks, deadlines, assignments, exams or follow-ups. Put dates first when given.

			Write for the person who took the notes, in the same language the notes are in. Keep it tight: \
			a short recording needs only a few bullets; a long lecture can have more.""";

	public record Summary(String text, String source) {
	}

	private final AnthropicClient client;

	private final String model;

	public SummaryService(@Value("${notes.anthropic.api-key:}") String apiKey,
			@Value("${notes.anthropic.model:claude-opus-5-5}") String model) {
		this.client = apiKey == null || apiKey.isBlank() ? null
				: AnthropicOkHttpClient.builder().apiKey(apiKey.strip()).build();
		this.model = model;
	}

	public boolean isAiEnabled() {
		return client != null;
	}

	public Summary summarize(Note note) {
		if (!note.hasContent()) {
			throw new NoteAiException("Nothing to summarize yet. Type some notes or add a recording with a transcript.");
		}
		if (client == null) {
			return new Summary(LocalSummarizer.summarize(plainText(note)), "local");
		}
		return new Summary(askClaude(note), "claude");
	}

	private String askClaude(Note note) {
		MessageCreateParams params = MessageCreateParams.builder()
			.model(model)
			.maxTokens(16000L)
			.system(SYSTEM_PROMPT)
			.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
			.addBeta(FALLBACK_BETA)
			.fallbacksDefault()
			.addUserMessage(promptFor(note))
			.build();
		try {
			BetaMessage response = client.beta().messages().create(params);
			if (response.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false)) {
				throw new NoteAiException("Claude declined to summarize this note.");
			}
			String text = response.content()
				.stream()
				.map(BetaContentBlock::text)
				.flatMap(java.util.Optional::stream)
				.map(block -> block.text())
				.collect(Collectors.joining("\n"))
				.strip();
			if (text.isEmpty()) {
				throw new NoteAiException("Claude sent back an empty summary. Try again.");
			}
			return text;
		}
		catch (UnauthorizedException ex) {
			throw new NoteAiException("The Anthropic API key was rejected. Check ANTHROPIC_API_KEY on the server.", ex);
		}
		catch (RateLimitException ex) {
			throw new NoteAiException("Claude is rate limited right now. Try again in a minute.", ex);
		}
		catch (AnthropicServiceException ex) {
			throw new NoteAiException("Claude returned an error (" + ex.statusCode() + "). Try again.", ex);
		}
		catch (AnthropicException ex) {
			throw new NoteAiException("Couldn't reach Claude. Check the server's internet connection.", ex);
		}
	}

	private static String promptFor(Note note) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("<title>").append(note.getTitle()).append("</title>\n");
		if (note.getFolder() != null) {
			prompt.append("<folder>").append(note.getFolder().getName()).append("</folder>\n");
		}
		if (note.getBody() != null && !note.getBody().isBlank()) {
			prompt.append("<typed_notes>\n").append(note.getBody().strip()).append("\n</typed_notes>\n");
		}
		List<String> transcripts = note.getTranscripts();
		for (int i = 0; i < transcripts.size(); i++) {
			prompt.append("<recording_transcript number=\"").append(i + 1).append("\">\n")
				.append(transcripts.get(i))
				.append("\n</recording_transcript>\n");
		}
		prompt.append("\nSummarize this note.");
		return prompt.toString();
	}

	private static String plainText(Note note) {
		StringBuilder text = new StringBuilder();
		if (note.getBody() != null) {
			text.append(note.getBody()).append('\n');
		}
		note.getTranscripts().forEach(t -> text.append(t).append('\n'));
		return text.toString();
	}

}
