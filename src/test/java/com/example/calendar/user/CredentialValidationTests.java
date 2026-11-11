package com.example.calendar.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure unit tests for the email/password rules and the registration form constraints. */
class CredentialValidationTests {

	@Nested
	class Emails {

		@ParameterizedTest
		@ValueSource(strings = {
				"alice@example.com",
				"Alice.Smith@Example.COM",
				"a.b-c_d+tag@sub.domain.co.uk",
				"x@y.io",
				"user%name@example.org",
				"first.last@my-company.de" })
		void acceptsWellFormedAddresses(String email) {
			assertThat(CredentialRules.isValidEmail(email)).isTrue();
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {
				"plainaddress",
				"@example.com",
				"alice@",
				"alice@example",
				"alice@@example.com",
				"alice@example..com",
				".alice@example.com",
				"alice.@example.com",
				"al..ice@example.com",
				"alice@-example.com",
				"alice@example-.com",
				"alice @example.com",
				"alice@exa mple.com",
				"alice@example.c",
				"alice@example.c0m",
				"alice'--@example.com",
				"alice\"@example.com",
				"alice;DROP TABLE app_user@example.com",
				"<script>@example.com",
				"alice@example.com\n",
				"alïce@example.com" })
		void rejectsMalformedOrDangerousAddresses(String email) {
			assertThat(CredentialRules.isValidEmail(email)).isFalse();
		}

		@Test
		void rejectsAddressesLongerThan254Characters() {
			String domain = "@" + "a".repeat(60) + "." + "b".repeat(60) + "." + "c".repeat(60) + ".com";
			String email = "x".repeat(255 - domain.length()) + domain;
			assertThat(email).hasSize(255);
			assertThat(CredentialRules.isValidEmail(email)).isFalse();
			assertThat(CredentialRules.isValidEmail(email.substring(1))).isTrue();
		}

		@Test
		void normalizationTrimsAndLowerCases() {
			assertThat(CredentialRules.normalizeEmail("  Alice@Example.COM ")).isEqualTo("alice@example.com");
		}
	}

	@Nested
	class Passwords {

		@ParameterizedTest
		@ValueSource(strings = {
				"password1",
				"Sup3rSecret",
				"a1!@#$%^&*()_+=[]{}:,.?~|/-",
				"abcdefg8",
				"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ12" })
		void acceptsAllowedPasswords(String password) {
			assertThat(CredentialRules.isValidPassword(password)).isTrue();
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {
				"short1",                        // too short
				"abcdefgh",                      // no digit
				"12345678",                      // no letter
				"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ123" })  // 65 chars, too long
		void rejectsPasswordsBreakingLengthOrStrengthRules(String password) {
			assertThat(CredentialRules.isValidPassword(password)).isFalse();
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"' OR '1'='1' --x1",             // SQL injection
				"pass1'; DROP TABLE app_user;",  // SQL injection
				"abc123\" OR \"\"=\"",           // SQL injection with double quotes
				"<script>alert(1)</script>a1",   // XSS
				"a1<img src=x onerror=x>",       // XSS
				"pass1`rm -rf /`",               // shell command substitution
				"pass1\\x00abc",                 // backslash escapes
				"pass word1",                    // whitespace
				"password1\t",                   // tab
				"password1\n",                   // newline (header/log injection)
				"password1\u0000",               // NUL byte
				"pässword1",                     // non-ASCII
				"pass1 ${jndi:ldap://x}" })      // log4shell-style payload (contains space)
		void rejectsPasswordsWithHarmfulCharacters(String password) {
			assertThat(CredentialRules.isValidPassword(password)).isFalse();
		}

		@Test
		void eachForbiddenCharacterIsRejectedOnItsOwn() {
			for (char c : "'\";<>\\` \t\r\n".toCharArray()) {
				assertThat(CredentialRules.isValidPassword("abcd123" + c))
						.as("password containing %s", Character.getName(c))
						.isFalse();
			}
		}
	}

	@Nested
	class Form {

		static ValidatorFactory factory;
		static Validator validator;

		@BeforeAll
		static void createValidator() {
			factory = Validation.buildDefaultValidatorFactory();
			validator = factory.getValidator();
		}

		@AfterAll
		static void closeValidator() {
			factory.close();
		}

		@Test
		void validFormHasNoViolations() {
			assertThat(validator.validate(form("alice@example.com", "password1", "password1"))).isEmpty();
		}

		@Test
		void invalidEmailIsReportedOnEmailField() {
			assertThat(invalidFields(form("not-an-email", "password1", "password1"))).containsExactly("email");
		}

		@Test
		void harmfulPasswordIsReportedOnPasswordField() {
			assertThat(invalidFields(form("alice@example.com", "x1'; DROP TABLE task;--", "x1'; DROP TABLE task;--")))
					.containsExactly("password");
		}

		@Test
		void mismatchingConfirmationIsReported() {
			assertThat(invalidFields(form("alice@example.com", "password1", "password2")))
					.containsExactly("passwordConfirmed");
		}

		@Test
		void blankFieldsAreReported() {
			assertThat(invalidFields(form("", "", ""))).contains("email", "password");
		}

		@Test
		void emailIsTrimmedOnBinding() {
			assertThat(form("  alice@example.com  ", "password1", "password1").getEmail()).isEqualTo("alice@example.com");
		}

		private Set<String> invalidFields(RegistrationForm form) {
			return validator.validate(form).stream()
					.map(ConstraintViolation::getPropertyPath)
					.map(Object::toString)
					.collect(Collectors.toSet());
		}

		private static RegistrationForm form(String email, String password, String confirm) {
			RegistrationForm form = new RegistrationForm();
			form.setEmail(email);
			form.setPassword(password);
			form.setConfirmPassword(confirm);
			return form;
		}
	}
}
