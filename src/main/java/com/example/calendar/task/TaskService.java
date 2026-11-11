package com.example.calendar.task;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.calendar.user.AppUser;
import com.example.calendar.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private static final Logger log = LoggerFactory.getLogger(TaskService.class);

	public static final List<DayOfWeek> WEEKDAYS = List.of(
			DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

	private final TaskRepository repository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final Period completedRetention;

	public TaskService(TaskRepository repository, UserRepository userRepository, Clock clock,
			@Value("${calendar.tasks.completed-retention:P1M}") Period completedRetention) {
		this.repository = repository;
		this.userRepository = userRepository;
		this.clock = clock;
		this.completedRetention = completedRetention;
	}

	@Transactional
	public Task create(String ownerEmail, TaskForm form) {
		AppUser owner = userRepository.findById(ownerEmail)
				.orElseThrow(() -> new IllegalStateException("Unknown user: " + ownerEmail));
		String description = form.getDescription() == null ? "" : form.getDescription().strip();
		Task task = new Task(owner, form.getHeadline().strip(), description,
				form.getEstimatedHours(), form.getStatus(), form.getDate(), clock.instant());
		return repository.save(task);
	}

	@Transactional
	public void updateStatus(String ownerEmail, UUID id, TaskStatus status) {
		findOwned(ownerEmail, id).changeStatus(status, clock.instant());
	}

	@Transactional
	public void updateDate(String ownerEmail, UUID id, LocalDate date) {
		if (!isWeekday(date)) {
			throw new IllegalArgumentException("Tasks can only be scheduled Monday to Friday: " + date);
		}
		findOwned(ownerEmail, id).changeDate(date);
	}

	@Transactional
	public void delete(String ownerEmail, UUID id) {
		repository.delete(findOwned(ownerEmail, id));
	}

	private Task findOwned(String ownerEmail, UUID id) {
		return repository.findByIdAndOwner_Email(id, ownerEmail).orElseThrow(() -> new TaskNotFoundException(id));
	}

	@Scheduled(cron = "${calendar.tasks.cleanup-cron:0 0 * * * *}")
	@Transactional
	public int purgeExpiredCompletedTasks() {
		Instant cutoff = ZonedDateTime.now(clock).minus(completedRetention).toInstant();
		int removed = repository.deleteCompletedBefore(cutoff);
		if (removed > 0) {
			log.info("Removed {} task(s) completed before {}", removed, cutoff);
		}
		return removed;
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	public static LocalDate weekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	public static boolean isWeekday(LocalDate date) {
		return WEEKDAYS.contains(date.getDayOfWeek());
	}

	@Transactional(readOnly = true)
	public Map<DayOfWeek, List<Task>> tasksForWeek(String ownerEmail, LocalDate monday) {
		List<Task> all = repository.findByOwner_EmailAndDateBetweenOrderByCreatedAtAsc(
				ownerEmail, monday, monday.plusDays(WEEKDAYS.size() - 1));
		Map<DayOfWeek, List<Task>> result = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek day : WEEKDAYS) {
			result.put(day, all.stream().filter(t -> t.getDay() == day).toList());
		}
		return result;
	}
}
