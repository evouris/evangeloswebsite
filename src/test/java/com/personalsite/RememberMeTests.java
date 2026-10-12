package com.personalsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.personalsite.security.RememberMeTokenRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:remembermetests",
		"spring.security.user.name=tester",
		"spring.security.user.password={noop}test-pw" })
@AutoConfigureMockMvc
class RememberMeTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RememberMeTokenRepository tokens;

	@BeforeEach
	void forgetDevices() {
		tokens.deleteAll();
	}

	/** Logs in like a fresh device and returns its remember-me cookie (or null if none was set). */
	private Cookie logInOnNewDevice(boolean keepSignedIn) throws Exception {
		var login = post("/login").param("username", "tester").param("password", "test-pw").with(csrf());
		if (keepSignedIn) {
			login.param("remember-me", "on");
		}
		return mvc.perform(login).andExpect(redirectedUrl("/tasks")).andReturn().getResponse().getCookie("remember-me");
	}

	/** Visits a private page with only the cookie (as if the session expired); returns the refreshed cookie. */
	private Cookie visitWithCookie(Cookie cookie) throws Exception {
		MvcResult result = mvc.perform(get("/tasks").cookie(cookie)).andExpect(status().isOk()).andReturn();
		return result.getResponse().getCookie("remember-me");
	}

	@Test
	void withoutCheckboxNoDeviceIsRemembered() throws Exception {
		assertThat(logInOnNewDevice(false)).isNull();
		assertThat(tokens.count()).isZero();
	}

	@Test
	void rememberedDeviceStaysSignedInFor30Days() throws Exception {
		Cookie phone = logInOnNewDevice(true);
		assertThat(phone.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
		assertThat(phone.isHttpOnly()).isTrue();

		Cookie refreshed = visitWithCookie(phone);
		assertThat(refreshed).isNotNull();
		visitWithCookie(refreshed);
	}

	@Test
	void loggingOutOneDeviceKeepsTheOthers() throws Exception {
		Cookie phone = logInOnNewDevice(true);
		Cookie laptop = logInOnNewDevice(true);
		assertThat(tokens.countByUsername("tester")).isEqualTo(2);

		mvc.perform(post("/logout").cookie(phone).with(csrf())).andExpect(redirectedUrl("/"));

		assertThat(tokens.countByUsername("tester")).isEqualTo(1);
		mvc.perform(get("/tasks").cookie(phone)).andExpect(redirectedUrl("/login"));
		visitWithCookie(laptop);
	}

	@Test
	void signOutEverywhereForgetsAllDevices() throws Exception {
		Cookie phone = logInOnNewDevice(true);
		Cookie laptop = logInOnNewDevice(true);
		laptop = visitWithCookie(laptop);

		mvc.perform(post("/account/sign-out-everywhere").cookie(laptop).with(csrf())).andExpect(redirectedUrl("/"));

		assertThat(tokens.count()).isZero();
		mvc.perform(get("/tasks").cookie(phone)).andExpect(redirectedUrl("/login"));
	}

}
