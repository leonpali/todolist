package com.example.calendar.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.calendar.task.TaskRepository;
import com.example.calendar.user.UserRepository;
import com.example.calendar.user.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

/** With HTTPS enforcement on (the production default), credentials are only accepted over TLS. */
@SpringBootTest(properties = "app.security.require-https=true")
@AutoConfigureMockMvc
class HttpsEnforcementTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	UserService userService;

	@Autowired
	UserRepository users;

	@Autowired
	TaskRepository tasks;

	@BeforeEach
	void setUp() {
		tasks.deleteAll();
		users.deleteAll();
		userService.register("alice@example.com", "password1");
	}

	@Test
	void plainHttpPageRequestIsRedirectedToHttps() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://")));
	}

	@Test
	void loginOverPlainHttpIsRedirectedAndNotAuthenticated() throws Exception {
		mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password("password1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://")))
				.andExpect(unauthenticated());
	}

	@Test
	void loginOverHttpsSucceeds() throws Exception {
		mvc.perform(post("/login").with(HttpsEnforcementTests::overTls).with(csrf())
						.param("email", "alice@example.com").param("password", "password1"))
				.andExpect(header().string("Location", "/"))
				.andExpect(authenticated().withUsername("alice@example.com"));
	}

	@Test
	void httpsResponsesCarryStrictTransportSecurityHeader() throws Exception {
		mvc.perform(get("/login").with(HttpsEnforcementTests::overTls))
				.andExpect(status().isOk())
				.andExpect(header().string("Strict-Transport-Security", containsString("max-age=")));
	}

	@Test
	void plainHttpResponsesDoNotAdvertiseHsts() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(header().doesNotExist("Strict-Transport-Security"));
	}

	/** Makes a MockMvc request look like it arrived on the TLS connector, as Tomcat would report it. */
	static MockHttpServletRequest overTls(MockHttpServletRequest request) {
		request.setScheme("https");
		request.setSecure(true);
		request.setServerPort(8443);
		return request;
	}
}
