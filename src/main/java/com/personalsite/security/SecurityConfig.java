package com.personalsite.security;

import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The portfolio is public; everything else (tasks, calendar, managing projects) needs a login.
 * The username and password come from spring.security.user.* in config/application.properties.
 */
@Configuration
public class SecurityConfig {

	/** How long a device stays signed in after its last visit when "keep me signed in" is ticked. */
	private static final Duration REMEMBER_DEVICE_FOR = Duration.ofDays(30);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users,
			JpaPersistentTokenRepository tokens) throws Exception {
		// The key only links Spring's internal remember-me pieces together; the cookie itself is checked
		// against the database, so a fresh random key on each start is fine.
		String key = UUID.randomUUID().toString();
		DeviceRememberMeServices rememberMe = new DeviceRememberMeServices(key, users, tokens);
		rememberMe.setTokenValiditySeconds((int) REMEMBER_DEVICE_FOR.toSeconds());

		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/about", "/projects", "/login", "/css/**", "/error", "/favicon.ico", "/now").permitAll()
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.defaultSuccessUrl("/tasks"))
			.rememberMe(remember -> remember
				.rememberMeServices(rememberMe)
				.key(key))
			.logout(logout -> logout
				.logoutSuccessUrl("/"));
		return http.build();
	}

}
