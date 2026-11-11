package com.example.calendar.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.example.calendar.user.AppUser;
import com.example.calendar.user.UserRepository;

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

	@Autowired
	UserRepository users;

	AppUser owner;

	@BeforeEach
	void clearTasks() {
		repository.deleteAll();
		users.deleteAll();
		owner = users.save(new AppUser("owner@example.com", "$2a$04$notarealhashnotarealhashnotarealhashnotarealhashnotr", Instant.now()));
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
		return repository.save(new Task(owner, headline, "", 1, status, LocalDate.of(2026, 10, 5), at));
	}
}
