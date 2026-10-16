package com.personalsite.notes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The Notes tab: notes in folders, voice recordings (from a phone or computer mic) with transcripts, and summaries.
 * Background saves from notes.js send X-Requested-With and get JSON back; plain form posts get redirects.
 */
@Controller
@RequestMapping("/notes")
public class NoteController {

	private static final String FETCH_HEADER = "X-Requested-With";

	private final NoteRepository notes;

	private final NoteFolderRepository folders;

	private final RecordingRepository recordings;

	private final AudioStorage storage;

	private final TranscriptionService transcription;

	private final SummaryService summaries;

	public NoteController(NoteRepository notes, NoteFolderRepository folders, RecordingRepository recordings,
			AudioStorage storage, TranscriptionService transcription, SummaryService summaries) {
		this.notes = notes;
		this.folders = folders;
		this.recordings = recordings;
		this.storage = storage;
		this.transcription = transcription;
		this.summaries = summaries;
	}

	// ----- Notes list -----

	/** @param folder a folder id, "unfiled" or "pinned"; empty for every note */
	@GetMapping
	public String list(@RequestParam(required = false) String folder, @RequestParam(required = false) String q,
			Model model) {
		List<Note> all = notes.findAllByOrderByPinnedDescUpdatedAtDesc();
		List<NoteFolder> folderList = folders.findAllByOrderByNameAsc();
		Map<Long, Long> counts = new LinkedHashMap<>();
		folderList.forEach(f -> counts.put(f.getId(), all.stream().filter(n -> isIn(n, f)).count()));

		String filter = folder == null ? "" : folder;
		NoteFolder current = parseId(filter).flatMap(folders::findById).orElse(null);
		List<Note> shown = all.stream().filter(n -> switch (filter) {
			case "" -> true;
			case "unfiled" -> n.getFolder() == null;
			case "pinned" -> n.isPinned();
			default -> current != null && isIn(n, current);
		}).filter(n -> q == null || q.isBlank() || n.matches(q.strip())).toList();

		model.addAttribute("notes", shown);
		model.addAttribute("folders", folderList);
		model.addAttribute("counts", counts);
		model.addAttribute("total", all.size());
		model.addAttribute("unfiledCount", all.stream().filter(n -> n.getFolder() == null).count());
		model.addAttribute("pinnedCount", all.stream().filter(Note::isPinned).count());
		model.addAttribute("filter", filter);
		model.addAttribute("currentFolder", current);
		model.addAttribute("q", q == null ? "" : q.strip());
		return "notes/list";
	}

	/** New note, optionally straight into a folder. {@code record=true} opens it with the recorder ready. */
	@PostMapping
	public String create(@RequestParam(required = false) String title, @RequestParam(required = false) Long folderId,
			@RequestParam(defaultValue = "false") boolean record) {
		Note note = new Note();
		note.setTitle(cleanTitle(title));
		if (folderId != null) {
			folders.findById(folderId).ifPresent(note::setFolder);
		}
		notes.save(note);
		return "redirect:/notes/" + note.getId() + (record ? "?record" : "");
	}

	// ----- One note -----

	@GetMapping("/{id}")
	public String show(@PathVariable Long id, Model model) {
		return notes.findById(id).map(note -> {
			model.addAttribute("note", note);
			model.addAttribute("folders", folders.findAllByOrderByNameAsc());
			model.addAttribute("transcriptionEnabled", transcription.isEnabled());
			model.addAttribute("aiSummaries", summaries.isAiEnabled());
			return "notes/note";
		}).orElse("redirect:/notes");
	}

	/** Saves title, text and folder. notes.js calls this as you type. */
	@PostMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @RequestParam(required = false) String title,
			@RequestParam(required = false) String body, @RequestParam(required = false) String folderId,
			@RequestHeader(name = FETCH_HEADER, required = false) String requestedWith) {
		Optional<Note> found = notes.findById(id);
		if (found.isEmpty()) {
			return requestedWith == null ? redirect("/notes") : ResponseEntity.notFound().build();
		}
		Note note = found.get();
		if (title != null) {
			note.setTitle(cleanTitle(title));
		}
		if (body != null) {
			note.setBody(body.isBlank() ? null : body);
		}
		if (folderId != null) {
			note.setFolder(parseId(folderId).flatMap(folders::findById).orElse(null));
		}
		note.touch();
		notes.save(note);
		if (requestedWith == null) {
			return redirect("/notes/" + id);
		}
		return ResponseEntity.ok(Map.of("saved", true, "updatedAt", note.getUpdatedAt().toString()));
	}

	@PostMapping("/{id}/pin")
	public String pin(@PathVariable Long id, @RequestParam(required = false) String back) {
		notes.findById(id).ifPresent(note -> {
			note.setPinned(!note.isPinned());
			notes.save(note);
		});
		return "redirect:" + safeBack(back, "/notes/" + id);
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		notes.findById(id).ifPresent(note -> {
			note.getRecordings().forEach(r -> storage.delete(r.getFileName()));
			notes.delete(note);
			redirect.addFlashAttribute("message", "Deleted \"" + note.getTitle() + "\".");
		});
		return "redirect:/notes";
	}

	/** The whole note as a Markdown file: text, summary and transcripts. */
	@GetMapping("/{id}/export")
	public ResponseEntity<String> export(@PathVariable Long id) {
		return notes.findById(id).map(note -> {
			StringBuilder md = new StringBuilder("# ").append(note.getTitle()).append("\n\n");
			md.append("_").append(DateTimeFormatter.ISO_LOCAL_DATE.format(note.getCreatedAt().atOffset(ZoneOffset.UTC)));
			if (note.getFolder() != null) {
				md.append(" · ").append(note.getFolder().getName());
			}
			md.append("_\n\n");
			if (note.getSummary() != null) {
				md.append("## Summary\n\n").append(note.getSummary().strip()).append("\n\n");
			}
			if (note.getBody() != null) {
				md.append("## Notes\n\n").append(note.getBody().strip()).append("\n\n");
			}
			List<Recording> withText = note.getRecordings().stream().filter(r -> r.getTranscript() != null).toList();
			for (int i = 0; i < withText.size(); i++) {
				Recording r = withText.get(i);
				md.append("## Recording ").append(i + 1).append(" (").append(r.getDurationLabel()).append(")\n\n")
					.append(r.getTranscript()).append("\n\n");
			}
			String fileName = note.getTitle().replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
			return ResponseEntity.ok()
				.contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
					.filename((fileName.isEmpty() ? "note" : fileName) + ".md", StandardCharsets.UTF_8)
					.build()
					.toString())
				.body(md.toString());
		}).orElse(ResponseEntity.notFound().build());
	}

	// ----- Summary -----

	@PostMapping("/{id}/summary")
	public ResponseEntity<?> summarize(@PathVariable Long id,
			@RequestHeader(name = FETCH_HEADER, required = false) String requestedWith, RedirectAttributes redirect) {
		Optional<Note> found = notes.findById(id);
		if (found.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		Note note = found.get();
		try {
			SummaryService.Summary summary = summaries.summarize(note);
			note.setSummary(summary.text(), summary.source());
			notes.save(note);
			return requestedWith == null ? redirect("/notes/" + id + "#summary")
					: ResponseEntity.ok(Map.of("summary", summary.text(), "source", summary.source()));
		}
		catch (NoteAiException ex) {
			return failure(ex, requestedWith, redirect, "/notes/" + id + "#summary");
		}
	}

	@PostMapping("/{id}/summary/delete")
	public String clearSummary(@PathVariable Long id) {
		notes.findById(id).ifPresent(note -> {
			note.setSummary(null, null);
			notes.save(note);
		});
		return "redirect:/notes/" + id;
	}

	// ----- Recordings -----

	/**
	 * Upload from the recorder. {@code transcript} is what the browser heard live, if it could listen;
	 * when the server has a transcription key the audio is transcribed here too (unless {@code transcribe=false}).
	 */
	@PostMapping("/{id}/recordings")
	public ResponseEntity<?> upload(@PathVariable Long id, @RequestParam("audio") MultipartFile audio,
			@RequestParam(defaultValue = "0") double duration, @RequestParam(required = false) String transcript,
			@RequestParam(defaultValue = "true") boolean transcribe,
			@RequestHeader(name = FETCH_HEADER, required = false) String requestedWith, RedirectAttributes redirect) {
		Optional<Note> found = notes.findById(id);
		if (found.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		String contentType = AudioStorage.typeOf(audio.getContentType(), audio.getOriginalFilename());
		if (audio.isEmpty() || !AudioStorage.isSupported(contentType)) {
			return failure(new NoteAiException("That doesn't look like an audio recording."), requestedWith, redirect,
					"/notes/" + id);
		}
		String fileName;
		try (InputStream in = audio.getInputStream()) {
			fileName = storage.save(in, contentType);
		}
		catch (IOException ex) {
			return failure(new NoteAiException("Couldn't save the recording.", ex), requestedWith, redirect,
					"/notes/" + id);
		}

		Note note = found.get();
		Recording recording = new Recording(fileName, contentType, audio.getSize(),
				Math.max(0, Math.round(duration)));
		recording.setTranscript(transcript, "live");
		note.addRecording(recording);
		note.touch();
		if (Note.UNTITLED.equals(note.getTitle())) {
			note.setTitle("Recording · " + DateTimeFormatter.ofPattern("MMM d").format(
					recording.getCreatedAt().atOffset(ZoneOffset.UTC)));
		}
		notes.save(note);

		String warning = null;
		if (transcribe && transcription.isEnabled()) {
			Recording saved = note.getRecordings().getLast();
			try {
				saved.setTranscript(transcription.transcribe(storage.resolve(fileName)), "ai");
				notes.save(note);
			}
			catch (NoteAiException ex) {
				warning = "Saved the recording, but transcribing it failed: " + ex.getMessage();
			}
		}
		if (requestedWith == null) {
			if (warning != null) {
				redirect.addFlashAttribute("error", warning);
			}
			return redirect("/notes/" + id + "#recordings");
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("saved", true);
		body.put("warning", warning);
		return ResponseEntity.ok(body);
	}

	/** Streams a recording. Spring answers Range requests itself, which iPhones need to play audio. */
	@GetMapping("/{id}/recordings/{recordingId}/audio")
	public ResponseEntity<Resource> audio(@PathVariable Long id, @PathVariable Long recordingId,
			@RequestParam(defaultValue = "false") boolean download) {
		return findRecording(id, recordingId).filter(r -> storage.exists(r.getFileName())).map(r -> {
			var response = ResponseEntity.ok().contentType(MediaType.parseMediaType(r.getContentType()));
			if (download) {
				response.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(r.getFileName()).build().toString());
			}
			return response.body((Resource) new FileSystemResource(storage.resolve(r.getFileName())));
		}).orElse(ResponseEntity.notFound().build());
	}

	/** Save a hand-edited transcript. */
	@PostMapping("/{id}/recordings/{recordingId}")
	public ResponseEntity<?> updateTranscript(@PathVariable Long id, @PathVariable Long recordingId,
			@RequestParam(required = false) String transcript,
			@RequestHeader(name = FETCH_HEADER, required = false) String requestedWith) {
		findRecording(id, recordingId).ifPresent(r -> {
			r.setTranscript(transcript, "edited");
			r.getNote().touch();
			notes.save(r.getNote());
		});
		return requestedWith == null ? redirect("/notes/" + id + "#recording-" + recordingId)
				: ResponseEntity.ok(Map.of("saved", true));
	}

	@PostMapping("/{id}/recordings/{recordingId}/transcribe")
	public ResponseEntity<?> transcribe(@PathVariable Long id, @PathVariable Long recordingId,
			@RequestHeader(name = FETCH_HEADER, required = false) String requestedWith, RedirectAttributes redirect) {
		Optional<Recording> found = findRecording(id, recordingId);
		if (found.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		Recording recording = found.get();
		try {
			if (!storage.exists(recording.getFileName())) {
				throw new NoteAiException("The audio file for this recording is missing.");
			}
			recording.setTranscript(transcription.transcribe(storage.resolve(recording.getFileName())), "ai");
			recording.getNote().touch();
			notes.save(recording.getNote());
			return requestedWith == null ? redirect("/notes/" + id + "#recording-" + recordingId)
					: ResponseEntity.ok(Map.of("transcript", recording.getTranscript() == null ? "" : recording.getTranscript()));
		}
		catch (NoteAiException ex) {
			return failure(ex, requestedWith, redirect, "/notes/" + id + "#recording-" + recordingId);
		}
	}

	@PostMapping("/{id}/recordings/{recordingId}/delete")
	public String deleteRecording(@PathVariable Long id, @PathVariable Long recordingId) {
		findRecording(id, recordingId).ifPresent(r -> {
			Note note = r.getNote();
			storage.delete(r.getFileName());
			note.getRecordings().removeIf(each -> each.getId().equals(recordingId));
			note.touch();
			notes.save(note);
		});
		return "redirect:/notes/" + id + "#recordings";
	}

	// ----- Folders -----

	@PostMapping("/folders")
	public String createFolder(@RequestParam String name, RedirectAttributes redirect) {
		String clean = cleanFolderName(name);
		if (clean.isEmpty()) {
			return "redirect:/notes";
		}
		NoteFolder folder = folders.findByNameIgnoreCase(clean).orElseGet(() -> folders.save(new NoteFolder(clean)));
		return "redirect:/notes?folder=" + folder.getId();
	}

	@PostMapping("/folders/{folderId}")
	public String renameFolder(@PathVariable Long folderId, @RequestParam String name) {
		String clean = cleanFolderName(name);
		folders.findById(folderId).ifPresent(folder -> {
			if (!clean.isEmpty()) {
				folder.setName(clean);
				folders.save(folder);
			}
		});
		return "redirect:/notes?folder=" + folderId;
	}

	/** Deleting a folder keeps its notes; they just become unfiled. */
	@PostMapping("/folders/{folderId}/delete")
	public String deleteFolder(@PathVariable Long folderId, RedirectAttributes redirect) {
		folders.findById(folderId).ifPresent(folder -> {
			notes.unfileAll(folder);
			folders.delete(folder);
			redirect.addFlashAttribute("message", "Deleted folder \"" + folder.getName() + "\". Its notes are now unfiled.");
		});
		return "redirect:/notes";
	}

	// ----- Helpers -----

	private Optional<Recording> findRecording(Long noteId, Long recordingId) {
		return recordings.findById(recordingId).filter(r -> r.getNote().getId().equals(noteId));
	}

	private static boolean isIn(Note note, NoteFolder folder) {
		return note.getFolder() != null && note.getFolder().getId().equals(folder.getId());
	}

	private static ResponseEntity<?> failure(NoteAiException ex, String requestedWith, RedirectAttributes redirect,
			String back) {
		if (requestedWith == null) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return redirect(back);
		}
		return ResponseEntity.unprocessableContent().body(Map.of("error", ex.getMessage()));
	}

	private static ResponseEntity<?> redirect(String path) {
		return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
	}

	/** Only same-site paths, so a crafted form can't bounce you somewhere else. */
	private static String safeBack(String back, String fallback) {
		return back != null && back.startsWith("/notes") && !back.startsWith("//") ? back : fallback;
	}

	private static Optional<Long> parseId(String value) {
		try {
			return value == null || value.isBlank() ? Optional.empty() : Optional.of(Long.valueOf(value.strip()));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	private static String cleanTitle(String title) {
		String clean = title == null ? "" : title.strip();
		return clean.isEmpty() ? Note.UNTITLED : clean.substring(0, Math.min(150, clean.length()));
	}

	private static String cleanFolderName(String name) {
		String clean = name == null ? "" : name.strip();
		return clean.substring(0, Math.min(60, clean.length()));
	}

}
