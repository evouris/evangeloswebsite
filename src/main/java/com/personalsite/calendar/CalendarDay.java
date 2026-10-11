package com.personalsite.calendar;

import java.time.LocalDate;
import java.util.List;

public record CalendarDay(LocalDate date, boolean inMonth, List<CalendarEvent> events) {
}
