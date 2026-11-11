package com.example.calendar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.example.calendar.task.Task;
import com.example.calendar.task.TaskRepository;
import com.example.calendar.task.TaskStatus;
import com.example.calendar.user.AppUser;
import com.example.calendar.user.UserRepository;
import com.example.calendar.user.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Users log in for real (form login, session cookie) and must only ever see and modify their own tasks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskOwnershipTests {

	static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
	static final String ALICE = "alice@example.com";
	static final String BOB = "bob@example.com";
	static final String PASSWORD = "password1";

	@Autowired
	MockMvc mvc;

	@Autowired
	UserService userService;

	@Autowired
	UserRepository users;

	@Autowired
	TaskRepository tasks;

	@Autowired
	JdbcTemplate jdbc;

	AppUser alice;
	AppUser bob;

	@BeforeEach
	void setUp() {
		tasks.deleteAll();
		users.deleteAll();
		alice = userService.register(ALICE, PASSWORD);
		bob = userService.register(BOB, PASSWORD);
	}

	@Test
	void eachUserSeesOnlyTheirOwnTasksAfterLoggingIn() throws Exception {
		save(alice, "Alice writes report");
		save(bob, "Bob fixes printer");

		MockHttpSession aliceSession = login(ALICE);
		MockHttpSession bobSession = login(BOB);

		mvc.perform(get("/").param("week", MONDAY.toString()).session(aliceSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(ALICE)))
				.andExpect(content().string(containsString("Alice writes report")))
				.andExpect(content().string(not(containsString("Bob fixes printer"))));

		mvc.perform(get("/").param("week", MONDAY.toString()).session(bobSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(BOB)))
				.andExpect(content().string(containsString("Bob fixes printer")))
				.andExpect(content().string(not(containsString("Alice writes report"))));
	}

	@Test
	void createdTaskIsAssignedToTheLoggedInUser() throws Exception {
		MockHttpSession bobSession = login(BOB);

		mvc.perform(post("/tasks").session(bobSession).with(csrf())
				.param("headline", "Bob's new task").param("estimatedHours", "1")
				.param("status", "TODO").param("date", MONDAY.toString()));

		assertThat(tasks.findByOwner_Email(BOB)).extracting(Task::getHeadline).containsExactly("Bob's new task");
		assertThat(tasks.findByOwner_Email(ALICE)).isEmpty();
		assertThat(jdbc.queryForObject("select owner_email from task", String.class)).isEqualTo(BOB);
	}

	@Test
	void ownerCannotBeSpoofedThroughRequestParameters() throws Exception {
		MockHttpSession bobSession = login(BOB);

		mvc.perform(post("/tasks").session(bobSession).with(csrf())
				.param("headline", "Planted task").param("estimatedHours", "1")
				.param("status", "TODO").param("date", MONDAY.toString())
				.param("owner", ALICE).param("owner.email", ALICE).param("ownerEmail", ALICE));

		assertThat(tasks.findByOwner_Email(ALICE)).isEmpty();
		assertThat(tasks.findByOwner_Email(BOB)).hasSize(1);
	}

	@Test
	void userCannotChangeStatusOfAnotherUsersTask() throws Exception {
		UUID aliceTask = save(alice, "Alice only").getId();
		MockHttpSession bobSession = login(BOB);

		mvc.perform(post("/tasks/{id}/status", aliceTask).session(bobSession).with(csrf()).param("status", "DONE"))
				.andExpect(status().isNotFound());

		assertThat(tasks.findById(aliceTask).orElseThrow().getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void userCannotMoveAnotherUsersTask() throws Exception {
		UUID aliceTask = save(alice, "Alice only").getId();
		MockHttpSession bobSession = login(BOB);

		mvc.perform(post("/tasks/{id}/date", aliceTask).session(bobSession).with(csrf())
						.param("date", MONDAY.plusDays(1).toString()))
				.andExpect(status().isNotFound());

		assertThat(tasks.findById(aliceTask).orElseThrow().getDate()).isEqualTo(MONDAY);
	}

	@Test
	void userCannotDeleteAnotherUsersTask() throws Exception {
		UUID aliceTask = save(alice, "Alice only").getId();
		MockHttpSession bobSession = login(BOB);

		mvc.perform(post("/tasks/{id}/delete", aliceTask).session(bobSession).with(csrf()))
				.andExpect(status().isNotFound());

		assertThat(tasks.existsById(aliceTask)).isTrue();
	}

	@Test
	void ownerCanStillModifyTheirOwnTask() throws Exception {
		UUID aliceTask = save(alice, "Alice only").getId();
		MockHttpSession aliceSession = login(ALICE);

		mvc.perform(post("/tasks/{id}/status", aliceTask).session(aliceSession).with(csrf()).param("status", "DONE"))
				.andExpect(status().is3xxRedirection());
		assertThat(tasks.findById(aliceTask).orElseThrow().getStatus()).isEqualTo(TaskStatus.DONE);

		mvc.perform(post("/tasks/{id}/delete", aliceTask).session(aliceSession).with(csrf()))
				.andExpect(status().is3xxRedirection());
		assertThat(tasks.existsById(aliceTask)).isFalse();
	}

	@Test
	void aUserCanOwnManyTasks() {
		IntStream.range(0, 250).forEach(i -> save(alice, "Task " + i));

		assertThat(tasks.findByOwner_Email(ALICE)).hasSize(250);
		assertThat(tasks.findByOwner_Email(BOB)).isEmpty();
	}

	@Test
	void everyTaskHasExactlyOneOwnerInTheDatabase() {
		save(alice, "A");
		save(bob, "B");

		List<String> owners = jdbc.queryForList("select owner_email from task order by headline", String.class);
		assertThat(owners).containsExactly(ALICE, BOB);
	}

	@Test
	void taskCannotReferenceANonExistentUser() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into task (id, headline, description, estimated_hours, status, scheduled_date, created_at, owner_email) "
						+ "values (random_uuid(), 'x', '', 1, 'TODO', date '2026-10-05', now(), 'ghost@example.com')"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.message().containsIgnoringCase("task_owner_fk");
	}

	@Test
	void deletingAUserDeletesTheirTasksOnly() {
		save(alice, "A1");
		save(alice, "A2");
		save(bob, "B1");

		jdbc.update("delete from app_user where email = ?", ALICE);

		assertThat(tasks.findAll()).extracting(Task::getHeadline).containsExactly("B1");
	}

	private Task save(AppUser owner, String headline) {
		return tasks.save(new Task(owner, headline, "", 1, TaskStatus.TODO, MONDAY, Instant.now()));
	}

	private MockHttpSession login(String email) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession(false);
	}
}
