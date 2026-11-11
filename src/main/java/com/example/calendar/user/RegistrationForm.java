package com.example.calendar.user;

import java.util.Objects;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

	@NotBlank(message = CredentialRules.EMAIL_MESSAGE)
	@Size(max = CredentialRules.EMAIL_MAX_LENGTH, message = CredentialRules.EMAIL_MESSAGE)
	@Pattern(regexp = CredentialRules.EMAIL_REGEX, message = CredentialRules.EMAIL_MESSAGE)
	private String email;

	@NotBlank(message = CredentialRules.PASSWORD_MESSAGE)
	@Pattern(regexp = CredentialRules.PASSWORD_REGEX, message = CredentialRules.PASSWORD_MESSAGE)
	private String password;

	private String confirmPassword;

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email == null ? null : email.strip(); }

	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }

	public String getConfirmPassword() { return confirmPassword; }
	public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

	@AssertTrue(message = "Passwords do not match")
	public boolean isPasswordConfirmed() {
		return password == null || Objects.equals(password, confirmPassword);
	}
}
