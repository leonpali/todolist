package com.example.calendar.user;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CredentialRules {

	public static final int EMAIL_MAX_LENGTH = 254;

	public static final String EMAIL_REGEX =
			"^[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*"
			+ "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$";

	public static final int PASSWORD_MIN_LENGTH = 8;

	public static final int PASSWORD_MAX_LENGTH = 64;

	public static final String PASSWORD_ALLOWED_SPECIALS = "!@#$%^&*()_+=[]{}:,.?~|/-";

	/**
	 * Allow-list: ASCII letters, digits and {@link #PASSWORD_ALLOWED_SPECIALS}, 8-64 characters,
	 * at least one letter and one digit. Everything else is rejected, notably quotes ({@code ' " `}),
	 * {@code ;}, {@code < >}, backslash, whitespace, control and non-ASCII characters, which are the
	 * usual building blocks of SQL/script/shell injection payloads.
	 */
	public static final String PASSWORD_REGEX =
			"^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9!@#$%^&*()_+=\\[\\]{}:,.?~|/-]{8,64}$";

	public static final String EMAIL_MESSAGE = "Enter a valid email address";
	public static final String PASSWORD_MESSAGE = "Password must be 8-64 characters, contain a letter and a digit, "
			+ "and may only use letters, digits and " + PASSWORD_ALLOWED_SPECIALS;

	private static final Pattern EMAIL = Pattern.compile(EMAIL_REGEX);
	private static final Pattern PASSWORD = Pattern.compile(PASSWORD_REGEX);

	private CredentialRules() {
	}

	public static boolean isValidEmail(String email) {
		return email != null && email.length() <= EMAIL_MAX_LENGTH && EMAIL.matcher(email).matches();
	}

	public static boolean isValidPassword(String password) {
		return password != null && PASSWORD.matcher(password).matches();
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}
}
