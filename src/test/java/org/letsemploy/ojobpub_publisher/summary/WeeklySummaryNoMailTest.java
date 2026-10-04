package org.letsemploy.ojobpub_publisher.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.mail.Mailer;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Without a mail server the weekly summary does nothing (spec 7.28) - and claims
 * nobody, so the first run once one is configured still reaches everyone. The
 * mocked {@link Mailer} answers {@code configured()} with false.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
class WeeklySummaryNoMailTest {

    @Autowired
    private SummaryService summaries;
    @Autowired
    private UserRepo users;
    @MockitoBean
    private Mailer mailer;

    private UserEntity reader;

    @BeforeEach
    void aSubscriber() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("summary-" + UUID.randomUUID());
        user.setEmail("summary-" + UUID.randomUUID() + "@example.com");
        user.setMailSummary(true);
        reader = users.save(user);
    }

    @AfterEach
    void cleanUp() {
        users.deleteById(reader.getId());
    }

    @Test
    void nothingIsSentAndNobodyClaimed() {
        assertThat(summaries.sendDue(Instant.now())).isZero();
        assertThat(users.findById(reader.getId()).orElseThrow().getSummarySentAt()).isNull();
        verify(mailer, never()).send(any());
    }
}
