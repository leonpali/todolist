package com.example.calendar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.calendar.task.Task;
import com.example.calendar.task.TaskRepository;
import com.example.calendar.task.TaskStatus;
import com.example.calendar.user.AppUser;
import com.example.calendar.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class CalendarControllerTests {

	static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
	static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
	static final String USER = "alice@example.com";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TaskRepository repository;

	@Autowired
	UserRepository users;

	MockMvc mvc;

	/** Every request in this class is made by a logged-in user and carries a CSRF token. */
	@BeforeEach
	void setUp() {
		repository.deleteAll();
		users.deleteAll();
		users.save(new AppUser(USER, "$2a$04$notarealhashnotarealhashnotarealhashnotarealhashnotr", Instant.now()));
		mvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.defaultRequest(get("/").with(user(USER)).with(csrf()))
				.build();
	}

	@Test
	void rendersAllWeekdayColumnsWithDates() throws Exception {
		mvc.perform(get("/").param("week", MONDAY.toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Monday")))
				.andExpect(content().string(containsString("Friday")))
				.andExpect(content().string(containsString("Create task")))
				.andExpect(content().string(containsString("5 Oct – 9 Oct 2026")))
				.andExpect(content().string(containsString("Week 41")))
				.andExpect(content().string(containsString("?week=2026-09-28")))
				.andExpect(content().string(containsString("?week=2026-10-12")))
				.andExpect(content().string(not(containsString("Saturday"))));
	}

	@Test
	void defaultsToTheCurrentWeek() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Week ")));
	}

	@Test
	void createdTaskIsPersistedAndShownOnlyInItsWeek() throws Exception {
		mvc.perform(post("/tasks")
						.param("headline", "Write release notes")
						.param("description", "Cover the new board")
						.param("estimatedHours", "1.5")
						.param("status", "IN_PROGRESS")
						.param("date", WEDNESDAY.toString()))
				.andExpect(redirectedUrl("/?week=2026-10-05"));

		Task saved = repository.findAll().getFirst();
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getDate()).isEqualTo(WEDNESDAY);

		mvc.perform(get("/").param("week", "2026-10-08"))
				.andExpect(content().string(containsString("Write release notes")))
				.andExpect(content().string(containsString("Cover the new board")))
				.andExpect(content().string(containsString("1h 30m")))
				.andExpect(content().string(containsString("status-in-progress")))
				.andExpect(content().string(containsString("value=\"2026-10-07\"")))
				.andExpect(content().string(containsString("/tasks/" + saved.getId() + "/delete")));

		mvc.perform(get("/").param("week", MONDAY.plusWeeks(1).toString()))
				.andExpect(content().string(not(containsString("Write release notes"))));
		mvc.perform(get("/").param("week", MONDAY.minusWeeks(1).toString()))
				.andExpect(content().string(not(containsString("Write release notes"))));
	}

	@Test
	void invalidTaskReopensDialogWithErrors() throws Exception {
		mvc.perform(post("/tasks")
						.param("headline", "")
						.param("estimatedHours", "0")
						.param("status", "TODO")
						.param("date", MONDAY.toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-open=\"true\"")))
				.andExpect(content().string(containsString("class=\"error\"")));
		assertThat(repository.count()).isZero();
	}

	@Test
	void weekendDateIsRejectedOnCreate() throws Exception {
		mvc.perform(post("/tasks")
						.param("headline", "Weekend work")
						.param("estimatedHours", "1")
						.param("status", "TODO")
						.param("date", "2026-10-10"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Pick a weekday")));
		assertThat(repository.count()).isZero();
	}

	@Test
	void dateCanBeChangedToAnotherWeek() throws Exception {
		UUID id = createTask("Move me", TaskStatus.TODO, WEDNESDAY);
		LocalDate nextTuesday = MONDAY.plusWeeks(1).plusDays(1);

		mvc.perform(post("/tasks/{id}/date", id)
						.param("date", nextTuesday.toString())
						.param("week", MONDAY.toString()))
				.andExpect(redirectedUrl("/?week=2026-10-05"));

		assertThat(repository.findById(id).orElseThrow().getDate()).isEqualTo(nextTuesday);
		mvc.perform(get("/").param("week", MONDAY.toString()))
				.andExpect(content().string(not(containsString("Move me"))));
		mvc.perform(get("/").param("week", nextTuesday.toString()))
				.andExpect(content().string(containsString("Move me")));
	}

	@Test
	void weekendDateIsRejectedOnChange() throws Exception {
		UUID id = createTask("Stay put", TaskStatus.TODO, WEDNESDAY);

		mvc.perform(post("/tasks/{id}/date", id).param("date", "2026-10-11"))
				.andExpect(status().isBadRequest());

		assertThat(repository.findById(id).orElseThrow().getDate()).isEqualTo(WEDNESDAY);
	}

	@Test
	void statusCanBeChangedAndCompletionIsTracked() throws Exception {
		UUID id = createTask("Fix bug", TaskStatus.TODO, MONDAY.plusDays(4));

		mvc.perform(post("/tasks/{id}/status", id).param("status", "DONE").param("week", MONDAY.toString()))
				.andExpect(redirectedUrl("/?week=2026-10-05"));

		assertThat(repository.findById(id)).get()
				.satisfies(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.DONE))
				.satisfies(t -> assertThat(t.getCompletedAt()).isNotNull());
		mvc.perform(get("/").param("week", MONDAY.toString()))
				.andExpect(content().string(containsString("status-done")));

		mvc.perform(post("/tasks/{id}/status", id).param("status", "IN_REVIEW"));
		assertThat(repository.findById(id).orElseThrow().getCompletedAt()).isNull();
	}

	@Test
	void taskCanBeDeleted() throws Exception {
		UUID keep = createTask("Keep me", TaskStatus.TODO, MONDAY);
		UUID remove = createTask("Delete me", TaskStatus.TODO, MONDAY);

		mvc.perform(post("/tasks/{id}/delete", remove).param("week", MONDAY.toString()))
				.andExpect(redirectedUrl("/?week=2026-10-05"));

		assertThat(repository.existsById(remove)).isFalse();
		assertThat(repository.existsById(keep)).isTrue();
		mvc.perform(get("/").param("week", MONDAY.toString()))
				.andExpect(content().string(not(containsString("Delete me"))))
				.andExpect(content().string(containsString("Keep me")));
	}

	private UUID createTask(String headline, TaskStatus status, LocalDate date) throws Exception {
		mvc.perform(post("/tasks")
				.param("headline", headline)
				.param("estimatedHours", "2")
				.param("status", status.name())
				.param("date", date.toString()));
		return repository.findAll().stream()
				.filter(t -> t.getHeadline().equals(headline))
				.findFirst().orElseThrow().getId();
	}
}
