package com.personalsite.calendar;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

	List<CalendarEvent> findByEventDateBetweenOrderByEventDateAscStartTimeAsc(LocalDate start, LocalDate end);

}
