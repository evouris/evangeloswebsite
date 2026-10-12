package com.personalsite.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring's persistent remember-me, except logging out only forgets the device you log out on.
 * (The stock version forgets every device, so logging out on your phone would also log out your laptop.)
 */
public class DeviceRememberMeServices extends PersistentTokenBasedRememberMeServices {

	private final JpaPersistentTokenRepository tokens;

	public DeviceRememberMeServices(String key, UserDetailsService users, JpaPersistentTokenRepository tokens) {
		super(key, users, tokens);
		this.tokens = tokens;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		String cookie = extractRememberMeCookie(request);
		if (cookie != null) {
			try {
				String[] parts = decodeCookie(cookie);
				if (parts.length == 2) {
					tokens.removeSeries(parts[0]);
				}
			}
			catch (InvalidCookieException ignored) {
				// Garbled cookie: nothing to forget, just clear it below.
			}
		}
		cancelCookie(request, response);
	}

}
