package org.letsemploy.ojobpub_publisher.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Two first clicks of one job, day and country at once (spec 5.6). Both find no
 * counter to increment and both insert one; the key refuses the second, which
 * must then count on the row the first made - on both databases, though SQLite
 * reports the refusal as a different exception, and without overwriting it.
 *
 * <p>Made deterministic: the first click's row is committed, and our increment
 * reports no row once, as it would just before that commit. Not
 * {@code @Transactional}: the counting runs in transactions of its own.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
class JobClickRaceTest {

    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID PUBLISHED = UUID.fromString("8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f");
    /** A country no other test counts, so its row is this test's alone. */
    private static final String COUNTRY = "NZ";

    @Autowired
    private JobClickService service;
    @MockitoSpyBean
    private JobClickRepo clickRepo;

    private final JobClick.Key key = new JobClick.Key(PUBLISHED, LocalDate.now(ZoneOffset.UTC), COUNTRY);

    @AfterEach
    void removeTheCounter() {
        Mockito.reset(clickRepo);
        clickRepo.findById(key).ifPresent(clickRepo::delete);
    }

    @Test
    void theSecondFirstClickCountsOnTheFirstsRow() {
        service.record(PUBLISHED, ACME, COUNTRY);   // the first click: its row, committed

        AtomicBoolean raced = new AtomicBoolean();
        doAnswer(inv -> raced.getAndSet(true)
                ? Mockito.mockingDetails(clickRepo).getMockCreationSettings().getDefaultAnswer().answer(inv)
                : 0)
                .when(clickRepo).increment(any());

        service.record(PUBLISHED, ACME, COUNTRY);   // ours: found nothing, inserted, was refused

        assertThat(clickRepo.findById(key)).get().extracting(JobClick::getClicks).isEqualTo(2L);
    }
}
