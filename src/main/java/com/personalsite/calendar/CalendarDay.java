package com.personalsite.calendar;

import java.time.LocalDate;
import java.util.List;

import com.personalsite.tasks.Task;

public record CalendarDay(LocalDate date, boolean inMonth, List<CalendarEvent> events, List<Task> tasks) {
}
