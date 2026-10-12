package com.personalsite.tasks;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the events out of an iCalendar (.ics) file, such as the calendar feed Canvas exports.
 * Only handles what we need for tasks: UID, SUMMARY, DESCRIPTION, URL, and the date of DTSTART.
 */
final class IcsParser {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

	private IcsParser() {
	}

	/** One event from the file. {@code date} is converted to {@code zone}, or null if the event has none. */
	record IcsEvent(String uid, String summary, String description, String url, LocalDate date) {

		/** Canvas gives assignments UIDs like "event-assignment-12345"; class events are "event-calendar-event-...". */
		boolean isCanvasAssignment() {
			return uid != null && uid.contains("assignment");
		}

	}

	private record Property(String name, Map<String, String> params, String value) {
	}

	static List<IcsEvent> parse(String ics, ZoneId zone) {
		if (!ics.contains("BEGIN:VCALENDAR")) {
			throw new IllegalArgumentException("That doesn't look like a calendar (.ics) file.");
		}
		List<IcsEvent> events = new ArrayList<>();
		Map<String, Property> current = null;
		int nested = 0; // depth inside sub-components like VALARM, whose properties we ignore
		for (String line : unfold(ics)) {
			if (line.equals("BEGIN:VEVENT")) {
				current = new HashMap<>();
				nested = 0;
			}
			else if (line.equals("END:VEVENT")) {
				if (current != null) {
					events.add(toEvent(current, zone));
				}
				current = null;
			}
			else if (current != null && line.startsWith("BEGIN:")) {
				nested++;
			}
			else if (current != null && line.startsWith("END:")) {
				nested--;
			}
			else if (current != null && nested == 0) {
				Property property = parseProperty(line);
				if (property != null) {
					current.putIfAbsent(property.name(), property);
				}
			}
		}
		return events;
	}

	/** Long .ics lines are "folded": continuation lines start with a space or tab. Join them back up. */
	private static List<String> unfold(String ics) {
		if (!ics.isEmpty() && ics.charAt(0) == 0xFEFF) {
			ics = ics.substring(1); // byte-order mark
		}
		List<String> lines = new ArrayList<>();
		for (String raw : ics.split("\r\n|\n|\r")) {
			if (!lines.isEmpty() && !raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t')) {
				int last = lines.size() - 1;
				lines.set(last, lines.get(last) + raw.substring(1));
			}
			else {
				lines.add(raw);
			}
		}
		return lines.stream().map(String::strip).toList();
	}

	/** Splits "DTSTART;VALUE=DATE:20261015" into name, params, and value. */
	private static Property parseProperty(String line) {
		boolean quoted = false;
		int colon = -1;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			}
			else if (c == ':' && !quoted) {
				colon = i;
				break;
			}
		}
		if (colon <= 0) {
			return null;
		}
		String[] head = line.substring(0, colon).split(";");
		Map<String, String> params = new HashMap<>();
		for (int i = 1; i < head.length; i++) {
			int eq = head[i].indexOf('=');
			if (eq > 0) {
				params.put(head[i].substring(0, eq).toUpperCase(), head[i].substring(eq + 1).replace("\"", ""));
			}
		}
		return new Property(head[0].toUpperCase(), params, line.substring(colon + 1));
	}

	private static IcsEvent toEvent(Map<String, Property> props, ZoneId zone) {
		Property start = props.getOrDefault("DTSTART", props.get("DUE"));
		return new IcsEvent(
				valueOf(props.get("UID"), false),
				valueOf(props.get("SUMMARY"), true),
				valueOf(props.get("DESCRIPTION"), true),
				valueOf(props.get("URL"), false),
				start == null ? null : parseDate(start, zone));
	}

	private static String valueOf(Property property, boolean escapedText) {
		if (property == null || property.value().isBlank()) {
			return null;
		}
		return escapedText ? unescape(property.value()).strip() : property.value().strip();
	}

	/** Text values escape newlines, commas, semicolons, and backslashes with a backslash. */
	private static String unescape(String value) {
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				char next = value.charAt(++i);
				out.append(next == 'n' || next == 'N' ? '\n' : next);
			}
			else {
				out.append(c);
			}
		}
		return out.toString();
	}

	/**
	 * Dates come as "20261015" (all day), "20261015T055900Z" (UTC), or "20261015T235900" with an optional TZID.
	 * Canvas sends due times in UTC, so 11:59pm Central is the next day in UTC; converting to {@code zone} fixes that.
	 */
	private static LocalDate parseDate(Property property, ZoneId zone) {
		String value = property.value().strip();
		try {
			if ("DATE".equalsIgnoreCase(property.params().get("VALUE")) || value.length() == 8) {
				return LocalDate.parse(value.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
			}
			if (value.endsWith("Z")) {
				LocalDateTime utc = LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME);
				return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();
			}
			LocalDateTime local = LocalDateTime.parse(value, DATE_TIME);
			String tzid = property.params().get("TZID");
			if (tzid != null) {
				try {
					return local.atZone(ZoneId.of(tzid)).withZoneSameInstant(zone).toLocalDate();
				}
				catch (DateTimeException unknownZone) {
					return local.toLocalDate();
				}
			}
			return local.toLocalDate();
		}
		catch (DateTimeException | StringIndexOutOfBoundsException badDate) {
			return null;
		}
	}

}
