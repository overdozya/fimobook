package com.fimobook.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fimo.price-refresh.enabled=false")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
