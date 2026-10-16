package com.personalsite.notes;

import java.util.ArrayList;
import java.util.List;

/** One line of a stored summary: a section heading, a bullet, or plain text. */
public record SummaryLine(Kind kind, String text) {

	public enum Kind {
		HEADING, BULLET, TEXT
	}

	public boolean isHeading() {
		return kind == Kind.HEADING;
	}

	public boolean isBullet() {
		return kind == Kind.BULLET;
	}

	static List<SummaryLine> parse(String summary) {
		List<SummaryLine> lines = new ArrayList<>();
		if (summary == null) {
			return lines;
		}
		for (String raw : summary.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
				lines.add(new SummaryLine(Kind.BULLET, line.substring(2).strip()));
			}
			else if (line.startsWith("#")) {
				lines.add(new SummaryLine(Kind.HEADING, line.replaceFirst("^#+\\s*", "")));
			}
			else if (line.length() <= 40 && line.endsWith(":") || isKnownHeading(line)) {
				lines.add(new SummaryLine(Kind.HEADING, line.replaceFirst(":$", "")));
			}
			else {
				lines.add(new SummaryLine(Kind.TEXT, line));
			}
		}
		return lines;
	}

	private static boolean isKnownHeading(String line) {
		String lower = line.toLowerCase().replaceFirst(":$", "");
		return lower.equals("summary") || lower.equals("key points") || lower.equals("action items")
				|| lower.equals("questions") || lower.equals("open questions");
	}

}
