package com.personalsite.notes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps recordings as plain files in one folder (notes.storage-dir). On the server that folder sits on the
 * same persistent volume as the database, so audio survives redeploys.
 */
@Component
public class AudioStorage {

	/** File extensions for the formats phones and browsers record in. The extension matters to transcription APIs. */
	private static final Map<String, String> EXTENSIONS = Map.of(
			"audio/webm", "webm",
			"audio/ogg", "ogg",
			"audio/mp4", "m4a",
			"audio/x-m4a", "m4a",
			"audio/aac", "aac",
			"audio/mpeg", "mp3",
			"audio/wav", "wav",
			"audio/x-wav", "wav",
			"video/webm", "webm",
			"video/mp4", "m4a");

	private final Path root;

	public AudioStorage(@Value("${notes.storage-dir:./data/recordings}") String dir) {
		this.root = Path.of(dir).toAbsolutePath().normalize();
	}

	/** True for content types we know how to store and play back. */
	public static boolean isSupported(String contentType) {
		return EXTENSIONS.containsKey(baseType(contentType));
	}

	/**
	 * The audio type of an upload. Some phones send picked files as "application/octet-stream", so fall back to
	 * the file's extension.
	 */
	public static String typeOf(String contentType, String fileName) {
		String type = baseType(contentType);
		if (isSupported(type) || fileName == null) {
			return type;
		}
		String lower = fileName.toLowerCase();
		String extension = lower.substring(lower.lastIndexOf('.') + 1);
		return EXTENSIONS.entrySet()
			.stream()
			.filter(e -> e.getValue().equals(extension) && e.getKey().startsWith("audio/"))
			.map(Map.Entry::getKey)
			.sorted()
			.findFirst()
			.orElse(type);
	}

	/** "audio/webm;codecs=opus" becomes "audio/webm". */
	public static String baseType(String contentType) {
		return contentType == null ? "" : contentType.split(";")[0].strip().toLowerCase();
	}

	/** Saves the stream under a fresh random name and returns that name. */
	public String save(InputStream audio, String contentType) {
		String name = UUID.randomUUID() + "." + EXTENSIONS.get(baseType(contentType));
		try {
			Files.createDirectories(root);
			Files.copy(audio, resolve(name), StandardCopyOption.REPLACE_EXISTING);
			return name;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Couldn't save the recording", ex);
		}
	}

	public Path resolve(String name) {
		Path path = root.resolve(name).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Bad recording name");
		}
		return path;
	}

	public boolean exists(String name) {
		return name != null && Files.isRegularFile(resolve(name));
	}

	public void delete(String name) {
		if (name == null) {
			return;
		}
		try {
			Files.deleteIfExists(resolve(name));
		}
		catch (IOException ex) {
			// A leftover file is harmless; don't fail the delete over it.
		}
	}

}
