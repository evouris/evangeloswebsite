package com.personalsite.security;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AccountController {

	private final JpaPersistentTokenRepository tokens;

	public AccountController(JpaPersistentTokenRepository tokens) {
		this.tokens = tokens;
	}

	/** Forgets every remembered device (e.g. a lost phone), then logs out this one too. */
	@PostMapping("/account/sign-out-everywhere")
	public String signOutEverywhere(Principal principal, HttpServletRequest request) throws ServletException {
		tokens.removeUserTokens(principal.getName());
		request.logout();
		return "redirect:/";
	}

}
