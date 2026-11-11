package com.example.calendar.security;

import com.example.calendar.user.UserService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	static PasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-strength:12}") int strength) {
		return new BCryptPasswordEncoder(strength);
	}

	@Bean
	AuthenticationProvider authenticationProvider(UserService userService, PasswordEncoder passwordEncoder) {
		return new ValidatingAuthenticationProvider(userService, passwordEncoder);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${app.security.require-https:true}") boolean requireHttps) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/login", "/register", "/css/**", "/error").permitAll()
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("email")
				.passwordParameter("password")
				.defaultSuccessUrl("/", true)
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutSuccessUrl("/login?logout")
				.deleteCookies("JSESSIONID"));
		if (requireHttps) {
			http.redirectToHttps(Customizer.withDefaults());
		}
		return http.build();
	}
}
