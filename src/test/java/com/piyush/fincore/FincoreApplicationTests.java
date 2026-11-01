package com.piyush.fincore;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FincoreApplicationTests {

	@Autowired
	private Flyway flyway;

	// The whole app starts: Flyway built the schema and Hibernate's "validate" found every entity matches it.
	@Test
	void contextLoadsOnAFreshDatabaseBuiltByFlyway() {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
	}

}
