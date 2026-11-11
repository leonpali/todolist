package com.example.calendar.web;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.calendar.task.Task;
import com.example.calendar.task.TaskForm;
import com.example.calendar.task.TaskService;
import com.example.calendar.task.TaskStatus;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CalendarController {

	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final TaskService taskService;

	public CalendarController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping("/")
	public String calendar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
			Model model, Principal principal) {
		LocalDate monday = TaskService.weekStart(week != null ? week : taskService.today());
		if (!model.containsAttribute("taskForm")) {
			TaskForm form = new TaskForm();
			form.setDate(defaultDate(monday));
			model.addAttribute("taskForm", form);
		}
		populate(model, principal.getName(), monday);
		return "calendar";
	}

	@PostMapping("/tasks")
	public String create(@Valid @ModelAttribute("taskForm") TaskForm form, BindingResult result,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
			Model model, Principal principal) {
		if (result.hasErrors()) {
			model.addAttribute("openCreateDialog", true);
			populate(model, principal.getName(), TaskService.weekStart(week != null ? week : taskService.today()));
			return "calendar";
		}
		Task task = taskService.create(principal.getName(), form);
		return redirectToWeek(task.getDate());
	}

	@PostMapping("/tasks/{id}/status")
	public String updateStatus(@PathVariable UUID id, @RequestParam TaskStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
			Principal principal) {
		taskService.updateStatus(principal.getName(), id, status);
		return redirectToWeek(week);
	}

	@PostMapping("/tasks/{id}/date")
	public String updateDate(@PathVariable UUID id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
			Principal principal) {
		if (!TaskService.isWeekday(date)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tasks can only be scheduled Monday to Friday");
		}
		taskService.updateDate(principal.getName(), id, date);
		return redirectToWeek(week);
	}

	@PostMapping("/tasks/{id}/delete")
	public String delete(@PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
			Principal principal) {
		taskService.delete(principal.getName(), id);
		return redirectToWeek(week);
	}

	private static String redirectToWeek(LocalDate dateInWeek) {
		return dateInWeek == null ? "redirect:/" : "redirect:/?week=" + TaskService.weekStart(dateInWeek);
	}

	private LocalDate defaultDate(LocalDate monday) {
		LocalDate today = taskService.today();
		boolean inShownWeek = TaskService.weekStart(today).equals(monday);
		return inShownWeek && TaskService.isWeekday(today) ? today : monday;
	}

	private void populate(Model model, String ownerEmail, LocalDate monday) {
		LocalDate today = taskService.today();
		LocalDate friday = monday.plusDays(TaskService.WEEKDAYS.size() - 1);
		Map<DayOfWeek, List<Task>> byDay = taskService.tasksForWeek(ownerEmail, monday);

		model.addAttribute("currentUser", ownerEmail);

		model.addAttribute("weekStart", monday);
		model.addAttribute("prevWeek", monday.minusWeeks(1));
		model.addAttribute("nextWeek", monday.plusWeeks(1));
		model.addAttribute("isCurrentWeek", TaskService.weekStart(today).equals(monday));
		model.addAttribute("weekNumber", monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
		model.addAttribute("weekRange", (monday.getYear() == friday.getYear()
				? monday.format(DAY_MONTH) : monday.format(DAY_MONTH_YEAR)) + " – " + friday.format(DAY_MONTH_YEAR));
		model.addAttribute("today", today);

		model.addAttribute("weekdays", TaskService.WEEKDAYS);
		model.addAttribute("tasksByDay", byDay);
		model.addAttribute("statuses", TaskStatus.values());
		model.addAttribute("dayNames", TaskService.WEEKDAYS.stream()
				.collect(Collectors.toMap(d -> d, d -> d.getDisplayName(TextStyle.FULL, Locale.ENGLISH))));
		model.addAttribute("dayDates", TaskService.WEEKDAYS.stream()
				.collect(Collectors.toMap(d -> d, d -> monday.plusDays(d.getValue() - 1))));
		model.addAttribute("dayDateLabels", TaskService.WEEKDAYS.stream()
				.collect(Collectors.toMap(d -> d, d -> monday.plusDays(d.getValue() - 1).format(DAY_MONTH))));
	}
}
