package com.example.calendar.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@Column(length = CredentialRules.EMAIL_MAX_LENGTH)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false)
	private Instant createdAt;

	protected AppUser() {
	}

	public AppUser(String email, String passwordHash, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public Instant getCreatedAt() { return createdAt; }
}
