package com.example.calendar.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.example.calendar.task.TaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Registration persistence, email primary key, and salted BCrypt hashing, verified against the raw table. */
@SpringBootTest
class UserStorageAndHashingTests {

	/** {@code $2a$<cost>$<22-char salt><31-char hash>}, 60 characters in total. */
	static final String BCRYPT_FORMAT = "^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$";

	@Autowired
	UserService userService;

	@Autowired
	UserRepository users;

	@Autowired
	TaskRepository tasks;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clean() {
		tasks.deleteAll();
		users.deleteAll();
	}

	@Test
	void registeredUserIsStoredWithEmailAsPrimaryKey() {
		userService.register("alice@example.com", "password1");

		Map<String, Object> row = jdbc.queryForMap("select email, password_hash, created_at from app_user");
		assertThat(row.get("email")).isEqualTo("alice@example.com");
		assertThat(row.get("created_at")).isNotNull();
		assertThat(users.findById("alice@example.com")).isPresent();
	}

	@Test
	void emailIsNormalizedBeforeStoring() {
		userService.register("  Alice@Example.COM ", "password1");

		assertThat(jdbc.queryForObject("select email from app_user", String.class)).isEqualTo("alice@example.com");
	}

	@Test
	void emailIsUniqueRegardlessOfCase() {
		userService.register("alice@example.com", "password1");

		assertThatThrownBy(() -> userService.register("ALICE@example.com", "otherPass2"))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
		assertThat(users.count()).isEqualTo(1);
	}

	@Test
	void databaseRejectsDuplicatePrimaryKey() {
		jdbc.update("insert into app_user (email, password_hash, created_at) values ('bob@example.com', 'x', now())");

		assertThatThrownBy(() -> jdbc.update(
				"insert into app_user (email, password_hash, created_at) values ('bob@example.com', 'y', now())"))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void plainPasswordIsNeverStored() {
		userService.register("alice@example.com", "password1");

		String stored = jdbc.queryForObject("select password_hash from app_user", String.class);
		assertThat(stored).isNotEqualTo("password1").doesNotContain("password1");
		Integer rowsContainingPlainText = jdbc.queryForObject(
				"select count(*) from app_user where password_hash like '%password1%'", Integer.class);
		assertThat(rowsContainingPlainText).isZero();
	}

	@Test
	void storedValueIsABcryptHashThatVerifiesTheOriginalPassword() {
		AppUser user = userService.register("alice@example.com", "password1");

		assertThat(user.getPasswordHash()).matches(BCRYPT_FORMAT);
		assertThat(passwordEncoder.matches("password1", user.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("password2", user.getPasswordHash())).isFalse();
		assertThat(passwordEncoder.matches("Password1", user.getPasswordHash())).isFalse();
	}

	@Test
	void samePasswordProducesDifferentHashesBecauseEachIsSalted() {
		String aliceHash = userService.register("alice@example.com", "samePassw0rd").getPasswordHash();
		String bobHash = userService.register("bob@example.com", "samePassw0rd").getPasswordHash();

		assertThat(aliceHash).isNotEqualTo(bobHash);
		assertThat(salt(aliceHash)).isNotEqualTo(salt(bobHash));
		assertThat(passwordEncoder.matches("samePassw0rd", aliceHash)).isTrue();
		assertThat(passwordEncoder.matches("samePassw0rd", bobHash)).isTrue();
	}

	@Test
	void encoderGeneratesAFreshSaltOnEveryCall() {
		String first = passwordEncoder.encode("password1");
		String second = passwordEncoder.encode("password1");

		assertThat(first).isNotEqualTo(second);
		assertThat(salt(first)).isNotEqualTo(salt(second));
	}

	@Test
	void serviceRejectsInvalidEmailWithoutStoringAnything() {
		assertThatThrownBy(() -> userService.register("not-an-email", "password1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(users.count()).isZero();
	}

	@Test
	void serviceRejectsHarmfulPasswordWithoutStoringAnything() {
		assertThatThrownBy(() -> userService.register("alice@example.com", "x1'; DROP TABLE app_user;--"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(users.count()).isZero();
	}

	@Test
	void loadUserByUsernameFindsUserCaseInsensitively() {
		AppUser user = userService.register("alice@example.com", "password1");

		UserDetails details = userService.loadUserByUsername("ALICE@example.com");
		assertThat(details.getUsername()).isEqualTo("alice@example.com");
		assertThat(details.getPassword()).isEqualTo(user.getPasswordHash());
	}

	@Test
	void loadUserByUsernameRejectsUnknownAndMalformedEmails() {
		assertThatThrownBy(() -> userService.loadUserByUsername("nobody@example.com"))
				.isInstanceOf(UsernameNotFoundException.class);
		assertThatThrownBy(() -> userService.loadUserByUsername("' OR 1=1 --"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	private static String salt(String bcryptHash) {
		// "$2a$12$" is 7 characters, followed by the 22-character salt.
		return bcryptHash.substring(7, 29);
	}
}
