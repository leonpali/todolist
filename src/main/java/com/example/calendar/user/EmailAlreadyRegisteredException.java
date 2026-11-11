package com.example.calendar.user;

public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException(String email) {
		super("An account already exists for " + email);
	}
}
