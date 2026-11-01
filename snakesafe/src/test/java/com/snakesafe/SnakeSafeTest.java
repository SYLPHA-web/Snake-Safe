package com.snakesafe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * One shared app setup for all integration tests: private in-memory database, the clock under test control,
 * and the timer switched off so each test moves time and runs the checks itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:test-${random.uuid};DB_CLOSE_DELAY=-1",
        "app.scheduler-enabled=false",
        "app.photo-dir=target/test-photos"
})
@Import(TestClockConfig.class)
public @interface SnakeSafeTest {
}
