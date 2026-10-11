package com.personalsite.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The portfolio is public; everything else (tasks, calendar, managing projects) needs a login.
 * The username and password come from spring.security.user.* in config/application.properties.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/about", "/projects", "/login", "/css/**", "/error", "/favicon.ico").permitAll()
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.defaultSuccessUrl("/tasks"))
			.logout(logout -> logout
				.logoutSuccessUrl("/"));
		return http.build();
	}

}
