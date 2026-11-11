package com.example.calendar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.calendar.task.TaskRepository;
import com.example.calendar.user.UserRepository;
import com.example.calendar.user.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Registration and login through the real security filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTests {

	static final String EMAIL = "alice@example.com";
	static final String PASSWORD = "password1";

	@Autowired
	MockMvc mvc;

	@Autowired
	UserService userService;

	@Autowired
	UserRepository users;

	@Autowired
	TaskRepository tasks;

	@BeforeEach
	void clean() {
		tasks.deleteAll();
		users.deleteAll();
	}

	// --- access control ---

	@Test
	void anonymousUserIsRedirectedToLogin() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void anonymousUserCannotCreateTasks() throws Exception {
		mvc.perform(post("/tasks").with(csrf())
						.param("headline", "Sneaky").param("estimatedHours", "1")
						.param("status", "TODO").param("date", "2026-10-05"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		assertThat(tasks.count()).isZero();
	}

	@Test
	void loginAndRegisterPagesArePublic() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Log in")));
		mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(content().string(containsString("Create account")));
	}

	@Test
	void postsWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/register").param("email", EMAIL).param("password", PASSWORD).param("confirmPassword", PASSWORD))
				.andExpect(status().isForbidden());
		assertThat(users.count()).isZero();
	}

	// --- registration ---

	@Test
	void registrationStoresUserAndRedirectsToLogin() throws Exception {
		mvc.perform(post("/register").with(csrf())
						.param("email", "Alice@Example.com").param("password", PASSWORD).param("confirmPassword", PASSWORD))
				.andExpect(redirectedUrl("/login?registered"));

		assertThat(users.findById(EMAIL)).isPresent();
	}

	@Test
	void registrationWithInvalidEmailShowsErrorAndStoresNothing() throws Exception {
		mvc.perform(post("/register").with(csrf())
						.param("email", "alice@").param("password", PASSWORD).param("confirmPassword", PASSWORD))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Enter a valid email address")));
		assertThat(users.count()).isZero();
	}

	@Test
	void registrationWithHarmfulPasswordShowsErrorAndStoresNothing() throws Exception {
		String harmful = "abc123<script>";
		mvc.perform(post("/register").with(csrf())
						.param("email", EMAIL).param("password", harmful).param("confirmPassword", harmful))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("may only use letters, digits")))
				.andExpect(content().string(not(containsString(harmful))));
		assertThat(users.count()).isZero();
	}

	@Test
	void registrationWithMismatchedConfirmationIsRejected() throws Exception {
		mvc.perform(post("/register").with(csrf())
						.param("email", EMAIL).param("password", PASSWORD).param("confirmPassword", "password2"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Passwords do not match")));
		assertThat(users.count()).isZero();
	}

	@Test
	void duplicateRegistrationIsRejected() throws Exception {
		userService.register(EMAIL, PASSWORD);

		mvc.perform(post("/register").with(csrf())
						.param("email", "ALICE@example.com").param("password", "another1").param("confirmPassword", "another1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("already exists")));
		assertThat(users.count()).isEqualTo(1);
	}

	@Test
	void passwordIsNotEchoedBackAfterFailedRegistration() throws Exception {
		mvc.perform(post("/register").with(csrf())
						.param("email", "bad").param("password", "Secret123").param("confirmPassword", "Secret123"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Secret123"))));
	}

	// --- login ---

	@Test
	void validCredentialsLogTheUserIn() throws Exception {
		userService.register(EMAIL, PASSWORD);

		mvc.perform(formLogin("/login").userParameter("email").user(EMAIL).password(PASSWORD))
				.andExpect(redirectedUrl("/"))
				.andExpect(authenticated().withUsername(EMAIL));
	}

	@Test
	void loginEmailIsCaseInsensitive() throws Exception {
		userService.register(EMAIL, PASSWORD);

		mvc.perform(formLogin("/login").userParameter("email").user("  ALICE@Example.COM").password(PASSWORD))
				.andExpect(authenticated().withUsername(EMAIL));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		userService.register(EMAIL, PASSWORD);

		mvc.perform(formLogin("/login").userParameter("email").user(EMAIL).password("password2"))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	@Test
	void unknownEmailIsRejected() throws Exception {
		mvc.perform(formLogin("/login").userParameter("email").user("nobody@example.com").password(PASSWORD))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "' OR '1'='1", "alice@example.com' --", "<script>@x.com", "not-an-email" })
	void malformedOrInjectedEmailIsRejectedAtLogin(String email) throws Exception {
		userService.register(EMAIL, PASSWORD);

		mvc.perform(formLogin("/login").userParameter("email").user(email).password(PASSWORD))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "' OR '1'='1' --", "password1'; --", "password1<", "password1 " })
	void passwordWithHarmfulCharactersIsRejectedAtLogin(String password) throws Exception {
		userService.register(EMAIL, PASSWORD);

		mvc.perform(formLogin("/login").userParameter("email").user(EMAIL).password(password))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	@Test
	void loginErrorPageShowsGenericMessage() throws Exception {
		mvc.perform(get("/login").param("error", ""))
				.andExpect(content().string(containsString("Invalid email or password.")));
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		mvc.perform(logout())
				.andExpect(redirectedUrl("/login?logout"))
				.andExpect(unauthenticated());
	}
}
