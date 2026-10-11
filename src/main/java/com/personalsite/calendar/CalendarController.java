package com.personalsite.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.personalsite.tasks.Task;
import com.personalsite.tasks.TaskRepository;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/calendar")
public class CalendarController {

	private final CalendarEventRepository events;

	private final TaskRepository tasks;

	public CalendarController(CalendarEventRepository events, TaskRepository tasks) {
		this.events = events;
		this.tasks = tasks;
	}

	@GetMapping
	public String month(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
			Model model) {
		YearMonth current = resolveMonth(year, month);
		fillMonth(current, model);
		CalendarEvent form = new CalendarEvent();
		form.setEventDate(LocalDate.now());
		model.addAttribute("event", form);
		return "calendar/month";
	}

	@PostMapping
	public String add(@Valid @ModelAttribute("event") CalendarEvent event, BindingResult result,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month, Model model) {
		if (result.hasErrors()) {
			fillMonth(resolveMonth(year, month), model);
			return "calendar/month";
		}
		events.save(event);
		YearMonth target = YearMonth.from(event.getEventDate());
		return "redirect:/calendar?year=" + target.getYear() + "&month=" + target.getMonthValue();
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month) {
		events.deleteById(id);
		YearMonth current = resolveMonth(year, month);
		return "redirect:/calendar?year=" + current.getYear() + "&month=" + current.getMonthValue();
	}

	private void fillMonth(YearMonth current, Model model) {
		LocalDate gridStart = current.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
		LocalDate gridEnd = current.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

		Map<LocalDate, List<CalendarEvent>> byDate = events
			.findByEventDateBetweenOrderByEventDateAscStartTimeAsc(gridStart, gridEnd)
			.stream()
			.collect(Collectors.groupingBy(CalendarEvent::getEventDate));

		Map<LocalDate, List<Task>> tasksByDate = tasks.findByDueDateBetween(gridStart, gridEnd)
			.stream()
			.sorted(Comparator.comparing(Task::isCompleted).thenComparing(Task::getPriority, Comparator.reverseOrder()))
			.collect(Collectors.groupingBy(Task::getDueDate));

		List<List<CalendarDay>> weeks = new ArrayList<>();
		List<CalendarDay> week = new ArrayList<>();
		for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
			week.add(new CalendarDay(date, YearMonth.from(date).equals(current),
					byDate.getOrDefault(date, List.of()), tasksByDate.getOrDefault(date, List.of())));
			if (week.size() == 7) {
				weeks.add(week);
				week = new ArrayList<>();
			}
		}

		model.addAttribute("current", current);
		model.addAttribute("previous", current.minusMonths(1));
		model.addAttribute("next", current.plusMonths(1));
		model.addAttribute("weeks", weeks);
	}

	private YearMonth resolveMonth(Integer year, Integer month) {
		if (year != null && month != null) {
			return YearMonth.of(year, month);
		}
		return YearMonth.now();
	}

}
