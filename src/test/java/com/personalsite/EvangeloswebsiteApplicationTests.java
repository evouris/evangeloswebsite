package com.personalsite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// In-memory database so tests never touch (or lock) the real data/ file.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:contextloads")
class EvangeloswebsiteApplicationTests {

	@Test
	void contextLoads() {
	}

}
