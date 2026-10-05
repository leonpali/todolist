package com.example.calendar.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TaskRetentionTests {

	@Autowired
	TaskRepository repository;

	@Autowired
	TaskService service;

	@BeforeEach
	void clearTasks() {
		repository.deleteAll();
	}

	@Test
	void removesOnlyTasksCompletedMoreThanAMonthAgo() {
		ZonedDateTime now = ZonedDateTime.now();
		Task expired = save("Done 5 weeks ago", TaskStatus.DONE, now.minusWeeks(5).toInstant());
		Task recent = save("Done 3 weeks ago", TaskStatus.DONE, now.minusWeeks(3).toInstant());
		Task oldOpen = save("Open for 2 months", TaskStatus.IN_PROGRESS, now.minusMonths(2).toInstant());

		assertThat(service.purgeExpiredCompletedTasks()).isEqualTo(1);

		assertThat(repository.existsById(expired.getId())).isFalse();
		assertThat(repository.existsById(recent.getId())).isTrue();
		assertThat(repository.existsById(oldOpen.getId())).isTrue();
	}

	private Task save(String headline, TaskStatus status, Instant at) {
		return repository.save(new Task(headline, "", 1, status, LocalDate.of(2026, 10, 5), at));
	}
}
