package com.example.calendar.user;

import java.time.Clock;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional
	public AppUser register(String rawEmail, String rawPassword) {
		String email = CredentialRules.normalizeEmail(rawEmail);
		if (!CredentialRules.isValidEmail(email)) {
			throw new IllegalArgumentException(CredentialRules.EMAIL_MESSAGE);
		}
		if (!CredentialRules.isValidPassword(rawPassword)) {
			throw new IllegalArgumentException(CredentialRules.PASSWORD_MESSAGE);
		}
		if (repository.existsById(email)) {
			throw new EmailAlreadyRegisteredException(email);
		}
		return repository.save(new AppUser(email, passwordEncoder.encode(rawPassword), clock.instant()));
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String rawEmail) {
		String email = CredentialRules.normalizeEmail(rawEmail);
		if (!CredentialRules.isValidEmail(email)) {
			throw new UsernameNotFoundException("Invalid email format");
		}
		AppUser user = repository.findById(email)
				.orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
		return User.withUsername(user.getEmail()).password(user.getPasswordHash()).authorities("ROLE_USER").build();
	}
}
