package org.letsemploy.ojobpub_publisher;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The production context starts: Flyway migrates, every bean wires, and the
 * security configuration resolves.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
