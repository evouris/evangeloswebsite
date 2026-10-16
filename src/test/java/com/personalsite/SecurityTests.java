package com.personalsite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:securitytests")
@AutoConfigureMockMvc
class SecurityTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void portfolioIsPublic() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk());
		mvc.perform(get("/about")).andExpect(status().isOk());
		mvc.perform(get("/projects")).andExpect(status().isOk());
		mvc.perform(get("/login")).andExpect(status().isOk());
	}

	@Test
	void privatePagesRedirectToLogin() throws Exception {
		for (String path : new String[] { "/tasks", "/calendar", "/projects/manage", "/workouts",
				"/workouts/exercises", "/notes", "/notes/1", "/notes/1/recordings/1/audio" }) {
			mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
		}
	}

	@Test
	void formsWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/tasks").param("title", "sneaky")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	void loggedInUserSeesPrivatePages() throws Exception {
		mvc.perform(get("/tasks")).andExpect(status().isOk());
		mvc.perform(get("/calendar")).andExpect(status().isOk());
		mvc.perform(get("/projects/manage")).andExpect(status().isOk());
		mvc.perform(get("/workouts")).andExpect(status().isOk());
		mvc.perform(get("/workouts/exercises")).andExpect(status().isOk());
		mvc.perform(get("/notes")).andExpect(status().isOk());
	}

}
