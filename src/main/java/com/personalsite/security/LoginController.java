package com.personalsite.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Shows the login form; Spring Security handles the POST to /login itself. */
@Controller
public class LoginController {

	@GetMapping("/login")
	public String login() {
		return "login";
	}

}
