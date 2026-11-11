package com.example.calendar.web;

import com.example.calendar.user.EmailAlreadyRegisteredException;
import com.example.calendar.user.RegistrationForm;
import com.example.calendar.user.UserService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/register")
	public String registerForm(Model model) {
		model.addAttribute("registrationForm", new RegistrationForm());
		return "register";
	}

	@PostMapping("/register")
	public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form, BindingResult result) {
		if (!result.hasErrors()) {
			try {
				userService.register(form.getEmail(), form.getPassword());
				return "redirect:/login?registered";
			}
			catch (EmailAlreadyRegisteredException ex) {
				result.rejectValue("email", "duplicate", "An account with this email already exists");
			}
		}
		form.setPassword(null);
		form.setConfirmPassword(null);
		return "register";
	}
}
