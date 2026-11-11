package com.example.calendar.security;

import com.example.calendar.user.CredentialRules;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

public class ValidatingAuthenticationProvider extends DaoAuthenticationProvider {

	public ValidatingAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		super(userDetailsService);
		setPasswordEncoder(passwordEncoder);
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		Object credentials = authentication.getCredentials();
		if (!(credentials instanceof String password) || !CredentialRules.isValidPassword(password)) {
			throw new BadCredentialsException("Bad credentials");
		}
		return super.authenticate(authentication);
	}
}
