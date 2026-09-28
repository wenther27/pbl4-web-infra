package com.example.cinehub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Kiem tra toan bo Spring context dung duoc: thieu bean, sai @Value,
 * circular dependency... deu bi phat hien o day.
 */
@SpringBootTest
@ActiveProfiles("test")
class cinehubTests {

	@Test
	void contextLoads() {
	}

}
