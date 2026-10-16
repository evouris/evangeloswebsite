package com.personalsite.notes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A no-API fallback: picks the sentences that use the note's most frequent words, plus any sentence that sounds
 * like a to-do. Not as good as the AI summary, but it means the button always does something.
 */
final class LocalSummarizer {

	private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+|\\R+");

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}']+");

	private static final Pattern TODO = Pattern.compile(
			"\\b(need to|needs to|have to|has to|must|should|remember to|don't forget|todo|to-do|deadline|due|"
					+ "follow up|homework|assignment|exam|quiz|test on|by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|next week))\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Set<String> STOP_WORDS = Set.of("a", "about", "after", "all", "also", "am", "an", "and", "any",
			"are", "as", "at", "be", "because", "been", "but", "by", "can", "could", "did", "do", "does", "doing", "don't",
			"for", "from", "get", "go", "going", "got", "had", "has", "have", "he", "her", "here", "him", "his", "how", "i",
			"i'm", "if", "in", "into", "is", "it", "it's", "its", "just", "know", "like", "me", "more", "my", "no", "not",
			"now", "of", "okay", "ok", "on", "one", "or", "our", "out", "really", "right", "say", "so", "some", "that",
			"that's", "the", "their", "them", "then", "there", "these", "they", "thing", "things", "think", "this",
			"to", "um", "uh", "up", "us", "very", "was", "we", "well", "were", "what", "when", "where", "which", "who",
			"will", "with", "would", "yeah", "you", "your");

	private LocalSummarizer() {
	}

	static String summarize(String text) {
		List<String> sentences = SENTENCE_END.splitAsStream(text)
			.map(String::strip)
			.filter(s -> WORD.matcher(s).results().count() >= 4)
			.toList();
		if (sentences.isEmpty()) {
			return "Summary\n" + text.strip();
		}

		Map<String, Integer> frequency = new HashMap<>();
		sentences.forEach(s -> words(s).forEach(w -> frequency.merge(w, 1, Integer::sum)));

		int keep = Math.clamp(Math.round(sentences.size() * 0.2f), 1, 7);
		double[] scores = sentences.stream()
			.mapToDouble(s -> {
				List<String> words = words(s);
				return words.isEmpty() ? 0 : words.stream().mapToInt(frequency::get).sum() / Math.sqrt(words.size());
			})
			.toArray();
		List<Integer> best = IntStream.range(0, sentences.size())
			.boxed()
			.sorted(Comparator.comparingDouble((Integer i) -> scores[i]).reversed())
			.limit(keep)
			.sorted()
			.toList();

		List<String> topWords = frequency.entrySet()
			.stream()
			.filter(e -> e.getValue() > 1)
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(6)
			.map(Map.Entry::getKey)
			.toList();

		Set<String> todos = new LinkedHashSet<>();
		sentences.stream().filter(s -> TODO.matcher(s).find()).limit(8).forEach(todos::add);

		StringBuilder out = new StringBuilder("Summary\n");
		out.append(sentences.get(best.getFirst())).append('\n');
		if (!topWords.isEmpty()) {
			out.append("Main topics: ").append(String.join(", ", topWords)).append('\n');
		}
		List<String> points = new ArrayList<>(best.subList(1, best.size()).stream().map(sentences::get).toList());
		if (!points.isEmpty()) {
			out.append("\nKey points\n");
			points.forEach(p -> out.append("- ").append(p).append('\n'));
		}
		if (!todos.isEmpty()) {
			out.append("\nAction items\n");
			todos.forEach(t -> out.append("- ").append(t).append('\n'));
		}
		return out.toString().strip();
	}

	private static List<String> words(String sentence) {
		return WORD.matcher(sentence.toLowerCase())
			.results()
			.map(m -> m.group())
			.filter(w -> w.length() > 2 && !STOP_WORDS.contains(w))
			.toList();
	}

}
